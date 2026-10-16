package com.skillhub.dto;
import com.skillhub.model.enums.SubmissionStatus;
public class CodeExecutionResult {
    private SubmissionStatus status;
    private Long executionTimeMs;
    private Long memoryUsedMb;
    private Integer testsPassed;
    private Integer testsTotal;
    private String outputLogs;
    private String errorMessage;
    
    public SubmissionStatus getStatus() { return status; } public void setStatus(SubmissionStatus status) { this.status = status; }
    public Long getExecutionTimeMs() { return executionTimeMs; } public void setExecutionTimeMs(Long executionTimeMs) { this.executionTimeMs = executionTimeMs; }
    public Long getMemoryUsedMb() { return memoryUsedMb; } public void setMemoryUsedMb(Long memoryUsedMb) { this.memoryUsedMb = memoryUsedMb; }
    public Integer getTestsPassed() { return testsPassed; } public void setTestsPassed(Integer testsPassed) { this.testsPassed = testsPassed; }
    public Integer getTestsTotal() { return testsTotal; } public void setTestsTotal(Integer testsTotal) { this.testsTotal = testsTotal; }
    public String getOutputLogs() { return outputLogs; } public void setOutputLogs(String outputLogs) { this.outputLogs = outputLogs; }
    public String getErrorMessage() { return errorMessage; } public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
