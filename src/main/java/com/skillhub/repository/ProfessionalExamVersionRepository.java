package com.skillhub.repository;

import com.skillhub.model.ProfessionalExamVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProfessionalExamVersionRepository extends JpaRepository<ProfessionalExamVersion, Long> {
    java.util.List<ProfessionalExamVersion> findByProfessionalExamId(Long examId);
}
