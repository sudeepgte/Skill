package com.skillhub.dto;
import java.time.LocalDateTime;
public class ExamAttemptOverviewResponse {
    private Long attemptId;
    private String examTitle;
    private int totalQuestions;
    private int answeredQuestions;
    private int unansweredQuestions;
    private int markedForReview;
    private long remainingSeconds;
    private LocalDateTime startedAt;
    private String status;
    
    // Getters and setters
    public Long getAttemptId() { return attemptId; }
    public void setAttemptId(Long attemptId) { this.attemptId = attemptId; }
    public String getExamTitle() { return examTitle; }
    public void setExamTitle(String examTitle) { this.examTitle = examTitle; }
    public int getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(int totalQuestions) { this.totalQuestions = totalQuestions; }
    public int getAnsweredQuestions() { return answeredQuestions; }
    public void setAnsweredQuestions(int answeredQuestions) { this.answeredQuestions = answeredQuestions; }
    public int getUnansweredQuestions() { return unansweredQuestions; }
    public void setUnansweredQuestions(int unansweredQuestions) { this.unansweredQuestions = unansweredQuestions; }
    public int getMarkedForReview() { return markedForReview; }
    public void setMarkedForReview(int markedForReview) { this.markedForReview = markedForReview; }
    public long getRemainingSeconds() { return remainingSeconds; }
    public void setRemainingSeconds(long remainingSeconds) { this.remainingSeconds = remainingSeconds; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
