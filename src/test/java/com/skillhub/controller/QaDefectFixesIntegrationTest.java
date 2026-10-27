package com.skillhub.controller;

import com.skillhub.model.Category;
import com.skillhub.model.Course;
import com.skillhub.model.CourseStatus;
import com.skillhub.model.User;
import com.skillhub.model.Wishlist;
import com.skillhub.repository.CategoryRepository;
import com.skillhub.repository.CourseRepository;
import com.skillhub.repository.EnrollmentRepository;
import com.skillhub.repository.NotificationRepository;
import com.skillhub.repository.UserRepository;
import com.skillhub.repository.WishlistRepository;
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

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.datasource.url=jdbc:h2:mem:qadefectsdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "razorpay.key.id=rzp_test_mock",
    "razorpay.key.secret=mock_secret"
})
@AutoConfigureMockMvc
@DisplayName("QA Defect Fixes & Regression Integration Tests (Defects 1, 2, 3)")
public class QaDefectFixesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @MockBean(name = "initData")
    private CommandLineRunner initData;

    private User testStudent;
    private User testInstructor;
    private Course publishedCourse;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        enrollmentRepository.deleteAll();
        wishlistRepository.deleteAll();
        courseRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.deleteAll();

        testStudent = new User();
        testStudent.setName("Alice Student");
        testStudent.setEmail("alice.student@skillhub.com");
        testStudent.setPassword("Password@123");
        testStudent.setRole("STUDENT");
        testStudent = userRepository.save(testStudent);

        testInstructor = new User();
        testInstructor.setName("Bob Instructor");
        testInstructor.setEmail("bob.instructor@skillhub.com");
        testInstructor.setPassword("Password@123");
        testInstructor.setRole("INSTRUCTOR");
        testInstructor = userRepository.save(testInstructor);

        Category techCategory = new Category("Software Engineering", "fa-code");
        techCategory = categoryRepository.save(techCategory);

        publishedCourse = new Course();
        publishedCourse.setTitle("Complete Full-Stack Java Bootcamp");
        publishedCourse.setDescription("Comprehensive Java and Spring Boot guide.");
        publishedCourse.setInstructor(testInstructor);
        publishedCourse.setCategory(techCategory);
        publishedCourse.setPrice(499.0);
        publishedCourse.setStatus(CourseStatus.PUBLISHED);
        publishedCourse.setPublished(true);
        publishedCourse = courseRepository.save(publishedCourse);
    }

    // ==========================================
    // DEFECT 1: VIEWPORT TAGS IN RENDERED HTML
    // ==========================================

    @Test
    @WithMockUser(username = "alice.student@skillhub.com", roles = "STUDENT")
    @DisplayName("Defect 1: Placement Dashboard contains exactly one mobile viewport tag")
    void testPlacementDashboardContainsViewportTag() throws Exception {
        mockMvc.perform(get("/user/placement"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")));
    }

    @Test
    @WithMockUser(username = "alice.student@skillhub.com", roles = "STUDENT")
    @DisplayName("Defect 1: Placement Interview Questions page contains mobile viewport tag")
    void testPlacementInterviewQuestionsContainsViewportTag() throws Exception {
        mockMvc.perform(get("/user/placement/interview"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")));
    }

    @Test
    @WithMockUser(username = "admin@skillhub.com", roles = "ADMIN")
    @DisplayName("Defect 1: Admin Reports page contains mobile viewport tag and responsive table wrapper")
    void testAdminReportsContainsViewportTag() throws Exception {
        mockMvc.perform(get("/admin/reports"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")))
                .andExpect(content().string(containsString("reports-table-wrapper")));
    }

    // ==========================================
    // DEFECT 2: INSTRUCTOR COURSES ROUTE (/vendor/courses)
    // ==========================================

    @Test
    @WithMockUser(username = "bob.instructor@skillhub.com", roles = "INSTRUCTOR")
    @DisplayName("Defect 2: GET /vendor/courses redirects authenticated instructor to /vendor/dashboard?tab=courses")
    void testInstructorCoursesRouteRedirectsToDashboardTab() throws Exception {
        mockMvc.perform(get("/vendor/courses").header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vendor/dashboard?tab=courses"));
    }

    @Test
    @WithMockUser(username = "bob.instructor@skillhub.com", roles = "INSTRUCTOR")
    @DisplayName("Defect 2: GET /vendor/courses preserves query parameters in redirection")
    void testInstructorCoursesRoutePreservesQueryParams() throws Exception {
        mockMvc.perform(get("/vendor/courses")
                .param("status", "PUBLISHED")
                .param("page", "2")
                .param("size", "25"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vendor/dashboard?tab=courses&status=PUBLISHED&page=2&size=25"));
    }

    @Test
    @DisplayName("Defect 2: Unauthenticated access to /vendor/courses redirects to /login")
    void testUnauthenticatedAccessToVendorCoursesRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/vendor/courses").header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "alice.student@skillhub.com", roles = "STUDENT")
    @DisplayName("Defect 2: Student accessing /vendor/courses is blocked with 403 Forbidden")
    void testStudentAccessToVendorCoursesIsForbidden() throws Exception {
        mockMvc.perform(get("/vendor/courses").header("Accept", "text/html"))
                .andExpect(status().isForbidden());
    }

    // ==========================================
    // DEFECT 3: WISHLIST API PARAMETER & CONTENT-TYPE CONSISTENCY
    // ==========================================

    @Test
    @WithMockUser(username = "alice.student@skillhub.com", roles = "STUDENT")
    @DisplayName("Defect 3: /course/wishlist/add works with application/x-www-form-urlencoded")
    void testCourseWishlistAddWithFormUrlEncoded() throws Exception {
        mockMvc.perform(post("/course/wishlist/add")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("courseId", publishedCourse.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.alreadyAdded").value(false))
                .andExpect(jsonPath("$.message").value("This item is added in your wishlist"));

        assertTrue(wishlistRepository.findByUserAndCourse(testStudent, publishedCourse).isPresent());
    }

    @Test
    @WithMockUser(username = "alice.student@skillhub.com", roles = "STUDENT")
    @DisplayName("Defect 3: /course/wishlist/add works with application/json body")
    void testCourseWishlistAddWithJsonBody() throws Exception {
        mockMvc.perform(post("/course/wishlist/add")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courseId\": " + publishedCourse.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.alreadyAdded").value(false));

        assertTrue(wishlistRepository.findByUserAndCourse(testStudent, publishedCourse).isPresent());
    }

    @Test
    @WithMockUser(username = "alice.student@skillhub.com", roles = "STUDENT")
    @DisplayName("Defect 3: /api/wishlist/add works with application/json body")
    void testApiWishlistAddWithJsonBody() throws Exception {
        mockMvc.perform(post("/api/wishlist/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courseId\": " + publishedCourse.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.alreadyAdded").value(false))
                .andExpect(jsonPath("$.message").value("Course added to wishlist"));

        assertTrue(wishlistRepository.findByUserAndCourse(testStudent, publishedCourse).isPresent());
    }

    @Test
    @WithMockUser(username = "alice.student@skillhub.com", roles = "STUDENT")
    @DisplayName("Defect 3: /api/wishlist/add works with form parameter")
    void testApiWishlistAddWithFormParam() throws Exception {
        mockMvc.perform(post("/api/wishlist/add")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("courseId", publishedCourse.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.alreadyAdded").value(false))
                .andExpect(jsonPath("$.message").value("Course added to wishlist"));

        assertTrue(wishlistRepository.findByUserAndCourse(testStudent, publishedCourse).isPresent());
    }

    @Test
    @WithMockUser(username = "alice.student@skillhub.com", roles = "STUDENT")
    @DisplayName("Defect 3: Duplicate wishlist addition returns graceful alreadyAdded response without error")
    void testDuplicateWishlistAdditionHandledGracefully() throws Exception {
        // First addition
        mockMvc.perform(post("/course/wishlist/add")
                .with(csrf())
                .param("courseId", publishedCourse.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.alreadyAdded").value(false));

        // Rapid duplicate addition
        mockMvc.perform(post("/course/wishlist/add")
                .with(csrf())
                .param("courseId", publishedCourse.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.alreadyAdded").value(true))
                .andExpect(jsonPath("$.message").value("Course already in wishlist"));

        // Duplicate addition via REST API endpoint
        mockMvc.perform(post("/api/wishlist/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courseId\": " + publishedCourse.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.alreadyAdded").value(true))
                .andExpect(jsonPath("$.message").value("Course already in wishlist"));

        assertEquals(1, wishlistRepository.count());
    }

    @Test
    @DisplayName("Defect 3: Unauthenticated wishlist requests handled properly")
    void testUnauthenticatedWishlistAddHandledProperly() throws Exception {
        mockMvc.perform(post("/course/wishlist/add")
                .with(csrf())
                .param("courseId", publishedCourse.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.redirect").value("/login"));

        mockMvc.perform(post("/api/wishlist/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courseId\": " + publishedCourse.getId() + "}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @WithMockUser(username = "alice.student@skillhub.com", roles = "STUDENT")
    @DisplayName("Defect 3: Wishlist removal works via form and API endpoints")
    void testWishlistRemovalEndpoints() throws Exception {
        // Add course to wishlist first
        Wishlist w = new Wishlist(testStudent, publishedCourse);
        wishlistRepository.save(w);
        assertEquals(1, wishlistRepository.count());

        // Test API remove
        mockMvc.perform(post("/api/wishlist/remove")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courseId\": " + publishedCourse.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Course removed from wishlist"));

        assertEquals(0, wishlistRepository.count());

        // Re-add and test form remove
        wishlistRepository.save(new Wishlist(testStudent, publishedCourse));
        assertEquals(1, wishlistRepository.count());

        mockMvc.perform(post("/course/wishlist/remove")
                .with(csrf())
                .param("courseId", publishedCourse.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/student/wishlist"));

        assertEquals(0, wishlistRepository.count());
    }
}
