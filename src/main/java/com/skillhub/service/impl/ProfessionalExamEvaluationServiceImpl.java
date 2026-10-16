package com.skillhub.service.impl;

import com.skillhub.model.*;
import com.skillhub.model.enums.*;
import com.skillhub.repository.*;
import com.skillhub.service.ExamObservabilityService;
import com.skillhub.service.ProfessionalCertificateService;
import com.skillhub.service.SqlEvaluationSandboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProfessionalExamEvaluationServiceImpl {

    private static final Logger log = LoggerFactory.getLogger(ProfessionalExamEvaluationServiceImpl.class);

    @Autowired
    private ProfessionalExamAttemptRepository attemptRepository;
    @Autowired
    private ProfessionalExamAnswerRepository answerRepository;
    @Autowired
    private ProfessionalExamResultRepository resultRepository;
    @Autowired
    private ProfessionalExamSkillResultRepository skillResultRepository;
    @Autowired
    private ProfessionalExamApplicationRepository applicationRepository;
    @Autowired
    private ProfessionalCertificateService certificateService;
    @Autowired
    private ProfessionalCodingSubmissionRepository codingSubmissionRepository;
    @Autowired
    private SqlEvaluationSandboxService sqlSandboxService;
    @Autowired
    private ExamObservabilityService observabilityService;

    @Transactional
    public synchronized ProfessionalExamResult evaluateAttempt(Long attemptId) {
        // Idempotency: Return existing result if attempt was already finalized
        Optional<ProfessionalExamResult> existingResult = resultRepository.findByAttemptId(attemptId);
        if (existingResult.isPresent()) {
            log.info("Attempt {} already has finalized result. Returning existing.", attemptId);
            return existingResult.get();
        }

        ProfessionalExamAttempt attempt = attemptRepository.findById(attemptId).orElseThrow();
        attempt.setStatus(AttemptStatus.EVALUATING);
        attemptRepository.save(attempt);

        List<ProfessionalExamAnswer> answers = answerRepository.findByAttemptId(attemptId);
        
        BigDecimal obtainedMarks = BigDecimal.ZERO;
        int correct = 0, wrong = 0, unanswered = 0;

        for (ProfessionalExamAnswer ans : answers) {
            ProfessionalExamQuestion q = ans.getQuestion();
            QuestionType type = q.getQuestionType() != null ? q.getQuestionType() : QuestionType.MCQ;

            switch (type) {
                case MULTIPLE_SELECT:
                case MULTIPLE_CHOICE: {
                    String selected = ans.getSelectedOption();
                    if (selected == null || selected.trim().isEmpty()) {
                        unanswered++;
                        ans.setIsCorrect(false);
                        ans.setMarksObtained(BigDecimal.ZERO);
                    } else {
                        Set<String> candidateOptions = parseOptionSet(selected);
                        Set<String> correctOptions = parseOptionSet(q.getCorrectAnswer());
                        if (candidateOptions.equals(correctOptions)) {
                            correct++;
                            ans.setIsCorrect(true);
                            BigDecimal earned = new BigDecimal(q.getMarks());
                            ans.setMarksObtained(earned);
                            obtainedMarks = obtainedMarks.add(earned);
                        } else {
                            wrong++;
                            ans.setIsCorrect(false);
                            BigDecimal penalty = q.getNegativeMarks() != null ? q.getNegativeMarks() : BigDecimal.ZERO;
                            ans.setMarksObtained(penalty.negate());
                            obtainedMarks = obtainedMarks.subtract(penalty);
                        }
                    }
                    break;
                }

                case CODING:
                case DEBUGGING: {
                    List<ProfessionalCodingSubmission> subs = codingSubmissionRepository.findByAttemptIdAndQuestionId(attemptId, q.getId());
                    if (!subs.isEmpty()) {
                        // Find highest scoring submission
                        ProfessionalCodingSubmission best = subs.stream()
                                .max(Comparator.comparingDouble(s -> s.getScore() != null ? s.getScore() : 0.0))
                                .orElse(subs.get(subs.size() - 1));

                        double scorePct = best.getScore() != null ? best.getScore() : 0.0;
                        BigDecimal earned = BigDecimal.valueOf((scorePct / 100.0) * q.getMarks()).setScale(2, RoundingMode.HALF_UP);

                        ans.setMarksObtained(earned);
                        obtainedMarks = obtainedMarks.add(earned);

                        if (scorePct >= 100.0) {
                            correct++;
                            ans.setIsCorrect(true);
                        } else if (scorePct > 0.0) {
                            correct++;
                            ans.setIsCorrect(true);
                        } else {
                            wrong++;
                            ans.setIsCorrect(false);
                        }
                    } else if (ans.getCodeAnswer() != null && !ans.getCodeAnswer().trim().isEmpty()) {
                        wrong++;
                        ans.setIsCorrect(false);
                        ans.setMarksObtained(BigDecimal.ZERO);
                    } else {
                        unanswered++;
                        ans.setIsCorrect(false);
                        ans.setMarksObtained(BigDecimal.ZERO);
                    }
                    break;
                }

                case SQL: {
                    String candidateSql = ans.getTextAnswer() != null && !ans.getTextAnswer().trim().isEmpty()
                            ? ans.getTextAnswer()
                            : ans.getSelectedOption();

                    if (candidateSql == null || candidateSql.trim().isEmpty()) {
                        unanswered++;
                        ans.setIsCorrect(false);
                        ans.setMarksObtained(BigDecimal.ZERO);
                    } else {
                        var sqlResult = sqlSandboxService.evaluate(candidateSql, q.getCorrectAnswer(), null);
                        if (sqlResult.isPassed()) {
                            correct++;
                            ans.setIsCorrect(true);
                            BigDecimal earned = new BigDecimal(q.getMarks());
                            ans.setMarksObtained(earned);
                            obtainedMarks = obtainedMarks.add(earned);
                        } else {
                            wrong++;
                            ans.setIsCorrect(false);
                            BigDecimal penalty = q.getNegativeMarks() != null ? q.getNegativeMarks() : BigDecimal.ZERO;
                            ans.setMarksObtained(penalty.negate());
                            obtainedMarks = obtainedMarks.subtract(penalty);
                            observabilityService.increment("sql_execution_failure_count");
                        }
                    }
                    break;
                }

                case MCQ:
                case TRUE_FALSE:
                case SINGLE_CHOICE:
                case CODE_OUTPUT:
                case SCENARIO:
                case SHORT_ANSWER:
                default: {
                    String selected = ans.getSelectedOption() != null ? ans.getSelectedOption() : ans.getTextAnswer();
                    if (selected == null || selected.trim().isEmpty()) {
                        unanswered++;
                        ans.setIsCorrect(false);
                        ans.setMarksObtained(BigDecimal.ZERO);
                    } else if (selected.trim().equalsIgnoreCase(q.getCorrectAnswer() != null ? q.getCorrectAnswer().trim() : "")) {
                        correct++;
                        ans.setIsCorrect(true);
                        BigDecimal earned = new BigDecimal(q.getMarks());
                        ans.setMarksObtained(earned);
                        obtainedMarks = obtainedMarks.add(earned);
                    } else {
                        wrong++;
                        ans.setIsCorrect(false);
                        BigDecimal penalty = q.getNegativeMarks() != null ? q.getNegativeMarks() : BigDecimal.ZERO;
                        ans.setMarksObtained(penalty.negate());
                        obtainedMarks = obtainedMarks.subtract(penalty);
                    }
                    break;
                }
            }
            answerRepository.save(ans);
        }

        int totalPossibleMarks = attempt.getExamVersion().getTotalMarks() > 0 
                ? attempt.getExamVersion().getTotalMarks() 
                : 100;
        
        double percentage = Math.max(0.0, (obtainedMarks.doubleValue() / totalPossibleMarks) * 100.0);

        ProfessionalExamResult result = new ProfessionalExamResult();
        result.setAttempt(attempt);
        result.setTotalMarks(totalPossibleMarks);
        result.setObtainedMarks(obtainedMarks);
        result.setPercentage(percentage);
        result.setCorrectAnswers(correct);
        result.setWrongAnswers(wrong);
        result.setUnansweredAnswers(unanswered);

        boolean passed = percentage >= attempt.getExamVersion().getPassingMarks();
        if (passed) {
            result.setStatus("PASS");
            if (percentage >= 90.0) result.setGrade("DISTINCTION");
            else if (percentage >= 75.0) result.setGrade("MERIT");
            else result.setGrade("PASS");
        } else {
            result.setStatus("FAIL");
            result.setGrade("FAIL");
        }

        result.setEvaluatedAt(LocalDateTime.now());
        ProfessionalExamResult savedResult = resultRepository.save(result);

        attempt.setObtainedMarks(Math.max(0, obtainedMarks.intValue()));
        attempt.setStatus(AttemptStatus.EVALUATED);
        attemptRepository.save(attempt);

        ProfessionalExamApplication app = attempt.getApplication();
        app.setStatus(passed ? ApplicationStatus.PASSED : ApplicationStatus.FAILED);
        applicationRepository.save(app);

        observabilityService.increment("exam_submission_count");

        // Issue professional certificate on passing
        if (passed) {
            try {
                certificateService.issueCertificate(savedResult.getId());
                observabilityService.increment("certificate_generation_count");
            } catch (Exception ex) {
                log.error("Failed to automatically issue certificate for passed attempt {}: {}", attemptId, ex.getMessage(), ex);
            }
        }

        log.info("Evaluated attempt {}: Score={}, Percentage={:.2f}%, Status={}", attemptId, obtainedMarks, percentage, result.getStatus());
        return savedResult;
    }

    private Set<String> parseOptionSet(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptySet();
        }
        String cleaned = raw.replaceAll("[\\[\\]\"']", "").trim();
        return Arrays.stream(cleaned.split("[,;\\s]+"))
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
