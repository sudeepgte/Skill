package com.skillhub.config;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Enhanced authentication success handler that returns users to their
 * intended destination (SavedRequest) or redirects based on role.
 */
@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @org.springframework.beans.factory.annotation.Autowired
    private com.skillhub.service.UserService userService;

    private final RequestCache requestCache = new HttpSessionRequestCache();

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication)
            throws IOException, ServletException {

        // Track login
        userService.trackLogin(authentication.getName());

        // Check if there's a saved request (e.g. from trying to access a restricted
        // page)
        SavedRequest savedRequest = requestCache.getRequest(request, response);

        if (savedRequest != null) {
            String targetUrl = savedRequest.getRedirectUrl();
            // Don't redirect back to login or error pages if possible
            if (!targetUrl.contains("/login") && !targetUrl.contains("/error")) {
                response.sendRedirect(targetUrl);
                return;
            }
        }

        // Default Redirect based on role
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(ga -> ga.getAuthority().equalsIgnoreCase("ADMIN")
                        || ga.getAuthority().equalsIgnoreCase("ROLE_ADMIN"));
        boolean isInstructor = authentication.getAuthorities().stream()
                .anyMatch(ga -> ga.getAuthority().equalsIgnoreCase("INSTRUCTOR")
                        || ga.getAuthority().equalsIgnoreCase("ROLE_INSTRUCTOR"));

        if (isAdmin) {
            response.sendRedirect("/admin/dashboard");
        } else if (isInstructor) {
            response.sendRedirect("/vendor/dashboard");
        } else {
            response.sendRedirect("/user/home");
        }
    }
}
