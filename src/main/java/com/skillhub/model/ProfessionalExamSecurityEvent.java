package com.skillhub.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "professional_exam_security_events", indexes = {
    @Index(name = "idx_prof_sec_att", columnList = "attempt_id"),
    @Index(name = "idx_prof_sec_type", columnList = "eventType")
})
public class ProfessionalExamSecurityEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = false)
    private ProfessionalExamAttempt attempt;

    @Column(nullable = false)
    private String eventType;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    private String ipAddress;
    private String userAgent;

    private LocalDateTime eventTimestamp;

    public ProfessionalExamSecurityEvent() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ProfessionalExamAttempt getAttempt() { return attempt; }
    public void setAttempt(ProfessionalExamAttempt attempt) { this.attempt = attempt; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public LocalDateTime getEventTimestamp() { return eventTimestamp; }
    public void setEventTimestamp(LocalDateTime eventTimestamp) { this.eventTimestamp = eventTimestamp; }
}
