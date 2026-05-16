package com.moae.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Full per-step detail for GET /api/workflow/{id} (embedded) and
 * GET /api/workflow/{id}/steps (top-level list).
 *
 * Includes result_json and failureReason which are absent from
 * WorkflowStepSummaryDTO (the lightweight history-list version).
 *
 * WHY @JsonInclude(NON_NULL):
 *   failureReason is null for every successful step — no need to return "failureReason": null
 *   resultJson is null for PENDING/ACTIVE/timed-out steps
 *   NON_NULL keeps the response clean and matches the SRS API spec exactly.
 *
 * WHY stepId is int (primitive, not Integer):
 *   stepId is always set — it is a non-null ordinal position (1, 2, 3...).
 *   Unlike score fields, stepId is never null, so primitive is correct here.
 *
 * WHY timeTakenMs is Long (boxed):
 *   PENDING and ACTIVE steps have no duration yet — null is the correct value.
 *   Primitive long would default to 0, which is indistinguishable from "completed in 0ms".
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowStepDetailDTO {
    private int    stepId;        // 1-based ordinal position (always set)
    private String tool;          // "github" | "jira" | "slack" | "llm"
    private String action;        // action name from the Planner's plan
    private String status;        // StepStatus.name()
    private String failureReason; // FailureReason.name(); null if SUCCESS (omitted by NON_NULL)
    private Long   timeTakenMs;   // wall-clock ms; null if PENDING/ACTIVE (omitted by NON_NULL)
    private String resultJson;    // raw API result JSON; null on failure/pending (omitted by NON_NULL)
}
