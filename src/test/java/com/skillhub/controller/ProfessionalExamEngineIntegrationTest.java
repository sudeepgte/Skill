package com.skillhub.controller;

import com.skillhub.model.*;
import com.skillhub.model.enums.*;
import com.skillhub.repository.*;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
public class ProfessionalExamEngineIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfessionalExamRepository examRepository;
    @Autowired private ProfessionalExamVersionRepository versionRepository;
    @Autowired private ProfessionalExamSectionRepository sectionRepository;
    @Autowired private ProfessionalExamQuestionRepository questionRepository;
    @Autowired private ProfessionalExamApplicationRepository appRepository;

    @org.springframework.boot.test.mock.mockito.MockBean(name = "initData")
    private org.springframework.boot.CommandLineRunner initData;

    private User student;
    private ProfessionalExamApplication app;

    @BeforeEach
    public void setup() {
        if (userRepository.findByEmail("student@test.com") == null) {
            student = new User("Test Student", "student@test.com", "password", "STUDENT");
            userRepository.save(student);
        } else {
            student = userRepository.findByEmail("student@test.com");
        }

        ProfessionalExam exam = new ProfessionalExam();
        exam.setExamCode("SH-DEV-TEST-" + System.currentTimeMillis());
        exam.setTitle("Test Exam");
        exam.setTrack(ExamTrack.DEVELOPMENT);
        exam.setLevel(ExamDifficulty.ADVANCED);
        exam.setDurationMinutes(60);
        exam.setTotalMarks(10);
        exam.setPassingMarks(5);
        exam.setNegativeMarking(true);
        exam.setNegativeMarkValue(new BigDecimal("0.5"));
        exam.setMaximumAttempts(1);
        exam.setApplicationRequired(false);
        exam.setActive(true);
        exam = examRepository.save(exam);

        ProfessionalExamVersion version = new ProfessionalExamVersion();
        version.setProfessionalExam(exam);
        version.setVersionNumber("1.0");
        version.setVersionName("v1");
        version.setTotalMarks(10);
        version.setPassingMarks(5);
        version.setDurationMinutes(60);
        version.setActive(true);
        version.setPublished(true);
        version = versionRepository.save(version);

        ProfessionalExamSection section = new ProfessionalExamSection();
        section.setExamVersion(version);
        section.setName("Core");
        section.setDisplayOrder(1);
        section = sectionRepository.save(section);

        ProfessionalExamQuestion q = new ProfessionalExamQuestion();
        q.setExamSection(section);
        q.setQuestionText("What is Java?");
        q.setQuestionType(QuestionType.SINGLE_CHOICE);
        q.setDifficulty(ExamDifficulty.ADVANCED);
        q.setMarks(10);
        q.setNegativeMarks(new BigDecimal("0.5"));
        q.setOptions(List.of("A", "B", "C"));
        q.setCorrectAnswer("A");
        questionRepository.save(q);

        app = new ProfessionalExamApplication();
        app.setApplicationNumber("APP-001");
        app.setUser(student);
        app.setProfessionalExam(exam);
        app.setExamVersion(version);
        app.setStatus(ApplicationStatus.APPROVED);
        app = appRepository.save(app);
    }

    @Test
    @WithMockUser(username = "student@test.com", roles = "STUDENT")
    public void testFullExamLifecycle() throws Exception {
        // 1. Start Exam
        String response = mockMvc.perform(post("/api/professional-exams/attempts/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"applicationId\": " + app.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andReturn().getResponse().getContentAsString();
        
        Long attemptId = Long.parseLong(response.split("\"attemptId\":")[1].split(",")[0].trim());

        // 2. Get Question
        mockMvc.perform(get("/api/professional-exams/attempts/" + attemptId + "/questions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionText").value("What is Java?"));

        // 3. Save Answer
        mockMvc.perform(post("/api/professional-exams/attempts/" + attemptId + "/answers?questionNumber=1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedOption\": \"A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));

        // 4. Get Palette
        mockMvc.perform(get("/api/professional-exams/attempts/" + attemptId + "/palette"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].answered").value(true));

        // 5. Submit Exam
        mockMvc.perform(post("/api/professional-exams/attempts/" + attemptId + "/submit"))
                .andExpect(status().isOk());
    }
}
