package com.skillhub.dto;

import jakarta.validation.constraints.Size;

public class AnswerSubmitRequest {
    @Size(max = 255, message = "Selected option exceeds maximum length")
    private String selectedOption;

    @Size(max = 10000, message = "Text answer exceeds maximum length")
    private String textAnswer;

    @Size(max = 65536, message = "Code answer exceeds maximum length")
    private String codeAnswer;

    public String getSelectedOption() { return selectedOption; }
    public void setSelectedOption(String selectedOption) { this.selectedOption = selectedOption; }
    public String getTextAnswer() { return textAnswer; }
    public void setTextAnswer(String textAnswer) { this.textAnswer = textAnswer; }
    public String getCodeAnswer() { return codeAnswer; }
    public void setCodeAnswer(String codeAnswer) { this.codeAnswer = codeAnswer; }
}
