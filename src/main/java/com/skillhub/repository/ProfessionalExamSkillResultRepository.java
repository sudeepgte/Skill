package com.skillhub.repository;

import com.skillhub.model.ProfessionalExamSkillResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProfessionalExamSkillResultRepository extends JpaRepository<ProfessionalExamSkillResult, Long> {
    java.util.List<ProfessionalExamSkillResult> findByResultId(Long resultId);
}
