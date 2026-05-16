package com.moae.client;

import com.moae.enums.FailureReason;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * HTTP adapter for the Slack Web API.
 *
 * CRITICAL — Slack always returns HTTP 200:
 *   Unlike GitHub/Jira, Slack's API returns HTTP 200 even when the call fails.
 *   Errors are indicated by the "ok": false field in the JSON response body.
 *   This client always checks the "ok" field — never relies on HTTP status alone.
 *
 * Credentials:
 *   All methods receive the botToken as a parameter.
 *   ExecutorAgent reads botToken from "slackConfig" JSON in HttpSession.
 *   This client never reads from session or DB.
 */
@Component
@Slf4j
public class SlackClient {

    private static final String SLACK_API_BASE = "https://slack.com/api";

    private final RestTemplate restTemplate;

    public SlackClient(@Qualifier("externalApiRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private HttpHeaders buildSlackHeaders(String botToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(botToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /**
     * Sends a message to a Slack channel.
     *
     * CRITICAL — response body check required:
     *   Slack always returns HTTP 200 OK, even for errors (invalid token, bad channel).
     *   The "ok" field in the response body determines success or failure.
     *   "error" field contains a machine-readable code (e.g. "invalid_auth", "channel_not_found").
     *
     * @param botToken bot OAuth token (xoxb-...)
     * @param channel  channel name (e.g. "#dev-team") or channel ID (e.g. "C1234567")
     * @param text     message text to send
     */
    public void sendMessage(String botToken, String channel, String text) {
        String url = SLACK_API_BASE + "/chat.postMessage";

        Map<String, String> body = Map.of(
            "channel", channel,
            "text",    text
        );

        try {
            HttpEntity<Map<String, String>> entity =
                new HttpEntity<>(body, buildSlackHeaders(botToken));
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, Map.class);

            // SLACK-SPECIFIC: check "ok" field — HTTP 200 does NOT mean success
            Map<?, ?> responseBody = response.getBody();
            Boolean ok = responseBody != null ? (Boolean) responseBody.get("ok") : false;

            if (!Boolean.TRUE.equals(ok)) {
                String errorCode = responseBody != null
                    ? (String) responseBody.get("error")
                    : "unknown_error";
                log.error("Slack API error: {}", errorCode);
                throw new MoaeClientException(
                    "Slack error: " + errorCode,
                    FailureReason.CLIENT_ERROR,
                    200  // HTTP was 200 but the call logically failed
                );
            }

            log.info("Slack sendMessage: sent to channel '{}'", channel);

        } catch (MoaeClientException e) {
            throw e; // re-throw our own exception — don't wrap it again
        } catch (ResourceAccessException e) {
            log.error("Slack API timeout sending to {}: {}", channel, e.getMessage());
            throw new MoaeClientException(
                "Slack API timeout",
                FailureReason.TIMEOUT,
                0,
                e
            );
        }
    }
}
