package com.skillhub.controller;

import com.skillhub.exception.BusinessException;
import com.skillhub.exception.ResourceNotFoundException;
import com.skillhub.model.*;
import com.skillhub.model.enums.ApplicationStatus;
import com.skillhub.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.UUID;

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

    private User getUser(Principal p) {
        if (p == null) throw new AccessDeniedException("Unauthenticated user");
        User user = userRepository.findByEmail(p.getName());
        if (user == null) throw new ResourceNotFoundException("User not found");
        return user;
    }

    @GetMapping
    public String listExams(Model model) {
        model.addAttribute("exams", examRepo.findByActiveTrue());
        return "student/professional-exams/list";
    }
    
    @GetMapping("/{id}")
    public String examDetails(@PathVariable("id") Long id, Model model) {
        ProfessionalExam exam = examRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found"));
        model.addAttribute("exam", exam);
        return "student/professional-exams/details";
    }
    
    @GetMapping("/{id}/apply")
    public String applyForm(@PathVariable("id") Long id, Model model) {
        ProfessionalExam exam = examRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found"));
        model.addAttribute("exam", exam);
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

        app.setId(null);
        app.setProfessionalExam(exam);
        app.setUser(user);
        if (app.getEmail() == null || app.getEmail().isEmpty()) {
            app.setEmail(user.getEmail());
        }
        app.setApplicationNumber("APP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        app.setStatus(ApplicationStatus.SUBMITTED);

        // Link latest published active version if available
        versionRepo.findByProfessionalExamId(id).stream()
                .filter(ProfessionalExamVersion::isPublished)
                .findFirst()
                .ifPresent(app::setExamVersion);

        if (exam.getApplicationFee() != null && exam.getApplicationFee().compareTo(BigDecimal.ZERO) > 0) {
            app.setApplicationFee(exam.getApplicationFee());
            app.setPaymentStatus(PaymentStatus.PENDING);
        } else {
            app.setApplicationFee(BigDecimal.ZERO);
            app.setPaymentStatus(PaymentStatus.SUCCESS);
        }

        appRepo.save(app);
        redirectAttributes.addFlashAttribute("success", "Your application has been submitted successfully!");
        return "redirect:/student/professional-exams";
    }

    @GetMapping("/attempt/{attemptId}")
    public String attemptExam(@PathVariable("attemptId") Long attemptId, Model model, Principal p) {
        User user = getUser(p);
        ProfessionalExamAttempt attempt = attemptRepo.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam attempt not found"));

        // IDOR Protection: verify user ownership
        if (!attempt.getStudent().getId().equals(user.getId())) {
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
        if (!attempt.getStudent().getId().equals(user.getId())) {
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
