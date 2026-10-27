package com.skillhub.service;

import com.skillhub.dto.AnswerSubmitRequest;
import com.skillhub.dto.ExamStartRequest;
import com.skillhub.model.*;
import com.skillhub.model.enums.*;
import com.skillhub.repository.*;
import com.skillhub.service.impl.ProfessionalExamAttemptServiceImpl;
import com.skillhub.service.impl.ProfessionalExamEvaluationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Candidate Concurrency & Exam Engine Load Benchmark")
public class ExamLoadAndPerformanceBenchmarkTest {

    @Mock private ProfessionalExamAttemptRepository attemptRepo;
    @Mock private ProfessionalExamApplicationRepository applicationRepo;
    @Mock private ProfessionalExamQuestionRepository questionRepo;
    @Mock private ProfessionalExamAttemptQuestionRepository attemptQuestionRepo;
    @Mock private ProfessionalExamAnswerRepository answerRepo;
    @Mock private ProfessionalExamResultRepository resultRepo;
    @Mock private ProfessionalExamSkillResultRepository skillResultRepo;
    @Mock private ProfessionalCertificateRepository certificateRepo;
    @Mock private ProfessionalCertificateService certificateService;
    @Mock private RateLimitingService rateLimitingService;
    @Mock private ExamObservabilityService observabilityService;
    @Mock private ProfessionalCodingSubmissionRepository codingSubmissionRepo;
    @Mock private SqlEvaluationSandboxService sqlSandboxService;

    @InjectMocks private ProfessionalExamEvaluationServiceImpl evaluationService;
    @InjectMocks private ProfessionalExamAttemptServiceImpl attemptService;

    private ProfessionalExam exam;
    private ProfessionalExamVersion version;
    private ProfessionalExamSection section;
    private ProfessionalExamQuestion question1;
    private ProfessionalExamQuestion question2;

    private final Map<Long, ProfessionalExamApplication> appMap = new ConcurrentHashMap<>();
    private final Map<Long, ProfessionalExamAttempt> attemptMap = new ConcurrentHashMap<>();
    private final Map<String, ProfessionalExamAttemptQuestion> aqMap = new ConcurrentHashMap<>();
    private final Map<String, ProfessionalExamAnswer> ansMap = new ConcurrentHashMap<>();
    private final AtomicLong attemptIdGen = new AtomicLong(2000L);

    @BeforeEach
    void setUp() {
        appMap.clear();
        attemptMap.clear();
        aqMap.clear();
        ansMap.clear();

        exam = new ProfessionalExam();
        exam.setId(1L);
        exam.setTitle("High Scale Exam");
        exam.setMaximumAttempts(5);
        exam.setApplicationRequired(false);

        version = new ProfessionalExamVersion();
        version.setId(10L);
        version.setProfessionalExam(exam);
        version.setDurationMinutes(60);
        version.setTotalMarks(50);
        version.setPassingMarks(30);

        section = new ProfessionalExamSection();
        section.setId(100L);
        section.setExamVersion(version);

        question1 = new ProfessionalExamQuestion();
        question1.setId(1001L);
        question1.setQuestionType(QuestionType.MCQ);
        question1.setMarks(25);
        question1.setCorrectAnswer("A");

        question2 = new ProfessionalExamQuestion();
        question2.setId(1002L);
        question2.setQuestionType(QuestionType.SQL);
        question2.setMarks(25);
        question2.setCorrectAnswer("SELECT 1");

        when(rateLimitingService.allowRequest(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(questionRepo.findByExamSectionExamVersionId(10L)).thenAnswer(inv -> List.of(question1, question2));
        when(questionRepo.findById(1001L)).thenReturn(Optional.of(question1));
        when(questionRepo.findById(1002L)).thenReturn(Optional.of(question2));
        when(resultRepo.findByAttemptId(anyLong())).thenReturn(Optional.empty());
        when(resultRepo.save(any(ProfessionalExamResult.class))).thenAnswer(inv -> {
            ProfessionalExamResult res = inv.getArgument(0);
            if (res.getId() == null) res.setId(8000L);
            return res;
        });

        when(sqlSandboxService.evaluate(any(), any(), any())).thenReturn(
                new SqlEvaluationSandboxService.SqlEvaluationResult(true, 1, 5L, "Query executed successfully"));

        when(attemptRepo.findByStudentIdAndExamVersionProfessionalExamIdAndStatusIn(anyLong(), anyLong(), any()))
                .thenReturn(Collections.emptyList());
        when(attemptRepo.findByApplicationIdAndStudentId(anyLong(), anyLong()))
                .thenReturn(Optional.empty());

        // Dynamic Map-based Mocking to prevent thread cross-talk
        when(applicationRepo.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(appMap.get(inv.getArgument(0))));
        when(applicationRepo.findByIdWithLock(anyLong())).thenAnswer(inv -> Optional.ofNullable(appMap.get(inv.getArgument(0))));
        when(attemptRepo.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(attemptMap.get(inv.getArgument(0))));
        when(attemptRepo.save(any(ProfessionalExamAttempt.class))).thenAnswer(inv -> {
            ProfessionalExamAttempt a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(attemptIdGen.incrementAndGet());
            }
            attemptMap.put(a.getId(), a);
            return a;
        });

        when(attemptQuestionRepo.save(any(ProfessionalExamAttemptQuestion.class))).thenAnswer(inv -> {
            ProfessionalExamAttemptQuestion aq = inv.getArgument(0);
            aqMap.put(aq.getAttempt().getId() + ":" + aq.getQuestionNumber(), aq);
            return aq;
        });
        when(attemptQuestionRepo.findByAttemptIdAndQuestionNumber(anyLong(), anyInt())).thenAnswer(inv -> {
            Long attId = inv.getArgument(0);
            Integer qNum = inv.getArgument(1);
            return Optional.ofNullable(aqMap.get(attId + ":" + qNum));
        });
        when(attemptQuestionRepo.findByAttemptIdOrderByDisplayOrderAsc(anyLong())).thenAnswer(inv -> {
            Long attId = inv.getArgument(0);
            List<ProfessionalExamAttemptQuestion> list = new ArrayList<>();
            aqMap.forEach((k, v) -> {
                if (k.startsWith(attId + ":")) list.add(v);
            });
            list.sort(Comparator.comparingInt(ProfessionalExamAttemptQuestion::getDisplayOrder));
            return list;
        });

        when(answerRepo.save(any(ProfessionalExamAnswer.class))).thenAnswer(inv -> {
            ProfessionalExamAnswer ans = inv.getArgument(0);
            ansMap.put(ans.getAttempt().getId() + ":" + ans.getQuestion().getId(), ans);
            return ans;
        });
        when(answerRepo.findByAttemptIdAndQuestionId(anyLong(), anyLong())).thenAnswer(inv -> {
            Long attId = inv.getArgument(0);
            Long qId = inv.getArgument(1);
            return Optional.ofNullable(ansMap.get(attId + ":" + qId));
        });
        when(answerRepo.findByAttemptId(anyLong())).thenAnswer(inv -> {
            Long attId = inv.getArgument(0);
            List<ProfessionalExamAnswer> list = new ArrayList<>();
            ansMap.forEach((k, v) -> {
                if (k.startsWith(attId + ":")) list.add(v);
            });
            return list;
        });

        org.springframework.test.util.ReflectionTestUtils.setField(attemptService, "evaluationService", evaluationService);
    }

    @Test
    @DisplayName("Load Benchmark: 100 concurrent candidates executing start -> answer -> submit simultaneously")
    void test100ConcurrentCandidatesSimulation() throws InterruptedException {
        int candidateCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(candidateCount);

        List<Long> latencies = new CopyOnWriteArrayList<>();
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        long memBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        for (int i = 0; i < candidateCount; i++) {
            final long candidateId = 1000L + i;
            final long appId = 3000L + i;

            User candidate = new User();
            candidate.setId(candidateId);
            candidate.setEmail("candidate" + candidateId + "@benchmark.com");

            ProfessionalExamApplication app = new ProfessionalExamApplication();
            app.setId(appId);
            app.setUser(candidate);
            app.setProfessionalExam(exam);
            app.setExamVersion(version);
            app.setStatus(ApplicationStatus.APPROVED);

            appMap.put(appId, app);

            executor.submit(() -> {
                try {
                    startLatch.await(); // Synchronized release of all 100 candidates
                    long t0 = System.nanoTime();

                    // Step 1: Start
                    ExamStartRequest startReq = new ExamStartRequest();
                    startReq.setApplicationId(app.getId());
                    var overview = attemptService.startExam(startReq, candidate);
                    Long attemptId = overview.getAttemptId();

                    // Step 2: Answer Q1
                    AnswerSubmitRequest ansReq1 = new AnswerSubmitRequest();
                    ansReq1.setSelectedOption("A");
                    attemptService.saveAnswer(attemptId, 1, ansReq1, candidate);

                    // Step 3: Answer Q2
                    AnswerSubmitRequest ansReq2 = new AnswerSubmitRequest();
                    ansReq2.setTextAnswer("SELECT 1");
                    attemptService.saveAnswer(attemptId, 2, ansReq2, candidate);

                    // Step 4: Submit
                    attemptService.submitExam(attemptId, candidate);

                    long durationMs = (System.nanoTime() - t0) / 1_000_000;
                    latencies.add(durationMs);
                    successCount.incrementAndGet();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    errorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        long wallStart = System.currentTimeMillis();
        startLatch.countDown(); // Fire all threads!
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "All 100 concurrent candidates must finish within 10s");
        long wallDuration = System.currentTimeMillis() - wallStart;

        executor.shutdown();

        long memAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long memDeltaMb = Math.max(0, (memAfter - memBefore) / (1024 * 1024));

        assertEquals(100, successCount.get(), "All 100 candidates must complete successfully");
        assertEquals(0, errorCount.get(), "Error count must be zero");

        // Latency calculations
        List<Long> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);
        long p50 = sorted.get((int) (sorted.size() * 0.50));
        long p95 = sorted.get((int) (sorted.size() * 0.95));
        long p99 = sorted.get((int) (sorted.size() * 0.99));
        double throughput = (successCount.get() * 1000.0) / Math.max(1, wallDuration);

        System.out.println("=== 100 CONCURRENT CANDIDATES LOAD BENCHMARK ===");
        System.out.printf("Total Candidates Processed: %d\n", successCount.get());
        System.out.printf("Wall Clock Time:            %d ms\n", wallDuration);
        System.out.printf("Throughput:                 %.2f candidates/sec (%.2f exam ops/sec)\n", throughput, throughput * 4);
        System.out.printf("Latency p50:                %d ms\n", p50);
        System.out.printf("Latency p95:                %d ms\n", p95);
        System.out.printf("Latency p99:                %d ms\n", p99);
        System.out.printf("Error Rate:                 0.00%%\n");
        System.out.printf("Memory Delta:               %d MB\n", memDeltaMb);
        System.out.println("================================================");

        assertTrue(p95 < 250, "p95 latency must be sub-250ms under 100 concurrent candidates");
    }
}
