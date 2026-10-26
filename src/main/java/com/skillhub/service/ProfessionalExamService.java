package com.skillhub.service;

import com.skillhub.model.ProfessionalExam;
import com.skillhub.model.ProfessionalExamVersion;
import com.skillhub.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProfessionalExamService {
    List<ProfessionalExam> getAllActiveExams();
    Page<ProfessionalExam> getAllExams(Pageable pageable);
    Optional<ProfessionalExam> getExamById(Long id);
    ProfessionalExam createExam(ProfessionalExam exam, User admin);
    ProfessionalExam updateExam(Long id, ProfessionalExam exam, User admin);
    void publishExamVersion(Long versionId, User admin);
    ProfessionalExamVersion ensurePublishedVersionWithQuestions(ProfessionalExam exam);
}
