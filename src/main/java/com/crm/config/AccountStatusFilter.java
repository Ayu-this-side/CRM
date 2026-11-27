package com.crm.config;

import com.crm.entity.User;
import com.crm.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Revalidates the logged-in account on every request.
 *
 * The authenticated principal is a JPA User stored in the session; without this
 * filter, deactivating an account or changing its role would only take effect
 * at the next login. This check closes that gap and also handles concurrent
 * sessions of a deactivated user.
 */
@Component
@RequiredArgsConstructor
public class AccountStatusFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof User principal) {
            Optional<User> current = userRepository.findByEmailIgnoreCase(principal.getEmail());

            if (current.isEmpty() || !current.get().isEnabled()
                    || current.get().getRole() != principal.getRole()) {
                new SecurityContextLogoutHandler().logout(request, response, authentication);
                response.sendRedirect(request.getContextPath() + "/login?disabled=true");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
