package com.skillhub.controller;

import com.skillhub.exception.BusinessException;
import com.skillhub.exception.ResourceNotFoundException;
import com.skillhub.model.*;
import com.skillhub.model.enums.*;
import com.skillhub.repository.*;
import com.skillhub.service.ProfessionalCertificateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@Controller
@RequestMapping("/admin/professional-exams")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminProfessionalExamController {

    @Autowired private ProfessionalExamRepository examRepo;
    @Autowired private ProfessionalExamVersionRepository versionRepo;
    @Autowired private ProfessionalExamSectionRepository sectionRepo;
    @Autowired private ProfessionalExamQuestionRepository questionRepo;
    @Autowired private ProfessionalExamApplicationRepository appRepo;
    @Autowired private ProfessionalExamResultRepository resultRepo;
    @Autowired private ProfessionalExamSecurityEventRepository securityEventRepo;
    @Autowired private ProfessionalCertificateService certificateService;
    @Autowired private AdminLogRepository adminLogRepo;
    @Autowired private UserRepository userRepo;

    @Autowired private ProfessionalExamBlueprintRepository blueprintRepo;

    private User getAdmin(Principal p) {
        return p != null ? userRepo.findByEmail(p.getName()) : null;
    }

    @ModelAttribute("user")
    public User populateUser(Principal principal) {
        return getAdmin(principal);
    }

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("exams", examRepo.findAll());
        model.addAttribute("totalExams", examRepo.count());
        model.addAttribute("totalApplications", appRepo.count());
        model.addAttribute("pendingApplications", appRepo.countByStatus(ApplicationStatus.SUBMITTED));
        model.addAttribute("totalResults", resultRepo.count());
        return "admin/professional-exams/list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("exam", new ProfessionalExam());
        model.addAttribute("tracks", ExamTrack.values());
        model.addAttribute("difficulties", ExamDifficulty.values());
        return "admin/professional-exams/form";
    }

    @PostMapping("/create")
    public String createExam(@ModelAttribute ProfessionalExam exam, Principal p) {
        if (exam.getPassingMarks() <= 0 || exam.getTotalMarks() <= 0) {
            throw new BusinessException("INVALID_MARKS_CONFIGURATION");
        }
        if (exam.getPassingMarks() > exam.getTotalMarks()) {
            throw new BusinessException("PASSING_MARKS_CANNOT_EXCEED_TOTAL_MARKS");
        }
        exam.setActive(true);
        ProfessionalExam saved = examRepo.save(exam);

        // Audit log
        adminLogRepo.save(new AdminLog(getAdmin(p), "CREATE_PROFESSIONAL_EXAM", "Created exam: " + saved.getTitle() + " (" + saved.getExamCode() + ")"));
        return "redirect:/admin/professional-exams";
    }
    
    @GetMapping("/{id}/versions")
    public String versions(@PathVariable("id") Long id, Model model) {
        model.addAttribute("exam", examRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Exam not found")));
        model.addAttribute("versions", versionRepo.findByProfessionalExamId(id));
        return "admin/professional-exams/versions";
    }

    @PostMapping("/{id}/update")
    public String updateExam(@PathVariable("id") Long id, @ModelAttribute ProfessionalExam updated, Principal p) {
        ProfessionalExam exam = examRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Exam not found"));
        exam.setTitle(updated.getTitle());
        exam.setDurationMinutes(updated.getDurationMinutes());
        exam.setTotalMarks(updated.getTotalMarks());
        exam.setPassingMarks(updated.getPassingMarks());
        if (updated.getLevel() != null) exam.setLevel(updated.getLevel());
        exam.setActive(updated.isActive());
        examRepo.save(exam);
        adminLogRepo.save(new AdminLog(getAdmin(p), "UPDATE_PROFESSIONAL_EXAM", "Updated exam: " + exam.getTitle()));
        return "redirect:/admin/professional-exams/" + id + "/versions";
    }

    @PostMapping("/{id}/delete")
    public String deleteExam(@PathVariable("id") Long id, Principal p) {
        ProfessionalExam exam = examRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Exam not found"));
        versionRepo.findByProfessionalExamId(id).forEach(v -> {
            questionRepo.deleteAll(questionRepo.findByExamSectionExamVersionId(v.getId()));
            sectionRepo.deleteAll(sectionRepo.findByExamVersionId(v.getId()));
            versionRepo.delete(v);
        });
        examRepo.delete(exam);
        adminLogRepo.save(new AdminLog(getAdmin(p), "DELETE_PROFESSIONAL_EXAM", "Deleted exam: " + exam.getTitle()));
        return "redirect:/admin/professional-exams";
    }

    @RequestMapping(value = "/{id}/save-blueprint-version", method = {RequestMethod.GET, RequestMethod.POST})
    public String saveBlueprintVersion(@PathVariable("id") Long id, Principal p) {
        ProfessionalExam exam = examRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Exam not found"));
        List<ProfessionalExamVersion> existing = versionRepo.findByProfessionalExamId(id);
        int nextVer = existing.size() + 1;
        ProfessionalExamVersion version = new ProfessionalExamVersion();
        version.setProfessionalExam(exam);
        version.setVersionNumber("v" + nextVer + ".0");
        version.setVersionName("Blueprint Deployment v" + nextVer + ".0");
        version.setTotalMarks(exam.getTotalMarks());
        version.setPassingMarks(exam.getPassingMarks());
        version.setDurationMinutes(exam.getDurationMinutes());
        version.setActive(true);
        version.setPublished(true);
        versionRepo.save(version);
        adminLogRepo.save(new AdminLog(getAdmin(p), "SAVE_BLUEPRINT_VERSION", "Saved blueprint version for exam: " + exam.getTitle()));
        return "redirect:/admin/professional-exams/" + id + "/versions";
    }

    @PostMapping("/{id}/update-questions")
    @ResponseBody
    public String updateQuestions(@PathVariable("id") Long id, @RequestParam("description") String description, Principal p) {
        ProfessionalExam exam = examRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Exam not found"));
        exam.setDescription(description);
        examRepo.save(exam);
        return "SUCCESS";
    }

    @PostMapping("/versions/{versionId}/publish")
    public String publishVersion(@PathVariable("versionId") Long versionId, Principal p, Model model) {
        ProfessionalExamVersion version = versionRepo.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Version not found"));

        if (version.isPublished()) {
            throw new BusinessException("VERSION_ALREADY_PUBLISHED");
        }

        if (version.getDurationMinutes() <= 0) {
            throw new BusinessException("INVALID_DURATION_MINUTES");
        }
        if (version.getPassingMarks() <= 0) {
            throw new BusinessException("INVALID_PASSING_MARKS");
        }

        // 1. Section Validation
        List<ProfessionalExamSection> sections = sectionRepo.findByExamVersionId(versionId);
        if (sections.isEmpty()) {
            throw new BusinessException("CANNOT_PUBLISH_VERSION_WITHOUT_SECTIONS");
        }

        // 2. Blueprint Question Bank Sufficiency Validation (Requirement 21)
        for (ProfessionalExamSection section : sections) {
            List<ProfessionalExamBlueprint> blueprints = blueprintRepo.findBySectionId(section.getId());
            for (ProfessionalExamBlueprint bp : blueprints) {
                if (bp.getQuestionCount() > 0) {
                    List<ProfessionalExamQuestion> questions = questionRepo.findByExamSectionId(section.getId());
                    long availableCount = questions.stream()
                            .filter(q -> bp.getQuestionType() == null || q.getQuestionType() == bp.getQuestionType())
                            .filter(q -> bp.getDifficulty() == null || q.getDifficulty() == bp.getDifficulty())
                            .count();

                    if (availableCount < bp.getQuestionCount()) {
                        String sectionName = section.getName() != null ? section.getName() : "Section " + section.getId();
                        throw new BusinessException("BLUEPRINT_VALIDATION_FAILED: Section '" + sectionName + 
                                "' requires " + bp.getQuestionCount() + " questions" +
                                (bp.getQuestionType() != null ? " of type " + bp.getQuestionType() : "") +
                                (bp.getDifficulty() != null ? " (" + bp.getDifficulty() + ")" : "") +
                                ", but only " + availableCount + " valid questions are available in the Question Bank.");
                    }
                }
            }
        }

        List<ProfessionalExamQuestion> questions = questionRepo.findByExamSectionExamVersionId(versionId);
        if (questions.isEmpty()) {
            throw new BusinessException("CANNOT_PUBLISH_VERSION_WITHOUT_QUESTIONS");
        }

        int calculatedMarks = questions.stream().mapToInt(ProfessionalExamQuestion::getMarks).sum();
        if (calculatedMarks <= 0) {
            throw new BusinessException("TOTAL_MARKS_MUST_BE_GREATER_THAN_ZERO");
        }

        version.setTotalMarks(calculatedMarks);
        version.setPublished(true);
        version.setActive(true);
        versionRepo.save(version);

        adminLogRepo.save(new AdminLog(getAdmin(p), "PUBLISH_EXAM_VERSION",
                "Published version: " + version.getVersionNumber() + " for exam ID: " + version.getProfessionalExam().getId()));
        return "redirect:/admin/professional-exams/" + version.getProfessionalExam().getId() + "/versions";
    }

    @GetMapping("/question-bank")
    public String questionBank(@RequestParam(name = "page", defaultValue = "0") int page,
                               @RequestParam(name = "size", defaultValue = "20") int size,
                               @RequestParam(name = "search", required = false) String search,
                               Model model) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        Page<ProfessionalExamQuestion> questionPage = questionRepo.findAll(pageable);
        
        model.addAttribute("questions", questionPage.getContent());
        model.addAttribute("page", questionPage);
        model.addAttribute("totalApplications", appRepo.count());
        return "admin/professional-exams/question-bank";
    }
    
    @GetMapping("/applications")
    public String applications(@RequestParam(name = "page", defaultValue = "0") int page,
                               @RequestParam(name = "size", defaultValue = "20") int size,
                               Model model) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        Page<ProfessionalExamApplication> appPage = appRepo.findAll(pageable);

        model.addAttribute("applications", appPage.getContent());
        model.addAttribute("page", appPage);
        model.addAttribute("totalApplications", appRepo.count());
        model.addAttribute("pendingApplications", appRepo.countByStatus(ApplicationStatus.SUBMITTED));
        return "admin/professional-exams/applications";
    }

    @PostMapping("/applications/{appId}/approve")
    public String approveApplication(@PathVariable("appId") Long appId, Principal p) {
        ProfessionalExamApplication app = appRepo.findById(appId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));

        if (app.getStatus() != ApplicationStatus.SUBMITTED && app.getStatus() != ApplicationStatus.UNDER_REVIEW) {
            throw new BusinessException("INVALID_APPLICATION_STATUS_TRANSITION");
        }

        app.setStatus(ApplicationStatus.APPROVED);
        appRepo.save(app);

        adminLogRepo.save(new AdminLog(getAdmin(p), "APPROVE_APPLICATION",
                "Approved application " + app.getApplicationNumber() + " for student " + app.getUser().getEmail()));
        return "redirect:/admin/professional-exams/applications";
    }

    @PostMapping("/applications/{appId}/reject")
    public String rejectApplication(@PathVariable("appId") Long appId, 
                                    @RequestParam(name = "reason", defaultValue = "Eligibility criteria not met") String reason, 
                                    Principal p) {
        ProfessionalExamApplication app = appRepo.findById(appId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));

        app.setStatus(ApplicationStatus.REJECTED);
        app.setRejectionReason(reason);
        appRepo.save(app);

        adminLogRepo.save(new AdminLog(getAdmin(p), "REJECT_APPLICATION",
                "Rejected application " + app.getApplicationNumber() + ". Reason: " + reason));
        return "redirect:/admin/professional-exams/applications";
    }
    
    @GetMapping("/results")
    public String results(@RequestParam(name = "page", defaultValue = "0") int page,
                          @RequestParam(name = "size", defaultValue = "20") int size,
                          Model model) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        Page<ProfessionalExamResult> resultPage = resultRepo.findAll(pageable);

        model.addAttribute("results", resultPage.getContent());
        model.addAttribute("page", resultPage);
        model.addAttribute("totalResults", resultRepo.count());
        model.addAttribute("totalApplications", appRepo.count());
        return "admin/professional-exams/results";
    }

    @GetMapping("/security-events")
    public String securityEvents(@RequestParam(name = "page", defaultValue = "0") int page,
                                 @RequestParam(name = "size", defaultValue = "20") int size,
                                 Model model) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        Page<ProfessionalExamSecurityEvent> events = securityEventRepo.findAll(pageable);

        model.addAttribute("events", events.getContent());
        model.addAttribute("page", events);
        model.addAttribute("totalApplications", appRepo.count());
        return "admin/professional-exams/security-events";
    }

    @PostMapping("/certificates/{certificateId}/revoke")
    public String revokeCertificate(@PathVariable("certificateId") String certificateId,
                                    @RequestParam("reason") String reason,
                                    Principal p) {
        certificateService.revokeCertificate(certificateId, reason, getAdmin(p));
        return "redirect:/admin/professional-exams/results";
    }
}
