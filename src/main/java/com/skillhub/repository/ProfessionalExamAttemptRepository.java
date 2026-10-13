package com.skillhub.repository;
import com.skillhub.model.ProfessionalExamAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;
@Repository
public interface ProfessionalExamAttemptRepository extends JpaRepository<ProfessionalExamAttempt, Long> {
    Optional<ProfessionalExamAttempt> findByApplicationIdAndStudentId(Long applicationId, Long studentId);
    List<ProfessionalExamAttempt> findByStudentIdAndExamVersionProfessionalExamIdAndStatusIn(Long studentId, Long examId, List<com.skillhub.model.enums.AttemptStatus> statuses);
}
