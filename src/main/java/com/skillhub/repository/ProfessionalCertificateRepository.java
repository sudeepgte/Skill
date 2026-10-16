package com.skillhub.repository;
import com.skillhub.model.ProfessionalCertificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface ProfessionalCertificateRepository extends JpaRepository<ProfessionalCertificate, Long> {
    Optional<ProfessionalCertificate> findByCertificateId(String certificateId);
    Optional<ProfessionalCertificate> findByVerificationToken(String verificationToken);
    List<ProfessionalCertificate> findByStudentId(Long studentId);
    Optional<ProfessionalCertificate> findByResultId(Long resultId);
}
