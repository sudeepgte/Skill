package com.skillhub.security;

import com.skillhub.dto.LoginRequest;
import com.skillhub.model.User;
import com.skillhub.repository.UserRepository;
import com.skillhub.service.FileStorageService;
import com.skillhub.service.RateLimitingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.datasource.url=jdbc:h2:mem:sec2testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
public class SpringBootSecurityHardeningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private RateLimitingService rateLimitingService;

    @MockBean(name = "initData")
    private CommandLineRunner initData;

    @MockBean(name = "seedProfessionalExamAttempt")
    private CommandLineRunner seedProfessionalExamAttempt;

    @Autowired
    private ObjectMapper objectMapper;

    // =========================================================================
    // 1. CSRF ENFORCEMENT & PROTECTION TESTS
    // =========================================================================

    @Test
    @WithMockUser(username = "student@test.com", roles = {"STUDENT"})
    @DisplayName("CSRF: State-changing POST /course/wishlist/add without CSRF token is rejected (403)")
    void testWishlistAddWithoutCsrfRejected() throws Exception {
        mockMvc.perform(post("/course/wishlist/add")
                        .param("courseId", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "student@test.com", roles = {"STUDENT"})
    @DisplayName("CSRF: State-changing POST /payment/create-order without CSRF token is rejected (403)")
    void testPaymentCreateOrderWithoutCsrfRejected() throws Exception {
        mockMvc.perform(post("/payment/create-order")
                        .param("courseId", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "student@test.com", roles = {"STUDENT"})
    @DisplayName("CSRF: State-changing POST /payment/verify without CSRF token is rejected (403)")
    void testPaymentVerifyWithoutCsrfRejected() throws Exception {
        mockMvc.perform(post("/payment/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"razorpay_order_id\":\"order_123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "instructor@test.com", roles = {"INSTRUCTOR"})
    @DisplayName("CSRF: State-changing POST /vendor/courses/1/questions/clear-all without CSRF token is rejected (403)")
    void testVendorQuestionsClearWithoutCsrfRejected() throws Exception {
        mockMvc.perform(post("/vendor/courses/1/questions/clear-all"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = {"STUDENT"})
    @DisplayName("CSRF: POST /logout without CSRF token is rejected (403)")
    void testLogoutWithoutCsrfRejected() throws Exception {
        mockMvc.perform(post("/logout"))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 2. CORS RESTRICTIONS AND ORIGIN VALIDATION
    // =========================================================================

    @Test
    @DisplayName("CORS: Inappropriate cross-origin request from untrusted origin is denied")
    void testUntrustedOriginDeniedCors() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header("Origin", "https://malicious-attacker.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@test.com\",\"password\":\"TestPassword123!\"}"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("CORS: Legitimate cross-origin request from localhost origin is permitted")
    void testTrustedOriginAllowedCors() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header("Origin", "http://localhost:8086")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@test.com\",\"password\":\"TestPassword123!\"}"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8086"));
    }

    // =========================================================================
    // 3. SECURITY HEADERS VALIDATION
    // =========================================================================

    @Test
    @DisplayName("Security Headers: Responses include strict CSP, frame-options, and MIME-sniffing protection")
    void testSecurityHeadersEnforced() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("connect-src 'self' ws: wss: https://cdn.jsdelivr.net https://cdnjs.cloudflare.com")))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("font-src 'self' https://fonts.gstatic.com https://cdnjs.cloudflare.com https://cdn.jsdelivr.net data:")))
                .andExpect(header().string("Permissions-Policy", "camera=(self), microphone=(self), geolocation=()"));
    }

    // =========================================================================
    // 4. OPEN REDIRECT DEFENSE
    // =========================================================================

    @Test
    @WithMockUser(username = "student@test.com", roles = {"STUDENT"})
    @DisplayName("Open Redirect: Malicious external referer header is sanitized to local default")
    void testOpenRedirectSanitizedToLocalDefault() throws Exception {
        mockMvc.perform(post("/notifications/mark-read")
                        .with(csrf())
                        .param("id", "1")
                        .header("Referer", "https://evil-phishing-attacker.com/login"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/notifications"));
    }

    @Test
    @WithMockUser(username = "student@test.com", roles = {"STUDENT"})
    @DisplayName("Open Redirect: Protocol-relative referer (//evil.com) is sanitized to local default")
    void testProtocolRelativeRefererSanitized() throws Exception {
        mockMvc.perform(post("/notifications/mark-read")
                        .with(csrf())
                        .param("id", "1")
                        .header("Referer", "//evil-phishing-attacker.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/notifications"));
    }

    // =========================================================================
    // 5. PATH TRAVERSAL DEFENSE
    // =========================================================================

    @Test
    @DisplayName("Path Traversal: Attempt to delete file outside upload directory throws SecurityException")
    void testPathTraversalDeleteBlocked() {
        assertThrows(SecurityException.class, () -> {
            fileStorageService.delete("/uploads/../../etc/passwd");
        });
    }

    @Test
    @DisplayName("Path Traversal: Malicious filename with traversal sequences is sanitized on store")
    void testPathTraversalStoreSanitized() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../../../../windows/system32/test.txt",
                "text/plain",
                "echo hello".getBytes()
        );
        String savedPath = fileStorageService.store(file);
        assertTrue(savedPath.startsWith("/uploads/"), "Saved path must be constrained inside /uploads/");
        assertTrue(!savedPath.contains(".."), "Saved path must not contain traversal sequences");
    }

    // =========================================================================
    // 6. INFORMATION DISCLOSURE PREVENTION
    // =========================================================================

    @Test
    @DisplayName("Info Disclosure: /api/health does not reveal internal server absolute filesystem paths")
    void testHealthEndpointDoesNotLeakAbsoluteFileSystemPath() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("C:\\Users\\"))))
                .andExpect(content().string(not(containsString("/home/"))));
    }

    // =========================================================================
    // 7. DTO VALIDATION AND RATE LIMITING
    // =========================================================================

    @Test
    @DisplayName("DTO Validation: Blank email or short password in /api/auth/login returns 400 Bad Request")
    void testLoginRequestDtoValidationFailsOnInvalidInput() throws Exception {
        LoginRequest badRequest = new LoginRequest();
        badRequest.setEmail("not-an-email");
        badRequest.setPassword("short");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Rate Limiting: Exceeding request threshold triggers 429 Too Many Requests")
    void testApiLoginRateLimitingTriggered() throws Exception {
        String testIp = "192.168.1.100";
        LoginRequest req = new LoginRequest();
        req.setEmail("valid@test.com");
        req.setPassword("ValidPassword123!");

        // Consume all tokens in rate limit bucket
        for (int i = 0; i < 10; i++) {
            rateLimitingService.allowRequest("api-login:" + testIp, 10, 60);
        }

        mockMvc.perform(post("/api/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(testIp);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Too Many Requests"));
    }
}
