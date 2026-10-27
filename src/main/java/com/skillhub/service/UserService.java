package com.skillhub.service;

import com.skillhub.model.User;
import com.skillhub.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    public void saveUser(User user) {
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
        String cleanEmail = user.getEmail().trim().toLowerCase();
        user.setEmail(cleanEmail);

        if (userRepository.existsByEmail(cleanEmail)) {
            throw new IllegalArgumentException("Email is already registered: " + cleanEmail);
        }

        if (user.getPassword() == null || user.getPassword().length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        // Default role if not set
        if (user.getRole() == null || user.getRole().isEmpty()) {
            user.setRole("STUDENT");
        }
        userRepository.save(user);
    }

    public void updateUser(User user) {
        userRepository.save(user);
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public boolean existsByEmail(String email) {
        if (email == null) return false;
        return userRepository.existsByEmail(email.trim().toLowerCase());
    }

    public User getUserById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    public long countUsers() {
        return userRepository.count();
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    public void trackLogin(String email) {
        User user = userRepository.findByEmail(email);
        if (user != null) {
            user.setLastLoginDate(java.time.LocalDateTime.now());
            user.updateStreak(); // Update gamification streak
            userRepository.save(user);
        }
    }

    public void blockUser(Long id) {
        User user = userRepository.findById(id).orElse(null);
        if (user != null) {
            user.setBlocked(true);
            userRepository.save(user);
        }
    }

    public void unblockUser(Long id) {
        User user = userRepository.findById(id).orElse(null);
        if (user != null) {
            user.setBlocked(false);
            userRepository.save(user);
        }
    }

    public void approveUser(Long id) {
        User user = userRepository.findById(id).orElse(null);
        if (user != null) {
            user.setApproved(true);
            userRepository.save(user);
            
            // Send email notification indicating approval
            try {
                String subject = "Account Approved - Welcome to SkillHub!";
                String text = "Hello " + user.getName() + ",\n\n" +
                              "Great news! Your vendor account has been approved by the administrator.\n" +
                              "You can now log in and start creating courses.\n\n" +
                              "Best regards,\nThe SkillHub Team";
                emailService.sendSimpleMessage(user.getEmail(), subject, text);
            } catch (Exception e) {
                System.err.println("Failed to send approval email: " + e.getMessage());
            }
        }
    }

    public long countActiveUsers24h() {
        return userRepository.countByLastLoginDateAfter(java.time.LocalDateTime.now().minusHours(24));
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + email);
        }

        String role = user.getRole();
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        if (role != null && !role.trim().isEmpty()) {
            String cleanRole = role.trim().toUpperCase();
            authorities.add(new SimpleGrantedAuthority(cleanRole));
            if (!cleanRole.startsWith("ROLE_")) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + cleanRole));
            } else {
                authorities.add(new SimpleGrantedAuthority(cleanRole.substring(5)));
            }
        }

        boolean enabled = Boolean.TRUE.equals(user.isApproved()) && !user.isBlocked();
        boolean accountNonLocked = !user.isBlocked();

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                enabled,
                true,
                true,
                accountNonLocked,
                authorities);
    }
}
