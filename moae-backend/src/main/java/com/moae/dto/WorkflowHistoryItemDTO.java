package com.moae.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One row in the GET /api/workflow/history array response.
 *
 * Contains enough data to render a history table row:
 *   - goal text (truncated in UI if needed)
 *   - status badge (SUCCESS / FAILED / RUNNING)
 *   - overall DQ score (null while RUNNING — boxed Integer)
 *   - createdAt timestamp (ISO-8601 string — directly usable in React)
 *   - lightweight step summaries (for per-step tool/status badges in the row)
 *
 * Does NOT include DQScoreDTO breakdown — that is in GET /{id} only.
 * Does NOT include result_json in steps — too large for a list view.
 *
 * WHY score is Integer (nullable):
 *   A RUNNING workflow has no score yet. Integer allows null.
 *   Returning 0 for in-progress runs would be misleading.
 *
 * WHY createdAt is String:
 *   ISO-8601 string is consumed directly by React without date parsing.
 *   LocalDateTime.toString() produces a valid ISO-8601 string.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowHistoryItemDTO {
    private String                       id;        // WorkflowRun UUID as String
    private String                       goal;      // raw goal text
    private String                       status;    // WorkflowStatus.name()
    private Integer                      score;     // overallScore; null while RUNNING
    private String                       createdAt; // ISO-8601 from LocalDateTime.toString()
    private List<WorkflowStepSummaryDTO> steps;     // lightweight step list
}
