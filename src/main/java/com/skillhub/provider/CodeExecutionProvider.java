package com.skillhub.provider;
import com.skillhub.dto.CodeSubmission;
import com.skillhub.dto.CodeExecutionResult;
public interface CodeExecutionProvider {
    CodeExecutionResult execute(CodeSubmission submission);
}
