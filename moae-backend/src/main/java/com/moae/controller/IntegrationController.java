package com.moae.controller;

import com.moae.dto.IntegrationStatusResponse;
import com.moae.dto.JiraConnectRequest;
import com.moae.dto.SlackConnectRequest;
import com.moae.service.IntegrationService;
import com.moae.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Integration management endpoints — routes requests to IntegrationService.
 *
 * Controller contract (non-negotiable):
 *   - ZERO business logic
 *   - ZERO HTTP calls to external APIs
 *   - ZERO database calls
 *   - Every method calls SessionUtil.getUserId(session) FIRST
 *   - Every method delegates immediately to IntegrationService
 *   - The only conditional allowed: returning 400 vs 200 based on status="error"
 *
 * Security note:
 *   All /api/** endpoints are protected by SecurityConfig (Step 2).
 *   SessionUtil.getUserId() throws 401 automatically if session is invalid,
 *   so no explicit auth check is needed here.
 *
 * Endpoints:
 *   POST   /api/integrations/jira   → connect Jira
 *   DELETE /api/integrations/jira   → disconnect Jira
 *   POST   /api/integrations/slack  → connect Slack
 *   DELETE /api/integrations/slack  → disconnect Slack
 */
@RestController
@RequestMapping("/api/integrations")
@RequiredArgsConstructor
public class IntegrationController {

    private final IntegrationService integrationService;

    // =========================================================================
    // JIRA
    // =========================================================================

    /**
     * Connect a Jira workspace.
     * Validates credentials against /rest/api/3/myself, stores in DB, caches in session.
     *
     * @param request JiraConnectRequest with domain, email, apiToken
     * @param session current HttpSession (session must be valid — enforced by SessionUtil)
     * @return 200 with { status, displayName } on success;
     *         400 with { status, message } on credential failure
     */
    @PostMapping("/jira")
    public ResponseEntity<IntegrationStatusResponse> connectJira(
            @RequestBody JiraConnectRequest request,
            HttpSession session) {

        UUID userId = SessionUtil.getUserId(session);
        IntegrationStatusResponse response = integrationService.connectJira(userId, request, session);

        if ("error".equals(response.getStatus())) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Disconnect the active Jira integration.
     * Soft-deletes the DB row (isActive=false) and removes session cache.
     * Idempotent — safe to call even if not connected.
     *
     * @param session current HttpSession
     * @return 200 with { status: "disconnected" } always
     */
    @DeleteMapping("/jira")
    public ResponseEntity<IntegrationStatusResponse> disconnectJira(HttpSession session) {
        UUID userId = SessionUtil.getUserId(session);
        return ResponseEntity.ok(integrationService.disconnectJira(userId, session));
    }

    // =========================================================================
    // SLACK
    // =========================================================================

    /**
     * Connect a Slack workspace.
     * Validates bot token against https://slack.com/api/auth.test,
     * stores in DB, caches in session.
     *
     * @param request SlackConnectRequest with botToken (xoxb-...)
     * @param session current HttpSession
     * @return 200 with { status, teamName } on success;
     *         400 with { status, message } on credential failure
     */
    @PostMapping("/slack")
    public ResponseEntity<IntegrationStatusResponse> connectSlack(
            @RequestBody SlackConnectRequest request,
            HttpSession session) {

        UUID userId = SessionUtil.getUserId(session);
        IntegrationStatusResponse response = integrationService.connectSlack(userId, request, session);

        if ("error".equals(response.getStatus())) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Disconnect the active Slack integration.
     * Soft-deletes the DB row and removes session cache.
     * Idempotent — safe to call even if not connected.
     *
     * @param session current HttpSession
     * @return 200 with { status: "disconnected" } always
     */
    @DeleteMapping("/slack")
    public ResponseEntity<IntegrationStatusResponse> disconnectSlack(HttpSession session) {
        UUID userId = SessionUtil.getUserId(session);
        return ResponseEntity.ok(integrationService.disconnectSlack(userId, session));
    }
}
