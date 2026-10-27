package com.skillhub.model;

import com.skillhub.model.enums.AttemptStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "professional_exam_attempts", indexes = {
    @Index(name = "idx_prof_att_user", columnList = "user_id"),
    @Index(name = "idx_prof_att_app", columnList = "application_id"),
    @Index(name = "idx_prof_att_ver", columnList = "exam_version_id"),
    @Index(name = "idx_prof_att_status", columnList = "status"),
    @Index(name = "idx_prof_att_dates", columnList = "startedAt, submittedAt")
})
public class ProfessionalExamAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private ProfessionalExamApplication application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_version_id", nullable = false)
    private ProfessionalExamVersion examVersion;

    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttemptStatus status;

    private Long currentQuestion;

    private int totalMarks;
    private int obtainedMarks;

    private int correctAnswers;
    private int wrongAnswers;
    private int unansweredAnswers;

    private int riskScore;

    @Column(name = "recording_url", length = 1000)
    private String recordingUrl;

    @Column(name = "proctoring_warnings")
    private Integer proctoringWarnings = 0;

    @Column(name = "recording_duration_seconds")
    private Long recordingDurationSeconds;

    @Version
    private Long version;

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ProfessionalExamApplication getApplication() { return application; }
    public void setApplication(ProfessionalExamApplication application) { this.application = application; }
    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }
    public ProfessionalExamVersion getExamVersion() { return examVersion; }
    public void setExamVersion(ProfessionalExamVersion examVersion) { this.examVersion = examVersion; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public AttemptStatus getStatus() { return status; }
    public void setStatus(AttemptStatus status) { this.status = status; }
    public Long getCurrentQuestion() { return currentQuestion; }
    public void setCurrentQuestion(Long currentQuestion) { this.currentQuestion = currentQuestion; }
    public int getTotalMarks() { return totalMarks; }
    public void setTotalMarks(int totalMarks) { this.totalMarks = totalMarks; }
    public int getObtainedMarks() { return obtainedMarks; }
    public void setObtainedMarks(int obtainedMarks) { this.obtainedMarks = obtainedMarks; }
    public int getCorrectAnswers() { return correctAnswers; }
    public void setCorrectAnswers(int correctAnswers) { this.correctAnswers = correctAnswers; }
    public int getWrongAnswers() { return wrongAnswers; }
    public void setWrongAnswers(int wrongAnswers) { this.wrongAnswers = wrongAnswers; }
    public int getUnansweredAnswers() { return unansweredAnswers; }
    public void setUnansweredAnswers(int unansweredAnswers) { this.unansweredAnswers = unansweredAnswers; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public int getRiskScore() { return riskScore; }
    public void setRiskScore(int riskScore) { this.riskScore = riskScore; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public User getUser() { return student; }
    public String getRecordingUrl() { return recordingUrl; }
    public void setRecordingUrl(String recordingUrl) { this.recordingUrl = recordingUrl; }
    public Integer getProctoringWarnings() { return proctoringWarnings != null ? proctoringWarnings : 0; }
    public void setProctoringWarnings(Integer proctoringWarnings) { this.proctoringWarnings = proctoringWarnings != null ? proctoringWarnings : 0; }
    public Long getRecordingDurationSeconds() { return recordingDurationSeconds; }
    public void setRecordingDurationSeconds(Long recordingDurationSeconds) { this.recordingDurationSeconds = recordingDurationSeconds; }
    public ProfessionalExam getProfessionalExam() {
        return examVersion != null ? examVersion.getProfessionalExam() : null;
    }
}
