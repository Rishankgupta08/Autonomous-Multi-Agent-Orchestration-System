package com.moae.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /api/workflow/{workflowId}/approve-code.
 *
 * The user sends back the (possibly hand-edited) final code they want committed.
 * If the user made no changes in the editor they should send the same code they
 * received from /pending-code or /iterate-code.
 *
 * Design note: we accept the final code in the request rather than re-reading it
 * from the DB. This gives the frontend complete control — any in-editor tweaks the
 * user made are honoured without a separate save-draft round-trip.
 */
@Getter
@NoArgsConstructor
public class ApproveCodeRequest {

    /**
     * The final, approved source code to commit to GitHub.
     * The backend replaces WorkflowRun.pendingCode with this value before
     * resuming the pipeline, so the pushFile step commits exactly what the
     * user approved.
     *
     * Must not be null or blank — validated by WorkflowService.
     */
    @JsonProperty("approvedCode")
    private String approvedCode;
}
