package com.moae.client;

import com.moae.client.dto.JiraTicketResponse;
import com.moae.enums.FailureReason;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * HTTP adapter for the Jira REST API v3 (Atlassian Cloud).
 *
 * Base URL pattern:
 *   https://{domain}.atlassian.net — domain is per-user, always a method parameter.
 *
 * CRITICAL — Atlassian Document Format (ADF):
 *   Jira REST API v3 rejects plain strings in the "description" field.
 *   All descriptions must use the ADF nested structure (type: "doc", version: 1).
 *   Passing a plain string results in HTTP 400 Bad Request.
 *
 * Auth: Basic Auth using Base64(email:apiToken) — NOT OAuth2.
 * Zero session/DB access — credentials always passed as method parameters.
 */
@Component
@Slf4j
public class JiraClient {

    private final RestTemplate restTemplate;

    public JiraClient(@Qualifier("externalApiRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // ── PRIVATE HELPERS ────────────────────────────────────────────────────────

    private HttpHeaders buildJiraHeaders(String email, String apiToken) {
        String credentials = email + ":" + apiToken;
        String encoded = Base64.getEncoder()
            .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + encoded);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private void handleClientError(HttpClientErrorException e, String operation) {
        log.error("Jira {} failed — HTTP {}: {}", operation, e.getStatusCode().value(),
            e.getResponseBodyAsString());
        throw new MoaeClientException(
            "Jira " + operation + " failed: HTTP " + e.getStatusCode().value(),
            FailureReason.CLIENT_ERROR, e.getStatusCode().value(), e);
    }

    private void handleServerError(HttpServerErrorException e, String operation) {
        log.error("Jira {} server error — HTTP {}", operation, e.getStatusCode().value());
        throw new MoaeClientException(
            "Jira server error during " + operation + ": HTTP " + e.getStatusCode().value(),
            FailureReason.SERVER_ERROR, e.getStatusCode().value(), e);
    }

    private void handleTimeout(ResourceAccessException e, String operation) {
        log.error("Jira {} timed out: {}", operation, e.getMessage());
        throw new MoaeClientException(
            "Jira API timeout during " + operation, FailureReason.TIMEOUT, 0, e);
    }

    // ── PUBLIC API ─────────────────────────────────────────────────────────────

    /**
     * Creates a Jira Task issue and returns its issue key (e.g. "PROJ-42").
     *
     * The "description" field uses Atlassian Document Format (ADF) —
     * Jira REST API v3 rejects plain strings; the nested doc/paragraph/text
     * structure is required.
     *
     * @param domain      Jira subdomain (e.g. "myteam" → myteam.atlassian.net)
     * @param email       Jira account email
     * @param apiToken    Jira API token
     * @param projectKey  Jira project key (e.g. "PROJ")
     * @param summary     one-line issue title
     * @param description body text (wrapped in ADF by this method)
     * @return created issue key
     */
    public String createTicket(String domain, String email, String apiToken,
                                String projectKey, String summary, String description) {
        String url = "https://" + domain + ".atlassian.net/rest/api/3/issue";

        Map<String, Object> textNode      = Map.of("type", "text", "text", description);
        Map<String, Object> paragraphNode = Map.of("type", "paragraph", "content", List.of(textNode));
        Map<String, Object> adfDesc       = Map.of("type", "doc", "version", 1,
                                                    "content", List.of(paragraphNode));

        Map<String, Object> fields = Map.of(
            "project",     Map.of("key", projectKey),
            "summary",     summary,
            "issuetype",   Map.of("name", "Task"),
            "description", adfDesc
        );
        Map<String, Object> requestBody = Map.of("fields", fields);

        try {
            HttpEntity<Map<String, Object>> entity =
                new HttpEntity<>(requestBody, buildJiraHeaders(email, apiToken));
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, Map.class);
            String issueKey = (String) response.getBody().get("key");
            log.info("Jira createTicket: created {} in project {}", issueKey, projectKey);
            return issueKey;
        } catch (HttpClientErrorException e) { handleClientError(e, "createTicket"); }
          catch (HttpServerErrorException e) { handleServerError(e, "createTicket"); }
          catch (ResourceAccessException  e) { handleTimeout(e,    "createTicket"); }

        throw new IllegalStateException("Unreachable — error handlers always throw");
    }

    /**
     * Transitions a Jira issue to a new status.
     * Jira returns HTTP 204 No Content on success — no response body to parse.
     *
     * @param domain       Jira subdomain
     * @param email        Jira account email
     * @param apiToken     Jira API token
     * @param issueId      issue key (e.g. "PROJ-42")
     * @param transitionId transition ID from GET /rest/api/3/issue/{id}/transitions
     */
    public void updateStatus(String domain, String email, String apiToken,
                              String issueId, String transitionId) {
        String url = "https://" + domain + ".atlassian.net/rest/api/3/issue/"
            + issueId + "/transitions";

        Map<String, Object> requestBody = Map.of("transition", Map.of("id", transitionId));

        try {
            HttpEntity<Map<String, Object>> entity =
                new HttpEntity<>(requestBody, buildJiraHeaders(email, apiToken));
            restTemplate.exchange(url, HttpMethod.POST, entity, Map.class); // 204 No Content
            log.info("Jira updateStatus: transitioned {} via transition {}", issueId, transitionId);
        } catch (HttpClientErrorException e) { handleClientError(e, "updateStatus"); }
          catch (HttpServerErrorException e) { handleServerError(e, "updateStatus"); }
          catch (ResourceAccessException  e) { handleTimeout(e,    "updateStatus"); }
    }

    /**
     * Fetches a Jira issue and parses its ADF description into plain text.
     *
     * @param domain   Jira subdomain
     * @param email    Jira account email
     * @param apiToken Jira API token
     * @param ticketId issue key or ID
     * @return JiraTicketResponse containing parsed text
     */
    public JiraTicketResponse getTicket(String domain, String email, String apiToken, String ticketId) {
        String url = "https://" + domain + ".atlassian.net/rest/api/3/issue/" + ticketId;

        try {
            HttpEntity<Void> entity = new HttpEntity<>(buildJiraHeaders(email, apiToken));
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            
            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new MoaeClientException("Empty response from Jira for ticket " + ticketId, 
                    FailureReason.SERVER_ERROR, 500, null);
            }

            String issueKey = (String) body.get("key");

            @SuppressWarnings("unchecked")
            Map<String, Object> fields = (Map<String, Object>) body.get("fields");
            if (fields == null) {
                fields = Map.of();
            }

            String summary = (String) fields.get("summary");

            @SuppressWarnings("unchecked")
            Map<String, Object> statusObj = (Map<String, Object>) fields.get("status");
            String status = (statusObj != null) ? (String) statusObj.get("name") : null;

            @SuppressWarnings("unchecked")
            Map<String, Object> assigneeObj = (Map<String, Object>) fields.get("assignee");
            String assignee = (assigneeObj != null) ? (String) assigneeObj.get("displayName") : null;

            @SuppressWarnings("unchecked")
            Map<String, Object> descriptionObj = (Map<String, Object>) fields.get("description");
            String description = parseAdfDescription(descriptionObj);

            log.info("Jira getTicket: fetched {} ({})", issueKey, status);
            return new JiraTicketResponse(issueKey, summary, description, status, assignee);

        } catch (HttpClientErrorException e) { handleClientError(e, "getTicket"); }
          catch (HttpServerErrorException e) { handleServerError(e, "getTicket"); }
          catch (ResourceAccessException  e) { handleTimeout(e,    "getTicket"); }

        throw new IllegalStateException("Unreachable — error handlers always throw");
    }

    /**
     * Recursively traverses Atlassian Document Format (ADF) nodes to extract plain text.
     */
    private String parseAdfDescription(Map<String, Object> adfNode) {
        if (adfNode == null) return "";

        StringBuilder sb = new StringBuilder();
        String type = (String) adfNode.get("type");

        if ("text".equals(type)) {
            String text = (String) adfNode.get("text");
            if (text != null) {
                sb.append(text);
            }
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) adfNode.get("content");
        if (content != null) {
            for (Map<String, Object> child : content) {
                sb.append(parseAdfDescription(child));
            }
        }

        if ("paragraph".equals(type) || (type != null && type.startsWith("heading"))) {
            sb.append("\n\n");
        }

        return sb.toString().trim();
    }
}
