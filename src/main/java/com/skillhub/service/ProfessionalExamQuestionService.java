package com.skillhub.service;

import com.skillhub.model.ProfessionalExamQuestion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProfessionalExamQuestionService {
    ProfessionalExamQuestion createQuestion(ProfessionalExamQuestion question);
    ProfessionalExamQuestion updateQuestion(Long id, ProfessionalExamQuestion question);
    Optional<ProfessionalExamQuestion> getQuestionById(Long id);
    Page<ProfessionalExamQuestion> getAllQuestions(Pageable pageable);
    List<ProfessionalExamQuestion> getQuestionsForSection(Long sectionId);
    void validateQuestion(ProfessionalExamQuestion question);
}
