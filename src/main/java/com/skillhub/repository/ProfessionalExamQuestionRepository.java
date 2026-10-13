package com.skillhub.repository;
import com.skillhub.model.ProfessionalExamQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface ProfessionalExamQuestionRepository extends JpaRepository<ProfessionalExamQuestion, Long> {
    List<ProfessionalExamQuestion> findByExamSectionExamVersionId(Long examVersionId);
    List<ProfessionalExamQuestion> findByExamSectionId(Long sectionId);
}
