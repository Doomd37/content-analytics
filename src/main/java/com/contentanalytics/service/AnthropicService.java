package com.contentanalytics.service;

import com.contentanalytics.service.DocumentAnalysisResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnthropicService {

    @Value("${app.anthropic.api-key}")
    private String apiKey;

    @Value("${app.anthropic.api-url:https://api.anthropic.com/v1}")
    private String apiUrl;

    @Value("${app.anthropic.model:claude-3-5-sonnet-20241022}")
    private String model;

    @Value("${app.anthropic.max-tokens:2048}")
    private int maxTokens;

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Analyze document with Claude API
     */
    public DocumentAnalysisResult analyzeDocument(String documentText) {
        log.info("Analyzing document with Claude API (text length: {} chars)", documentText.length());

        try {
            // Prepare analysis prompt
            String prompt = buildAnalysisPrompt(documentText);

            // Call Claude API
            String response = callClaudeAPI(prompt);

            // Parse response
            DocumentAnalysisResult result = parseAnalysisResponse(response);

            log.info("Document analysis complete. Summary length: {}", result.getSummary().length());

            return result;

        } catch (Exception e) {
            log.error("Error analyzing document with Claude API", e);
            throw new RuntimeException("Failed to analyze document", e);
        }
    }

    /**
     * Build analysis prompt for Claude
     */
    private String buildAnalysisPrompt(String documentText) {
        // Truncate very long documents
        String truncatedText = documentText;
        if (documentText.length() > 10000) {
            truncatedText = documentText.substring(0, 10000) + "...[truncated]";
        }

        return """
            You are a document analysis expert. Analyze the following document and provide:
            
            1. A concise summary (2-3 paragraphs)
            2. Key topics (comma-separated list)
            3. Sentiment analysis (POSITIVE, NEUTRAL, or NEGATIVE)
            4. Confidence score (0.0-1.0)
            
            Document:
            ---
            """ + truncatedText + """
            ---
            
            Provide your response in the following JSON format:
            {
              "summary": "...",
              "keyTopics": "topic1, topic2, topic3",
              "sentiment": "NEUTRAL",
              "confidenceScore": 0.85
            }
            
            Respond ONLY with valid JSON, no additional text.
            """;
    }

    /**
     * Call Claude API
     */
    private String callClaudeAPI(String prompt) throws IOException {
        log.debug("Calling Claude API with model: {}", model);

        // Build request body
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("messages", List.of(
                Map.of(
                        "role", "user",
                        "content", prompt
                )
        ));

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        // Build HTTP request
        RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json"));

        Request request = new Request.Builder()
                .url(apiUrl + "/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .post(body)
                .build();

        // Execute request
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                log.error("Claude API error: {} - {}", response.code(), errorBody);
                throw new RuntimeException("Claude API error: " + response.code());
            }

            String responseBody = response.body().string();
            log.debug("Claude API response received (length: {} chars)", responseBody.length());

            // Extract text from response
            return extractTextFromResponse(responseBody);
        }
    }

    /**
     * Extract text content from Claude API response
     */
    private String extractTextFromResponse(String responseJson) throws IOException {
        Map<String, Object> response = objectMapper.readValue(responseJson, Map.class);

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        if (content == null || content.isEmpty()) {
            throw new RuntimeException("No content in Claude API response");
        }

        return (String) content.get(0).get("text");
    }

    /**
     * Parse Claude API response into DocumentAnalysisResult
     */
    private DocumentAnalysisResult parseAnalysisResponse(String response) {
        try {
            // Extract JSON from response (handle markdown code blocks)
            String jsonContent = response;
            if (response.contains("```json")) {
                jsonContent = response.substring(
                        response.indexOf("```json") + 7,
                        response.lastIndexOf("```")
                ).trim();
            } else if (response.contains("```")) {
                jsonContent = response.substring(
                        response.indexOf("```") + 3,
                        response.lastIndexOf("```")
                ).trim();
            }

            // Parse JSON
            Map<String, Object> parsed = objectMapper.readValue(jsonContent, Map.class);

            return DocumentAnalysisResult.builder()
                    .summary((String) parsed.get("summary"))
                    .keyTopics((String) parsed.get("keyTopics"))
                    .sentiment((String) parsed.get("sentiment"))
                    .confidenceScore(((Number) parsed.get("confidenceScore")).floatValue())
                    .build();

        } catch (Exception e) {
            log.error("Error parsing Claude API response", e);
            // Return default result on parse error
            return DocumentAnalysisResult.builder()
                    .summary("Analysis could not be completed")
                    .keyTopics("N/A")
                    .sentiment("NEUTRAL")
                    .confidenceScore(0f)
                    .build();
        }
    }

}
