package com.skillhub.controller;

import com.skillhub.dto.CourseUsageDto;
import com.skillhub.model.*;
import com.skillhub.repository.CourseRepository;
import com.skillhub.repository.InstructorSubscriptionRepository;
import com.skillhub.repository.SubscriptionPaymentRepository;
import com.skillhub.repository.SubscriptionPlanRepository;
import com.skillhub.repository.UserRepository;
import com.skillhub.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.datasource.url=jdbc:h2:mem:subtestdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "razorpay.key.id=rzp_test_mock",
    "razorpay.key.secret=mock_secret"
})
@AutoConfigureMockMvc
@DisplayName("Instructor Subscription Management System Integration Tests")
public class InstructorSubscriptionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private SubscriptionPlanRepository planRepository;

    @Autowired
    private InstructorSubscriptionRepository subscriptionRepository;

    @Autowired
    private SubscriptionPaymentRepository paymentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserRepository userRepository;

    private User testInstructor;
    private User testStudent;

    @BeforeEach
    public void setup() {
        courseRepository.deleteAll();
        paymentRepository.deleteAll();
        subscriptionRepository.deleteAll();

        subscriptionService.seedDefaultPlansIfEmpty();

        testInstructor = userRepository.findByEmail("test.instructor@skillhub.com");
        if (testInstructor == null) {
            testInstructor = new User("Instructor Bob", "test.instructor@skillhub.com", "pass123", "INSTRUCTOR");
            testInstructor = userRepository.save(testInstructor);
        }

        testStudent = userRepository.findByEmail("test.student@skillhub.com");
        if (testStudent == null) {
            testStudent = new User("Student Alice", "test.student@skillhub.com", "pass123", "STUDENT");
            testStudent = userRepository.save(testStudent);
        }
    }

    @Test
    @DisplayName("1. Subscription Plans are seeded and configured properly")
    public void testPlansSeeded() {
        assertTrue(planRepository.count() >= 5);
        assertTrue(planRepository.findByNameIgnoreCase("Free").isPresent());
        assertTrue(planRepository.findByNameIgnoreCase("Starter").isPresent());
        assertTrue(planRepository.findByNameIgnoreCase("Growth").isPresent());
        assertTrue(planRepository.findByNameIgnoreCase("Professional").isPresent());
        assertTrue(planRepository.findByNameIgnoreCase("Enterprise").isPresent());

        SubscriptionPlan growth = planRepository.findByNameIgnoreCase("Growth").get();
        assertEquals(499.0, growth.getMonthlyPrice());
        assertEquals(5, growth.getCourseLimit());
    }

    @Test
    @DisplayName("2. Tiered price calculation for custom course limits")
    public void testCustomPricingCalculation() {
        assertEquals(0.0, subscriptionService.calculateCustomPrice(1));
        assertEquals(199.0, subscriptionService.calculateCustomPrice(2));
        assertEquals(299.0, subscriptionService.calculateCustomPrice(3));
        assertEquals(499.0, subscriptionService.calculateCustomPrice(5));
        assertEquals(699.0, subscriptionService.calculateCustomPrice(7)); // 499 + 2*100
        assertEquals(999.0, subscriptionService.calculateCustomPrice(10));
        assertEquals(1449.0, subscriptionService.calculateCustomPrice(15)); // 999 + 5*90
    }

    @Test
    @WithMockUser(username = "test.instructor@skillhub.com", authorities = {"INSTRUCTOR"})
    @DisplayName("3. Instructor can activate Free tier plan without payment")
    public void testFreePlanActivation() throws Exception {
        mockMvc.perform(post("/api/subscriptions/subscribe-free")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        var activeSub = subscriptionService.getActiveSubscription(testInstructor);
        assertTrue(activeSub.isPresent());
        assertEquals("Free", activeSub.get().getPlanName());
        assertEquals(1, activeSub.get().getCourseLimit());
        assertEquals(0.0, activeSub.get().getMonthlyPrice());
        assertEquals(SubscriptionStatus.ACTIVE, activeSub.get().getStatus());
    }

    @Test
    @WithMockUser(username = "test.instructor@skillhub.com", authorities = {"INSTRUCTOR"})
    @DisplayName("4. Monthly course creation allowance is strictly enforced on backend")
    public void testCourseAllowanceEnforcement() throws Exception {
        // Activate free plan with 1 course allowance
        subscriptionService.subscribeFreePlan(testInstructor);

        CourseUsageDto usageBefore = subscriptionService.getCourseUsage(testInstructor);
        assertEquals(0, usageBefore.getUsedCount());
        assertEquals(1, usageBefore.getLimitCount());
        assertFalse(usageBefore.isLimitReached());
        assertTrue(subscriptionService.canCreateCourse(testInstructor));

        // Create 1st course -> Allowed
        mockMvc.perform(post("/vendor/courses/save")
                        .param("title", "First Allowed Course")
                        .param("price", "99")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vendor/dashboard?tab=courses"));

        CourseUsageDto usageAfterOne = subscriptionService.getCourseUsage(testInstructor);
        assertEquals(1, usageAfterOne.getUsedCount());
        assertEquals(0, usageAfterOne.getRemainingSlots());
        assertTrue(usageAfterOne.isLimitReached());
        assertFalse(subscriptionService.canCreateCourse(testInstructor));

        // Try creating 2nd course -> BLOCKED by monthly subscription allowance
        mockMvc.perform(post("/vendor/courses/save")
                        .param("title", "Second Excess Course")
                        .param("price", "199")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vendor/subscription"))
                .andExpect(flash().attributeExists("error"));

        // Verify only 1 course exists in database
        assertEquals(1, courseRepository.findByInstructorEmail("test.instructor@skillhub.com").size());
    }

    @Test
    @WithMockUser(username = "test.instructor@skillhub.com", authorities = {"INSTRUCTOR"})
    @DisplayName("5. Editing an existing course does not count against monthly allowance")
    public void testCourseEditDoesNotCountTowardsLimit() throws Exception {
        // Activate free plan with 1 course allowance
        subscriptionService.subscribeFreePlan(testInstructor);

        // Create 1 course (reaching limit of 1)
        Course course = new Course();
        course.setTitle("Original Title");
        course.setInstructor(testInstructor);
        course.setCreatedAt(LocalDateTime.now());
        course = courseRepository.save(course);

        assertTrue(subscriptionService.getCourseUsage(testInstructor).isLimitReached());

        // Edit the existing course (with its ID) -> Must succeed!
        mockMvc.perform(post("/vendor/courses/save")
                        .param("id", course.getId().toString())
                        .param("title", "Updated Title After Limit Reached")
                        .param("price", "299")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vendor/dashboard?tab=courses"));

        Course updated = courseRepository.findById(course.getId()).get();
        assertEquals("Updated Title After Limit Reached", updated.getTitle());
    }

    @Test
    @WithMockUser(username = "test.student@skillhub.com", authorities = {"STUDENT"})
    @DisplayName("6. Student cannot access instructor subscription page")
    public void testStudentAccessDenied() throws Exception {
        mockMvc.perform(get("/vendor/subscription"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@skillhub.com", authorities = {"ADMIN"})
    @DisplayName("7. Admin can access admin subscription console and view plans")
    public void testAdminConsoleAccess() throws Exception {
        mockMvc.perform(get("/admin/subscriptions"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/subscriptions"))
                .andExpect(model().attributeExists("subscriptions"))
                .andExpect(model().attributeExists("plans"))
                .andExpect(model().attributeExists("metrics"));
    }

    @Test
    @WithMockUser(username = "test.instructor@skillhub.com", authorities = {"INSTRUCTOR"})
    @DisplayName("8. Instructor can cancel subscription auto-renewal")
    public void testCancelSubscription() throws Exception {
        InstructorSubscription sub = subscriptionService.subscribeFreePlan(testInstructor);

        mockMvc.perform(post("/api/subscriptions/cancel")
                        .param("subscriptionId", sub.getId().toString())
                        .param("reason", "Taking a break")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        InstructorSubscription updatedSub = subscriptionRepository.findById(sub.getId()).get();
        assertEquals(SubscriptionStatus.CANCELLED, updatedSub.getStatus());
        assertFalse(updatedSub.isAutoRenew());
        assertNotNull(updatedSub.getCancelledAt());
        assertEquals("Taking a break", updatedSub.getCancellationReason());
    }
}
