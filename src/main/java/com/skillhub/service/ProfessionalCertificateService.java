package com.skillhub.service;

import com.skillhub.dto.PublicCertificateDTO;
import com.skillhub.model.ProfessionalCertificate;
import com.skillhub.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProfessionalCertificateService {
    ProfessionalCertificate issueCertificate(Long resultId);
    Optional<PublicCertificateDTO> verifyPublicCertificate(String certificateId);
    void revokeCertificate(String certificateId, String reason, User admin);
    List<ProfessionalCertificate> getCertificatesForStudent(Long studentId);
    Page<ProfessionalCertificate> getAllCertificates(Pageable pageable);
}
