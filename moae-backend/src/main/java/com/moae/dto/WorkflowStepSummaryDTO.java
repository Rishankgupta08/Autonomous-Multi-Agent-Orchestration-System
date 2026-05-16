package com.moae.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Lightweight step summary used inside WorkflowHistoryItemDTO.
 *
 * WHY no result_json / params_json:
 *   Those fields can be large (full API responses, code blobs).
 *   The history list is a summary view — it does not need raw payloads.
 *   result_json is available from GET /{id}/steps when the user drills in.
 *
 * WHY String status (not StepStatus enum):
 *   The frontend maps status strings to badge colours.
 *   Using the enum name() directly keeps the DTO decoupled from the enum type
 *   while producing identical JSON ("SUCCESS", "FAILED", "PENDING", "ACTIVE").
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStepSummaryDTO {
    private String tool;    // e.g. "github", "jira", "slack", "llm"
    private String action;  // e.g. "createPR", "sendMessage", "generateCode"
    private String status;  // StepStatus.name() — "SUCCESS" | "FAILED" | "PENDING" | "ACTIVE"
}
