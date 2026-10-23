package com.skillhub.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillhub.model.Course;
import com.skillhub.model.ExamAttempt;
import com.skillhub.model.ExamQuestion;
import com.skillhub.model.User;
import com.skillhub.model.enums.CourseQuestionType;
import com.skillhub.repository.ExamAttemptRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ExamService {

    @Autowired
    private ExamAttemptRepository examAttemptRepository;

    @Autowired
    private com.skillhub.repository.ExamQuestionRepository examQuestionRepository;

    @Autowired
    private GeminiService geminiService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory cache to store correct answers per user and course: examKey -> (qId -> correctAnswer)
    private final Map<String, Map<String, String>> activeExams = new HashMap<>();

    // In-memory cache to store question metadata: examKey -> (qId -> metaMap)
    private final Map<String, Map<String, Map<String, Object>>> activeExamsMeta = new HashMap<>();

    // In-memory cache to store full question representations for submission logging
    private final Map<String, List<Map<String, Object>>> activeExamsList = new HashMap<>();

    public List<Map<String, Object>> generateQuestions(Course course, User user) {
        List<Map<String, Object>> finalQuestions = new ArrayList<>();
        Map<String, String> answerKey = new HashMap<>();
        Map<String, Map<String, Object>> metaMap = new HashMap<>();
        int questionIdCounter = 1;

        // 1. Fetch custom course questions from database
        List<ExamQuestion> customQuestions = (course != null && course.getId() != null) ? examQuestionRepository.findByCourseId(course.getId()) : (course != null ? course.getExamQuestions() : Collections.emptyList());
        if (customQuestions != null && !customQuestions.isEmpty()) {
            for (ExamQuestion customQ : customQuestions) {
                int currentQId = questionIdCounter++;
                String qKey = "q_" + currentQId;

                Map<String, Object> q = new HashMap<>();
                q.put("id", currentQId);
                q.put("dbId", customQ.getId());
                q.put("question", customQ.getQuestionText());
                
                CourseQuestionType qType = customQ.getQuestionType();
                q.put("questionType", qType.name());
                q.put("questionTypeDisplay", qType.getDisplayName());
                q.put("marks", customQ.getMarks());
                q.put("explanation", customQ.getExplanation());
                q.put("codeLanguage", customQ.getCodeLanguage());

                // Parse typeData JSON if present
                Map<String, Object> parsedTypeData = parseJson(customQ.getTypeData());
                q.put("typeData", parsedTypeData);

                // Populate options for backward compatibility and MCQ
                if (qType == CourseQuestionType.MULTIPLE_CHOICE) {
                    Map<String, String> options = new LinkedHashMap<>();
                    if (parsedTypeData.containsKey("options") && parsedTypeData.get("options") instanceof List) {
                        List<?> optList = (List<?>) parsedTypeData.get("options");
                        for (int i = 0; i < optList.size(); i++) {
                            String key = String.valueOf((char) ('a' + i));
                            options.put(key, String.valueOf(optList.get(i)));
                        }
                    } else {
                        if (customQ.getOptionA() != null && !customQ.getOptionA().isEmpty()) options.put("a", customQ.getOptionA());
                        if (customQ.getOptionB() != null && !customQ.getOptionB().isEmpty()) options.put("b", customQ.getOptionB());
                        if (customQ.getOptionC() != null && !customQ.getOptionC().isEmpty()) options.put("c", customQ.getOptionC());
                        if (customQ.getOptionD() != null && !customQ.getOptionD().isEmpty()) options.put("d", customQ.getOptionD());
                    }
                    q.put("options", options);
                }

                finalQuestions.add(q);

                // Cache correct answer securely on backend (DO NOT expose in student response)
                String canonicalAnswer = customQ.getCorrectAnswer();
                if ((canonicalAnswer == null || canonicalAnswer.trim().isEmpty()) && customQ.getCorrectOption() != null) {
                    canonicalAnswer = customQ.getCorrectOption();
                }
                answerKey.put(qKey, canonicalAnswer != null ? canonicalAnswer.trim() : "");

                Map<String, Object> meta = new HashMap<>();
                meta.put("questionType", qType);
                meta.put("marks", customQ.getMarks());
                meta.put("caseSensitive", customQ.getCaseSensitive());
                meta.put("tolerance", customQ.getTolerance());
                meta.put("manualGrading", customQ.getManualGrading());
                meta.put("typeData", parsedTypeData);
                meta.put("correctAnswer", canonicalAnswer);
                meta.put("questionText", customQ.getQuestionText());
                metaMap.put(qKey, meta);

            }
        } else {
            // 2. AI Fallback for remaining questions (only when instructor has not added custom questions)
            int remainingQuestions = 20;
            List<Map<String, Object>> aiQuestions = geminiService.generateQuestions(course.getTitle());

            if (aiQuestions != null && aiQuestions.size() >= remainingQuestions) {
                for (int i = 0; i < remainingQuestions; i++) {
                    Map<String, Object> q = aiQuestions.get(i);
                    int currentQId = questionIdCounter++;
                    String qKey = "q_" + currentQId;
                    q.put("id", currentQId);
                    q.put("questionType", CourseQuestionType.MULTIPLE_CHOICE.name());
                    q.put("questionTypeDisplay", CourseQuestionType.MULTIPLE_CHOICE.getDisplayName());
                    q.put("marks", 1);

                    Object correctAns = q.remove("correctAnswer"); // Strip from view
                    answerKey.put(qKey, correctAns != null ? String.valueOf(correctAns).toLowerCase().trim() : "a");

                    Map<String, Object> meta = new HashMap<>();
                    meta.put("questionType", CourseQuestionType.MULTIPLE_CHOICE);
                    meta.put("marks", 1);
                    meta.put("correctAnswer", answerKey.get(qKey));
                    meta.put("questionText", q.get("question"));
                    metaMap.put(qKey, meta);

                    finalQuestions.add(q);
                }
            } else {
                // 3. Fallback mock templates
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
                Random rand = new Random(course.getId() + (user != null ? user.getId() : 0));

                for (int i = 0; i < remainingQuestions; i++) {
                    int currentQId = questionIdCounter++;
                    String qKey = "q_" + currentQId;

                    Map<String, Object> q = new HashMap<>();
                    q.put("id", currentQId);
                    q.put("questionType", CourseQuestionType.MULTIPLE_CHOICE.name());
                    q.put("questionTypeDisplay", CourseQuestionType.MULTIPLE_CHOICE.getDisplayName());
                    q.put("marks", 1);

                    String template = templates[i % templates.length];
                    q.put("question", template.replace("${topic}", course.getTitle()));

                    Map<String, String> options = new LinkedHashMap<>();
                    String[] set = optionSets[i % optionSets.length];
                    options.put("a", set[0]);
                    options.put("b", set[1]);
                    options.put("c", set[2]);
                    options.put("d", set[3]);
                    q.put("options", options);

                    String chosenAns = String.valueOf(answerKeys[rand.nextInt(4)]);
                    answerKey.put(qKey, chosenAns);

                    Map<String, Object> meta = new HashMap<>();
                    meta.put("questionType", CourseQuestionType.MULTIPLE_CHOICE);
                    meta.put("marks", 1);
                    meta.put("correctAnswer", chosenAns);
                    meta.put("questionText", q.get("question"));
                    metaMap.put(qKey, meta);

                    finalQuestions.add(q);
                }
            }
        }

        String examKey = (user != null ? user.getId() : 0) + "_" + course.getId();
        activeExams.put(examKey, answerKey);
        activeExamsMeta.put(examKey, metaMap);
        activeExamsList.put(examKey, finalQuestions);

        return finalQuestions;
    }

    public ExamAttempt gradeExam(User user, Course course, Map<String, String> submittedAnswers) {
        String examKey = (user != null ? user.getId() : 0) + "_" + course.getId();
        Map<String, String> answerKey = activeExams.get(examKey);
        Map<String, Map<String, Object>> metaMap = activeExamsMeta.get(examKey);
        List<Map<String, Object>> cachedQuestions = activeExamsList.get(examKey);

        int totalPossibleMarks = 0;
        int totalEarnedMarks = 0;
        List<Map<String, Object>> submissionList = new ArrayList<>();

        if (metaMap != null) {
            for (Map.Entry<String, Map<String, Object>> entry : metaMap.entrySet()) {
                String qKey = entry.getKey();
                Map<String, Object> meta = entry.getValue();
                CourseQuestionType qType = (CourseQuestionType) meta.getOrDefault("questionType", CourseQuestionType.MULTIPLE_CHOICE);
                int marks = (Integer) meta.getOrDefault("marks", 1);
                String correctAnswer = (String) meta.getOrDefault("correctAnswer", "");
                boolean caseSensitive = Boolean.TRUE.equals(meta.get("caseSensitive"));
                Double tolerance = (Double) meta.get("tolerance");
                boolean manualGrading = Boolean.TRUE.equals(meta.get("manualGrading"));
                @SuppressWarnings("unchecked")
                Map<String, Object> typeData = (Map<String, Object>) meta.getOrDefault("typeData", Collections.emptyMap());

                totalPossibleMarks += marks;

                String candidateAnswer = submittedAnswers.getOrDefault(qKey, "");
                boolean isCorrect = evaluateAnswer(qType, candidateAnswer, correctAnswer, caseSensitive, tolerance, manualGrading, typeData, submittedAnswers, qKey);
                int earned = isCorrect ? marks : 0;
                totalEarnedMarks += earned;

                Map<String, Object> item = new HashMap<>();
                item.put("key", qKey);
                item.put("question", meta.get("questionText"));
                item.put("questionType", qType.name());
                item.put("marks", marks);
                item.put("earnedMarks", earned);
                item.put("selectedAnswer", candidateAnswer);
                item.put("correctAnswer", correctAnswer);
                item.put("isCorrect", isCorrect);
                submissionList.add(item);
            }

            // Cleanup caches
            activeExams.remove(examKey);
            activeExamsMeta.remove(examKey);
            activeExamsList.remove(examKey);
        } else {
            // Fallback grading if session lost
            for (Map.Entry<String, String> entry : submittedAnswers.entrySet()) {
                if (entry.getKey().startsWith("q_")) {
                    totalPossibleMarks += 1;
                    if ("a".equalsIgnoreCase(entry.getValue()) || "true".equalsIgnoreCase(entry.getValue())) {
                        totalEarnedMarks += 1;
                    }
                }
            }
            if (totalPossibleMarks == 0) totalPossibleMarks = 20;
        }

        if (totalPossibleMarks == 0) totalPossibleMarks = 20;
        int score = (int) Math.round(((double) totalEarnedMarks / totalPossibleMarks) * 100);
        boolean isPassed = score >= 70;

        ExamAttempt attempt = new ExamAttempt(course, user, score, isPassed);

        // Serialize submission details to JSON
        try {
            attempt.setSubmissionDetails(objectMapper.writeValueAsString(submissionList));
        } catch (Exception e) {
            attempt.setSubmissionDetails("[]");
        }

        return examAttemptRepository.save(attempt);
    }

    private boolean evaluateAnswer(CourseQuestionType type, String candidate, String correct,
                                    boolean caseSensitive, Double tolerance, boolean manualGrading,
                                    Map<String, Object> typeData, Map<String, String> allAnswers, String qKey) {
        if (candidate == null) candidate = "";
        if (correct == null) correct = "";

        switch (type) {
            case MULTIPLE_CHOICE:
                return candidate.trim().equalsIgnoreCase(correct.trim());

            case MULTIPLE_SELECT:
                // Candidate can submit comma-separated (e.g. "A,B" or "opt_0,opt_2")
                Set<String> candSet = normalizeAnswerSet(candidate);
                Set<String> corrSet = normalizeAnswerSet(correct);
                return !candSet.isEmpty() && candSet.equals(corrSet);

            case TRUE_FALSE:
                return candidate.trim().equalsIgnoreCase(correct.trim());

            case SHORT_ANSWER:
                if (candidate.trim().isEmpty()) return false;
                List<String> acceptable = new ArrayList<>();
                acceptable.add(correct.trim());
                if (typeData != null && typeData.containsKey("alternatives")) {
                    Object alts = typeData.get("alternatives");
                    if (alts instanceof List) {
                        for (Object a : (List<?>) alts) acceptable.add(String.valueOf(a).trim());
                    } else if (alts instanceof String) {
                        for (String a : ((String) alts).split("[,\\n]")) acceptable.add(a.trim());
                    }
                }
                for (String acc : acceptable) {
                    if (caseSensitive) {
                        if (candidate.trim().equals(acc)) return true;
                    } else {
                        if (candidate.trim().equalsIgnoreCase(acc)) return true;
                    }
                }
                return false;

            case ESSAY:
                // Essay: if manual grading, award completion marks upon submission
                return !candidate.trim().isEmpty();

            case FILL_BLANKS:
                // Supports multiple blanks: q_1_blank_0, q_1_blank_1, etc. or comma-separated
                if (typeData != null && typeData.containsKey("blanks") && typeData.get("blanks") instanceof List) {
                    List<?> blanks = (List<?>) typeData.get("blanks");
                    boolean allBlankCorrect = true;
                    for (int i = 0; i < blanks.size(); i++) {
                        String blankKey = qKey + "_blank_" + i;
                        String userBlank = allAnswers.getOrDefault(blankKey, "");
                        String expectedBlank = String.valueOf(blanks.get(i)).trim();
                        if (!userBlank.trim().equalsIgnoreCase(expectedBlank)) {
                            allBlankCorrect = false;
                            break;
                        }
                    }
                    if (!blanks.isEmpty()) return allBlankCorrect;
                }
                return candidate.trim().equalsIgnoreCase(correct.trim());

            case MATCHING:
                // Matching premise-choice pairs: q_1_match_0 -> expected
                if (typeData != null && typeData.containsKey("pairs") && typeData.get("pairs") instanceof List) {
                    List<?> pairs = (List<?>) typeData.get("pairs");
                    for (int i = 0; i < pairs.size(); i++) {
                        String pairKey = qKey + "_match_" + i;
                        String selected = allAnswers.getOrDefault(pairKey, "");
                        Object pairObj = pairs.get(i);
                        if (pairObj instanceof Map) {
                            String target = String.valueOf(((Map<?, ?>) pairObj).get("right")).trim();
                            if (!selected.trim().equalsIgnoreCase(target)) return false;
                        }
                    }
                    return !pairs.isEmpty();
                }
                return candidate.trim().equalsIgnoreCase(correct.trim());

            case ORDERING:
                // Sequence ordering
                if (typeData != null && typeData.containsKey("items") && typeData.get("items") instanceof List) {
                    List<?> items = (List<?>) typeData.get("items");
                    for (int i = 0; i < items.size(); i++) {
                        String orderKey = qKey + "_order_" + i;
                        String selectedRank = allAnswers.getOrDefault(orderKey, "");
                        if (!String.valueOf(i + 1).equals(selectedRank.trim())) {
                            return false;
                        }
                    }
                    return !items.isEmpty();
                }
                return candidate.trim().equalsIgnoreCase(correct.trim());

            case NUMERICAL:
                try {
                    double candVal = Double.parseDouble(candidate.trim());
                    double corrVal = Double.parseDouble(correct.trim());
                    double tol = tolerance != null && tolerance >= 0 ? tolerance : 0.001;
                    return Math.abs(candVal - corrVal) <= tol;
                } catch (Exception e) {
                    return false;
                }

            case CODE:
                // Safe code writing evaluation: non-empty and contains required logic / passes basic syntax
                if (candidate.trim().isEmpty()) return false;
                if (typeData != null && typeData.containsKey("requiredKeywords")) {
                    String req = String.valueOf(typeData.get("requiredKeywords"));
                    for (String kw : req.split(",")) {
                        if (!kw.trim().isEmpty() && !candidate.contains(kw.trim())) return false;
                    }
                }
                return true;

            default:
                return candidate.trim().equalsIgnoreCase(correct.trim());
        }
    }

    private Set<String> normalizeAnswerSet(String answerStr) {
        Set<String> set = new HashSet<>();
        if (answerStr == null || answerStr.trim().isEmpty()) return set;
        for (String item : answerStr.split("[,;|]")) {
            if (!item.trim().isEmpty()) {
                set.add(item.trim().toUpperCase());
            }
        }
        return set;
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.trim().isEmpty()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
