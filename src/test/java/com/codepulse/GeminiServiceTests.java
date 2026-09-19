package com.codepulse;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.codepulse.dto.GeminiReviewResult;
import com.codepulse.service.GeminiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GeminiServiceTests {

    private GeminiService geminiService;
    private RestTemplate mockRestTemplate;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        geminiService = new GeminiService(objectMapper);
        mockRestTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(geminiService, "restTemplate", mockRestTemplate);
        ReflectionTestUtils.setField(geminiService, "model", "gemini-3.6-flash");
        ReflectionTestUtils.setField(geminiService, "baseUrl", "https://generativelanguage.googleapis.com/v1beta");
    }

    @Test
    @DisplayName("1. Missing or blank API key returns fallback review")
    void testMissingApiKeyReturnsFallback() {
        ReflectionTestUtils.setField(geminiService, "apiKey", "");

        GeminiReviewResult result = geminiService.reviewCode("Factorial", "Compute factorial", "code");

        assertNotNull(result);
        assertNull(result.getScore(), "Score must be null when API key is missing");
        assertEquals("AI review is currently unavailable. Your code has been saved successfully.", result.getFeedback());
    }

    @Test
    @DisplayName("2. Successful Gemini API response is correctly parsed into score and feedback")
    void testSuccessfulGeminiResponse() {
        String testApiKey = "TEST_API_KEY_VALID_XYZ";
        ReflectionTestUtils.setField(geminiService, "apiKey", testApiKey);
        ReflectionTestUtils.setField(geminiService, "model", "gemini-3.6-flash");
        ReflectionTestUtils.setField(geminiService, "baseUrl", "https://generativelanguage.googleapis.com/v1beta");

        String geminiJson = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "{\\"score\\": 95, \\"feedback\\": \\"Optimal logic and clear code formatting.\\"}"
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        when(mockRestTemplate.postForEntity(anyString(), any(), any()))
                .thenReturn(new ResponseEntity<>(geminiJson, HttpStatus.OK));

        GeminiReviewResult result = geminiService.reviewCode(
                "Factorial",
                "Compute factorial",
                "public int fact(int n) { return n <= 1 ? 1 : n * fact(n-1); }"
        );

        assertNotNull(result);
        assertEquals(95, result.getScore());
        assertEquals("Optimal logic and clear code formatting.", result.getFeedback());
    }

    @Test
    @DisplayName("3. Gemini API failure returns graceful fallback review")
    void testGeminiApiFailureReturnsFallback() {
        String testApiKey = "SECRET_KEY_99999";
        ReflectionTestUtils.setField(geminiService, "apiKey", testApiKey);
        ReflectionTestUtils.setField(geminiService, "model", "gemini-3.6-flash");

        byte[] errorBody = "{\"error\": {\"code\": 404, \"message\": \"models/gemini-3.6-flash is not found\"}}".getBytes(StandardCharsets.UTF_8);
        HttpClientErrorException notFoundException = HttpClientErrorException.create(
                HttpStatus.NOT_FOUND,
                "Not Found",
                new HttpHeaders(),
                errorBody,
                StandardCharsets.UTF_8
        );

        when(mockRestTemplate.postForEntity(anyString(), any(), any()))
                .thenThrow(notFoundException);

        GeminiReviewResult result = geminiService.reviewCode("Factorial", "Compute factorial", "code");

        assertNotNull(result);
        assertNull(result.getScore(), "Score must be null on API failure");
        assertEquals("AI review is currently unavailable. Your code has been saved successfully.", result.getFeedback());
    }

    @Test
    @DisplayName("4. Verify API key is never logged even when exceptions contain query parameters")
    void testApiKeyIsNeverLogged() {
        String secretApiKey = "SUPER_SECRET_KEY_DO_NOT_LEAK";
        ReflectionTestUtils.setField(geminiService, "apiKey", secretApiKey);
        ReflectionTestUtils.setField(geminiService, "model", "gemini-3.6-flash");

        Logger logger = (Logger) LoggerFactory.getLogger(GeminiService.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        byte[] errorBody = ("Error with key " + secretApiKey).getBytes(StandardCharsets.UTF_8);
        HttpClientErrorException exceptionWithKey = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST,
                "Bad Request with " + secretApiKey,
                new HttpHeaders(),
                errorBody,
                StandardCharsets.UTF_8
        );

        when(mockRestTemplate.postForEntity(anyString(), any(), any()))
                .thenThrow(exceptionWithKey);

        geminiService.reviewCode("Factorial", "Compute factorial", "code");

        for (ILoggingEvent event : listAppender.list) {
            String formattedMessage = event.getFormattedMessage();
            assertFalse(formattedMessage.contains(secretApiKey),
                    "Log message must NEVER contain the secret API key! Found in: " + formattedMessage);
        }

        logger.detachAppender(listAppender);
    }

    @Test
    @DisplayName("5. GeminiService uses the configured model in the request URL")
    void testGeminiServiceUsesConfiguredModel() {
        String configuredModel = "gemini-3.6-flash";
        ReflectionTestUtils.setField(geminiService, "apiKey", "dummy-key");
        ReflectionTestUtils.setField(geminiService, "model", configuredModel);
        ReflectionTestUtils.setField(geminiService, "baseUrl", "https://generativelanguage.googleapis.com/v1beta");

        org.mockito.ArgumentCaptor<String> urlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);

        when(mockRestTemplate.postForEntity(urlCaptor.capture(), any(), any()))
                .thenReturn(new ResponseEntity<>("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"score\\\":80,\\\"feedback\\\":\\\"Good\\\"}\"}]}}]}", HttpStatus.OK));

        geminiService.reviewCode("Title", "Description", "System.out.println(1);");

        String capturedUrl = urlCaptor.getValue();
        assertNotNull(capturedUrl);
        assertTrue(capturedUrl.contains("/models/" + configuredModel + ":generateContent"),
                "Request URL must target configured model: " + capturedUrl);
        assertEquals(configuredModel, geminiService.getModel());
    }

    @Test
    @DisplayName("6. Request format and schema are compatible with Gemini 3.6 Flash structured output")
    @SuppressWarnings("unchecked")
    void testRequestFormatIsCompatibleWithGeminiModel() {
        ReflectionTestUtils.setField(geminiService, "apiKey", "dummy-key");
        ReflectionTestUtils.setField(geminiService, "model", "gemini-3.6-flash");

        org.mockito.ArgumentCaptor<org.springframework.http.HttpEntity> entityCaptor =
                org.mockito.ArgumentCaptor.forClass(org.springframework.http.HttpEntity.class);

        when(mockRestTemplate.postForEntity(anyString(), entityCaptor.capture(), any()))
                .thenReturn(new ResponseEntity<>("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"score\\\":90,\\\"feedback\\\":\\\"Nice\\\"}\"}]}}]}", HttpStatus.OK));

        geminiService.reviewCode("Sum", "Sum description", "return a + b;");

        org.springframework.http.HttpEntity capturedEntity = entityCaptor.getValue();
        assertNotNull(capturedEntity);
        assertEquals(org.springframework.http.MediaType.APPLICATION_JSON, capturedEntity.getHeaders().getContentType());

        java.util.Map<String, Object> body = (java.util.Map<String, Object>) capturedEntity.getBody();
        assertNotNull(body);
        assertTrue(body.containsKey("contents"));
        assertTrue(body.containsKey("generationConfig"));

        java.util.Map<String, Object> genConfig = (java.util.Map<String, Object>) body.get("generationConfig");
        assertEquals("application/json", genConfig.get("responseMimeType"));

        java.util.Map<String, Object> schema = (java.util.Map<String, Object>) genConfig.get("responseSchema");
        assertNotNull(schema);
        assertEquals("OBJECT", schema.get("type"));

        java.util.Map<String, Object> properties = (java.util.Map<String, Object>) schema.get("properties");
        assertNotNull(properties);
        assertTrue(properties.containsKey("score"));
        assertTrue(properties.containsKey("feedback"));

        java.util.List<String> required = (java.util.List<String>) schema.get("required");
        assertTrue(required.contains("score"));
        assertTrue(required.contains("feedback"));
    }
}
