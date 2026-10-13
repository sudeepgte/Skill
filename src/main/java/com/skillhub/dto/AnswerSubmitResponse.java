package com.skillhub.dto;
import java.time.LocalDateTime;
public class AnswerSubmitResponse {
    private boolean saved;
    private LocalDateTime savedAt;

    public AnswerSubmitResponse(boolean saved, LocalDateTime savedAt) {
        this.saved = saved;
        this.savedAt = savedAt;
    }
    public boolean isSaved() { return saved; }
    public LocalDateTime getSavedAt() { return savedAt; }
}
