package com.skillhub.service;

import com.skillhub.model.ProfessionalExamApplication;
import com.skillhub.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProfessionalExamApplicationService {
    ProfessionalExamApplication submitApplication(Long examId, User student, String remarks);
    ProfessionalExamApplication approveApplication(Long applicationId, User admin);
    ProfessionalExamApplication rejectApplication(Long applicationId, String reason, User admin);
    Optional<ProfessionalExamApplication> getApplicationById(Long id);
    Page<ProfessionalExamApplication> getAllApplications(Pageable pageable);
    List<ProfessionalExamApplication> getApplicationsForStudent(Long studentId);
}
