package com.skillhub.controller;

import com.skillhub.model.User;
import com.skillhub.repository.NotificationRepository;
import com.skillhub.repository.UserRepository;
import com.skillhub.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.datasource.url=jdbc:h2:mem:contacttestdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
@DisplayName("Contact Form and Email Dispatch Integration Test")
public class ContactFormEmailIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @MockBean(name = "initData")
    private CommandLineRunner initData;

    @MockBean
    private EmailService emailService;

    @BeforeEach
    public void setUp() {
        notificationRepository.deleteAll();
        userRepository.deleteAll();

        // Seed admin user for in-app notifications
        User admin = new User();
        admin.setEmail("admin@skillhub.com");
        admin.setName("Admin User");
        admin.setPassword("admin123");
        admin.setRole("ADMIN");
        admin.setApproved(true);
        userRepository.save(admin);
    }

    @Test
    @DisplayName("Submit contact form from homepage redirects with success flag and sends confirmation email")
    public void testSubmitContactFormFromHomepage() throws Exception {
        mockMvc.perform(post("/submit-contact-form")
                .header("Referer", "http://localhost:8086/#contact")
                .param("name", "John Doe")
                .param("email", "john.doe@example.com")
                .param("message", "I want to inquire about course pricing."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?success=contact#contact"))
                .andExpect(flash().attributeExists("successMessage"));

        // Wait briefly for asynchronous email dispatch
        Thread.sleep(500);

        verify(emailService, atLeastOnce()).sendSimpleMessage(
                eq("john.doe@example.com"),
                contains("Thank you for contacting us"),
                contains("I want to inquire about course pricing")
        );
    }

    @Test
    @DisplayName("Submit contact form from /contact page redirects with success flag and sends confirmation email")
    public void testSubmitContactFormFromContactPage() throws Exception {
        mockMvc.perform(post("/submit-contact-form")
                .header("Referer", "http://localhost:8086/contact")
                .param("name", "Jane Smith")
                .param("email", "jane.smith@example.com")
                .param("subject", "Corporate Training")
                .param("phone", "9876543210")
                .param("message", "We want corporate training for our team of 20."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contact?success=true"))
                .andExpect(flash().attributeExists("successMessage"));

        Thread.sleep(500);

        verify(emailService, atLeastOnce()).sendSimpleMessage(
                eq("jane.smith@example.com"),
                contains("Thank you for contacting us"),
                contains("We want corporate training")
        );
    }

    @Test
    @DisplayName("AJAX submission of contact form returns JSON and sends confirmation email")
    public void testAjaxSubmitContactForm() throws Exception {
        mockMvc.perform(post("/submit-contact-form")
                .header("X-Requested-With", "XMLHttpRequest")
                .accept(MediaType.APPLICATION_JSON)
                .param("name", "Alex Developer")
                .param("email", "alex@example.com")
                .param("message", "Looking forward to learning full stack development."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(containsString("alex@example.com")));

        Thread.sleep(500);

        verify(emailService, atLeastOnce()).sendSimpleMessage(
                eq("alex@example.com"),
                contains("Thank you for contacting us"),
                contains("Looking forward to learning")
        );
    }
}
