package com.skillhub.repository;

import com.skillhub.model.ProfessionalExamSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProfessionalExamSectionRepository extends JpaRepository<ProfessionalExamSection, Long> {
    java.util.List<ProfessionalExamSection> findByExamVersionId(Long examVersionId);
}
