package com.skillhub.controller;

import com.skillhub.provider.CodeExecutionProvider;
import com.skillhub.provider.impl.ExternalSandboxCodeExecutionProvider;
import com.skillhub.service.ExamObservabilityService;
import com.skillhub.service.SqlEvaluationSandboxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthCheckRestController {

    @Autowired(required = false)
    private DataSource dataSource;

    @Autowired
    private CodeExecutionProvider codeExecutionProvider;

    @Autowired
    private SqlEvaluationSandboxService sqlSandboxService;

    @Autowired
    private ExamObservabilityService observabilityService;

    @Value("${skillhub.code-execution.mode:mock}")
    private String codeExecutionMode;

    @Value("${skillhub.code-execution.runner-url:}")
    private String runnerUrl;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @GetMapping
    public ResponseEntity<Map<String, Object>> checkHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("timestamp", LocalDateTime.now().toString());
        health.put("status", "UP");

        Map<String, Object> components = new HashMap<>();

        // 1. Database Health Check
        try {
            if (dataSource != null) {
                try (Connection conn = dataSource.getConnection()) {
                    components.put("database", Map.of(
                            "status", "UP",
                            "databaseProductName", conn.getMetaData().getDatabaseProductName()
                    ));
                }
            } else {
                components.put("database", Map.of("status", "DOWN", "error", "No DataSource configured"));
            }
        } catch (Exception ex) {
            components.put("database", Map.of("status", "DOWN", "error", ex.getMessage()));
            health.put("status", "DEGRADED");
        }

        // 2. Code Runner Health Check (Distinguishable from core application health)
        boolean isProductionRunnerConfigured = runnerUrl != null && !runnerUrl.trim().isEmpty();
        if ("production".equalsIgnoreCase(codeExecutionMode) && !isProductionRunnerConfigured) {
            components.put("codingRunner", Map.of(
                    "status", "DEGRADED",
                    "mode", codeExecutionMode,
                    "message", "External sandbox runner URL not configured."
            ));
            health.put("status", "DEGRADED");
        } else if (codeExecutionProvider instanceof ExternalSandboxCodeExecutionProvider externalProvider && externalProvider.isCircuitOpen()) {
            components.put("codingRunner", Map.of(
                    "status", "DEGRADED",
                    "mode", codeExecutionMode,
                    "circuitBreaker", "OPEN"
            ));
            health.put("status", "DEGRADED");
        } else {
            components.put("codingRunner", Map.of(
                    "status", "UP",
                    "mode", codeExecutionMode,
                    "isolatedSandbox", isProductionRunnerConfigured || "isolated".equalsIgnoreCase(codeExecutionMode)
            ));
        }

        // 3. SQL Sandbox Evaluation Health Check
        try {
            var sqlTest = sqlSandboxService.evaluate("SELECT 1 AS ok", "SELECT 1 AS ok", null);
            components.put("sqlEvaluation", Map.of(
                    "status", sqlTest.isPassed() ? "UP" : "DEGRADED",
                    "sandboxType", "EPHEMERAL_IN_MEMORY_H2"
            ));
        } catch (Exception ex) {
            components.put("sqlEvaluation", Map.of("status", "DEGRADED", "error", ex.getMessage()));
        }

        // 4. Storage Health Check
        File uploadDirFile = new File(uploadDir);
        components.put("storage", Map.of(
                "status", uploadDirFile.canWrite() ? "UP" : "READ_ONLY",
                "uploadDirectory", uploadDirFile.getAbsolutePath()
        ));

        health.put("components", components);
        health.put("metrics", observabilityService.getAllMetrics());
        return ResponseEntity.ok(health);
    }
}
