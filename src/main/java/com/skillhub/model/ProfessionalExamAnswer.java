package com.skillhub.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "professional_exam_answers", indexes = {
    @Index(name = "idx_prof_ans_att_q", columnList = "attempt_id, question_id")
})
public class ProfessionalExamAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = false)
    private ProfessionalExamAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private ProfessionalExamQuestion question;

    @Column(columnDefinition = "TEXT")
    private String selectedOption;

    @Column(columnDefinition = "TEXT")
    private String textAnswer;

    @Column(columnDefinition = "TEXT")
    private String codeAnswer;

    private Boolean isCorrect;
    private BigDecimal marksObtained;

    private Long executionTime;
    private Long memoryUsed;

    private LocalDateTime submittedAt;

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
    public ProfessionalExamQuestion getQuestion() { return question; }
    public void setQuestion(ProfessionalExamQuestion question) { this.question = question; }
    public String getSelectedOption() { return selectedOption; }
    public void setSelectedOption(String selectedOption) { this.selectedOption = selectedOption; }
    public String getTextAnswer() { return textAnswer; }
    public void setTextAnswer(String textAnswer) { this.textAnswer = textAnswer; }
    public String getCodeAnswer() { return codeAnswer; }
    public void setCodeAnswer(String codeAnswer) { this.codeAnswer = codeAnswer; }
    public Boolean getIsCorrect() { return isCorrect; }
    public void setIsCorrect(Boolean isCorrect) { this.isCorrect = isCorrect; }
    public BigDecimal getMarksObtained() { return marksObtained; }
    public void setMarksObtained(BigDecimal marksObtained) { this.marksObtained = marksObtained; }
    public Long getExecutionTime() { return executionTime; }
    public void setExecutionTime(Long executionTime) { this.executionTime = executionTime; }
    public Long getMemoryUsed() { return memoryUsed; }
    public void setMemoryUsed(Long memoryUsed) { this.memoryUsed = memoryUsed; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
