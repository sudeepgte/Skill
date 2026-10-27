package com.skillhub.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = com.skillhub.SkillHubApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class ProductionOperationalReadinessTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Readiness: Liveness probe endpoint returns 200 UP")
    void testLivenessEndpoint() throws Exception {
        mockMvc.perform(get("/api/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Readiness: Readiness probe endpoint returns 200 UP when DB is connected")
    void testReadinessEndpoint() throws Exception {
        mockMvc.perform(get("/api/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("UP"));
    }

    @Test
    @DisplayName("Readiness: Correlation ID filter generates and attaches X-Request-ID header")
    void testCorrelationIdHeaderGenerated() throws Exception {
        mockMvc.perform(get("/api/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"));
    }

    @Test
    @DisplayName("Readiness: Correlation ID filter propagates client-supplied valid X-Request-ID")
    void testCorrelationIdHeaderPropagated() throws Exception {
        String customId = "trace-client-12345-abcde";
        mockMvc.perform(get("/api/health/liveness").header("X-Request-ID", customId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", customId));
    }

    @Test
    @DisplayName("Readiness: Health check endpoint includes components and metrics")
    void testComprehensiveHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.components.database").exists())
                .andExpect(jsonPath("$.components.storage").exists());
    }

    @Test
    @DisplayName("Config: Verify application-prod.properties hardening invariants")
    void testProductionPropertiesHardening() throws Exception {
        ClassPathResource resource = new ClassPathResource("application-prod.properties");
        assertTrue(resource.exists(), "application-prod.properties must exist");

        Properties props = PropertiesLoaderUtils.loadProperties(resource);

        // 1. Database schema safety
        assertEquals("validate", props.getProperty("spring.jpa.hibernate.ddl-auto"),
                "Production must use ddl-auto=validate to prevent accidental schema mutation");
        assertEquals("false", props.getProperty("spring.jpa.show-sql"),
                "Production show-sql must be disabled");

        // 2. Cookie security
        assertEquals("true", props.getProperty("server.servlet.session.cookie.secure"),
                "Production session cookies must have Secure attribute");
        assertEquals("true", props.getProperty("server.servlet.session.cookie.http-only"),
                "Production session cookies must have HttpOnly attribute");

        // 3. Graceful shutdown
        assertEquals("graceful", props.getProperty("server.shutdown"),
                "Production server must have graceful shutdown enabled");

        // 4. Secret externalization checks (No hardcoded credentials)
        assertEquals("${DB_URL}", props.getProperty("spring.datasource.url"));
        assertEquals("${DB_USERNAME}", props.getProperty("spring.datasource.username"));
        assertEquals("${DB_PASSWORD}", props.getProperty("spring.datasource.password"));
        assertEquals("${RAZORPAY_KEY_ID}", props.getProperty("razorpay.key.id"));
        assertEquals("${RAZORPAY_KEY_SECRET}", props.getProperty("razorpay.key.secret"));
        assertEquals("${JWT_SECRET}", props.getProperty("skillhub.app.jwtSecret"));
    }

    @Test
    @DisplayName("Deployment: Verify Dockerfile, docker-compose, and Nginx reverse proxy configs exist")
    void testDeploymentArtifactsExist() {
        assertTrue(new File("Dockerfile").exists(), "Dockerfile must exist");
        assertTrue(new File("docker-compose.prod.yml").exists(), "docker-compose.prod.yml must exist");
        assertTrue(new File(".env.example").exists(), ".env.example must exist");
        assertTrue(new File("nginx/skillhub.conf").exists(), "nginx/skillhub.conf must exist");
        assertTrue(new File("scripts/backup_production.sh").exists(), "backup script must exist");
        assertTrue(new File("scripts/restore_production.sh").exists(), "restore script must exist");
    }
}
