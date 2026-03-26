package com.skillhub.controller;

import com.skillhub.model.User;
import com.skillhub.model.Enrollment;
import com.skillhub.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private com.skillhub.service.PlacementService placementService;

    @Autowired
    private com.skillhub.service.CategoryService categoryService;

    @Autowired
    private com.skillhub.repository.CourseRepository courseRepository;

    @Autowired
    private com.skillhub.repository.LectureProgressRepository lectureProgressRepository;

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(required = false) String tab, 
                           @RequestParam(required = false) String category,
                           Model model, Principal principal) {
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

        // Calculate dynamic progress count strings (e.g., "1/5 completed")
        java.util.Map<Long, String> progressCountMap = new java.util.HashMap<>();
        for (Enrollment enrollment : enrollments) {
            long total = lectureProgressRepository.countTotalLecturesByCourse(enrollment.getCourse().getId());
            long completed = lectureProgressRepository.countCompletedLecturesByCourse(student.getId(), enrollment.getCourse().getId());
            
            if (total > 0 && completed == total) {
                progressCountMap.put(enrollment.getId(), "Completed \u2714\ufe0f");
            } else {
                progressCountMap.put(enrollment.getId(), completed + "/" + total + " completed");
            }
        }

        // Fetch certificates (courses where exam passed)
        List<Enrollment> certificates = enrollments.stream().filter(Enrollment::isExamPassed).collect(java.util.stream.Collectors.toList());

        // Placement Data
        if ("placement".equals(tab)) {
            model.addAttribute("categories", categoryService.getAllCategories());
            model.addAttribute("interviewQuestions", placementService.getAllInterviewQuestions());
            model.addAttribute("aptitudeQuestions", placementService.getRandomAptitudeQuestions(10));
            model.addAttribute("resume", placementService.getResumeByUser(student));
        }

        model.addAttribute("enrollments", enrollments);
        model.addAttribute("notifications", notifications);
        model.addAttribute("recommendedCourses", recommendedCourses);
        model.addAttribute("student", student);
        model.addAttribute("progressCountMap", progressCountMap);
        model.addAttribute("certificates", certificates);
        model.addAttribute("activeTab", tab != null ? tab : "courses");
        
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
