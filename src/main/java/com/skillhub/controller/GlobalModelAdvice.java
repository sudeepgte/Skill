package com.skillhub.controller;

import com.skillhub.model.User;
import com.skillhub.model.Enrollment;
import com.skillhub.service.UserService;
import com.skillhub.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import java.security.Principal;
import java.util.Comparator;
import java.util.Optional;

@ControllerAdvice
public class GlobalModelAdvice {

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryService categoryService;

    @ModelAttribute
    public void addGlobalAttributes(Model model, Principal principal) {
        if (principal != null) {
            String email = principal.getName();
            User user = userService.findByEmail(email);
            if (user != null) {
                model.addAttribute("user", user);

                // Find current course (unfinished one with highest progress or latest
                // enrollment)
                if (user.getEnrollments() != null && !user.getEnrollments().isEmpty()) {
                    // Filter out null elements or enrollments without courses
                    java.util.List<Enrollment> validEnrollments = user.getEnrollments().stream()
                            .filter(e -> e != null && e.getCourse() != null)
                            .collect(java.util.stream.Collectors.toList());

                    if (!validEnrollments.isEmpty()) {
                        Optional<Enrollment> currentEnrollment = validEnrollments.stream()
                                .filter(e -> e.getProgressPercentage() < 100.0)
                                .max(Comparator.comparing(Enrollment::getProgressPercentage)
                                        .thenComparing(e -> e.getEnrolledAt() != null ? e.getEnrolledAt() : java.time.LocalDateTime.MIN));

                        // Fallback to latest enrollment if all completed
                        if (currentEnrollment.isEmpty()) {
                            currentEnrollment = validEnrollments.stream()
                                    .max(Comparator.comparing(e -> e.getEnrolledAt() != null ? e.getEnrolledAt() : java.time.LocalDateTime.MIN));
                        }

                        currentEnrollment.ifPresent(e -> model.addAttribute("currentCourseId", e.getCourse().getId()));
                    }
                }
            }
        }
        // Always provide categories for the navbar dropdown
        model.addAttribute("categories", categoryService.getAllCategories());
    }
}
