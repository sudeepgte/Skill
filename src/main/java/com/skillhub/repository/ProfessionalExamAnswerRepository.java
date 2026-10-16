package com.skillhub.repository;
import com.skillhub.model.ProfessionalExamAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;
@Repository
public interface ProfessionalExamAnswerRepository extends JpaRepository<ProfessionalExamAnswer, Long> {
    Optional<ProfessionalExamAnswer> findByAttemptIdAndQuestionId(Long attemptId, Long questionId);
    List<ProfessionalExamAnswer> findByAttemptId(Long attemptId);
}
