package com.skillhub.config;

import com.skillhub.provider.CodeExecutionProvider;
import com.skillhub.provider.impl.ExternalSandboxCodeExecutionProvider;
import com.skillhub.provider.impl.LocalIsolatedSandboxCodeExecutionProvider;
import com.skillhub.provider.impl.MockCodeExecutionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class CodeExecutionConfig {

    private static final Logger log = LoggerFactory.getLogger(CodeExecutionConfig.class);

    @Value("${skillhub.code-execution.mode:mock}")
    private String executionMode;

    @Bean
    @Primary
    public CodeExecutionProvider primaryCodeExecutionProvider(
            ExternalSandboxCodeExecutionProvider externalProvider,
            LocalIsolatedSandboxCodeExecutionProvider localIsolatedProvider,
            MockCodeExecutionProvider mockProvider) {

        if ("production".equalsIgnoreCase(executionMode)) {
            log.info("Active CodeExecutionProvider: ExternalSandboxCodeExecutionProvider (Isolated container gateway)");
            return externalProvider;
        } else if ("isolated".equalsIgnoreCase(executionMode)) {
            log.info("Active CodeExecutionProvider: LocalIsolatedSandboxCodeExecutionProvider (Isolated local sandbox)");
            return localIsolatedProvider;
        } else {
            log.info("Active CodeExecutionProvider: MockCodeExecutionProvider (Simulation/Test mode)");
            return mockProvider;
        }
    }
}
