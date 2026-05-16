package com.moae.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Outbound DTO returned by all four integration endpoints:
 *   POST   /api/integrations/jira   → status="connected",  displayName populated
 *   DELETE /api/integrations/jira   → status="disconnected"
 *   POST   /api/integrations/slack  → status="connected",  teamName populated
 *   DELETE /api/integrations/slack  → status="disconnected"
 *   (any validation failure)        → status="error",      message populated
 *
 * Design rules:
 *   - @Getter only (no @Setter, no @Data) — outbound DTOs are read-only.
 *   - @Builder — constructed once in IntegrationService, never mutated.
 *   - @JsonInclude(NON_NULL) — null fields are omitted from JSON output.
 *     This keeps error responses clean (no "displayName": null noise) and
 *     success responses clean (no "message": null noise).
 *   - No JPA annotations — plain outbound DTO.
 *
 * status values:
 *   "connected"    → external API validated, DB upserted, session written
 *   "disconnected" → soft-deleted in DB, removed from session
 *   "error"        → external API rejected the credentials; see message field
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IntegrationStatusResponse {

    /**
     * Outcome of the operation.
     * One of: "connected" | "disconnected" | "error"
     */
    private String status;

    /**
     * Human-readable error description.
     * Only populated when status="error". Null otherwise (omitted from JSON).
     */
    private String message;

    /**
     * Jira user's display name from the /myself endpoint.
     * Only populated on successful Jira connect. Null otherwise (omitted from JSON).
     */
    private String displayName;

    /**
     * Slack workspace name from the auth.test response.
     * Only populated on successful Slack connect. Null otherwise (omitted from JSON).
     */
    private String teamName;
}
