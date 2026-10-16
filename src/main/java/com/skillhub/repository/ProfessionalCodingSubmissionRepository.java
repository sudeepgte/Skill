package com.skillhub.repository;
import com.skillhub.model.ProfessionalCodingSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProfessionalCodingSubmissionRepository extends JpaRepository<ProfessionalCodingSubmission, Long> {
    List<ProfessionalCodingSubmission> findByAttemptIdAndQuestionId(Long attemptId, Long questionId);
}
