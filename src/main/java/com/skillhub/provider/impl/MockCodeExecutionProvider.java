package com.skillhub.provider.impl;

import com.skillhub.dto.CodeExecutionResult;
import com.skillhub.dto.CodeSubmission;
import com.skillhub.model.enums.SubmissionStatus;
import com.skillhub.provider.CodeExecutionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Code Execution Provider Implementation.
 * 
 * CRITICAL PRODUCTION SECURITY NOTICE:
 * Untrusted candidate source code MUST NEVER be executed directly within the Spring Boot JVM,
 * application host, or production database network.
 * 
 * In production deployments, this provider requires a dedicated, containerized code execution runner
 * with gVisor/nsjail/Docker container sandbox, memory limits, CPU quotas, and strict network isolation.
 */
@Service
public class MockCodeExecutionProvider implements CodeExecutionProvider {

    private static final Logger log = LoggerFactory.getLogger(MockCodeExecutionProvider.class);

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @Value("${skillhub.code-execution.mode:mock}")
    private String executionMode;

    @Value("${skillhub.code-execution.runner-url:}")
    private String runnerUrl;

    @jakarta.annotation.PostConstruct
    public void validateNotProduction() {
        if ((executionMode == null || "mock".equalsIgnoreCase(executionMode)) && ("production".equalsIgnoreCase(activeProfile) || "prod".equalsIgnoreCase(activeProfile))) {
            throw new IllegalStateException("CRITICAL SECURITY VIOLATION: MockCodeExecutionProvider cannot be activated in production profile!");
        }
        if ("production".equalsIgnoreCase(executionMode)) {
            log.warn("MockCodeExecutionProvider loaded under production mode setting: Live coding requests will be rejected as EXECUTION_UNAVAILABLE.");
        }
    }

    @Override
    public CodeExecutionResult execute(CodeSubmission submission) {
        CodeExecutionResult result = new CodeExecutionResult();

        // Enforce safety: if configured as production without an isolated runner endpoint
        if ("production".equalsIgnoreCase(executionMode) && (runnerUrl == null || runnerUrl.trim().isEmpty())) {
            log.warn("Production code execution attempted but isolated sandbox runner is not configured.");
            result.setStatus(SubmissionStatus.EXECUTION_UNAVAILABLE);
            result.setExecutionTimeMs(0L);
            result.setMemoryUsedMb(0L);
            result.setTestsPassed(0);
            result.setTestsTotal(1);
            result.setOutputLogs("LIVE CODING EXECUTION: NOT READY FOR PRODUCTION. An isolated sandbox runner environment is required for live code execution.");
            return result;
        }

        // Staging/Mock/Development Simulation Mode
        log.info("Executing code in sandbox simulation mode for language: {}", submission.getLanguage());
        result.setStatus(SubmissionStatus.PASSED);
        result.setExecutionTimeMs(42L);
        result.setMemoryUsedMb(14L);
        result.setTestsPassed(10);
        result.setTestsTotal(10);
        result.setOutputLogs("[SIMULATED SANDBOX] All 10 verification test cases passed successfully within time limit.");
        return result;
    }
}
