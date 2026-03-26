package com.skillhub.controller;

import com.skillhub.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AnalyticsController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @GetMapping("/stats")
    public String getPremiumAnalytics(Model model) {
        // 1. Basic Counts
        model.addAttribute("totalUsers", userRepository.count());
        model.addAttribute("totalStudents", userRepository.countByRole("STUDENT"));
        model.addAttribute("totalInstructors", userRepository.countByRole("INSTRUCTOR"));
        model.addAttribute("totalCourses", courseRepository.count());
        model.addAttribute("totalEnrollments", enrollmentRepository.count());

        // 2. User Activity (Active in last 30 days)
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        long activeUsers = userRepository.countByLastLoginDateAfter(thirtyDaysAgo);
        long totalUsers = userRepository.count();
        model.addAttribute("activeUsers", activeUsers);
        model.addAttribute("inactiveUsers", totalUsers - activeUsers);

        // 3. Enrollment Trends (Last 7 Days)
        Map<String, Long> enrollmentTrends = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            LocalDateTime dayStart = LocalDateTime.now().minusDays(i).withHour(0).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime dayEnd = dayStart.plusDays(1);
            long count = enrollmentRepository.findAll().stream()
                    .filter(e -> e.getEnrolledAt().isAfter(dayStart) && e.getEnrolledAt().isBefore(dayEnd))
                    .count();
            String label = dayStart.getDayOfWeek().name().substring(0, 3);
            enrollmentTrends.put(label, count);
        }
        model.addAttribute("enrollmentLabels", enrollmentTrends.keySet());
        model.addAttribute("enrollmentValues", enrollmentTrends.values());

        // 4. Category Popularity
        Map<String, Long> categoryStats = enrollmentRepository.findAll().stream()
                .filter(e -> e.getCourse() != null && e.getCourse().getCategory() != null)
                .collect(Collectors.groupingBy(e -> e.getCourse().getCategory().getName(), Collectors.counting()));
        
        // Ensure at least empty map if no enrollments
        model.addAttribute("categoryLabels", categoryStats.keySet());
        model.addAttribute("categoryValues", categoryStats.values());

        return "admin/analytics";
    }
}
