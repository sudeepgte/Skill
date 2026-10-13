package com.skillhub.model;
import jakarta.persistence.*;
import com.skillhub.model.enums.*;
@Entity
@Table(name = "professional_exam_blueprints", indexes = {
    @Index(name = "idx_prof_bp_sec", columnList = "exam_section_id")
})
public class ProfessionalExamBlueprint {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "exam_section_id") private ProfessionalExamSection section;
    private int questionCount;
    private int marksPerQuestion;
    @Enumerated(EnumType.STRING) private ExamDifficulty difficulty;
    @Enumerated(EnumType.STRING) private QuestionType questionType;
    private String topic;
    private String skill;
    
    // Getters and Setters
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public ProfessionalExamSection getSection() { return section; } public void setSection(ProfessionalExamSection section) { this.section = section; }
    public int getQuestionCount() { return questionCount; } public void setQuestionCount(int questionCount) { this.questionCount = questionCount; }
    public int getMarksPerQuestion() { return marksPerQuestion; } public void setMarksPerQuestion(int marksPerQuestion) { this.marksPerQuestion = marksPerQuestion; }
    public ExamDifficulty getDifficulty() { return difficulty; } public void setDifficulty(ExamDifficulty difficulty) { this.difficulty = difficulty; }
    public QuestionType getQuestionType() { return questionType; } public void setQuestionType(QuestionType questionType) { this.questionType = questionType; }
    public String getTopic() { return topic; } public void setTopic(String topic) { this.topic = topic; }
    public String getSkill() { return skill; } public void setSkill(String skill) { this.skill = skill; }
}
