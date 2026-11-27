package com.crm.repository;

import com.crm.entity.User;
import com.crm.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link User} entities.
 * Extends JpaRepository for standard CRUD + Spring Data query derivation.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    /** Login and duplicate checks must be case-insensitive. */
    Optional<User> findByEmailIgnoreCase(String email);

    /** Only active Sales Reps may own CRM records. */
    Optional<User> findByIdAndRoleAndEnabledTrue(Long id, Role role);

    List<User> findByRole(Role role);

    List<User> findByRoleAndEnabledTrue(Role role);

    long countByRoleAndEnabledTrue(Role role);

    boolean existsByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
}
