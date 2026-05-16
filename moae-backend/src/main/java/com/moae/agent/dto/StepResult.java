package com.moae.agent.dto;

import com.moae.enums.FailureReason;
import com.moae.enums.StepStatus;
import lombok.Builder;
import lombok.Getter;

/**
 * Immutable result record for one executed step in the Executor pipeline.
 *
 * Built by ExecutorAgent after each step completes (success or failure).
 * The full List<StepResult> is passed to VerifierAgent for quality scoring.
 *
 * Fields:
 *   stepId        → 1-based ordinal from the Planner's plan
 *   tool          → "github" | "jira" | "slack" | "llm"
 *   action        → "getFile" | "createPR" | "sendMessage" | "generateCode" | etc.
 *   paramsJson    → original step params serialised to JSON string
 *   status        → SUCCESS | FAILED | UNCERTAIN
 *   failureReason → CLIENT_ERROR | SERVER_ERROR | TIMEOUT; null if SUCCESS
 *   resultJson    → API response serialised to JSON string; null on timeout/failure
 *   timeTakenMs   → wall-clock duration in milliseconds
 *   errorMessage  → human-readable failure message; null if SUCCESS
 */
@Getter
@Builder
public class StepResult {

    private int           stepId;
    private String        tool;
    private String        action;
    private String        paramsJson;
    private StepStatus    status;
    private FailureReason failureReason;
    private String        resultJson;
    private long          timeTakenMs;
    private String        errorMessage;
}
