package com.moae.enums;

/**
 * The external integration service a UserIntegration record represents.
 *
 * Stored as VARCHAR STRING in DB.
 *
 * JIRA  → Atlassian Jira; config_json contains {domain, email, apiToken}
 * SLACK → Slack workspace; config_json contains {botToken}
 *
 * Note: GitHub OAuth is handled separately via Spring Security (Phase 2),
 * NOT via this enum. GitHub credentials are stored on the User entity itself.
 */
public enum IntegrationType {
    JIRA,
    SLACK
}
