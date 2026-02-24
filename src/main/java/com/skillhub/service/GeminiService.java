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
    @Value("${gemini.api.key:}")
    private String apiKey;

    public List<Map<String, Object>> generateQuestions(String topic) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return null; // Fallback to mock
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key="
                    + apiKey;

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
                        // Clean markdown if present
                        if (jsonString.startsWith("```json")) {
                            jsonString = jsonString.substring(7);
                        }
                        if (jsonString.startsWith("```")) {
                            jsonString = jsonString.substring(3);
                        }
                        if (jsonString.endsWith("```")) {
                            jsonString = jsonString.substring(0, jsonString.length() - 3);
                        }

                        ObjectMapper mapper = new ObjectMapper();
                        return mapper.readValue(jsonString.trim(), new TypeReference<List<Map<String, Object>>>() {
                        });
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error calling Gemini API: " + e.getMessage());
        }
        return null; // Fallback
    }
}
