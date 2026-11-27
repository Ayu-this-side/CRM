package com.crm.service;

import com.crm.dto.UserDto;
import com.crm.entity.User;
import com.crm.enums.Role;
import com.crm.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User admin;

    @BeforeEach
    void setUp() {
        admin = new User();
        admin.setId(1L);
        admin.setName("Admin");
        admin.setEmail("admin@crm.com");
        admin.setRole(Role.ADMIN);
        admin.setEnabled(true);
    }

    @Test
    @DisplayName("Login lookup normalises email casing")
    void loadUserByUsername_normalisesEmail() {
        when(userRepository.findByEmailIgnoreCase("admin@crm.com")).thenReturn(Optional.of(admin));

        UserDetails details = userService.loadUserByUsername("  ADMIN@CRM.COM ");

        assertThat(details.getUsername()).isEqualTo("admin@crm.com");
        verify(userRepository).findByEmailIgnoreCase("admin@crm.com");
    }

    @Test
    @DisplayName("An admin cannot demote themselves")
    void updateUser_selfDemotionRejected() {
        UserDto dto = new UserDto();
        dto.setName("Admin");
        dto.setEmail("admin@crm.com");
        dto.setRole(Role.SALES_REP);

        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.existsByEmailIgnoreCaseAndIdNot("admin@crm.com", 1L)).thenReturn(false);

        assertThatThrownBy(() -> userService.updateUser(1L, dto, admin))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot demote");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("An admin cannot deactivate themselves")
    void deactivateUser_selfDeactivationRejected() {
        assertThatThrownBy(() -> userService.deactivateUser(1L, admin))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot deactivate");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("The last active admin cannot be deactivated")
    void deactivateUser_lastAdminRejected() {
        User otherAdmin = new User();
        otherAdmin.setId(2L);
        otherAdmin.setEmail("other@crm.com");
        otherAdmin.setRole(Role.ADMIN);
        otherAdmin.setEnabled(true);

        when(userRepository.findById(2L)).thenReturn(Optional.of(otherAdmin));
        when(userRepository.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.deactivateUser(2L, admin))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active Admin account must remain");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("createUser stores a normalised email and BCrypt-hashed password")
    void createUser_normalisesAndHashes() {
        UserDto dto = new UserDto();
        dto.setName("  Jane  ");
        dto.setEmail("  JANE@Example.COM ");
        dto.setPassword("securepass123");
        dto.setRole(Role.SALES_REP);

        when(userRepository.existsByEmailIgnoreCase("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode("securepass123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User created = userService.createUser(dto);

        assertThat(created.getEmail()).isEqualTo("jane@example.com");
        assertThat(created.getName()).isEqualTo("Jane");
        assertThat(created.getPassword()).isEqualTo("hashed");
        verify(userRepository, never()).findByEmail(anyString());
    }
}
