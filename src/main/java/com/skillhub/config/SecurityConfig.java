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
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private AuthEntryPointJwt unauthorizedHandler;

    @Autowired
    private CustomAuthenticationSuccessHandler successHandler;

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
    public SecurityFilterChain filterChain(HttpSecurity http, UserService userService, PasswordEncoder passwordEncoder) throws Exception {
        http.csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
            .exceptionHandling(exception -> exception
                .defaultAuthenticationEntryPointFor(
                    unauthorizedHandler, 
                    new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/api/**")
                )
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/register", "/register/admin", "/admin/register", "/saveUser",
                                "/login", "/forgot-password", "/reset-password",
                                "/api/auth/**",
                                "/css/**", "/js/**", "/images/**",
                                "/course/**", "/error", "/uploads/**")
                .permitAll()
                .requestMatchers("/admin/**").hasAuthority("ADMIN")
                .requestMatchers("/instructor/**").hasAnyAuthority("INSTRUCTOR", "ADMIN")
                .requestMatchers("/user/**").authenticated()
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(successHandler)
                .permitAll())
            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .permitAll());

        http.authenticationProvider(authenticationProvider(userService, passwordEncoder));
        http.addFilterBefore(authenticationJwtTokenFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
