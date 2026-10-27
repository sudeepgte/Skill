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

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT a FROM ProfessionalExamApplication a WHERE a.id = :id")
    Optional<ProfessionalExamApplication> findByIdWithLock(@org.springframework.data.repository.query.Param("id") Long id);
}
