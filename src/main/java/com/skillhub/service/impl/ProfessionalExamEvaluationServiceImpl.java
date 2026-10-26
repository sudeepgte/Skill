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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ProfessionalExamEvaluationServiceImpl {

    private static final Logger log = LoggerFactory.getLogger(ProfessionalExamEvaluationServiceImpl.class);

    private static final Pattern EXPLICIT_LETTER_PATTERN = 
        Pattern.compile("^\\s*(?:[\\[\\(]\\s*([A-Za-z])\\s*[\\]\\)]|([A-Za-z])\\s*[\\.\\:\\)]\\s+|Option\\s+([A-Za-z])\\b|^\\s*([A-Za-z])\\s*$)", Pattern.CASE_INSENSITIVE);

    private static final Pattern STRIP_PREFIX_PATTERN = 
        Pattern.compile("^\\s*(?:[\\[\\(]\\s*[A-Za-z]\\s*[\\]\\)]|[A-Za-z]\\s*[\\.\\:\\)]|Option\\s+[A-Za-z]\\s*[:\\-]?)\\s*[-:–]?\\s*", Pattern.CASE_INSENSITIVE);

    @Autowired
    private ProfessionalExamAttemptRepository attemptRepository;
    @Autowired
    private ProfessionalExamAnswerRepository answerRepository;
    @Autowired
    private ProfessionalExamResultRepository resultRepository;
    @Autowired(required = false)
    private ProfessionalExamSkillResultRepository skillResultRepository;
    @Autowired
    private ProfessionalExamApplicationRepository applicationRepository;
    @Autowired(required = false)
    private ProfessionalCertificateService certificateService;
    @Autowired(required = false)
    private ProfessionalCodingSubmissionRepository codingSubmissionRepository;
    @Autowired(required = false)
    private SqlEvaluationSandboxService sqlSandboxService;
    @Autowired(required = false)
    private ExamObservabilityService observabilityService;
    @Autowired(required = false)
    private ProfessionalExamAttemptQuestionRepository attemptQuestionRepository;

    @Transactional
    public synchronized ProfessionalExamResult evaluateAttempt(Long attemptId) {
        return evaluateAttempt(attemptId, false);
    }

    @Transactional
    public synchronized ProfessionalExamResult evaluateAttempt(Long attemptId, boolean forceReevaluate) {
        Optional<ProfessionalExamResult> existingResult = resultRepository.findByAttemptId(attemptId);
        ProfessionalExamAttempt attempt = attemptRepository.findById(attemptId).orElseThrow();

        List<ProfessionalExamAttemptQuestion> attemptQuestions = (attemptQuestionRepository != null)
                ? attemptQuestionRepository.findByAttemptIdOrderByDisplayOrderAsc(attemptId)
                : Collections.emptyList();

        int expectedQuestionCount = attemptQuestions.size();

        if (existingResult.isPresent() && !forceReevaluate) {
            ProfessionalExamResult res = existingResult.get();
            int evaluatedTotal = res.getCorrectAnswers() + res.getWrongAnswers() + res.getUnansweredAnswers();
            if (expectedQuestionCount == 0 || evaluatedTotal == expectedQuestionCount) {
                log.info("Attempt {} already has finalized result. Returning existing.", attemptId);
                return res;
            }
            log.info("Existing result for attempt {} has invalid count sum {} vs expected {}. Re-evaluating.", 
                    attemptId, evaluatedTotal, expectedQuestionCount);
        }

        attempt.setStatus(AttemptStatus.EVALUATING);
        attemptRepository.save(attempt);

        BigDecimal obtainedMarks = BigDecimal.ZERO;
        int correct = 0, wrong = 0, unanswered = 0;

        if (!attemptQuestions.isEmpty()) {
            for (ProfessionalExamAttemptQuestion aq : attemptQuestions) {
                ProfessionalExamQuestion q = aq.getQuestion();
                List<ProfessionalExamAnswer> ansList = answerRepository.findAllByAttemptIdAndQuestionId(attemptId, q.getId());

                ProfessionalExamAnswer ans;
                if (ansList.isEmpty()) {
                    ans = new ProfessionalExamAnswer();
                    ans.setAttempt(attempt);
                    ans.setQuestion(q);
                    ans.setSubmittedAt(LocalDateTime.now());
                } else {
                    ans = ansList.get(0);
                    if (ansList.size() > 1) {
                        for (int i = 1; i < ansList.size(); i++) {
                            try {
                                answerRepository.delete(ansList.get(i));
                            } catch (Exception ignored) {}
                        }
                    }
                }

                boolean isAnswered = isQuestionAnswered(ans);
                if (!isAnswered) {
                    unanswered++;
                    ans.setIsCorrect(false);
                    ans.setMarksObtained(BigDecimal.ZERO);
                    aq.setAnswered(false);
                    if (attemptQuestionRepository != null) {
                        attemptQuestionRepository.save(aq);
                    }
                    answerRepository.save(ans);
                    continue;
                }

                aq.setAnswered(true);
                if (attemptQuestionRepository != null) {
                    attemptQuestionRepository.save(aq);
                }

                EvaluationOutcome outcome = evaluateSingleQuestion(attemptId, q, ans);
                if (outcome.isCorrect) {
                    correct++;
                    ans.setIsCorrect(true);
                    ans.setMarksObtained(outcome.earnedMarks);
                    obtainedMarks = obtainedMarks.add(outcome.earnedMarks);
                } else {
                    wrong++;
                    ans.setIsCorrect(false);
                    ans.setMarksObtained(outcome.earnedMarks);
                    obtainedMarks = obtainedMarks.add(outcome.earnedMarks);
                }
                answerRepository.save(ans);
            }
        } else {
            // Fallback for tests or attempts without attempt_question records
            List<ProfessionalExamAnswer> answers = answerRepository.findByAttemptId(attemptId);
            for (ProfessionalExamAnswer ans : answers) {
                ProfessionalExamQuestion q = ans.getQuestion();
                boolean isAnswered = isQuestionAnswered(ans);
                if (!isAnswered) {
                    unanswered++;
                    ans.setIsCorrect(false);
                    ans.setMarksObtained(BigDecimal.ZERO);
                    answerRepository.save(ans);
                    continue;
                }

                EvaluationOutcome outcome = evaluateSingleQuestion(attemptId, q, ans);
                if (outcome.isCorrect) {
                    correct++;
                    ans.setIsCorrect(true);
                    ans.setMarksObtained(outcome.earnedMarks);
                    obtainedMarks = obtainedMarks.add(outcome.earnedMarks);
                } else {
                    wrong++;
                    ans.setIsCorrect(false);
                    ans.setMarksObtained(outcome.earnedMarks);
                    obtainedMarks = obtainedMarks.add(outcome.earnedMarks);
                }
                answerRepository.save(ans);
            }
        }

        obtainedMarks = obtainedMarks.max(BigDecimal.ZERO);

        int totalPossibleMarks = 0;
        if (!attemptQuestions.isEmpty()) {
            totalPossibleMarks = attemptQuestions.stream()
                    .mapToInt(aq -> aq.getQuestion().getMarks())
                    .sum();
        }
        if (totalPossibleMarks <= 0) {
            totalPossibleMarks = (attempt.getExamVersion() != null && attempt.getExamVersion().getTotalMarks() > 0)
                    ? attempt.getExamVersion().getTotalMarks()
                    : 100;
        }

        double percentage = Math.max(0.0, (obtainedMarks.doubleValue() / totalPossibleMarks) * 100.0);

        int passingMarks = 70;
        if (attempt.getExamVersion() != null && attempt.getExamVersion().getPassingMarks() > 0) {
            passingMarks = attempt.getExamVersion().getPassingMarks();
        } else if (attempt.getExamVersion() != null && attempt.getExamVersion().getProfessionalExam() != null 
                && attempt.getExamVersion().getProfessionalExam().getPassingMarks() > 0) {
            passingMarks = attempt.getExamVersion().getProfessionalExam().getPassingMarks();
        }

        boolean passed = obtainedMarks.compareTo(BigDecimal.valueOf(passingMarks)) >= 0 || percentage >= passingMarks;

        ProfessionalExamResult result = existingResult.orElse(new ProfessionalExamResult());
        result.setAttempt(attempt);
        result.setTotalMarks(totalPossibleMarks);
        result.setObtainedMarks(obtainedMarks);
        result.setPercentage(percentage);
        result.setCorrectAnswers(correct);
        result.setWrongAnswers(wrong);
        result.setUnansweredAnswers(unanswered);
        result.setStatus(passed ? "PASS" : "FAIL");

        if (passed) {
            if (percentage >= 90.0) result.setGrade("DISTINCTION");
            else if (percentage >= 75.0) result.setGrade("MERIT");
            else result.setGrade("PASS");
        } else {
            result.setGrade("FAIL");
        }

        result.setEvaluatedAt(LocalDateTime.now());
        ProfessionalExamResult savedResult = resultRepository.save(result);

        attempt.setTotalMarks(totalPossibleMarks);
        attempt.setObtainedMarks(Math.max(0, obtainedMarks.intValue()));
        attempt.setCorrectAnswers(correct);
        attempt.setWrongAnswers(wrong);
        attempt.setUnansweredAnswers(unanswered);
        attempt.setStatus(AttemptStatus.EVALUATED);
        attemptRepository.save(attempt);

        ProfessionalExamApplication app = attempt.getApplication();
        if (app != null) {
            app.setStatus(passed ? ApplicationStatus.PASSED : ApplicationStatus.FAILED);
            applicationRepository.save(app);
        }

        if (observabilityService != null) {
            observabilityService.increment("exam_submission_count");
        }

        if (passed && certificateService != null) {
            try {
                certificateService.issueCertificate(savedResult.getId());
                if (observabilityService != null) {
                    observabilityService.increment("certificate_generation_count");
                }
            } catch (Exception ex) {
                log.error("Failed to automatically issue certificate for passed attempt {}: {}", attemptId, ex.getMessage(), ex);
            }
        }

        log.info("Evaluated attempt {}: Score={}, Percentage={:.2f}%, Correct={}, Wrong={}, Skipped={}, Status={}",
                attemptId, obtainedMarks, percentage, correct, wrong, unanswered, result.getStatus());
        return savedResult;
    }

    private boolean isQuestionAnswered(ProfessionalExamAnswer ans) {
        if (ans == null) return false;
        if (ans.getSelectedOption() != null && !ans.getSelectedOption().trim().isEmpty()) return true;
        if (ans.getTextAnswer() != null && !ans.getTextAnswer().trim().isEmpty()) return true;
        if (ans.getCodeAnswer() != null && !ans.getCodeAnswer().trim().isEmpty()) return true;
        return false;
    }

    private static class EvaluationOutcome {
        final boolean isCorrect;
        final BigDecimal earnedMarks;
        EvaluationOutcome(boolean isCorrect, BigDecimal earnedMarks) {
            this.isCorrect = isCorrect;
            this.earnedMarks = earnedMarks;
        }
    }

    private EvaluationOutcome evaluateSingleQuestion(Long attemptId, ProfessionalExamQuestion q, ProfessionalExamAnswer ans) {
        QuestionType type = q.getQuestionType() != null ? q.getQuestionType() : QuestionType.MCQ;
        BigDecimal qMarks = BigDecimal.valueOf(q.getMarks() > 0 ? q.getMarks() : 1);
        BigDecimal penalty = q.getNegativeMarks() != null ? q.getNegativeMarks() : BigDecimal.ZERO;

        switch (type) {
            case MULTIPLE_SELECT:
            case MULTIPLE_CHOICE: {
                String selected = ans.getSelectedOption();
                if (selected == null || selected.trim().isEmpty()) {
                    return new EvaluationOutcome(false, BigDecimal.ZERO);
                }
                Set<String> candidateOptions = parseOptionSet(selected);
                Set<String> correctOptions = parseOptionSet(q.getCorrectAnswer());
                if (!candidateOptions.isEmpty() && candidateOptions.equals(correctOptions)) {
                    return new EvaluationOutcome(true, qMarks);
                } else {
                    return new EvaluationOutcome(false, penalty.negate());
                }
            }

            case CODING:
            case DEBUGGING: {
                if (codingSubmissionRepository != null) {
                    List<ProfessionalCodingSubmission> subs = codingSubmissionRepository.findByAttemptIdAndQuestionId(attemptId, q.getId());
                    if (!subs.isEmpty()) {
                        ProfessionalCodingSubmission best = subs.stream()
                                .max(Comparator.comparingDouble(s -> s.getScore() != null ? s.getScore() : 0.0))
                                .orElse(subs.get(subs.size() - 1));

                        double scorePct = best.getScore() != null ? best.getScore() : 0.0;
                        BigDecimal earned = BigDecimal.valueOf((scorePct / 100.0) * q.getMarks()).setScale(2, RoundingMode.HALF_UP);
                        boolean isCorr = scorePct > 0.0;
                        return new EvaluationOutcome(isCorr, isCorr ? earned : BigDecimal.ZERO);
                    }
                }
                if (ans.getCodeAnswer() != null && !ans.getCodeAnswer().trim().isEmpty()) {
                    return new EvaluationOutcome(false, BigDecimal.ZERO);
                }
                return new EvaluationOutcome(false, BigDecimal.ZERO);
            }

            case SQL: {
                String candidateSql = ans.getTextAnswer() != null && !ans.getTextAnswer().trim().isEmpty()
                        ? ans.getTextAnswer()
                        : ans.getSelectedOption();

                if (candidateSql != null && !candidateSql.trim().isEmpty() && sqlSandboxService != null) {
                    var sqlResult = sqlSandboxService.evaluate(candidateSql, q.getCorrectAnswer(), null);
                    if (sqlResult.isPassed()) {
                        return new EvaluationOutcome(true, qMarks);
                    } else {
                        if (observabilityService != null) {
                            observabilityService.increment("sql_execution_failure_count");
                        }
                        return new EvaluationOutcome(false, penalty.negate());
                    }
                }
                return new EvaluationOutcome(false, BigDecimal.ZERO);
            }

            case MCQ:
            case TRUE_FALSE:
            case SINGLE_CHOICE:
            case CODE_OUTPUT:
            case SCENARIO:
            case SHORT_ANSWER:
            default: {
                String selected = ans.getSelectedOption() != null && !ans.getSelectedOption().trim().isEmpty()
                        ? ans.getSelectedOption()
                        : ans.getTextAnswer();

                if (selected == null || selected.trim().isEmpty()) {
                    return new EvaluationOutcome(false, BigDecimal.ZERO);
                }

                boolean isMatch = isOptionMatch(selected, q.getCorrectAnswer(), q.getOptions());
                if (isMatch) {
                    return new EvaluationOutcome(true, qMarks);
                } else {
                    return new EvaluationOutcome(false, penalty.negate());
                }
            }
        }
    }

    public static String extractLetter(String s) {
        if (s == null) return null;
        Matcher m = EXPLICIT_LETTER_PATTERN.matcher(s.trim());
        if (m.find()) {
            for (int i = 1; i <= m.groupCount(); i++) {
                if (m.group(i) != null) {
                    return m.group(i).toUpperCase();
                }
            }
        }
        return null;
    }

    public static String stripPrefix(String s) {
        if (s == null) return "";
        return STRIP_PREFIX_PATTERN.matcher(s.trim()).replaceFirst("").trim();
    }

    public static boolean isOptionMatch(String candidate, String correct, List<String> options) {
        if (candidate == null || correct == null) return false;
        String candTrim = candidate.trim();
        String corrTrim = correct.trim();
        if (candTrim.isEmpty() || corrTrim.isEmpty()) return false;

        // 1. Direct case-insensitive match
        if (candTrim.equalsIgnoreCase(corrTrim)) return true;

        // 2. Extracted option letter match (e.g. candidate has "(B) Some text" and correct has "(B)" or "B")
        String candLetter = extractLetter(candTrim);
        String corrLetter = extractLetter(corrTrim);
        if (candLetter != null && corrLetter != null && candLetter.equalsIgnoreCase(corrLetter)) {
            return true;
        }

        // 3. Stripped body text match
        String candBody = stripPrefix(candTrim);
        String corrBody = stripPrefix(corrTrim);
        if (!candBody.isEmpty() && !corrBody.isEmpty() && candBody.equalsIgnoreCase(corrBody)) {
            return true;
        }

        // 4. Match against question options list
        if (options != null && !options.isEmpty()) {
            if (corrLetter != null) {
                int index = corrLetter.toUpperCase().charAt(0) - 'A';
                if (index >= 0 && index < options.size()) {
                    String opt = options.get(index);
                    if (opt != null) {
                        if (candTrim.equalsIgnoreCase(opt.trim())) return true;
                        String optBody = stripPrefix(opt.trim());
                        if (!optBody.isEmpty() && (candTrim.equalsIgnoreCase(optBody) || candBody.equalsIgnoreCase(optBody))) {
                            return true;
                        }
                    }
                }
            }
            if (candLetter != null) {
                int index = candLetter.toUpperCase().charAt(0) - 'A';
                if (index >= 0 && index < options.size()) {
                    String opt = options.get(index);
                    if (opt != null) {
                        if (corrTrim.equalsIgnoreCase(opt.trim())) return true;
                        String optBody = stripPrefix(opt.trim());
                        if (!optBody.isEmpty() && (corrTrim.equalsIgnoreCase(optBody) || corrBody.equalsIgnoreCase(optBody))) {
                            return true;
                        }
                    }
                }
            }

            // True / False normalization
            if (options.size() == 2) {
                boolean cTrue = isAffirmative(candTrim);
                boolean kTrue = isAffirmative(corrTrim);
                if (cTrue == kTrue && (cTrue || isNegative(candTrim)) && (kTrue || isNegative(corrTrim))) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean isAffirmative(String s) {
        if (s == null) return false;
        String t = s.trim().toLowerCase();
        return t.equals("true") || t.equals("t") || t.equals("a") || t.equals("(a)") || t.equals("1") || t.equals("yes");
    }

    private static boolean isNegative(String s) {
        if (s == null) return false;
        String t = s.trim().toLowerCase();
        return t.equals("false") || t.equals("f") || t.equals("b") || t.equals("(b)") || t.equals("0") || t.equals("no");
    }

    private Set<String> parseOptionSet(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptySet();
        }
        String cleaned = raw.replaceAll("[\\[\\]\"']", "").trim();
        String[] tokens = cleaned.split("[,;\\n]+");
        Set<String> set = new HashSet<>();
        for (String t : tokens) {
            String trimmed = t.trim();
            if (trimmed.isEmpty()) continue;
            String letter = extractLetter(trimmed);
            if (letter != null) {
                set.add(letter);
            } else {
                set.add(trimmed.toUpperCase());
            }
        }
        return set;
    }
}
