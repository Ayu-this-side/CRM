package com.crm.service;

import com.crm.dto.UserDto;
import com.crm.entity.User;
import com.crm.enums.Role;
import com.crm.exception.ResourceNotFoundException;
import com.crm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service layer for User management.
 *
 * Also implements {@link UserDetailsService} so Spring Security can load
 * users by email during form-login authentication.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // -------------------------------------------------
    // Spring Security — UserDetailsService contract
    // -------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .orElseThrow(() -> new UsernameNotFoundException("No user found with email: " + email));
    }

    // -------------------------------------------------
    // Queries
    // -------------------------------------------------

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<User> findActiveSalesReps() {
        return userRepository.findByRoleAndEnabledTrue(Role.SALES_REP);
    }

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAll();
    }

    /**
     * Resolves an assignable owner: only enabled SALES_REP accounts may own records.
     * Admins remain the fallback owner when no rep is selected.
     */
    @Transactional(readOnly = true)
    public User findAssignableRep(Long id) {
        if (id == null) {
            return null;
        }
        return userRepository.findByIdAndRoleAndEnabledTrue(id, Role.SALES_REP)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Only active Sales Rep accounts can be assigned to records (id: " + id + ")."));
    }

    // -------------------------------------------------
    // Mutations
    // -------------------------------------------------

    /** Create a new user account. Password is required; email must be unique. */
    @Transactional
    public User createUser(UserDto dto) {
        String email = normalizeEmail(dto.getEmail());
        if (email == null) {
            throw new IllegalArgumentException("Email is required");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        if (dto.getRole() == null) {
            throw new IllegalArgumentException("Role is required");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email is already in use: " + email);
        }
        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password is required when creating a new user");
        }
        if (dto.getPassword().length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long");
        }

        User user = new User();
        user.setName(dto.getName().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRole(dto.getRole());
        user.setEnabled(true);

        User saved = userRepository.save(user);
        log.info("Created user [id={}] email={} role={}", saved.getId(), saved.getEmail(), saved.getRole());
        return saved;
    }

    /**
     * Update an existing user. Password is optional — if blank, the existing
     * hashed password is retained. Self-demotion and removing the last active
     * Admin are rejected so the application cannot be locked out.
     */
    @Transactional
    public User updateUser(Long id, UserDto dto, User currentUser) {
        User user = findById(id);
        String email = normalizeEmail(dto.getEmail());

        if (email == null) {
            throw new IllegalArgumentException("Email is required");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        if (dto.getRole() == null) {
            throw new IllegalArgumentException("Role is required");
        }
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new IllegalArgumentException("Email is already in use: " + email);
        }

        boolean demotingAdmin = user.getRole() == Role.ADMIN && dto.getRole() != Role.ADMIN;
        if (demotingAdmin && currentUser != null && currentUser.getId().equals(id)) {
            throw new IllegalStateException("You cannot demote your own Admin account.");
        }
        if (demotingAdmin && user.isEnabled()
                && userRepository.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new IllegalStateException("At least one active Admin account must remain.");
        }

        user.setName(dto.getName().trim());
        user.setEmail(email);
        user.setRole(dto.getRole());

        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            if (dto.getPassword().length() < 8) {
                throw new IllegalArgumentException("Password must be at least 8 characters long");
            }
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
            log.info("Password updated for user [id={}]", id);
        }

        User saved = userRepository.save(user);
        log.info("Updated user [id={}]", saved.getId());
        return saved;
    }

    @Transactional
    public void deactivateUser(Long id, User currentUser) {
        if (currentUser != null && currentUser.getId().equals(id)) {
            throw new IllegalStateException("You cannot deactivate your own account.");
        }

        User user = findById(id);
        if (user.getRole() == Role.ADMIN && user.isEnabled()
                && userRepository.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new IllegalStateException("At least one active Admin account must remain.");
        }

        user.setEnabled(false);
        userRepository.save(user);
        log.info("Deactivated user [id={}]", id);
    }

    @Transactional
    public void activateUser(Long id) {
        User user = findById(id);
        user.setEnabled(true);
        userRepository.save(user);
        log.info("Activated user [id={}]", id);
    }

    private String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase();
    }
}
