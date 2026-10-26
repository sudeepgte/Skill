  package com.skillhub.controller;

import com.skillhub.dto.DashboardAnalyticsDTO;
import com.skillhub.model.*;
import com.skillhub.repository.*;
import com.skillhub.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    @Autowired
    private UserService userService;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private LectureProgressRepository lectureProgressRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ExamAttemptRepository examAttemptRepository;

    @GetMapping("/student")
    public ResponseEntity<DashboardAnalyticsDTO> getStudentAnalytics(Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        User user = userService.findByEmail(principal.getName());

        DashboardAnalyticsDTO dto = new DashboardAnalyticsDTO();
        dto.setCurrentStreak(user.getStreakCount());
        dto.setTotalXP(user.getTotalXP());
        dto.setLevel(user.getLevel());

        // 1. Overall Completion
        List<Enrollment> enrollments = enrollmentRepository.findByStudent(user);
        double totalProgress = 0;
        for (Enrollment e : enrollments) {
            long total = lectureProgressRepository.countTotalLecturesByCourse(e.getCourse().getId());
            if (total > 0) {
                long completed = lectureProgressRepository.countCompletedLecturesByCourse(user.getId(), e.getCourse().getId());
                totalProgress += (double) completed / total;
            }
        }
        dto.setOverallCompletion(enrollments.isEmpty() ? 0 : (totalProgress / enrollments.size()) * 100);

        // 2. Category Expertise (Radar)
        List<Category> categories = categoryRepository.findAll();
        Map<String, Double> expertiseMap = new HashMap<>();
        for (Category cat : categories) {
            long completed = lectureProgressRepository.countCompletedLecturesByCategory(user.getId(), cat.getId());
            long total = cat.getCourses().stream()
                    .flatMap(c -> c.getSections().stream())
                    .flatMap(s -> s.getLectures().stream())
                    .count();
            expertiseMap.put(cat.getName(), total > 0 ? (completed * 100.0 / total) : 0.0);
        }
        dto.setCategoryExpertise(expertiseMap);

        // 3. Strengths and Weaknesses
        List<Map.Entry<String, Double>> sorted = expertiseMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toList());
        
        dto.setStrengths(sorted.stream().limit(3).map(Map.Entry::getKey).collect(Collectors.toList()));
        Collections.reverse(sorted);
        dto.setWeaknesses(sorted.stream().limit(3).map(Map.Entry::getKey).collect(Collectors.toList()));

        // 4. Learning Activity Timeline (Last 14 Days) - Zero Padded
        List<Object[]> activityData = lectureProgressRepository.findActivityData(user.getId());
        Map<String, Integer> activityMap = activityData.stream()
                .collect(Collectors.toMap(row -> row[0].toString(), row -> ((Long) row[1]).intValue()));

        Map<String, Integer> paddedActivity = new LinkedHashMap<>();
        java.time.LocalDate today = java.time.LocalDate.now();
        for (int i = 13; i >= 0; i--) {
            String dateStr = today.minusDays(i).toString();
            paddedActivity.put(dateStr, activityMap.getOrDefault(dateStr, 0));
        }
        dto.setWeeklyActivity(paddedActivity);

        return ResponseEntity.ok(dto);
    }
    
    @GetMapping("/vendor")
    public ResponseEntity<Map<String, Object>> getVendorAnalytics(
            @org.springframework.web.bind.annotation.RequestParam(value = "courseId", required = false) Long courseId,
            Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        String email = principal.getName();
        
        Map<String, Object> response = new HashMap<>();
        
        // 1. Course Distribution (Enrollments per Course)
        List<Enrollment> enrollments = enrollmentRepository.findByInstructorEmail(email);
        if (courseId != null) {
            enrollments = enrollments.stream().filter(e -> e.getCourse() != null && courseId.equals(e.getCourse().getId())).collect(Collectors.toList());
        }
        Map<String, Long> courseEnrollments = enrollments.stream()
                .filter(e -> e.getCourse() != null && e.getCourse().getTitle() != null)
                .collect(Collectors.groupingBy(e -> e.getCourse().getTitle(), Collectors.counting()));
        if (courseEnrollments.isEmpty()) {
            courseEnrollments.put("No enrollments yet", 0L);
        }
        response.put("coursePopularity", courseEnrollments);
        
        // 2. Exam Performance (Success Rate)
        List<ExamAttempt> attempts = examAttemptRepository.findByInstructorEmail(email);
        if (courseId != null) {
            attempts = attempts.stream().filter(a -> a.getCourse() != null && courseId.equals(a.getCourse().getId())).collect(Collectors.toList());
        }
        long passed = (attempts != null) ? attempts.stream().filter(ExamAttempt::isPassed).count() : 0;
        long failed = (attempts != null) ? (attempts.size() - passed) : 0;
        
        // Also check passed enrollments
        long enrollmentsPassed = enrollments.stream().filter(Enrollment::isExamPassed).count();
        long finalPassed = Math.max(passed, enrollmentsPassed);
        
        Map<String, Long> examStats = new HashMap<>();
        examStats.put("Passed", finalPassed);
        examStats.put("Failed", failed);
        response.put("examPerformance", examStats);
        
        // 3. Average Scores per Course
        Map<String, Double> avgScores = new LinkedHashMap<>();
        if (attempts != null && !attempts.isEmpty()) {
            avgScores = attempts.stream()
                    .filter(a -> a.getCourse() != null && a.getCourse().getTitle() != null)
                    .collect(Collectors.groupingBy(a -> a.getCourse().getTitle(), 
                            Collectors.averagingInt(ExamAttempt::getScore)));
        } else {
            for (Enrollment e : enrollments) {
                if (e.getCourse() != null && e.getCourse().getTitle() != null) {
                    avgScores.put(e.getCourse().getTitle(), e.isExamPassed() ? 85.0 : 0.0);
                }
            }
        }
        if (avgScores.isEmpty()) {
            avgScores.put("No score data", 0.0);
        }
        response.put("avgScores", avgScores);
        
        // 4. Enrollment Timeline (Last 30 Days)
        java.time.LocalDateTime thirtyDaysAgo = java.time.LocalDateTime.now().minusDays(30);
        Map<String, Long> timeline = enrollments.stream()
                .filter(e -> e.getEnrolledAt() != null && e.getEnrolledAt().isAfter(thirtyDaysAgo))
                .collect(Collectors.groupingBy(e -> e.getEnrolledAt().toLocalDate().toString(), 
                        TreeMap::new, Collectors.counting()));
        response.put("enrollmentTimeline", timeline);
        
        long totalStudents = enrollments.stream().filter(e -> e.getStudent() != null).map(e -> e.getStudent().getId()).distinct().count();
        long totalAttemptsCount = (attempts != null ? attempts.size() : 0) + (finalPassed > passed ? (finalPassed - passed) : 0);
        double passRate = (totalAttemptsCount > 0) ? ((double) finalPassed / totalAttemptsCount * 100.0) : (finalPassed > 0 ? 100.0 : 0.0);
        double overallAvg = (attempts != null && !attempts.isEmpty()) ? attempts.stream().mapToInt(ExamAttempt::getScore).average().orElse(0.0) : (finalPassed > 0 ? 85.0 : 0.0);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalStudents", totalStudents);
        stats.put("totalAttempts", totalAttemptsCount);
        stats.put("totalPassed", finalPassed);
        stats.put("passRate", String.format("%.1f%%", passRate));
        stats.put("avgScore", String.format("%.1f", overallAvg));

        response.put("totalStudents", totalStudents);
        response.put("totalExams", totalAttemptsCount);
        response.put("stats", stats);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin/revenue")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getAdminRevenue() {
        Map<String, Object> response = new HashMap<>();

        // 1. Total Revenue
        Double total = enrollmentRepository.getTotalRevenue();
        response.put("totalRevenue", total != null ? total : 0.0);

        // 2. Monthly Revenue (Current Year)
        int currentYear = java.time.LocalDate.now().getYear();
        List<Object[]> monthlyData = enrollmentRepository.getMonthlyRevenue(currentYear);
        
        Map<Integer, Double> monthlyMap = new HashMap<>();
        for (Object[] row : monthlyData) {
            monthlyMap.put(((Number) row[0]).intValue(), ((Number) row[1]).doubleValue());
        }

        List<String> months = Arrays.asList("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec");
        List<Double> monthlyValues = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            monthlyValues.add(monthlyMap.getOrDefault(i, 0.0));
        }
        
        Map<String, Object> monthlyRevenue = new HashMap<>();
        monthlyRevenue.put("labels", months);
        monthlyRevenue.put("data", monthlyValues);
        response.put("monthlyRevenue", monthlyRevenue);

        // 3. Yearly Revenue
        List<Object[]> yearlyData = enrollmentRepository.getYearlyRevenue();
        List<String> yearlyLabels = new ArrayList<>();
        List<Double> yearlyValues = new ArrayList<>();
        for (Object[] row : yearlyData) {
            yearlyLabels.add(row[0].toString());
            yearlyValues.add(((Number) row[1]).doubleValue());
        }
        
        Map<String, Object> yearlyRevenue = new HashMap<>();
        yearlyRevenue.put("labels", yearlyLabels);
        yearlyRevenue.put("data", yearlyValues);
        response.put("yearlyRevenue", yearlyRevenue);

        return ResponseEntity.ok(response);
    }
}
