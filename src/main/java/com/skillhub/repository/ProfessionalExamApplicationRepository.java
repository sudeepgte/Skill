package com.skillhub.repository;

import com.skillhub.model.ProfessionalExamApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProfessionalExamApplicationRepository extends JpaRepository<ProfessionalExamApplication, Long> {
    Optional<ProfessionalExamApplication> findByApplicationNumber(String applicationNumber);
    List<ProfessionalExamApplication> findByUserId(Long userId);
    List<ProfessionalExamApplication> findByStatus(com.skillhub.model.enums.ApplicationStatus status);
    long countByStatus(com.skillhub.model.enums.ApplicationStatus status);
    Optional<ProfessionalExamApplication> findByUserIdAndProfessionalExamId(Long userId, Long examId);
    Optional<ProfessionalExamApplication> findFirstByUserIdAndProfessionalExamIdOrderByIdDesc(Long userId, Long examId);
    List<ProfessionalExamApplication> findAllByUserIdAndProfessionalExamId(Long userId, Long examId);
}
