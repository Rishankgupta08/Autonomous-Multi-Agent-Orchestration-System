package com.moae.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Inbound request body for POST /api/integrations/slack.
 *
 * Design rules:
 *   - @Getter only (no @Setter, no @Data) — inbound request DTOs are write-once.
 *   - No JPA annotations — this is a plain inbound DTO, not an entity.
 *
 * Field contract:
 *   botToken → Slack Bot Token starting with xoxb-
 *              Created in the Slack App Dashboard under "OAuth & Permissions".
 *              Validated via POST https://slack.com/api/auth.test before persisting.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SlackConnectRequest {

    /** Slack Bot OAuth token (xoxb-...) obtained from the Slack App Dashboard */
    private String botToken;
}
