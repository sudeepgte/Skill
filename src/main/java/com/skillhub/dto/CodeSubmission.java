package com.skillhub.dto;
public class CodeSubmission {
    private String language;
    private String sourceCode;
    private Integer timeLimit;
    private Integer memoryLimit;
    
    public String getLanguage() { return language; } public void setLanguage(String language) { this.language = language; }
    public String getSourceCode() { return sourceCode; } public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    public Integer getTimeLimit() { return timeLimit; } public void setTimeLimit(Integer timeLimit) { this.timeLimit = timeLimit; }
    public Integer getMemoryLimit() { return memoryLimit; } public void setMemoryLimit(Integer memoryLimit) { this.memoryLimit = memoryLimit; }
}
