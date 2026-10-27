package com.skillhub.config;

import com.skillhub.model.User;
import com.skillhub.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAuthenticationFailureHandler implements AuthenticationFailureHandler {

    @Autowired
    private UserRepository userRepository;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {

        String username = request.getParameter("username");
        if (username != null && !username.trim().isEmpty()) {
            User user = userRepository.findByEmail(username.trim().toLowerCase());
            if (user != null) {
                if (Boolean.FALSE.equals(user.isApproved())) {
                    response.sendRedirect("/login?unapproved=true");
                    return;
                }
                if (user.isBlocked()) {
                    response.sendRedirect("/login?blocked=true");
                    return;
                }
            }
        }

        if (exception instanceof DisabledException) {
            response.sendRedirect("/login?unapproved=true");
            return;
        }

        response.sendRedirect("/login?error=true");
    }
}
