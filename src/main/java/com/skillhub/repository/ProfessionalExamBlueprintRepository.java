package com.skillhub.repository;
import com.skillhub.model.ProfessionalExamBlueprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProfessionalExamBlueprintRepository extends JpaRepository<ProfessionalExamBlueprint, Long> {
    List<ProfessionalExamBlueprint> findBySectionId(Long sectionId);
}
