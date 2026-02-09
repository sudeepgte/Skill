package com.skillhub.controller;

import com.skillhub.dto.PasswordDto;
import com.skillhub.model.PasswordResetToken;
import com.skillhub.model.User;
import com.skillhub.repository.PasswordResetTokenRepository;
import com.skillhub.repository.UserRepository;
import com.skillhub.service.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Controller
public class ForgotPasswordController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/forgot-password")
    public String showForgotPasswordForm() {
        return "forgot_password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(HttpServletRequest request, @RequestParam("email") String userEmail,
            Model model) {
        User user = userRepository.findByEmail(userEmail);

        if (user == null) {
            // For security, don't reveal if user exists or not, but for UX we might just
            // say "If an account exists, email sent"
            // However, the prompt says "Do NOT expose user existence in error messages."
            // But also "Success & error message handling." and "Friendly messages."
            // Standard approach: "We have sent a reset link to your email if it exists."
            model.addAttribute("message", "We have sent a reset link to your email address if it exists.");
            return "forgot_password";
        }
        String token = UUID.randomUUID().toString();

        // Remove existing token if any? Or just create new.
        PasswordResetToken existingToken = tokenRepository.findByUser(user);
        if (existingToken != null) {
            tokenRepository.delete(existingToken);
        }

        PasswordResetToken myToken = new PasswordResetToken(token, user, LocalDateTime.now().plusMinutes(15));
        tokenRepository.save(myToken);

        String appUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();

        // Handle standard port 80/443
        if ((request.getScheme().equals("http") && request.getServerPort() == 80) ||
                (request.getScheme().equals("https") && request.getServerPort() == 443)) {
            appUrl = request.getScheme() + "://" + request.getServerName();
        }

        String link = appUrl + "/reset-password?token=" + token;

        String subject = "Reset your SkillHub Password";
        String content = "Hello " + user.getName() + ",\n\n" +
                "You have requested to reset your password on SkillHub.\n" +
                "Click the link below to change your password:\n\n" +
                link + "\n\n" +
                "This link will expire in 15 minutes.\n" +
                "Ignore this email if you do remember your password, or you have not made the request.";

        emailService.sendSimpleMessage(user.getEmail(), subject, content);

        model.addAttribute("message", "We have sent a reset link to your email address.");
        return "forgot_password";
    }

    @GetMapping("/reset-password")
    public String showResetPasswordForm(@RequestParam("token") String token, Model model) {
        Optional<PasswordResetToken> tokenOptional = tokenRepository.findByToken(token);

        if (!tokenOptional.isPresent()) {
            model.addAttribute("error", "Invalid password reset token.");
            return "login"; // Or specific error page
        }

        PasswordResetToken resetToken = tokenOptional.get();
        if (resetToken.isExpired()) {
            model.addAttribute("error", "Token has expired. Please request a new one.");
            return "login";
        }

        PasswordDto passwordDto = new PasswordDto();
        passwordDto.setToken(token);
        model.addAttribute("passwordDto", passwordDto);
        return "reset_password";
    }

    @PostMapping("/reset-password")
    public String processResetPassword(@ModelAttribute("passwordDto") PasswordDto passwordDto, Model model) {
        Optional<PasswordResetToken> tokenOptional = tokenRepository.findByToken(passwordDto.getToken());

        if (!tokenOptional.isPresent()) {
            model.addAttribute("error", "Invalid token.");
            return "login";
        }

        PasswordResetToken token = tokenOptional.get();
        if (token.isExpired()) {
            model.addAttribute("error", "Token has expired.");
            return "login";
        }

        if (!passwordDto.getNewPassword().equals(passwordDto.getConfirmPassword())) {
            model.addAttribute("error", "Passwords do not match.");
            return "reset_password";
        }

        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(passwordDto.getNewPassword()));
        userRepository.save(user);

        tokenRepository.delete(token);

        return "redirect:/login?resetSuccess";
    }
}
