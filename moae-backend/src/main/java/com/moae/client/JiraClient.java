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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP adapter for the Jira REST API v3 (Atlassian Cloud).
 *
 * Base URL pattern:
 * https://{domain}.atlassian.net — domain is per-user, always a method
 * parameter.
 *
 * CRITICAL — Atlassian Document Format (ADF):
 * Jira REST API v3 rejects plain strings in the "description" field.
 * All descriptions must use the ADF nested structure (type: "doc", version: 1).
 * Passing a plain string results in HTTP 400 Bad Request.
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
     * Assignee resolution uses Jira's fuzzy {@code /user/assignable/search}
     * endpoint, which matches on display name, email, and username simultaneously.
     * Examples: "Rishank", "rish", "rishank@email.com" all resolve correctly.
     * If resolution fails for any reason, the ticket is created unassigned — this
     * is NOT an error.
     *
     * @param domain              Jira subdomain (e.g. "myteam" →
     *                            myteam.atlassian.net)
     * @param email               Jira account email (used for auth)
     * @param apiToken            Jira API token
     * @param projectKey          Jira project key (e.g. "PROJ")
     * @param summary             one-line issue title
     * @param description         body text (wrapped in ADF by this method)
     * @param assigneeNameOrEmail display name or email to assign; null/blank →
     *                            unassigned
     * @return created issue key
     */
    @SuppressWarnings("unchecked")
    public String createTicket(String domain, String email, String apiToken,
            String projectKey, String summary, String description, String assigneeEmail) {

        // ── Step A: Resolve assignee by email / name (best-effort) ────────────
        String accountId = null;
        if (assigneeEmail != null && !assigneeEmail.isBlank()) {
            accountId = resolveAssigneeAccountId(
                    assigneeEmail, projectKey, domain, email, apiToken);
        }

        // ── Step B: Build fields map ───────────────────────────────────────────
        String url = "https://" + domain + ".atlassian.net/rest/api/3/issue";

        Map<String, Object> textNode = Map.of("type", "text", "text", description);
        Map<String, Object> paragraphNode = Map.of("type", "paragraph", "content", List.of(textNode));
        Map<String, Object> adfDesc = Map.of("type", "doc", "version", 1,
                "content", List.of(paragraphNode));

        Map<String, Object> fields = new HashMap<>();
        fields.put("project", Map.of("key", projectKey));
        fields.put("summary", summary);
        fields.put("issuetype", Map.of("name", "Task"));
        fields.put("description", adfDesc);
        if (accountId != null) {
            fields.put("assignee", Map.of("accountId", accountId));
        }

        Map<String, Object> requestBody = Map.of("fields", fields);

        // ── Step C: Create the ticket ──────────────────────────────────────────
        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, buildJiraHeaders(email, apiToken));
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, Map.class);
            String issueKey = (String) response.getBody().get("key");
            log.info("Jira createTicket: created {} in project {} (assignee={})",
                    issueKey, projectKey,
                    accountId != null ? assigneeEmail : "unassigned");
            return issueKey;
        } catch (HttpClientErrorException e) {
            handleClientError(e, "createTicket");
        } catch (HttpServerErrorException e) {
            handleServerError(e, "createTicket");
        } catch (ResourceAccessException e) {
            handleTimeout(e, "createTicket");
        }

        throw new IllegalStateException("Unreachable — error handlers always throw");
    }

    /**
     * Resolves a display name or email address to a Jira accountId using the
     * {@code /user/assignable/search} endpoint.
     *
     * <p>
     * This endpoint performs fuzzy matching across display name, email address,
     * and Jira username simultaneously. It is scoped to users who can be assigned
     * issues in the given project, so it is both accurate and permission-aware.
     *
     * <p>
     * All exceptions are swallowed — the caller creates the ticket unassigned
     * on any failure. This is intentional: assignee resolution must never block
     * ticket creation.
     *
     * @param nameOrEmail display name or email supplied by the user (e.g.
     *                    "Rishank")
     * @param projectKey  Jira project key used to scope the search (e.g. "EC")
     * @param domain      Jira subdomain
     * @param authEmail   Jira account email for Basic Auth
     * @param apiToken    Jira API token for Basic Auth
     * @return accountId string, or {@code null} if not found or on any error
     */
    private String resolveAssigneeAccountId(String nameOrEmail, String projectKey,
            String domain, String authEmail, String apiToken) {
        String baseUrl = "https://" + domain + ".atlassian.net";
        HttpHeaders headers = buildJiraHeaders(authEmail, apiToken);

        // Step 1 — assignable/search with full input (display names always visible)
        String accountId = searchViaAssignable(nameOrEmail, projectKey, baseUrl, headers);
        if (accountId != null) return accountId;

        // Step 2 — If email, try the username prefix (e.g. "rishankgutpa567")
        if (nameOrEmail.contains("@")) {
            String usernamePart = nameOrEmail.split("@")[0];
            accountId = searchViaAssignable(usernamePart, projectKey, baseUrl, headers);
            if (accountId != null) return accountId;
        }

        // Step 3 — user/picker GDPR-safe fuzzy search
        accountId = searchViaUserPicker(nameOrEmail, baseUrl, headers);
        if (accountId != null) return accountId;

        // Step 4 — Full project member scan, match by displayName
        accountId = searchByDisplayName(nameOrEmail, projectKey, baseUrl, headers);
        if (accountId != null) return accountId;

        log.warn("No assignable Jira user found for '{}' in project={} — ticket will be unassigned",
                nameOrEmail, projectKey);
        return null;
    }

    /**
     * Full project-member scan that matches by {@code displayName} substring.
     * Jira Cloud GDPR privacy hides {@code emailAddress} in all user search
     * responses, but {@code displayName} is always visible. This is the definitive
     * fallback for email-based lookups when all faster steps fail.
     */
    @SuppressWarnings("unchecked")
    private String searchByDisplayName(String nameOrEmail, String projectKey,
            String baseUrl, HttpHeaders headers) {
        try {
            // Extract a searchable name token: strip non-alpha from the username prefix
            // e.g. "rishankgutpa567@gmail.com" → "rishankgutpa"
            String searchName = nameOrEmail.contains("@")
                    ? nameOrEmail.split("@")[0].replaceAll("[^a-zA-Z]", " ").trim()
                    : nameOrEmail;

            int startAt = 0;
            final int PAGE_SIZE = 50;

            while (true) {
                String url = baseUrl + "/rest/api/3/user/assignable/search?project="
                        + URLEncoder.encode(projectKey, StandardCharsets.UTF_8)
                        + "&startAt=" + startAt
                        + "&maxResults=" + PAGE_SIZE;

                ResponseEntity<List> response = restTemplate.exchange(
                        url, HttpMethod.GET,
                        new HttpEntity<>(headers), List.class);

                List<Map<String, Object>> users = response.getBody();
                if (users == null || users.isEmpty()) break;

                for (Map<String, Object> user : users) {
                    String displayName = (String) user.get("displayName");
                    String visibleEmail = (String) user.get("emailAddress"); // may be null (GDPR)

                    boolean emailMatch = visibleEmail != null
                            && visibleEmail.equalsIgnoreCase(nameOrEmail);
                    boolean nameMatch = displayName != null
                            && displayName.toLowerCase().contains(searchName.toLowerCase());

                    if (emailMatch || nameMatch) {
                        String accountId = (String) user.get("accountId");
                        log.info("displayName scan resolved '{}' → accountId={} (displayName='{}')",
                                nameOrEmail, accountId, displayName);
                        return accountId;
                    }
                }

                if (users.size() < PAGE_SIZE) break;
                startAt += PAGE_SIZE;
            }
        } catch (Exception e) {
            log.warn("displayName scan failed for '{}': {}", nameOrEmail, e.getMessage());
        }
        return null;
    }

    /**
     * Exact email lookup via {@code /user/search}. This is the fastest and most
     * precise resolution path when the caller supplies a full email address.
     */
    @SuppressWarnings("unchecked")
    private String searchViaUserSearch(String email, String baseUrl, HttpHeaders headers) {
        try {
            String url = baseUrl + "/rest/api/3/user/search?query="
                    + URLEncoder.encode(email, StandardCharsets.UTF_8)
                    + "&maxResults=5";

            ResponseEntity<List> response = restTemplate.exchange(
                    url, HttpMethod.GET,
                    new HttpEntity<>(headers), List.class);

            List<Map<String, Object>> users = response.getBody();
            if (users != null && !users.isEmpty()) {
                String accountId = (String) users.get(0).get("accountId");
                log.info("user/search resolved '{}' → accountId={}", email, accountId);
                return accountId;
            }
        } catch (Exception e) {
            log.warn("user/search lookup failed for '{}': {}", email, e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String searchViaUserPicker(String query, String baseUrl, HttpHeaders headers) {
        try {
            String url = baseUrl + "/rest/api/3/user/picker?query="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&maxResults=5";

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET,
                    new HttpEntity<>(headers), Map.class);

            List<Map<String, Object>> users = (List<Map<String, Object>>) response.getBody().get("users");
            if (users != null && !users.isEmpty()) {
                String accountId = (String) users.get(0).get("accountId");
                log.info("user/picker resolved '{}' → accountId={}", query, accountId);
                return accountId;
            }
        } catch (Exception e) {
            log.warn("user/picker lookup failed for '{}': {}", query, e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String searchViaAssignable(String query, String projectKey, String baseUrl, HttpHeaders headers) {
        try {
            String url = baseUrl + "/rest/api/3/user/assignable/search?project="
                    + URLEncoder.encode(projectKey, StandardCharsets.UTF_8) + "&query="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&maxResults=5";

            ResponseEntity<List> response = restTemplate.exchange(
                    url, HttpMethod.GET,
                    new HttpEntity<>(headers), List.class);

            List<Map<String, Object>> users = response.getBody();
            if (users != null && !users.isEmpty()) {
                String accountId = (String) users.get(0).get("accountId");
                log.info("assignable/search resolved '{}' → accountId={}", query, accountId);
                return accountId;
            }
        } catch (Exception e) {
            log.warn("assignable/search failed for '{}': {}", query, e.getMessage());
        }
        return null;
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
    @SuppressWarnings("unchecked")
    public Map<String, String> getTransitions(String domain, String email,
            String apiToken, String issueId) {
        String url = "https://" + domain + ".atlassian.net/rest/api/3/issue/"
                + issueId + "/transitions";
        try {
            HttpEntity<Void> entity = new HttpEntity<>(buildJiraHeaders(email, apiToken));
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, Map.class);

            Map<String, Object> body = response.getBody();
            List<Map<String, Object>> transitions = (List<Map<String, Object>>) body.get("transitions");

            Map<String, String> nameToId = new HashMap<>();
            if (transitions != null) {
                for (Map<String, Object> t : transitions) {
                    nameToId.put((String) t.get("name"), (String) t.get("id"));
                }
            }
            log.info("Jira getTransitions: {} available for {}", nameToId.keySet(), issueId);
            return nameToId;

        } catch (HttpClientErrorException e) {
            handleClientError(e, "getTransitions");
        } catch (HttpServerErrorException e) {
            handleServerError(e, "getTransitions");
        } catch (ResourceAccessException e) {
            handleTimeout(e, "getTransitions");
        }
        throw new IllegalStateException("Unreachable");
    }

    public void updateStatus(String domain, String email, String apiToken,
            String issueId, String transitionNameOrId) {

        Map<String, String> availableTransitions = getTransitions(domain, email, apiToken, issueId);
        String resolvedId = availableTransitions.get(transitionNameOrId);

        if (resolvedId == null) {
            try {
                Integer.parseInt(transitionNameOrId.trim());
                resolvedId = transitionNameOrId;
                log.warn("Jira updateStatus: '{}' not found by name — using as raw ID",
                        transitionNameOrId);
            } catch (NumberFormatException e) {
                throw new MoaeClientException(
                        "Unknown Jira transition: '" + transitionNameOrId + "'. " +
                                "Available transitions: " + availableTransitions.keySet(),
                        FailureReason.CLIENT_ERROR, 0);
            }
        }

        // Step 4: Parse to int (Jira requires integer, not string)
        int transitionIdInt = Integer.parseInt(resolvedId.trim());

        String url = "https://" + domain + ".atlassian.net/rest/api/3/issue/"
                + issueId + "/transitions";
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("transition", Map.of("id", transitionIdInt));

        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, buildJiraHeaders(email, apiToken));
            restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            log.info("Jira updateStatus: transitioned {} → '{}' (id={})",
                    issueId, transitionNameOrId, transitionIdInt);
        } catch (HttpClientErrorException e) {
            handleClientError(e, "updateStatus");
        } catch (HttpServerErrorException e) {
            handleServerError(e, "updateStatus");
        } catch (ResourceAccessException e) {
            handleTimeout(e, "updateStatus");
        }
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

        } catch (HttpClientErrorException e) {
            handleClientError(e, "getTicket");
        } catch (HttpServerErrorException e) {
            handleServerError(e, "getTicket");
        } catch (ResourceAccessException e) {
            handleTimeout(e, "getTicket");
        }

        throw new IllegalStateException("Unreachable — error handlers always throw");
    }

    /**
     * Recursively traverses Atlassian Document Format (ADF) nodes to extract plain
     * text.
     */
    private String parseAdfDescription(Map<String, Object> adfNode) {
        if (adfNode == null)
            return "";

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
