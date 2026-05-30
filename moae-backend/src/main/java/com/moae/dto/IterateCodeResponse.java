package com.moae.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Response payload for POST /api/workflow/{workflowId}/iterate-code.
 *
 * The frontend replaces the code in its editor with updatedCode and shows
 * the message to the user. The updated code has already been saved to
 * WorkflowRun.pendingCode in the DB — the user can iterate again or approve.
 */
@Getter
@Builder
public class IterateCodeResponse {

    /**
     * The revised code produced by the LLM.
     * The frontend should replace the current editor content with this value.
     */
    private final String updatedCode;

    /**
     * A short human-readable status message.
     * Example: "Code updated. Review the changes."
     */
    private final String message;
}
