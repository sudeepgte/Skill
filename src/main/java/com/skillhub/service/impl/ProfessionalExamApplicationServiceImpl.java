package com.skillhub.service.impl;

import com.skillhub.exception.BusinessException;
import com.skillhub.exception.ResourceNotFoundException;
import com.skillhub.model.AdminLog;
import com.skillhub.model.ProfessionalExam;
import com.skillhub.model.ProfessionalExamApplication;
import com.skillhub.model.ProfessionalExamVersion;
import com.skillhub.model.User;
import com.skillhub.model.enums.ApplicationStatus;
import com.skillhub.model.PaymentStatus;
import com.skillhub.repository.AdminLogRepository;
import com.skillhub.repository.ProfessionalExamApplicationRepository;
import com.skillhub.repository.ProfessionalExamRepository;
import com.skillhub.repository.ProfessionalExamVersionRepository;
import com.skillhub.service.ProfessionalExamApplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProfessionalExamApplicationServiceImpl implements ProfessionalExamApplicationService {

    @Autowired private ProfessionalExamApplicationRepository applicationRepository;
    @Autowired private ProfessionalExamRepository examRepository;
    @Autowired private ProfessionalExamVersionRepository versionRepository;
    @Autowired private AdminLogRepository adminLogRepository;

    @Override
    @Transactional
    public ProfessionalExamApplication submitApplication(Long examId, User student, String remarks) {
        ProfessionalExam exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found"));

        ProfessionalExamApplication app = new ProfessionalExamApplication();
        app.setProfessionalExam(exam);
        app.setUser(student);
        app.setApplicationNumber("APP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        app.setStatus(ApplicationStatus.SUBMITTED);

        versionRepository.findByProfessionalExamId(examId).stream()
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

        return applicationRepository.save(app);
    }

    @Override
    @Transactional
    public ProfessionalExamApplication approveApplication(Long applicationId, User admin) {
        ProfessionalExamApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));

        if (app.getStatus() != ApplicationStatus.SUBMITTED && app.getStatus() != ApplicationStatus.UNDER_REVIEW) {
            throw new BusinessException("INVALID_APPLICATION_STATUS_TRANSITION");
        }

        app.setStatus(ApplicationStatus.APPROVED);
        ProfessionalExamApplication saved = applicationRepository.save(app);

        adminLogRepository.save(new AdminLog(admin, "APPROVE_APPLICATION",
                "Approved application " + app.getApplicationNumber() + " for candidate " + app.getUser().getEmail()));
        return saved;
    }

    @Override
    @Transactional
    public ProfessionalExamApplication rejectApplication(Long applicationId, String reason, User admin) {
        ProfessionalExamApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));

        app.setStatus(ApplicationStatus.REJECTED);
        app.setRejectionReason(reason);
        ProfessionalExamApplication saved = applicationRepository.save(app);

        adminLogRepository.save(new AdminLog(admin, "REJECT_APPLICATION",
                "Rejected application " + app.getApplicationNumber() + ". Reason: " + reason));
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProfessionalExamApplication> getApplicationById(Long id) {
        return applicationRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProfessionalExamApplication> getAllApplications(Pageable pageable) {
        return applicationRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProfessionalExamApplication> getApplicationsForStudent(Long studentId) {
        return applicationRepository.findByUserId(studentId);
    }
}
