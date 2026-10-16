package com.skillhub.model;
import com.skillhub.model.enums.SubmissionStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "professional_coding_submissions", indexes = {
    @Index(name = "idx_prof_code_att_q", columnList = "attempt_id, question_id"),
    @Index(name = "idx_prof_code_status", columnList = "status")
})
public class ProfessionalCodingSubmission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "attempt_id") private ProfessionalExamAttempt attempt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "question_id") private ProfessionalExamQuestion question;
    
    private String language;
    @Column(columnDefinition = "TEXT") private String sourceCode;
    
    @Enumerated(EnumType.STRING) private SubmissionStatus status;
    private Long executionTimeMs;
    private Long memoryUsedMb;
    private Integer testsPassed;
    private Integer testsTotal;
    private Double score;
    
    @Column(updatable = false) private LocalDateTime submittedAt;
    @PrePersist protected void onCreate() { submittedAt = LocalDateTime.now(); }

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public ProfessionalExamAttempt getAttempt() { return attempt; } public void setAttempt(ProfessionalExamAttempt attempt) { this.attempt = attempt; }
    public ProfessionalExamQuestion getQuestion() { return question; } public void setQuestion(ProfessionalExamQuestion question) { this.question = question; }
    public String getLanguage() { return language; } public void setLanguage(String language) { this.language = language; }
    public String getSourceCode() { return sourceCode; } public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    public SubmissionStatus getStatus() { return status; } public void setStatus(SubmissionStatus status) { this.status = status; }
    public Long getExecutionTimeMs() { return executionTimeMs; } public void setExecutionTimeMs(Long executionTimeMs) { this.executionTimeMs = executionTimeMs; }
    public Long getMemoryUsedMb() { return memoryUsedMb; } public void setMemoryUsedMb(Long memoryUsedMb) { this.memoryUsedMb = memoryUsedMb; }
    public Integer getTestsPassed() { return testsPassed; } public void setTestsPassed(Integer testsPassed) { this.testsPassed = testsPassed; }
    public Integer getTestsTotal() { return testsTotal; } public void setTestsTotal(Integer testsTotal) { this.testsTotal = testsTotal; }
    public Double getScore() { return score; } public void setScore(Double score) { this.score = score; }
    public LocalDateTime getSubmittedAt() { return submittedAt; } public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
}
