package com.skillhub.model;

import com.skillhub.model.enums.CourseQuestionType;
import jakarta.persistence.*;

@Entity
@Table(name = "exam_questions")
public class ExamQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = true)
    private CourseExam exam;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String questionText;

    // Legacy Multiple Choice options (retained for 100% backward compatibility)
    @Column(nullable = true)
    private String optionA = "";

    @Column(nullable = true)
    private String optionB = "";

    @Column(nullable = true)
    private String optionC = "";

    @Column(nullable = true)
    private String optionD = "";

    @Column(nullable = true, length = 100)
    private String correctOption = ""; // Legacy: "A", "B", "C", "D"

    // Multi-type fields
    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", length = 40)
    private CourseQuestionType questionType = CourseQuestionType.MULTIPLE_CHOICE;

    @Column(name = "marks")
    private Integer marks = 1;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(columnDefinition = "LONGTEXT")
    private String typeData; // Structured JSON for type-specific data (options list, blanks, pairs, sequence items, rubric, test cases)

    @Column(columnDefinition = "TEXT")
    private String correctAnswer; // Canonical correct answer representation

    @Column(name = "manual_grading")
    private Boolean manualGrading = false;

    @Column(name = "case_sensitive")
    private Boolean caseSensitive = false;

    @Column(name = "tolerance")
    private Double tolerance;

    @Column(name = "code_language", length = 50)
    private String codeLanguage;

    public ExamQuestion() {
    }

    public ExamQuestion(Course course, String questionText, String optionA, String optionB, String optionC,
            String optionD, String correctOption) {
        this.course = course;
        this.questionText = questionText;
        this.optionA = optionA != null ? optionA : "";
        this.optionB = optionB != null ? optionB : "";
        this.optionC = optionC != null ? optionC : "";
        this.optionD = optionD != null ? optionD : "";
        this.correctOption = correctOption != null ? correctOption : "";
        this.correctAnswer = this.correctOption;
        this.questionType = CourseQuestionType.MULTIPLE_CHOICE;
        this.marks = 1;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public String getOptionA() {
        return optionA;
    }

    public void setOptionA(String optionA) {
        this.optionA = optionA;
    }

    public String getOptionB() {
        return optionB;
    }

    public void setOptionB(String optionB) {
        this.optionB = optionB;
    }

    public String getOptionC() {
        return optionC;
    }

    public void setOptionC(String optionC) {
        this.optionC = optionC;
    }

    public String getOptionD() {
        return optionD;
    }

    public void setOptionD(String optionD) {
        this.optionD = optionD;
    }

    public String getCorrectOption() {
        return correctOption;
    }

    public void setCorrectOption(String correctOption) {
        this.correctOption = correctOption;
    }

    public CourseQuestionType getQuestionType() {
        return questionType != null ? questionType : CourseQuestionType.MULTIPLE_CHOICE;
    }

    public void setQuestionType(CourseQuestionType questionType) {
        this.questionType = questionType;
    }

    public Integer getMarks() {
        return marks != null && marks > 0 ? marks : 1;
    }

    public void setMarks(Integer marks) {
        this.marks = marks;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getTypeData() {
        return typeData;
    }

    public void setTypeData(String typeData) {
        this.typeData = typeData;
    }

    public String getCorrectAnswer() {
        if (correctAnswer != null && !correctAnswer.trim().isEmpty()) {
            return correctAnswer;
        }
        return correctOption != null ? correctOption : "";
    }

    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    public Boolean getManualGrading() {
        return manualGrading != null ? manualGrading : false;
    }

    public void setManualGrading(Boolean manualGrading) {
        this.manualGrading = manualGrading;
    }

    public Boolean getCaseSensitive() {
        return caseSensitive != null ? caseSensitive : false;
    }

    public void setCaseSensitive(Boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    public Double getTolerance() {
        return tolerance;
    }

    public void setTolerance(Double tolerance) {
        this.tolerance = tolerance;
    }

    public String getCodeLanguage() {
        return codeLanguage;
    }

    public void setCodeLanguage(String codeLanguage) {
        this.codeLanguage = codeLanguage;
    }

    public CourseExam getExam() {
        return exam;
    }

    public void setExam(CourseExam exam) {
        this.exam = exam;
    }
}
