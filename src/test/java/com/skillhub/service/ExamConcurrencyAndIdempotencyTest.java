package com.skillhub.service;

import com.skillhub.model.*;
import com.skillhub.model.enums.*;
import com.skillhub.repository.ProfessionalCertificateRepository;
import com.skillhub.repository.ProfessionalExamAttemptRepository;
import com.skillhub.repository.ProfessionalExamResultRepository;
import com.skillhub.service.impl.ProfessionalCertificateServiceImpl;
import com.skillhub.service.impl.ProfessionalExamAttemptServiceImpl;
import com.skillhub.service.impl.ProfessionalExamEvaluationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Exam Concurrency, Idempotency & Lock Safety Tests")
public class ExamConcurrencyAndIdempotencyTest {

    @Mock private ProfessionalExamAttemptRepository attemptRepo;
    @Mock private ProfessionalExamResultRepository resultRepo;
    @Mock private ProfessionalCertificateRepository certificateRepo;
    @Mock private ProfessionalExamEvaluationServiceImpl evaluationService;
    @Mock private RateLimitingService rateLimitingService;
    @Mock private ExamObservabilityService observabilityService;

    @InjectMocks
    private ProfessionalExamAttemptServiceImpl attemptService;

    @InjectMocks
    private ProfessionalCertificateServiceImpl certificateService;

    private User student;
    private ProfessionalExamAttempt attempt;
    private ProfessionalExamResult result;

    @BeforeEach
    void setUp() {
        student = new User();
        student.setId(101L);
        student.setEmail("student@skillhub.com");

        ProfessionalExam exam = new ProfessionalExam();
        exam.setId(1L);
        exam.setTitle("Cloud Engineer");
        exam.setTrack(ExamTrack.CLOUD);

        ProfessionalExamVersion version = new ProfessionalExamVersion();
        version.setId(10L);
        version.setProfessionalExam(exam);
        version.setDurationMinutes(60);

        attempt = new ProfessionalExamAttempt();
        attempt.setId(201L);
        attempt.setStudent(student);
        attempt.setExamVersion(version);
        attempt.setStartedAt(LocalDateTime.now().minusMinutes(10));
        attempt.setStatus(AttemptStatus.IN_PROGRESS);

        result = new ProfessionalExamResult();
        result.setId(301L);
        result.setAttempt(attempt);
        result.setStatus("PASS");
        result.setGrade("MERIT");
        result.setObtainedMarks(BigDecimal.valueOf(80));
    }

    @Test
    @DisplayName("Idempotency: Repeated submitExam on submitted attempt does not trigger second evaluation")
    void testSubmitExamIdempotency() {
        when(rateLimitingService.allowRequest(any(), anyInt(), anyInt())).thenReturn(true);
        when(attemptRepo.findById(201L)).thenReturn(Optional.of(attempt));

        attempt.setStatus(AttemptStatus.SUBMITTED); // Already submitted

        attemptService.submitExam(201L, student);

        // Verification: evaluationService must NEVER be called again
        verify(evaluationService, never()).evaluateAttempt(201L);
    }

    @Test
    @DisplayName("Certificate Idempotency: Multiple issue calls return the same existing certificate")
    void testCertificateGenerationIdempotency() {
        ProfessionalCertificate existingCert = new ProfessionalCertificate();
        existingCert.setId(5001L);
        existingCert.setCertificateId("SH-CLOUD-EXISTING");
        existingCert.setResult(result);
        existingCert.setStudent(student);

        when(certificateRepo.findByResultId(301L)).thenReturn(Optional.of(existingCert));

        ProfessionalCertificate cert1 = certificateService.issueCertificate(301L);
        ProfessionalCertificate cert2 = certificateService.issueCertificate(301L);

        assertEquals("SH-CLOUD-EXISTING", cert1.getCertificateId());
        assertEquals("SH-CLOUD-EXISTING", cert2.getCertificateId());
        verify(certificateRepo, never()).save(any());
    }

    @Test
    @DisplayName("Concurrency: Simultaneous submission calls handle optimistic lock without crashing")
    void testConcurrentSubmissionsGracefulHandling() throws InterruptedException {
        when(rateLimitingService.allowRequest(any(), anyInt(), anyInt())).thenReturn(true);
        when(attemptRepo.findById(201L)).thenReturn(Optional.of(attempt));

        // Simulate 4 concurrent browser tabs submitting the same attempt
        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successfulCalls = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    attemptService.submitExam(201L, student);
                    successfulCalls.incrementAndGet();
                } catch (Exception ignored) {}
            });
        }

        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));

        assertEquals(threads, successfulCalls.get(), "All concurrent submission requests must complete gracefully");
    }
}
