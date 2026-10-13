package com.skillhub.dto;
import java.util.List;
public class ExamQuestionResponse {
    private int questionNumber;
    private int totalQuestions;
    private String section;
    private String questionType;
    private String difficulty;
    private String questionText;
    private List<String> options;
    private int marks;
    private double negativeMarks;
    private boolean answered;
    private boolean markedForReview;
    private String savedAnswer;

    // Getters and setters
    public int getQuestionNumber() { return questionNumber; }
    public void setQuestionNumber(int questionNumber) { this.questionNumber = questionNumber; }
    public int getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(int totalQuestions) { this.totalQuestions = totalQuestions; }
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }
    public List<String> getOptions() { return options; }
    public void setOptions(List<String> options) { this.options = options; }
    public int getMarks() { return marks; }
    public void setMarks(int marks) { this.marks = marks; }
    public double getNegativeMarks() { return negativeMarks; }
    public void setNegativeMarks(double negativeMarks) { this.negativeMarks = negativeMarks; }
    public boolean isAnswered() { return answered; }
    public void setAnswered(boolean answered) { this.answered = answered; }
    public boolean isMarkedForReview() { return markedForReview; }
    public void setMarkedForReview(boolean markedForReview) { this.markedForReview = markedForReview; }
    public String getSavedAnswer() { return savedAnswer; }
    public void setSavedAnswer(String savedAnswer) { this.savedAnswer = savedAnswer; }
}
