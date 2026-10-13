package com.skillhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CodingExecutionRequest {
    @NotNull(message = "Question number is required")
    private Integer questionNumber;

    @NotBlank(message = "Language is required")
    @Size(max = 50, message = "Language identifier exceeds maximum length")
    private String language;

    @NotBlank(message = "Source code cannot be empty")
    @Size(max = 65536, message = "Source code exceeds maximum permitted size of 64KB")
    private String sourceCode;

    public CodingExecutionRequest() {}

    public CodingExecutionRequest(Integer questionNumber, String language, String sourceCode) {
        this.questionNumber = questionNumber;
        this.language = language;
        this.sourceCode = sourceCode;
    }

    public Integer getQuestionNumber() { return questionNumber; }
    public void setQuestionNumber(Integer questionNumber) { this.questionNumber = questionNumber; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
}
