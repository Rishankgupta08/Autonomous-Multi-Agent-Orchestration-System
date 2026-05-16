package com.moae.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Inbound request body for POST /api/integrations/jira.
 *
 * Design rules:
 *   - @Getter only (no @Setter, no @Data) — inbound request DTOs are write-once.
 *   - Jackson deserializes into fields via the @NoArgsConstructor + setter-free
 *     approach: Spring Boot's ObjectMapper uses field-access when no setters exist.
 *   - No JPA annotations — this is a plain inbound DTO, not an entity.
 *
 * Field contract:
 *   domain   → Jira subdomain ONLY (e.g. "myteam", not "myteam.atlassian.net")
 *              IntegrationService constructs the full URL: https://{domain}.atlassian.net
 *   email    → Jira account email used for Basic Auth
 *   apiToken → Jira API token starting with ATATT...
 *              Generated at: https://id.atlassian.com/manage-profile/security/api-tokens
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class JiraConnectRequest {

    /** Jira subdomain only — e.g. "myteam" for myteam.atlassian.net */
    private String domain;

    /** Jira account email used for Basic Auth credential encoding */
    private String email;

    /** Jira API token (ATATT...) used for Basic Auth credential encoding */
    private String apiToken;
}
