package com.skillhub.repository;

import com.skillhub.model.ProfessionalExamSecurityEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProfessionalExamSecurityEventRepository extends JpaRepository<ProfessionalExamSecurityEvent, Long> {
    java.util.List<ProfessionalExamSecurityEvent> findByAttemptIdOrderByEventTimestampDesc(Long attemptId);
    java.util.List<ProfessionalExamSecurityEvent> findByAttemptIdOrderByEventTimestampAsc(Long attemptId);
    long countByAttemptIdAndEventType(Long attemptId, String eventType);
}
