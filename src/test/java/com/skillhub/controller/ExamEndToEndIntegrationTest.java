package com.skillhub.controller;

import com.skillhub.dto.PublicCertificateDTO;
import com.skillhub.model.*;
import com.skillhub.model.enums.*;
import com.skillhub.repository.*;
import com.skillhub.service.ProfessionalCertificateService;
import com.skillhub.service.impl.ProfessionalExamEvaluationServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.datasource.url=jdbc:h2:mem:e2etestdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
@DisplayName("Comprehensive End-to-End Exam Lifecycle Integration Test")
public class ExamEndToEndIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfessionalExamRepository examRepository;
    @Autowired private ProfessionalExamVersionRepository versionRepository;
    @Autowired private ProfessionalExamSectionRepository sectionRepository;
    @Autowired private ProfessionalExamQuestionRepository questionRepository;
    @Autowired private ProfessionalExamApplicationRepository appRepository;
    @Autowired private ProfessionalExamAttemptRepository attemptRepository;
    @Autowired private ProfessionalExamAnswerRepository answerRepository;
    @Autowired private ProfessionalExamResultRepository resultRepository;
    @Autowired private ProfessionalCertificateRepository certificateRepository;
    @Autowired private ProfessionalCertificateService certificateService;
    @Autowired private ProfessionalExamEvaluationServiceImpl evaluationService;

    @org.springframework.boot.test.mock.mockito.MockBean(name = "initData")
    private org.springframework.boot.CommandLineRunner initData;

    @Test
    @WithMockUser(username = "e2e_student@test.com", roles = "STUDENT")
    @DisplayName("End-to-End: Exam -> Version -> Question -> Apply -> Start -> Answer -> Submit -> Result -> Certificate -> Public Verification")
    public void testFullEndToEndLifecycle() throws Exception {
        long ts = System.currentTimeMillis();

        // 1. Setup Student & Admin
        User student = new User("E2E Candidate", "e2e_student@test.com", "password", "STUDENT");
        student = userRepository.save(student);

        // 2. Admin creates Exam
        ProfessionalExam exam = new ProfessionalExam();
        exam.setExamCode("E2E-EXAM-" + ts);
        exam.setTitle("E2E Enterprise Certification");
        exam.setTrack(ExamTrack.BACKEND);
        exam.setLevel(ExamDifficulty.ADVANCED);
        exam.setDurationMinutes(90);
        exam.setTotalMarks(50);
        exam.setPassingMarks(30);
        exam.setMaximumAttempts(2);
        exam.setActive(true);
        exam = examRepository.save(exam);

        // 3. Admin creates Version
        ProfessionalExamVersion version = new ProfessionalExamVersion();
        version.setProfessionalExam(exam);
        version.setVersionNumber("1.0.0");
        version.setVersionName("Initial Release");
        version.setTotalMarks(50);
        version.setPassingMarks(30);
        version.setDurationMinutes(90);
        version.setActive(true);
        version.setPublished(true);
        version = versionRepository.save(version);

        // 4. Section & Multi-type Questions
        ProfessionalExamSection section = new ProfessionalExamSection();
        section.setExamVersion(version);
        section.setName("Enterprise Java & Data");
        section.setDisplayOrder(1);
        section = sectionRepository.save(section);

        // MCQ Question (25 marks)
        ProfessionalExamQuestion mcq = new ProfessionalExamQuestion();
        mcq.setExamSection(section);
        mcq.setQuestionText("Which JPA annotation designates a persistent entity?");
        mcq.setQuestionType(QuestionType.MCQ);
        mcq.setDifficulty(ExamDifficulty.ADVANCED);
        mcq.setMarks(25);
        mcq.setCorrectAnswer("@Entity");
        mcq.setOptions(List.of("@Entity", "@Table", "@Service", "@Component"));
        mcq = questionRepository.save(mcq);

        // SQL Question (25 marks)
        ProfessionalExamQuestion sql = new ProfessionalExamQuestion();
        sql.setExamSection(section);
        sql.setQuestionText("Select static score constant 100.");
        sql.setQuestionType(QuestionType.SQL);
        sql.setDifficulty(ExamDifficulty.ADVANCED);
        sql.setMarks(25);
        sql.setCorrectAnswer("SELECT 100 AS score");
        sql = questionRepository.save(sql);

        // 5. Student applies & application is approved
        ProfessionalExamApplication app = new ProfessionalExamApplication();
        app.setApplicationNumber("APP-E2E-" + ts);
        app.setUser(student);
        app.setProfessionalExam(exam);
        app.setExamVersion(version);
        app.setStatus(ApplicationStatus.APPROVED);
        app = appRepository.save(app);

        // 6. Student starts the exam
        String startResponse = mockMvc.perform(post("/api/professional-exams/attempts/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"applicationId\": " + app.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andReturn().getResponse().getContentAsString();

        Long attemptId = Long.parseLong(startResponse.split("\"attemptId\":")[1].split(",")[0].trim());
        assertNotNull(attemptId);

        // 7 & 8. Answer Question 1 and Question 2 correctly based on question type
        for (int qNum = 1; qNum <= 2; qNum++) {
            String qJson = mockMvc.perform(get("/api/professional-exams/attempts/" + attemptId + "/questions/" + qNum))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            if (qJson.contains("\"SQL\"")) {
                mockMvc.perform(post("/api/professional-exams/attempts/" + attemptId + "/answers?questionNumber=" + qNum)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"textAnswer\": \"SELECT 100 AS score\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.saved").value(true));
            } else {
                mockMvc.perform(post("/api/professional-exams/attempts/" + attemptId + "/answers?questionNumber=" + qNum)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selectedOption\": \"@Entity\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.saved").value(true));
            }
        }

        // 9. Student submits exam
        mockMvc.perform(post("/api/professional-exams/attempts/" + attemptId + "/submit"))
                .andExpect(status().isOk());

        // 10. Verify Evaluation & Result
        Optional<ProfessionalExamResult> resultOpt = resultRepository.findByAttemptId(attemptId);
        assertTrue(resultOpt.isPresent(), "Finalized exam result must be generated");
        ProfessionalExamResult result = resultOpt.get();
        assertEquals("PASS", result.getStatus());
        assertEquals("DISTINCTION", result.getGrade());
        assertEquals(50, result.getObtainedMarks().intValue());

        // 11. Verify Certificate Issuance
        Optional<ProfessionalCertificate> certOpt = certificateRepository.findByResultId(result.getId());
        assertTrue(certOpt.isPresent(), "Professional certificate must be automatically issued for passing candidate");
        ProfessionalCertificate cert = certOpt.get();
        assertNotNull(cert.getCertificateId());
        assertEquals(CertificateStatus.ACTIVE, cert.getStatus());

        // 12. Verify Public Certificate Verification (accessible without authentication)
        Optional<PublicCertificateDTO> publicCert = certificateService.verifyPublicCertificate(cert.getCertificateId());
        assertTrue(publicCert.isPresent());
        assertEquals(cert.getCertificateId(), publicCert.get().getCertificateId());
        assertEquals("DISTINCTION", publicCert.get().getGrade());
        assertTrue(publicCert.get().isValid());
    }
}
