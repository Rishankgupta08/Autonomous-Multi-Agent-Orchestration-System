package com.moae.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moae.dto.IntegrationStatusResponse;
import com.moae.dto.JiraConnectRequest;
import com.moae.dto.SlackConnectRequest;
import com.moae.entity.User;
import com.moae.entity.UserIntegration;
import com.moae.enums.IntegrationType;
import com.moae.repository.UserIntegrationRepository;
import com.moae.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Business logic for connecting and disconnecting Jira and Slack integrations.
 *
 * Responsibilities per connect operation:
 *   1. Validate credentials against the external API (/myself or auth.test).
 *   2. Upsert a UserIntegration row in the DB (update if exists, insert if not).
 *   3. Cache the credential config JSON in HttpSession for fast access by agents.
 *   4. Return a typed IntegrationStatusResponse (never throw to caller).
 *
 * Responsibilities per disconnect operation:
 *   1. Soft-delete the UserIntegration row (isActive = false, row preserved).
 *   2. Remove the cached config from HttpSession.
 *   3. Return status="disconnected" unconditionally (idempotent).
 *
 * RestTemplate usage:
 *   Created locally (new RestTemplate()) inside each method.
 *   Spring Boot 3.x does NOT auto-configure a RestTemplate bean.
 *   A proper @Bean with timeout config is introduced in Step 5.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IntegrationService {

    private final UserIntegrationRepository userIntegrationRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    // =========================================================================
    // JIRA
    // =========================================================================

    /**
     * Validates Jira credentials against Atlassian's /myself endpoint,
     * upserts the integration in the DB, and caches config in the session.
     *
     * @param userId  authenticated user's UUID (from session via SessionUtil)
     * @param request inbound DTO with domain, email, apiToken
     * @param session current HttpSession — receives "jiraConfig" on success
     * @return IntegrationStatusResponse with status="connected" or status="error"
     */
    public IntegrationStatusResponse connectJira(UUID userId,
                                                  JiraConnectRequest request,
                                                  HttpSession session) {
        // Step A — Build the full Jira base URL from the subdomain
        String baseUrl = "https://" + request.getDomain() + ".atlassian.net";

        // Step B — Build Basic Auth header (email:apiToken Base64-encoded)
        String credentials = request.getEmail() + ":" + request.getApiToken();
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + encoded);
        headers.set("Accept", "application/json");

        RestTemplate restTemplate = new RestTemplate();
        String displayName;

        try {
            // Step B — Call /myself to validate credentials and get display name
            ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/rest/api/3/myself",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
            );

            // Step C — Extract display name from the Jira user profile response
            Map<?, ?> body = response.getBody();
            displayName = body != null ? (String) body.get("displayName") : null;

        } catch (RestClientException e) {
            // Step C — Any 4xx/5xx or network error → clean error response (no throw)
            log.warn("Jira credential validation failed for userId={}: {}", userId, e.getMessage());
            return IntegrationStatusResponse.builder()
                .status("error")
                .message("Invalid Jira credentials — check domain, email, and API token")
                .build();
        }

        // Step D — Serialize credentials map to JSON for DB storage
        String configJson;
        try {
            Map<String, String> configMap = Map.of(
                "domain",   request.getDomain(),
                "email",    request.getEmail(),
                "apiToken", request.getApiToken()
            );
            configJson = objectMapper.writeValueAsString(configMap);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize Jira config for userId={}: {}", userId, e.getMessage());
            return IntegrationStatusResponse.builder()
                .status("error")
                .message("Internal error — failed to store integration config")
                .build();
        }

        // Step E — Upsert UserIntegration (update existing or insert new)
        Optional<UserIntegration> existing = userIntegrationRepository
            .findByUserIdAndIntegrationTypeAndIsActiveTrue(userId, IntegrationType.JIRA);

        UserIntegration integration;
        if (existing.isPresent()) {
            // Update the token (rotated credentials or re-connection)
            integration = existing.get();
            integration.setConfigJson(configJson);
            integration.setIsActive(true);
        } else {
            // First-time connection — build a new row
            // getReferenceById returns a JPA proxy (no SELECT) — valid for FK references
            User userRef = userRepository.getReferenceById(userId);
            integration = UserIntegration.builder()
                .user(userRef)
                .integrationType(IntegrationType.JIRA)
                .configJson(configJson)
                .isActive(true)
                .build();
        }
        userIntegrationRepository.save(integration);
        log.info("Jira connected for userId={}, displayName={}", userId, displayName);

        // Step F — Cache config in session for fast access by Executor Agent (Step 5)
        session.setAttribute("jiraConfig", configJson);

        // Step G — Return success response with the user's Jira display name
        return IntegrationStatusResponse.builder()
            .status("connected")
            .displayName(displayName)
            .build();
    }

    /**
     * Soft-deletes the active Jira integration and removes it from the session.
     * Idempotent — safe to call when no integration exists.
     *
     * @param userId  authenticated user's UUID
     * @param session current HttpSession — "jiraConfig" attribute is removed
     * @return IntegrationStatusResponse with status="disconnected"
     */
    public IntegrationStatusResponse disconnectJira(UUID userId, HttpSession session) {
        Optional<UserIntegration> existing = userIntegrationRepository
            .findByUserIdAndIntegrationTypeAndIsActiveTrue(userId, IntegrationType.JIRA);

        if (existing.isPresent()) {
            // Soft delete — row is preserved for audit; only isActive flips to false
            UserIntegration integration = existing.get();
            integration.setIsActive(false);
            userIntegrationRepository.save(integration);
            log.info("Jira disconnected for userId={}", userId);
        }
        // No-op if not connected — disconnecting twice is not an error (idempotent)

        // Remove cached config from session
        session.removeAttribute("jiraConfig");

        return IntegrationStatusResponse.builder()
            .status("disconnected")
            .build();
    }

    // =========================================================================
    // SLACK
    // =========================================================================

    /**
     * Validates the Slack Bot Token via auth.test, upserts the integration in DB,
     * and caches the config in the session.
     *
     * @param userId  authenticated user's UUID
     * @param request inbound DTO with botToken (xoxb-...)
     * @param session current HttpSession — receives "slackConfig" on success
     * @return IntegrationStatusResponse with status="connected" or status="error"
     */
    public IntegrationStatusResponse connectSlack(UUID userId,
                                                   SlackConnectRequest request,
                                                   HttpSession session) {
        // Step A — Build Bearer Auth header for Slack API
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + request.getBotToken());
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        RestTemplate restTemplate = new RestTemplate();
        String teamName;

        try {
            // Step A — Call auth.test to validate the bot token
            ResponseEntity<Map> response = restTemplate.exchange(
                "https://slack.com/api/auth.test",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                Map.class
            );

            // Step B — Slack always returns 200; check the "ok" field in the body
            Map<?, ?> body = response.getBody();
            Boolean ok = body != null ? (Boolean) body.get("ok") : false;

            if (Boolean.TRUE.equals(ok)) {
                // Token is valid — extract workspace name
                teamName = (String) body.get("team");
            } else {
                // Token rejected — extract Slack's error code (e.g. "invalid_auth")
                String slackError = body != null ? (String) body.get("error") : "unknown_error";
                log.warn("Slack token validation failed for userId={}: {}", userId, slackError);
                return IntegrationStatusResponse.builder()
                    .status("error")
                    .message(slackError)
                    .build();
            }

        } catch (RestClientException e) {
            // Network or HTTP error reaching the Slack API
            log.warn("Slack API unreachable for userId={}: {}", userId, e.getMessage());
            return IntegrationStatusResponse.builder()
                .status("error")
                .message("Could not reach Slack API — check network connectivity")
                .build();
        }

        // Step C — Serialize bot token map to JSON for DB storage
        String configJson;
        try {
            Map<String, String> configMap = Map.of("botToken", request.getBotToken());
            configJson = objectMapper.writeValueAsString(configMap);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize Slack config for userId={}: {}", userId, e.getMessage());
            return IntegrationStatusResponse.builder()
                .status("error")
                .message("Internal error — failed to store integration config")
                .build();
        }

        // Step D — Upsert UserIntegration (update existing or insert new)
        Optional<UserIntegration> existing = userIntegrationRepository
            .findByUserIdAndIntegrationTypeAndIsActiveTrue(userId, IntegrationType.SLACK);

        UserIntegration integration;
        if (existing.isPresent()) {
            integration = existing.get();
            integration.setConfigJson(configJson);
            integration.setIsActive(true);
        } else {
            User userRef = userRepository.getReferenceById(userId);
            integration = UserIntegration.builder()
                .user(userRef)
                .integrationType(IntegrationType.SLACK)
                .configJson(configJson)
                .isActive(true)
                .build();
        }
        userIntegrationRepository.save(integration);
        log.info("Slack connected for userId={}, teamName={}", userId, teamName);

        // Step E — Cache config in session for fast access by Executor Agent (Step 5)
        session.setAttribute("slackConfig", configJson);

        // Step F — Return success response with workspace name
        return IntegrationStatusResponse.builder()
            .status("connected")
            .teamName(teamName)
            .build();
    }

    /**
     * Soft-deletes the active Slack integration and removes it from the session.
     * Idempotent — safe to call when no integration exists.
     *
     * @param userId  authenticated user's UUID
     * @param session current HttpSession — "slackConfig" attribute is removed
     * @return IntegrationStatusResponse with status="disconnected"
     */
    public IntegrationStatusResponse disconnectSlack(UUID userId, HttpSession session) {
        Optional<UserIntegration> existing = userIntegrationRepository
            .findByUserIdAndIntegrationTypeAndIsActiveTrue(userId, IntegrationType.SLACK);

        if (existing.isPresent()) {
            UserIntegration integration = existing.get();
            integration.setIsActive(false);
            userIntegrationRepository.save(integration);
            log.info("Slack disconnected for userId={}", userId);
        }
        // Idempotent — no-op if not currently connected

        session.removeAttribute("slackConfig");

        return IntegrationStatusResponse.builder()
            .status("disconnected")
            .build();
    }
}
