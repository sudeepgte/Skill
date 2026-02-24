package com.skillhub.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "enrollments")
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    private LocalDateTime enrolledAt;

    private double progressPercentage; // 0 to 100

    @Column(name = "last_played_lecture_id")
    private Long lastPlayedLectureId; // For "Continue Watching" functionality

    @Column(name = "is_exam_passed")
    private Boolean isExamPassed = false; // Changed to Wrapper

    private LocalDateTime certificateDate;

    public Enrollment() {
        this.enrolledAt = LocalDateTime.now();
        this.progressPercentage = 0;
    }

    public Enrollment(User student, Course course) {
        this.student = student;
        this.course = course;
        this.enrolledAt = LocalDateTime.now();
        this.progressPercentage = 0;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getStudent() {
        return student;
    }

    public void setStudent(User student) {
        this.student = student;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public LocalDateTime getEnrolledAt() {
        return enrolledAt;
    }

    public void setEnrolledAt(LocalDateTime enrolledAt) {
        this.enrolledAt = enrolledAt;
    }

    public double getProgressPercentage() {
        return progressPercentage;
    }

    public void setProgressPercentage(double progressPercentage) {
        this.progressPercentage = progressPercentage;
    }

    public Long getLastPlayedLectureId() {
        return lastPlayedLectureId;
    }

    public void setLastPlayedLectureId(Long lastPlayedLectureId) {
        this.lastPlayedLectureId = lastPlayedLectureId;
    }

    public boolean isExamPassed() {
        return isExamPassed != null && isExamPassed;
    }

    public void setExamPassed(boolean examPassed) {
        this.isExamPassed = examPassed;
    }

    public LocalDateTime getCertificateDate() {
        return certificateDate;
    }

    public void setCertificateDate(LocalDateTime certificateDate) {
        this.certificateDate = certificateDate;
    }
}
