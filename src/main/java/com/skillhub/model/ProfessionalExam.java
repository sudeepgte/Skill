package com.skillhub.model;

import com.skillhub.model.enums.ExamDifficulty;
import com.skillhub.model.enums.ExamTrack;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "professional_exams", indexes = {
    @Index(name = "idx_prof_exam_code", columnList = "examCode", unique = true),
    @Index(name = "idx_prof_exam_track", columnList = "track"),
    @Index(name = "idx_prof_exam_active", columnList = "active")
})
public class ProfessionalExam {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String examCode;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExamTrack track;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExamDifficulty level;

    private int durationMinutes;
    private int totalMarks;
    private int passingMarks;

    private boolean negativeMarking;
    private BigDecimal negativeMarkValue;

    private int maximumAttempts;
    private boolean applicationRequired;
    private BigDecimal applicationFee;

    private boolean active;

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
    public String getExamCode() { return examCode; }
    public void setExamCode(String examCode) { this.examCode = examCode; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public ExamTrack getTrack() { return track; }
    public void setTrack(ExamTrack track) { this.track = track; }
    public ExamDifficulty getLevel() { return level; }
    public void setLevel(ExamDifficulty level) { this.level = level; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public int getTotalMarks() { return totalMarks; }
    public void setTotalMarks(int totalMarks) { this.totalMarks = totalMarks; }
    public int getPassingMarks() { return passingMarks; }
    public void setPassingMarks(int passingMarks) { this.passingMarks = passingMarks; }
    public boolean isNegativeMarking() { return negativeMarking; }
    public void setNegativeMarking(boolean negativeMarking) { this.negativeMarking = negativeMarking; }
    public BigDecimal getNegativeMarkValue() { return negativeMarkValue; }
    public void setNegativeMarkValue(BigDecimal negativeMarkValue) { this.negativeMarkValue = negativeMarkValue; }
    public int getMaximumAttempts() { return maximumAttempts; }
    public void setMaximumAttempts(int maximumAttempts) { this.maximumAttempts = maximumAttempts; }
    public boolean isApplicationRequired() { return applicationRequired; }
    public void setApplicationRequired(boolean applicationRequired) { this.applicationRequired = applicationRequired; }
    public BigDecimal getApplicationFee() { return applicationFee; }
    public void setApplicationFee(BigDecimal applicationFee) { this.applicationFee = applicationFee; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
