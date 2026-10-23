package com.skillhub.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.*;

@Service
public class GeminiService {

    @Value("${gemini.api.key:AIzaSyBxo5TkOi9DTNBC30LXIolUdYeeErLUJsI}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String model;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Robust helper to clean and extract JSON array string from Gemini response.
     */
    private String extractJsonArray(String raw) {
        if (raw == null) return null;
        String cleaned = raw.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();
        int firstBracket = cleaned.indexOf('[');
        int lastBracket = cleaned.lastIndexOf(']');
        if (firstBracket != -1 && lastBracket != -1 && lastBracket > firstBracket) {
            return cleaned.substring(firstBracket, lastBracket + 1);
        }
        return cleaned;
    }

    /**
     * Direct Gemini call implementation from Person 1.
     */
    public List<Map<String, Object>> generateQuestionsDirect(String topic) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return null; // Fallback to mock
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + (model != null ? model : "gemini-2.5-flash") + ":generateContent?key="
                    + apiKey.trim();

            String prompt = "Generate exactly 20 multiple choice questions for a certification exam on the topic of '"
                    + topic + "'. " +
                    "If the topic is about a specific programming language like C or Python, the questions MUST be about that language. "
                    +
                    "Return strictly a JSON array WITHOUT markdown formatting. Each object should have: " +
                    "\"id\" (integer 1-20), \"question\" (string), \"options\" (object with keys a, b, c, d and string values for each option), "
                    +
                    "and \"correctAnswer\" (string matching strictly one of the keys a, b, c, d).";

            Map<String, Object> part = new HashMap<>();
            part.put("text", prompt);

            Map<String, Object> content = new HashMap<>();
            content.put("parts", Collections.singletonList(part));

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", Collections.singletonList(content));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                List<Map> candidates = (List<Map>) response.getBody().get("candidates");
                if (candidates != null && !candidates.isEmpty()) {
                    Map contentObj = (Map) candidates.get(0).get("content");
                    List<Map> parts = (List<Map>) contentObj.get("parts");
                    if (parts != null && !parts.isEmpty()) {
                        String jsonString = (String) parts.get(0).get("text");
                        String cleaned = extractJsonArray(jsonString);

                        ObjectMapper mapper = new ObjectMapper();
                        return mapper.readValue(cleaned.trim(), new TypeReference<List<Map<String, Object>>>() {
                        });
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error calling Gemini API directly: " + e.getMessage());
        }
        return null;
    }

    /**
     * Generates at least 'count' (minimum 20) MCQ questions strictly on the specified topic.
     * Each returned map contains: questionText, optionA, optionB, optionC, optionD, correctOption.
     */
    public List<Map<String, String>> generateMcqQuestions(String topic, int count) {
        int targetCount = Math.max(20, count);
        String effectiveTopic = (topic != null && !topic.trim().isEmpty()) ? topic.trim() : "General Computer Science & IT";

        if (apiKey != null && !apiKey.trim().isEmpty()) {
            try {
                RestTemplate restTemplate = new RestTemplate();
                String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey.trim();

                String prompt = "Generate exactly " + targetCount + " unique, practical multiple choice questions (MCQ) for a certification exam strictly on the topic: '"
                        + effectiveTopic + "'.\n"
                        + "CRITICAL RULES:\n"
                        + "1. Every question MUST be solely and strictly about '" + effectiveTopic + "'. Do NOT mix in other unrelated subjects.\n"
                        + "2. Provide 4 realistic, distinct options labeled optionA, optionB, optionC, optionD.\n"
                        + "3. Provide the single correct option strictly as 'A', 'B', 'C', or 'D'.\n"
                        + "4. Return STRICTLY a valid JSON array of objects without markdown fences. Schema:\n"
                        + "[\n"
                        + "  {\n"
                        + "    \"questionText\": \"...\",\n"
                        + "    \"optionA\": \"...\",\n"
                        + "    \"optionB\": \"...\",\n"
                        + "    \"optionC\": \"...\",\n"
                        + "    \"optionD\": \"...\",\n"
                        + "    \"correctOption\": \"A\"\n"
                        + "  }\n"
                        + "]";

                Map<String, Object> part = new HashMap<>();
                part.put("text", prompt);

                Map<String, Object> content = new HashMap<>();
                content.put("parts", Collections.singletonList(part));

                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("contents", Collections.singletonList(content));

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
                ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    List<Map> candidates = (List<Map>) response.getBody().get("candidates");
                    if (candidates != null && !candidates.isEmpty()) {
                        Map contentObj = (Map) candidates.get(0).get("content");
                        List<Map> parts = (List<Map>) contentObj.get("parts");
                        if (parts != null && !parts.isEmpty()) {
                            String rawText = (String) parts.get(0).get("text");
                            String jsonArrayStr = extractJsonArray(rawText);

                            List<Map<String, Object>> parsedList = objectMapper.readValue(
                                    jsonArrayStr, new TypeReference<List<Map<String, Object>>>() {});

                            List<Map<String, String>> normalized = new ArrayList<>();
                            for (Map<String, Object> rawItem : parsedList) {
                                Map<String, String> item = normalizeQuestionItem(rawItem);
                                if (item != null) {
                                    normalized.add(item);
                                }
                            }

                            if (normalized.size() >= 20) {
                                return normalized;
                            } else if (!normalized.isEmpty()) {
                                // Pad remaining questions using fallback
                                List<Map<String, String>> fallback = generateFallbackQuestions(effectiveTopic, targetCount - normalized.size(), normalized.size() + 1);
                                normalized.addAll(fallback);
                                return normalized;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("GeminiService.generateMcqQuestions error: " + e.getMessage());
            }
        }

        // Fallback generator
        return generateFallbackQuestions(effectiveTopic, targetCount, 1);
    }

    private Map<String, String> normalizeQuestionItem(Map<String, Object> raw) {
        if (raw == null) return null;

        String qText = getStringValue(raw, "questionText", "question", "text");
        if (qText == null || qText.isBlank()) return null;

        String optA = "";
        String optB = "";
        String optC = "";
        String optD = "";
        String correct = "A";

        // Check if options is a nested map
        if (raw.get("options") instanceof Map) {
            Map opts = (Map) raw.get("options");
            optA = opts.get("a") != null ? String.valueOf(opts.get("a")) : (opts.get("A") != null ? String.valueOf(opts.get("A")) : "");
            optB = opts.get("b") != null ? String.valueOf(opts.get("b")) : (opts.get("B") != null ? String.valueOf(opts.get("B")) : "");
            optC = opts.get("c") != null ? String.valueOf(opts.get("c")) : (opts.get("C") != null ? String.valueOf(opts.get("C")) : "");
            optD = opts.get("d") != null ? String.valueOf(opts.get("d")) : (opts.get("D") != null ? String.valueOf(opts.get("D")) : "");
        } else {
            optA = getStringValue(raw, "optionA", "option_a", "optA", "a", "A");
            optB = getStringValue(raw, "optionB", "option_b", "optB", "b", "B");
            optC = getStringValue(raw, "optionC", "option_c", "optC", "c", "C");
            optD = getStringValue(raw, "optionD", "option_d", "optD", "d", "D");
        }

        String rawCorrect = getStringValue(raw, "correctOption", "correctAnswer", "correct_option", "answer");
        if (rawCorrect != null && !rawCorrect.isBlank()) {
            correct = rawCorrect.trim().toUpperCase().substring(0, 1);
            if (!List.of("A", "B", "C", "D").contains(correct)) {
                correct = "A";
            }
        }

        Map<String, String> result = new LinkedHashMap<>();
        result.put("questionText", qText);
        result.put("optionA", optA != null ? optA : "Option A");
        result.put("optionB", optB != null ? optB : "Option B");
        result.put("optionC", optC != null ? optC : "Option C");
        result.put("optionD", optD != null ? optD : "Option D");
        result.put("correctOption", correct);
        return result;
    }

    private String getStringValue(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            if (map.containsKey(k) && map.get(k) != null) {
                return String.valueOf(map.get(k)).trim();
            }
        }
        return null;
    }

    /**
     * Fallback generator strictly focused on topic.
     */
    private List<Map<String, String>> generateFallbackQuestions(String topic, int count, int startIndex) {
        List<Map<String, String>> list = new ArrayList<>();
        String[][] baseBank = {
            {"What is a primary design objective when architecting systems for " + topic + "?", "High maintainability and scalability", "Maximizing technical debt", "Hardcoding dependencies", "Eliminating version control", "A"},
            {"Which standard best practice is fundamental to code quality in " + topic + "?", "Rigorous automated testing and code reviews", "Ignoring compiler warnings", "Deploying untested code to production", "Disabling logging and monitoring", "A"},
            {"In the context of " + topic + ", what role does modular design play?", "It isolates components for reusability and testing", "It couples all modules into a single class", "It increases build failures", "It removes the need for error handling", "A"},
            {"How should sensitive configuration data be managed in " + topic + "?", "Hardcoded into public git repositories", "Through secure environment variables and secrets managers", "Written in plain text comments", "Shared via unencrypted email", "B"},
            {"What does DRY stand for in modern " + topic + " engineering?", "Don't Repeat Yourself", "Do Repeat Yourself", "Design Rapid Yields", "Data Routing Yield", "A"},
            {"Which technique is widely utilized to optimize database query performance in " + topic + "?", "Appropriate indexing and query profiling", "Dropping primary keys", "Running table scans for every request", "Disabling connection pools", "A"},
            {"What is the principal purpose of Continuous Integration (CI) in " + topic + "?", "Automatically build and test code changes on every commit", "Manually deploy code once a year", "Delete obsolete repositories", "Prevent developers from writing unit tests", "A"},
            {"In " + topic + ", what is the benefit of using asynchronous processing?", "Non-blocking execution improves application responsiveness and throughput", "It makes all operations run synchronously", "It guarantees zero memory usage", "It disables multi-threading", "A"},
            {"Which error handling practice is recommended in " + topic + "?", "Catching specific exceptions and logging meaningful error messages", "Catching Throwable and silently suppressing errors", "Terminating the server on any input error", "Printing stack traces directly to end users", "A"},
            {"What is the main advantage of RESTful API design in " + topic + "?", "Standardized stateless HTTP communication with resource-based URIs", "Coupling client and server internal classes", "Using non-standard protocols exclusively", "Restricting data formats to raw binary only", "A"},
            {"Why is input validation critical in " + topic + " applications?", "To prevent security vulnerabilities such as injection attacks", "To slow down application startup", "To increase database storage size", "To bypass authentication mechanisms", "A"},
            {"What does the Single Responsibility Principle (SRP) dictate in " + topic + "?", "A module or class should have one, and only one, reason to change", "Every file must contain at least 1,000 lines of code", "A class must handle database, UI, and business logic simultaneously", "All methods must take zero parameters", "A"},
            {"In " + topic + ", what is the primary benefit of containerization (e.g., Docker)?", "Consistent environment across development, testing, and production", "Replacing all application code with hardware drivers", "Eliminating the need for operating systems", "Restricting deployment to a single computer", "A"},
            {"Which caching strategy is commonly applied in " + topic + " to reduce latency?", "In-memory caching of frequently requested read-heavy data", "Writing cache to floppy disks", "Caching only error responses", "Clearing the database on every read", "A"},
            {"What is the main goal of load balancing in " + topic + " infrastructure?", "Distributing incoming traffic evenly across multiple server instances", "Directing all users to a single server until it crashes", "Increasing server network latency", "Disabling redundant instances", "A"},
            {"How does semantic versioning (SemVer) format releases in " + topic + "?", "MAJOR.MINOR.PATCH", "DATE.TIME.REVISION", "BUILD.ALPHA.BETA", "YEAR.MONTH.DAY", "A"},
            {"In " + topic + ", what does idempotency mean for an API operation?", "Performing the operation multiple times produces the same result as performing it once", "The operation always returns a random status code", "The operation can only ever be called once in history", "The operation deletes database tables", "A"},
            {"What is the primary role of a reverse proxy in " + topic + " architectures?", "Routing client requests, SSL termination, and security filtering", "Running client-side JavaScript in the browser", "Replacing the core relational database", "Generating random user passwords", "A"},
            {"Which metric is crucial for observing the health of a " + topic + " service?", "Latency, traffic, errors, and resource saturation", "The length of developer email addresses", "The monitor screen refresh rate", "The number of comments in config files", "A"},
            {"What is the standard procedure for handling schema migrations in " + topic + "?", "Version-controlled automated migration scripts (e.g., Flyway, Liquibase)", "Manually running unreviewed queries directly in production", "Dropping all tables on every server reboot", "Ignoring database schema changes completely", "A"},
            {"In " + topic + ", what does a message broker (e.g., Kafka, RabbitMQ) provide?", "Decoupled asynchronous communication between microservices", "A replacement for CSS styling", "A tool to compile Java source code", "An image editing platform", "A"},
            {"What is the purpose of rate limiting in " + topic + " web APIs?", "To protect services from abuse, denial of service, and resource exhaustion", "To prevent legitimate users from logging in", "To increase response payload size", "To disable HTTPS encryption", "A"},
            {"In " + topic + ", why are transaction boundaries important in persistence layers?", "To guarantee ACID properties and prevent data inconsistency", "To make every query non-blocking", "To bypass relational foreign keys", "To ensure data is never saved to disk", "A"},
            {"What does OAuth 2.0 primarily handle in modern " + topic + " applications?", "Delegated authorization and secure token exchange", "Compressing image assets", "Writing unit test assertions", "Sorting arrays in memory", "A"}
        };

        for (int i = 0; i < count; i++) {
            int idx = (startIndex - 1 + i) % baseBank.length;
            String[] q = baseBank[idx];
            Map<String, String> item = new LinkedHashMap<>();
            item.put("questionText", (startIndex + i) + ". " + q[0]);
            item.put("optionA", q[1]);
            item.put("optionB", q[2]);
            item.put("optionC", q[3]);
            item.put("optionD", q[4]);
            item.put("correctOption", q[5]);
            list.add(item);
        }
        return list;
    }

    /**
     * Backward-compatible method used by exam services.
     * Uses direct Gemini generation first, falling back to normalized MCQ generation with topic question bank.
     */
    public List<Map<String, Object>> generateQuestions(String topic) {
        List<Map<String, Object>> direct = generateQuestionsDirect(topic);
        if (direct != null && !direct.isEmpty()) {
            return direct;
        }

        List<Map<String, String>> mcqs = generateMcqQuestions(topic, 20);
        List<Map<String, Object>> legacyList = new ArrayList<>();
        int idCounter = 1;
        for (Map<String, String> mcq : mcqs) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", idCounter++);
            map.put("question", mcq.get("questionText"));

            Map<String, String> options = new HashMap<>();
            options.put("a", mcq.get("optionA"));
            options.put("b", mcq.get("optionB"));
            options.put("c", mcq.get("optionC"));
            options.put("d", mcq.get("optionD"));
            map.put("options", options);

            map.put("correctAnswer", mcq.get("correctOption").toLowerCase());
            legacyList.add(map);
        }
        return legacyList;
    }
}
