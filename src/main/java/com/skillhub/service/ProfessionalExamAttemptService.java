package com.skillhub.service;

import com.skillhub.dto.*;
import com.skillhub.model.User;

public interface ProfessionalExamAttemptService {
    ExamAttemptOverviewResponse startExam(ExamStartRequest request, User user);
    ExamAttemptOverviewResponse getAttemptOverview(Long attemptId, User user);
    ExamQuestionResponse getQuestion(Long attemptId, int questionNumber, User user);
    AnswerSubmitResponse saveAnswer(Long attemptId, int questionNumber, AnswerSubmitRequest req, User user);
    void markForReview(Long attemptId, int questionNumber, boolean review, User user);
    QuestionPaletteResponse getPalette(Long attemptId, User user);
    void submitExam(Long attemptId, User user);
    CodeExecutionResult executeCode(Long attemptId, CodingExecutionRequest req, User user);
}
