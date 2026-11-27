package com.crm.config;

import com.crm.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Spring Security configuration.
 *
 * - Form login via /login (email as username parameter)
 * - Role-based route protection: /admin/** requires ADMIN
 * - All other routes require authentication
 * - BCrypt password hashing (see AppConfig)
 * - CSRF enabled (Spring Security default; Thymeleaf includes CSRF tokens in forms)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final AccountStatusFilter accountStatusFilter;

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authenticationProvider(authenticationProvider())

            // ---- Route protection ----
            .authorizeHttpRequests(auth -> auth
                // Static assets and error pages are public
                .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                .requestMatchers("/login", "/error").permitAll()
                // Admin-only section
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // Everything else requires login
                .anyRequest().authenticated()
            )

            // ---- Form Login ----
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("email")      // We use email, not username
                .passwordParameter("password")
                .defaultSuccessUrl("/", true)
                .failureHandler((request, response, exception) -> {
                    String reason = exception instanceof DisabledException ? "disabled=true" : "error=true";
                    response.sendRedirect(request.getContextPath() + "/login?" + reason);
                })
                .permitAll()
            )

            // ---- Logout ----
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "POST"))
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )

            // ---- Access Denied ----
            // Redirect (rather than forward) so POSTs do not hit the GET-only page.
            .exceptionHandling(ex -> ex
                .accessDeniedHandler((request, response, denied) ->
                        response.sendRedirect(request.getContextPath() + "/access-denied"))
            )

            // ---- Revalidate deactivated/demoted accounts on every request ----
            .addFilterAfter(accountStatusFilter, SecurityContextHolderFilter.class);

        return http.build();
    }
}
