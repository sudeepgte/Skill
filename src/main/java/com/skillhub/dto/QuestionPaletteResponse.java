package com.skillhub.dto;
import java.util.List;
public class QuestionPaletteResponse {
    private List<QuestionPaletteItem> questions;
    public List<QuestionPaletteItem> getQuestions() { return questions; }
    public void setQuestions(List<QuestionPaletteItem> questions) { this.questions = questions; }
}
