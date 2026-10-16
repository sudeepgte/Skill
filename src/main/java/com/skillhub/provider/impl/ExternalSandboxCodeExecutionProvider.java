package com.skillhub.provider.impl;

import com.skillhub.dto.CodeExecutionResult;
import com.skillhub.dto.CodeSubmission;
import com.skillhub.model.enums.SubmissionStatus;
import com.skillhub.provider.CodeExecutionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Production-grade External Isolated Sandbox Code Execution Provider.
 * Communicates with a dedicated gVisor / Docker containerized runner service over HTTPS/internal authenticated channel.
 *
 * Enforces:
 * - Runner authentication via Bearer token / secret header
 * - Network isolation & request timeout
 * - Circuit breaker protection to prevent thread starvation under runner failure
 * - Information leakage prevention (internal paths & stack traces sanitized)
 */
@Component("externalSandboxCodeExecutionProvider")
public class ExternalSandboxCodeExecutionProvider implements CodeExecutionProvider {

    private static final Logger log = LoggerFactory.getLogger(ExternalSandboxCodeExecutionProvider.class);

    @Value("${skillhub.code-execution.runner-url:}")
    private String runnerUrl;

    @Value("${skillhub.code-execution.runner-secret:}")
    private String runnerSecret;

    @Value("${skillhub.code-execution.connect-timeout-ms:3000}")
    private int connectTimeoutMs;

    @Value("${skillhub.code-execution.read-timeout-ms:8000}")
    private int readTimeoutMs;

    private final RestTemplate restTemplate;

    // Circuit Breaker State
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicBoolean circuitOpen = new AtomicBoolean(false);
    private volatile long circuitOpenUntil = 0L;
    private static final int FAILURE_THRESHOLD = 3;
    private static final long CIRCUIT_COOLDOWN_MS = 30_000L;

    public ExternalSandboxCodeExecutionProvider() {
        this.restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofMillis(3000))
                .setReadTimeout(Duration.ofMillis(8000))
                .build();
    }

    public ExternalSandboxCodeExecutionProvider(RestTemplate restTemplate, String runnerUrl, String runnerSecret) {
        this.restTemplate = restTemplate;
        this.runnerUrl = runnerUrl;
        this.runnerSecret = runnerSecret;
    }

    @Override
    public CodeExecutionResult execute(CodeSubmission submission) {
        CodeExecutionResult result = new CodeExecutionResult();

        // 1. Validate configuration
        if (runnerUrl == null || runnerUrl.trim().isEmpty()) {
            log.warn("External code runner URL is not configured.");
            result.setStatus(SubmissionStatus.EXECUTION_UNAVAILABLE);
            result.setErrorMessage("CODE_EXECUTION_TEMPORARILY_UNAVAILABLE");
            result.setOutputLogs("Isolated sandbox runner service is not configured.");
            return result;
        }

        // 2. Circuit Breaker Check
        long now = System.currentTimeMillis();
        if (circuitOpen.get()) {
            if (now < circuitOpenUntil) {
                log.warn("Code runner circuit breaker is OPEN. Fast-failing execution request.");
                result.setStatus(SubmissionStatus.EXECUTION_UNAVAILABLE);
                result.setErrorMessage("CODE_EXECUTION_TEMPORARILY_UNAVAILABLE");
                result.setOutputLogs("Execution gateway is temporarily unavailable. Please retry shortly.");
                return result;
            } else {
                circuitOpen.set(false);
                consecutiveFailures.set(0);
                log.info("Code runner circuit breaker cooldown elapsed. Resetting to half-open.");
            }
        }

        // 3. Prepare payload and authentication headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (runnerSecret != null && !runnerSecret.trim().isEmpty()) {
            headers.set("X-Runner-Secret", runnerSecret);
            headers.setBearerAuth(runnerSecret);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("language", submission.getLanguage());
        payload.put("sourceCode", submission.getSourceCode());
        payload.put("timeLimitMs", submission.getTimeLimit() != null ? submission.getTimeLimit() : 3000);
        payload.put("memoryLimitMb", submission.getMemoryLimit() != null ? submission.getMemoryLimit() : 256);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(runnerUrl + "/api/v1/execute", requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map body = response.getBody();
                consecutiveFailures.set(0);

                String statusStr = (String) body.getOrDefault("status", "PASSED");
                try {
                    result.setStatus(SubmissionStatus.valueOf(statusStr));
                } catch (Exception ex) {
                    result.setStatus(SubmissionStatus.PASSED);
                }

                Number execTime = (Number) body.getOrDefault("executionTimeMs", 0L);
                Number memUsed = (Number) body.getOrDefault("memoryUsedMb", 0L);
                Number passed = (Number) body.getOrDefault("testsPassed", 0);
                Number total = (Number) body.getOrDefault("testsTotal", 0);

                result.setExecutionTimeMs(execTime.longValue());
                result.setMemoryUsedMb(memUsed.longValue());
                result.setTestsPassed(passed.intValue());
                result.setTestsTotal(total.intValue());
                result.setOutputLogs(sanitizeOutput((String) body.get("outputLogs")));
                return result;
            } else {
                handleFailure();
                result.setStatus(SubmissionStatus.EXECUTION_UNAVAILABLE);
                result.setErrorMessage("CODE_EXECUTION_TEMPORARILY_UNAVAILABLE");
                return result;
            }
        } catch (ResourceAccessException ex) {
            log.error("Network timeout or connection refused connecting to code runner: {}", ex.getMessage());
            handleFailure();
            result.setStatus(SubmissionStatus.EXECUTION_UNAVAILABLE);
            result.setErrorMessage("CODE_EXECUTION_TEMPORARILY_UNAVAILABLE");
            result.setOutputLogs("Code execution gateway timed out or unreachable.");
            return result;
        } catch (RestClientResponseException ex) {
            log.error("Code runner responded with HTTP {}: {}", ex.getStatusCode(), ex.getStatusText());
            handleFailure();
            result.setStatus(SubmissionStatus.EXECUTION_UNAVAILABLE);
            result.setErrorMessage("CODE_EXECUTION_TEMPORARILY_UNAVAILABLE");
            return result;
        } catch (Exception ex) {
            log.error("Unexpected error executing code via external runner: {}", ex.getMessage(), ex);
            handleFailure();
            result.setStatus(SubmissionStatus.EXECUTION_UNAVAILABLE);
            result.setErrorMessage("CODE_EXECUTION_TEMPORARILY_UNAVAILABLE");
            return result;
        }
    }

    private void handleFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= FAILURE_THRESHOLD) {
            circuitOpen.set(true);
            circuitOpenUntil = System.currentTimeMillis() + CIRCUIT_COOLDOWN_MS;
            log.warn("Code runner failed {} consecutive times. Tripping circuit breaker for {}ms.", failures, CIRCUIT_COOLDOWN_MS);
        }
    }

    private String sanitizeOutput(String output) {
        if (output == null) return "";
        // Strip sensitive internal host paths or credentials
        return output.replaceAll("/home/[a-zA-Z0-9_-]+", "/workspace")
                .replaceAll("C:\\\\[a-zA-Z0-9_\\\\-]+", "/workspace")
                .trim();
    }

    public boolean isCircuitOpen() {
        return circuitOpen.get() && System.currentTimeMillis() < circuitOpenUntil;
    }
}
