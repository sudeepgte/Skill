package com.skillhub.repository;

import com.skillhub.model.ProfessionalExam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProfessionalExamRepository extends JpaRepository<ProfessionalExam, Long> {
    java.util.Optional<ProfessionalExam> findByExamCode(String examCode);
    java.util.List<ProfessionalExam> findByActiveTrue();
}
