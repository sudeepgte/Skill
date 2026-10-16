package com.skillhub.repository;

import com.skillhub.model.ProfessionalExamAttemptQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProfessionalExamAttemptQuestionRepository extends JpaRepository<ProfessionalExamAttemptQuestion, Long> {
    java.util.List<ProfessionalExamAttemptQuestion> findByAttemptIdOrderByDisplayOrderAsc(Long attemptId);
    java.util.Optional<ProfessionalExamAttemptQuestion> findByAttemptIdAndQuestionNumber(Long attemptId, int questionNumber);
}
