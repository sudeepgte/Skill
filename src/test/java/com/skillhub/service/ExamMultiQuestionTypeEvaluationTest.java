package com.skillhub.service;

import com.skillhub.model.*;
import com.skillhub.model.enums.*;
import com.skillhub.repository.*;
import com.skillhub.service.impl.ProfessionalExamEvaluationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Multi-Question Type Exam Evaluation Tests")
public class ExamMultiQuestionTypeEvaluationTest {

    @Mock private ProfessionalExamAttemptRepository attemptRepo;
    @Mock private ProfessionalExamAnswerRepository answerRepo;
    @Mock private ProfessionalExamResultRepository resultRepo;
    @Mock private ProfessionalExamSkillResultRepository skillResultRepo;
    @Mock private ProfessionalExamApplicationRepository applicationRepo;
    @Mock private ProfessionalCertificateService certificateService;
    @Mock private ProfessionalCodingSubmissionRepository codingSubmissionRepo;
    @Mock private SqlEvaluationSandboxService sqlSandboxService;
    @Mock private ExamObservabilityService observabilityService;

    @InjectMocks
    private ProfessionalExamEvaluationServiceImpl evaluationService;

    private ProfessionalExamAttempt attempt;
    private ProfessionalExamVersion version;
    private ProfessionalExamApplication application;

    @BeforeEach
    void setUp() {
        ProfessionalExam exam = new ProfessionalExam();
        exam.setId(1L);
        exam.setTitle("Full Stack Certification");

        version = new ProfessionalExamVersion();
        version.setId(10L);
        version.setProfessionalExam(exam);
        version.setTotalMarks(100);
        version.setPassingMarks(60);

        application = new ProfessionalExamApplication();
        application.setId(50L);
        application.setStatus(ApplicationStatus.EXAM_STARTED);

        attempt = new ProfessionalExamAttempt();
        attempt.setId(100L);
        attempt.setExamVersion(version);
        attempt.setApplication(application);
        attempt.setStatus(AttemptStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("Evaluation: MCQ with single choice correctly marks positive score")
    void testMcqEvaluationCorrect() {
        when(resultRepo.findByAttemptId(100L)).thenReturn(Optional.empty());
        when(attemptRepo.findById(100L)).thenReturn(Optional.of(attempt));
        when(resultRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProfessionalExamQuestion q = new ProfessionalExamQuestion();
        q.setId(1L);
        q.setQuestionType(QuestionType.MCQ);
        q.setMarks(10);
        q.setNegativeMarks(BigDecimal.valueOf(2.5));
        q.setCorrectAnswer("B");

        ProfessionalExamAnswer ans = new ProfessionalExamAnswer();
        ans.setAttempt(attempt);
        ans.setQuestion(q);
        ans.setSelectedOption("B");

        when(answerRepo.findByAttemptId(100L)).thenReturn(List.of(ans));

        ProfessionalExamResult result = evaluationService.evaluateAttempt(100L);

        assertEquals(1, result.getCorrectAnswers());
        assertEquals(0, result.getWrongAnswers());
        assertEquals(BigDecimal.valueOf(10), result.getObtainedMarks());
        assertTrue(ans.getIsCorrect());
    }

    @Test
    @DisplayName("Evaluation: MSQ order-independent set matching passes ('A, C' == 'C, A')")
    void testMsqOrderIndependentMatching() {
        when(resultRepo.findByAttemptId(100L)).thenReturn(Optional.empty());
        when(attemptRepo.findById(100L)).thenReturn(Optional.of(attempt));
        when(resultRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProfessionalExamQuestion q = new ProfessionalExamQuestion();
        q.setId(2L);
        q.setQuestionType(QuestionType.MULTIPLE_SELECT);
        q.setMarks(20);
        q.setCorrectAnswer("A, C");

        ProfessionalExamAnswer ans = new ProfessionalExamAnswer();
        ans.setAttempt(attempt);
        ans.setQuestion(q);
        ans.setSelectedOption("C, A"); // Reversed order with spaces

        when(answerRepo.findByAttemptId(100L)).thenReturn(List.of(ans));

        ProfessionalExamResult result = evaluationService.evaluateAttempt(100L);

        assertEquals(1, result.getCorrectAnswers());
        assertEquals(BigDecimal.valueOf(20), result.getObtainedMarks());
        assertTrue(ans.getIsCorrect());
    }

    @Test
    @DisplayName("Evaluation: Coding question awards proportional marks based on sandbox test score")
    void testCodingQuestionProportionalScoring() {
        when(resultRepo.findByAttemptId(100L)).thenReturn(Optional.empty());
        when(attemptRepo.findById(100L)).thenReturn(Optional.of(attempt));
        when(resultRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProfessionalExamQuestion q = new ProfessionalExamQuestion();
        q.setId(3L);
        q.setQuestionType(QuestionType.CODING);
        q.setMarks(30);

        ProfessionalExamAnswer ans = new ProfessionalExamAnswer();
        ans.setAttempt(attempt);
        ans.setQuestion(q);
        ans.setCodeAnswer("def solve(): pass");

        // 8 out of 10 test cases passed (80% score)
        ProfessionalCodingSubmission sub = new ProfessionalCodingSubmission();
        sub.setId(901L);
        sub.setScore(80.0);
        sub.setTestsPassed(8);
        sub.setTestsTotal(10);

        when(codingSubmissionRepo.findByAttemptIdAndQuestionId(100L, 3L)).thenReturn(List.of(sub));
        when(answerRepo.findByAttemptId(100L)).thenReturn(List.of(ans));

        ProfessionalExamResult result = evaluationService.evaluateAttempt(100L);

        // 80% of 30 marks = 24.00 marks
        assertEquals(BigDecimal.valueOf(24.0).setScale(2), result.getObtainedMarks().setScale(2));
        assertTrue(ans.getIsCorrect());
    }

    @Test
    @DisplayName("Evaluation: SQL question evaluates through isolated SQL sandbox")
    void testSqlQuestionSandboxEvaluation() {
        when(resultRepo.findByAttemptId(100L)).thenReturn(Optional.empty());
        when(attemptRepo.findById(100L)).thenReturn(Optional.of(attempt));
        when(resultRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProfessionalExamQuestion q = new ProfessionalExamQuestion();
        q.setId(4L);
        q.setQuestionType(QuestionType.SQL);
        q.setMarks(25);
        q.setCorrectAnswer("SELECT * FROM employees WHERE salary > 50000");

        ProfessionalExamAnswer ans = new ProfessionalExamAnswer();
        ans.setAttempt(attempt);
        ans.setQuestion(q);
        ans.setTextAnswer("SELECT * FROM employees WHERE salary > 50000");

        when(sqlSandboxService.evaluate(anyString(), anyString(), any())).thenReturn(
                new SqlEvaluationSandboxService.SqlEvaluationResult(true, 5, 12L, "Verified successfully")
        );
        when(answerRepo.findByAttemptId(100L)).thenReturn(List.of(ans));

        ProfessionalExamResult result = evaluationService.evaluateAttempt(100L);

        assertEquals(1, result.getCorrectAnswers());
        assertEquals(BigDecimal.valueOf(25), result.getObtainedMarks());
        assertTrue(ans.getIsCorrect());
    }

    @Test
    @DisplayName("Evaluation: Distinction grade awarded for percentage >= 90%")
    void testDistinctionGradeAssignment() {
        when(resultRepo.findByAttemptId(100L)).thenReturn(Optional.empty());
        when(attemptRepo.findById(100L)).thenReturn(Optional.of(attempt));
        when(resultRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProfessionalExamQuestion q = new ProfessionalExamQuestion();
        q.setId(5L);
        q.setQuestionType(QuestionType.MCQ);
        q.setMarks(95); // 95 / 100 = 95%
        q.setCorrectAnswer("A");

        ProfessionalExamAnswer ans = new ProfessionalExamAnswer();
        ans.setAttempt(attempt);
        ans.setQuestion(q);
        ans.setSelectedOption("A");

        when(answerRepo.findByAttemptId(100L)).thenReturn(List.of(ans));

        ProfessionalExamResult result = evaluationService.evaluateAttempt(100L);

        assertEquals("PASS", result.getStatus());
        assertEquals("DISTINCTION", result.getGrade());
        verify(certificateService).issueCertificate(any());
    }
}
