package com.skillhub.controller;

import com.skillhub.model.User;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.datasource.url=jdbc:h2:mem:adminregtestdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
@DisplayName("Admin Registration Security Restriction Test (BUG-014)")
public class AdminRegistrationSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @MockBean(name = "initData")
    private CommandLineRunner initData;

    @BeforeEach
    public void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("BUG-014: Unauthenticated visitor accessing /register/admin is blocked (401 Unauthorized)")
    public void testUnauthenticatedAccessToRegisterAdminBlocked() throws Exception {
        mockMvc.perform(get("/register/admin"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("BUG-014: Unauthenticated POST /saveUser with role=ADMIN is blocked from creating an admin")
    public void testUnauthenticatedAdminCreationBlocked() throws Exception {
        mockMvc.perform(post("/saveUser")
                .param("name", "Malicious Attacker")
                .param("email", "hacker@skillhub.com")
                .param("password", "HackerPass123!")
                .param("role", "ADMIN")
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        User created = userRepository.findByEmail("hacker@skillhub.com");
        assertNull(created, "Unauthenticated user should NOT be able to create an admin account");
    }

    @Test
    @DisplayName("Public student registration via /saveUser continues to work properly")
    public void testPublicStudentRegistrationAllowed() throws Exception {
        mockMvc.perform(post("/saveUser")
                .param("name", "Legitimate Student")
                .param("email", "student@skillhub.com")
                .param("password", "StudentPass123!")
                .param("role", "STUDENT")
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?success"));

        User created = userRepository.findByEmail("student@skillhub.com");
        assertNotNull(created);
        assertEquals("STUDENT", created.getRole());
    }

    @Test
    @WithMockUser(username = "admin@skillhub.com", authorities = {"ADMIN", "ROLE_ADMIN"})
    @DisplayName("Authenticated admin CAN access /register/admin")
    public void testAuthenticatedAdminCanAccessRegisterAdmin() throws Exception {
        mockMvc.perform(get("/register/admin"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attribute("isAdmin", true));
    }
}
