package com.moae.enums;

/**
 * Classifies WHY a WorkflowStep failed.
 * Only populated when StepStatus == FAILED.
 *
 * Stored as VARCHAR STRING in DB (column is nullable when step is not failed).
 *
 * CLIENT_ERROR → HTTP 4xx — bad request, invalid credentials, resource not found.
 *                The Executor sent something wrong; retrying as-is will not help.
 * SERVER_ERROR → HTTP 5xx — the external service (GitHub/Jira/Slack) had an error.
 *                May be worth retrying later.
 * TIMEOUT      → The HTTP call exceeded the configured connection/read timeout.
 *                External service may be down or overloaded.
 */
public enum FailureReason {
    CLIENT_ERROR,
    SERVER_ERROR,
    TIMEOUT
}
