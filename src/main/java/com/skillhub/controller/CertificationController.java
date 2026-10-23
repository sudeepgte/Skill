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
    @Autowired
    private com.skillhub.repository.ExamAttemptRepository examAttemptRepository;
    @Autowired
    private com.skillhub.repository.ExamQuestionRepository examQuestionRepository;

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

            List<ExamAttempt> attempts = examAttemptRepository.findByUserAndCourse(user, course);
            boolean hasSubmittedExam = attempts.stream().anyMatch(ExamAttempt::isPassed);

            Map<String, Object> info = new HashMap<>();
            info.put("completed", completedLectures);
            info.put("total", totalLectures);
            info.put("percent", percent);
            info.put("isExamPending", hasSubmittedExam && !enrollment.isExamPassed());
            int qCount = examQuestionRepository.findByCourseId(course.getId()).size();
            info.put("examQuestionCount", qCount);
            info.put("hasInstructorExam", qCount > 0);
            progressMap.put(course.getId(), info);
        }

        java.util.Set<Long> enrolledIds = enrollments.stream().map(e -> e.getCourse().getId()).collect(java.util.stream.Collectors.toSet());
        List<Course> allCourses = courseService.getAllCourses();
        java.util.List<Course> availableExamCourses = new ArrayList<>();
        for (Course c : allCourses) {
            if (!enrolledIds.contains(c.getId())) {
                List<ExamQuestion> qList = examQuestionRepository.findByCourseId(c.getId());
                if (!qList.isEmpty()) {
                    c.setExamQuestions(qList);
                    availableExamCourses.add(c);
                }
            }
        }
        model.addAttribute("availableExamCourses", availableExamCourses);

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

        // Ensure enrolled so user can attempt exam directly
        Enrollment enrollment = enrollmentRepository.findByStudentAndCourse(user, course)
                .orElse(null);
        if (enrollment == null) {
            enrollment = new Enrollment(user, course);
            enrollment = enrollmentRepository.save(enrollment);
        }

        // Exam can be taken at any time, restriction removed.

        int questionCount = examQuestionRepository.findByCourseId(id).size();
        model.addAttribute("course", course);
        model.addAttribute("questionCount", questionCount);
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
            return "redirect:/user/certifications?pending=true&score=" + attempt.getScore();
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

        if (!enrollment.isExamPassed()) {
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
