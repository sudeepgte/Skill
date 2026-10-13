package com.skillhub.model;

import com.skillhub.model.enums.ExamDifficulty;
import com.skillhub.model.enums.QuestionType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "professional_exam_questions", indexes = {
    @Index(name = "idx_prof_q_sec", columnList = "exam_section_id"),
    @Index(name = "idx_prof_q_type", columnList = "questionType"),
    @Index(name = "idx_prof_q_diff", columnList = "difficulty"),
    @Index(name = "idx_prof_q_topic", columnList = "topic"),
    @Index(name = "idx_prof_q_active", columnList = "active")
})
public class ProfessionalExamQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_section_id", nullable = false)
    private ProfessionalExamSection examSection;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionType questionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExamDifficulty difficulty;

    private String topic;
    private String skill;

    private int marks;
    private BigDecimal negativeMarks;

    @ElementCollection
    @CollectionTable(name = "professional_exam_question_options", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "option_text", columnDefinition = "TEXT")
    private List<String> options = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String correctAnswer;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    private String codeLanguage;
    
    @Column(columnDefinition = "TEXT")
    private String codeStarterTemplate;

    private Integer timeLimit; // in milliseconds, for code execution
    private Integer memoryLimit; // in MB

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
    public ProfessionalExamSection getExamSection() { return examSection; }
    public void setExamSection(ProfessionalExamSection examSection) { this.examSection = examSection; }
    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }
    public QuestionType getQuestionType() { return questionType; }
    public void setQuestionType(QuestionType questionType) { this.questionType = questionType; }
    public ExamDifficulty getDifficulty() { return difficulty; }
    public void setDifficulty(ExamDifficulty difficulty) { this.difficulty = difficulty; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getSkill() { return skill; }
    public void setSkill(String skill) { this.skill = skill; }
    public int getMarks() { return marks; }
    public void setMarks(int marks) { this.marks = marks; }
    public BigDecimal getNegativeMarks() { return negativeMarks; }
    public void setNegativeMarks(BigDecimal negativeMarks) { this.negativeMarks = negativeMarks; }
    public List<String> getOptions() { return options; }
    public void setOptions(List<String> options) { this.options = options; }
    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public String getCodeLanguage() { return codeLanguage; }
    public void setCodeLanguage(String codeLanguage) { this.codeLanguage = codeLanguage; }
    public String getCodeStarterTemplate() { return codeStarterTemplate; }
    public void setCodeStarterTemplate(String codeStarterTemplate) { this.codeStarterTemplate = codeStarterTemplate; }
    public Integer getTimeLimit() { return timeLimit; }
    public void setTimeLimit(Integer timeLimit) { this.timeLimit = timeLimit; }
    public Integer getMemoryLimit() { return memoryLimit; }
    public void setMemoryLimit(Integer memoryLimit) { this.memoryLimit = memoryLimit; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
