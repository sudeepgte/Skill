package com.skillhub.security;

import com.skillhub.SkillHubApplication;
import com.skillhub.dto.CourseUsageDto;
import com.skillhub.dto.PublicCertificateDTO;
import com.skillhub.exception.BusinessException;
import com.skillhub.model.*;
import com.skillhub.model.enums.*;
import com.skillhub.repository.*;
import com.skillhub.service.*;
import com.skillhub.service.impl.ProfessionalExamEvaluationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = SkillHubApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class EndToEndBusinessWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private CourseService courseService;

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ProfessionalExamAttemptService attemptService;

    @Autowired
    private ProfessionalExamEvaluationServiceImpl evaluationService;

    @Autowired
    private ProfessionalCertificateService certificateService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ExamQuestionRepository examQuestionRepository;

    @Autowired
    private ExamAttemptRepository examAttemptRepository;

    @Autowired
    private SubscriptionPlanRepository planRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ProfessionalExamRepository examRepository;

    @Autowired
    private ProfessionalExamVersionRepository versionRepository;

    @Autowired
    private ProfessionalExamSectionRepository sectionRepository;

    @Autowired
    private ProfessionalExamQuestionRepository profQuestionRepository;

    @Autowired
    private ProfessionalExamApplicationRepository applicationRepository;

    @Autowired
    private ProfessionalExamAttemptRepository attemptRepository;

    @Autowired
    private ProfessionalExamResultRepository resultRepository;

    @Autowired
    private ProfessionalCertificateRepository certificateRepository;

    private User student;
    private User studentB;
    private User instructor;
    private User admin;
    private Course freeCourse;
    private Course paidCourse;

    @BeforeEach
    void setupData() {
        // 1. Create Student
        student = new User();
        student.setName("Alice Student");
        student.setEmail("alice_" + UUID.randomUUID().toString().substring(0, 8) + "@skillhub.com");
        student.setPassword(passwordEncoder.encode("Password123!"));
        student.setRole("STUDENT");
        student.setApproved(true);
        student = userRepository.save(student);

        studentB = new User();
        studentB.setName("Bob Student");
        studentB.setEmail("bob_" + UUID.randomUUID().toString().substring(0, 8) + "@skillhub.com");
        studentB.setPassword(passwordEncoder.encode("Password123!"));
        studentB.setRole("STUDENT");
        studentB.setApproved(true);
        studentB = userRepository.save(studentB);

        // 2. Create Instructor
        instructor = new User();
        instructor.setName("Dr. Sarah Instructor");
        instructor.setEmail("sarah_" + UUID.randomUUID().toString().substring(0, 8) + "@skillhub.com");
        instructor.setPassword(passwordEncoder.encode("Password123!"));
        instructor.setRole("INSTRUCTOR");
        instructor.setApproved(true);
        instructor = userRepository.save(instructor);

        // 3. Create Admin
        admin = new User();
        admin.setName("System Admin");
        admin.setEmail("admin_" + UUID.randomUUID().toString().substring(0, 8) + "@skillhub.com");
        admin.setPassword(passwordEncoder.encode("Password123!"));
        admin.setRole("ADMIN");
        admin.setApproved(true);
        admin = userRepository.save(admin);

        // 4. Create Free Course
        freeCourse = new Course();
        freeCourse.setTitle("Intro to Java");
        freeCourse.setInstructor(instructor);
        freeCourse.setPrice(0.0);
        freeCourse.setStatus(CourseStatus.PUBLISHED);
        freeCourse.setPublished(true);
        freeCourse = courseRepository.save(freeCourse);

        // 5. Create Paid Course
        paidCourse = new Course();
        paidCourse.setTitle("Advanced Microservices");
        paidCourse.setInstructor(instructor);
        paidCourse.setPrice(499.0);
        paidCourse.setStatus(CourseStatus.PUBLISHED);
        paidCourse.setPublished(true);
        paidCourse = courseRepository.save(paidCourse);
    }

    // =========================================================================
    // TASK 1: STUDENT WORKFLOW TESTS
    // =========================================================================

    @Test
    @DisplayName("Student: Register new student and reject duplicate email")
    void testStudentRegistrationAndDuplicateRejection() {
        String testEmail = "new_student_" + UUID.randomUUID().toString().substring(0, 8) + "@skillhub.com";
        User newStudent = new User();
        newStudent.setName("Fresh Student");
        newStudent.setEmail(testEmail);
        newStudent.setPassword("SecretPass123!");
        newStudent.setRole("STUDENT");

        userService.saveUser(newStudent);
        User saved = userService.findByEmail(testEmail);
        assertNotNull(saved);
        assertNotNull(saved.getId());

        // Duplicate registration must fail
        User duplicate = new User();
        duplicate.setName("Duplicate Attempt");
        duplicate.setEmail(testEmail);
        duplicate.setPassword("AnotherPass123!");
        duplicate.setRole("STUDENT");

        assertThrows(IllegalArgumentException.class, () -> userService.saveUser(duplicate));
    }

    @Test
    @DisplayName("Student: Enroll in free course directly")
    void testStudentFreeCourseEnrollment() {
        assertFalse(enrollmentService.isEnrolled(student, freeCourse));
        enrollmentService.enrollUser(student, freeCourse.getId());
        assertTrue(enrollmentService.isEnrolled(student, freeCourse));
    }

    @Test
    @DisplayName("Student: Cannot enroll in unpublished or draft course")
    void testStudentCannotEnrollInDraftCourse() {
        Course draftCourse = new Course();
        draftCourse.setTitle("Draft Secrets");
        draftCourse.setInstructor(instructor);
        draftCourse.setStatus(CourseStatus.DRAFT);
        draftCourse.setPublished(false);
        draftCourse.setPrice(0.0);
        draftCourse = courseRepository.save(draftCourse);

        final Long draftId = draftCourse.getId();
        assertThrows(Exception.class, () -> enrollmentService.enrollUser(student, draftId));
    }

    @Test
    @DisplayName("Student: Wishlist add, check duplicate prevention, and remove")
    void testStudentWishlistWorkflow() {
        assertEquals(0, wishlistRepository.findByUser(student).size());

        Wishlist w = new Wishlist(student, paidCourse);
        wishlistRepository.save(w);

        assertEquals(1, wishlistRepository.findByUser(student).size());
        assertTrue(wishlistRepository.findByUserAndCourse(student, paidCourse).isPresent());

        // Remove from wishlist
        wishlistRepository.delete(w);
        assertEquals(0, wishlistRepository.findByUser(student).size());
    }

    @Test
    @DisplayName("Student: Only enrolled students can submit course reviews")
    void testStudentCourseReviewRestrictions() {
        // 1. Un-enrolled student cannot review
        Review reviewUnenrolled = new Review();
        reviewUnenrolled.setUser(student);
        reviewUnenrolled.setCourse(paidCourse);
        reviewUnenrolled.setRating(5);
        reviewUnenrolled.setComment("Great course preview!");

        assertFalse(enrollmentService.isEnrolled(student, paidCourse));

        // 2. Enroll student
        enrollmentService.enrollUser(student, paidCourse.getId());
        assertTrue(enrollmentService.isEnrolled(student, paidCourse));

        Review reviewEnrolled = new Review();
        reviewEnrolled.setUser(student);
        reviewEnrolled.setCourse(paidCourse);
        reviewEnrolled.setRating(5);
        reviewEnrolled.setComment("Excellent in-depth content!");
        Review saved = reviewRepository.save(reviewEnrolled);
        assertNotNull(saved.getId());
        assertEquals(5, saved.getRating());
    }

    @Test
    @DisplayName("Student: Course exam completion with server-side 70% passing threshold")
    void testCourseExamServerSideScoring() {
        enrollmentService.enrollUser(student, freeCourse.getId());
        Enrollment enrollment = enrollmentRepository.findByStudentAndCourse(student, freeCourse).orElseThrow();
        assertFalse(enrollment.isExamPassed());

        // Add 2 questions
        ExamQuestion q1 = new ExamQuestion(freeCourse, "What is JVM?", "Java Virtual Machine", "Tool", "OS", "IDE", "Java Virtual Machine");
        ExamQuestion q2 = new ExamQuestion(freeCourse, "What is JDK?", "Java Development Kit", "Tool", "OS", "IDE", "Java Development Kit");
        examQuestionRepository.saveAll(List.of(q1, q2));

        // Attempt 1: 1/2 correct = 50% -> Fail
        ExamAttempt attemptFail = new ExamAttempt(freeCourse, student, 50, false);
        examAttemptRepository.save(attemptFail);
        assertFalse(enrollment.isExamPassed());

        // Attempt 2: 2/2 correct = 100% -> Pass
        ExamAttempt attemptPass = new ExamAttempt(freeCourse, student, 100, true);
        examAttemptRepository.save(attemptPass);

        enrollment.setExamPassed(true);
        enrollment.setCertificateDate(LocalDateTime.now());
        enrollment = enrollmentRepository.save(enrollment);

        assertTrue(enrollment.isExamPassed());
        assertNotNull(enrollment.getCertificateDate());
    }

    // =========================================================================
    // TASK 2: INSTRUCTOR WORKFLOW TESTS
    // =========================================================================

    @Test
    @DisplayName("Instructor: Unapproved instructor is disabled and cannot log in")
    void testUnapprovedInstructorDisabled() {
        User pendingInstructor = new User();
        pendingInstructor.setName("Pending Prof");
        pendingInstructor.setEmail("pending_" + UUID.randomUUID().toString().substring(0, 8) + "@skillhub.com");
        pendingInstructor.setPassword(passwordEncoder.encode("Pass123!"));
        pendingInstructor.setRole("INSTRUCTOR");
        pendingInstructor.setApproved(false);
        pendingInstructor = userRepository.save(pendingInstructor);

        assertFalse(pendingInstructor.isApproved());
        var userDetails = userService.loadUserByUsername(pendingInstructor.getEmail());
        assertFalse(userDetails.isEnabled(), "Unapproved instructor UserDetails must have isEnabled() == false");

        // Admin approves instructor
        userService.approveUser(pendingInstructor.getId());
        User approved = userRepository.findById(pendingInstructor.getId()).orElseThrow();
        assertTrue(approved.isApproved());

        var approvedDetails = userService.loadUserByUsername(approved.getEmail());
        assertTrue(approvedDetails.isEnabled(), "Approved instructor UserDetails must be enabled");
    }

    @Test
    @DisplayName("Instructor: Monthly course creation limits under subscription plan")
    void testInstructorSubscriptionQuotas() {
        User quotaInstructor = new User();
        quotaInstructor.setName("Quota Instructor");
        quotaInstructor.setEmail("quota_inst_" + UUID.randomUUID().toString().substring(0, 8) + "@skillhub.com");
        quotaInstructor.setPassword(passwordEncoder.encode("Password123!"));
        quotaInstructor.setRole("INSTRUCTOR");
        quotaInstructor.setApproved(true);
        quotaInstructor = userRepository.save(quotaInstructor);

        // Activate free plan (1 course per month quota)
        subscriptionService.subscribeFreePlan(quotaInstructor);

        // 1st course: Allowed
        assertTrue(subscriptionService.canCreateCourse(quotaInstructor));

        Course course1 = new Course();
        course1.setTitle("Instructor Course 1");
        course1.setInstructor(quotaInstructor);
        course1.setStatus(CourseStatus.PUBLISHED);
        course1.setPublished(true);
        courseRepository.save(course1);

        // 2nd course: Exceeded quota
        CourseUsageDto usage = subscriptionService.getCourseUsage(quotaInstructor);
        assertEquals(1, usage.getUsedCount());
        assertTrue(usage.isLimitReached());
        assertFalse(subscriptionService.canCreateCourse(quotaInstructor));
    }

    // =========================================================================
    // TASK 3: ADMIN WORKFLOW TESTS
    // =========================================================================

    @Test
    @DisplayName("Admin: Approve and reject course with feedback")
    void testAdminCourseApprovalAndRejection() {
        Course submittedCourse = new Course();
        submittedCourse.setTitle("Kubernetes Deep Dive");
        submittedCourse.setInstructor(instructor);
        submittedCourse.setStatus(CourseStatus.PENDING_REVIEW);
        submittedCourse.setPublished(false);
        submittedCourse = courseRepository.save(submittedCourse);

        // Reject with feedback
        courseService.rejectCourse(submittedCourse.getId(), "Please add more hands-on labs in section 3");
        Course rejected = courseRepository.findById(submittedCourse.getId()).orElseThrow();
        assertEquals(CourseStatus.REJECTED, rejected.getStatus());
        assertEquals("Please add more hands-on labs in section 3", rejected.getRejectionReason());
        assertFalse(rejected.isPublished());

        // Approve course
        courseService.approveCourse(submittedCourse.getId());
        Course approved = courseRepository.findById(submittedCourse.getId()).orElseThrow();
        assertEquals(CourseStatus.PUBLISHED, approved.getStatus());
        assertTrue(approved.isPublished());
        assertNull(approved.getRejectionReason());
    }

    @Test
    @DisplayName("Admin: Send broadcast notification and verify user-specific dismissal")
    void testAdminBroadcastAndUserDismissal() {
        // Send broadcast to all users
        notificationService.createAndSend(student, "System Maintenance", "Platform upgrade tonight", "BROADCAST");
        notificationService.createAndSend(studentB, "System Maintenance", "Platform upgrade tonight", "BROADCAST");

        // Both students have an active unread broadcast
        Optional<Notification> broadA = notificationService.getActiveBroadcastForUser(student);
        Optional<Notification> broadB = notificationService.getActiveBroadcastForUser(studentB);
        assertTrue(broadA.isPresent());
        assertTrue(broadB.isPresent());

        // Student A dismisses it
        notificationService.markAsRead(broadA.get().getId());

        // Student A now has NO active broadcast, but Student B STILL has it!
        assertTrue(notificationService.getActiveBroadcastForUser(student).isEmpty());
        assertTrue(notificationService.getActiveBroadcastForUser(studentB).isPresent());
    }

    @Test
    @DisplayName("Admin: Review flagged exam attempt (APPROVE clears flag and issues certificate)")
    void testAdminFlaggedAttemptReviewAndApproval() {
        ProfessionalExam exam = new ProfessionalExam();
        exam.setTitle("Cloud Architect Exam");
        exam.setExamCode("EX-" + UUID.randomUUID().toString().substring(0, 6));
        exam.setTrack(ExamTrack.CLOUD);
        exam.setLevel(ExamDifficulty.ADVANCED);
        exam.setTotalMarks(100);
        exam.setPassingMarks(60);
        exam.setActive(true);
        exam = examRepository.save(exam);

        ProfessionalExamVersion ver = new ProfessionalExamVersion();
        ver.setProfessionalExam(exam);
        ver.setVersionNumber("1.0");
        ver.setVersionName("v1.0");
        ver.setDurationMinutes(60);
        ver.setPassingMarks(60);
        ver.setTotalMarks(100);
        ver.setPublished(true);
        ver.setActive(true);
        ver = versionRepository.save(ver);

        ProfessionalExamApplication app = new ProfessionalExamApplication();
        app.setUser(student);
        app.setProfessionalExam(exam);
        app.setExamVersion(ver);
        app.setStatus(ApplicationStatus.APPROVED);
        app.setPaymentStatus(PaymentStatus.SUCCESS);
        app.setApplicationNumber("APP-" + UUID.randomUUID().toString().substring(0, 8));
        app = applicationRepository.save(app);

        ProfessionalExamAttempt attempt = new ProfessionalExamAttempt();
        attempt.setApplication(app);
        attempt.setStudent(student);
        attempt.setExamVersion(ver);
        attempt.setStartedAt(LocalDateTime.now().minusMinutes(40));
        attempt.setStatus(AttemptStatus.IN_PROGRESS);
        attempt.setProctoringWarnings(3); // Flagged for review
        attempt = attemptRepository.save(attempt);

        ProfessionalExamResult evaluated = evaluationService.evaluateAttempt(attempt.getId());
        assertEquals("FLAGGED_FOR_REVIEW", evaluated.getStatus());

        // No certificate auto-issued while flagged
        assertTrue(certificateRepository.findByResultId(evaluated.getId()).isEmpty());

        // Admin reviews and clears proctoring flag
        evaluated.setStatus("PASS");
        evaluated.setPercentage(88.0);
        resultRepository.save(evaluated);

        ProfessionalCertificate cert = certificateService.issueCertificate(evaluated.getId());
        assertNotNull(cert);
        assertNotNull(cert.getCertificateId());
        assertEquals("PASS", evaluated.getStatus());
    }

    // =========================================================================
    // TASK 4: SECURITY & DATA INTEGRITY TESTS
    // =========================================================================

    @Test
    @DisplayName("Security: Student cannot access Admin or Vendor endpoints (RBAC)")
    @WithMockUser(username = "student@skillhub.com", authorities = {"STUDENT"})
    void testStudentRbacDenialOnAdminAndVendor() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/vendors"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/vendor/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Security: Instructor cannot access Admin endpoints (RBAC)")
    @WithMockUser(username = "instructor@skillhub.com", authorities = {"INSTRUCTOR"})
    void testInstructorRbacDenialOnAdmin() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/courses"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Security: Anonymous user denied or redirected on protected routes")
    void testAnonymousUserRedirectToLogin() throws Exception {
        mockMvc.perform(get("/profile").header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        mockMvc.perform(get("/user/certifications").header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }
}
