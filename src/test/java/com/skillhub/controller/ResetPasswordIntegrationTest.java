package com.skillhub.controller;

import com.skillhub.model.PasswordResetToken;
import com.skillhub.model.User;
import com.skillhub.repository.PasswordResetTokenRepository;
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
import com.skillhub.service.EmailService;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.datasource.url=jdbc:h2:mem:resetpwdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
@DisplayName("Reset Password Token and OTP Handling Integration Test")
public class ResetPasswordIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @MockBean(name = "initData")
    private CommandLineRunner initData;

    @MockBean
    private EmailService emailService;

    @BeforeEach
    public void setUp() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("BUG-010: Navigate to /reset-password?token=invalid_dummy_token_123 without valid token should render login-page with error and not crash with 500")
    public void testInvalidDummyTokenRendersLoginPageWithError() throws Exception {
        mockMvc.perform(get("/reset-password").param("token", "invalid_dummy_token_123"))
                .andExpect(status().isOk())
                .andExpect(view().name("login-page"))
                .andExpect(model().attributeExists("error"))
                .andExpect(model().attribute("error", "Invalid password reset token."))
                .andExpect(content().string(containsString("Invalid password reset token.")));
    }

    @Test
    @DisplayName("Navigate to /reset-password without token param should render login-page with missing token error")
    public void testMissingTokenRendersLoginPageWithError() throws Exception {
        mockMvc.perform(get("/reset-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("login-page"))
                .andExpect(model().attributeExists("error"))
                .andExpect(model().attribute("error", "Password reset token is missing."))
                .andExpect(content().string(containsString("Password reset token is missing.")));
    }

    @Test
    @DisplayName("Expired token should render login-page with expired token error")
    public void testExpiredTokenRendersLoginPageWithError() throws Exception {
        User user = new User();
        user.setEmail("testuser@example.com");
        user.setName("Test User");
        user.setPassword("hashedpassword");
        user.setRole("STUDENT");
        user = userRepository.save(user);

        PasswordResetToken expiredToken = new PasswordResetToken("expired_token_abc", user, LocalDateTime.now().minusMinutes(5));
        tokenRepository.save(expiredToken);

        mockMvc.perform(get("/reset-password").param("token", "expired_token_abc"))
                .andExpect(status().isOk())
                .andExpect(view().name("login-page"))
                .andExpect(model().attributeExists("error"))
                .andExpect(model().attribute("error", "Token has expired. Please request a new one."))
                .andExpect(content().string(containsString("Token has expired. Please request a new one.")));
    }

    @Test
    @DisplayName("BUG-015: forgot-password must not redirect with token in URL; email with OTP is sent instead")
    public void testForgotPasswordDoesNotExposeTokenInRedirect() throws Exception {
        User user = new User();
        user.setEmail("admin@skillhub.com");
        user.setName("Admin");
        user.setPassword("hashedpassword");
        user.setRole("ADMIN");
        userRepository.save(user);

        mockMvc.perform(post("/forgot-password").param("email", "admin@skillhub.com").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("forgot_password"))
                .andExpect(model().attributeExists("message"))
                .andExpect(model().attribute("otpSent", true));

        verify(emailService).sendSimpleMessage(eq("admin@skillhub.com"), anyString(), contains("/reset-password?token="));
    }

    @Test
    @DisplayName("Valid token should render reset_password page successfully")
    public void testValidTokenRendersResetPasswordForm() throws Exception {
        User user = new User();
        user.setEmail("validuser@example.com");
        user.setName("Valid User");
        user.setPassword("hashedpassword");
        user.setRole("STUDENT");
        user = userRepository.save(user);

        PasswordResetToken validToken = new PasswordResetToken("valid_token_xyz", user, LocalDateTime.now().plusMinutes(15));
        tokenRepository.save(validToken);

        mockMvc.perform(get("/reset-password").param("token", "valid_token_xyz"))
                .andExpect(status().isOk())
                .andExpect(view().name("reset_password"))
                .andExpect(model().attributeExists("passwordDto"));
    }

    @Test
    @DisplayName("OTP Verification: Correct OTP redirects to reset-password with token")
    public void testVerifyOtpSuccessRedirectsToResetPassword() throws Exception {
        User user = new User();
        user.setEmail("otpuser@example.com");
        user.setName("OTP User");
        user.setPassword("hashedpassword");
        user.setRole("STUDENT");
        user = userRepository.save(user);

        PasswordResetToken token = new PasswordResetToken("otp_token_123", "654321", user, LocalDateTime.now().plusMinutes(15));
        tokenRepository.save(token);

        mockMvc.perform(post("/verify-otp")
                        .param("email", "otpuser@example.com")
                        .param("otp", "654321")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reset-password?token=otp_token_123"));
    }

    @Test
    @DisplayName("OTP Verification: Incorrect OTP stays on forgot_password with error")
    public void testVerifyOtpIncorrectRendersForgotPageWithError() throws Exception {
        User user = new User();
        user.setEmail("otpuser@example.com");
        user.setName("OTP User");
        user.setPassword("hashedpassword");
        user.setRole("STUDENT");
        user = userRepository.save(user);

        PasswordResetToken token = new PasswordResetToken("otp_token_123", "654321", user, LocalDateTime.now().plusMinutes(15));
        tokenRepository.save(token);

        mockMvc.perform(post("/verify-otp")
                        .param("email", "otpuser@example.com")
                        .param("otp", "000000")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("forgot_password"))
                .andExpect(model().attributeExists("error"))
                .andExpect(model().attribute("error", "Invalid OTP. Please check your email and try again."));
    }

    @Test
    @DisplayName("OTP Verification: Expired OTP stays on forgot_password with expired error")
    public void testVerifyOtpExpiredRendersForgotPageWithError() throws Exception {
        User user = new User();
        user.setEmail("otpuser@example.com");
        user.setName("OTP User");
        user.setPassword("hashedpassword");
        user.setRole("STUDENT");
        user = userRepository.save(user);

        PasswordResetToken token = new PasswordResetToken("otp_token_123", "654321", user, LocalDateTime.now().minusMinutes(5));
        tokenRepository.save(token);

        mockMvc.perform(post("/verify-otp")
                        .param("email", "otpuser@example.com")
                        .param("otp", "654321")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("forgot_password"))
                .andExpect(model().attributeExists("error"))
                .andExpect(model().attribute("error", "OTP has expired. Please request a new one."));
    }
}
