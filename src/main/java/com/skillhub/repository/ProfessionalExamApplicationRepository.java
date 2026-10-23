package com.skillhub.repository;

import com.skillhub.model.ProfessionalExamApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProfessionalExamApplicationRepository extends JpaRepository<ProfessionalExamApplication, Long> {
    java.util.Optional<ProfessionalExamApplication> findByApplicationNumber(String applicationNumber);
    java.util.List<ProfessionalExamApplication> findByUserId(Long userId);
    java.util.List<ProfessionalExamApplication> findByStatus(com.skillhub.model.enums.ApplicationStatus status);
    long countByStatus(com.skillhub.model.enums.ApplicationStatus status);
    java.util.Optional<ProfessionalExamApplication> findByUserIdAndProfessionalExamId(Long userId, Long examId);
}
