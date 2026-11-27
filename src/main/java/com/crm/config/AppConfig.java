package com.crm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Standalone PasswordEncoder bean — kept separate from SecurityConfig to
 * avoid a circular dependency: SecurityConfig → UserService → PasswordEncoder.
 *
 * By moving PasswordEncoder here, both SecurityConfig and UserService can
 * inject it without creating a dependency cycle.
 */
@Configuration
public class AppConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
