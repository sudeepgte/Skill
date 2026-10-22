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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.datasource.url=jdbc:h2:mem:courseaccesstestdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
@DisplayName("Course Video Access Control Integration Test")
public class CourseVideoAccessControlIntegrationTest {

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
        paidCourse.setTitle("Java & Spring Boot Backend Development");
        paidCourse.setPrice(2000.0);
        paidCourse.setStatus(CourseStatus.PUBLISHED);
        paidCourse = courseRepository.save(paidCourse);

        freeCourse = new Course();
        freeCourse.setTitle("Introduction to Git and GitHub");
        freeCourse.setPrice(0.0);
        freeCourse.setStatus(CourseStatus.PUBLISHED);
        freeCourse = courseRepository.save(freeCourse);
    }

    @Test
    @DisplayName("Paid Course - Unpaid student seeing course details sees 'Buy Now' and NOT 'Watch Now'")
    @WithMockUser(username = "student@skillhub.com", roles = "STUDENT")
    public void testPaidCourseUnpaidStudentSeesBuyNowOnly() throws Exception {
        mockMvc.perform(get("/course/" + paidCourse.getId()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("isEnrolled", false))
                .andExpect(content().string(containsString("Buy Now")))
                .andExpect(content().string(not(containsString(">Watch Now<"))));
    }

    @Test
    @DisplayName("Paid Course - Unpaid student attempting to access learning room is blocked and redirected to checkout")
    @WithMockUser(username = "student@skillhub.com", roles = "STUDENT")
    public void testPaidCourseUnpaidStudentBlockedFromLearningRoom() throws Exception {
        mockMvc.perform(get("/user/course/" + paidCourse.getId() + "/learn"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/checkout/" + paidCourse.getId()));

        assertFalse(enrollmentRepository.findByStudentAndCourse(student, paidCourse).isPresent(),
                "Unpaid student must not have active enrollment in paid course");
    }

    @Test
    @DisplayName("Paid Course - Dangling unpaid enrollment is purged and student is blocked from learning room")
    @WithMockUser(username = "student@skillhub.com", roles = "STUDENT")
    public void testPaidCourseDanglingUnpaidEnrollmentIsPurged() throws Exception {
        // Simulate an unauthorized enrollment row in DB without payment
        Enrollment unverifiedEnrollment = new Enrollment();
        unverifiedEnrollment.setStudent(student);
        unverifiedEnrollment.setCourse(paidCourse);
        unverifiedEnrollment.setEnrolledAt(LocalDateTime.now());
        enrollmentRepository.save(unverifiedEnrollment);

        // Accessing learning room must purge the enrollment and redirect to checkout
        mockMvc.perform(get("/user/course/" + paidCourse.getId() + "/learn"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/checkout/" + paidCourse.getId()));

        assertFalse(enrollmentRepository.findByStudentAndCourse(student, paidCourse).isPresent(),
                "Dangling unauthorized enrollment must be purged");
    }

    @Test
    @DisplayName("Paid Course - Verified paid student sees 'Watch Now' and NOT 'Buy Now'")
    @WithMockUser(username = "student@skillhub.com", roles = "STUDENT")
    public void testPaidCourseVerifiedPaidStudentSeesWatchNowOnly() throws Exception {
        Payment payment = new Payment();
        payment.setUser(student);
        payment.setCourse(paidCourse);
        payment.setAmount(paidCourse.getPrice());
        payment.setPaymentMethod("RAZORPAY");
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setTransactionId("TXN_SUCCESS_123");
        payment.setPaymentDate(LocalDateTime.now());
        paymentRepository.save(payment);

        mockMvc.perform(get("/course/" + paidCourse.getId()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("isEnrolled", true))
                .andExpect(content().string(containsString("Watch Now")))
                .andExpect(content().string(not(containsString("id=\"buyNowRazorpayBtn\""))));
    }

    @Test
    @DisplayName("Paid Course - Verified paid student can access learning room successfully")
    @WithMockUser(username = "student@skillhub.com", roles = "STUDENT")
    public void testPaidCourseVerifiedPaidStudentAccessesLearningRoom() throws Exception {
        Payment payment = new Payment();
        payment.setUser(student);
        payment.setCourse(paidCourse);
        payment.setAmount(paidCourse.getPrice());
        payment.setPaymentMethod("RAZORPAY");
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setTransactionId("TXN_SUCCESS_456");
        payment.setPaymentDate(LocalDateTime.now());
        paymentRepository.save(payment);

        mockMvc.perform(get("/user/course/" + paidCourse.getId() + "/learn"))
                .andExpect(status().isOk())
                .andExpect(view().name("course/learning_room"));

        assertTrue(enrollmentRepository.findByStudentAndCourse(student, paidCourse).isPresent(),
                "Paid student must have enrollment created and verified");
    }

    @Test
    @DisplayName("Free Course - Student can watch anytime and is automatically enrolled")
    @WithMockUser(username = "student@skillhub.com", roles = "STUDENT")
    public void testFreeCourseStudentCanWatchAnytime() throws Exception {
        mockMvc.perform(get("/user/course/" + freeCourse.getId() + "/learn"))
                .andExpect(status().isOk())
                .andExpect(view().name("course/learning_room"));

        assertTrue(enrollmentRepository.findByStudentAndCourse(student, freeCourse).isPresent(),
                "Student must be automatically enrolled in free course when watching");
    }

    @Test
    @DisplayName("Free Course - Not yet enrolled student sees 'Enroll for Free' on course details")
    @WithMockUser(username = "student@skillhub.com", roles = "STUDENT")
    public void testFreeCourseUnenrolledStudentSeesEnrollForFree() throws Exception {
        mockMvc.perform(get("/course/" + freeCourse.getId()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("isEnrolled", false))
                .andExpect(content().string(containsString("Enroll for Free")))
                .andExpect(content().string(not(containsString(">Watch Now<"))));
    }
}
