package com.skillhub.config;

import com.skillhub.service.UserService;
import com.skillhub.security.AuthEntryPointJwt;
import com.skillhub.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
public class SecurityConfig {

    @Autowired
    private AuthEntryPointJwt unauthorizedHandler;

    @Autowired
    private CustomAuthenticationSuccessHandler successHandler;

    @Autowired
    private CustomAuthenticationFailureHandler failureHandler;

    @Bean
    public JwtAuthenticationFilter authenticationJwtTokenFilter() {
        return new JwtAuthenticationFilter();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(UserService userService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider auth = new DaoAuthenticationProvider();
        auth.setUserDetailsService(userService);
        auth.setPasswordEncoder(passwordEncoder);
        return auth;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();
        configuration.setAllowedOriginPatterns(java.util.Arrays.asList(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "https://*.skillhub.com"
        ));
        configuration.setAllowedMethods(java.util.Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(java.util.Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-CSRF-TOKEN",
                "X-XSRF-TOKEN",
                "X-Requested-With",
                "Accept",
                "Origin"
        ));
        configuration.setExposedHeaders(java.util.Arrays.asList("Authorization", "X-Request-ID"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, UserService userService, PasswordEncoder passwordEncoder)
            throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/api/**", "/ws/**", "/newsletter/**", "/submit-contact-form"))
                .exceptionHandling(exception -> exception
                        .defaultAuthenticationEntryPointFor(
                                unauthorizedHandler,
                                new AntPathRequestMatcher("/api/**")))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(sessionFixation -> sessionFixation.changeSessionId()))
                .authorizeHttpRequests(auth -> auth
                        // 1. Role-specific restricted routes (Must be declared before permitAll catch-alls)
                        .requestMatchers("/register/admin", "/admin/register", "/admin/**", "/api/analytics/admin/**").hasAnyAuthority("ADMIN", "ROLE_ADMIN")
                        .requestMatchers("/vendor/**", "/instructor/subscription/**", "/api/subscriptions/**", "/api/upload-video").hasAnyAuthority("INSTRUCTOR", "ADMIN", "ROLE_INSTRUCTOR", "ROLE_ADMIN")
                        
                        // 2. Publicly accessible routes (specific matches, no overly broad wildcard)
                        .requestMatchers("/", "/register", "/saveUser",
                                "/login", "/forgot-password", "/reset-password", "/verify-otp",
                                "/api/auth/**", "/api/broadcast/**",
                                "/css/**", "/js/**", "/images/**",
                                "/course/**", "/courses", "/courses/**", "/pricing", "/library", "/error",
                                "/uploads/public/**", "/uploads/*.jpg", "/uploads/*.jpeg", "/uploads/*.png", "/uploads/*.webp",
                                "/about", "/contact", "/submit-contact-form",
                                "/business", "/teaching", "/app", "/help", "/blog", "/faq", 
                                "/instructors", "/instructor/*",
                                "/terms", "/privacy", "/cookies", "/accessibility",
                                "/sw.js", "/manifest.json", "/favicon.ico", "/.well-known/**", "/ws/**",
                                "/certifications/verify/**",
                                "/api/health", "/api/health/**",
                                "/api/payment/webhook", "/api/razorpay/webhook",
                                "/api/newsletter/**", "/newsletter/**")
                        .permitAll()

                        // 3. Authenticated routes
                        .requestMatchers("/user/**").authenticated()
                        .anyRequest().authenticated())
                .headers(headers -> headers
                        .contentTypeOptions(contentType -> {})
                        .frameOptions(frame -> frame.sameOrigin())
                        .referrerPolicy(referrer -> referrer.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com https://checkout.razorpay.com; style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com https://cdnjs.cloudflare.com https://cdn.jsdelivr.net data:; img-src 'self' data: blob: https:; media-src 'self' data: blob:; connect-src 'self' ws: wss: https://cdn.jsdelivr.net https://cdnjs.cloudflare.com https://api.razorpay.com https://lumberjack.razorpay.com; frame-src 'self' https://api.razorpay.com https://checkout.razorpay.com; frame-ancestors 'self';"))
                        .permissionsPolicyHeader(permissions -> permissions.policy("camera=(self), microphone=(self), geolocation=()"))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)))
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .successHandler(successHandler)
                        .failureHandler(failureHandler)
                        .permitAll())
                .logout(logout -> logout
                        .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                        .logoutSuccessUrl("/?logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll());

        http.authenticationProvider(authenticationProvider(userService, passwordEncoder));
        http.addFilterBefore(authenticationJwtTokenFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}