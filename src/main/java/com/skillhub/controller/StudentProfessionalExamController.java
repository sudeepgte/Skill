package com.skillhub.controller;

import com.skillhub.dto.ExamAttemptOverviewResponse;
import com.skillhub.dto.ExamStartRequest;
import com.skillhub.exception.BusinessException;
import com.skillhub.exception.ResourceNotFoundException;
import com.skillhub.model.*;
import com.skillhub.model.enums.ApplicationStatus;
import com.skillhub.model.enums.AttemptStatus;
import com.skillhub.repository.*;
import com.skillhub.service.ProfessionalExamAttemptService;
import com.skillhub.service.ProfessionalExamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.*;

@Controller
@RequestMapping("/student/professional-exams")
public class StudentProfessionalExamController {

    @Autowired private ProfessionalExamRepository examRepo;
    @Autowired private ProfessionalExamApplicationRepository appRepo;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfessionalExamAttemptRepository attemptRepo;
    @Autowired private ProfessionalExamResultRepository resultRepo;
    @Autowired private ProfessionalCertificateRepository certificateRepo;
    @Autowired private ProfessionalExamVersionRepository versionRepo;
    @Autowired private ProfessionalExamQuestionRepository questionRepo;
    @Autowired private ProfessionalExamService professionalExamService;
    @Autowired private ProfessionalExamAttemptService attemptService;

    private User getUser(Principal p) {
        if (p == null) throw new AccessDeniedException("Unauthenticated user");
        User user = userRepository.findByEmail(p.getName());
        if (user == null) throw new ResourceNotFoundException("User not found");
        return user;
    }

    @GetMapping
    public String listExams(Model model, Principal p) {
        List<ProfessionalExam> exams = examRepo.findByActiveTrue();

        // Ensure all active exams have published versions and questions ready
        Map<Long, Integer> questionCountMap = new HashMap<>();
        for (ProfessionalExam exam : exams) {
            ProfessionalExamVersion v = professionalExamService.ensurePublishedVersionWithQuestions(exam);
            int count = (v != null) ? questionRepo.findByExamSectionExamVersionId(v.getId()).size() : 0;
            questionCountMap.put(exam.getId(), count);
        }

        Map<Long, ProfessionalExamApplication> userApps = new HashMap<>();
        Map<Long, ProfessionalExamAttempt> userAttempts = new HashMap<>();
        Map<Long, ProfessionalExamResult> userResults = new HashMap<>();

        if (p != null) {
            User user = userRepository.findByEmail(p.getName());
            if (user != null) {
                List<ProfessionalExamApplication> apps = appRepo.findByUserId(user.getId());
                for (ProfessionalExamApplication app : apps) {
                    if (app.getProfessionalExam() != null) {
                        userApps.put(app.getProfessionalExam().getId(), app);
                    }
                }
                List<ProfessionalExamAttempt> attempts = attemptRepo.findByStudentId(user.getId());
                for (ProfessionalExamAttempt att : attempts) {
                    if (att.getExamVersion() != null && att.getExamVersion().getProfessionalExam() != null) {
                        Long exId = att.getExamVersion().getProfessionalExam().getId();
                        if (!userAttempts.containsKey(exId) || att.getStatus() == AttemptStatus.IN_PROGRESS) {
                            userAttempts.put(exId, att);
                        }
                        resultRepo.findByAttemptId(att.getId()).ifPresent(res -> userResults.put(exId, res));
                    }
                }
            }
        }

        model.addAttribute("exams", exams);
        model.addAttribute("questionCountMap", questionCountMap);
        model.addAttribute("userApps", userApps);
        model.addAttribute("userAttempts", userAttempts);
        model.addAttribute("userResults", userResults);
        return "student/professional-exams/list";
    }

    @GetMapping("/{id}")
    public String examDetails(@PathVariable("id") Long id, Model model, Principal p) {
        ProfessionalExam exam = examRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found"));
        ProfessionalExamVersion version = professionalExamService.ensurePublishedVersionWithQuestions(exam);
        int questionCount = (version != null) ? questionRepo.findByExamSectionExamVersionId(version.getId()).size() : 0;

        model.addAttribute("exam", exam);
        model.addAttribute("questionCount", questionCount);

        if (p != null) {
            User user = userRepository.findByEmail(p.getName());
            if (user != null) {
                appRepo.findByUserIdAndProfessionalExamId(user.getId(), id)
                        .ifPresent(app -> model.addAttribute("userApp", app));

                attemptRepo.findTopByStudentIdAndExamVersionProfessionalExamIdOrderByStartedAtDesc(user.getId(), id)
                        .ifPresent(att -> {
                            model.addAttribute("userAttempt", att);
                            resultRepo.findByAttemptId(att.getId())
                                    .ifPresent(res -> model.addAttribute("userResult", res));
                        });
            }
        }

        return "student/professional-exams/details";
    }

    @GetMapping("/{id}/apply")
    public String applyForm(@PathVariable("id") Long id, Model model, Principal p) {
        ProfessionalExam exam = examRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found"));
        ProfessionalExamVersion version = professionalExamService.ensurePublishedVersionWithQuestions(exam);
        int questionCount = (version != null) ? questionRepo.findByExamSectionExamVersionId(version.getId()).size() : 0;

        model.addAttribute("exam", exam);
        model.addAttribute("questionCount", questionCount);
        model.addAttribute("application", new ProfessionalExamApplication());
        return "student/professional-exams/apply";
    }

    @PostMapping("/{id}/apply")
    public String submitApplication(@PathVariable("id") Long id,
            @ModelAttribute ProfessionalExamApplication app,
            Principal p,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        User user = getUser(p);
        ProfessionalExam exam = examRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found"));

        ProfessionalExamVersion version = professionalExamService.ensurePublishedVersionWithQuestions(exam);

        app.setId(null);
        app.setProfessionalExam(exam);
        app.setUser(user);
        if (app.getEmail() == null || app.getEmail().isEmpty()) {
            app.setEmail(user.getEmail());
        }
        app.setApplicationNumber("APP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        app.setStatus(ApplicationStatus.APPROVED);
        app.setExamVersion(version);

        if (exam.getApplicationFee() != null && exam.getApplicationFee().compareTo(BigDecimal.ZERO) > 0) {
            app.setApplicationFee(exam.getApplicationFee());
            app.setPaymentStatus(PaymentStatus.SUCCESS);
        } else {
            app.setApplicationFee(BigDecimal.ZERO);
            app.setPaymentStatus(PaymentStatus.SUCCESS);
        }

        appRepo.save(app);
        return "redirect:/student/professional-exams/" + id + "/start";
    }

    @GetMapping("/{id}/start")
    public String startExamDirect(@PathVariable("id") Long id, Principal p) {
        User user = getUser(p);
        ProfessionalExam exam = examRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found"));

        ProfessionalExamVersion version = professionalExamService.ensurePublishedVersionWithQuestions(exam);

        // Find or create approved application
        ProfessionalExamApplication app = appRepo.findByUserIdAndProfessionalExamId(user.getId(), id)
                .orElseGet(() -> {
                    ProfessionalExamApplication newApp = new ProfessionalExamApplication();
                    newApp.setUser(user);
                    newApp.setProfessionalExam(exam);
                    newApp.setExamVersion(version);
                    newApp.setEmail(user.getEmail());
                    newApp.setFullName(user.getName() != null ? user.getName() : user.getEmail());
                    newApp.setApplicationNumber("APP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
                    newApp.setStatus(ApplicationStatus.APPROVED);
                    newApp.setPaymentStatus(PaymentStatus.SUCCESS);
                    newApp.setApplicationFee(exam.getApplicationFee() != null ? exam.getApplicationFee() : BigDecimal.ZERO);
                    return appRepo.save(newApp);
                });

        if (app.getStatus() != ApplicationStatus.APPROVED && app.getStatus() != ApplicationStatus.EXAM_STARTED) {
            app.setStatus(ApplicationStatus.APPROVED);
            app.setPaymentStatus(PaymentStatus.SUCCESS);
            app.setExamVersion(version);
            app = appRepo.save(app);
        }

        // Check for active attempt
        List<AttemptStatus> activeStatuses = List.of(AttemptStatus.IN_PROGRESS, AttemptStatus.PAUSED);
        List<ProfessionalExamAttempt> active = attemptRepo.findByStudentIdAndExamVersionProfessionalExamIdAndStatusIn(
                user.getId(), exam.getId(), activeStatuses);
        if (!active.isEmpty()) {
            return "redirect:/student/professional-exams/attempt/" + active.get(0).getId();
        }

        // If user already completed max attempts, redirect directly to their latest results
        int maxAllowed = exam.getMaximumAttempts() > 0 ? exam.getMaximumAttempts() : 3;
        Optional<ProfessionalExamAttempt> lastAttempt = attemptRepo.findTopByStudentIdAndExamVersionProfessionalExamIdOrderByStartedAtDesc(user.getId(), exam.getId());
        if (lastAttempt.isPresent()) {
            long totalAttempts = attemptRepo.findByApplicationIdAndStudentId(app.getId(), user.getId()).stream().count();
            if (totalAttempts >= maxAllowed) {
                return "redirect:/student/professional-exams/results/" + lastAttempt.get().getId();
            }
        }

        // Start exam attempt via attemptService
        ExamStartRequest req = new ExamStartRequest();
        req.setApplicationId(app.getId());
        ExamAttemptOverviewResponse overview = attemptService.startExam(req, user);
        return "redirect:/student/professional-exams/attempt/" + overview.getAttemptId();
    }

    @GetMapping("/attempt/{attemptId}")
    public String attemptExam(@PathVariable("attemptId") Long attemptId, Model model, Principal p) {
        User user = getUser(p);
        ProfessionalExamAttempt attempt = attemptRepo.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam attempt not found"));

        // IDOR Protection: verify user ownership
        boolean isAdmin = "ADMIN".equalsIgnoreCase(user.getRole()) || "ROLE_ADMIN".equalsIgnoreCase(user.getRole());
        if (!isAdmin && !attempt.getStudent().getId().equals(user.getId())) {
            throw new AccessDeniedException("Access denied: You do not own this examination attempt.");
        }

        model.addAttribute("attempt", attempt);
        return "student/professional-exams/attempt";
    }

    @GetMapping("/results/{attemptId}")
    public String results(@PathVariable("attemptId") Long attemptId, Model model, Principal p) {
        User user = getUser(p);
        ProfessionalExamAttempt attempt = attemptRepo.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam attempt not found"));

        // IDOR Protection: verify user ownership
        boolean isAdmin = "ADMIN".equalsIgnoreCase(user.getRole()) || "ROLE_ADMIN".equalsIgnoreCase(user.getRole());
        if (!isAdmin && !attempt.getStudent().getId().equals(user.getId())) {
            throw new AccessDeniedException("Access denied: You do not have permission to view these results.");
        }

        ProfessionalExamResult result = resultRepo.findByAttemptId(attemptId).orElse(null);
        model.addAttribute("attempt", attempt);
        model.addAttribute("result", result);

        if (result != null) {
            certificateRepo.findByResultId(result.getId()).ifPresent(cert -> model.addAttribute("certificate", cert));
        }

        return "student/professional-exams/result";
    }
}
