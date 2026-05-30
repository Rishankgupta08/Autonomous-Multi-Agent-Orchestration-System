package com.moae.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

/**
 * Response payload for GET /api/workflow/{workflowId}/pending-code.
 *
 * Returned when a workflow is in AWAITING_CODE_REVIEW status. The frontend
 * uses these fields to render the code editor and provide context to the user.
 *
 * @JsonInclude NON_NULL — omits null fields so the JSON stays clean
 *              when optional fields (e.g. branchName) are not yet resolved.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PendingCodeResponse {

    /** The generated source code awaiting the user's approval. */
    private final String code;

    /**
     * Relative file path the code will be written to once approved.
     * Example: "calculator-app/index.html"
     */
    private final String filePath;

    /**
     * Syntax-highlighter language identifier.
     * Example values: "html", "python", "javascript", "java", "css"
     */
    private final String language;

    /**
     * Git branch the approved code will be pushed to.
     * Example: "feature/EC-42-dark-theme"
     */
    private final String branchName;
}
