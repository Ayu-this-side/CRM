package com.crm.entity;

import com.crm.enums.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * User entity — represents both Admin and Sales Rep accounts.
 *
 * Implements UserDetails so it can be used directly by Spring Security
 * without a separate wrapper class. Login is done by email (not username).
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean enabled = true;

    /** Optimistic locking — prevents silent last-write-wins on account changes. */
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // -------------------------------------------------
    // Lifecycle Hooks
    // -------------------------------------------------

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // -------------------------------------------------
    // UserDetails — Spring Security contract
    // -------------------------------------------------

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /**
     * Spring Security uses this as the "username". We use email.
     */
    @Override
    public String getUsername() {
        return email;
    }

    // Lombok generates getPassword() and isEnabled() from the fields above.

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
