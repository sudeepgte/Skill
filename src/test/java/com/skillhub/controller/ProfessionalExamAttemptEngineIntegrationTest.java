package com.skillhub.controller;

import com.skillhub.model.*;
import com.skillhub.model.enums.*;
import com.skillhub.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.datasource.url=jdbc:h2:mem:profexamattemptdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
@DisplayName("Professional Exam Attempt Engine Integration Test (BUG-021)")
public class ProfessionalExamAttemptEngineIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfessionalExamRepository examRepository;
    @Autowired private ProfessionalExamVersionRepository versionRepository;
    @Autowired private ProfessionalExamSectionRepository sectionRepository;
    @Autowired private ProfessionalExamQuestionRepository questionRepository;
    @Autowired private ProfessionalExamApplicationRepository appRepository;
    @Autowired private ProfessionalExamAttemptRepository attemptRepository;
    @Autowired private ProfessionalExamAttemptQuestionRepository attemptQuestionRepository;

    @MockBean(name = "initData")
    private CommandLineRunner initData;

    @MockBean(name = "seedProfessionalExamAttempt")
    private CommandLineRunner seedProfessionalExamAttempt;

    private User student;
    private ProfessionalExamAttempt attempt;

    @BeforeEach
    public void setUp() {
        attemptQuestionRepository.deleteAll();
        attemptRepository.deleteAll();
        appRepository.deleteAll();
        questionRepository.deleteAll();
        sectionRepository.deleteAll();
        versionRepository.deleteAll();
        examRepository.deleteAll();
        userRepository.deleteAll();

        student = new User();
        student.setEmail("neha@123");
        student.setName("Neha");
        student.setPassword("neha@123");
        student.setRole("STUDENT");
        student = userRepository.save(student);

        ProfessionalExam exam = new ProfessionalExam();
        exam.setExamCode("SH-FULLSTACK-TEST");
        exam.setTitle("Full Stack Software Engineering Certification");
        exam.setDescription("Test exam for engine verification");
        exam.setTrack(ExamTrack.DEVELOPMENT);
        exam.setLevel(ExamDifficulty.INTERMEDIATE);
        exam.setDurationMinutes(60);
        exam.setTotalMarks(20);
        exam.setPassingMarks(10);
        exam.setNegativeMarking(true);
        exam.setNegativeMarkValue(new BigDecimal("0.5"));
        exam.setMaximumAttempts(3);
        exam.setApplicationRequired(false);
        exam.setActive(true);
        exam = examRepository.save(exam);

        ProfessionalExamVersion version = new ProfessionalExamVersion();
        version.setProfessionalExam(exam);
        version.setVersionNumber("1.0");
        version.setVersionName("Standard 2026");
        version.setTotalMarks(20);
        version.setPassingMarks(10);
        version.setDurationMinutes(60);
        version.setActive(true);
        version.setPublished(true);
        version = versionRepository.save(version);

        ProfessionalExamSection section = new ProfessionalExamSection();
        section.setExamVersion(version);
        section.setName("Core Engineering");
        section.setDisplayOrder(1);
        section.setActive(true);
        section = sectionRepository.save(section);

        ProfessionalExamQuestion q1 = new ProfessionalExamQuestion();
        q1.setExamSection(section);
        q1.setQuestionText("What does the Open/Closed Principle state?");
        q1.setQuestionType(QuestionType.SINGLE_CHOICE);
        q1.setDifficulty(ExamDifficulty.INTERMEDIATE);
        q1.setMarks(5);
        q1.setNegativeMarks(new BigDecimal("0.5"));
        q1.setOptions(List.of("Option A", "Option B", "Option C"));
        q1.setCorrectAnswer("Option A");
        q1.setActive(true);
        questionRepository.save(q1);

        ProfessionalExamQuestion q2 = new ProfessionalExamQuestion();
        q2.setExamSection(section);
        q2.setQuestionText("Implement isPalindrome(String s)");
        q2.setQuestionType(QuestionType.CODING);
        q2.setDifficulty(ExamDifficulty.INTERMEDIATE);
        q2.setMarks(15);
        q2.setNegativeMarks(BigDecimal.ZERO);
        q2.setCodeLanguage("java");
        q2.setCodeStarterTemplate("public boolean isPalindrome(String s) { return true; }");
        q2.setActive(true);
        questionRepository.save(q2);

        ProfessionalExamApplication app = new ProfessionalExamApplication();
        app.setApplicationNumber("APP-TEST-001");
        app.setUser(student);
        app.setEmail(student.getEmail());
        app.setProfessionalExam(exam);
        app.setExamVersion(version);
        app.setStatus(ApplicationStatus.APPROVED);
        app.setPaymentStatus(PaymentStatus.SUCCESS);
        app.setApplicationFee(BigDecimal.ZERO);
        app = appRepository.save(app);

        attempt = new ProfessionalExamAttempt();
        attempt.setApplication(app);
        attempt.setStudent(student);
        attempt.setExamVersion(version);
        attempt.setStartedAt(LocalDateTime.now());
        attempt.setStatus(AttemptStatus.IN_PROGRESS);
        attempt.setCurrentQuestion(1L);
        attempt = attemptRepository.save(attempt);

        ProfessionalExamAttemptQuestion aq1 = new ProfessionalExamAttemptQuestion(attempt, q1, 1, 1);
        attemptQuestionRepository.save(aq1);

        ProfessionalExamAttemptQuestion aq2 = new ProfessionalExamAttemptQuestion(attempt, q2, 2, 2);
        attemptQuestionRepository.save(aq2);
    }

    @Test
    @WithMockUser(username = "neha@123", roles = "STUDENT")
    @DisplayName("BUG-021: Attempt page renders complete SPA engine with timer, question container, palette, and handlers")
    public void testExamAttemptPageRendersCompleteEngine() throws Exception {
        mockMvc.perform(get("/student/professional-exams/attempt/" + attempt.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("student/professional-exams/attempt"))
                .andExpect(content().string(containsString("SkillHub Exam Engine")))
                .andExpect(content().string(containsString("id=\"exam-app\"")))
                .andExpect(content().string(containsString("id=\"timer\"")))
                .andExpect(content().string(containsString("id=\"question-container\"")))
                .andExpect(content().string(containsString("id=\"palette-container\"")))
                .andExpect(content().string(containsString("initExamSession(")))
                .andExpect(content().string(containsString("loadQuestion(")))
                .andExpect(content().string(containsString("executeCodeSolution(")))
                .andExpect(content().string(containsString("handleTimeExpiryAutoSubmit(")))
                .andExpect(content().string(not(containsString("// SPAs logic for loading questions"))));
    }

    @Test
    @WithMockUser(username = "neha@123", roles = "STUDENT")
    @DisplayName("BUG-021: API endpoints successfully provide question, answers, palette, and submission")
    public void testExamEngineApiEndpoints() throws Exception {
        // 1. Overview API
        mockMvc.perform(get("/api/professional-exams/attempts/" + attempt.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptId").value(attempt.getId()))
                .andExpect(jsonPath("$.totalQuestions").value(2))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // 2. Question 1 (MCQ)
        mockMvc.perform(get("/api/professional-exams/attempts/" + attempt.getId() + "/questions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionNumber").value(1))
                .andExpect(jsonPath("$.questionText").value("What does the Open/Closed Principle state?"))
                .andExpect(jsonPath("$.questionType").value("SINGLE_CHOICE"));

        // 3. Save Answer for Q1
        mockMvc.perform(post("/api/professional-exams/attempts/" + attempt.getId() + "/answers?questionNumber=1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedOption\": \"Option A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));

        // 4. Palette verification
        mockMvc.perform(get("/api/professional-exams/attempts/" + attempt.getId() + "/palette"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].answered").value(true))
                .andExpect(jsonPath("$.questions[1].answered").value(false));

        // 5. Submit Exam
        mockMvc.perform(post("/api/professional-exams/attempts/" + attempt.getId() + "/submit"))
                .andExpect(status().isOk());
    }
}
