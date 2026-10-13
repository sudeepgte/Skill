package com.skillhub.dto;
public class QuestionPaletteItem {
    private int number;
    private boolean answered;
    private boolean markedForReview;
    
    public QuestionPaletteItem(int number, boolean answered, boolean markedForReview) {
        this.number = number;
        this.answered = answered;
        this.markedForReview = markedForReview;
    }
    public int getNumber() { return number; }
    public boolean isAnswered() { return answered; }
    public boolean isMarkedForReview() { return markedForReview; }
}
