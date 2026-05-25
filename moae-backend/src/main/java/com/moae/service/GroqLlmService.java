package com.moae.service;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Thin wrapper around the Groq Chat Completions API.
 *
 * Three public methods expose three distinct models:
 * plannerCall → mixtral-8x7b-32768 (PlannerAgent)
 * codeGeneratorCall → qwen-qwq-32b (ExecutorAgent / generateCode)
 * verifierCall → llama-3.3-70b-versatile (VerifierAgent)
 *
 * All three delegate to the private {@code callGroq} method which handles
 * HTTP, auth, parsing, and error handling uniformly.
 */
@Service
@Slf4j
public class GroqLlmService {

    private static final String GROQ_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";

    private final RestTemplate restTemplate;
    private final String apiKey;

    public GroqLlmService(@Qualifier("externalApiRestTemplate") RestTemplate restTemplate,
            @Value("${groq.api.key}") String apiKey) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────────

    /** Used by PlannerAgent for plan generation and intent extraction. */
    public String plannerCall(String prompt) {
        return callGroq("qwen/qwen3-32b", prompt);
    }

    /** Used by ExecutorAgent for the generateCode step. */
    public String codeGeneratorCall(String prompt) {
        return callGroq("llama-3.3-70b-versatile", prompt);
    }

    /** Used by VerifierAgent for plan and result verification. */
    public String verifierCall(String prompt) {
        return callGroq("openai/gpt-oss-120b", prompt);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE CORE
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String callGroq(String model, String prompt) {
        String promptPreview = prompt.length() > 100
                ? prompt.substring(0, 100) + "..."
                : prompt;
        log.info("GroqLlmService calling model={} | prompt[:100]={}", model, promptPreview);

        // Build request body (OpenAI Chat Completions format)
        Map<String, Object> message = Map.of("role", "user", "content", prompt);
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(message),
                "temperature", 0.2,
                "max_tokens", 3000);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        int maxRetries = 3;
        long waitMs = 15_000; // 15 s initial wait on 429

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            Map<String, Object> response;
            try {
                response = restTemplate.postForObject(GROQ_ENDPOINT, entity, Map.class);
            } catch (org.springframework.web.client.HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429 && attempt < maxRetries) {
                    log.warn("Groq 429 rate limit on attempt {}/{} for model={} — waiting {}ms before retry",
                            attempt, maxRetries, model, waitMs);
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    waitMs += 10_000; // backoff: 15 s, 25 s, 35 s …
                    continue;
                }
                log.error("GroqLlmService HTTP call failed for model={}: {}", model, e.getMessage(), e);
                throw new RuntimeException("Groq API call failed for model=" + model + ": " + e.getMessage(), e);
            } catch (Exception e) {
                log.error("GroqLlmService HTTP call failed for model={}: {}", model, e.getMessage(), e);
                throw new RuntimeException("Groq API call failed for model=" + model + ": " + e.getMessage(), e);
            }

            if (response == null) {
                throw new RuntimeException("Groq API returned null response for model=" + model);
            }

            // Extract choices[0].message.content
            Object choicesObj = response.get("choices");
            if (choicesObj == null) {
                throw new RuntimeException(
                        "Groq response missing 'choices' field for model=" + model + ". Full body: " + response);
            }

            List<Map<String, Object>> choices = (List<Map<String, Object>>) choicesObj;
            if (choices.isEmpty()) {
                throw new RuntimeException("Groq returned empty choices array for model=" + model);
            }

            Map<String, Object> firstChoice = choices.get(0);
            Map<String, Object> messageObj = (Map<String, Object>) firstChoice.get("message");
            if (messageObj == null) {
                throw new RuntimeException("Groq choice missing 'message' field for model=" + model);
            }

            String content = (String) messageObj.get("content");
            if (content == null || content.isBlank()) {
                throw new RuntimeException("Groq returned blank content for model=" + model);
            }

            log.info("GroqLlmService response received | model={} | responseLength={}", model, content.length());
            return content.trim();
        }

        throw new RuntimeException("Groq API call failed after " + maxRetries + " retries for model=" + model);
    }
}
