package com.moae.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /api/workflow/{workflowId}/iterate-code.
 *
 * The user sends a plain-English modification request (e.g. "make it dark themed",
 * "add a clear button", "use Bootstrap"). The backend feeds this prompt plus the
 * current pending code into the LLM and returns the updated version.
 *
 * The updated code is saved back to WorkflowRun.pendingCode — it replaces the
 * previous pending code but does NOT push anything to GitHub.
 */
@Getter
@NoArgsConstructor
public class IterateCodeRequest {

    /**
     * The user's natural-language modification request.
     * Must not be null or blank — validated by WorkflowService.
     */
    @JsonProperty("prompt")
    private String prompt;
    
    // Optional — if null, targets the primary generated file
    // If set, targets a different open file in the IDE session
    @JsonProperty("targetFilePath")
    private String targetFilePath;
}
