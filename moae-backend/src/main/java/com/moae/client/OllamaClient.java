package com.moae.client;

import com.moae.enums.FailureReason;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class OllamaClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String apiKey;
    private final String defaultModel;
    private final String siteUrl;
    private final String siteName;

    public OllamaClient(
            @Qualifier("ollamaRestTemplate") RestTemplate restTemplate,
            @Value("${moae.openrouter.base-url}") String baseUrl,
            @Value("${moae.openrouter.api-key}") String apiKey,
            @Value("${moae.openrouter.model}") String defaultModel,
            @Value("${moae.openrouter.site-url}") String siteUrl,
            @Value("${moae.openrouter.site-name}") String siteName) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.defaultModel = defaultModel;
        this.siteUrl = siteUrl;
        this.siteName = siteName;
    }

    // ─────────────────────────────────────────────────────────────────
    // PUBLIC API — signatures unchanged; all callers work without edits
    // ─────────────────────────────────────────────────────────────────

    public String generate(String prompt) {
        return generate(prompt, null);
    }

    public String generate(String prompt, String model) {
        String modelToUse = (model != null && !model.isBlank())
                ? model
                : defaultModel;

        log.info("OLLAMA/OPENROUTER GENERATE STARTED | model={}", modelToUse);
        log.debug("OpenRouter generate | model={} | prompt[:100]={}",
                modelToUse,
                prompt.length() > 100 ? prompt.substring(0, 100) + "..." : prompt);

        // Build request body — OpenAI Chat Completions format
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelToUse);
        requestBody.put("messages", List.of(message));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, buildHeaders());

        String url = baseUrl + "/chat/completions";

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Map.class);

            log.info("RAW RESPONSE: {}", response.getBody());

            String content = extractContent(response.getBody());

            log.info("AI CONTENT:\n{}", content);
            log.info("OpenRouter generate complete | model={} | responseLength={}",
                    modelToUse, content.length());

            return content;

        } catch (HttpClientErrorException e) {

            // ── 429 Rate Limit: wait 15s and retry ONCE ──────────────
            if (e.getStatusCode().value() == 429) {
                log.warn("OpenRouter rate limited (429) for model={}. " +
                        "Waiting 15s before retry...", modelToUse);
                try {
                    Thread.sleep(15_000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                try {
                    ResponseEntity<Map> retryResponse = restTemplate.exchange(
                            url, HttpMethod.POST, entity, Map.class);
                    String retryContent = extractContent(retryResponse.getBody());
                    log.info("OpenRouter retry succeeded after 429 | model={} | " +
                            "responseLength={}", modelToUse, retryContent.length());
                    return retryContent;
                } catch (Exception retryEx) {
                    log.error("OpenRouter retry also failed after 429: {}",
                            retryEx.getMessage());
                    // Fall through to throw below
                }
            }

            log.error("OpenRouter client error: {} | body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            log.error("FULL STACKTRACE", e);
            throw new MoaeClientException(
                    "OpenRouter client error: " + e.getStatusCode(),
                    FailureReason.CLIENT_ERROR,
                    e.getStatusCode().value(),
                    e);

        } catch (HttpServerErrorException e) {
            log.error("OpenRouter server error: {} | body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            log.error("FULL STACKTRACE", e);
            throw new MoaeClientException(
                    "OpenRouter server error: " + e.getStatusCode(),
                    FailureReason.SERVER_ERROR,
                    e.getStatusCode().value(),
                    e);

        } catch (ResourceAccessException e) {
            log.error("OpenRouter timeout or unreachable: {}", e.getMessage());
            log.error("FULL STACKTRACE", e);
            throw new MoaeClientException(
                    "OpenRouter timeout — check network or increase read timeout",
                    FailureReason.TIMEOUT,
                    0,
                    e);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        headers.set("HTTP-Referer", siteUrl);
        headers.set("X-Title", siteName);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> body) {
        if (body == null) {
            throw new MoaeClientException(
                    "OpenRouter returned null response body",
                    FailureReason.SERVER_ERROR, 200);
        }
        if (body.containsKey("error")) {
            Map<String, Object> error = (Map<String, Object>) body.get("error");
            String errorMsg = error != null ? (String) error.get("message") : "Provider error";
            Integer errorCode = error != null && error.get("code") instanceof Integer
                    ? (Integer) error.get("code")
                    : 502;
            log.error("OpenRouter provider error: code={}, message={}", errorCode, errorMsg);
            throw new MoaeClientException(
                    "OpenRouter provider error (code=" + errorCode + "): " + errorMsg,
                    FailureReason.SERVER_ERROR,
                    errorCode);
        }

        Object choicesObj = body.get("choices");
        if (choicesObj == null) {
            throw new MoaeClientException(
                    "OpenRouter response missing 'choices' field. Full body: " + body,
                    FailureReason.SERVER_ERROR, 200);
        }

        List<Map<String, Object>> choices = (List<Map<String, Object>>) choicesObj;

        if (choices.isEmpty()) {
            throw new MoaeClientException(
                    "OpenRouter returned empty choices array",
                    FailureReason.SERVER_ERROR, 200);
        }

        Map<String, Object> firstChoice = choices.get(0);
        Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");

        if (message == null) {
            throw new MoaeClientException(
                    "OpenRouter choice missing 'message' field",
                    FailureReason.SERVER_ERROR, 200);
        }

        String content = (String) message.get("content");

        if (content == null || content.isBlank()) {
            throw new MoaeClientException(
                    "OpenRouter returned blank content",
                    FailureReason.SERVER_ERROR, 200);
        }

        return content.trim();
    }
}
