package com.skillhub.service.impl;

import com.skillhub.exception.BusinessException;
import com.skillhub.exception.ResourceNotFoundException;
import com.skillhub.model.ProfessionalExamQuestion;
import com.skillhub.model.enums.QuestionType;
import com.skillhub.repository.ProfessionalExamQuestionRepository;
import com.skillhub.service.ProfessionalExamQuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ProfessionalExamQuestionServiceImpl implements ProfessionalExamQuestionService {

    @Autowired
    private ProfessionalExamQuestionRepository questionRepository;

    @Override
    @Transactional
    public ProfessionalExamQuestion createQuestion(ProfessionalExamQuestion question) {
        validateQuestion(question);
        question.setActive(true);
        return questionRepository.save(question);
    }

    @Override
    @Transactional
    public ProfessionalExamQuestion updateQuestion(Long id, ProfessionalExamQuestion updated) {
        validateQuestion(updated);
        ProfessionalExamQuestion existing = questionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found"));

        existing.setQuestionText(updated.getQuestionText());
        existing.setQuestionType(updated.getQuestionType());
        existing.setDifficulty(updated.getDifficulty());
        existing.setTopic(updated.getTopic());
        existing.setSkill(updated.getSkill());
        existing.setMarks(updated.getMarks());
        existing.setNegativeMarks(updated.getNegativeMarks());
        existing.setOptions(updated.getOptions());
        existing.setCorrectAnswer(updated.getCorrectAnswer());
        existing.setExplanation(updated.getExplanation());
        existing.setCodeLanguage(updated.getCodeLanguage());
        existing.setCodeStarterTemplate(updated.getCodeStarterTemplate());
        existing.setTimeLimit(updated.getTimeLimit());
        existing.setMemoryLimit(updated.getMemoryLimit());
        existing.setActive(updated.isActive());

        return questionRepository.save(existing);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProfessionalExamQuestion> getQuestionById(Long id) {
        return questionRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProfessionalExamQuestion> getAllQuestions(Pageable pageable) {
        return questionRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProfessionalExamQuestion> getQuestionsForSection(Long sectionId) {
        return questionRepository.findByExamSectionId(sectionId);
    }

    @Override
    public void validateQuestion(ProfessionalExamQuestion question) {
        if (question.getQuestionText() == null || question.getQuestionText().trim().isEmpty()) {
            throw new BusinessException("QUESTION_TEXT_CANNOT_BE_EMPTY");
        }
        if (question.getMarks() <= 0) {
            throw new BusinessException("QUESTION_MARKS_MUST_BE_GREATER_THAN_ZERO");
        }
        if (question.getNegativeMarks() != null && question.getNegativeMarks().compareTo(new BigDecimal(question.getMarks())) > 0) {
            throw new BusinessException("NEGATIVE_MARKS_CANNOT_EXCEED_QUESTION_MARKS");
        }
        if (question.getQuestionType() == QuestionType.SINGLE_CHOICE || question.getQuestionType() == QuestionType.MULTIPLE_CHOICE) {
            if (question.getOptions() == null || question.getOptions().size() < 2) {
                throw new BusinessException("CHOICE_QUESTIONS_REQUIRE_AT_LEAST_TWO_OPTIONS");
            }
            Set<String> uniqueOptions = new HashSet<>(question.getOptions());
            if (uniqueOptions.size() < question.getOptions().size()) {
                throw new BusinessException("DUPLICATE_OPTIONS_DETECTED");
            }
            if (question.getCorrectAnswer() == null || question.getCorrectAnswer().trim().isEmpty()) {
                throw new BusinessException("CORRECT_ANSWER_REQUIRED_FOR_CHOICE_QUESTIONS");
            }
        }
    }
}
