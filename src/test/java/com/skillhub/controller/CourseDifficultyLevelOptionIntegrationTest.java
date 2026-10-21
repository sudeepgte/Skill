package com.skillhub.controller;

import com.skillhub.model.Category;
import com.skillhub.model.Course;
import com.skillhub.model.User;
import com.skillhub.repository.CategoryRepository;
import com.skillhub.repository.CourseRepository;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.datasource.url=jdbc:h2:mem:coursedifficultytestdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
@DisplayName("Course Difficulty Level Option Integration Test (BUG-020)")
public class CourseDifficultyLevelOptionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean(name = "initData")
    private CommandLineRunner initData;

    private User admin;
    private User instructor;
    private Course sampleCourse;

    @BeforeEach
    public void setUp() {
        courseRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        Category cat = new Category();
        cat.setName("Development and IT");
        categoryRepository.save(cat);

        admin = new User();
        admin.setEmail("admin@skillhub.com");
        admin.setName("Admin User");
        admin.setPassword("admin123");
        admin.setRole("ADMIN");
        admin = userRepository.save(admin);

        instructor = new User();
        instructor.setEmail("instructor@skillhub.com");
        instructor.setName("Instructor User");
        instructor.setPassword("password");
        instructor.setRole("INSTRUCTOR");
        instructor = userRepository.save(instructor);

        sampleCourse = new Course();
        sampleCourse.setTitle("Full Stack Java Mastery");
        sampleCourse.setInstructor(instructor);
        sampleCourse.setCategory(cat);
        sampleCourse.setLevel("Intermediate");
        sampleCourse.setPrice(199.99);
        sampleCourse = courseRepository.save(sampleCourse);
    }

    @Test
    @WithMockUser(username = "admin@skillhub.com", roles = "ADMIN")
    @DisplayName("BUG-020: Admin course form renders Intermediate level option without DM Sansmediate")
    public void testAdminCourseFormDisplaysIntermediateOption() throws Exception {
        mockMvc.perform(get("/admin/courses/edit").param("id", String.valueOf(sampleCourse.getId())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/course_form"))
                .andExpect(content().string(containsString("value=\"Intermediate\"")))
                .andExpect(content().string(containsString(">Intermediate</option>")))
                .andExpect(content().string(not(containsString("DM Sansmediate"))));
    }

    @Test
    @WithMockUser(username = "instructor@skillhub.com", roles = "INSTRUCTOR")
    @DisplayName("BUG-020: Instructor course creation form renders Intermediate Level option without DM Sansmediate")
    public void testInstructorCreateCourseFormDisplaysIntermediateOption() throws Exception {
        mockMvc.perform(get("/vendor/courses/create"))
                .andExpect(status().isOk())
                .andExpect(view().name("instructor/create_course"))
                .andExpect(content().string(containsString("value=\"Intermediate\"")))
                .andExpect(content().string(containsString(">Intermediate Level</option>")))
                .andExpect(content().string(not(containsString("DM Sansmediate"))));
    }

    @Test
    @WithMockUser(username = "instructor@skillhub.com", roles = "INSTRUCTOR")
    @DisplayName("BUG-020: Instructor course edit form renders Intermediate Level option without DM Sansmediate")
    public void testInstructorEditCourseFormDisplaysIntermediateOption() throws Exception {
        mockMvc.perform(get("/vendor/courses/" + sampleCourse.getId() + "/edit"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"Intermediate\"")))
                .andExpect(content().string(containsString("Intermediate Level</option>")))
                .andExpect(content().string(not(containsString("DM Sansmediate"))));
    }

    @Test
    @DisplayName("BUG-020: Course entity automatically sanitizes DM Sansmediate to Intermediate")
    public void testCourseEntityAutoSanitizesDMSansmediate() {
        Course course = new Course();
        course.setLevel("DM Sansmediate");
        assertEquals("Intermediate", course.getLevel());

        course.setLevel("Beginner");
        assertEquals("Beginner", course.getLevel());

        course.setLevel("Advanced");
        assertEquals("Advanced", course.getLevel());
    }
}
