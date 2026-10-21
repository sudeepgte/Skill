package com.skillhub.controller;

import com.skillhub.model.Course;
import com.skillhub.model.InterviewQuestion;
import com.skillhub.model.User;
import com.skillhub.repository.CourseRepository;
import com.skillhub.repository.InterviewQuestionRepository;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.datasource.url=jdbc:h2:mem:placementcontentdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
@DisplayName("Placement & Interview Content Integration Test (BUG-019)")
public class PlacementInterviewContentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InterviewQuestionRepository interviewQuestionRepository;

    @Autowired
    private CourseRepository courseRepository;

    @MockBean(name = "initData")
    private CommandLineRunner initData;

    private User student;

    @BeforeEach
    public void setUp() {
        userRepository.deleteAll();

        student = new User();
        student.setEmail("neha@123");
        student.setName("Neha");
        student.setPassword("neha@123");
        student.setRole("STUDENT");
        userRepository.save(student);

        if (interviewQuestionRepository.count() == 0) {
            InterviewQuestion q = new InterviewQuestion();
            q.setCategory("Java");
            q.setQuestion("What is Polymorphism?");
            q.setAnswer("Polymorphism allows objects to take multiple forms.");
            interviewQuestionRepository.save(q);
        }
    }

    @Test
    @WithMockUser(username = "neha@123", roles = "STUDENT")
    @DisplayName("BUG-019: /user/placement renders Interview Preparation without DM Sansview")
    public void testPlacementDashboardDisplaysInterviewPreparation() throws Exception {
        mockMvc.perform(get("/user/placement"))
                .andExpect(status().isOk())
                .andExpect(view().name("placement/dashboard"))
                .andExpect(content().string(containsString("Interview Preparation")))
                .andExpect(content().string(not(containsString("DM Sansview"))));
    }

    @Test
    @WithMockUser(username = "neha@123", roles = "STUDENT")
    @DisplayName("BUG-019: /user/placement/dashboard renders Interview Preparation without DM Sansview")
    public void testPlacementDashboardPathDisplaysInterviewPreparation() throws Exception {
        mockMvc.perform(get("/user/placement/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("placement/dashboard"))
                .andExpect(content().string(containsString("Interview Preparation")))
                .andExpect(content().string(not(containsString("DM Sansview"))));
    }

    @Test
    @WithMockUser(username = "neha@123", roles = "STUDENT")
    @DisplayName("BUG-019: /user/placement/interview displays Interview Questions title and heading without DM Sansview")
    public void testInterviewQuestionsPageDisplaysCorrectHeadings() throws Exception {
        mockMvc.perform(get("/user/placement/interview"))
                .andExpect(status().isOk())
                .andExpect(view().name("placement/interview_questions"))
                .andExpect(content().string(containsString("<title>Interview Questions | SkillHub</title>")))
                .andExpect(content().string(containsString("Interview Prep")))
                .andExpect(content().string(not(containsString("DM Sansview"))));
    }

    @Test
    @WithMockUser(username = "admin@skillhub.com", roles = "ADMIN")
    @DisplayName("BUG-019: Admin course form displays Intermediate level without DM Sansmediate")
    public void testCourseFormDisplaysIntermediateWithoutDMSansmediate() throws Exception {
        User admin = new User();
        admin.setEmail("admin@skillhub.com");
        admin.setName("Admin");
        admin.setPassword("admin123");
        admin.setRole("ADMIN");
        userRepository.save(admin);

        Course course = new Course();
        course.setTitle("Java Basics");
        course.setPrice(100.0);
        course = courseRepository.save(course);

        mockMvc.perform(get("/admin/courses/edit").param("id", String.valueOf(course.getId())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/course_form"))
                .andExpect(content().string(containsString("Intermediate")))
                .andExpect(content().string(not(containsString("DM Sansmediate"))));
    }
}
