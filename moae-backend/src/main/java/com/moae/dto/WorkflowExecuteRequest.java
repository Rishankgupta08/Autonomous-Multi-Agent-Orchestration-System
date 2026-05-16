package com.moae.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Inbound request body for POST /api/workflow/execute.
 *
 * Design rules:
 *   - @Getter only (no @Setter, no @Data) — inbound request DTOs are write-once.
 *   - Jackson deserializes via field access when no setters exist.
 *   - No JPA annotations — plain inbound DTO.
 *
 * Field contract:
 *   goal → plain-English description of what the 3-agent pipeline should accomplish.
 *          Max 590 chars is enforced on the frontend; backend validates not-blank only.
 *          Stored verbatim in workflow_runs.goal (TEXT column — no length limit in DB).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowExecuteRequest {

    private String message;
}
