package com.skillhub.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "professional_exam_results", indexes = {
    @Index(name = "idx_prof_res_att", columnList = "attempt_id"),
    @Index(name = "idx_prof_res_status", columnList = "status")
})
public class ProfessionalExamResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = false)
    private ProfessionalExamAttempt attempt;

    private int totalMarks;
    private BigDecimal obtainedMarks;
    private double percentage;

    private int correctAnswers;
    private int wrongAnswers;
    private int unansweredAnswers;

    private double technicalScore;
    private double problemSolvingScore;
    private double codingScore;

    private String grade;
    private String status; // PASS, FAIL, etc.
    private int riskScore;

    private LocalDateTime evaluatedAt;

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
    public ProfessionalExamAttempt getAttempt() { return attempt; }
    public void setAttempt(ProfessionalExamAttempt attempt) { this.attempt = attempt; }
    public int getTotalMarks() { return totalMarks; }
    public void setTotalMarks(int totalMarks) { this.totalMarks = totalMarks; }
    public BigDecimal getObtainedMarks() { return obtainedMarks; }
    public void setObtainedMarks(BigDecimal obtainedMarks) { this.obtainedMarks = obtainedMarks; }
    public double getPercentage() { return percentage; }
    public void setPercentage(double percentage) { this.percentage = percentage; }
    public int getCorrectAnswers() { return correctAnswers; }
    public void setCorrectAnswers(int correctAnswers) { this.correctAnswers = correctAnswers; }
    public int getWrongAnswers() { return wrongAnswers; }
    public void setWrongAnswers(int wrongAnswers) { this.wrongAnswers = wrongAnswers; }
    public int getUnansweredAnswers() { return unansweredAnswers; }
    public void setUnansweredAnswers(int unansweredAnswers) { this.unansweredAnswers = unansweredAnswers; }
    public double getTechnicalScore() { return technicalScore; }
    public void setTechnicalScore(double technicalScore) { this.technicalScore = technicalScore; }
    public double getProblemSolvingScore() { return problemSolvingScore; }
    public void setProblemSolvingScore(double problemSolvingScore) { this.problemSolvingScore = problemSolvingScore; }
    public double getCodingScore() { return codingScore; }
    public void setCodingScore(double codingScore) { this.codingScore = codingScore; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public int getRiskScore() { return riskScore; }
    public void setRiskScore(int riskScore) { this.riskScore = riskScore; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(LocalDateTime evaluatedAt) { this.evaluatedAt = evaluatedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
