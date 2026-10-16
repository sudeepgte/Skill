package com.skillhub.repository;
import com.skillhub.model.ExamSkillTaxonomy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExamSkillTaxonomyRepository extends JpaRepository<ExamSkillTaxonomy, Long> {
}
