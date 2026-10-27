package com.skillhub.service;

import com.skillhub.dto.CodeExecutionResult;
import com.skillhub.dto.CodeSubmission;
import com.skillhub.exception.BusinessException;
import com.skillhub.model.ProfessionalCodingSubmission;
import com.skillhub.model.ProfessionalExamAttempt;
import com.skillhub.model.ProfessionalExamQuestion;
import com.skillhub.model.enums.AttemptStatus;
import com.skillhub.model.enums.QuestionType;
import com.skillhub.model.enums.SubmissionStatus;
import com.skillhub.provider.CodeExecutionProvider;
import com.skillhub.repository.ProfessionalCodingSubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CodingEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(CodingEvaluationService.class);

    @Autowired
    private CodeExecutionProvider executionProvider;

    @Autowired
    private ProfessionalCodingSubmissionRepository submissionRepo;

    @Autowired
    private RateLimitingService rateLimitingService;

    @Autowired(required = false)
    private SqlEvaluationSandboxService sqlSandboxService;

    @Value("${skillhub.code-execution.max-submissions-per-question:10}")
    private int maxSubmissionsPerQuestion;

    @Value("${skillhub.code-execution.submission-cooldown-seconds:5}")
    private int submissionCooldownSeconds;

    @Transactional
    public CodeExecutionResult evaluateSubmission(ProfessionalExamAttempt attempt, ProfessionalExamQuestion question, String sourceCode, String language) {
        // Validation: Attempt Status
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new BusinessException("ATTEMPT_LOCKED");
        }

        // Validation: Question Type
        if (question.getQuestionType() != QuestionType.CODING 
                && question.getQuestionType() != QuestionType.DEBUGGING 
                && question.getQuestionType() != QuestionType.SQL) {
            throw new BusinessException("QUESTION_TYPE_NOT_CODE_EVALUATED");
        }

        // Validation: Max Submissions per question to prevent reverse engineering
        List<ProfessionalCodingSubmission> previousSubmissions = submissionRepo.findByAttemptIdAndQuestionId(attempt.getId(), question.getId());
        if (previousSubmissions.size() >= maxSubmissionsPerQuestion) {
            throw new BusinessException("MAX_SUBMISSIONS_REACHED");
        }

        // Validation: Cooldown between submissions to prevent double-click / rapid spam
        String rateLimitKey = "code-submit:" + attempt.getId() + ":" + question.getId();
        if (!rateLimitingService.allowRequest(rateLimitKey, 1, submissionCooldownSeconds)) {
            long remaining = rateLimitingService.getRemainingCooldownSeconds(rateLimitKey);
            log.warn("Coding submission cooldown active for attempt {} question {}. {}s remaining", attempt.getId(), question.getId(), remaining);
            throw new BusinessException("SUBMISSION_COOLDOWN_ACTIVE");
        }

        CodeExecutionResult result = new CodeExecutionResult();

        if (question.getQuestionType() == QuestionType.SQL && sqlSandboxService != null) {
            // Dedicated, ephemeral, isolated SQL sandbox execution
            var sqlResult = sqlSandboxService.evaluate(sourceCode, question.getCorrectAnswer(), null);
            result.setStatus(sqlResult.isPassed() ? SubmissionStatus.PASSED : SubmissionStatus.FAILED);
            result.setExecutionTimeMs(sqlResult.getExecutionTimeMs());
            result.setMemoryUsedMb(15L);
            result.setTestsPassed(sqlResult.isPassed() ? 1 : 0);
            result.setTestsTotal(1);
            result.setOutputLogs(sqlResult.getMessage());
            if (!sqlResult.isPassed()) {
                result.setErrorMessage(sqlResult.getMessage());
            }
        } else {
            // Programming language execution via isolated provider abstraction
            CodeSubmission sub = new CodeSubmission();
            sub.setLanguage(language);
            sub.setSourceCode(sourceCode);
            sub.setTimeLimit(question.getTimeLimit() != null ? question.getTimeLimit() : 2000);
            sub.setMemoryLimit(question.getMemoryLimit() != null ? question.getMemoryLimit() : 256);

            result = executionProvider.execute(sub);
        }

        ProfessionalCodingSubmission record = new ProfessionalCodingSubmission();
        record.setAttempt(attempt);
        record.setQuestion(question);
        record.setLanguage(language);
        record.setSourceCode(sourceCode);
        record.setStatus(result.getStatus());
        record.setExecutionTimeMs(result.getExecutionTimeMs());
        record.setMemoryUsedMb(result.getMemoryUsedMb());
        record.setTestsPassed(result.getTestsPassed());
        record.setTestsTotal(result.getTestsTotal());

        if (result.getTestsTotal() != null && result.getTestsTotal() > 0) {
            record.setScore((double) result.getTestsPassed() / result.getTestsTotal() * 100.0);
        } else {
            record.setScore(0.0);
        }

        submissionRepo.save(record);
        log.info("Saved coding submission #{} for attempt {}, question {}. Status={}",
                previousSubmissions.size() + 1, attempt.getId(), question.getId(), result.getStatus());
        return result;
    }
}
