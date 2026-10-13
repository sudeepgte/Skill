package com.skillhub.model;

import com.skillhub.model.enums.CertificateStatus;
import com.skillhub.model.enums.ExamTrack;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "professional_certificates", indexes = {
    @Index(name = "idx_prof_cert_cid", columnList = "certificateId", unique = true),
    @Index(name = "idx_prof_cert_token", columnList = "verificationToken", unique = true),
    @Index(name = "idx_prof_cert_user", columnList = "user_id"),
    @Index(name = "idx_prof_cert_exam", columnList = "exam_id"),
    @Index(name = "idx_prof_cert_status", columnList = "status")
})
public class ProfessionalCertificate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String certificateId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_id", nullable = false)
    private ProfessionalExamResult result;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false)
    private ProfessionalExam exam;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExamTrack track;

    private LocalDate issueDate;
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CertificateStatus status;

    @Column(nullable = false, unique = true, length = 150)
    private String verificationToken;

    @Column(length = 500)
    private String verificationUrl;

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
    public String getCertificateId() { return certificateId; }
    public void setCertificateId(String certificateId) { this.certificateId = certificateId; }
    public ProfessionalExamResult getResult() { return result; }
    public void setResult(ProfessionalExamResult result) { this.result = result; }
    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }
    public ProfessionalExam getExam() { return exam; }
    public void setExam(ProfessionalExam exam) { this.exam = exam; }
    public ExamTrack getTrack() { return track; }
    public void setTrack(ExamTrack track) { this.track = track; }
    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public CertificateStatus getStatus() { return status; }
    public void setStatus(CertificateStatus status) { this.status = status; }
    public String getVerificationToken() { return verificationToken; }
    public void setVerificationToken(String verificationToken) { this.verificationToken = verificationToken; }
    public String getVerificationUrl() { return verificationUrl; }
    public void setVerificationUrl(String verificationUrl) { this.verificationUrl = verificationUrl; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
