package com.skillhub.service.impl;

import com.skillhub.exception.BusinessException;
import com.skillhub.exception.ResourceNotFoundException;
import com.skillhub.model.AdminLog;
import com.skillhub.model.ProfessionalExamResult;
import com.skillhub.model.User;
import com.skillhub.repository.AdminLogRepository;
import com.skillhub.repository.ProfessionalExamResultRepository;
import com.skillhub.service.ProfessionalCertificateService;
import com.skillhub.service.ProfessionalExamResultService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class ProfessionalExamResultServiceImpl implements ProfessionalExamResultService {

    @Autowired private ProfessionalExamResultRepository resultRepository;
    @Autowired private AdminLogRepository adminLogRepository;
    @Autowired private ProfessionalCertificateService certificateService;

    @Override
    @Transactional(readOnly = true)
    public Optional<ProfessionalExamResult> getResultByAttemptId(Long attemptId) {
        return resultRepository.findByAttemptId(attemptId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProfessionalExamResult> getResultById(Long id) {
        return resultRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProfessionalExamResult> getAllResults(Pageable pageable) {
        return resultRepository.findAll(pageable);
    }

    @Override
    @Transactional
    public ProfessionalExamResult correctResult(Long resultId, BigDecimal newObtainedMarks, String reason, User admin) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessException("CORRECTION_REASON_REQUIRED");
        }

        ProfessionalExamResult result = resultRepository.findById(resultId)
                .orElseThrow(() -> new ResourceNotFoundException("Result not found"));

        BigDecimal oldScore = result.getObtainedMarks();
        result.setObtainedMarks(newObtainedMarks);
        double percentage = (newObtainedMarks.doubleValue() / result.getTotalMarks()) * 100.0;
        result.setPercentage(percentage);

        boolean passed = percentage >= result.getAttempt().getExamVersion().getPassingMarks();
        result.setStatus(passed ? "PASS" : "FAIL");

        ProfessionalExamResult saved = resultRepository.save(result);

        // Immutable audit record
        AdminLog audit = new AdminLog(admin, "CORRECT_RESULT",
                "Result ID " + resultId + " score changed from " + oldScore + " to " + newObtainedMarks + ". Reason: " + reason);
        adminLogRepository.save(audit);

        if (passed) {
            certificateService.issueCertificate(saved.getId());
        }

        return saved;
    }
}
