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

    @Autowired
    private com.skillhub.service.RateLimitingService rateLimitingService;

    @GetMapping("/forgot-password")
    public String showForgotPasswordForm() {
        return "forgot_password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(HttpServletRequest request, @RequestParam("email") String userEmail,
            Model model) {
        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isEmpty()) {
            clientIp = request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
        } else {
            clientIp = clientIp.split(",")[0].trim();
        }

        if (!rateLimitingService.allowRequest("forgot-pw:" + clientIp, 5, 300)) {
            model.addAttribute("error", "Too many password reset requests from this IP. Please try again after 5 minutes.");
            return "forgot_password";
        }

        User user = userRepository.findByEmail(userEmail != null ? userEmail.trim() : "");

        if (user == null) {
            model.addAttribute("message", "We have sent a reset link to your email address if it exists.");
            return "forgot_password";
        }

        String token = UUID.randomUUID().toString();
        String otp = String.format("%06d", new java.util.Random().nextInt(900000) + 100000);

        PasswordResetToken existingToken = tokenRepository.findByUser(user);
        if (existingToken != null) {
            tokenRepository.delete(existingToken);
        }

        PasswordResetToken myToken = new PasswordResetToken(token, otp, user, LocalDateTime.now().plusMinutes(15));
        tokenRepository.save(myToken);

        String appUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();

        if ((request.getScheme().equals("http") && request.getServerPort() == 80) ||
                (request.getScheme().equals("https") && request.getServerPort() == 443)) {
            appUrl = request.getScheme() + "://" + request.getServerName();
        }

        String link = appUrl + "/reset-password?token=" + token;

        String subject = "SkillHub - Your Password Reset OTP: " + otp;
        String content = "Hello " + user.getName() + ",\n\n" +
                "You have requested to reset your password on SkillHub.\n\n" +
                "Your 6-digit verification OTP is:\n" +
                "-------------------------\n" +
                "         " + otp + "\n" +
                "-------------------------\n\n" +
                "Enter this OTP on the verification page to reset your password. This OTP is valid for 15 minutes.\n\n" +
                "Alternatively, you can click this direct reset link:\n" +
                link + "\n\n" +
                "If you did not request this password reset, please ignore this email.";
        emailService.sendSimpleMessage(user.getEmail(), subject, content);

        model.addAttribute("message", "We have sent a 6-digit OTP to your email address: " + user.getEmail());
        model.addAttribute("otpSent", true);
        model.addAttribute("email", user.getEmail());
        return "forgot_password";
    }

    @GetMapping("/verify-otp")
    public String showVerifyOtpForm(@RequestParam(value = "email", required = false) String email, Model model) {
        model.addAttribute("email", email);
        model.addAttribute("otpSent", true);
        return "forgot_password";
    }

    @PostMapping("/verify-otp")
    public String processVerifyOtp(@RequestParam("email") String email,
                                   @RequestParam("otp") String otp,
                                   Model model) {
        if (email == null || email.trim().isEmpty() || otp == null || otp.trim().isEmpty()) {
            model.addAttribute("error", "Email and OTP are required.");
            model.addAttribute("otpSent", true);
            model.addAttribute("email", email);
            return "forgot_password";
        }

        User user = userRepository.findByEmail(email.trim());
        if (user == null) {
            model.addAttribute("error", "No user found with this email.");
            return "forgot_password";
        }

        Optional<PasswordResetToken> tokenOptional = tokenRepository.findByUserAndOtp(user, otp.trim());
        if (!tokenOptional.isPresent()) {
            model.addAttribute("error", "Invalid OTP. Please check your email and try again.");
            model.addAttribute("otpSent", true);
            model.addAttribute("email", email);
            return "forgot_password";
        }

        PasswordResetToken resetToken = tokenOptional.get();
        if (resetToken.isExpired()) {
            model.addAttribute("error", "OTP has expired. Please request a new one.");
            return "forgot_password";
        }

        // OTP is verified! Redirect to set new password with the secure token
        return "redirect:/reset-password?token=" + resetToken.getToken();
    }

    @GetMapping("/reset-password")
    public String showResetPasswordForm(@RequestParam(name = "token", required = false) String token, Model model, HttpServletRequest request) {
        if (request != null) {
            request.getSession(true);
        }
        if (token == null || token.trim().isEmpty()) {
            model.addAttribute("error", "Password reset token is missing.");
            return "login-page";
        }

        Optional<PasswordResetToken> tokenOptional = tokenRepository.findByToken(token);

        if (!tokenOptional.isPresent()) {
            model.addAttribute("error", "Invalid password reset token.");
            return "login-page";
        }

        PasswordResetToken resetToken = tokenOptional.get();
        if (resetToken.isExpired()) {
            model.addAttribute("error", "Token has expired. Please request a new one.");
            return "login-page";
        }

        PasswordDto passwordDto = new PasswordDto();
        passwordDto.setToken(token);
        model.addAttribute("passwordDto", passwordDto);
        return "reset_password";
    }

    @PostMapping("/reset-password")
    public String processResetPassword(@ModelAttribute("passwordDto") PasswordDto passwordDto, Model model, HttpServletRequest request) {
        if (request != null) {
            request.getSession(true);
        }
        Optional<PasswordResetToken> tokenOptional = tokenRepository.findByToken(passwordDto.getToken());

        if (!tokenOptional.isPresent()) {
            model.addAttribute("error", "Invalid token.");
            return "login-page";
        }

        PasswordResetToken token = tokenOptional.get();
        if (token.isExpired()) {
            model.addAttribute("error", "Token has expired.");
            return "login-page";
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
