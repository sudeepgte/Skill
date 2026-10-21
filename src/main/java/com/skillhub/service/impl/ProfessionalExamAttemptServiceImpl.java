package com.skillhub.service.impl;

import com.skillhub.dto.*;
import com.skillhub.exception.BusinessException;
import com.skillhub.model.*;
import com.skillhub.model.enums.*;
import com.skillhub.repository.*;
import com.skillhub.service.CodingEvaluationService;
import com.skillhub.service.ProfessionalExamAttemptService;
import com.skillhub.service.RateLimitingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProfessionalExamAttemptServiceImpl implements ProfessionalExamAttemptService {

    private static final Logger log = LoggerFactory.getLogger(ProfessionalExamAttemptServiceImpl.class);

    @Autowired
    private ProfessionalExamAttemptRepository attemptRepository;
    @Autowired
    private ProfessionalExamApplicationRepository applicationRepository;
    @Autowired
    private ProfessionalExamQuestionRepository questionRepository;
    @Autowired
    private ProfessionalExamAttemptQuestionRepository attemptQuestionRepository;
    @Autowired
    private ProfessionalExamAnswerRepository answerRepository;
    @Autowired
    private ProfessionalExamEvaluationServiceImpl evaluationService;
    @Autowired
    private CodingEvaluationService codingEvaluationService;
    @Autowired
    private RateLimitingService rateLimitingService;
    @Autowired
    private com.skillhub.service.ExamObservabilityService observabilityService;

    @Override
    @Transactional
    public ExamAttemptOverviewResponse startExam(ExamStartRequest request, User user) {
        // Rate limiting start attempts: max 5 starts per minute per user
        if (!rateLimitingService.allowRequest("exam-start:" + user.getId(), 5, 60)) {
            throw new BusinessException("RATE_LIMIT_EXCEEDED");
        }

        ProfessionalExamApplication app = applicationRepository.findById(request.getApplicationId())
                .orElseThrow(() -> new BusinessException("APPLICATION_NOT_FOUND"));

        if (!app.getUser().getId().equals(user.getId())) {
            throw new BusinessException("UNAUTHORIZED_ATTEMPT_ACCESS");
        }
        if (app.getStatus() != ApplicationStatus.APPROVED && app.getStatus() != ApplicationStatus.EXAM_STARTED) {
            throw new BusinessException("APPLICATION_NOT_APPROVED");
        }
        if (app.getProfessionalExam().isApplicationRequired() 
                && app.getPaymentStatus() != PaymentStatus.SUCCESS 
                && app.getApplicationFee() != null 
                && app.getApplicationFee().doubleValue() > 0) {
            throw new BusinessException("PAYMENT_NOT_COMPLETED");
        }
        
        // Check for active attempts
        List<AttemptStatus> activeStatuses = List.of(AttemptStatus.IN_PROGRESS, AttemptStatus.PAUSED);
        List<ProfessionalExamAttempt> activeAttempts = attemptRepository.findByStudentIdAndExamVersionProfessionalExamIdAndStatusIn(
                user.getId(), app.getProfessionalExam().getId(), activeStatuses);
        
        if (!activeAttempts.isEmpty()) {
            return getAttemptOverview(activeAttempts.get(0).getId(), user);
        }

        // Check max attempts
        long totalAttempts = attemptRepository.findByApplicationIdAndStudentId(app.getId(), user.getId()).stream().count();
        if (totalAttempts >= app.getProfessionalExam().getMaximumAttempts()) {
            throw new BusinessException("MAX_ATTEMPTS_EXCEEDED");
        }

        // Create attempt snapshot
        ProfessionalExamAttempt attempt = new ProfessionalExamAttempt();
        attempt.setApplication(app);
        attempt.setStudent(user);
        attempt.setExamVersion(app.getExamVersion());
        attempt.setStartedAt(LocalDateTime.now());
        attempt.setStatus(AttemptStatus.IN_PROGRESS);
        attempt.setCurrentQuestion(1L);
        attempt = attemptRepository.save(attempt);

        // Generate immutable attempt question set
        generateQuestionSet(attempt);

        app.setStatus(ApplicationStatus.EXAM_STARTED);
        applicationRepository.save(app);

        observabilityService.increment("exam_start_count");
        log.info("Started exam attempt {} for user {} on exam {}", attempt.getId(), user.getId(), app.getProfessionalExam().getTitle());
        return getAttemptOverview(attempt.getId(), user);
    }

    private void generateQuestionSet(ProfessionalExamAttempt attempt) {
        List<ProfessionalExamQuestion> allQuestions = new ArrayList<>(questionRepository.findByExamSectionExamVersionId(attempt.getExamVersion().getId()));
        Collections.shuffle(allQuestions);
        
        int number = 1;
        for (ProfessionalExamQuestion q : allQuestions) {
            ProfessionalExamAttemptQuestion aq = new ProfessionalExamAttemptQuestion(attempt, q, number, number);
            attemptQuestionRepository.save(aq);
            number++;
        }
    }

    @Override
    @Transactional
    public ExamAttemptOverviewResponse getAttemptOverview(Long attemptId, User user) {
        ProfessionalExamAttempt attempt = getValidAttempt(attemptId, user, true);
        checkExpiryAndSubmit(attempt);

        List<ProfessionalExamAttemptQuestion> qs = attemptQuestionRepository.findByAttemptIdOrderByDisplayOrderAsc(attemptId);
        
        long elapsed = Duration.between(attempt.getStartedAt(), LocalDateTime.now()).getSeconds();
        long durationSec = attempt.getExamVersion().getDurationMinutes() * 60L;
        long remaining = Math.max(0, durationSec - elapsed);

        ExamAttemptOverviewResponse res = new ExamAttemptOverviewResponse();
        res.setAttemptId(attempt.getId());
        res.setExamTitle(attempt.getExamVersion().getProfessionalExam().getTitle());
        res.setTotalQuestions(qs.size());
        res.setAnsweredQuestions((int) qs.stream().filter(ProfessionalExamAttemptQuestion::isAnswered).count());
        res.setUnansweredQuestions((int) qs.stream().filter(q -> !q.isAnswered()).count());
        res.setMarkedForReview((int) qs.stream().filter(ProfessionalExamAttemptQuestion::isMarkedForReview).count());
        res.setRemainingSeconds(remaining);
        res.setStartedAt(attempt.getStartedAt());
        res.setStatus(attempt.getStatus().name());
        return res;
    }

    @Override
    @Transactional
    public ExamQuestionResponse getQuestion(Long attemptId, int questionNumber, User user) {
        ProfessionalExamAttempt attempt = getValidAttempt(attemptId, user, true);
        checkExpiryAndSubmit(attempt);

        ProfessionalExamAttemptQuestion aq = attemptQuestionRepository.findByAttemptIdAndQuestionNumber(attemptId, questionNumber)
                .orElseThrow(() -> new BusinessException("QUESTION_NOT_FOUND"));
        
        aq.setVisited(true);
        attemptQuestionRepository.save(aq);
        attempt.setCurrentQuestion((long) questionNumber);
        attemptRepository.save(attempt);

        ProfessionalExamQuestion q = aq.getQuestion();
        ExamQuestionResponse res = new ExamQuestionResponse();
        res.setQuestionNumber(aq.getQuestionNumber());
        long totalQs = attemptQuestionRepository.findByAttemptIdOrderByDisplayOrderAsc(attemptId).size();
        res.setTotalQuestions((int) totalQs);
        res.setSection(q.getExamSection().getName());
        res.setQuestionType(q.getQuestionType().name());
        res.setDifficulty(q.getDifficulty().name());
        res.setQuestionText(q.getQuestionText());
        res.setOptions(q.getOptions());
        res.setMarks(q.getMarks());
        res.setNegativeMarks(q.getNegativeMarks() != null ? q.getNegativeMarks().doubleValue() : 0.0);
        res.setAnswered(aq.isAnswered());
        res.setMarkedForReview(aq.isMarkedForReview());
        res.setCodeLanguage(q.getCodeLanguage());
        res.setStarterCode(q.getCodeStarterTemplate());

        answerRepository.findByAttemptIdAndQuestionId(attemptId, q.getId()).ifPresent(ans -> {
            if (ans.getSelectedOption() != null && !ans.getSelectedOption().isEmpty()) {
                res.setSavedAnswer(ans.getSelectedOption());
            } else if (ans.getCodeAnswer() != null && !ans.getCodeAnswer().isEmpty()) {
                res.setSavedAnswer(ans.getCodeAnswer());
            } else if (ans.getTextAnswer() != null && !ans.getTextAnswer().isEmpty()) {
                res.setSavedAnswer(ans.getTextAnswer());
            }
        });

        return res;
    }

    @Override
    @Transactional
    public AnswerSubmitResponse saveAnswer(Long attemptId, int questionNumber, AnswerSubmitRequest req, User user) {
        // Enforce rate limiting: 120 saves per minute per candidate
        if (!rateLimitingService.allowRequest("save-answer:" + user.getId(), 120, 60)) {
            throw new BusinessException("RATE_LIMIT_EXCEEDED");
        }

        ProfessionalExamAttempt attempt = getValidAttempt(attemptId, user, false);
        if (checkExpiryAndSubmit(attempt)) {
            throw new BusinessException("ATTEMPT_EXPIRED");
        }

        ProfessionalExamAttemptQuestion aq = attemptQuestionRepository.findByAttemptIdAndQuestionNumber(attemptId, questionNumber)
                .orElseThrow(() -> new BusinessException("QUESTION_NOT_FOUND"));
        
        ProfessionalExamQuestion q = aq.getQuestion();

        ProfessionalExamAnswer ans = answerRepository.findByAttemptIdAndQuestionId(attemptId, q.getId())
                .orElse(new ProfessionalExamAnswer());
        
        ans.setAttempt(attempt);
        ans.setQuestion(q);
        ans.setSelectedOption(req.getSelectedOption());
        ans.setTextAnswer(req.getTextAnswer());
        ans.setCodeAnswer(req.getCodeAnswer());
        ans.setSubmittedAt(LocalDateTime.now());
        
        answerRepository.save(ans);

        aq.setAnswered(req.getSelectedOption() != null || req.getTextAnswer() != null || req.getCodeAnswer() != null);
        attemptQuestionRepository.save(aq);

        return new AnswerSubmitResponse(true, LocalDateTime.now());
    }

    @Override
    @Transactional
    public void markForReview(Long attemptId, int questionNumber, boolean review, User user) {
        ProfessionalExamAttempt attempt = getValidAttempt(attemptId, user, false);
        if (checkExpiryAndSubmit(attempt)) {
            throw new BusinessException("ATTEMPT_EXPIRED");
        }

        ProfessionalExamAttemptQuestion aq = attemptQuestionRepository.findByAttemptIdAndQuestionNumber(attemptId, questionNumber)
                .orElseThrow(() -> new BusinessException("QUESTION_NOT_FOUND"));
        aq.setMarkedForReview(review);
        attemptQuestionRepository.save(aq);
    }

    @Override
    @Transactional(readOnly = true)
    public QuestionPaletteResponse getPalette(Long attemptId, User user) {
        ProfessionalExamAttempt attempt = getValidAttempt(attemptId, user, true);
        List<ProfessionalExamAttemptQuestion> qs = attemptQuestionRepository.findByAttemptIdOrderByDisplayOrderAsc(attemptId);
        
        List<QuestionPaletteItem> items = qs.stream()
            .map(q -> new QuestionPaletteItem(q.getQuestionNumber(), q.isAnswered(), q.isMarkedForReview()))
            .collect(Collectors.toList());
            
        QuestionPaletteResponse res = new QuestionPaletteResponse();
        res.setQuestions(items);
        return res;
    }

    @Override
    @Transactional
    public void submitExam(Long attemptId, User user) {
        // Enforce rate limiting: 5 submit calls per minute
        if (!rateLimitingService.allowRequest("exam-submit:" + attemptId, 5, 60)) {
            throw new BusinessException("RATE_LIMIT_EXCEEDED");
        }

        ProfessionalExamAttempt attempt = getValidAttempt(attemptId, user, true);
        if (attempt.getStatus() == AttemptStatus.SUBMITTED 
                || attempt.getStatus() == AttemptStatus.AUTO_SUBMITTED 
                || attempt.getStatus() == AttemptStatus.EVALUATING 
                || attempt.getStatus() == AttemptStatus.EVALUATED) {
            return; // Idempotent: already submitted
        }
        
        try {
            attempt.setStatus(AttemptStatus.SUBMITTED);
            attempt.setSubmittedAt(LocalDateTime.now());
            attemptRepository.save(attempt);
            
            // Evaluate attempt deterministically
            evaluationService.evaluateAttempt(attemptId);
            log.info("Candidate {} submitted exam attempt {}", user.getId(), attemptId);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException | jakarta.persistence.OptimisticLockException ex) {
            log.warn("Concurrent submission handled idempotently for attempt {}", attemptId);
        }
    }

    @Override
    @Transactional
    public CodeExecutionResult executeCode(Long attemptId, CodingExecutionRequest req, User user) {
        ProfessionalExamAttempt attempt = getValidAttempt(attemptId, user, false);
        if (checkExpiryAndSubmit(attempt)) {
            throw new BusinessException("ATTEMPT_EXPIRED");
        }

        ProfessionalExamAttemptQuestion aq = attemptQuestionRepository.findByAttemptIdAndQuestionNumber(attemptId, req.getQuestionNumber())
                .orElseThrow(() -> new BusinessException("QUESTION_NOT_FOUND"));

        ProfessionalExamQuestion q = aq.getQuestion();

        // Preserve current code answer as well
        ProfessionalExamAnswer ans = answerRepository.findByAttemptIdAndQuestionId(attemptId, q.getId())
                .orElse(new ProfessionalExamAnswer());
        ans.setAttempt(attempt);
        ans.setQuestion(q);
        ans.setCodeAnswer(req.getSourceCode());
        ans.setSubmittedAt(LocalDateTime.now());
        answerRepository.save(ans);
        aq.setAnswered(true);
        attemptQuestionRepository.save(aq);

        // Execute code via isolated provider abstraction with rate limiting and quota checks
        return codingEvaluationService.evaluateSubmission(attempt, q, req.getSourceCode(), req.getLanguage());
    }

    private ProfessionalExamAttempt getValidAttempt(Long attemptId, User user, boolean allowLocked) {
        ProfessionalExamAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new BusinessException("ATTEMPT_NOT_FOUND"));
        
        // IDOR Protection: User ownership verification
        boolean isAdmin = user != null && ("ADMIN".equalsIgnoreCase(user.getRole()) || "ROLE_ADMIN".equalsIgnoreCase(user.getRole()));
        if (!isAdmin && !attempt.getStudent().getId().equals(user.getId())) {
            observabilityService.increment("authorization_failure_count");
            log.warn("IDOR attempt detected: User {} tried accessing Attempt {}", user.getId(), attemptId);
            throw new BusinessException("UNAUTHORIZED_ATTEMPT_ACCESS");
        }

        if (!allowLocked && attempt.getStatus() != AttemptStatus.IN_PROGRESS && attempt.getStatus() != AttemptStatus.PAUSED) {
            throw new BusinessException("ATTEMPT_LOCKED");
        }
        return attempt;
    }

    private boolean checkExpiryAndSubmit(ProfessionalExamAttempt attempt) {
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) return false;
        
        long elapsed = Duration.between(attempt.getStartedAt(), LocalDateTime.now()).getSeconds();
        long durationSec = attempt.getExamVersion().getDurationMinutes() * 60L;
        
        if (elapsed > durationSec) {
            try {
                attempt.setStatus(AttemptStatus.AUTO_SUBMITTED);
                attempt.setSubmittedAt(attempt.getStartedAt().plusSeconds(durationSec));
                attemptRepository.save(attempt);
                evaluationService.evaluateAttempt(attempt.getId());
                observabilityService.increment("auto_submission_count");
                log.info("Server-authoritative auto-submission executed for expired attempt {}", attempt.getId());
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException | jakarta.persistence.OptimisticLockException ex) {
                log.warn("Concurrent auto-submission handled idempotently for attempt {}", attempt.getId());
            }
            return true;
        }
        return false;
    }
}
