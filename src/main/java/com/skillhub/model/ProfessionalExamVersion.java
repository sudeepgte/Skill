package com.skillhub.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "professional_exam_versions", indexes = {
    @Index(name = "idx_prof_ver_exam", columnList = "professional_exam_id"),
    @Index(name = "idx_prof_ver_status", columnList = "active, published")
})
public class ProfessionalExamVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "professional_exam_id", nullable = false)
    private ProfessionalExam professionalExam;

    @Column(nullable = false)
    private String versionNumber;

    @Column(nullable = false)
    private String versionName;

    private int totalMarks;
    private int passingMarks;
    private int durationMinutes;

    private boolean active;
    private boolean published;

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
    public ProfessionalExam getProfessionalExam() { return professionalExam; }
    public void setProfessionalExam(ProfessionalExam professionalExam) { this.professionalExam = professionalExam; }
    public String getVersionNumber() { return versionNumber; }
    public void setVersionNumber(String versionNumber) { this.versionNumber = versionNumber; }
    public String getVersionName() { return versionName; }
    public void setVersionName(String versionName) { this.versionName = versionName; }
    public int getTotalMarks() { return totalMarks; }
    public void setTotalMarks(int totalMarks) { this.totalMarks = totalMarks; }
    public int getPassingMarks() { return passingMarks; }
    public void setPassingMarks(int passingMarks) { this.passingMarks = passingMarks; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
