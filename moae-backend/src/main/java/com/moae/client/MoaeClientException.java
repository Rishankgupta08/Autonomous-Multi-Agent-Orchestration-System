package com.moae.client;

import com.moae.enums.FailureReason;
import lombok.Getter;

/**
 * Typed exception thrown by all four HTTP clients (GitHub, Jira, Slack, Ollama).
 *
 * WHY A CUSTOM EXCEPTION:
 *   ExecutorAgent (Step 6) catches this in its step-execution loop.
 *   It reads failureReason to persist the correct FailureReason enum value
 *   into the workflow_steps.failure_reason column (CLIENT_ERROR / SERVER_ERROR / TIMEOUT).
 *   Without a typed exception, the agent cannot distinguish a bad-credential error
 *   (CLIENT_ERROR — no point retrying) from a timeout (TIMEOUT — may retry later).
 *
 * Field contract:
 *   failureReason → one of: CLIENT_ERROR (4xx), SERVER_ERROR (5xx), TIMEOUT (network)
 *   httpStatus    → raw HTTP status code as int; 0 when the failure was a timeout
 *                   (no HTTP response was received, so no status code exists)
 *
 * Two constructors:
 *   Without cause → for cases where the error source is fully described in the message
 *   With cause    → used when wrapping a caught RestClientException to preserve the
 *                   original stack trace for debugging
 */
@Getter
public class MoaeClientException extends RuntimeException {

    /** Classifies why the call failed — used by ExecutorAgent to set step.failureReason */
    private final FailureReason failureReason;

    /**
     * Raw HTTP status code from the external API response.
     * 0 when the failure was a network timeout (ResourceAccessException) —
     * no HTTP response was received in that case.
     */
    private final int httpStatus;

    /**
     * Use when the exception message fully describes the failure
     * and no underlying Throwable cause needs to be preserved.
     *
     * @param message       human-readable failure description
     * @param failureReason CLIENT_ERROR | SERVER_ERROR | TIMEOUT
     * @param httpStatus    HTTP status code; 0 for timeouts
     */
    public MoaeClientException(String message, FailureReason failureReason, int httpStatus) {
        super(message);
        this.failureReason = failureReason;
        this.httpStatus = httpStatus;
    }

    /**
     * Use when wrapping a caught RestClientException to preserve the
     * original exception stack trace for debugging.
     *
     * @param message       human-readable failure description
     * @param failureReason CLIENT_ERROR | SERVER_ERROR | TIMEOUT
     * @param httpStatus    HTTP status code; 0 for timeouts
     * @param cause         the original RestClientException (or other Throwable)
     */
    public MoaeClientException(String message, FailureReason failureReason,
                                int httpStatus, Throwable cause) {
        super(message, cause);
        this.failureReason = failureReason;
        this.httpStatus = httpStatus;
    }
}
