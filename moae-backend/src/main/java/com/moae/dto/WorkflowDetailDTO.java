package com.moae.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Full response body for GET /api/workflow/{id}.
 *
 * Matches the SRS API response shape exactly:
 *   {
 *     "id":          "...",
 *     "goal":        "...",
 *     "status":      "SUCCESS",
 *     "score":       { "overall": 94, "taskCompletion": 87, ... },  ← null if RUNNING
 *     "steps":       [ { "stepId": 1, "tool": "github", ... } ],
 *     "createdAt":   "2025-01-15T10:30:00",
 *     "completedAt": "2025-01-15T10:31:45"  ← absent if RUNNING
 *   }
 *
 * WHY @JsonInclude(NON_NULL):
 *   score is null while the workflow is RUNNING — omit it, not "score": null.
 *   completedAt is null while RUNNING — omit it, not "completedAt": null.
 *   NON_NULL produces a clean response that signals state accurately.
 *
 * WHY both createdAt and completedAt as String:
 *   ISO-8601 strings are consumed directly by the React frontend.
 *   LocalDateTime.toString() produces ISO-8601 without any Jackson config dependency.
 *   Keeps the DTO decoupled from Java time types for easier unit testing.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowDetailDTO {
    private String                      id;          // WorkflowRun UUID as String
    private String                      goal;        // raw natural language goal
    private String                      status;      // WorkflowStatus.name()
    private DQScoreDTO                  score;       // null while RUNNING (omitted by NON_NULL)
    private List<WorkflowStepDetailDTO> steps;       // full step detail list
    private String                      createdAt;   // ISO-8601 from LocalDateTime.toString()
    private String                      completedAt; // null while RUNNING (omitted by NON_NULL)
}
