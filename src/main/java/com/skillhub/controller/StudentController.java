package com.skillhub.controller;

import com.skillhub.model.User;
import com.skillhub.model.Enrollment;
import com.skillhub.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import java.security.Principal;
import java.util.List;

@Controller
@RequestMapping("/student")
public class StudentController {

    @Autowired
    private UserService userService;

    @Autowired
    private com.skillhub.repository.EnrollmentRepository enrollmentRepository;

    @Autowired
    private com.skillhub.repository.WishlistRepository wishlistRepository;

    @Autowired
    private com.skillhub.repository.NotificationRepository notificationRepository;

    @Autowired
    private com.skillhub.repository.CourseRepository courseRepository;

    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal) {
        String email = principal.getName();
        User student = userService.findByEmail(email);

        List<Enrollment> enrollments = enrollmentRepository.findByStudent(student);
        List<com.skillhub.model.Notification> notifications = notificationRepository.findByUserOrderByCreatedAtDesc(student);
        
        // Basic Recommendation: Fetch all published courses and exclude enrolled ones
        List<com.skillhub.model.Course> allCourses = courseRepository.findByIsPublishedTrue();
        List<com.skillhub.model.Course> enrolledCourses = enrollments.stream()
                .map(Enrollment::getCourse)
                .collect(java.util.stream.Collectors.toList());
        
        List<com.skillhub.model.Course> recommendedCourses = allCourses.stream()
                .filter(c -> !enrolledCourses.contains(c))
                .limit(4) // Show top 4
                .collect(java.util.stream.Collectors.toList());

        model.addAttribute("enrollments", enrollments);
        model.addAttribute("notifications", notifications);
        model.addAttribute("recommendedCourses", recommendedCourses);
        model.addAttribute("student", student);
        
        return "student/dashboard";
    }

    @GetMapping("/wishlist")
    public String wishlist(Model model, Principal principal) {
        String email = principal.getName();
        User student = userService.findByEmail(email);
        List<com.skillhub.model.Wishlist> wishlistItems = wishlistRepository.findByUser(student);
        model.addAttribute("wishlistItems", wishlistItems);
        return "student/wishlist";
    }
}
