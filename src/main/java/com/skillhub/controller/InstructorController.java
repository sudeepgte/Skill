package com.skillhub.controller;

import com.skillhub.model.*;
import com.skillhub.repository.*;
import com.skillhub.service.CategoryService;
import com.skillhub.service.CourseService;
import com.skillhub.service.SubscriptionService;
import com.skillhub.dto.CourseUsageDto;
import com.skillhub.service.GeminiService;
import com.skillhub.service.FileStorageService;
import com.skillhub.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.function.Predicate;

@Controller
@RequestMapping("/vendor")
public class InstructorController {

    @Autowired
    private CourseService courseService;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private UserService userService;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private LectureProgressRepository lectureProgressRepository;

    @Autowired
    private CourseQuestionRepository courseQuestionRepository;

    @Autowired
    private CourseAnswerRepository courseAnswerRepository;

    @Autowired
    private ExamAttemptRepository examAttemptRepository;

    @Autowired
    private CourseResourceRepository courseResourceRepository;

    @Autowired
    private CourseSectionRepository courseSectionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private GeminiService geminiService;

    @Autowired
    private ExamQuestionRepository examQuestionRepository;

    @Autowired
    private CourseExamRepository courseExamRepository;

    // Helper method to enforce exam ownership validation
    private CourseExam getOwnedCourseExamOrThrow(Long examId, String email) {
        CourseExam exam = courseExamRepository.findById(examId)
                .orElseThrow(() -> new NoSuchElementException("Exam not found with id: " + examId));
        getOwnedCourseOrThrow(exam.getCourse().getId(), email);
        return exam;
    }

    // Helper method to enforce ownership validation
    private Course getOwnedCourseOrThrow(Long courseId, String email) {
        Course course = courseService.getCourseById(courseId);
        if (course == null) {
            throw new NoSuchElementException("Course not found with id: " + courseId);
        }
        User user = userService.findByEmail(email);
        if (user != null && ("ADMIN".equalsIgnoreCase(user.getRole()) || "ROLE_ADMIN".equalsIgnoreCase(user.getRole()))) {
            return course;
        }
        if (course.getInstructor() == null || !course.getInstructor().getEmail().equals(email)) {
            throw new AccessDeniedException("Unauthorized: You do not own this course");
        }
        return course;
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(value = "tab", defaultValue = "courses") String tab,
            @RequestParam(value = "status", required = false) String statusFilter,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            Model model, Principal principal) {
        String email = principal.getName();
        User instructor = userService.findByEmail(email);
        List<Course> allInstructorCourses = courseService.getCoursesByInstructor(email);

        List<Course> displayedCourses = allInstructorCourses;
        if (statusFilter != null && !statusFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(statusFilter)) {
            try {
                CourseStatus cs = CourseStatus.valueOf(statusFilter.toUpperCase());
                displayedCourses = new ArrayList<>();
                for (Course c : allInstructorCourses) {
                    if (c.getStatus() == cs) {
                        displayedCourses.add(c);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        long totalStudents = enrollmentRepository.countByInstructorEmail(email);
        Long viewsObj = courseService.sumViewsByInstructorEmail(email);
        long totalViews = viewsObj != null ? viewsObj : 0L;
        long totalExamsPassed = enrollmentRepository.countExamsPassedByInstructorEmail(email);

        double totalRating = 0.0;
        int totalReviews = 0;

        for (Course course : allInstructorCourses) {
            if (course.getEnrollments() != null) {
                totalStudents += course.getEnrollments().size();
            }
            if (course.getViewCount() != null) {
                totalViews += course.getViewCount();
            }
            // Populate ratings
            Double avg = reviewRepository.getAverageRatingByCourseId(course.getId()).orElse(0.0);
            Integer count = reviewRepository.countByCourseId(course.getId());

            course.setAverageRating(Math.round(avg * 10.0) / 10.0);
            course.setReviewCount(count);

            totalRating += avg * count;
            totalReviews += count;

            if (course.getEnrollments() != null) {
                for (Enrollment e : course.getEnrollments()) {
                    if (e.isExamPassed()) {
                        totalExamsPassed++;
                    }
                }
            }
        }

        double averageRating = (totalReviews > 0) ? totalRating / totalReviews : 0.0;
        List<CourseQuestion> questionsList = courseQuestionRepository.findByInstructorEmail(email);

        model.addAttribute("instructor", instructor);
        model.addAttribute("vendor", instructor);
        model.addAttribute("user", instructor);
        model.addAttribute("courses", displayedCourses);
        model.addAttribute("allCourses", allInstructorCourses);
        model.addAttribute("totalStudents", totalStudents);
        model.addAttribute("totalViews", totalViews);
        model.addAttribute("averageRating", String.format("%.1f", averageRating));
        model.addAttribute("totalReviews", totalReviews);
        model.addAttribute("totalQuestions", questionsList.size());
        model.addAttribute("totalExamsPassed", totalExamsPassed);
        model.addAttribute("activeTab", tab);
        model.addAttribute("statusFilter", statusFilter != null ? statusFilter : "ALL");

        if ("communications".equals(tab) || "messages".equals(tab)) {
            model.addAttribute("reviews", reviewRepository.findByInstructorEmail(email));
            model.addAttribute("questions", questionsList);
            List<User> students = userService.getAllUsers().stream()
                    .filter(u -> !"INSTRUCTOR".equals(u.getRole()) && !"ADMIN".equals(u.getRole()))
                    .toList();
            model.addAttribute("chatStudents", students);
        } else if ("reviews".equals(tab)) {
            List<Review> allReviews = reviewRepository.findByInstructorEmail(email);
            int totalReviewsCount = allReviews.size();
            int pageSize = (size > 0) ? size : 10;
            int reviewsTotalPages = (int) Math.ceil((double) totalReviewsCount / pageSize);
            if (reviewsTotalPages < 1) reviewsTotalPages = 1;
            int revPage = page;
            if (revPage < 1) revPage = 1;
            if (revPage > reviewsTotalPages && totalReviewsCount > 0) revPage = reviewsTotalPages;
            int revStart = Math.min((revPage - 1) * pageSize, totalReviewsCount);
            int revEnd = Math.min(revStart + pageSize, totalReviewsCount);
            List<Review> pagedReviews = allReviews.subList(revStart, revEnd);

            List<Integer> reviewsPageNumbers = new ArrayList<>();
            for (int i = 1; i <= reviewsTotalPages; i++) {
                reviewsPageNumbers.add(i);
            }

            model.addAttribute("reviews", pagedReviews);
            model.addAttribute("reviewsCurrentPage", revPage);
            model.addAttribute("reviewsTotalPages", reviewsTotalPages);
            model.addAttribute("reviewsTotalItems", totalReviewsCount);
            model.addAttribute("reviewsPageSize", pageSize);
            model.addAttribute("reviewsStartItem", totalReviewsCount > 0 ? (revStart + 1) : 0);
            model.addAttribute("reviewsEndItem", revEnd);
            model.addAttribute("reviewsPageNumbers", reviewsPageNumbers);
        } else if ("questions".equals(tab)) {
            int totalQuestionsCount = questionsList.size();
            int pageSize = (size > 0) ? size : 10;
            int questionsTotalPages = (int) Math.ceil((double) totalQuestionsCount / pageSize);
            if (questionsTotalPages < 1) questionsTotalPages = 1;
            int qPage = page;
            if (qPage < 1) qPage = 1;
            if (qPage > questionsTotalPages && totalQuestionsCount > 0) qPage = questionsTotalPages;
            int qStart = Math.min((qPage - 1) * pageSize, totalQuestionsCount);
            int qEnd = Math.min(qStart + pageSize, totalQuestionsCount);
            List<CourseQuestion> pagedQuestions = questionsList.subList(qStart, qEnd);

            List<Integer> questionsPageNumbers = new ArrayList<>();
            for (int i = 1; i <= questionsTotalPages; i++) {
                questionsPageNumbers.add(i);
            }

            model.addAttribute("questions", pagedQuestions);
            model.addAttribute("questionsCurrentPage", qPage);
            model.addAttribute("questionsTotalPages", questionsTotalPages);
            model.addAttribute("questionsTotalItems", totalQuestionsCount);
            model.addAttribute("questionsPageSize", pageSize);
            model.addAttribute("questionsStartItem", totalQuestionsCount > 0 ? (qStart + 1) : 0);
            model.addAttribute("questionsEndItem", qEnd);
            model.addAttribute("questionsPageNumbers", questionsPageNumbers);
        } else if ("students".equals(tab)) {
            List<Enrollment> allEnrollments = enrollmentRepository.findByInstructorEmail(email);
            for (Enrollment enrollment : allEnrollments) {
                long total = lectureProgressRepository.countTotalLecturesByCourse(enrollment.getCourse().getId());
                double lectureBasedPct = 0.0;
                if (total > 0) {
                    long completed = lectureProgressRepository.countCompletedLecturesByCourse(
                            enrollment.getStudent().getId(), enrollment.getCourse().getId());
                    lectureBasedPct = Math.min(100.0, (double) completed / total * 100.0);
                }
                double storedPct = enrollment.getProgressPercentage();
                enrollment.setProgressPercentage(Math.max(lectureBasedPct, storedPct));
            }

            int totalStudentsCount = allEnrollments.size();
            int pageSize = (size > 0) ? size : 10;
            int studentsTotalPages = (int) Math.ceil((double) totalStudentsCount / pageSize);
            if (studentsTotalPages < 1) studentsTotalPages = 1;
            int sPage = page;
            if (sPage < 1) sPage = 1;
            if (sPage > studentsTotalPages && totalStudentsCount > 0) sPage = studentsTotalPages;
            int sStart = Math.min((sPage - 1) * pageSize, totalStudentsCount);
            int sEnd = Math.min(sStart + pageSize, totalStudentsCount);
            List<Enrollment> pagedEnrollments = (totalStudentsCount > 0) ? allEnrollments.subList(sStart, sEnd) : Collections.emptyList();

            List<Integer> studentsPageNumbers = new ArrayList<>();
            for (int i = 1; i <= studentsTotalPages; i++) {
                studentsPageNumbers.add(i);
            }

            model.addAttribute("enrollments", pagedEnrollments);
            model.addAttribute("allEnrollments", allEnrollments);
            model.addAttribute("studentsCurrentPage", sPage);
            model.addAttribute("studentsTotalPages", studentsTotalPages);
            model.addAttribute("studentsTotalItems", totalStudentsCount);
            model.addAttribute("studentsPageSize", pageSize);
            model.addAttribute("studentsStartItem", totalStudentsCount > 0 ? (sStart + 1) : 0);
            model.addAttribute("studentsEndItem", sEnd);
            model.addAttribute("studentsPageNumbers", studentsPageNumbers);
        } else if ("analytics".equals(tab)) {
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");
            List<Map<String, Object>> assessmentRecords = new ArrayList<>();

            // 1. Fetch real student exam attempts for this instructor's courses
            List<ExamAttempt> attempts = examAttemptRepository.findByInstructorEmail(email);
            if (attempts != null) {
                attempts.sort(Comparator.comparing(ExamAttempt::getAttemptDate, Comparator.nullsLast(Comparator.reverseOrder())));
                for (ExamAttempt a : attempts) {
                    Map<String, Object> rec = new HashMap<>();
                    rec.put("id", a.getId());
                    rec.put("studentName", (a.getUser() != null && a.getUser().getName() != null) ? a.getUser().getName() : "Student");
                    rec.put("studentEmail", (a.getUser() != null && a.getUser().getEmail() != null) ? a.getUser().getEmail() : "");
                    rec.put("courseTitle", (a.getCourse() != null) ? a.getCourse().getTitle() : "Course");
                    rec.put("examTitle", (a.getExam() != null && a.getExam().getTitle() != null) ? a.getExam().getTitle() : "Final Assessment");
                    rec.put("score", a.getScore());
                    rec.put("isPassed", a.isPassed());
                    rec.put("attemptDate", a.getAttemptDate() != null ? a.getAttemptDate().format(dtf) : "N/A");
                    rec.put("submissionDetails", a.getSubmissionDetails() != null ? a.getSubmissionDetails() : "[]");
                    assessmentRecords.add(rec);
                }
            }

            // 2. Also incorporate any passed enrollments not already in attempts
            List<Enrollment> allEnrollments = enrollmentRepository.findByInstructorEmail(email);
            List<Enrollment> passedExams = new ArrayList<>();
            for (Enrollment e : allEnrollments) {
                if (e.isExamPassed()) {
                    passedExams.add(e);
                    boolean alreadyInAttempts = (attempts != null) && attempts.stream().anyMatch(
                            a -> a.getUser() != null && e.getStudent() != null && a.getUser().getId().equals(e.getStudent().getId())
                                    && a.getCourse() != null && e.getCourse() != null && a.getCourse().getId().equals(e.getCourse().getId())
                    );
                    if (!alreadyInAttempts) {
                        Map<String, Object> rec = new HashMap<>();
                        rec.put("id", e.getId());
                        rec.put("studentName", (e.getStudent() != null && e.getStudent().getName() != null) ? e.getStudent().getName() : "Student");
                        rec.put("studentEmail", (e.getStudent() != null && e.getStudent().getEmail() != null) ? e.getStudent().getEmail() : "");
                        rec.put("courseTitle", (e.getCourse() != null) ? e.getCourse().getTitle() : "Course");
                        rec.put("examTitle", "Course Assessment");
                        rec.put("score", 100);
                        rec.put("isPassed", true);
                        rec.put("attemptDate", e.getCertificateDate() != null ? e.getCertificateDate().format(dtf) : (e.getEnrolledAt() != null ? e.getEnrolledAt().format(dtf) : "N/A"));
                        rec.put("submissionDetails", "[]");
                        assessmentRecords.add(rec);
                    }
                }
            }

            // Summary metrics for KPI Cards
            int totalAssessmentCount = assessmentRecords.size();
            long passedCount = assessmentRecords.stream().filter(r -> Boolean.TRUE.equals(r.get("isPassed"))).count();
            double passRate = (totalAssessmentCount > 0) ? ((double) passedCount / totalAssessmentCount * 100.0) : (totalExamsPassed > 0 ? 100.0 : 0.0);
            double avgScore = (totalAssessmentCount > 0) ? assessmentRecords.stream().mapToInt(r -> (Integer) r.get("score")).average().orElse(0.0) : (totalExamsPassed > 0 ? 85.0 : 0.0);

            // Pagination for assessment records table
            int pageSize = (size > 0) ? size : 10;
            int analyticsTotalPages = (int) Math.ceil((double) totalAssessmentCount / pageSize);
            if (analyticsTotalPages < 1) analyticsTotalPages = 1;
            int aPage = page;
            if (aPage < 1) aPage = 1;
            if (aPage > analyticsTotalPages && totalAssessmentCount > 0) aPage = analyticsTotalPages;
            int aStart = Math.min((aPage - 1) * pageSize, totalAssessmentCount);
            int aEnd = Math.min(aStart + pageSize, totalAssessmentCount);
            List<Map<String, Object>> pagedAssessments = (totalAssessmentCount > 0) ? assessmentRecords.subList(aStart, aEnd) : Collections.emptyList();

            List<Integer> analyticsPageNumbers = new ArrayList<>();
            for (int i = 1; i <= analyticsTotalPages; i++) {
                analyticsPageNumbers.add(i);
            }

            model.addAttribute("passedExams", passedExams);
            model.addAttribute("assessments", pagedAssessments);
            model.addAttribute("analyticsCurrentPage", aPage);
            model.addAttribute("analyticsTotalPages", analyticsTotalPages);
            model.addAttribute("analyticsTotalItems", totalAssessmentCount);
            model.addAttribute("analyticsPageSize", pageSize);
            model.addAttribute("analyticsStartItem", totalAssessmentCount > 0 ? (aStart + 1) : 0);
            model.addAttribute("analyticsEndItem", aEnd);
            model.addAttribute("analyticsPageNumbers", analyticsPageNumbers);

            model.addAttribute("analyticsTotalAttempts", totalAssessmentCount);
            model.addAttribute("analyticsTotalPassed", passedCount);
            model.addAttribute("analyticsPassRate", String.format("%.1f%%", passRate));
            model.addAttribute("analyticsAvgScore", String.format("%.1f", avgScore));
            model.addAttribute("allInstructorCourses", allInstructorCourses);
        }

        return "instructor/dashboard";
    }

    /** Dedicated Page: View enrolled students for a course with pagination */
    @GetMapping("/courses/{id}/students")
    public String viewCourseStudents(@PathVariable("id") Long id,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            Model model, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }
        Course course = getOwnedCourseOrThrow(id, principal.getName());
        User instructor = userService.findByEmail(principal.getName());
        List<Enrollment> allEnrollments = course.getEnrollments() != null ? new ArrayList<>(course.getEnrollments()) : new ArrayList<>();

        // Ensure updated lecture-based progress is calculated
        for (Enrollment enrollment : allEnrollments) {
            if (enrollment.getStudent() != null) {
                long total = lectureProgressRepository.countTotalLecturesByCourse(course.getId());
                double lectureBasedPct = 0.0;
                if (total > 0) {
                    long completed = lectureProgressRepository.countCompletedLecturesByCourse(
                            enrollment.getStudent().getId(), course.getId());
                    lectureBasedPct = Math.min(100.0, (double) completed / total * 100.0);
                }
                double storedPct = enrollment.getProgressPercentage();
                enrollment.setProgressPercentage(Math.max(lectureBasedPct, storedPct));
            }
        }

        int totalCount = allEnrollments.size();
        int pageSize = (size > 0) ? size : 10;
        int totalPages = Math.max(1, (int) Math.ceil((double) totalCount / pageSize));
        int curPage = Math.max(1, Math.min(page, totalPages));
        int startIdx = Math.min((curPage - 1) * pageSize, totalCount);
        int endIdx = Math.min(startIdx + pageSize, totalCount);
        List<Enrollment> pagedEnrollments = (totalCount > 0) ? allEnrollments.subList(startIdx, endIdx) : Collections.emptyList();
        List<Integer> pageNumbers = new ArrayList<>();
        for (int i = 1; i <= totalPages; i++) {
            pageNumbers.add(i);
        }

        model.addAttribute("course", course);
        model.addAttribute("instructor", instructor);
        model.addAttribute("vendor", instructor);
        model.addAttribute("user", instructor);
        model.addAttribute("enrollments", pagedEnrollments);
        model.addAttribute("allEnrollments", allEnrollments);
        model.addAttribute("totalStudents", totalCount);
        model.addAttribute("currentPage", curPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("startItem", totalCount > 0 ? (startIdx + 1) : 0);
        model.addAttribute("endItem", endIdx);
        model.addAttribute("pageNumbers", pageNumbers);
        return "instructor/course_students";
    }

    /** REST endpoint â€“ returns live progress for all enrolled students (JSON) */
    @GetMapping("/api/courses/{id}/students")
    @ResponseBody
    public ResponseEntity<?> getCourseStudents(@PathVariable("id") Long id, Principal principal) {
        if (principal == null)
            return ResponseEntity.status(401).build();
        Course course = getOwnedCourseOrThrow(id, principal.getName());
        List<Map<String, Object>> result = new ArrayList<>();
        java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy");
        if (course.getEnrollments() != null) {
            for (Enrollment e : course.getEnrollments()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", e.getId());
                row.put("name", e.getStudent() != null ? e.getStudent().getName() : "Unknown");
                row.put("email", e.getStudent() != null ? e.getStudent().getEmail() : "N/A");
                row.put("enrolledAt", e.getEnrolledAt() != null ? e.getEnrolledAt().format(dtf) : "N/A");
                row.put("progress", Math.round(e.getProgressPercentage()));
                row.put("examPassed", Boolean.TRUE.equals(e.isExamPassed()));
                result.add(row);
            }
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/students-progress")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getStudentsProgress(Principal principal) {
        if (principal == null)
            return ResponseEntity.status(401).build();
        String email = principal.getName();
        List<Enrollment> enrollments = enrollmentRepository.findByInstructorEmail(email);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Enrollment enrollment : enrollments) {
            long total = lectureProgressRepository.countTotalLecturesByCourse(enrollment.getCourse().getId());
            double lectureBasedPct = 0.0;
            if (total > 0) {
                long completed = lectureProgressRepository.countCompletedLecturesByCourse(
                        enrollment.getStudent().getId(), enrollment.getCourse().getId());
                lectureBasedPct = Math.min(100.0, (double) completed / total * 100.0);
            }
            double pct = Math.max(lectureBasedPct, enrollment.getProgressPercentage());
            Map<String, Object> row = new HashMap<>();
            row.put("enrollmentId", enrollment.getId());
            row.put("pct", Math.round(pct));
            result.add(row);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/dashboard-overview")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getDashboardOverview(Principal principal) {
        if (principal == null)
            return ResponseEntity.status(401).build();
        String email = principal.getName();
        List<Course> courses = courseService.getCoursesByInstructor(email);

        long totalEnrolled = enrollmentRepository.countByInstructorEmail(email);
        Long viewsObj = courseService.sumViewsByInstructorEmail(email);
        long totalViews = viewsObj != null ? viewsObj : 0L;
        long totalExamsPassed = enrollmentRepository.countExamsPassedByInstructorEmail(email);

        double totalRating = 0.0;
        int totalReviews = 0;

        for (Course course : courses) {
            totalEnrolled += (course.getEnrollments() != null ? course.getEnrollments().size() : 0);
            totalViews += (course.getViewCount() != null ? course.getViewCount() : 0);
            Double avg = reviewRepository.getAverageRatingByCourseId(course.getId()).orElse(0.0);
            Integer count = reviewRepository.countByCourseId(course.getId());
            totalRating += avg * count;
            totalReviews += count;

            if (course.getEnrollments() != null) {
                for (Enrollment e : course.getEnrollments()) {
                    if (e.isExamPassed()) {
                        totalExamsPassed++;
                    }
                }
            }
        }

        int totalQuestions = courseQuestionRepository.findByInstructorEmail(email).size();
        double avgRating = (totalReviews > 0) ? totalRating / totalReviews : 0.0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEnrolled", totalEnrolled);
        stats.put("totalViews", totalViews);
        stats.put("averageRating", String.format("%.1f", avgRating));
        stats.put("totalReviews", totalReviews);
        stats.put("totalQuestions", totalQuestions);
        stats.put("totalExamsPassed", totalExamsPassed);

        return ResponseEntity.ok(stats);
    }

    @GetMapping({"/api/analytics", "/api/analytics/vendor"})
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getVendorAnalytics(
            @RequestParam(value = "courseId", required = false) Long courseId,
            Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String email = principal.getName();
        List<Course> courses = courseService.getCoursesByInstructor(email);
        if (courseId != null) {
            courses = courses.stream().filter(c -> courseId.equals(c.getId())).collect(Collectors.toList());
        }

        // 1. Course Popularity
        Map<String, Integer> coursePopularity = new LinkedHashMap<>();
        for (Course c : courses) {
            int count = (c.getEnrollments() != null) ? c.getEnrollments().size() : 0;
            coursePopularity.put(c.getTitle(), count);
        }
        if (coursePopularity.isEmpty()) {
            coursePopularity.put("No courses yet", 0);
        }

        // 2. Exam Performance
        List<ExamAttempt> attempts = examAttemptRepository.findByInstructorEmail(email);
        if (courseId != null) {
            attempts = attempts.stream().filter(a -> a.getCourse() != null && courseId.equals(a.getCourse().getId())).collect(Collectors.toList());
        }
        int passedAttempts = 0;
        int failedAttempts = 0;
        if (attempts != null) {
            for (ExamAttempt a : attempts) {
                if (a.isPassed()) {
                    passedAttempts++;
                } else {
                    failedAttempts++;
                }
            }
        }
        List<Enrollment> enrollments = enrollmentRepository.findByInstructorEmail(email);
        if (courseId != null) {
            enrollments = enrollments.stream().filter(e -> e.getCourse() != null && courseId.equals(e.getCourse().getId())).collect(Collectors.toList());
        }
        int totalEnrollmentsPassed = 0;
        if (enrollments != null) {
            for (Enrollment e : enrollments) {
                if (e.isExamPassed()) {
                    totalEnrollmentsPassed++;
                }
            }
        }
        int finalPassed = Math.max(passedAttempts, totalEnrollmentsPassed);
        int finalFailed = failedAttempts;
        Map<String, Integer> examPerformance = new LinkedHashMap<>();
        examPerformance.put("Passed", finalPassed);
        examPerformance.put("Failed / Retake Required", finalFailed);

        // 3. Enrollment Timeline (30 days)
        Map<String, Integer> enrollmentTimeline = new LinkedHashMap<>();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("MMM dd");
        java.time.LocalDate today = java.time.LocalDate.now();
        for (int i = 29; i >= 0; i--) {
            java.time.LocalDate d = today.minusDays(i);
            enrollmentTimeline.put(d.format(dtf), 0);
        }
        if (enrollments != null) {
            for (Enrollment e : enrollments) {
                if (e.getEnrolledAt() != null) {
                    String dKey = e.getEnrolledAt().toLocalDate().format(dtf);
                    if (enrollmentTimeline.containsKey(dKey)) {
                        enrollmentTimeline.put(dKey, enrollmentTimeline.get(dKey) + 1);
                    }
                }
            }
        }

        // 4. Avg Scores by Course
        Map<String, Double> avgScores = new LinkedHashMap<>();
        for (Course c : courses) {
            List<ExamAttempt> cAttempts = examAttemptRepository.findByCourse(c);
            if (cAttempts != null && !cAttempts.isEmpty()) {
                double avg = cAttempts.stream().mapToInt(ExamAttempt::getScore).average().orElse(0.0);
                avgScores.put(c.getTitle(), Math.round(avg * 10.0) / 10.0);
            } else {
                boolean hasPassed = (c.getEnrollments() != null) && c.getEnrollments().stream().anyMatch(Enrollment::isExamPassed);
                avgScores.put(c.getTitle(), hasPassed ? 80.0 : 0.0);
            }
        }
        if (avgScores.isEmpty()) {
            avgScores.put("No data", 0.0);
        }

        // 5. Summary KPI Stats
        int totalAttemptsCount = (attempts != null ? attempts.size() : 0) + (finalPassed > passedAttempts ? (finalPassed - passedAttempts) : 0);
        double passRate = (totalAttemptsCount > 0) ? ((double) finalPassed / totalAttemptsCount * 100.0) : (finalPassed > 0 ? 100.0 : 0.0);
        double overallAvgScore = (attempts != null && !attempts.isEmpty()) ? attempts.stream().mapToInt(ExamAttempt::getScore).average().orElse(0.0) : (finalPassed > 0 ? 85.0 : 0.0);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalStudents", enrollments != null ? enrollments.size() : 0);
        stats.put("totalAttempts", totalAttemptsCount);
        stats.put("totalPassed", finalPassed);
        stats.put("passRate", String.format("%.1f%%", passRate));
        stats.put("avgScore", String.format("%.1f", overallAvgScore));

        Map<String, Object> response = new HashMap<>();
        response.put("coursePopularity", coursePopularity);
        response.put("examPerformance", examPerformance);
        response.put("enrollmentTimeline", enrollmentTimeline);
        response.put("avgScores", avgScores);
        response.put("stats", stats);

        return ResponseEntity.ok(response);
    }

    // Dynamic categories subcategories & fields API
    @GetMapping("/api/categories/{name}/subcategories")
    @ResponseBody
    public ResponseEntity<List<String>> getCategorySubcategories(@PathVariable("name") String name) {
        return ResponseEntity.ok(categoryService.getSubcategoriesForCategory(name));
    }

    @GetMapping("/api/categories/{name}/fields")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getCategoryFields(@PathVariable("name") String name) {
        return ResponseEntity.ok(categoryService.getCategoryFieldDefinitions(name));
    }

    @GetMapping("/courses/create")
    public String createCourseForm(Model model, Principal principal) {
        Course course = new Course();
        List<Category> allCategories = categoryService.getAllCategories();
        Category selectedCategory = null;

        if (principal != null) {
            User instructor = userService.findByEmail(principal.getName());
            if (instructor != null) {
                model.addAttribute("instructor", instructor);
                model.addAttribute("vendor", instructor);
                model.addAttribute("user", instructor);
                String expertise = instructor.getSkills() != null && !instructor.getSkills().isBlank()
                        ? instructor.getSkills()
                        : instructor.getExpertise();
                if (expertise != null && !expertise.isBlank()) {
                    for (Category cat : allCategories) {
                        if (cat.getName() != null && (cat.getName().equalsIgnoreCase(expertise.trim())
                                || cat.getName().toLowerCase().contains(expertise.trim().toLowerCase())
                                || expertise.trim().toLowerCase().contains(cat.getName().toLowerCase()))) {
                            selectedCategory = cat;
                            break;
                        }
                    }
                }
            }
        }

        if (selectedCategory != null) {
            course.setCategory(selectedCategory);
            model.addAttribute("defaultCategoryId", selectedCategory.getId());
            model.addAttribute("defaultCategoryName", selectedCategory.getName());
        } else {
            course.setCategory(new Category());
            model.addAttribute("defaultCategoryId", "");
            model.addAttribute("defaultCategoryName", "");
        }
        course.setPrice(0.0);
        course.setStatus(CourseStatus.DRAFT);
        course.setFormat("Video Course");
        course.setTeachingMethod("Self-paced");
        course.setLevel("Beginner");
        course.setLanguage("English");
        course.setAccessDuration("Lifetime Access");

        model.addAttribute("course", course);
        if (course.getCategory() != null && course.getCategory().getId() != null) {
            model.addAttribute("defaultCategoryId", course.getCategory().getId());
            model.addAttribute("defaultCategoryName", course.getCategory().getName());
        } else {
            model.addAttribute("defaultCategoryId", "");
            model.addAttribute("defaultCategoryName", "");
        }
        model.addAttribute("categories", categoryService.getAllCategories());
        model.addAttribute("subcategoriesMap", categoryService.getCategorySubcategoriesMap());
        model.addAttribute("categoryFieldsMap", categoryService.getAllCategoryFieldDefinitions());
        model.addAttribute("categoryDataMap", new HashMap<String, String>());
        if (principal != null) {
            User instructor = userService.findByEmail(principal.getName());
            if (instructor != null) {
                com.skillhub.dto.CourseUsageDto usage = subscriptionService.getCourseUsage(instructor);
                model.addAttribute("subscriptionUsage", usage);
                model.addAttribute("canCreateCourse", !usage.isLimitReached());
            }
        }
        model.addAttribute("isEdit", false);
        return "instructor/create_course";
    }

    @GetMapping("/courses/{id}/edit")
    public String editCourse(@PathVariable("id") Long id, Model model, Principal principal) {
        String email = principal.getName();
        Course course = getOwnedCourseOrThrow(id, email);
        User instructor = userService.findByEmail(email);
        if (instructor != null) {
            model.addAttribute("instructor", instructor);
            model.addAttribute("vendor", instructor);
            model.addAttribute("user", instructor);
        }

        model.addAttribute("course", course);
        if (course.getCategory() != null && course.getCategory().getId() != null) {
            model.addAttribute("defaultCategoryId", course.getCategory().getId());
            model.addAttribute("defaultCategoryName", course.getCategory().getName());
        } else {
            model.addAttribute("defaultCategoryId", "");
            model.addAttribute("defaultCategoryName", "");
        }
        model.addAttribute("categories", categoryService.getAllCategories());
        model.addAttribute("subcategoriesMap", categoryService.getCategorySubcategoriesMap());
        model.addAttribute("categoryFieldsMap", categoryService.getAllCategoryFieldDefinitions());
        model.addAttribute("categoryDataMap", course.getCategoryDataMap());
        model.addAttribute("isEdit", true);
        return "instructor/create_course"; // Unified dynamic wizard supporting both create & edit
    }

    @PostMapping("/courses/save")
    public String saveCourse(@ModelAttribute Course course,
            @RequestParam(value = "action", defaultValue = "draft") String action,
            @RequestParam(value = "thumbnail", required = false) MultipartFile thumbnail,
            @RequestParam(value = "promoVideo", required = false) MultipartFile promoVideo,
            @RequestParam(value = "resourceFiles", required = false) MultipartFile[] resourceFiles,
            @RequestParam(value = "resourceTitles", required = false) String[] resourceTitles,
            @RequestParam(value = "resourceTypes", required = false) String[] resourceTypes,
            @RequestParam(value = "resourceDescriptions", required = false) String[] resourceDescriptions,
            @RequestParam Map<String, String> allParams,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        String email = principal.getName();
        User instructor = userService.findByEmail(email);

        Course targetCourse;
        boolean isNew = (course.getId() == null);

        if (course.getPrice() != null && course.getPrice() < 0) {
            redirectAttributes.addFlashAttribute("error", "Course price cannot be negative.");
            return "redirect:/vendor/courses/create";
        }

        // Validate file types before processing
        if (thumbnail != null && !thumbnail.isEmpty()) {
            String mimeType = thumbnail.getContentType();
            if (mimeType == null || !mimeType.startsWith("image/")) {
                redirectAttributes.addFlashAttribute("error", "Thumbnail must be an image file.");
                return "redirect:/vendor/courses/create";
            }
        }
        if (promoVideo != null && !promoVideo.isEmpty()) {
            String mimeType = promoVideo.getContentType();
            if (mimeType == null || !mimeType.startsWith("video/")) {
                redirectAttributes.addFlashAttribute("error", "Promotional video must be a valid video file.");
                return "redirect:/vendor/courses/create";
            }
        }

        if (course.getId() == null) {
            course.setPublished(false);
            course.setStatus(CourseStatus.DRAFT);
        }
        if (isNew && !subscriptionService.canCreateCourse(instructor)) {
            redirectAttributes.addFlashAttribute("error", "You have reached your monthly course publishing limit under your current subscription. Please upgrade or renew your plan to publish more courses.");
            return "redirect:/vendor/subscription";
        }

        if (isNew) {
            targetCourse = course;
            targetCourse.setInstructor(instructor);
            targetCourse.setViewCount(0L);
        } else {
            // Edit mode: verify ownership
            targetCourse = getOwnedCourseOrThrow(course.getId(), email);
            // Copy editable fields
            targetCourse.setTitle(course.getTitle());
            targetCourse.setSubtitle(course.getSubtitle());
            targetCourse.setDescription(course.getDescription());
            targetCourse.setLongDescription(course.getLongDescription());
            targetCourse.setCategory(course.getCategory());
            targetCourse.setSubCategory(course.getSubCategory());
            targetCourse.setLevel(course.getLevel());
            targetCourse.setLanguage(course.getLanguage());
            targetCourse.setFormat(course.getFormat());
            targetCourse.setTeachingMethod(course.getTeachingMethod());
            targetCourse.setTags(course.getTags());
            targetCourse.setPrice(course.getPrice());
            targetCourse.setSalePrice(course.getSalePrice());
            targetCourse.setLearningObjectives(course.getLearningObjectives());
            targetCourse.setRequirements(course.getRequirements());
            targetCourse.setTargetAudience(course.getTargetAudience());
            targetCourse.setRequiredExperience(course.getRequiredExperience());
            targetCourse.setExpectedSkills(course.getExpectedSkills());
            targetCourse.setAgeRange(course.getAgeRange());
            targetCourse.setAccessibilityRequirements(course.getAccessibilityRequirements());
            targetCourse.setAccessDuration(course.getAccessDuration());
        }

        // Process thumbnail (only once on targetCourse)
        if (thumbnail != null && !thumbnail.isEmpty()) {
            targetCourse.setImageUrl(fileStorageService.store(thumbnail));
        } else if (isNew || targetCourse.getImageUrl() == null || targetCourse.getImageUrl().trim().isEmpty() || targetCourse.getImageUrl().contains("udemycdn.com")) {
            targetCourse.setImageUrl("/images/course-placeholder.jpg");
        }

        // Process promotional video (only once on targetCourse)
        if (promoVideo != null && !promoVideo.isEmpty()) {
            targetCourse.setVideoUrl(fileStorageService.store(promoVideo));
        }

        targetCourse.setLastUpdated(LocalDateTime.now().toLocalDate().toString());

        // Extract Category-Specific Fields from request
        Map<String, String> catDataMap = new HashMap<>();
        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("catData_")) {
                String cleanKey = key.substring("catData_".length());
                if (entry.getValue() != null && !entry.getValue().trim().isEmpty()) {
                    catDataMap.put(cleanKey, entry.getValue().trim());
                }
            }
        }
        targetCourse.setCategoryDataMap(catDataMap);

        // Validation rule: Must start with a letter for titles/descriptions if provided
        Predicate<String> startsWithLetter = s -> s != null && !s.trim().isEmpty() && Character.isLetter(s.trim().charAt(0));

        if (!startsWithLetter.test(targetCourse.getTitle())) {
            redirectAttributes.addFlashAttribute("error", "Course Title must start with a letter.");
            return isNew ? "redirect:/vendor/courses/create" : "redirect:/vendor/courses/" + targetCourse.getId() + "/edit";
        }

        if (targetCourse.getDescription() != null && !targetCourse.getDescription().trim().isEmpty() && !startsWithLetter.test(targetCourse.getDescription())) {
            redirectAttributes.addFlashAttribute("error", "Course Description must start with a letter.");
            return isNew ? "redirect:/vendor/courses/create" : "redirect:/vendor/courses/" + targetCourse.getId() + "/edit";
        }


        // Process Curriculum Videos and Validate Titles
        if (course.getSections() != null && !course.getSections().isEmpty()) {
            List<CourseSection> validSections = new ArrayList<>();
            for (CourseSection section : course.getSections()) {
                if (section.getTitle() != null && !section.getTitle().trim().isEmpty()) {
                    if (!startsWithLetter.test(section.getTitle())) {
                        redirectAttributes.addFlashAttribute("error", "Section titles must start with a letter.");
                        return isNew ? "redirect:/vendor/courses/create" : "redirect:/vendor/courses/" + targetCourse.getId() + "/edit";
                    }
                    section.setCourse(targetCourse);
                    if (section.getLectures() != null) {
                        List<Lecture> validLectures = new ArrayList<>();
                        for (Lecture lecture : section.getLectures()) {
                            if (lecture.getTitle() != null && !lecture.getTitle().trim().isEmpty()) {
                                if (!startsWithLetter.test(lecture.getTitle())) {
                                    redirectAttributes.addFlashAttribute("error", "Lecture titles must start with a letter.");
                                    return isNew ? "redirect:/vendor/courses/create" : "redirect:/vendor/courses/" + targetCourse.getId() + "/edit";
                                }
                                if (lecture.getVideoFile() != null && !lecture.getVideoFile().isEmpty()) {
                                    String lecMimeType = lecture.getVideoFile().getContentType();
                                    if (lecMimeType == null || !lecMimeType.startsWith("video/")) {
                                        redirectAttributes.addFlashAttribute("error", "Lecture video must be a valid video file.");
                                        return isNew ? "redirect:/vendor/courses/create" : "redirect:/vendor/courses/" + targetCourse.getId() + "/edit";
                                    }
                                    lecture.setVideoUrl(fileStorageService.store(lecture.getVideoFile()));
                                } else if ((lecture.getVideoUrl() == null || lecture.getVideoUrl().trim().isEmpty()) && (isNew || "submit".equalsIgnoreCase(action))) {
                                    redirectAttributes.addFlashAttribute("error", "Video File Upload (.mp4) is required for lecture: " + lecture.getTitle());
                                    return isNew ? "redirect:/vendor/courses/create" : "redirect:/vendor/courses/" + targetCourse.getId() + "/edit";
                                }
                                lecture.setSection(section);
                                validLectures.add(lecture);
                            }
                        }
                        section.setLectures(validLectures);
                    }
                    validSections.add(section);
                }
            }

            if (isNew) {
                targetCourse.setSections(validSections);
            } else {
                syncCourseCurriculum(targetCourse, validSections);
            }
        }

        // Process Downloadable Course Resources if uploaded
        if (resourceFiles != null && resourceFiles.length > 0) {
            for (int i = 0; i < resourceFiles.length; i++) {
                MultipartFile resFile = resourceFiles[i];
                if (resFile != null && !resFile.isEmpty()) {
                    String storedPath = fileStorageService.store(resFile);
                    String resTitle = (resourceTitles != null && resourceTitles.length > i && resourceTitles[i] != null && !resourceTitles[i].trim().isEmpty())
                            ? resourceTitles[i].trim() : resFile.getOriginalFilename();
                    String resType = (resourceTypes != null && resourceTypes.length > i && resourceTypes[i] != null && !resourceTypes[i].trim().isEmpty())
                            ? resourceTypes[i].trim() : "RESOURCE";
                    String resDesc = (resourceDescriptions != null && resourceDescriptions.length > i && resourceDescriptions[i] != null)
                            ? resourceDescriptions[i].trim() : "";

                    CourseResource cr = new CourseResource(resTitle, resDesc, storedPath, resType, resFile.getSize(), targetCourse);
                    targetCourse.addResource(cr);
                }
            }
        }

        // Action Decision: "submit" (submit for review) vs "draft" (save draft)
        if ("submit".equalsIgnoreCase(action)) {
            // Validate required fields for submission
            if (targetCourse.getDescription() == null || targetCourse.getDescription().trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Course description is required to submit for review.");
                return isNew ? "redirect:/vendor/courses/create" : "redirect:/vendor/courses/" + targetCourse.getId() + "/edit";
            }
            if (targetCourse.getCategory() == null || targetCourse.getCategory().getId() == null) {
                redirectAttributes.addFlashAttribute("error", "Please select a Category before submitting.");
                return isNew ? "redirect:/vendor/courses/create" : "redirect:/vendor/courses/" + targetCourse.getId() + "/edit";
            }

            targetCourse.setStatus(CourseStatus.PENDING_REVIEW);
            targetCourse.setPublished(false);
            targetCourse.setSubmittedAt(LocalDateTime.now());
            targetCourse.setRejectionReason(null); // Clear previous rejection reason upon resubmission
            courseService.saveCourse(targetCourse);
            redirectAttributes.addFlashAttribute("success", "Course '" + targetCourse.getTitle() + "' submitted for admin review successfully!");
        } else {
            // Save as Draft
            if (targetCourse.getStatus() != CourseStatus.PENDING_REVIEW && targetCourse.getStatus() != CourseStatus.PUBLISHED) {
                targetCourse.setStatus(CourseStatus.DRAFT);
            }
            targetCourse.setPublished(false);
            courseService.saveCourse(targetCourse);
            redirectAttributes.addFlashAttribute("success", "Course draft saved successfully!");
        }

        return "redirect:/vendor/dashboard?tab=courses";
    }

    @PostMapping("/courses/{id}/submit")
    public String submitCourseForReview(@PathVariable("id") Long id, Principal principal, RedirectAttributes redirectAttributes) {
        String email = principal.getName();
        Course course = getOwnedCourseOrThrow(id, email);
        if (course.getTitle() == null || course.getTitle().trim().isEmpty() ||
            course.getDescription() == null || course.getDescription().trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Course title and description are required before submitting.");
            return "redirect:/vendor/courses/" + id + "/edit";
        }

        courseService.submitCourseForReview(id);
        redirectAttributes.addFlashAttribute("success", "Course submitted for admin review!");
        return "redirect:/vendor/dashboard?tab=courses";
    }

    @PostMapping("/courses/{id}/status")
    @ResponseBody
    public ResponseEntity<?> updateCourseStatus(@PathVariable("id") Long id,
            @RequestParam("published") boolean published, Principal principal) {
        try {
            getOwnedCourseOrThrow(id, principal.getName());
            if (published) {
                courseService.publishCourse(id);
            } else {
                courseService.unpublishCourse(id);
            }
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }


    @PostMapping("/courses/{id}/delete")
    public String deleteCourse(@PathVariable("id") Long id, Principal principal,
            RedirectAttributes redirectAttributes) {
        try {
            getOwnedCourseOrThrow(id, principal.getName());
            courseService.deleteCourse(id);
            redirectAttributes.addFlashAttribute("success", "Course deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Unable to delete course: " + e.getMessage());
        }
        return "redirect:/vendor/dashboard?tab=courses";
    }

    @PostMapping("/courses/{id}/sections/add")
    public String addSection(@PathVariable("id") Long id, @RequestParam("title") String title,
            Principal principal, RedirectAttributes redirectAttributes) {
        getOwnedCourseOrThrow(id, principal.getName());
        courseService.addSectionToCourse(id, title);
        redirectAttributes.addFlashAttribute("success", "Section added successfully!");
        return "redirect:/vendor/courses/" + id + "/edit";
    }

    @PostMapping("/sections/{id}/lectures/add")
    public String addLecture(@PathVariable("id") Long id,
            @RequestParam("title") String title,
            @RequestParam("durationSeconds") Integer durationSeconds,
            @RequestParam(value = "videoFile", required = false) MultipartFile videoFile,
            @RequestParam(value = "videoUrl", required = false) String videoUrl,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "isPreview", defaultValue = "false") boolean isPreview,
            @RequestHeader(value = "Referer", required = false) String referer,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        CourseSection section = courseSectionRepository.findById(id).orElse(null);
        if (section == null) {
            redirectAttributes.addFlashAttribute("error", "Section not found.");
            return "redirect:/vendor/dashboard";
        }
        // Verify ownership
        getOwnedCourseOrThrow(section.getCourse().getId(), principal.getName());

        Lecture lecture = new Lecture();
        lecture.setTitle(title);
        lecture.setDurationSeconds(durationSeconds != null ? durationSeconds : 0);
        lecture.setContent(content);
        lecture.setPreview(isPreview);

        if (videoFile != null && !videoFile.isEmpty()) {
            String fileUrl = fileStorageService.store(videoFile);
            lecture.setVideoUrl(fileUrl);
        } else if (videoUrl != null && !videoUrl.trim().isEmpty()) {
            lecture.setVideoUrl(videoUrl.trim());
        }

        courseService.addLectureToSection(id, lecture);
        courseService.updateCourseStats(section.getCourse().getId());

        redirectAttributes.addFlashAttribute("success", "Lecture added successfully!");
        return (referer != null) ? "redirect:" + referer : "redirect:/vendor/dashboard";
    }

    @PostMapping("/profile/update")
    public String updateProfile(
            @RequestParam("name") String name,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "bio", required = false) String bio,
            @RequestParam(value = "skills", required = false) String skills,
            @RequestParam(value = "expertise", required = false) String expertise,
            @RequestParam(value = "instagramUrl", required = false) String instagramUrl,
            @RequestParam(value = "linkedinUrl", required = false) String linkedinUrl,
            @RequestParam(value = "youtubeUrl", required = false) String youtubeUrl,
            @RequestParam(value = "profilePicture", required = false) MultipartFile profilePicture,
            @RequestParam(value = "currentPassword", required = false) String currentPassword,
            @RequestParam(value = "newPassword", required = false) String newPassword,
            @RequestParam(value = "confirmPassword", required = false) String confirmPassword,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        User user = userService.findByEmail(principal.getName());

        if (name == null || name.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Full Name is required and cannot be empty.");
            return "redirect:/vendor/dashboard?tab=profile";
        }
        if (name.trim().length() < 2) {
            redirectAttributes.addFlashAttribute("error", "Full Name must be at least 2 characters long.");
            return "redirect:/vendor/dashboard?tab=profile";
        }
        if (name.trim().length() > 60) {
            redirectAttributes.addFlashAttribute("error", "Full Name cannot exceed 60 characters.");
            return "redirect:/vendor/dashboard?tab=profile";
        }

        user.setName(name.trim());
        if (phone != null && !phone.trim().isEmpty()) {
            String cleanPhone = phone.trim();
            String digitsOnly = cleanPhone.replaceAll("[^0-9]", "");
            if (digitsOnly.length() < 10 || digitsOnly.length() > 15) {
                redirectAttributes.addFlashAttribute("error", "Phone number must contain between 10 and 15 digits.");
                return "redirect:/vendor/dashboard?tab=profile";
            }
            user.setPhone(cleanPhone);
        } else {
            user.setPhone(null);
        }

        if (expertise != null && expertise.length() > 100) {
            user.setExpertise(expertise.substring(0, 100));
        } else {
            user.setExpertise(expertise != null ? expertise.trim() : null);
        }

        if (skills != null && skills.length() > 250) {
            user.setSkills(skills.substring(0, 250));
        } else {
            user.setSkills(skills != null ? skills.trim() : null);
        }

        if (bio != null && bio.length() > 1500) {
            user.setBio(bio.substring(0, 1500));
        } else {
            user.setBio(bio != null ? bio.trim() : null);
        }

        user.setInstagramUrl(instagramUrl);
        user.setLinkedinUrl(linkedinUrl);
        user.setYoutubeUrl(youtubeUrl);

        if (profilePicture != null && !profilePicture.isEmpty()) {
            try {
                String contentType = profilePicture.getContentType();
                if (contentType != null && !contentType.startsWith("image/")) {
                    redirectAttributes.addFlashAttribute("error", "Invalid file type. Only image files (JPG, PNG, GIF, WEBP) are allowed.");
                    return "redirect:/vendor/dashboard?tab=profile";
                }
                if (profilePicture.getSize() > 5 * 1024 * 1024) {
                    redirectAttributes.addFlashAttribute("error", "Profile picture exceeds maximum allowed size of 5MB.");
                    return "redirect:/vendor/dashboard?tab=profile";
                }
                String imagePath = fileStorageService.store(profilePicture);
                user.setProfileImageUrl(imagePath);
            } catch (Exception e) {
                redirectAttributes.addFlashAttribute("error", "Failed to upload image: " + e.getMessage());
                return "redirect:/vendor/dashboard?tab=profile";
            }
        }

        boolean isPasswordChangeRequested = (currentPassword != null && !currentPassword.trim().isEmpty())
                || (newPassword != null && !newPassword.trim().isEmpty())
                || (confirmPassword != null && !confirmPassword.trim().isEmpty());

        if (isPasswordChangeRequested) {
            if (currentPassword == null || currentPassword.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Current password is required to update your login password.");
                return "redirect:/vendor/dashboard?tab=profile";
            }
            if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                redirectAttributes.addFlashAttribute("error", "Incorrect current password. Please try again.");
                return "redirect:/vendor/dashboard?tab=profile";
            }
            if (newPassword == null || newPassword.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "New password is required.");
                return "redirect:/vendor/dashboard?tab=profile";
            }
            if (newPassword.length() < 6) {
                redirectAttributes.addFlashAttribute("error", "New password must be at least 6 characters long.");
                return "redirect:/vendor/dashboard?tab=profile";
            }
            if (newPassword.equals(currentPassword)) {
                redirectAttributes.addFlashAttribute("error", "New password cannot be the same as your current password.");
                return "redirect:/vendor/dashboard?tab=profile";
            }
            if (!newPassword.equals(confirmPassword)) {
                redirectAttributes.addFlashAttribute("error", "New passwords do not match.");
                return "redirect:/vendor/dashboard?tab=profile";
            }
            user.setPassword(passwordEncoder.encode(newPassword));
        }

        userService.saveUser(user);
        redirectAttributes.addFlashAttribute("success", "Profile updated successfully!");
        return "redirect:/vendor/dashboard?tab=profile";
    }

    @PostMapping("/reviews/{id}/reply")
    public String replyToReview(@PathVariable("id") Long id, @RequestParam("reply") String reply,
            @RequestParam(value = "page", defaultValue = "1") int page,
            Principal principal, RedirectAttributes redirectAttributes) {
        Review review = reviewRepository.findById(id).orElse(null);
        if (review != null) {
            getOwnedCourseOrThrow(review.getCourse().getId(), principal.getName());
            review.setReply(reply);
            review.setReplyDate(LocalDateTime.now());
            reviewRepository.save(review);
            redirectAttributes.addFlashAttribute("success", "Reply sent!");
        }
        return "redirect:/vendor/dashboard?tab=reviews&page=" + page;
    }

    @PostMapping("/questions/{id}/reply")
    public String replyToQuestion(@PathVariable("id") Long id, @RequestParam("content") String content,
            @RequestParam(value = "page", defaultValue = "1") int page,
            Principal principal, RedirectAttributes redirectAttributes) {
        CourseQuestion question = courseQuestionRepository.findById(id).orElse(null);
        if (question != null) {
            getOwnedCourseOrThrow(question.getCourse().getId(), principal.getName());
            User instructor = userService.findByEmail(principal.getName());
            CourseAnswer answer = new CourseAnswer();
            answer.setQuestion(question);
            answer.setUser(instructor);
            answer.setContent(content);
            courseAnswerRepository.save(answer);
            redirectAttributes.addFlashAttribute("success", "Answer posted successfully!");
        }
        return "redirect:/vendor/dashboard?tab=questions&page=" + page;
    }

    @PostMapping("/enrollments/{id}/certificate")
    public String generateCertificate(@PathVariable("id") Long id, Principal principal, RedirectAttributes redirectAttributes) {
        Enrollment enrollment = enrollmentRepository.findById(id).orElse(null);
        if (enrollment != null) {
            getOwnedCourseOrThrow(enrollment.getCourse().getId(), principal.getName());
            enrollment.setExamPassed(true);
            enrollment.setCertificateDate(LocalDateTime.now());
            enrollmentRepository.save(enrollment);
            redirectAttributes.addFlashAttribute("success",
                    "Certificate generated for student: " + enrollment.getStudent().getName());
        }
        return "redirect:/vendor/dashboard?tab=students";
    }

    @GetMapping("/exams")
    public String listExams(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            Model model, Principal principal) {
        String email = principal.getName();
        User instructor = userService.findByEmail(email);
        List<Course> courses;
        if (instructor != null && ("ADMIN".equalsIgnoreCase(instructor.getRole()) || "ROLE_ADMIN".equalsIgnoreCase(instructor.getRole()))) {
            courses = courseService.getAllCourses();
        } else {
            courses = courseService.getCoursesByInstructor(email);
        }

        // Auto-synchronize CourseExam entities for existing courses with questions
        for (Course c : courses) {
            List<CourseExam> existingForCourse = courseExamRepository.findByCourseId(c.getId());
            if (existingForCourse == null || existingForCourse.isEmpty()) {
                if (c.getExamQuestions() != null && !c.getExamQuestions().isEmpty()) {
                    CourseExam defaultExam = new CourseExam(c.getTitle() + " - Final Assessment", c);
                    defaultExam.setCreatedAt(c.getExamUpdatedAt() != null ? c.getExamUpdatedAt() : LocalDateTime.now());
                    defaultExam.setUpdatedAt(c.getExamUpdatedAt() != null ? c.getExamUpdatedAt() : LocalDateTime.now());
                    defaultExam = courseExamRepository.save(defaultExam);
                    for (ExamQuestion q : c.getExamQuestions()) {
                        if (q.getExam() == null) {
                            q.setExam(defaultExam);
                            examQuestionRepository.save(q);
                        }
                    }
                }
            }
        }

        List<CourseExam> addedExams;
        if (instructor != null && ("ADMIN".equalsIgnoreCase(instructor.getRole()) || "ROLE_ADMIN".equalsIgnoreCase(instructor.getRole()))) {
            addedExams = courseExamRepository.findAll();
            addedExams.sort((e1, e2) -> e2.getUpdatedAt().compareTo(e1.getUpdatedAt()));
        } else {
            addedExams = courseExamRepository.findByInstructorEmail(email);
        }

        int totalExams = addedExams.size();
        int pageSize = (size > 0) ? size : 10;
        int totalPages = Math.max(1, (int) Math.ceil((double) totalExams / pageSize));
        int curPage = Math.max(1, Math.min(page, totalPages));
        int startIdx = Math.min((curPage - 1) * pageSize, totalExams);
        int endIdx = Math.min(startIdx + pageSize, totalExams);
        List<CourseExam> pagedExams = (totalExams > 0) ? addedExams.subList(startIdx, endIdx) : Collections.emptyList();
        List<Integer> pageNumbers = new ArrayList<>();
        for (int i = 1; i <= totalPages; i++) {
            pageNumbers.add(i);
        }

        model.addAttribute("courses", courses);
        model.addAttribute("addedExams", pagedExams);
        model.addAttribute("allAddedExams", addedExams);
        model.addAttribute("totalExams", totalExams);
        model.addAttribute("currentPage", curPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("startItem", totalExams > 0 ? (startIdx + 1) : 0);
        model.addAttribute("endItem", endIdx);
        model.addAttribute("pageNumbers", pageNumbers);
        model.addAttribute("activeTab", "exams");
        model.addAttribute("instructor", instructor);
        model.addAttribute("vendor", instructor);
        return "instructor/exams";
    }

    /** Create a brand new exam for a course without overwriting existing exams */
    @PostMapping("/exams/create")
    public String createExam(
            @RequestParam("courseId") Long courseId,
            @RequestParam("title") String title,
            @RequestParam(value = "passingScore", defaultValue = "70") int passingScore,
            @RequestParam(value = "durationMinutes", defaultValue = "30") int durationMinutes,
            Principal principal, RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        Course course = getOwnedCourseOrThrow(courseId, principal.getName());
        String examTitle = (title != null && !title.trim().isEmpty()) ? title.trim() : (course.getTitle() + " - Exam " + (courseExamRepository.findByCourseId(courseId).size() + 1));
        CourseExam exam = new CourseExam(examTitle, course);
        exam.setPassingScore(passingScore > 0 ? passingScore : 70);
        exam.setDurationMinutes(durationMinutes > 0 ? durationMinutes : 30);
        exam.setCreatedAt(LocalDateTime.now());
        exam.setUpdatedAt(LocalDateTime.now());
        exam = courseExamRepository.save(exam);

        redirectAttributes.addFlashAttribute("success", "New exam '" + exam.getTitle() + "' created successfully! Add your questions below.");
        return "redirect:/vendor/exams/" + exam.getId() + "/questions";
    }

    /** Manage questions for a specific CourseExam */
    @GetMapping("/exams/{examId}/questions")
    public String manageCourseExamQuestions(@PathVariable("examId") Long examId, Model model, Principal principal) {
        if (principal == null) return "redirect:/login";
        CourseExam exam = getOwnedCourseExamOrThrow(examId, principal.getName());
        Course course = exam.getCourse();

        List<ExamQuestion> questions = examQuestionRepository.findByExamId(exam.getId());
        if (questions == null) questions = new ArrayList<>();

        model.addAttribute("exam", exam);
        model.addAttribute("course", course);
        model.addAttribute("questions", questions);
        User instructor = userService.findByEmail(principal.getName());
        model.addAttribute("instructor", instructor);
        model.addAttribute("vendor", instructor);

        String defaultTopic = resolveCourseTopic(course);
        model.addAttribute("defaultTopic", defaultTopic);

        return "instructor/manage_questions";
    }

    /** Add single question to a specific CourseExam */
    @PostMapping("/exams/{examId}/questions/add")
    public String addExamQuestionToExam(@PathVariable("examId") Long examId,
            @RequestParam("questionText") String questionText,
            @RequestParam("optionA") String optionA,
            @RequestParam("optionB") String optionB,
            @RequestParam("optionC") String optionC,
            @RequestParam("optionD") String optionD,
            @RequestParam("correctOption") String correctOption,
            Principal principal) {
        if (principal == null) return "redirect:/login";
        CourseExam exam = getOwnedCourseExamOrThrow(examId, principal.getName());
        Course course = exam.getCourse();

        ExamQuestion q = new ExamQuestion(course, questionText, optionA, optionB, optionC, optionD, correctOption);
        q.setExam(exam);
        q = examQuestionRepository.save(q);

        exam.getQuestions().add(q);
        exam.setUpdatedAt(LocalDateTime.now());
        courseExamRepository.save(exam);

        return "redirect:/vendor/exams/" + examId + "/questions";
    }

    /** AI question generation for a specific CourseExam - APPENDS instead of replacing */
    @PostMapping("/exams/{examId}/questions/generate-ai")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateQuestionsAiForExam(
            @PathVariable("examId") Long examId,
            @RequestParam(value = "count", required = false) Integer count,
            Principal principal) {
        if (count == null || count <= 0) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Number of questions cannot be 0 or empty. Please enter any number greater than 0.");
            return ResponseEntity.badRequest().body(err);
        }
        CourseExam exam = getOwnedCourseExamOrThrow(examId, principal.getName());
        Course course = exam.getCourse();

        String topic = resolveCourseTopic(course);
        int targetCount = count;
        List<Map<String, String>> generatedList = geminiService.generateMcqQuestions(topic, targetCount);

        List<Map<String, Object>> savedResults = new ArrayList<>();
        for (Map<String, String> item : generatedList) {
            ExamQuestion q = new ExamQuestion(
                    course,
                    item.get("questionText"),
                    item.get("optionA"),
                    item.get("optionB"),
                    item.get("optionC"),
                    item.get("optionD"),
                    item.get("correctOption")
            );
            q.setExam(exam);
            q = examQuestionRepository.save(q);
            exam.getQuestions().add(q);

            Map<String, Object> dto = new HashMap<>();
            dto.put("id", q.getId());
            dto.put("questionText", q.getQuestionText());
            dto.put("optionA", q.getOptionA());
            dto.put("optionB", q.getOptionB());
            dto.put("optionC", q.getOptionC());
            dto.put("optionD", q.getOptionD());
            dto.put("correctOption", q.getCorrectOption());
            savedResults.add(dto);
        }
        exam.setUpdatedAt(LocalDateTime.now());
        courseExamRepository.save(exam);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Successfully generated and added " + savedResults.size() + " questions!");
        response.put("topic", topic);
        response.put("count", savedResults.size());
        response.put("totalQuestions", exam.getQuestions().size());
        response.put("questions", savedResults);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/exams/{examId}/questions/generate-ai-form")
    public String generateQuestionsAiFormForExam(
            @PathVariable("examId") Long examId,
            @RequestParam(value = "count", required = false) Integer count,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        if (count == null || count <= 0) {
            redirectAttributes.addFlashAttribute("error", "Number of questions cannot be 0 or empty. Please enter any number greater than 0.");
            return "redirect:/vendor/exams/" + examId + "/questions";
        }
        CourseExam exam = getOwnedCourseExamOrThrow(examId, principal.getName());
        Course course = exam.getCourse();

        String topic = resolveCourseTopic(course);
        int targetCount = count;
        List<Map<String, String>> generatedList = geminiService.generateMcqQuestions(topic, targetCount);

        for (Map<String, String> item : generatedList) {
            ExamQuestion q = new ExamQuestion(
                    course,
                    item.get("questionText"),
                    item.get("optionA"),
                    item.get("optionB"),
                    item.get("optionC"),
                    item.get("optionD"),
                    item.get("correctOption")
            );
            q.setExam(exam);
            q = examQuestionRepository.save(q);
            exam.getQuestions().add(q);
        }
        exam.setUpdatedAt(LocalDateTime.now());
        courseExamRepository.save(exam);

        redirectAttributes.addFlashAttribute("success", "Successfully generated and added " + generatedList.size() + " questions for topic: " + topic);
        return "redirect:/vendor/exams/" + examId + "/questions";
    }

    @PostMapping("/exams/{examId}/questions/delete/{qid}")
    public String deleteQuestionFromExam(@PathVariable("examId") Long examId,
            @PathVariable("qid") Long qid,
            Principal principal) {
        if (principal == null) return "redirect:/login";
        CourseExam exam = getOwnedCourseExamOrThrow(examId, principal.getName());
        examQuestionRepository.deleteById(qid);
        exam.getQuestions().removeIf(q -> q.getId().equals(qid));
        exam.setUpdatedAt(LocalDateTime.now());
        courseExamRepository.save(exam);
        return "redirect:/vendor/exams/" + examId + "/questions";
    }

    @PostMapping("/exams/{examId}/questions/clear-all")
    public String clearAllExamQuestionsForExam(@PathVariable("examId") Long examId, Principal principal, RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        CourseExam exam = getOwnedCourseExamOrThrow(examId, principal.getName());
        if (exam.getQuestions() != null) {
            exam.getQuestions().clear();
        }
        exam.setUpdatedAt(LocalDateTime.now());
        courseExamRepository.save(exam);
        examQuestionRepository.deleteByExamId(exam.getId());
        redirectAttributes.addFlashAttribute("success", "All exam questions have been cleared.");
        return "redirect:/vendor/exams/" + examId + "/questions";
    }

    @PostMapping("/exams/{examId}/questions/clear-all-ajax")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> clearAllExamQuestionsForExamAjax(@PathVariable("examId") Long examId, Principal principal) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        CourseExam exam = getOwnedCourseExamOrThrow(examId, principal.getName());
        if (exam.getQuestions() != null) {
            exam.getQuestions().clear();
        }
        exam.setUpdatedAt(LocalDateTime.now());
        courseExamRepository.save(exam);
        examQuestionRepository.deleteByExamId(exam.getId());

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "All exam questions have been cleared.");
        resp.put("totalQuestions", 0);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/exams/{examId}/delete")
    public String deleteExam(@PathVariable("examId") Long examId,
            Principal principal, RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        CourseExam exam = getOwnedCourseExamOrThrow(examId, principal.getName());
        String examTitle = exam.getTitle();
        courseExamRepository.delete(exam);
        redirectAttributes.addFlashAttribute("success", "Exam '" + examTitle + "' deleted successfully.");
        return "redirect:/vendor/exams";
    }

    /** Dedicated Page: View exam attempts, student scores, and history with pagination & result filter/sort */
    @GetMapping("/exams/{id}/attempts")
    public String viewExamAttempts(@PathVariable("id") Long id,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "filterSort", defaultValue = "all") String filterSort,
            Model model, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }
        User instructor = userService.findByEmail(principal.getName());

        CourseExam exam = courseExamRepository.findById(id).orElse(null);
        Course course;
        List<ExamAttempt> allAttempts;

        if (exam != null) {
            course = getOwnedCourseOrThrow(exam.getCourse().getId(), principal.getName());
            allAttempts = examAttemptRepository.findByExamOrderByAttemptDateDesc(exam);
            if (allAttempts == null || allAttempts.isEmpty()) {
                allAttempts = examAttemptRepository.findByCourseOrderByAttemptDateDesc(course);
            }
        } else {
            course = getOwnedCourseOrThrow(id, principal.getName());
            allAttempts = examAttemptRepository.findByCourseOrderByAttemptDateDesc(course);
        }

        if (allAttempts == null) {
            allAttempts = new ArrayList<>();
        } else {
            allAttempts = new ArrayList<>(allAttempts);
        }

        // Calculate attempt sequence number per user before filtering
        List<ExamAttempt> chronoAttempts = new ArrayList<>(allAttempts);
        chronoAttempts.sort(Comparator.comparing(ExamAttempt::getAttemptDate, Comparator.nullsLast(Comparator.naturalOrder())));
        Map<Long, Integer> attemptNumberMap = new HashMap<>();
        Map<Long, Integer> userAttemptCounter = new HashMap<>();
        for (ExamAttempt att : chronoAttempts) {
            if (att.getUser() != null) {
                int count = userAttemptCounter.getOrDefault(att.getUser().getId(), 0) + 1;
                userAttemptCounter.put(att.getUser().getId(), count);
                attemptNumberMap.put(att.getId(), count);
            }
        }

        int totalAttempts = allAttempts.size();
        Set<Long> uniqueStudents = new HashSet<>();
        long passedCount = 0;
        int totalScoreSum = 0;
        for (ExamAttempt att : allAttempts) {
            if (att.getUser() != null) {
                uniqueStudents.add(att.getUser().getId());
            }
            if (att.isPassed()) {
                passedCount++;
            }
            totalScoreSum += att.getScore();
        }
        double passRate = (totalAttempts > 0) ? ((double) passedCount / totalAttempts * 100.0) : 0.0;
        double avgScore = (totalAttempts > 0) ? ((double) totalScoreSum / totalAttempts) : 0.0;

        // Apply Result filter / sort
        if ("pass".equalsIgnoreCase(filterSort)) {
            allAttempts = allAttempts.stream().filter(ExamAttempt::isPassed).collect(Collectors.toList());
        } else if ("fail".equalsIgnoreCase(filterSort)) {
            allAttempts = allAttempts.stream().filter(a -> !a.isPassed()).collect(Collectors.toList());
        } else if ("highest".equalsIgnoreCase(filterSort)) {
            allAttempts.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));
        } else if ("lowest".equalsIgnoreCase(filterSort)) {
            allAttempts.sort((a, b) -> Integer.compare(a.getScore(), b.getScore()));
        }

        int filteredTotal = allAttempts.size();
        int pageSize = (size > 0) ? size : 10;
        int totalPages = Math.max(1, (int) Math.ceil((double) filteredTotal / pageSize));
        int curPage = Math.max(1, Math.min(page, totalPages));
        int startIdx = Math.min((curPage - 1) * pageSize, filteredTotal);
        int endIdx = Math.min(startIdx + pageSize, filteredTotal);
        List<ExamAttempt> pagedAttempts = (filteredTotal > 0) ? allAttempts.subList(startIdx, endIdx) : Collections.emptyList();
        List<Integer> pageNumbers = new ArrayList<>();
        for (int i = 1; i <= totalPages; i++) {
            pageNumbers.add(i);
        }

        model.addAttribute("course", course);
        model.addAttribute("exam", exam != null ? exam : course);
        model.addAttribute("examTitle", exam != null ? exam.getTitle() : (course.getTitle() + " - Final Assessment"));
        model.addAttribute("instructor", instructor);
        model.addAttribute("vendor", instructor);
        model.addAttribute("user", instructor);
        model.addAttribute("attempts", pagedAttempts);
        model.addAttribute("allAttempts", allAttempts);
        model.addAttribute("attemptNumberMap", attemptNumberMap);
        model.addAttribute("totalAttempts", totalAttempts);
        model.addAttribute("filteredTotal", filteredTotal);
        model.addAttribute("uniqueStudentCount", uniqueStudents.size());
        model.addAttribute("passedCount", passedCount);
        model.addAttribute("passRate", Math.round(passRate));
        model.addAttribute("avgScore", Math.round(avgScore));
        model.addAttribute("currentPage", curPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("filterSort", filterSort);
        model.addAttribute("startItem", filteredTotal > 0 ? (startIdx + 1) : 0);
        model.addAttribute("endItem", endIdx);
        model.addAttribute("pageNumbers", pageNumbers);
        return "instructor/exam_attempts";
    }

    /** REST endpoint – Get detailed Answer Sheet for a student's attempt */
    @GetMapping("/api/attempts/{attemptId}/answers")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getAttemptAnswerSheet(@PathVariable("attemptId") Long attemptId, Principal principal) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        ExamAttempt attempt = examAttemptRepository.findById(attemptId).orElse(null);
        if (attempt == null) return ResponseEntity.notFound().build();

        Course course = attempt.getCourse();
        if (course == null && attempt.getExam() != null) {
            course = attempt.getExam().getCourse();
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("attemptId", attempt.getId());
        resp.put("studentName", attempt.getUser() != null ? attempt.getUser().getName() : "Student");
        resp.put("studentEmail", attempt.getUser() != null ? attempt.getUser().getEmail() : "");
        resp.put("score", attempt.getScore());
        resp.put("isPassed", attempt.isPassed());
        resp.put("attemptDate", attempt.getAttemptDate() != null ? attempt.getAttemptDate().format(DateTimeFormatter.ofPattern("MMM dd, yyyy, hh:mm a")) : "N/A");
        resp.put("examTitle", attempt.getExam() != null ? attempt.getExam().getTitle() : (course != null ? course.getTitle() : "Assessment Exam"));

        List<Map<String, Object>> answers = new ArrayList<>();
        String detailsJson = attempt.getSubmissionDetails();
        ObjectMapper mapper = new ObjectMapper();

        if (detailsJson != null && !detailsJson.trim().isEmpty() && !detailsJson.trim().equals("[]")) {
            try {
                answers = mapper.readValue(detailsJson, new TypeReference<List<Map<String, Object>>>() {});
            } catch (Exception ignored) {}
        }

        List<ExamQuestion> qList = (attempt.getExam() != null && attempt.getExam().getExamQuestions() != null && !attempt.getExam().getExamQuestions().isEmpty())
                ? attempt.getExam().getExamQuestions()
                : (course != null ? course.getExamQuestions() : Collections.emptyList());

        if (answers.isEmpty() && qList != null && !qList.isEmpty()) {
            for (ExamQuestion q : qList) {
                Map<String, Object> item = new HashMap<>();
                item.put("question", q.getQuestionText());
                item.put("optionA", q.getOptionA());
                item.put("optionB", q.getOptionB());
                item.put("optionC", q.getOptionC());
                item.put("optionD", q.getOptionD());
                item.put("correctAnswer", q.getCorrectOption());
                item.put("selectedAnswer", "Unanswered");
                item.put("isCorrect", false);
                answers.add(item);
            }
        } else if (qList != null && !qList.isEmpty()) {
            for (Map<String, Object> ans : answers) {
                String qText = (String) ans.get("question");
                if (qText != null) {
                    for (ExamQuestion eq : qList) {
                        if (qText.trim().equalsIgnoreCase(eq.getQuestionText().trim())) {
                            ans.putIfAbsent("optionA", eq.getOptionA());
                            ans.putIfAbsent("optionB", eq.getOptionB());
                            ans.putIfAbsent("optionC", eq.getOptionC());
                            ans.putIfAbsent("optionD", eq.getOptionD());
                            ans.putIfAbsent("correctAnswer", eq.getCorrectOption());
                            break;
                        }
                    }
                }
            }
        }

        int totalQ = answers.size();
        long correctQ = answers.stream().filter(a -> Boolean.TRUE.equals(a.get("isCorrect"))).count();
        long wrongQ = totalQ - correctQ;

        resp.put("totalQuestions", totalQ);
        resp.put("correctCount", correctQ);
        resp.put("wrongCount", wrongQ);
        resp.put("answers", answers);

        return ResponseEntity.ok(resp);
    }

    /** REST endpoint â€“ returns question counts for each course (JSON) */
    @GetMapping("/api/exams-stats")
    @ResponseBody
    public ResponseEntity<Map<Long, Integer>> getExamsStats(Principal principal) {
        if (principal == null)
            return ResponseEntity.status(401).build();
        String email = principal.getName();
        User user = userService.findByEmail(email);
        List<Course> courses;
        if (user != null && ("ADMIN".equalsIgnoreCase(user.getRole()) || "ROLE_ADMIN".equalsIgnoreCase(user.getRole()))) {
            courses = courseService.getAllCourses();
        } else {
            courses = courseService.getCoursesByInstructor(email);
        }
        Map<Long, Integer> stats = new HashMap<>();
        for (Course course : courses) {
            stats.put(course.getId(), course.getExamQuestions() != null ? course.getExamQuestions().size() : 0);
        }
        return ResponseEntity.ok(stats);
    }

    private String resolveCourseTopic(Course course) {
        if (course.getCategory() != null && course.getCategory().getName() != null && !course.getCategory().getName().isBlank()) {
            String topic = course.getCategory().getName().trim();
            if (course.getSubCategory() != null && !course.getSubCategory().isBlank()) {
                topic += " - " + course.getSubCategory().trim();
            }
            return topic;
        }
        if (course.getTitle() != null && !course.getTitle().isBlank()) {
            return course.getTitle().trim();
        }
        return "General";
    }

    @GetMapping("/courses/{id}/questions")
    public String manageExamQuestions(@PathVariable("id") Long id, Model model, Principal principal) {
        Course course = getOwnedCourseOrThrow(id, principal.getName());

        model.addAttribute("course", course);
        model.addAttribute("questions", course.getExamQuestions());
        User instructor = userService.findByEmail(principal.getName());
        model.addAttribute("instructor", instructor);
        model.addAttribute("vendor", instructor);

        String defaultTopic = resolveCourseTopic(course);
        model.addAttribute("defaultTopic", defaultTopic);

        return "instructor/manage_questions";
    }

    @PostMapping("/courses/{id}/questions/add")
    public String addExamQuestion(@PathVariable("id") Long id,
            @RequestParam("questionText") String questionText,
            @RequestParam("optionA") String optionA,
            @RequestParam("optionB") String optionB,
            @RequestParam("optionC") String optionC,
            @RequestParam("optionD") String optionD,
            @RequestParam("correctOption") String correctOption,
            Principal principal) {
        Course course = getOwnedCourseOrThrow(id, principal.getName());

        ExamQuestion q = new ExamQuestion(course, questionText, optionA, optionB, optionC, optionD, correctOption);
        course.getExamQuestions().add(q);
        course.setExamUpdatedAt(LocalDateTime.now());
        courseService.saveCourse(course);
        return "redirect:/vendor/courses/" + id + "/questions";
    }

    @PostMapping("/courses/{id}/questions/generate-ai")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateQuestionsAi(
            @PathVariable("id") Long id,
            @RequestParam(value = "count", required = false) Integer count,
            Principal principal) {
        if (count == null || count <= 0) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Number of questions cannot be 0 or empty. Please enter any number greater than 0.");
            return ResponseEntity.badRequest().body(err);
        }
        Course course = getOwnedCourseOrThrow(id, principal.getName());

        // Strictly enforce the registered course category/topic (cannot be modified)
        String topic = resolveCourseTopic(course);

        int targetCount = count;
        List<Map<String, String>> generatedList = geminiService.generateMcqQuestions(topic, targetCount);

        // Do NOT delete existing questions! Append generated questions to existing questions.
        if (course.getExamQuestions() == null) {
            course.setExamQuestions(new ArrayList<>());
        }

        List<Map<String, Object>> savedResults = new ArrayList<>();
        for (Map<String, String> item : generatedList) {
            ExamQuestion q = new ExamQuestion(
                    course,
                    item.get("questionText"),
                    item.get("optionA"),
                    item.get("optionB"),
                    item.get("optionC"),
                    item.get("optionD"),
                    item.get("correctOption")
            );
            q.setCourse(course);
            q = examQuestionRepository.save(q);
            course.getExamQuestions().add(q);

            Map<String, Object> dto = new HashMap<>();
            dto.put("id", q.getId());
            dto.put("questionText", q.getQuestionText());
            dto.put("optionA", q.getOptionA());
            dto.put("optionB", q.getOptionB());
            dto.put("optionC", q.getOptionC());
            dto.put("optionD", q.getOptionD());
            dto.put("correctOption", q.getCorrectOption());
            savedResults.add(dto);
        }
        course.setExamUpdatedAt(LocalDateTime.now());
        courseService.saveCourse(course);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Successfully generated and added " + savedResults.size() + " questions!");
        response.put("topic", topic);
        response.put("count", savedResults.size());
        response.put("totalQuestions", course.getExamQuestions().size());
        response.put("questions", savedResults);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/courses/{id}/questions/generate-ai-form")
    public String generateQuestionsAiForm(
            @PathVariable("id") Long id,
            @RequestParam(value = "count", required = false) Integer count,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        if (count == null || count <= 0) {
            redirectAttributes.addFlashAttribute("error", "Number of questions cannot be 0 or empty. Please enter any number greater than 0.");
            return "redirect:/vendor/courses/" + id + "/questions";
        }
        Course course = getOwnedCourseOrThrow(id, principal.getName());

        String topic = resolveCourseTopic(course);
        int targetCount = count;
        List<Map<String, String>> generatedList = geminiService.generateMcqQuestions(topic, targetCount);

        // Do NOT delete existing questions! Append generated questions to existing questions.
        if (course.getExamQuestions() == null) {
            course.setExamQuestions(new ArrayList<>());
        }

        for (Map<String, String> item : generatedList) {
            ExamQuestion q = new ExamQuestion(
                    course,
                    item.get("questionText"),
                    item.get("optionA"),
                    item.get("optionB"),
                    item.get("optionC"),
                    item.get("optionD"),
                    item.get("correctOption")
            );
            q.setCourse(course);
            q = examQuestionRepository.save(q);
            course.getExamQuestions().add(q);
        }
        course.setExamUpdatedAt(LocalDateTime.now());
        courseService.saveCourse(course);

        redirectAttributes.addFlashAttribute("success", "Successfully generated and added " + generatedList.size() + " questions for topic: " + topic);
        return "redirect:/vendor/courses/" + id + "/questions";
    }

    @PostMapping("/courses/{id}/questions/clear-all")
    public String clearAllExamQuestions(@PathVariable("id") Long id, Principal principal, RedirectAttributes redirectAttributes) {
        Course course = getOwnedCourseOrThrow(id, principal.getName());
        if (course.getExamQuestions() != null) {
            course.getExamQuestions().clear();
        }
        course.setExamUpdatedAt(null);
        courseService.saveCourse(course);
        examQuestionRepository.deleteByCourseId(course.getId());
        redirectAttributes.addFlashAttribute("success", "All exam questions have been cleared.");
        return "redirect:/vendor/courses/" + id + "/questions";
    }

    @PostMapping("/courses/{id}/questions/clear-all-ajax")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> clearAllExamQuestionsAjax(@PathVariable("id") Long id, Principal principal) {
        Course course = getOwnedCourseOrThrow(id, principal.getName());
        if (course.getExamQuestions() != null) {
            course.getExamQuestions().clear();
        }
        course.setExamUpdatedAt(null);
        courseService.saveCourse(course);
        examQuestionRepository.deleteByCourseId(course.getId());

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "All exam questions have been cleared.");
        resp.put("totalQuestions", 0);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/courses/{id}/questions/delete/{qid}")
    public String deleteExamQuestion(@PathVariable("id") Long id,
            @PathVariable("qid") Long qid,
            Principal principal) {
        Course course = getOwnedCourseOrThrow(id, principal.getName());

        course.getExamQuestions().removeIf(q -> q.getId().equals(qid));
        if (course.getExamQuestions().isEmpty()) {
            course.setExamUpdatedAt(null);
        } else {
            course.setExamUpdatedAt(LocalDateTime.now());
        }
        courseService.saveCourse(course);
        return "redirect:/vendor/courses/" + id + "/questions";
    }

    private void syncCourseCurriculum(Course targetCourse, List<CourseSection> incomingSections) {
        if (incomingSections == null || incomingSections.isEmpty()) {
            targetCourse.getSections().clear();
            return;
        }

        Map<Long, CourseSection> existingMap = new HashMap<>();
        for (CourseSection sec : targetCourse.getSections()) {
            if (sec.getId() != null) {
                existingMap.put(sec.getId(), sec);
            }
        }

        Set<Long> incomingIds = incomingSections.stream()
                .map(CourseSection::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        targetCourse.getSections().removeIf(s -> s.getId() != null && !incomingIds.contains(s.getId()));

        for (CourseSection incoming : incomingSections) {
            if (incoming.getId() != null && existingMap.containsKey(incoming.getId())) {
                CourseSection existing = existingMap.get(incoming.getId());
                existing.setTitle(incoming.getTitle());
                existing.setPosition(incoming.getPosition());
                syncSectionLectures(existing, incoming.getLectures());
            } else {
                incoming.setCourse(targetCourse);
                targetCourse.getSections().add(incoming);
            }
        }
    }

    private void syncSectionLectures(CourseSection existingSection, List<Lecture> incomingLectures) {
        if (incomingLectures == null || incomingLectures.isEmpty()) {
            existingSection.getLectures().clear();
            return;
        }

        Map<Long, Lecture> existingMap = new HashMap<>();
        for (Lecture lec : existingSection.getLectures()) {
            if (lec.getId() != null) {
                existingMap.put(lec.getId(), lec);
            }
        }

        Set<Long> incomingIds = incomingLectures.stream()
                .map(Lecture::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        existingSection.getLectures().removeIf(l -> l.getId() != null && !incomingIds.contains(l.getId()));

        for (Lecture incoming : incomingLectures) {
            if (incoming.getId() != null && existingMap.containsKey(incoming.getId())) {
                Lecture existing = existingMap.get(incoming.getId());
                existing.setTitle(incoming.getTitle());
                existing.setPosition(incoming.getPosition());
                existing.setDurationSeconds(incoming.getDurationSeconds());
                if (incoming.getVideoUrl() != null && !incoming.getVideoUrl().isEmpty()) {
                    existing.setVideoUrl(incoming.getVideoUrl());
                }
            } else {
                incoming.setSection(existingSection);
                existingSection.getLectures().add(incoming);
            }
        }
    }
}
