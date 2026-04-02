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

import java.util.Collections;
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
            throw new UsernameNotFoundException("User not found");
        }
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                user.isApproved() && !user.isBlocked(), // enabled status
                true,
                true,
                true,
                Collections.singletonList(new SimpleGrantedAuthority(user.getRole())));
    }
}
