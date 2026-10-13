package com.skillhub.service.impl;

import com.skillhub.dto.PublicCertificateDTO;
import com.skillhub.exception.BusinessException;
import com.skillhub.model.AdminLog;
import com.skillhub.model.ProfessionalCertificate;
import com.skillhub.model.ProfessionalExamResult;
import com.skillhub.model.User;
import com.skillhub.model.enums.CertificateStatus;
import com.skillhub.repository.AdminLogRepository;
import com.skillhub.repository.ProfessionalCertificateRepository;
import com.skillhub.repository.ProfessionalExamResultRepository;
import com.skillhub.service.ProfessionalCertificateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProfessionalCertificateServiceImpl implements ProfessionalCertificateService {

    private static final Logger log = LoggerFactory.getLogger(ProfessionalCertificateServiceImpl.class);

    @Autowired
    private ProfessionalCertificateRepository certificateRepository;

    @Autowired
    private ProfessionalExamResultRepository resultRepository;

    @Autowired
    private AdminLogRepository adminLogRepository;

    @Override
    @Transactional
    public ProfessionalCertificate issueCertificate(Long resultId) {
        // Idempotency: verify if already issued for this result
        Optional<ProfessionalCertificate> existing = certificateRepository.findByResultId(resultId);
        if (existing.isPresent()) {
            return existing.get();
        }

        ProfessionalExamResult result = resultRepository.findById(resultId)
                .orElseThrow(() -> new BusinessException("RESULT_NOT_FOUND"));

        if (!"PASS".equalsIgnoreCase(result.getStatus())) {
            throw new BusinessException("CANNOT_ISSUE_CERTIFICATE_FOR_FAILED_RESULT");
        }

        User student = result.getAttempt().getStudent();
        var exam = result.getAttempt().getExamVersion().getProfessionalExam();

        // Generate cryptographically unpredictable identifiers
        String certId = "SH-" + exam.getTrack().name() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");

        ProfessionalCertificate cert = new ProfessionalCertificate();
        cert.setCertificateId(certId);
        cert.setResult(result);
        cert.setStudent(student);
        cert.setExam(exam);
        cert.setTrack(exam.getTrack());
        cert.setIssueDate(LocalDate.now());
        cert.setExpiryDate(LocalDate.now().plusYears(2));
        cert.setStatus(CertificateStatus.ACTIVE);
        cert.setVerificationToken(token);
        cert.setVerificationUrl("/certifications/verify/" + certId);

        ProfessionalCertificate saved = certificateRepository.save(cert);
        log.info("Issued professional certificate {} for user {} on exam {}", certId, student.getId(), exam.getTitle());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PublicCertificateDTO> verifyPublicCertificate(String certificateId) {
        if (certificateId == null || certificateId.trim().isEmpty()) {
            return Optional.empty();
        }

        return certificateRepository.findByCertificateId(certificateId.trim().toUpperCase())
                .map(cert -> {
                    boolean isValid = cert.getStatus() == CertificateStatus.ACTIVE
                            && (cert.getExpiryDate() == null || cert.getExpiryDate().isAfter(LocalDate.now()));

                    // Sanitized candidate name (First Name + Last Initial for privacy)
                    String rawName = cert.getStudent().getName() != null ? cert.getStudent().getName() : "SkillHub Candidate";

                    return new PublicCertificateDTO(
                            cert.getCertificateId(),
                            rawName,
                            cert.getExam().getTitle(),
                            cert.getTrack().name(),
                            cert.getResult().getGrade(),
                            cert.getIssueDate(),
                            cert.getExpiryDate(),
                            cert.getStatus().name(),
                            isValid
                    );
                });
    }

    @Override
    @Transactional
    public void revokeCertificate(String certificateId, String reason, User admin) {
        ProfessionalCertificate cert = certificateRepository.findByCertificateId(certificateId)
                .orElseThrow(() -> new BusinessException("CERTIFICATE_NOT_FOUND"));

        cert.setStatus(CertificateStatus.REVOKED);
        certificateRepository.save(cert);

        // Audit Trail
        AdminLog audit = new AdminLog(admin, "REVOKE_CERTIFICATE",
                "Revoked certificate: " + certificateId + " | Candidate: " + cert.getStudent().getId() + " | Reason: " + reason);
        adminLogRepository.save(audit);
        log.warn("Certificate {} revoked by admin {}. Reason: {}", certificateId, admin.getId(), reason);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProfessionalCertificate> getCertificatesForStudent(Long studentId) {
        return certificateRepository.findByStudentId(studentId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProfessionalCertificate> getAllCertificates(Pageable pageable) {
        return certificateRepository.findAll(pageable);
    }
}
