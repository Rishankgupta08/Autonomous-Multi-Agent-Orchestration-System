package com.moae.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Request body for POST /api/workflow/{workflowId}/approve-code.
 *
 * The user sends back the (possibly hand-edited) final code they want committed.
 * If the user made no changes in the editor they should send the same code they
 * received from /pending-code or /iterate-code.
 *
 * Multi-file support:
 *   {@code additionalFiles} allows the frontend to include edits the user made to
 *   other files in the repo (opened via GET /{workflowId}/repo-file).  Each entry
 *   is filePath → raw content.  These files are pushed to the same branch as the
 *   primary file immediately before the orchestrator resumes.
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

    /**
     * Optional: additional files the user modified in the IDE panel.
     * Key   = repository-relative file path (e.g. "src/utils.py")
     * Value = full file content (raw, not base-64)
     *
     * These are pushed to the branch immediately upon approval, before the
     * main pipeline resumes. Failures are logged as warnings — they do not
     * abort the approval flow.
     */
    @JsonProperty("additionalFiles")
    private Map<String, String> additionalFiles = new HashMap<>();
}
