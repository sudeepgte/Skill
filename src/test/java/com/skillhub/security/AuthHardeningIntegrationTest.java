package com.skillhub.security;

import com.skillhub.model.Course;
import com.skillhub.model.Payment;
import com.skillhub.model.User;
import com.skillhub.repository.CourseRepository;
import com.skillhub.repository.PaymentRepository;
import com.skillhub.repository.UserRepository;
import com.skillhub.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.datasource.url=jdbc:h2:mem:authtestdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
public class AuthHardeningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean(name = "initData")
    private CommandLineRunner initData;

    @MockBean(name = "seedProfessionalExamAttempt")
    private CommandLineRunner seedProfessionalExamAttempt;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private CourseRepository courseRepository;

    @MockBean
    private PaymentRepository paymentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserService userService;

    private User studentUser;
    private User instructorUser;
    private User unapprovedInstructor;
    private User blockedUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        studentUser = new User("Test Student", "student@test.com", passwordEncoder.encode("password123"), "STUDENT");
        studentUser.setId(101L);
        studentUser.setApproved(true);
        studentUser.setBlocked(false);

        instructorUser = new User("Test Instructor", "instructor@test.com", passwordEncoder.encode("password123"), "INSTRUCTOR");
        instructorUser.setId(201L);
        instructorUser.setApproved(true);
        instructorUser.setBlocked(false);

        unapprovedInstructor = new User("Pending Instructor", "pending@test.com", passwordEncoder.encode("password123"), "INSTRUCTOR");
        unapprovedInstructor.setId(202L);
        unapprovedInstructor.setApproved(false);
        unapprovedInstructor.setBlocked(false);

        blockedUser = new User("Blocked User", "blocked@test.com", passwordEncoder.encode("password123"), "STUDENT");
        blockedUser.setId(301L);
        blockedUser.setApproved(true);
        blockedUser.setBlocked(true);

        adminUser = new User("Test Admin", "admin@test.com", passwordEncoder.encode("password123"), "ADMIN");
        adminUser.setId(1L);
        adminUser.setApproved(true);
        adminUser.setBlocked(false);

        when(userRepository.findByEmail("student@test.com")).thenReturn(studentUser);
        when(userRepository.findByEmail("instructor@test.com")).thenReturn(instructorUser);
        when(userRepository.findByEmail("pending@test.com")).thenReturn(unapprovedInstructor);
        when(userRepository.findByEmail("blocked@test.com")).thenReturn(blockedUser);
        when(userRepository.findByEmail("admin@test.com")).thenReturn(adminUser);
    }

    // =========================================================================
    // TASK 2 & 3: REGISTRATION, PASSWORD POLICY, DUPLICATE PREVENTION
    // =========================================================================

    @Test
    @DisplayName("Registration: Rejects passwords shorter than 8 characters")
    void testRegistrationRejectsWeakPassword() throws Exception {
        mockMvc.perform(post("/saveUser")
                        .with(csrf())
                        .param("name", "New User")
                        .param("email", "newuser@test.com")
                        .param("password", "short")
                        .param("role", "STUDENT"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/register?error=invalid_password"));
    }

    @Test
    @DisplayName("Registration: Rejects duplicate email registration")
    void testRegistrationRejectsDuplicateEmail() throws Exception {
        when(userRepository.existsByEmail("student@test.com")).thenReturn(true);

        mockMvc.perform(post("/saveUser")
                        .with(csrf())
                        .param("name", "Duplicate User")
                        .param("email", "student@test.com")
                        .param("password", "validPassword123")
                        .param("role", "STUDENT"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/register?error=exists"));
    }

    @Test
    @DisplayName("Registration: Unauthenticated users cannot register as ADMIN")
    void testUnauthenticatedCannotRegisterAdmin() throws Exception {
        mockMvc.perform(post("/saveUser")
                        .with(csrf())
                        .param("name", "Hacker Admin")
                        .param("email", "hacker@test.com")
                        .param("password", "validPassword123")
                        .param("role", "ADMIN"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // =========================================================================
    // TASK 4: PENDING OR REJECTED INSTRUCTORS ENFORCEMENT
    // =========================================================================

    @Test
    @DisplayName("Login: Unapproved instructor cannot login and gets redirected with unapproved flag")
    void testUnapprovedInstructorCannotLogin() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "pending@test.com")
                        .param("password", "password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?unapproved=true"));
    }

    @Test
    @DisplayName("Login: Blocked user cannot login and gets redirected with blocked flag")
    void testBlockedUserCannotLogin() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "blocked@test.com")
                        .param("password", "password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?blocked=true"));
    }

    @Test
    @DisplayName("Login: Non-existent account does not disclose existence")
    void testNonExistentAccountGenericError() throws Exception {
        when(userRepository.findByEmail("unknown@test.com")).thenReturn(null);

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "unknown@test.com")
                        .param("password", "anyPassword123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=true"));
    }

    // =========================================================================
    // TASK 5: ROLE-BASED ACCESS CONTROL (STUDENT vs INSTRUCTOR vs ADMIN)
    // =========================================================================

    @Test
    @WithMockUser(username = "student@test.com", roles = {"STUDENT"})
    @DisplayName("RBAC: Student cannot access Admin console (/admin/dashboard)")
    void testStudentCannotAccessAdminDashboard() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "student@test.com", roles = {"STUDENT"})
    @DisplayName("RBAC: Student cannot access Vendor console (/vendor/dashboard)")
    void testStudentCannotAccessVendorDashboard() throws Exception {
        mockMvc.perform(get("/vendor/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "instructor@test.com", roles = {"INSTRUCTOR"})
    @DisplayName("RBAC: Instructor cannot access Admin console (/admin/dashboard)")
    void testInstructorCannotAccessAdminDashboard() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RBAC: Unauthenticated access to /instructor/subscription is rejected")
    void testUnauthenticatedCannotAccessInstructorSubscription() throws Exception {
        mockMvc.perform(get("/instructor/subscription"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // TASK 7: HORIZONTAL PRIVILEGE ESCALATION (IDOR)
    // =========================================================================

    @Test
    @WithMockUser(username = "student@test.com", roles = {"STUDENT"})
    @DisplayName("IDOR: Student cannot view another user's payment receipt")
    void testStudentCannotViewOtherUsersReceipt() throws Exception {
        User otherUser = new User("Other User", "other@test.com", "pass", "STUDENT");
        otherUser.setId(999L);

        Payment otherPayment = new Payment();
        otherPayment.setId(55L);
        otherPayment.setTransactionId("TXN_OTHER_123");
        otherPayment.setUser(otherUser);

        when(paymentRepository.findByTransactionId("TXN_OTHER_123")).thenReturn(Optional.of(otherPayment));

        mockMvc.perform(get("/payment/receipt/TXN_OTHER_123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?error=Unauthorized"));
    }

    @Test
    @WithMockUser(username = "student@test.com", roles = {"STUDENT"})
    @DisplayName("IDOR: Student cannot generate sample curriculum for a course")
    void testStudentCannotGenerateCurriculum() throws Exception {
        Course course = new Course();
        course.setId(10L);
        course.setTitle("Target Course");
        course.setInstructor(instructorUser);

        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));

        mockMvc.perform(get("/user/course/10/generate-sample"))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // TASK 9: PASSWORD RESET & ACCOUNT ENUMERATION PROTECTION
    // =========================================================================

    @Test
    @DisplayName("Password Reset: Non-existent email returns uniform response without disclosing account existence")
    void testForgotPasswordUniformResponse() throws Exception {
        when(userRepository.findByEmail("nonexistent@test.com")).thenReturn(null);

        mockMvc.perform(post("/forgot-password")
                        .with(csrf())
                        .param("email", "nonexistent@test.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("forgot_password"))
                .andExpect(model().attribute("otpSent", true));
    }

    // =========================================================================
    // TASK 10: JWT INTEGRATION SECURITY
    // =========================================================================

    @Test
    @DisplayName("JWT: Rejects invalid or forged token signature")
    void testJwtRejectsForgedSignature() {
        String forgedToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
                "eyJzdWIiOiJzdHVkZW50QHRlc3QuY29tIiwiaXNzIjoiU2tpbGxIdWIiLCJpYXQiOjE2MDAwMDAwMDAsImV4cCI6OTk5OTk5OTk5OX0." +
                "InVaLiDsIgNaTuReHeRe1234567890abcdefg";

        boolean isValid = jwtUtils.validateJwtToken(forgedToken);
        assertFalse(isValid, "Forged JWT signature must be rejected");
    }

    @Test
    @DisplayName("JWT: Rejects token with invalid issuer")
    void testJwtRejectsInvalidIssuer() {
        String foreignToken = io.jsonwebtoken.Jwts.builder()
                .setSubject("student@test.com")
                .setIssuer("MaliciousIssuer")
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor("skillhubSecretKeyskillhubSecretKeyskillhubSecretKeyskillhubSecretKey".getBytes()), io.jsonwebtoken.SignatureAlgorithm.HS256)
                .compact();

        boolean isValid = jwtUtils.validateJwtToken(foreignToken);
        assertFalse(isValid, "JWT from invalid issuer must be rejected");
    }
}
