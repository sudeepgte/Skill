package com.skillhub.service;

import com.skillhub.model.ProfessionalExamResult;
import com.skillhub.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Optional;

public interface ProfessionalExamResultService {
    Optional<ProfessionalExamResult> getResultByAttemptId(Long attemptId);
    Optional<ProfessionalExamResult> getResultById(Long id);
    Page<ProfessionalExamResult> getAllResults(Pageable pageable);
    ProfessionalExamResult correctResult(Long resultId, BigDecimal newObtainedMarks, String reason, User admin);
}
