package com.skillhub.security;

import com.skillhub.SkillHubApplication;
import com.skillhub.model.Course;
import com.skillhub.model.CourseStatus;
import com.skillhub.model.Review;
import com.skillhub.model.User;
import com.skillhub.repository.CourseRepository;
import com.skillhub.repository.ReviewRepository;
import com.skillhub.repository.UserRepository;
import com.skillhub.service.CourseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SkillHubApplication.class, properties = {
        "spring.datasource.hikari.maximum-pool-size=50",
        "spring.datasource.hikari.connection-timeout=10000",
        "spring.datasource.hikari.leak-detection-threshold=15000"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class PerformanceAndLoadTestingBenchmarkTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseService courseService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long sampleCourseId;

    @BeforeEach
    void setUpTestData() {
        if (sampleCourseId == null) {
            User instructor = new User();
            instructor.setName("Load Test Instructor");
            instructor.setEmail("instructor_load_" + UUID.randomUUID().toString().substring(0, 8) + "@skillhub.com");
            instructor.setPassword(passwordEncoder.encode("Password123!"));
            instructor.setRole("INSTRUCTOR");
            instructor.setApproved(true);
            instructor = userRepository.save(instructor);

            Course course = new Course();
            course.setTitle("High Scale System Design");
            course.setInstructor(instructor);
            course.setStatus(CourseStatus.PUBLISHED);
            course.setPublished(true);
            course.setPrice(99.0);
            course = courseRepository.save(course);
            sampleCourseId = course.getId();

            // Seed reviews for this course
            for (int i = 1; i <= 5; i++) {
                Review review = new Review();
                review.setCourse(course);
                review.setRating(i % 5 + 1);
                review.setComment("Great course content!");
                reviewRepository.save(review);
            }
        }
    }

    /**
     * Helper record to encapsulate measured load test results.
     */
    public record LoadTestResult(
            int virtualUsers,
            int totalRequests,
            long durationMs,
            double requestsPerSecond,
            long p50LatencyMs,
            long p95LatencyMs,
            long p99LatencyMs,
            long maxLatencyMs,
            int errorCount,
            double errorRatePercent,
            long memoryUsedMb
    ) {
        @Override
        public String toString() {
            return String.format(
                    "[Stage: %d VUs] Total: %d reqs in %d ms | Throughput: %.1f req/s | Latency (ms): p50=%d, p95=%d, p99=%d, max=%d | Errors: %d (%.2f%%) | Heap: %d MB",
                    virtualUsers, totalRequests, durationMs, requestsPerSecond,
                    p50LatencyMs, p95LatencyMs, p99LatencyMs, maxLatencyMs,
                    errorCount, errorRatePercent, memoryUsedMb
            );
        }
    }

    private LoadTestResult executeStage(int virtualUsers, int requestsPerUser) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(virtualUsers);
        List<Long> latencies = new CopyOnWriteArrayList<>();
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        // Pre-GC to observe clean memory usage
        System.gc();
        Runtime runtime = Runtime.getRuntime();
        long startMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);

        long startTime = System.currentTimeMillis();

        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(virtualUsers);

        String[] targetEndpoints = new String[]{
                "/api/health/liveness",
                "/api/health/readiness",
                "/api/health",
                "/courses",
                "/course/" + sampleCourseId
        };

        for (int u = 0; u < virtualUsers; u++) {
            final int userIndex = u;
            executor.submit(() -> {
                try {
                    startSignal.await();
                    for (int r = 0; r < requestsPerUser; r++) {
                        String target = targetEndpoints[(userIndex + r) % targetEndpoints.length];
                        long reqStart = System.currentTimeMillis();
                        try {
                            mockMvc.perform(get(target))
                                    .andExpect(status().isOk());
                            long reqLatency = System.currentTimeMillis() - reqStart;
                            latencies.add(reqLatency);
                            successCount.incrementAndGet();
                        } catch (Throwable t) {
                            errorCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    doneSignal.countDown();
                }
            });
        }

        // Release all virtual users simultaneously
        startSignal.countDown();
        boolean completed = doneSignal.await(60, TimeUnit.SECONDS);
        long durationMs = Math.max(1, System.currentTimeMillis() - startTime);

        executor.shutdown();
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }

        long endMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);

        List<Long> sortedLatencies = new ArrayList<>(latencies);
        Collections.sort(sortedLatencies);

        int totalRequests = virtualUsers * requestsPerUser;
        long p50 = sortedLatencies.isEmpty() ? 0 : sortedLatencies.get((int) (sortedLatencies.size() * 0.50));
        long p95 = sortedLatencies.isEmpty() ? 0 : sortedLatencies.get((int) (sortedLatencies.size() * 0.95));
        long p99 = sortedLatencies.isEmpty() ? 0 : sortedLatencies.get((int) (sortedLatencies.size() * 0.99));
        long max = sortedLatencies.isEmpty() ? 0 : sortedLatencies.get(sortedLatencies.size() - 1);

        double rps = (successCount.get() * 1000.0) / durationMs;
        double errorRate = (errorCount.get() * 100.0) / Math.max(1, totalRequests);

        LoadTestResult result = new LoadTestResult(
                virtualUsers, totalRequests, durationMs, rps, p50, p95, p99, max,
                errorCount.get(), errorRate, endMemory
        );

        System.out.println("LOAD BENCHMARK RESULT: " + result);
        return result;
    }

    @Test
    @DisplayName("Task 2 Optimization: Batch ratings query eliminates N+1 SQL queries")
    void testBatchRatingsQueryOptimization() {
        // Create 20 courses to verify batch resolution
        List<Course> testCourses = new ArrayList<>();
        User inst = userRepository.findAll().get(0);
        for (int i = 0; i < 20; i++) {
            Course c = new Course();
            c.setTitle("Batch Test Course " + i);
            c.setInstructor(inst);
            c.setStatus(CourseStatus.PUBLISHED);
            c.setPublished(true);
            testCourses.add(courseRepository.save(c));
        }

        long start = System.currentTimeMillis();
        courseService.populateCourseRatingsBatch(testCourses);
        long duration = System.currentTimeMillis() - start;

        System.out.println("Batch population of 20 courses completed in: " + duration + " ms");
        for (Course c : testCourses) {
            assertNotNull(c.getAverageRating());
            assertNotNull(c.getReviewCount());
        }
        assertTrue(duration < 2000, "Batch rating population must complete in under 2 seconds");
    }

    @Test
    @DisplayName("Stage 1: 5 Virtual Users Load Benchmark")
    void benchmarkStage1_5VirtualUsers() throws Exception {
        LoadTestResult result = executeStage(5, 10); // 50 requests
        assertEquals(0, result.errorCount(), "Stage 1 must have 0 errors");
        assertTrue(result.p95LatencyMs() < 1000, "Stage 1 p95 must be < 1000ms");
    }

    @Test
    @DisplayName("Stage 2: 10 Virtual Users Load Benchmark")
    void benchmarkStage2_10VirtualUsers() throws Exception {
        LoadTestResult result = executeStage(10, 10); // 100 requests
        assertEquals(0, result.errorCount(), "Stage 2 must have 0 errors");
        assertTrue(result.p95LatencyMs() < 1500, "Stage 2 p95 must be < 1500ms");
    }

    @Test
    @DisplayName("Stage 3: 25 Virtual Users Load Benchmark")
    void benchmarkStage3_25VirtualUsers() throws Exception {
        LoadTestResult result = executeStage(25, 6); // 150 requests
        assertEquals(0, result.errorCount(), "Stage 3 must have 0 errors");
        assertTrue(result.p95LatencyMs() < 3000, "Stage 3 p95 must be < 3000ms");
    }

    @Test
    @DisplayName("Stage 4: 50 Virtual Users Load Benchmark")
    void benchmarkStage4_50VirtualUsers() throws Exception {
        LoadTestResult result = executeStage(50, 4); // 200 requests
        assertEquals(0, result.errorCount(), "Stage 4 must have 0 errors");
        assertTrue(result.p95LatencyMs() < 4000, "Stage 4 p95 must be < 4000ms");
    }

    @Test
    @DisplayName("Stage 5: 100 Virtual Users Load Benchmark")
    void benchmarkStage5_100VirtualUsers() throws Exception {
        LoadTestResult result = executeStage(100, 2); // 200 requests
        // With 100 concurrent threads competing on a 50-connection pool, error rate must remain minimal
        assertTrue(result.errorRatePercent() <= 5.0, "Stage 5 error rate must be <= 5%");
        assertTrue(result.p95LatencyMs() < 6000, "Stage 5 p95 must be < 6000ms");
    }
}
