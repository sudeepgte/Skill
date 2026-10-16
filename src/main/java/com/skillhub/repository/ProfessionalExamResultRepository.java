package com.skillhub.repository;

import com.skillhub.model.ProfessionalExamResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProfessionalExamResultRepository extends JpaRepository<ProfessionalExamResult, Long> {
    java.util.Optional<ProfessionalExamResult> findByAttemptId(Long attemptId);
}
