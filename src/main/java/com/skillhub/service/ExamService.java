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

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ExamService {

    @Autowired
    private ExamAttemptRepository examAttemptRepository;

    @Autowired
    private GeminiService geminiService;

    // In-memory cache to store correct answers per user and course
    private Map<String, Map<String, String>> activeExams = new HashMap<>();
    
    // In-memory cache to store the full generated questions list for submission logging
    private Map<String, List<Map<String, Object>>> activeExamsList = new HashMap<>();

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
                // 3. Fallback to varied sample mock questions
                String[] templates = {
                    "Which of the following best defines a primary goal of ${topic}?",
                    "In a professional environment, how is ${topic} typically implemented?",
                    "What is a fundamental challenge associated with ${topic}?",
                    "Which key performance indicator is most relevant to ${topic}?",
                    "When evaluating ${topic}, which factor should be prioritized first?",
                    "How does ${topic} integrate with existing industry standards?",
                    "What is a common misconception about ${topic} theory?",
                    "Which of these strategies is most effective for improving ${topic} results?",
                    "Which role is primarily responsible for managing ${topic} execution?",
                    "Which technological advancement has most influenced ${topic} recently?",
                    "Which of the following is an ethical consideration in ${topic}?",
                    "What is the first step in a standard ${topic} workflow?",
                    "Which tool is most commonly used for monitoring ${topic}?",
                    "What is a long-term benefit of mastering ${topic}?",
                    "How often should a ${topic} audit be performed?",
                    "What is a prerequisite for understanding advanced ${topic}?",
                    "Which of these is a secondary effect of ${topic} misconfiguration?",
                    "Which department typically oversees ${topic} in a large organization?",
                    "How has modern regulation impacted the practice of ${topic}?",
                    "What defines the current industry standard for ${topic} success?"
                };

                String[][] optionSets = {
                    {"Increased efficiency", "Reduced overhead", "Complex integration", "Simplified workflows"},
                    {"Analyze data", "Engage stakeholders", "Allocate budget", "Perform testing"},
                    {"Cost optimization", "User experience", "Security compliance", "Network latency"},
                    {"Strategy phase", "Technical debt", "Resource management", "Market scaling"}
                };

                char[] answerKeys = {'a', 'b', 'c', 'd'};
                java.util.Random rand = new java.util.Random(course.getId() + user.getId());

                for (int i = 0; i < remainingQuestions; i++) {
                    Map<String, Object> q = new HashMap<>();
                    q.put("id", questionIdCounter++);
                    
                    String template = templates[i % templates.length];
                    q.put("question", template.replace("${topic}", course.getTitle()));

                    Map<String, String> options = new HashMap<>();
                    String[] set = optionSets[i % optionSets.length];
                    
                    // Assign options a-d
                    options.put("a", set[0]);
                    options.put("b", set[1]);
                    options.put("c", set[2]);
                    options.put("d", set[3]);

                    q.put("options", options);
                    // Cycle through answer keys a,b,c,d for variety
                    q.put("correctAnswer", String.valueOf(answerKeys[rand.nextInt(4)]));
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
        activeExamsList.put(examKey, finalQuestions);

        return finalQuestions;
    }

    public ExamAttempt gradeExam(User user, Course course, Map<String, String> submittedAnswers) {
        int correctCount = 0;
        int totalQuestions = 20;

        String examKey = user.getId() + "_" + course.getId();
        Map<String, String> answerKey = activeExams.get(examKey);
        List<Map<String, Object>> cachedQuestions = activeExamsList.get(examKey);
        List<Map<String, Object>> submissionList = new ArrayList<>();

        if (answerKey != null) {
            for (Map.Entry<String, String> entry : submittedAnswers.entrySet()) {
                if (entry.getKey().startsWith("q_")) {
                    String correctAnswer = answerKey.get(entry.getKey());
                    if (correctAnswer != null && correctAnswer.equalsIgnoreCase(entry.getValue())) {
                        correctCount++;
                    }
                }
            }

            // Build submission details
            if (cachedQuestions != null) {
                for (Map<String, Object> q : cachedQuestions) {
                    Map<String, Object> submissionItem = new HashMap<>();
                    submissionItem.put("id", q.get("id"));
                    submissionItem.put("question", q.get("question"));
                    submissionItem.put("options", q.get("options"));
                    submissionItem.put("correctAnswer", q.get("correctAnswer"));

                    String selected = submittedAnswers.get("q_" + q.get("id"));
                    submissionItem.put("selectedAnswer", selected != null ? selected : "none");
                    submissionList.add(submissionItem);
                }
                activeExamsList.remove(examKey);
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

        // Serialize submission details to JSON
        String submissionDetailsJson = "[]";
        if (!submissionList.isEmpty()) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                submissionDetailsJson = mapper.writeValueAsString(submissionList);
            } catch (Exception e) {
                System.err.println("Error serializing exam submission details to JSON: " + e.getMessage());
            }
        }
        attempt.setSubmissionDetails(submissionDetailsJson);

        return examAttemptRepository.save(attempt);
    }
}
