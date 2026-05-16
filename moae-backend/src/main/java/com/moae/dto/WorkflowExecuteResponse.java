package com.moae.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Outbound response body for POST /api/workflow/execute (HTTP 202 Accepted).
 *
 * Design rules:
 *   - @Getter + @Builder — constructed once in WorkflowController, never mutated.
 *   - No @Setter, no @Data — outbound DTOs are read-only.
 *   - No JPA annotations — plain outbound DTO.
 *
 * Field contract:
 *   workflowId → UUID of the created WorkflowRun, returned as String.
 *                The frontend uses this ID to:
 *                  1. Open SSE: GET /api/workflow/stream/{workflowId}
 *                  2. Poll status (if SSE disconnects): GET /api/workflow/{workflowId}
 *                  3. Display in the history list
 *
 * WHY String instead of UUID:
 *   Jackson serializes UUID to a string by default, but typing it as String here
 *   makes the contract explicit — the frontend treats workflowId as an opaque string.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowExecuteResponse {

    /**
     * UUID of the newly created WorkflowRun, serialized as a hyphenated string.
     * Example: "f47ac10b-58cc-4372-a567-0e02b2c3d479"
     */
    private String workflowId;
}
