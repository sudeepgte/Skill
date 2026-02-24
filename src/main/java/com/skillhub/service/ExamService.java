package com.skillhub.service;

import com.skillhub.model.Course;
import com.skillhub.model.ExamAttempt;
import com.skillhub.model.User;
import com.skillhub.repository.ExamAttemptRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
public class ExamService {

    @Autowired
    private ExamAttemptRepository examAttemptRepository;

    @Autowired
    private GeminiService geminiService;

    // In-memory cache to store correct answers per user and course (for grading
    // dynamic AI exams)
    private Map<String, Map<String, String>> activeExams = new HashMap<>();

    public List<Map<String, Object>> generateQuestions(Course course, User user) {
        List<Map<String, Object>> finalQuestions = new ArrayList<>();
        int questionIdCounter = 1;

        // 1. Fetch custom questions from the database
        List<com.skillhub.model.ExamQuestion> customQuestions = course.getExamQuestions();
        if (customQuestions != null) {
            for (com.skillhub.model.ExamQuestion customQ : customQuestions) {
                Map<String, Object> q = new HashMap<>();
                q.put("id", questionIdCounter++);
                q.put("question", customQ.getQuestionText());

                Map<String, String> options = new HashMap<>();
                options.put("a", customQ.getOptionA());
                options.put("b", customQ.getOptionB());
                options.put("c", customQ.getOptionC());
                options.put("d", customQ.getOptionD());

                q.put("options", options);
                q.put("correctAnswer", customQ.getCorrectOption().toLowerCase());
                finalQuestions.add(q);

                if (finalQuestions.size() >= 20)
                    break; // Limit to 20 total
            }
        }

        // 2. Fallback to Gemini AI if we need more questions
        int remainingQuestions = 20 - finalQuestions.size();
        if (remainingQuestions > 0) {
            List<Map<String, Object>> aiQuestions = geminiService.generateQuestions(course.getTitle());

            if (aiQuestions != null && aiQuestions.size() >= remainingQuestions) {
                for (int i = 0; i < remainingQuestions; i++) {
                    Map<String, Object> q = aiQuestions.get(i);
                    q.put("id", questionIdCounter++);
                    finalQuestions.add(q);
                }
            } else {
                // 3. Fallback to sample mock questions if API fails or is not configured
                for (int i = 0; i < remainingQuestions; i++) {
                    Map<String, Object> q = new HashMap<>();
                    q.put("id", questionIdCounter++);
                    q.put("question", "Question " + (finalQuestions.size() + 1) + " about " + course.getTitle()
                            + ": This is a backup auto-generated question.");

                    Map<String, String> options = new HashMap<>();
                    options.put("a", "Option A is correct");
                    options.put("b", "Option B is incorrect");
                    options.put("c", "Option C is incorrect");
                    options.put("d", "Option D is incorrect");

                    q.put("options", options);
                    q.put("correctAnswer", "a");
                    finalQuestions.add(q);
                }
            }
        }

        // Cache the correct answers securely on backend so the user can't inspect
        Map<String, String> answerKey = new HashMap<>();
        for (Map<String, Object> q : finalQuestions) {
            String qId = String.valueOf(q.get("id"));
            String correct = String.valueOf(q.get("correctAnswer"));
            answerKey.put("q_" + qId, correct);
        }

        String examKey = user.getId() + "_" + course.getId();
        activeExams.put(examKey, answerKey);

        return finalQuestions;
    }

    public ExamAttempt gradeExam(User user, Course course, Map<String, String> submittedAnswers) {
        int correctCount = 0;
        int totalQuestions = 20;

        String examKey = user.getId() + "_" + course.getId();
        Map<String, String> answerKey = activeExams.get(examKey);

        if (answerKey != null) {
            for (Map.Entry<String, String> entry : submittedAnswers.entrySet()) {
                if (entry.getKey().startsWith("q_")) {
                    String correctAnswer = answerKey.get(entry.getKey());
                    if (correctAnswer != null && correctAnswer.equalsIgnoreCase(entry.getValue())) {
                        correctCount++;
                    }
                }
            }
            // Cleanup cache after grade
            activeExams.remove(examKey);
        } else {
            // Fallback grading if cache missing (e.g., server restart during exam)
            for (Map.Entry<String, String> entry : submittedAnswers.entrySet()) {
                if (entry.getKey().startsWith("q_") && "a".equalsIgnoreCase(entry.getValue())) {
                    correctCount++;
                }
            }
        }

        int score = (int) (((double) correctCount / totalQuestions) * 100);
        boolean isPassed = score >= 70;

        ExamAttempt attempt = new ExamAttempt(course, user, score, isPassed);
        return examAttemptRepository.save(attempt);
    }
}
