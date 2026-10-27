package com.skillhub.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAuthenticationFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {

        // When Spring Security validates the account during authentication:
        // - DisabledException is thrown if user.isEnabled() == false (e.g. pending instructor approval)
        // - LockedException is thrown if user.isAccountNonLocked() == false (e.g. blocked user)
        if (exception instanceof DisabledException) {
            response.sendRedirect("/login?unapproved=true");
            return;
        }

        if (exception instanceof LockedException) {
            response.sendRedirect("/login?blocked=true");
            return;
        }

        // Generic error prevents account enumeration (does not reveal if email exists or password was wrong)
        response.sendRedirect("/login?error=true");
    }
}
