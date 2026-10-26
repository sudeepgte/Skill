package com.skillhub.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "course_exams")
public class CourseExam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "passing_score")
    private int passingScore = 70; // percentage

    @Column(name = "duration_minutes")
    private int durationMinutes = 30;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "exam", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExamQuestion> questions = new ArrayList<>();

    @OneToMany(mappedBy = "exam", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExamAttempt> attempts = new ArrayList<>();

    public CourseExam() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public CourseExam(String title, Course course) {
        this.title = title;
        this.course = course;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public int getPassingScore() {
        return passingScore;
    }

    public void setPassingScore(int passingScore) {
        this.passingScore = passingScore;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<ExamQuestion> getQuestions() {
        return questions;
    }

    public void setQuestions(List<ExamQuestion> questions) {
        this.questions = questions;
    }

    public List<ExamQuestion> getExamQuestions() {
        if (questions != null && !questions.isEmpty()) {
            return questions;
        }
        if (course != null && course.getExamQuestions() != null) {
            return course.getExamQuestions();
        }
        return Collections.emptyList();
    }

    public List<ExamAttempt> getAttempts() {
        return attempts;
    }

    public void setAttempts(List<ExamAttempt> attempts) {
        this.attempts = attempts;
    }

    @Transient
    public Category getCategory() {
        return course != null ? course.getCategory() : null;
    }

    @Transient
    public String getSubCategory() {
        return course != null ? course.getSubCategory() : null;
    }

    @Transient
    public String getFormattedExamDate() {
        LocalDateTime dt = updatedAt != null ? updatedAt : (createdAt != null ? createdAt : LocalDateTime.now());
        return dt.format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
    }

    @Transient
    public String getFormattedExamTime() {
        LocalDateTime dt = updatedAt != null ? updatedAt : (createdAt != null ? createdAt : LocalDateTime.now());
        return dt.format(DateTimeFormatter.ofPattern("hh:mm a"));
    }

    @Transient
    public String getFormattedExamDateTime() {
        LocalDateTime dt = updatedAt != null ? updatedAt : (createdAt != null ? createdAt : LocalDateTime.now());
        return dt.format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"));
    }
}
