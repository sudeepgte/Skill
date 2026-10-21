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
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.datasource.url=jdbc:h2:mem:publiccoursesdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
@DisplayName("Unauthenticated Public Access to Courses Integration Test (BUG-012)")
public class PublicCoursesAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @MockBean(name = "initData")
    private CommandLineRunner initData;

    @BeforeEach
    public void setUp() {
        courseRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("BUG-012: Anonymous visitor can access /courses without logging in (200 OK)")
    public void testAnonymousVisitorCanBrowseAllCourses() throws Exception {
        User instructor = new User("Music Teacher", "music@skillhub.com", "pass", "INSTRUCTOR");
        instructor = userRepository.save(instructor);

        Category musicCat = new Category("Music", "fa-music");
        musicCat = categoryRepository.save(musicCat);

        Course course = new Course();
        course.setTitle("Piano for Beginners");
        course.setInstructor(instructor);
        course.setCategory(musicCat);
        course.setPrice(49.99);
        course.setPublished(true);
        course = courseRepository.save(course);

        mockMvc.perform(get("/courses"))
                .andExpect(status().isOk())
                .andExpect(view().name("common/category_courses"))
                .andExpect(model().attribute("categoryName", "All"))
                .andExpect(model().attributeExists("courses"));
    }

    @Test
    @DisplayName("BUG-012: Anonymous visitor can access /courses/{category} e.g. /courses/Music without logging in (200 OK)")
    public void testAnonymousVisitorCanBrowseCategoryCourses() throws Exception {
        User instructor = new User("Music Teacher", "music2@skillhub.com", "pass", "INSTRUCTOR");
        instructor = userRepository.save(instructor);

        Category musicCat = new Category("Music", "fa-music");
        musicCat = categoryRepository.save(musicCat);

        Course course = new Course();
        course.setTitle("Guitar Mastery");
        course.setInstructor(instructor);
        course.setCategory(musicCat);
        course.setPrice(39.99);
        course.setPublished(true);
        course = courseRepository.save(course);

        mockMvc.perform(get("/courses/Music"))
                .andExpect(status().isOk())
                .andExpect(view().name("common/category_courses"))
                .andExpect(model().attribute("categoryName", "Music"))
                .andExpect(model().attributeExists("courses"));
    }
}
