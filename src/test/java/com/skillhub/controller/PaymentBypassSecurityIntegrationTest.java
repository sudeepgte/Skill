package com.skillhub.controller;

import com.skillhub.model.*;
import com.skillhub.repository.CourseRepository;
import com.skillhub.repository.EnrollmentRepository;
import com.skillhub.repository.PaymentRepository;
import com.skillhub.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.datasource.url=jdbc:h2:mem:paymenttestdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
@DisplayName("Payment Bypass Security Integration Test (BUG-016)")
public class PaymentBypassSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockBean(name = "initData")
    private CommandLineRunner initData;

    private User student;
    private Course paidCourse;
    private Course freeCourse;

    @BeforeEach
    public void setUp() {
        enrollmentRepository.deleteAll();
        paymentRepository.deleteAll();
        courseRepository.deleteAll();
        userRepository.deleteAll();

        student = new User();
        student.setEmail("student@skillhub.com");
        student.setName("Student Tester");
        student.setPassword("password");
        student.setRole("STUDENT");
        student = userRepository.save(student);

        paidCourse = new Course();
        paidCourse.setTitle("Full Stack Java Masterclass");
        paidCourse.setPrice(1999.0);
        paidCourse.setStatus(CourseStatus.PUBLISHED);
        paidCourse = courseRepository.save(paidCourse);

        freeCourse = new Course();
        freeCourse.setTitle("Introduction to Git and GitHub");
        freeCourse.setPrice(0.0);
        freeCourse.setStatus(CourseStatus.PUBLISHED);
        freeCourse = courseRepository.save(freeCourse);
    }

    @Test
    @DisplayName("BUG-016: Paid course direct GET /course/enroll/{id} without payment MUST redirect to /checkout/{id} and NOT enroll user")
    @WithMockUser(username = "student@skillhub.com", roles = "STUDENT")
    public void testDirectEnrollPaidCourseWithoutPaymentRedirectsToCheckout() throws Exception {
        mockMvc.perform(get("/course/enroll/" + paidCourse.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/checkout/" + paidCourse.getId()))
                .andExpect(flash().attributeExists("error"));

        // Critical verification: Student must NOT be enrolled in the paid course
        assertFalse(enrollmentRepository.findByStudentAndCourse(student, paidCourse).isPresent(),
                "Student must not be enrolled in paid course without payment receipt");
    }

    @Test
    @DisplayName("BUG-016: Paid course direct GET /course/enroll/{id} with verified payment enrolls user and redirects to profile")
    @WithMockUser(username = "student@skillhub.com", roles = "STUDENT")
    public void testDirectEnrollPaidCourseWithVerifiedPaymentEnrollsSuccessfully() throws Exception {
        // Record completed payment receipt
        Payment payment = new Payment();
        payment.setUser(student);
        payment.setCourse(paidCourse);
        payment.setAmount(paidCourse.getPrice());
        payment.setPaymentMethod("RAZORPAY");
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setTransactionId("TXN_TEST_12345");
        payment.setPaymentDate(LocalDateTime.now());
        paymentRepository.save(payment);

        mockMvc.perform(get("/course/enroll/" + paidCourse.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile?tab=courses"));

        assertTrue(enrollmentRepository.findByStudentAndCourse(student, paidCourse).isPresent(),
                "Student with verified payment must be enrolled successfully");
    }

    @Test
    @DisplayName("Free course direct GET /course/enroll/{id} enrolls user directly for free")
    @WithMockUser(username = "student@skillhub.com", roles = "STUDENT")
    public void testDirectEnrollFreeCourseEnrollsSuccessfully() throws Exception {
        mockMvc.perform(get("/course/enroll/" + freeCourse.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile?tab=courses"));

        assertTrue(enrollmentRepository.findByStudentAndCourse(student, freeCourse).isPresent(),
                "Student must be enrolled in free course directly");
    }

    @Test
    @DisplayName("REST API /api/enrollments/{courseId} blocks unpaid paid course enrollment with 402 Payment Required")
    @WithMockUser(username = "student@skillhub.com", roles = "STUDENT")
    public void testApiEnrollPaidCourseWithoutPaymentFails() throws Exception {
        mockMvc.perform(post("/api/enrollments/" + paidCourse.getId()).with(csrf()))
                .andExpect(status().isPaymentRequired());

        assertFalse(enrollmentRepository.findByStudentAndCourse(student, paidCourse).isPresent(),
                "Student must not be enrolled via API without payment");
    }

    @Test
    @DisplayName("Unauthenticated request to /course/enroll/{id} redirects to login")
    public void testUnauthenticatedEnrollRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/course/enroll/" + paidCourse.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
