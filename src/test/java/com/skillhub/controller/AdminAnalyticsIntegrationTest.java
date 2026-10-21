package com.skillhub.controller;

import com.skillhub.model.Category;
import com.skillhub.model.Course;
import com.skillhub.model.Enrollment;
import com.skillhub.model.User;
import com.skillhub.repository.CategoryRepository;
import com.skillhub.repository.CourseRepository;
import com.skillhub.repository.EnrollmentRepository;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.datasource.url=jdbc:h2:mem:adminanalyticstestdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
@DisplayName("Admin Analytics Route Integration Test (BUG-011)")
public class AdminAnalyticsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @MockBean(name = "initData")
    private CommandLineRunner initData;

    @BeforeEach
    public void setUp() {
        enrollmentRepository.deleteAll();
        courseRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "admin@skillhub.com", authorities = {"ADMIN", "ROLE_ADMIN"})
    @DisplayName("BUG-011: Admin navigates to /admin/analytics should render admin/analytics view and not return 404")
    public void testAdminAnalyticsRouteRendersSuccessfully() throws Exception {
        // Create sample data
        User student = new User("John Student", "student@skillhub.com", "pass", "STUDENT");
        student = userRepository.save(student);

        User instructor = new User("Jane Instructor", "instructor@skillhub.com", "pass", "INSTRUCTOR");
        instructor = userRepository.save(instructor);

        Category cat = new Category("Web Development", "fa-code");
        cat = categoryRepository.save(cat);

        Course course = new Course();
        course.setTitle("Full Stack Mastery");
        course.setInstructor(instructor);
        course.setCategory(cat);
        course.setPrice(99.99);
        course = courseRepository.save(course);

        Enrollment enrollment = new Enrollment();
        enrollment.setStudent(student);
        enrollment.setCourse(course);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollmentRepository.save(enrollment);

        mockMvc.perform(get("/admin/analytics"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/analytics"))
                .andExpect(model().attributeExists("totalUsers"))
                .andExpect(model().attributeExists("totalCourses"))
                .andExpect(model().attributeExists("totalEnrollments"))
                .andExpect(model().attributeExists("totalInstructors"))
                .andExpect(model().attributeExists("activeUsers"))
                .andExpect(model().attributeExists("inactiveUsers"))
                .andExpect(model().attributeExists("enrollmentLabels"))
                .andExpect(model().attributeExists("enrollmentValues"))
                .andExpect(model().attributeExists("categoryLabels"))
                .andExpect(model().attributeExists("categoryValues"))
                .andExpect(content().string(containsString("Admin Intelligence")))
                .andExpect(content().string(containsString("enrollmentChart")))
                .andExpect(content().string(containsString("activityChart")))
                .andExpect(content().string(containsString("categoryChart")));
    }

    @Test
    @DisplayName("Unauthenticated request to /admin/analytics should redirect to login")
    public void testUnauthenticatedAccessRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/analytics"))
                .andExpect(status().isUnauthorized());
    }
}
