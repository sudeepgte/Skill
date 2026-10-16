package com.skillhub.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "professional_exam_attempt_questions", indexes = {
    @Index(name = "idx_prof_attq_att", columnList = "attempt_id"),
    @Index(name = "idx_prof_attq_qid", columnList = "question_id"),
    @Index(name = "idx_prof_attq_att_num", columnList = "attempt_id, questionNumber")
})
public class ProfessionalExamAttemptQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = false)
    private ProfessionalExamAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private ProfessionalExamQuestion question;

    private int displayOrder;
    private int sectionOrder;
    private int questionNumber;

    private boolean visited;
    private boolean answered;
    private boolean markedForReview;

    private LocalDateTime selectedAt;

    public ProfessionalExamAttemptQuestion() {}

    public ProfessionalExamAttemptQuestion(ProfessionalExamAttempt attempt, ProfessionalExamQuestion question, int displayOrder, int questionNumber) {
        this.attempt = attempt;
        this.question = question;
        this.displayOrder = displayOrder;
        this.questionNumber = questionNumber;
        this.selectedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ProfessionalExamAttempt getAttempt() { return attempt; }
    public void setAttempt(ProfessionalExamAttempt attempt) { this.attempt = attempt; }
    public ProfessionalExamQuestion getQuestion() { return question; }
    public void setQuestion(ProfessionalExamQuestion question) { this.question = question; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public int getSectionOrder() { return sectionOrder; }
    public void setSectionOrder(int sectionOrder) { this.sectionOrder = sectionOrder; }
    public int getQuestionNumber() { return questionNumber; }
    public void setQuestionNumber(int questionNumber) { this.questionNumber = questionNumber; }
    public boolean isVisited() { return visited; }
    public void setVisited(boolean visited) { this.visited = visited; }
    public boolean isAnswered() { return answered; }
    public void setAnswered(boolean answered) { this.answered = answered; }
    public boolean isMarkedForReview() { return markedForReview; }
    public void setMarkedForReview(boolean markedForReview) { this.markedForReview = markedForReview; }
    public LocalDateTime getSelectedAt() { return selectedAt; }
    public void setSelectedAt(LocalDateTime selectedAt) { this.selectedAt = selectedAt; }
}
