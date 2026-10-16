package com.skillhub.dto;

import java.time.LocalDate;

/**
 * Publicly verifiable certificate information.
 * Exposes strictly safe data and conceals candidate contact details, internal IDs, and exam answers.
 */
public class PublicCertificateDTO {
    private String certificateId;
    private String candidateName;
    private String certificationName;
    private String track;
    private String grade;
    private LocalDate issueDate;
    private LocalDate expiryDate;
    private String status;
    private boolean valid;

    public PublicCertificateDTO() {}

    public PublicCertificateDTO(String certificateId, String candidateName, String certificationName,
                                String track, String grade, LocalDate issueDate, LocalDate expiryDate,
                                String status, boolean valid) {
        this.certificateId = certificateId;
        this.candidateName = candidateName;
        this.certificationName = certificationName;
        this.track = track;
        this.grade = grade;
        this.issueDate = issueDate;
        this.expiryDate = expiryDate;
        this.status = status;
        this.valid = valid;
    }

    public String getCertificateId() { return certificateId; }
    public void setCertificateId(String certificateId) { this.certificateId = certificateId; }

    public String getCandidateName() { return candidateName; }
    public void setCandidateName(String candidateName) { this.candidateName = candidateName; }

    public String getCertificationName() { return certificationName; }
    public void setCertificationName(String certificationName) { this.certificationName = certificationName; }

    public String getTrack() { return track; }
    public void setTrack(String track) { this.track = track; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }
}
