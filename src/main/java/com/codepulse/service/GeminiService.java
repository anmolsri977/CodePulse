package com.codepulse.service;

import com.codepulse.dto.GeminiReviewResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiService.class);
    private static final String DEFAULT_FALLBACK_FEEDBACK =
            "AI review is currently unavailable. Your code has been saved successfully.";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-3.6-flash}")
    private String model;

    @Value("${gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String baseUrl;

    public GeminiService(ObjectMapper objectMapper) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        logger.info("GeminiService initialized with model: '{}', baseUrl: '{}'", model, baseUrl);
    }

    public String getModel() {
        return model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public GeminiReviewResult reviewCode(String challengeTitle, String challengeDescription, String studentCode) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            logger.warn("GEMINI_API_KEY environment variable is not configured. Returning fallback feedback.");
            return new GeminiReviewResult(null, DEFAULT_FALLBACK_FEEDBACK);
        }

        String endpointPath = String.format("%s/models/%s:generateContent", baseUrl, model);
        String requestUrl = String.format("%s?key=%s", endpointPath, apiKey);

        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String prompt = buildPrompt(challengeTitle, challengeDescription, studentCode);
                Map<String, Object> requestBody = buildRequestBody(prompt);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

                ResponseEntity<String> response = restTemplate.postForEntity(requestUrl, requestEntity, String.class);
                return parseGeminiResponse(response.getBody());
            } catch (org.springframework.web.client.HttpServerErrorException.ServiceUnavailable e) {
                if (attempt < maxAttempts) {
                    logger.warn("Gemini service temporarily unavailable (503) for model '{}'. Retrying attempt {}/{}...",
                            model, attempt + 1, maxAttempts);
                    try {
                        Thread.sleep(500L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }
                return handleHttpException(e, endpointPath);
            } catch (org.springframework.web.client.HttpStatusCodeException e) {
                return handleHttpException(e, endpointPath);
            } catch (Exception e) {
                return handleGenericException(e, endpointPath);
            }
        }
        return new GeminiReviewResult(null, DEFAULT_FALLBACK_FEEDBACK);
    }

    private GeminiReviewResult handleHttpException(org.springframework.web.client.HttpStatusCodeException e, String endpointPath) {
        String statusText = e.getStatusText() != null ? e.getStatusText() : "";
        String errorBody = e.getResponseBodyAsString() != null ? e.getResponseBodyAsString() : "";
        if (apiKey != null && !apiKey.isEmpty()) {
            if (statusText.contains(apiKey)) {
                statusText = statusText.replace(apiKey, "[REDACTED]");
            }
            if (errorBody.contains(apiKey)) {
                errorBody = errorBody.replace(apiKey, "[REDACTED]");
            }
        }
        logger.warn("Gemini request failed.\nEndpoint: {}\nModel: {}\nStatus: {} {}\nError: {}",
                endpointPath, model, e.getStatusCode(), statusText, errorBody);
        return new GeminiReviewResult(null, DEFAULT_FALLBACK_FEEDBACK);
    }

    private GeminiReviewResult handleGenericException(Exception e, String endpointPath) {
        String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        if (apiKey != null && !apiKey.isEmpty() && message.contains(apiKey)) {
            message = message.replace(apiKey, "[REDACTED]");
        }
        logger.warn("Gemini request failed.\nEndpoint: {}\nModel: {}\nError: {}",
                endpointPath, model, message);
        return new GeminiReviewResult(null, DEFAULT_FALLBACK_FEEDBACK);
    }

    private String buildPrompt(String title, String description, String code) {
        return String.format(
                "You are an automated code evaluation engine. Review the student's submitted code for the following challenge:\n\n" +
                "Challenge Title: %s\n" +
                "Challenge Description: %s\n\n" +
                "Student Submitted Code:\n" +
                "```\n%s\n```\n\n" +
                "Evaluate the code for correctness, logic, edge cases, and style.\n" +
                "Provide a score from 0 to 100 and constructive feedback.",
                title != null ? title : "",
                description != null ? description : "",
                code != null ? code : ""
        );
    }

    private Map<String, Object> buildRequestBody(String prompt) {
        Map<String, Object> textPart = Map.of("text", prompt);
        Map<String, Object> content = Map.of("parts", List.of(textPart));

        // Define response schema for structured JSON output
        Map<String, Object> scoreSchema = Map.of(
                "type", "INTEGER",
                "description", "Evaluation score from 0 to 100"
        );
        Map<String, Object> feedbackSchema = Map.of(
                "type", "STRING",
                "description", "Constructive code review feedback"
        );
        Map<String, Object> properties = Map.of(
                "score", scoreSchema,
                "feedback", feedbackSchema
        );

        Map<String, Object> responseSchema = Map.of(
                "type", "OBJECT",
                "properties", properties,
                "required", List.of("score", "feedback")
        );

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseSchema", responseSchema);

        Map<String, Object> body = new HashMap<>();
        body.put("contents", List.of(content));
        body.put("generationConfig", generationConfig);

        return body;
    }

    private GeminiReviewResult parseGeminiResponse(String responseJson) {
        if (responseJson == null || responseJson.isBlank()) {
            return new GeminiReviewResult(null, DEFAULT_FALLBACK_FEEDBACK);
        }

        try {
            JsonNode rootNode = objectMapper.readTree(responseJson);
            JsonNode candidates = rootNode.path("candidates");
            if (candidates.isEmpty() || !candidates.isArray()) {
                return new GeminiReviewResult(null, DEFAULT_FALLBACK_FEEDBACK);
            }

            JsonNode firstCandidate = candidates.get(0);
            JsonNode parts = firstCandidate.path("content").path("parts");
            if (parts.isEmpty() || !parts.isArray()) {
                return new GeminiReviewResult(null, DEFAULT_FALLBACK_FEEDBACK);
            }

            String contentText = parts.get(0).path("text").asText("");
            if (contentText.isBlank()) {
                return new GeminiReviewResult(null, DEFAULT_FALLBACK_FEEDBACK);
            }

            // Clean markdown fences if present
            String cleanedJson = contentText.trim();
            if (cleanedJson.startsWith("```json")) {
                cleanedJson = cleanedJson.substring(7);
            } else if (cleanedJson.startsWith("```")) {
                cleanedJson = cleanedJson.substring(3);
            }
            if (cleanedJson.endsWith("```")) {
                cleanedJson = cleanedJson.substring(0, cleanedJson.length() - 3);
            }
            cleanedJson = cleanedJson.trim();

            JsonNode parsedOutput = objectMapper.readTree(cleanedJson);

            Integer score = null;
            if (parsedOutput.has("score") && parsedOutput.get("score").isNumber()) {
                int rawScore = parsedOutput.get("score").asInt();
                score = Math.max(0, Math.min(100, rawScore));
            }

            String feedback = null;
            if (parsedOutput.has("feedback") && !parsedOutput.get("feedback").asText().isBlank()) {
                feedback = parsedOutput.get("feedback").asText().trim();
            } else {
                feedback = "Code submitted successfully. Evaluation completed.";
            }

            return new GeminiReviewResult(score, feedback);
        } catch (Exception e) {
            logger.warn("Failed to parse Gemini review JSON response: {}", e.getClass().getSimpleName());
            return new GeminiReviewResult(null, DEFAULT_FALLBACK_FEEDBACK);
        }
    }
}
