package com.moae.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Outbound DTO returned by GET /api/auth/me.
 *
 * Design constraints:
 *   - @Getter only (no @Setter, no @Data) — DTOs are read-only outbound objects.
 *   - @Builder — constructed once in AuthController, never mutated.
 *   - No JPA annotations — this is a plain data transfer object, not an entity.
 *   - jiraConnected / slackConnected are computed live from UserIntegrationRepository,
 *     not stored on the User entity, to avoid stale cache issues.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDTO {

    /** User's UUID as a String (avoids UUID serialization quirks in some JSON clients). */
    private String id;

    /** GitHub username (e.g. "octocat"). */
    private String githubLogin;

    /** Full name from GitHub profile (may be null if user has not set it). */
    private String name;

    /** Primary email from GitHub profile (may be null if user has set email to private). */
    private String email;

    /** GitHub profile picture URL. */
    private String avatarUrl;

    /** True if an active Jira integration record exists in user_integrations table. */
    private boolean jiraConnected;

    /** True if an active Slack integration record exists in user_integrations table. */
    private boolean slackConnected;
}
