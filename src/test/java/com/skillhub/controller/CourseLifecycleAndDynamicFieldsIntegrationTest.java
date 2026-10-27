package com.skillhub.controller;

import com.skillhub.model.*;
import com.skillhub.repository.*;
import com.skillhub.service.CategoryService;
import com.skillhub.service.CourseService;
import com.skillhub.service.EnrollmentService;
import com.skillhub.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.datasource.url=jdbc:h2:mem:lifecycle_testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
@Transactional
@DisplayName("SkillHub Complete Dynamic Course Creation & End-to-End Publishing Lifecycle Integration Tests")
public class CourseLifecycleAndDynamicFieldsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CourseRepository courseRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CourseSectionRepository sectionRepository;
    @Autowired private LectureRepository lectureRepository;
    @Autowired private CourseResourceRepository resourceRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private com.skillhub.repository.ReviewRepository reviewRepository;
    @Autowired private CategoryService categoryService;
    @Autowired private CourseService courseService;
    @Autowired private UserService userService;
    @Autowired private EnrollmentService enrollmentService;
    @Autowired private com.skillhub.repository.InstructorSubscriptionRepository subscriptionRepository;

    @org.springframework.boot.test.mock.mockito.MockBean(name = "initData")
    private org.springframework.boot.CommandLineRunner initData;

    private User vendor1;
    private User vendor2;
    private User adminUser;
    private User studentUser;
    private Category devCategory;
    private Category musicCategory;
    private Category fitnessCategory;

    @BeforeEach
    void setUp() {
        // Create test users
        vendor1 = new User();
        vendor1.setName("Vendor One");
        vendor1.setEmail("vendor1@skillhub.com");
        vendor1.setPassword("Pass@1234");
        vendor1.setRole("INSTRUCTOR");
        vendor1 = userRepository.save(vendor1);

        vendor2 = new User();
        vendor2.setName("Vendor Two");
        vendor2.setEmail("vendor2@skillhub.com");
        vendor2.setPassword("Pass@1234");
        vendor2.setRole("INSTRUCTOR");
        vendor2 = userRepository.save(vendor2);

        adminUser = new User();
        adminUser.setName("System Administrator");
        adminUser.setEmail("admin@skillhub.com");
        adminUser.setPassword("Admin@1234");
        adminUser.setRole("ADMIN");
        adminUser = userRepository.save(adminUser);

        studentUser = new User();
        studentUser.setName("Alice Student");
        studentUser.setEmail("student@skillhub.com");
        studentUser.setPassword("Student@1234");
        studentUser.setRole("STUDENT");
        studentUser = userRepository.save(studentUser);

        // Seed categories
        devCategory = new Category();
        devCategory.setName("Development and IT");
        devCategory = categoryRepository.save(devCategory);

        musicCategory = new Category();
        musicCategory.setName("Music");
        musicCategory = categoryRepository.save(musicCategory);

        fitnessCategory = new Category();
        fitnessCategory.setName("Health, Fitness and Yoga");
        fitnessCategory = categoryRepository.save(fitnessCategory);
    }

    @Test
    @DisplayName("Dynamic Category REST APIs - Subcategories and Field Definitions for All Categories")
    @WithMockUser(username = "vendor1@skillhub.com", authorities = {"INSTRUCTOR"})
    void testDynamicCategoryApis() throws Exception {
        // Test Development and IT subcategories
        mockMvc.perform(get("/vendor/api/categories/Development and IT/subcategories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItem("Web Development")))
                .andExpect(jsonPath("$", hasItem("Cloud & DevOps")));

        // Test Development and IT dynamic field definitions
        mockMvc.perform(get("/vendor/api/categories/Development and IT/fields"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].key", hasItem("programmingLanguages")))
                .andExpect(jsonPath("$[*].key", hasItem("frameworksLibrariesTools")))
                .andExpect(jsonPath("$[*].key", hasItem("devEnvironmentRequirements")));

        // Test Music category
        mockMvc.perform(get("/vendor/api/categories/Music/subcategories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItem("Acoustic & Electric Guitar")))
                .andExpect(jsonPath("$", hasItem("Music Production & DAW")));

        mockMvc.perform(get("/vendor/api/categories/Music/fields"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].key", hasItem("musicInstrumentDiscipline")))
                .andExpect(jsonPath("$[*].key", hasItem("requiredInstrumentsEquipment")));

        // Test Health, Fitness & Yoga category
        mockMvc.perform(get("/vendor/api/categories/Health, Fitness and Yoga/fields"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].key", hasItem("safetyGuidance")));
    }

    @Test
    @DisplayName("Stage 1: Vendor creates course draft with category-specific fields and common fields")
    @WithMockUser(username = "vendor1@skillhub.com", authorities = {"INSTRUCTOR"})
    void testVendorCreateDraftWithCategorySpecificData() throws Exception {
        mockMvc.perform(post("/vendor/courses/save")
                .with(csrf())
                .param("action", "draft")
                .param("title", "Full Stack Cloud Architecture with Spring Boot")
                .param("subtitle", "Learn enterprise microservices and cloud deployments.")
                .param("description", "A complete guide for software architects building scalable systems.")
                .param("longDescription", "Detailed curriculum on Spring Boot, Docker, Kubernetes, CI/CD, and monitoring.")
                .param("category.id", devCategory.getId().toString())
                .param("subCategory", "Cloud & DevOps")
                .param("level", "Intermediate")
                .param("language", "English")
                .param("format", "Project-based")
                .param("teachingMethod", "Self-paced")
                .param("tags", "Java, Spring Boot, AWS, Docker, Kubernetes")
                .param("price", "2999")
                .param("salePrice", "1499")
                .param("accessDuration", "Lifetime Access")
                .param("targetAudience", "Software developers, backend engineers, cloud architects")
                .param("requiredExperience", "1+ year Java experience")
                .param("expectedSkills", "Microservices orchestration, Docker containerization, AWS ECS")
                .param("ageRange", "Adults (18+ years)")
                .param("accessibilityRequirements", "A computer with at least 8GB RAM and Docker Desktop")
                .param("catData_programming_languages", "Java 21, SQL, Bash")
                .param("catData_frameworks_tools", "Spring Boot 3, Docker, Kubernetes, PostgreSQL")
                .param("catData_dev_environment", "IntelliJ IDEA, Docker Desktop")
                .param("catData_coding_projects", "Scalable E-Commerce Microservices Cluster"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vendor/dashboard?tab=courses"));

        // Verify persistence in database
        List<Course> instructorCourses = courseRepository.findByInstructorEmail("vendor1@skillhub.com");
        assertEquals(1, instructorCourses.size());
        Course created = instructorCourses.get(0);

        assertEquals("Full Stack Cloud Architecture with Spring Boot", created.getTitle());
        assertEquals(CourseStatus.DRAFT, created.getStatus());
        assertFalse(created.isPublished());
        assertEquals("Cloud & DevOps", created.getSubCategory());
        assertEquals("Project-based", created.getFormat());
        assertEquals("Lifetime Access", created.getAccessDuration());
        assertEquals(2999.0, created.getPrice());
        assertEquals(1499.0, created.getSalePrice());

        // Verify category-specific fields persistence
        Map<String, String> catData = created.getCategoryDataMap();
        assertNotNull(catData);
        assertEquals("Java 21, SQL, Bash", catData.get("programming_languages"));
        assertEquals("Spring Boot 3, Docker, Kubernetes, PostgreSQL", catData.get("frameworks_tools"));
        assertEquals("Scalable E-Commerce Microservices Cluster", catData.get("coding_projects"));
    }

    @Test
    @DisplayName("Stage 2: Student discovery gating - Unpublished course is protected from public/students")
    void testUnpublishedCourseGating() throws Exception {
        // Create draft course directly
        Course draftCourse = new Course();
        draftCourse.setTitle("Private Unpublished Draft Course");
        draftCourse.setDescription("Confidential draft material.");
        draftCourse.setCategory(devCategory);
        draftCourse.setInstructor(vendor1);
        draftCourse.setStatus(CourseStatus.DRAFT);
        draftCourse.setPublished(false);
        draftCourse = courseRepository.save(draftCourse);

        // 1. Unauthenticated student attempts to view -> redirected
        mockMvc.perform(get("/course/" + draftCourse.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?error=CourseNotPublished"));

        // 2. Logged in student attempts to view -> redirected
        mockMvc.perform(get("/course/" + draftCourse.getId())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("student@skillhub.com").roles("STUDENT")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?error=CourseNotPublished"));

        // 3. Other vendor (Vendor 2) attempts to view -> redirected
        mockMvc.perform(get("/course/" + draftCourse.getId())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("vendor2@skillhub.com").roles("INSTRUCTOR")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?error=CourseNotPublished"));

        // 4. Owner Vendor 1 views for preview -> 200 OK
        mockMvc.perform(get("/course/" + draftCourse.getId())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("vendor1@skillhub.com").roles("INSTRUCTOR")))
                .andExpect(status().isOk());

        // 5. System Admin views for verification -> 200 OK
        mockMvc.perform(get("/course/" + draftCourse.getId())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin@skillhub.com").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Stage 3, 4, 5 & 6: Full Admin Review Queue, Rejection with Feedback, and Vendor Resubmission")
    @WithMockUser(username = "vendor1@skillhub.com", authorities = {"INSTRUCTOR"})
    void testEndToEndReviewRejectionAndResubmissionLifecycle() throws Exception {
        // 1. Vendor creates and submits a course
        Course course = new Course();
        course.setTitle("Acoustic Fingerstyle Guitar Mastery");
        course.setDescription("Master advanced fingerpicking, percussion techniques, and open tunings.");
        course.setCategory(musicCategory);
        course.setSubCategory("Guitar & Bass");
        course.setInstructor(vendor1);
        course.setStatus(CourseStatus.DRAFT);
        course.setPrice(1200.0);
        course.putCategoryData("musicInstrumentDiscipline", "Steel-string Acoustic Guitar");
        course.putCategoryData("musicGenreTechnique", "Fingerstyle, Percussive, Travis Picking");
        course.putCategoryData("requiredInstrumentsEquipment", "Acoustic Guitar, Tuner, Capo");
        course = courseRepository.save(course);

        // Vendor submits course for review
        mockMvc.perform(post("/vendor/courses/" + course.getId() + "/submit")
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vendor/dashboard?tab=courses"));

        Course submitted = courseRepository.findById(course.getId()).orElseThrow();
        assertEquals(CourseStatus.PENDING_REVIEW, submitted.getStatus());
        assertFalse(submitted.isPublished());
        assertNotNull(submitted.getSubmittedAt());

        // 2. Admin opens pending queue & reviews course
        mockMvc.perform(get("/admin/courses")
                .param("status", "PENDING_REVIEW")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin@skillhub.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("pendingCount", greaterThanOrEqualTo(1L)))
                .andExpect(model().attribute("courses", hasItem(hasProperty("id", is(course.getId())))));

        // Admin opens verification review page
        mockMvc.perform(get("/admin/courses/" + course.getId() + "/review")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin@skillhub.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("categoryDataMap"))
                .andExpect(model().attribute("categoryDataMap", hasEntry("musicInstrumentDiscipline", "Steel-string Acoustic Guitar")));

        // 3. Admin rejects the course with specific feedback
        String rejectionFeedback = "Please provide downloadable practice tabs and clarify capo positions in module 1.";
        mockMvc.perform(post("/admin/courses/" + course.getId() + "/reject")
                .with(csrf())
                .param("reason", rejectionFeedback)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin@skillhub.com").roles("ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/courses"));

        Course rejected = courseRepository.findById(course.getId()).orElseThrow();
        assertEquals(CourseStatus.REJECTED, rejected.getStatus());
        assertEquals(rejectionFeedback, rejected.getRejectionReason());
        assertNotNull(rejected.getReviewedAt());

        // 4. Vendor opens edit page and sees rejection feedback
        mockMvc.perform(get("/vendor/courses/" + course.getId() + "/edit"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("course", hasProperty("rejectionReason", is(rejectionFeedback))))
                .andExpect(model().attribute("categoryDataMap", hasEntry("requiredInstrumentsEquipment", "Acoustic Guitar, Tuner, Capo")));

        // 5. Vendor updates course and resubmits
        mockMvc.perform(post("/vendor/courses/" + course.getId() + "/submit")
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vendor/dashboard?tab=courses"));

        Course resubmitted = courseRepository.findById(course.getId()).orElseThrow();
        assertEquals(CourseStatus.PENDING_REVIEW, resubmitted.getStatus());
        assertNull(resubmitted.getRejectionReason()); // Reason should be cleared upon resubmission
    }

    @Test
    @DisplayName("Stage 7 & 8: Admin Approval, Publication, and Student Discovery & Consumption")
    void testApprovalPublicationAndStudentAccessWorkflow() throws Exception {
        // Create pending review course with curriculum
        Course course = new Course();
        course.setTitle("Mindful Vinyasa Flow & Breathwork");
        course.setDescription("Holistic yoga practice for mental clarity and physical flexibility.");
        course.setCategory(fitnessCategory);
        course.setSubCategory("Yoga & Mindfulness");
        course.setInstructor(vendor1);
        course.setStatus(CourseStatus.PENDING_REVIEW);
        course.setPrice(0.0); // Free course
        course.putCategoryData("fitnessDiscipline", "Vinyasa Yoga & Pranayama");
        course.putCategoryData("safetyGuidance", "Consult a physician before starting if you have pre-existing spine injuries.");
        course = courseRepository.save(course);

        // Add section and lecture
        CourseSection section = new CourseSection();
        section.setTitle("Module 1: Breath Awareness");
        section.setCourse(course);
        section = sectionRepository.save(section);

        Lecture lecture = new Lecture();
        lecture.setTitle("Pranayama Fundamentals");
        lecture.setSection(section);
        lecture.setVideoUrl("/uploads/yoga-sample.mp4");
        lecture.setDurationSeconds(600);
        lecture = lectureRepository.save(lecture);

        // Admin approves course
        mockMvc.perform(post("/admin/courses/" + course.getId() + "/approve")
                .with(csrf())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin@skillhub.com").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ADMIN"))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/courses"));

        Course published = courseRepository.findById(course.getId()).orElseThrow();
        assertEquals(CourseStatus.PUBLISHED, published.getStatus());
        assertTrue(published.isPublished());
        assertNotNull(published.getReviewedAt());

        // Student discovers published course on course details page -> 200 OK
        mockMvc.perform(get("/course/" + course.getId())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("student@skillhub.com").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("categoryDataMap"))
                .andExpect(model().attribute("categoryDataMap", hasEntry("fitnessDiscipline", "Vinyasa Yoga & Pranayama")));

        // Student enrolls in the course
        mockMvc.perform(post("/api/enrollments/" + course.getId())
                .with(csrf())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("student@skillhub.com").roles("STUDENT")))
                .andExpect(status().isOk());

        assertTrue(enrollmentRepository.findByStudentAndCourse(studentUser, published).isPresent());

        // Enrolled student enters learning environment -> 200 OK
        mockMvc.perform(get("/user/course/" + course.getId() + "/learn")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("student@skillhub.com").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("course"))
                .andExpect(model().attribute("course", hasProperty("id", is(course.getId()))));
    }

    @Test
    @DisplayName("Stage 9: Downloadable Course Resources Persistence & Retrieval")
    @WithMockUser(username = "vendor1@skillhub.com", authorities = {"INSTRUCTOR"})
    void testDownloadableCourseResources() throws Exception {
        Course course = new Course();
        course.setTitle("Python Data Structures & Algorithms");
        course.setDescription("Comprehensive guide to algorithms with coding resources.");
        course.setCategory(devCategory);
        course.setInstructor(vendor1);
        course.setStatus(CourseStatus.DRAFT);
        course = courseRepository.save(course);

        // Attach resources
        CourseResource res1 = new CourseResource("Starter Code Repository", "Full source code for sorting algorithms", "/uploads/starter-algo.zip", "ZIP", 2048576L, course);
        CourseResource res2 = new CourseResource("Big-O Complexity Cheat Sheet", "High resolution PDF reference guide", "/uploads/big-o-guide.pdf", "PDF", 102400L, course);
        resourceRepository.save(res1);
        resourceRepository.save(res2);

        List<CourseResource> savedResources = resourceRepository.findByCourseId(course.getId());
        assertEquals(2, savedResources.size());
        assertEquals("ZIP", savedResources.get(0).getFileType());
        assertEquals("PDF", savedResources.get(1).getFileType());
    }

    @Test
    @DisplayName("Security & IDOR: Vendor B cannot modify or submit Vendor A's courses")
    void testSecurityAndIdorProtection() throws Exception {
        // Course owned by vendor 1
        Course course1 = new Course();
        course1.setTitle("Vendor 1 Exclusive Course");
        course1.setDescription("Created by vendor 1.");
        course1.setCategory(devCategory);
        course1.setInstructor(vendor1);
        course1.setStatus(CourseStatus.DRAFT);
        course1 = courseRepository.save(course1);

        // Vendor 2 tries to access edit page -> 403 Forbidden
        mockMvc.perform(get("/vendor/courses/" + course1.getId() + "/edit")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("vendor2@skillhub.com").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("INSTRUCTOR"))))
                .andExpect(status().isForbidden());

        // Vendor 2 tries to submit vendor 1's course -> 403 Forbidden
        mockMvc.perform(post("/vendor/courses/" + course1.getId() + "/submit")
                .with(csrf())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("vendor2@skillhub.com").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("INSTRUCTOR"))))
                .andExpect(status().isForbidden());

        // Non-admin user tries to approve course -> 403 Forbidden
        mockMvc.perform(post("/admin/courses/" + course1.getId() + "/approve")
                .with(csrf())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("student@skillhub.com").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("STUDENT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Stage 10: Multi-Category Dynamic Fields Creation, Persistence & Retrieval across all remaining categories")
    @WithMockUser(username = "vendor1@skillhub.com", authorities = {"INSTRUCTOR"})
    void testMultipleCategoriesDynamicFieldPersistence() throws Exception {
        com.skillhub.model.InstructorSubscription sub = new com.skillhub.model.InstructorSubscription();
        sub.setInstructor(vendor1);
        sub.setPlanName("Enterprise");
        sub.setCourseLimit(50);
        sub.setMonthlyPrice(0.0);
        sub.setStatus(com.skillhub.model.SubscriptionStatus.ACTIVE);
        sub.setBillingInterval("MONTHLY");
        sub.setStartDate(java.time.LocalDateTime.now().minusDays(1));
        sub.setEndDate(java.time.LocalDateTime.now().plusDays(30));
        subscriptionRepository.save(sub);

        // Seed remaining categories
        Category bizCat = categoryRepository.save(new Category("Business and Marketing", "fa-briefcase"));
        Category officeCat = categoryRepository.save(new Category("Office Productivity and Design", "fa-desktop"));
        Category artCat = categoryRepository.save(new Category("Art, Craft and Painting", "fa-palette"));
        Category kidsCat = categoryRepository.save(new Category("Kids Learning and Speaking", "fa-child"));
        Category langCat = categoryRepository.save(new Category("Languages", "fa-language"));
        Category photoCat = categoryRepository.save(new Category("Photography", "fa-camera"));
        Category otherCat = categoryRepository.save(new Category("Other", "fa-ellipsis-h"));

        // 1. Business and Marketing
        mockMvc.perform(post("/vendor/courses/save")
                .with(csrf())
                .param("action", "draft")
                .param("title", "Growth Hacking & B2B SaaS Marketing Masterclass")
                .param("description", "Customer acquisition funnels, conversion optimization, and analytics.")
                .param("category.id", bizCat.getId().toString())
                .param("subCategory", "Digital Marketing")
                .param("catData_businessDomainIndustry", "B2B SaaS & Tech Startups")
                .param("catData_marketingChannelsStrategies", "Organic SEO, LinkedIn Ads, Cold Email")
                .param("catData_businessToolsPlatforms", "HubSpot, Google Analytics 4, Mixpanel"))
                .andExpect(status().is3xxRedirection());

        // 2. Office Productivity and Design
        mockMvc.perform(post("/vendor/courses/save")
                .with(csrf())
                .param("action", "draft")
                .param("title", "Figma to Webflow Design System Architecture")
                .param("description", "Create scalable design systems, auto-layouts, and component libraries.")
                .param("category.id", officeCat.getId().toString())
                .param("subCategory", "UI/UX Design")
                .param("catData_designSoftwareTaught", "Figma, Webflow, Tokens Studio")
                .param("catData_designDiscipline", "UI/UX Design"))
                .andExpect(status().is3xxRedirection());

        // 3. Art, Craft and Painting
        mockMvc.perform(post("/vendor/courses/save")
                .with(csrf())
                .param("action", "draft")
                .param("title", "Botanical Watercolor Illustration Techniques")
                .param("description", "Master wet-on-wet glazing and realistic plant textures.")
                .param("category.id", artCat.getId().toString())
                .param("subCategory", "Watercolor Painting")
                .param("catData_artFormTechnique", "Wet-on-wet Watercolor Glazing")
                .param("catData_materialsEquipment", "Arches 300gsm cold press paper, Kolinsky sable brushes"))
                .andExpect(status().is3xxRedirection());

        // 4. Kids Learning and Speaking
        mockMvc.perform(post("/vendor/courses/save")
                .with(csrf())
                .param("action", "draft")
                .param("title", "Little Orators: Confident Public Speaking for Young Minds")
                .param("description", "Fun speech games, storytelling, and stage presence for kids.")
                .param("category.id", kidsCat.getId().toString())
                .param("subCategory", "Public Speaking for Kids")
                .param("catData_kidsAgeGroup", "Ages 7 - 9 (Elementary)")
                .param("catData_kidsSubjectArea", "Storytelling & Speech Confidence"))
                .andExpect(status().is3xxRedirection());

        // 5. Languages
        mockMvc.perform(post("/vendor/courses/save")
                .with(csrf())
                .param("action", "draft")
                .param("title", "Conversational Spanish Fluency from Zero")
                .param("description", "Master everyday speaking, pronunciation, and practical grammar.")
                .param("category.id", langCat.getId().toString())
                .param("subCategory", "Spanish")
                .param("catData_sourceTargetLanguage", "Target: Spanish | Instruction: English")
                .param("catData_languageProficiencyLevel", "A1 - Complete Beginner")
                .param("catData_languageFocusAreas", "Everyday Speaking & Pronunciation"))
                .andExpect(status().is3xxRedirection());

        // 6. Photography
        mockMvc.perform(post("/vendor/courses/save")
                .with(csrf())
                .param("action", "draft")
                .param("title", "Cinematic Portrait Photography & Lighting")
                .param("description", "Master natural light, studio strobes, and Lightroom color grading.")
                .param("category.id", photoCat.getId().toString())
                .param("subCategory", "Portrait Photography")
                .param("catData_photographyGenre", "Portrait & People")
                .param("catData_cameraEquipmentRequirements", "DSLR/Mirrorless with 50mm or 85mm prime lens")
                .param("catData_editingSoftwareTools", "Adobe Lightroom Classic"))
                .andExpect(status().is3xxRedirection());

        // 7. Other
        mockMvc.perform(post("/vendor/courses/save")
                .with(csrf())
                .param("action", "draft")
                .param("title", "Mastering Artisanal Coffee Brewing & Roasting")
                .param("description", "Specialty coffee science, water chemistry, and extraction techniques.")
                .param("category.id", otherCat.getId().toString())
                .param("subCategory", "Hobbies & Lifestyle")
                .param("catData_customDomain", "Specialty Coffee Science")
                .param("catData_customObjectives", "Dial in espresso grinders, brew V60 pourovers, and roast green beans."))
                .andExpect(status().is3xxRedirection());

        // Verify all 7 category courses were created and category data is retrievable
        List<Course> allCreated = courseRepository.findByInstructorEmail("vendor1@skillhub.com");
        assertTrue(allCreated.size() >= 7);

        for (Course c : allCreated) {
            Map<String, String> data = c.getCategoryDataMap();
            assertNotNull(data, "Category data map should not be null for course " + c.getTitle());
            if ("Growth Hacking & B2B SaaS Marketing Masterclass".equals(c.getTitle())) {
                assertEquals("B2B SaaS & Tech Startups", data.get("businessDomainIndustry"));
            } else if ("Figma to Webflow Design System Architecture".equals(c.getTitle())) {
                assertEquals("Figma, Webflow, Tokens Studio", data.get("designSoftwareTaught"));
            } else if ("Botanical Watercolor Illustration Techniques".equals(c.getTitle())) {
                assertEquals("Wet-on-wet Watercolor Glazing", data.get("artFormTechnique"));
            } else if ("Little Orators: Confident Public Speaking for Young Minds".equals(c.getTitle())) {
                assertEquals("Ages 7 - 9 (Elementary)", data.get("kidsAgeGroup"));
            } else if ("Conversational Spanish Fluency from Zero".equals(c.getTitle())) {
                assertEquals("A1 - Complete Beginner", data.get("languageProficiencyLevel"));
            } else if ("Cinematic Portrait Photography & Lighting".equals(c.getTitle())) {
                assertEquals("Adobe Lightroom Classic", data.get("editingSoftwareTools"));
            } else if ("Mastering Artisanal Coffee Brewing & Roasting".equals(c.getTitle())) {
                assertEquals("Specialty Coffee Science", data.get("customDomain"));
            }
        }
    }

    @Test
    @DisplayName("Stage 14: Course Review Submission Requires Prior Enrollment (BUG-035)")
    void testCourseReview_RequiresEnrollment() throws Exception {
        Course course = new Course();
        course.setTitle("Docker & Kubernetes for Cloud Engineers");
        course.setDescription("Master containers and orchestration.");
        course.setCategory(devCategory);
        course.setInstructor(vendor1);
        course.setStatus(CourseStatus.PUBLISHED);
        course = courseRepository.save(course);

        // 1. Non-enrolled student attempts to post review -> rejected with error
        mockMvc.perform(post("/reviews/add")
                .with(csrf())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("student@skillhub.com").roles("STUDENT"))
                .param("courseId", course.getId().toString())
                .param("rating", "5")
                .param("comment", "Great!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/course/" + course.getId()))
                .andExpect(flash().attribute("error", "Only students who have enrolled in this course are permitted to rate and review it."));

        assertEquals(0, reviewRepository.findByCourseIdOrderByCreatedAtDesc(course.getId()).size(),
                "Non-enrolled user should not be able to save review");

        // 2. Student enrolls in the course
        Enrollment enrollment = new Enrollment();
        enrollment.setStudent(studentUser);
        enrollment.setCourse(course);
        enrollmentRepository.save(enrollment);

        // 3. Enrolled student posts review -> accepted with success
        mockMvc.perform(post("/reviews/add")
                .with(csrf())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("student@skillhub.com").roles("STUDENT"))
                .param("courseId", course.getId().toString())
                .param("rating", "5")
                .param("comment", "Great!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/course/" + course.getId()))
                .andExpect(flash().attribute("success", "Review submitted successfully!"));

        assertEquals(1, reviewRepository.findByCourseIdOrderByCreatedAtDesc(course.getId()).size(),
                "Enrolled user review should be saved successfully");
    }
}
