package com.moae.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Encapsulates the five Decision Quality score fields from the workflow_runs table.
 *
 * Nested inside WorkflowDetailDTO as:
 *   "score": { "overall": 94, "taskCompletion": 87, ... }
 *
 * WHY all Integer (boxed, nullable):
 *   While a workflow is RUNNING, all score columns are NULL in the DB.
 *   Using int (primitive) would default to 0 — misleading for in-progress runs.
 *   Using Integer allows the caller to check null and omit the entire DQScoreDTO
 *   from the response (WorkflowDetailDTO uses @JsonInclude NON_NULL on the score field).
 *
 * WHY a separate DTO (not fields on WorkflowDetailDTO):
 *   The SRS API spec defines the response shape as:
 *     { "id": "...", "score": { "overall": 94 }, ... }
 *   A nested "score" object requires a nested DTO.
 *   Inlining the fields on WorkflowDetailDTO would produce a flat structure
 *   that does not match the spec.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DQScoreDTO {
    private Integer overall;             // overallScore — average of the 4 sub-scores
    private Integer taskCompletion;      // % of steps that succeeded (computed, not from Ollama)
    private Integer decisionAccuracy;    // from VerifierAgent/Ollama
    private Integer executionEfficiency; // from VerifierAgent/Ollama
    private Integer contextRelevance;    // from VerifierAgent/Ollama
    private String  summary;             // plain-English paragraph from VerifierAgent
}
