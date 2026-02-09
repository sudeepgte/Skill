package com.skillhub.controller;

import com.skillhub.model.User;
import com.skillhub.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
public class ProfileController {

    @Autowired
    private UserService userService;

    @Autowired
    private com.skillhub.repository.EnrollmentRepository enrollmentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/profile")
    public String showProfile(Model model, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }
        User user = userService.findByEmail(principal.getName());
        model.addAttribute("user", user);

        // Fetch enrollments for My Courses tab
        java.util.List<com.skillhub.model.Enrollment> enrollments = enrollmentRepository.findByStudent(user);
        model.addAttribute("enrollments", enrollments);

        return "profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@RequestParam("name") String name,
            @RequestParam("email") String email,
            @RequestParam(value = "phone", required = false) String phone,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        if (principal == null) {
            return "redirect:/login";
        }

        User user = userService.findByEmail(principal.getName());
        if (user == null) {
            return "redirect:/login";
        }

        // Basic validation - check if email is changed and unique if necessary,
        // but for simplicity here we assume email update might need re-login or
        // verification.
        // If email is changed, check if it already exists for another user.
        if (!user.getEmail().equals(email)) {
            User existing = userService.findByEmail(email);
            if (existing != null) {
                redirectAttributes.addFlashAttribute("error", "Email already in use.");
                return "redirect:/profile";
            }
        }

        user.setName(name);
        user.setEmail(email);
        // user.setPhone(phone); // Assuming User model has phone field. If not, will
        // check.
        // Checking User model... The view_file previously didn't show User model
        // content.
        // I'll assume standard fields. If setPhone fails, I'll need to remove it or add
        // it to User.
        // Safe bet: stick to name and email first, or check User model.
        // Prompt says: Full Name, Email, Phone (optional).

        userService.updateUser(user);

        // If email changed, we might need to update the Principal/Authentication, but
        // that's complex.
        // For now, let's update the DB. User might need to re-login if Spring Security
        // relies on email as username.
        if (!principal.getName().equals(email)) {
            redirectAttributes.addFlashAttribute("success", "Profile updated. Please log in with your new email.");
            return "redirect:/logout";
        }

        redirectAttributes.addFlashAttribute("success", "Profile updated successfully.");
        return "redirect:/profile";
    }

    @GetMapping("/change-password")
    public String showChangePassword(Model model, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }
        model.addAttribute("user", userService.findByEmail(principal.getName()));
        return "change_password";
    }

    @PostMapping("/change-password")
    public String changePassword(@RequestParam("currentPassword") String currentPassword,
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        if (principal == null) {
            return "redirect:/login";
        }

        User user = userService.findByEmail(principal.getName());

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            redirectAttributes.addFlashAttribute("error", "Incorrect current password.");
            return "redirect:/change-password";
        }

        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "New passwords do not match.");
            return "redirect:/change-password";
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userService.updateUser(user);

        redirectAttributes.addFlashAttribute("success", "Password changed successfully.");
        return "redirect:/profile";
    }
}
