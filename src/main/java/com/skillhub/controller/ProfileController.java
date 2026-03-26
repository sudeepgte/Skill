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
    public String showProfile(@RequestParam(value = "tab", required = false, defaultValue = "profile") String tab, Model model, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }
        User user = userService.findByEmail(principal.getName());
        model.addAttribute("user", user);

        // Fetch enrollments for My Courses tab
        java.util.List<com.skillhub.model.Enrollment> enrollments = enrollmentRepository.findByStudent(user);
        model.addAttribute("enrollments", enrollments);
        
        // Fetch certificates (Enrolled courses where exam is passed)
        java.util.List<com.skillhub.model.Enrollment> certificates = enrollments.stream()
                .filter(com.skillhub.model.Enrollment::isExamPassed)
                .collect(java.util.stream.Collectors.toList());
        model.addAttribute("certificates", certificates);
        model.addAttribute("activeTab", tab);

        return "profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@RequestParam("name") String name,
                                @RequestParam("email") String email,
                                @RequestParam(value = "phone", required = false) String phone,
                                @RequestParam(value = "skills", required = false) String skills,
                                @RequestParam(value = "bio", required = false) String bio,
                                @RequestParam(value = "headline", required = false) String headline,
                                Principal principal,
                                RedirectAttributes redirectAttributes) {
        if (principal == null) {
            return "redirect:/login";
        }

        User user = userService.findByEmail(principal.getName());
        if (user == null) {
            return "redirect:/login";
        }

        // Basic validation
        if (!user.getEmail().equals(email)) {
            User existing = userService.findByEmail(email);
            if (existing != null) {
                redirectAttributes.addFlashAttribute("error", "Email already in use.");
                return "redirect:/profile";
            }
        }

        user.setName(name);
        user.setEmail(email);
        user.setPhone(phone);
        user.setSkills(skills);
        user.setBio(bio);
        user.setHeadline(headline);

        userService.updateUser(user);

        // Handle email change re-auth if needed
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
