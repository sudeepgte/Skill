package com.skillhub.service;

import com.skillhub.controller.AdminProfessionalExamController;
import com.skillhub.exception.BusinessException;
import com.skillhub.model.*;
import com.skillhub.model.enums.ExamDifficulty;
import com.skillhub.model.enums.QuestionType;
import com.skillhub.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.Model;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Exam Blueprint Validation & Version Immutability Tests")
public class BlueprintValidationAndVersionImmutabilityTest {

    @Mock private ProfessionalExamVersionRepository versionRepo;
    @Mock private ProfessionalExamSectionRepository sectionRepo;
    @Mock private ProfessionalExamQuestionRepository questionRepo;
    @Mock private ProfessionalExamBlueprintRepository blueprintRepo;
    @Mock private AdminLogRepository adminLogRepo;
    @Mock private UserRepository userRepo;
    @Mock private Principal principal;
    @Mock private Model model;

    @InjectMocks
    private AdminProfessionalExamController controller;

    private ProfessionalExam exam;
    private ProfessionalExamVersion version;
    private ProfessionalExamSection section;
    private User adminUser;

    @BeforeEach
    void setUp() {
        exam = new ProfessionalExam();
        exam.setId(1L);
        exam.setTitle("SkillHub Java Architect");

        version = new ProfessionalExamVersion();
        version.setId(10L);
        version.setProfessionalExam(exam);
        version.setVersionNumber("1.0.0");
        version.setDurationMinutes(120);
        version.setPassingMarks(70);
        version.setPublished(false);

        section = new ProfessionalExamSection();
        section.setId(100L);
        section.setExamVersion(version);
        section.setName("Core Architecture");

        adminUser = new User();
        adminUser.setId(99L);
        adminUser.setEmail("admin@skillhub.com");
    }

    @Test
    @DisplayName("Blueprint: Fails publish when questions in Question Bank are less than blueprint requires")
    void testRejectsInsufficientQuestionsForBlueprint() {
        when(versionRepo.findById(10L)).thenReturn(Optional.of(version));
        when(sectionRepo.findByExamVersionId(10L)).thenReturn(List.of(section));

        ProfessionalExamBlueprint bp = new ProfessionalExamBlueprint();
        bp.setId(500L);
        bp.setSection(section);
        bp.setQuestionCount(5); // Blueprint demands 5 questions
        bp.setQuestionType(QuestionType.CODING);
        bp.setDifficulty(ExamDifficulty.HARD);

        when(blueprintRepo.findBySectionId(100L)).thenReturn(List.of(bp));

        // Question bank only has 2 matching questions
        ProfessionalExamQuestion q1 = new ProfessionalExamQuestion();
        q1.setQuestionType(QuestionType.CODING);
        q1.setDifficulty(ExamDifficulty.HARD);
        q1.setMarks(10);

        ProfessionalExamQuestion q2 = new ProfessionalExamQuestion();
        q2.setQuestionType(QuestionType.CODING);
        q2.setDifficulty(ExamDifficulty.HARD);
        q2.setMarks(10);

        when(questionRepo.findByExamSectionId(100L)).thenReturn(List.of(q1, q2));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                controller.publishVersion(10L, principal, model));

        assertTrue(ex.getMessage().contains("BLUEPRINT_VALIDATION_FAILED"));
        assertTrue(ex.getMessage().contains("requires 5 questions"));
        assertTrue(ex.getMessage().contains("only 2 valid questions are available"));
    }

    @Test
    @DisplayName("Version Immutability: Fails publish if version is already published")
    void testRejectsAlreadyPublishedVersion() {
        version.setPublished(true);
        when(versionRepo.findById(10L)).thenReturn(Optional.of(version));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                controller.publishVersion(10L, principal, model));

        assertEquals("VERSION_ALREADY_PUBLISHED", ex.getMessage());
        verify(versionRepo, never()).save(any());
    }

    @Test
    @DisplayName("Section Validation: Fails publish if version has no sections")
    void testRejectsVersionWithNoSections() {
        when(versionRepo.findById(10L)).thenReturn(Optional.of(version));
        when(sectionRepo.findByExamVersionId(10L)).thenReturn(List.of());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                controller.publishVersion(10L, principal, model));

        assertEquals("CANNOT_PUBLISH_VERSION_WITHOUT_SECTIONS", ex.getMessage());
    }

    @Test
    @DisplayName("Blueprint: Successfully publishes when Question Bank meets blueprint requirements")
    void testPublishesSuccessfullyWhenRequirementsMet() {
        when(versionRepo.findById(10L)).thenReturn(Optional.of(version));
        when(sectionRepo.findByExamVersionId(10L)).thenReturn(List.of(section));
        when(principal.getName()).thenReturn("admin@skillhub.com");
        when(userRepo.findByEmail("admin@skillhub.com")).thenReturn(adminUser);

        ProfessionalExamBlueprint bp = new ProfessionalExamBlueprint();
        bp.setId(501L);
        bp.setSection(section);
        bp.setQuestionCount(2);
        bp.setQuestionType(QuestionType.MCQ);
        bp.setDifficulty(ExamDifficulty.MEDIUM);

        when(blueprintRepo.findBySectionId(100L)).thenReturn(List.of(bp));

        ProfessionalExamQuestion q1 = new ProfessionalExamQuestion();
        q1.setQuestionType(QuestionType.MCQ);
        q1.setDifficulty(ExamDifficulty.MEDIUM);
        q1.setMarks(5);

        ProfessionalExamQuestion q2 = new ProfessionalExamQuestion();
        q2.setQuestionType(QuestionType.MCQ);
        q2.setDifficulty(ExamDifficulty.MEDIUM);
        q2.setMarks(5);

        when(questionRepo.findByExamSectionId(100L)).thenReturn(List.of(q1, q2));
        when(questionRepo.findByExamSectionExamVersionId(10L)).thenReturn(List.of(q1, q2));

        String redirect = controller.publishVersion(10L, principal, model);
        assertTrue(redirect.contains("/admin/professional-exams/1/versions"));
        assertTrue(version.isPublished());
        assertEquals(10, version.getTotalMarks());
        verify(versionRepo).save(version);
        verify(adminLogRepo).save(any());
    }
}
