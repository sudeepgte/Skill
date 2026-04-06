package com.skillhub.controller;

import com.skillhub.model.*;
import com.skillhub.service.*;
import com.skillhub.repository.LectureProgressRepository;
import com.skillhub.repository.EnrollmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;

@Controller
public class CertificationController {

    @Autowired
    private UserService userService;
    @Autowired
    private CourseService courseService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private EnrollmentRepository enrollmentRepository;
    @Autowired
    private LectureProgressRepository lectureProgressRepository;
    @Autowired
    private ExamService examService;

    // ─── CERTIFICATIONS DASHBOARD ───────────────────────────────────────────────
    @GetMapping("/user/certifications")
    public String getCertificationsPage(Model model, Principal principal) {
        if (principal == null)
            return "redirect:/login";

        User user = userService.findByEmail(principal.getName());
        List<Enrollment> enrollments = user.getEnrollments();

        // Compute real video completion % from LectureProgress for each enrollment
        Map<Long, Map<String, Object>> progressMap = new HashMap<>();
        for (Enrollment enrollment : enrollments) {
            Course course = enrollment.getCourse();

            // Use JPQL to count total lectures — avoids Hibernate lazy-loading returning
            // wrong count
            long totalLectures = lectureProgressRepository.countTotalLecturesByCourse(course.getId());

            // Fallback: count from in-memory sections if DB query returns 0
            if (totalLectures == 0 && course.getSections() != null) {
                totalLectures = course.getSections().stream()
                        .mapToLong(s -> s.getLectures() != null ? s.getLectures().size() : 0)
                        .sum();
            }

            // Count from DB (LectureProgress records)
            long completedFromDB = lectureProgressRepository
                    .countCompletedLecturesByCourse(user.getId(), course.getId());

            // Derive completed count from stored progressPercentage as fallback
            // (handles users who watched before the LectureProgress table existed)
            long completedFromStored = (totalLectures > 0)
                    ? Math.round(enrollment.getProgressPercentage() / 100.0 * totalLectures)
                    : 0;

            // Use whichever is higher — DB is accurate; stored is a fallback
            long completedLectures = Math.max(completedFromDB, completedFromStored);
            // Cap at total
            completedLectures = Math.min(completedLectures, totalLectures);

            double percent = (totalLectures > 0)
                    ? Math.min(100.0, (double) completedLectures / totalLectures * 100.0)
                    : 0.0;

            Map<String, Object> info = new HashMap<>();
            info.put("completed", completedLectures);
            info.put("total", totalLectures);
            info.put("percent", percent);
            progressMap.put(course.getId(), info);
        }

        model.addAttribute("user", user);
        model.addAttribute("categories", categoryService.getAllCategories());
        model.addAttribute("enrollments", enrollments);
        model.addAttribute("progressMap", progressMap);
        return "certifications";
    }

    // ─── EXAM INTRO ─────────────────────────────────────────────────────────────
    @GetMapping("/user/course/{id}/exam-start")
    public String startExam(@PathVariable("id") Long id, Model model, Principal principal) {
        if (principal == null)
            return "redirect:/login";
        User user = userService.findByEmail(principal.getName());
        Course course = courseService.getCourseById(id);

        // Must be enrolled
        Enrollment enrollment = enrollmentRepository.findByStudentAndCourse(user, course)
                .orElse(null);
        if (enrollment == null) {
            return "redirect:/course/" + id;
        }

        // Exam can be taken at any time, restriction removed.

        model.addAttribute("course", course);
        return "course/exam_intro";
    }

    // ─── EXAM SIMULATION ────────────────────────────────────────────────────────
    @GetMapping("/user/course/{id}/exam-simulation")
    public String examSimulation(@PathVariable("id") Long id, Model model, Principal principal) {
        if (principal == null)
            return "redirect:/login";
        User user = userService.findByEmail(principal.getName());
        Course course = courseService.getCourseById(id);

        List<Map<String, Object>> questions = examService.generateQuestions(course, user);
        model.addAttribute("course", course);
        model.addAttribute("questions", questions);
        return "course/exam_simulation";
    }

    // ─── EXAM SUBMIT ─────────────────────────────────────────────────────────────
    @PostMapping("/user/course/{id}/exam/submit")
    public String submitExam(@PathVariable("id") Long id, @RequestParam Map<String, String> allParams,
            Principal principal, Model model) {
        if (principal == null)
            return "redirect:/login";
        User user = userService.findByEmail(principal.getName());
        Course course = courseService.getCourseById(id);

        ExamAttempt attempt = examService.gradeExam(user, course, allParams);

        if (attempt.isPassed()) {
            enrollmentRepository.findByStudentAndCourse(user, course).ifPresent(e -> {
                e.setExamPassed(true);
                e.setCertificateDate(java.time.LocalDateTime.now());
                enrollmentRepository.save(e);
            });
            return "redirect:/user/course/" + id + "/certificate";
        } else {
            return "redirect:/user/certifications?fail=true&score=" + attempt.getScore();
        }
    }

    // ─── VIEW CERTIFICATE ────────────────────────────────────────────────────────
    @GetMapping({ "/user/certificate/{id}", "/user/course/{id}/certificate" })
    public String viewCertificate(@PathVariable("id") Long id, Model model, Principal principal) {
        if (principal == null)
            return "redirect:/login";
        User user = userService.findByEmail(principal.getName());
        Course course = courseService.getCourseById(id);

        Enrollment enrollment = enrollmentRepository.findByStudentAndCourse(user, course)
                .orElseThrow(() -> new RuntimeException("Not enrolled"));

        boolean isCompleted = false;
        long totalLectures = lectureProgressRepository.countTotalLecturesByCourse(course.getId());
        long completedLectures = 0;
        if (totalLectures > 0) {
            completedLectures = lectureProgressRepository.countCompletedLecturesByCourse(user.getId(), course.getId());
            // Fallback to enrollment stored percentage if DB count is 0
            if (completedLectures == 0 && enrollment.getProgressPercentage() >= 99.9) {
                completedLectures = totalLectures;
            }
            if (completedLectures >= totalLectures) {
                isCompleted = true;
            }
        }

        if (!enrollment.isExamPassed() && !isCompleted) {
            return "redirect:/user/certifications";
        }

        // If it's completed but no certificate date, set it now
        LocalDateTime certDate = enrollment.getCertificateDate();
        if (certDate == null) {
            certDate = LocalDateTime.now();
            enrollment.setCertificateDate(certDate);
            enrollmentRepository.save(enrollment);
        }

        model.addAttribute("user", user);
        model.addAttribute("course", course);
        model.addAttribute("enrollObj", enrollment);
        model.addAttribute("certificateDate", certDate);
        return "certificate/view";
    }
}
