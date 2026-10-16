package com.skillhub.controller;

import com.skillhub.dto.CodingExecutionRequest;
import com.skillhub.model.*;
import com.skillhub.model.enums.*;
import com.skillhub.repository.*;
import com.skillhub.service.ProfessionalCertificateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.datasource.url=jdbc:h2:mem:securitytestdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
public class ProfessionalExamSecurityAndReadinessTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfessionalExamRepository examRepository;
    @Autowired private ProfessionalExamVersionRepository versionRepository;
    @Autowired private ProfessionalExamSectionRepository sectionRepository;
    @Autowired private ProfessionalExamQuestionRepository questionRepository;
    @Autowired private ProfessionalExamApplicationRepository appRepository;
    @Autowired private ProfessionalExamAttemptRepository attemptRepository;
    @Autowired private ProfessionalCertificateService certificateService;

    @org.springframework.boot.test.mock.mockito.MockBean(name = "initData")
    private org.springframework.boot.CommandLineRunner initData;

    private User studentA;
    private User studentB;
    private ProfessionalExamAttempt attemptA;
    private ProfessionalExamQuestion codingQuestion;

    @BeforeEach
    public void setup() {
        studentA = userRepository.findByEmail("studentA@test.com");
        if (studentA == null) {
            studentA = new User("Student Alpha", "studentA@test.com", "password", "STUDENT");
            studentA = userRepository.save(studentA);
        }

        studentB = userRepository.findByEmail("studentB@test.com");
        if (studentB == null) {
            studentB = new User("Student Beta", "studentB@test.com", "password", "STUDENT");
            studentB = userRepository.save(studentB);
        }

        ProfessionalExam exam = new ProfessionalExam();
        exam.setExamCode("SEC-EXAM-" + System.currentTimeMillis());
        exam.setTitle("Security Hardening Exam");
        exam.setTrack(ExamTrack.BACKEND);
        exam.setLevel(ExamDifficulty.INTERMEDIATE);
        exam.setDurationMinutes(30);
        exam.setTotalMarks(100);
        exam.setPassingMarks(60);
        exam.setMaximumAttempts(3);
        exam.setActive(true);
        exam = examRepository.save(exam);

        ProfessionalExamVersion version = new ProfessionalExamVersion();
        version.setProfessionalExam(exam);
        version.setVersionNumber("1.0");
        version.setVersionName("Release 1");
        version.setTotalMarks(100);
        version.setPassingMarks(60);
        version.setDurationMinutes(30);
        version.setActive(true);
        version.setPublished(true);
        version = versionRepository.save(version);

        ProfessionalExamSection section = new ProfessionalExamSection();
        section.setExamVersion(version);
        section.setName("Algorithms");
        section.setDisplayOrder(1);
        section = sectionRepository.save(section);

        codingQuestion = new ProfessionalExamQuestion();
        codingQuestion.setExamSection(section);
        codingQuestion.setQuestionText("Implement binary search.");
        codingQuestion.setQuestionType(QuestionType.CODING);
        codingQuestion.setDifficulty(ExamDifficulty.HARD);
        codingQuestion.setMarks(50);
        codingQuestion.setTimeLimit(2000);
        codingQuestion.setMemoryLimit(256);
        codingQuestion = questionRepository.save(codingQuestion);

        ProfessionalExamApplication appA = new ProfessionalExamApplication();
        appA.setApplicationNumber("APP-A-" + System.currentTimeMillis());
        appA.setUser(studentA);
        appA.setProfessionalExam(exam);
        appA.setExamVersion(version);
        appA.setStatus(ApplicationStatus.APPROVED);
        appA = appRepository.save(appA);

        attemptA = new ProfessionalExamAttempt();
        attemptA.setApplication(appA);
        attemptA.setStudent(studentA);
        attemptA.setExamVersion(version);
        attemptA.setStartedAt(java.time.LocalDateTime.now());
        attemptA.setStatus(AttemptStatus.IN_PROGRESS);
        attemptA.setCurrentQuestion(1L);
        attemptA = attemptRepository.save(attemptA);
    }

    @Test
    @WithMockUser(username = "studentB@test.com", roles = "STUDENT")
    public void testIdorProtection_StudentBCannotAccessStudentAAttempt() throws Exception {
        // Student B tries to access Student A's attempt overview
        mockMvc.perform(get("/api/professional-exams/attempts/" + attemptA.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED_ATTEMPT_ACCESS"));
    }

    @Test
    @WithMockUser(username = "studentB@test.com", roles = "STUDENT")
    public void testIdorProtection_StudentBCannotSubmitAnswerForStudentA() throws Exception {
        // Student B tries to submit an answer to Student A's attempt
        mockMvc.perform(post("/api/professional-exams/attempts/" + attemptA.getId() + "/answers?questionNumber=1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedOption\": \"MALICIOUS\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED_ATTEMPT_ACCESS"));
    }

    @Test
    public void testPublicCertificateVerification_UnauthenticatedAccessAllowed() throws Exception {
        // Public endpoint must be accessible without login
        mockMvc.perform(get("/certifications/verify/NON-EXISTENT-ID"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("No valid certificate was found")));
    }

    @Test
    public void testHealthCheckEndpoint() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.components.database.status").value("UP"))
                .andExpect(jsonPath("$.components.codingRunner.status").value("UP"));
    }

    @Test
    @WithMockUser(username = "studentA@test.com", roles = "STUDENT")
    public void testAttemptSubmit_Idempotency() throws Exception {
        // First submission
        mockMvc.perform(post("/api/professional-exams/attempts/" + attemptA.getId() + "/submit"))
                .andExpect(status().isOk());

        // Second submission must succeed gracefully without error or duplicate records
        mockMvc.perform(post("/api/professional-exams/attempts/" + attemptA.getId() + "/submit"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "studentA@test.com", roles = "STUDENT")
    public void testPrivilegeEscalation_StudentCannotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/admin/professional-exams"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "instructor@test.com", roles = "INSTRUCTOR")
    public void testPrivilegeEscalation_InstructorCannotAccessAdminExamEndpoints() throws Exception {
        mockMvc.perform(get("/admin/professional-exams"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testSecurityHeaders_CspAndCorrelationIdPresent() throws Exception {
        mockMvc.perform(get("/api/health").header("X-Request-ID", "trace-security-999"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "trace-security-999"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"));
    }
}
