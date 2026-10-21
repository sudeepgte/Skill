package com.skillhub.controller;

import com.skillhub.model.*;
import com.skillhub.repository.*;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.datasource.url=jdbc:h2:mem:exambypasstestdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
@DisplayName("Exam & Certificate Bypass Security Integration Test (BUG-017)")
public class ExamBypassSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private ExamQuestionRepository examQuestionRepository;

    @Autowired
    private ExamAttemptRepository examAttemptRepository;

    @MockBean(name = "initData")
    private CommandLineRunner initData;

    private User student;
    private Course course;
    private ExamQuestion question;
    private Enrollment enrollment;

    @BeforeEach
    public void setUp() {
        examAttemptRepository.deleteAll();
        examQuestionRepository.deleteAll();
        enrollmentRepository.deleteAll();
        courseRepository.deleteAll();
        userRepository.deleteAll();

        student = new User();
        student.setEmail("student@skillhub.com");
        student.setName("Student Tester");
        student.setPassword("password");
        student.setRole("STUDENT");
        student = userRepository.save(student);

        course = new Course();
        course.setTitle("Java & Spring Boot Backend Development");
        course.setPrice(2000.0);
        course.setStatus(CourseStatus.PUBLISHED);
        course = courseRepository.save(course);

        // Create an enrollment (initially not passed, no certificate date)
        enrollment = new Enrollment();
        enrollment.setStudent(student);
        enrollment.setCourse(course);
        enrollment.setExamPassed(false);
        enrollment.setCertificateDate(null);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment = enrollmentRepository.save(enrollment);

        // Create question with correct answer "B"
        question = new ExamQuestion();
        question.setCourse(course);
        question.setQuestionText("What is a class in Java?");
        question.setOptionA("A primitive type");
        question.setOptionB("A blueprint for creating objects");
        question.setOptionC("A database table");
        question.setOptionD("An operating system");
        question.setCorrectOption("B");
        question.setCorrectAnswer("B");
        question = examQuestionRepository.save(question);
    }

    @Test
    @DisplayName("BUG-017: Direct POST to /user/course/{id}/exam/pass without answering questions MUST NOT mark exam passed and MUST NOT award certificate")
    @WithMockUser(username = "student@skillhub.com", roles = "STUDENT")
    public void testDirectPostWithoutAnswersFailsAndDoesNotAwardCertificate() throws Exception {
        mockMvc.perform(post("/user/course/" + course.getId() + "/exam/pass").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/course/" + course.getId() + "/exam"))
                .andExpect(flash().attributeExists("error"));

        Enrollment updatedEnrollment = enrollmentRepository.findByStudentAndCourse(student, course).orElseThrow();
        assertFalse(updatedEnrollment.isExamPassed(), "Enrollment must NOT have passed exam via direct empty POST");
        assertNull(updatedEnrollment.getCertificateDate(), "Certificate date must NOT be awarded without answering questions");
    }

    @Test
    @DisplayName("BUG-017: POST with wrong answers MUST NOT pass exam and MUST NOT award certificate")
    @WithMockUser(username = "student@skillhub.com", roles = "STUDENT")
    public void testPostWithWrongAnswersFailsAndDoesNotAwardCertificate() throws Exception {
        mockMvc.perform(post("/user/course/" + course.getId() + "/exam/pass")
                .param("q" + question.getId(), "A") // Wrong answer (correct is "B")
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/course/" + course.getId() + "/exam?score=0"))
                .andExpect(flash().attributeExists("error"));

        Enrollment updatedEnrollment = enrollmentRepository.findByStudentAndCourse(student, course).orElseThrow();
        assertFalse(updatedEnrollment.isExamPassed(), "Enrollment must NOT pass with wrong answers");
        assertNull(updatedEnrollment.getCertificateDate(), "Certificate date must NOT be awarded with failing score");
    }

    @Test
    @DisplayName("BUG-017: POST with verified correct answers passes exam and awards certificate date")
    @WithMockUser(username = "student@skillhub.com", roles = "STUDENT")
    public void testPostWithCorrectAnswersPassesAndAwardsCertificate() throws Exception {
        mockMvc.perform(post("/user/course/" + course.getId() + "/exam/pass")
                .param("q" + question.getId(), "B") // Correct answer
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/certifications?score=100"))
                .andExpect(flash().attributeExists("success"));

        Enrollment updatedEnrollment = enrollmentRepository.findByStudentAndCourse(student, course).orElseThrow();
        assertTrue(updatedEnrollment.isExamPassed(), "Enrollment must be marked as passed on verified evaluation");
        assertNotNull(updatedEnrollment.getCertificateDate(), "Certificate date must be awarded on passing exam");
    }

    @Test
    @DisplayName("BUG-017: Unauthenticated request to /user/course/{id}/exam/pass is blocked")
    public void testUnauthenticatedRequestRedirectsToLogin() throws Exception {
        mockMvc.perform(post("/user/course/" + course.getId() + "/exam/pass").with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
