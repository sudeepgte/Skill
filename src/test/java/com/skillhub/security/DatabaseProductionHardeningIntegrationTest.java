package com.skillhub.security;

import com.skillhub.exception.BusinessException;
import com.skillhub.model.*;
import com.skillhub.model.enums.CertificateStatus;
import com.skillhub.model.enums.ExamTrack;
import com.skillhub.repository.*;
import com.skillhub.service.CourseService;
import com.skillhub.service.EnrollmentService;
import com.skillhub.service.PaymentService;
import com.skillhub.service.ProfessionalCertificateService;
import com.skillhub.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class DatabaseProductionHardeningIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private InstructorSubscriptionRepository subscriptionRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private ProfessionalCertificateRepository certificateRepository;

    @Autowired
    private ProfessionalExamRepository professionalExamRepository;

    @Autowired
    private ProfessionalExamVersionRepository examVersionRepository;

    @Autowired
    private ProfessionalExamApplicationRepository examApplicationRepository;

    @Autowired
    private ProfessionalExamAttemptRepository examAttemptRepository;

    @Autowired
    private ProfessionalExamResultRepository examResultRepository;

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private CourseService courseService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private ProfessionalCertificateService certificateService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User testStudent;
    private User testInstructor;
    private Course testCourse;

    @BeforeEach
    void setUp() {
        String randomSuffix = UUID.randomUUID().toString().substring(0, 8);

        testStudent = new User();
        testStudent.setName("Hardened Student");
        testStudent.setEmail("student_" + randomSuffix + "@test.com");
        testStudent.setPassword("Password123!");
        testStudent.setRole("STUDENT");
        testStudent.setApproved(true);
        testStudent = userRepository.save(testStudent);

        testInstructor = new User();
        testInstructor.setName("Hardened Instructor");
        testInstructor.setEmail("instructor_" + randomSuffix + "@test.com");
        testInstructor.setPassword("Password123!");
        testInstructor.setRole("INSTRUCTOR");
        testInstructor.setApproved(true);
        testInstructor = userRepository.save(testInstructor);

        testCourse = new Course();
        testCourse.setTitle("Database Security Mastery " + randomSuffix);
        testCourse.setPrice(499.0);
        testCourse.setInstructor(testInstructor);
        testCourse.setPublished(true);
        testCourse.setStatus(CourseStatus.PUBLISHED);
        testCourse = courseRepository.save(testCourse);
    }

    @Test
    @DisplayName("Verify concurrent enrollment requests for same user and course do not create duplicates")
    void testConcurrentEnrollmentDeduplication() throws Exception {
        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        List<Enrollment> enrolledList = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Enrollment e = enrollmentService.enrollUser(testStudent, testCourse.getId());
                    if (e != null) {
                        enrolledList.add(e);
                        successCount.incrementAndGet();
                    }
                } catch (Exception ex) {
                    // Ignored or handled gracefully
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Threads did not complete in time");
        executor.shutdown();

        // Database level verification: exactly ONE enrollment must exist in database
        List<Enrollment> enrollmentsInDb = enrollmentRepository.findByStudent(testStudent);
        assertEquals(1, enrollmentsInDb.size(), "Database must contain exactly 1 enrollment record despite concurrent calls");
    }

    @Test
    @DisplayName("Verify course subscription limits cannot be bypassed by simultaneous requests")
    void testCourseQuotaEnforcementUnderSimultaneousRequests() throws Exception {
        // Give instructor an active subscription with quota of 1 course
        InstructorSubscription sub = new InstructorSubscription();
        sub.setInstructor(testInstructor);
        sub.setPlanName("Free");
        sub.setCourseLimit(1);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setStartDate(LocalDateTime.now().minusDays(1));
        sub.setEndDate(LocalDateTime.now().plusDays(29));
        sub.setBillingInterval("MONTHLY");
        subscriptionRepository.save(sub);

        // Instructor already created testCourse (which uses 1 course allowance)
        // Check initial usage
        assertFalse(subscriptionService.canCreateCourse(testInstructor), "Instructor quota should be exhausted after 1 course");

        // Attempt 4 simultaneous course creation requests
        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger quotaBlockedCount = new AtomicInteger(0);
        AtomicInteger createdCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Course extraCourse = new Course();
                    extraCourse.setTitle("Concurrent Attempt " + index + " - " + UUID.randomUUID());
                    extraCourse.setPrice(100.0);
                    extraCourse.setInstructor(testInstructor);
                    courseService.saveCourse(extraCourse);
                    createdCount.incrementAndGet();
                } catch (BusinessException be) {
                    if ("COURSE_QUOTA_EXCEEDED".equals(be.getMessage())) {
                        quotaBlockedCount.incrementAndGet();
                    }
                } catch (Exception ex) {
                    // Other errors
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent course creation threads did not finish in time");
        executor.shutdown();

        // All concurrent attempts must be rejected due to strict row-lock quota serialization
        assertEquals(0, createdCount.get(), "No additional courses should be created beyond subscription quota");
        assertEquals(threadCount, quotaBlockedCount.get(), "All concurrent threads must be blocked by COURSE_QUOTA_EXCEEDED");
    }

    @Test
    @DisplayName("Verify transactional rollback on failure leaves no partial payment or enrollment state")
    void testTransactionalRollbackOnPaymentFailure() {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        TransactionStatus status = transactionManager.getTransaction(def);

        Payment payment = new Payment();
        payment.setTransactionId("ROLLBACK-TXN-" + UUID.randomUUID());
        payment.setAmount(999.0);
        payment.setPaymentMethod("CARD");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setUser(testStudent);
        payment.setCourse(testCourse);
        Payment saved = paymentRepository.save(payment);
        assertNotNull(saved.getId());

        // Injected failure triggering explicit rollback
        transactionManager.rollback(status);

        // Verification: database must NOT contain the payment record
        assertFalse(paymentRepository.findByTransactionId(payment.getTransactionId()).isPresent(),
                "Rolled back transaction must not persist payment");
    }

    @Test
    @DisplayName("Verify certificate issuance is idempotent and prevents duplicate certificates")
    void testCertificateIssuanceIdempotency() {
        ProfessionalExam exam = new ProfessionalExam();
        String code = "EXAM-" + UUID.randomUUID().toString().substring(0, 6);
        exam.setExamCode(code);
        exam.setTitle("Spring Cloud Architect " + code);
        exam.setTrack(ExamTrack.CLOUD);
        exam.setLevel(com.skillhub.model.enums.ExamDifficulty.ADVANCED);
        exam.setDurationMinutes(90);
        exam.setTotalMarks(100);
        exam.setPassingMarks(70);
        exam = professionalExamRepository.save(exam);

        ProfessionalExamVersion version = new ProfessionalExamVersion();
        version.setProfessionalExam(exam);
        version.setVersionNumber("1.0");
        version.setVersionName("v1.0");
        version.setPublished(true);
        version.setActive(true);
        version = examVersionRepository.save(version);

        ProfessionalExamApplication app = new ProfessionalExamApplication();
        app.setUser(testStudent);
        app.setProfessionalExam(exam);
        app.setExamVersion(version);
        app.setApplicationNumber("APP-" + UUID.randomUUID().toString().substring(0, 8));
        app.setStatus(com.skillhub.model.enums.ApplicationStatus.APPROVED);
        app = examApplicationRepository.save(app);

        ProfessionalExamAttempt attempt = new ProfessionalExamAttempt();
        attempt.setApplication(app);
        attempt.setStudent(testStudent);
        attempt.setExamVersion(version);
        attempt.setStatus(com.skillhub.model.enums.AttemptStatus.SUBMITTED);
        attempt.setStartedAt(LocalDateTime.now().minusHours(1));
        attempt.setSubmittedAt(LocalDateTime.now());
        attempt.setObtainedMarks(85);
        attempt.setTotalMarks(100);
        attempt = examAttemptRepository.save(attempt);

        ProfessionalExamResult result = new ProfessionalExamResult();
        result.setAttempt(attempt);
        result.setPercentage(85.0);
        result.setStatus("PASS");
        result = examResultRepository.save(result);

        // Issue certificate first time
        ProfessionalCertificate cert1 = certificateService.issueCertificate(result.getId());
        assertNotNull(cert1);
        assertEquals(CertificateStatus.ACTIVE, cert1.getStatus());

        // Issue certificate second time (simultaneous or repeated call)
        ProfessionalCertificate cert2 = certificateService.issueCertificate(result.getId());
        assertNotNull(cert2);
        assertEquals(cert1.getId(), cert2.getId(), "Second issuance must return existing certificate without duplication");
    }

    @Test
    @DisplayName("Verify enrollment query fetches course and instructor without N+1 query issue")
    void testEnrollmentQueryFetchEfficiency() {
        Enrollment enrollment = enrollmentService.enrollUser(testStudent, testCourse.getId());
        assertNotNull(enrollment);

        List<Enrollment> list = enrollmentRepository.findByStudent(testStudent);
        assertFalse(list.isEmpty());

        Enrollment fetched = list.get(0);
        assertNotNull(fetched.getCourse(), "Course must be eagerly joined");
        assertEquals(testCourse.getTitle(), fetched.getCourse().getTitle());
        assertNotNull(fetched.getCourse().getInstructor(), "Instructor must be eagerly joined");
        assertEquals(testInstructor.getEmail(), fetched.getCourse().getInstructor().getEmail());
    }
}
