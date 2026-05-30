package com.moae.service;

import com.moae.dto.ApproveCodeRequest;
import com.moae.dto.IterateCodeRequest;
import com.moae.dto.IterateCodeResponse;
import com.moae.dto.PendingCodeResponse;
import com.moae.entity.WorkflowRun;
import com.moae.enums.WorkflowStatus;
import com.moae.repository.WorkflowRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Business logic for the human-in-the-loop code review flow.
 *
 * This service owns the three operations that happen AFTER ExecutorAgent pauses
 * at a generateCode step and fires a {@code code_review_ready} SSE event:
 *
 *   1. {@link #getPendingCode}   — read the generated code waiting for review
 *   2. {@link #iterateCode}      — user asks the LLM to refine the code; save result
 *   3. {@link #approveCode}      — user approves; update pending_code, then hand off
 *                                  to WorkflowOrchestrator to resume remaining steps
 *
 * Design rules (same as all controllers/services in this project):
 *   - SessionUtil / auth is enforced by the controller BEFORE calling here.
 *   - Ownership is verified here via {@code findByIdAndUserId}.
 *   - Throws {@link ResponseStatusException} for all 4xx conditions so Spring MVC
 *     automatically converts them to the right HTTP status code.
 *   - GroqLlmService is injected (not called from the controller).
 *   - WorkflowOrchestrator is injected for the resume step only.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowService {

    private final WorkflowRunRepository  workflowRunRepository;
    private final GroqLlmService         groqLlmService;
    private final WorkflowOrchestrator   workflowOrchestrator;

    // ─────────────────────────────────────────────────────────────────────────
    // 1. GET /api/workflow/{workflowId}/pending-code
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the generated code waiting for the user's review.
     *
     * Enforces that the workflow:
     *   - exists and belongs to the requesting user (404 if not)
     *   - is currently in AWAITING_CODE_REVIEW status (409 Conflict if not)
     *
     * @param workflowRunId UUID of the WorkflowRun
     * @param userId        UUID of the authenticated user (ownership check)
     * @return PendingCodeResponse with code, filePath, language, branchName
     * @throws ResponseStatusException 404 if run not found / wrong user
     * @throws ResponseStatusException 409 if run is not in AWAITING_CODE_REVIEW
     */
    public PendingCodeResponse getPendingCode(UUID workflowRunId, UUID userId) {
        WorkflowRun run = findOwnedRun(workflowRunId, userId);

        if (run.getStatus() != WorkflowStatus.AWAITING_CODE_REVIEW) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Workflow is not awaiting code review (current status: "
                    + run.getStatus().name() + ")");
        }

        return PendingCodeResponse.builder()
                .code(run.getPendingCode())
                .filePath(run.getPendingFilePath())
                .language(detectLanguage(run.getPendingFilePath()))
                .branchName(run.getPendingBranchName())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. POST /api/workflow/{workflowId}/iterate-code
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Calls the LLM to modify the pending code according to the user's prompt.
     * Saves the updated code back to {@code WorkflowRun.pendingCode} in the DB.
     * Does NOT push anything to GitHub.
     *
     * The user can iterate multiple times — each call replaces pendingCode with
     * the latest revision. When satisfied, they call /approve-code.
     *
     * @param workflowRunId UUID of the WorkflowRun
     * @param userId        UUID of the authenticated user (ownership check)
     * @param request       contains the modification prompt
     * @return IterateCodeResponse with updatedCode and a status message
     * @throws ResponseStatusException 400 if prompt is null or blank
     * @throws ResponseStatusException 404 if run not found / wrong user
     * @throws ResponseStatusException 409 if run is not in AWAITING_CODE_REVIEW
     */
    public IterateCodeResponse iterateCode(UUID workflowRunId, UUID userId,
                                           IterateCodeRequest request) {
        if (request.getPrompt() == null || request.getPrompt().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "prompt field is required and cannot be blank");
        }

        WorkflowRun run = findOwnedRun(workflowRunId, userId);

        if (run.getStatus() != WorkflowStatus.AWAITING_CODE_REVIEW) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Workflow is not awaiting code review (current status: "
                    + run.getStatus().name() + ")");
        }

        String currentCode = run.getPendingCode();
        String filePath    = run.getPendingFilePath() != null ? run.getPendingFilePath() : "file";

        // Build an LLM prompt that provides the full current code as context
        // and asks it to apply only the user's requested change.
        String llmPrompt = """
                You are modifying code based on a user's request.

                Current code (%s):
                ```
                %s
                ```

                User's modification request: %s

                STRICT RULES — follow exactly:
                Return ONLY the complete updated file content.
                No markdown, no backticks, no explanations.
                Preserve all existing functionality unless explicitly told to remove it.
                ZERO placeholder text (no TODO, no YOUR_CODE_HERE, no <...>).
                The code must be complete, not a diff or a partial snippet.
                """.formatted(filePath, currentCode, request.getPrompt());

        log.info("iterateCode | workflowId={} | prompt={}", workflowRunId, request.getPrompt());
        String updatedCode = groqLlmService.codeGeneratorCall(llmPrompt);

        if (isPlaceholder(updatedCode)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "LLM failed to generate valid code, try rephrasing");
        }

        // Persist updated code back to DB so the next call (iterate or approve)
        // always sees the latest version.
        run.setPendingCode(updatedCode);
        workflowRunRepository.save(run);
        log.info("iterateCode | saved updated pendingCode | length={}", updatedCode.length());

        return IterateCodeResponse.builder()
                .updatedCode(updatedCode)
                .message("Code updated. Review the changes.")
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. POST /api/workflow/{workflowId}/approve-code
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Accepts the user's final approved (possibly hand-edited) code, saves it
     * to the DB, then hands off to WorkflowOrchestrator to resume the remaining
     * pipeline steps (pushFile → createPR, etc.).
     *
     * The orchestrator is invoked via {@code resumeFromCodeApproval()} which
     * re-enters the execution loop from {@code resumeFromStep} — the step index
     * saved when execution paused.
     *
     * @param workflowRunId UUID of the WorkflowRun
     * @param userId        UUID of the authenticated user (ownership check)
     * @param request       contains the approved (final) code
     * @throws ResponseStatusException 400 if approvedCode is null or blank
     * @throws ResponseStatusException 404 if run not found / wrong user
     * @throws ResponseStatusException 409 if run is not in AWAITING_CODE_REVIEW
     */
    public void approveCode(UUID workflowRunId, UUID userId, ApproveCodeRequest request) {
        if (request.getApprovedCode() == null || request.getApprovedCode().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "approvedCode field is required and cannot be blank");
        }

        WorkflowRun run = findOwnedRun(workflowRunId, userId);

        if (run.getStatus() != WorkflowStatus.AWAITING_CODE_REVIEW) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Workflow is not awaiting code review (current status: "
                    + run.getStatus().name() + ")");
        }

        // Save the user's final approved code — this is what pushFile will commit.
        // Even if the user edited nothing, we overwrite with what they sent so the
        // source of truth is always the explicitly approved version.
        run.setPendingCode(request.getApprovedCode());
        run.setStatus(WorkflowStatus.RUNNING);  // back to RUNNING while pipeline resumes
        workflowRunRepository.save(run);

        log.info("approveCode | workflowId={} | resumeFromStep={} | codeLength={}",
                workflowRunId, run.getResumeFromStep(), request.getApprovedCode().length());

        // Hand off to orchestrator — runs @Async on the thread pool
        workflowOrchestrator.resumeFromCodeApproval(workflowRunId, userId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Loads a WorkflowRun and enforces ownership.
     * Returns 404 for both "not found" and "found but belongs to another user"
     * — intentionally, to avoid leaking whether a UUID belongs to someone else.
     */
    private WorkflowRun findOwnedRun(UUID workflowRunId, UUID userId) {
        return workflowRunRepository
                .findByIdAndUserId(workflowRunId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Workflow not found"));
    }

    /**
     * Checks if the LLM returned placeholder text instead of real code.
     */
    private boolean isPlaceholder(String code) {
        if (code == null) return true;
        String lower = code.toLowerCase();
        return lower.contains("todo") ||
               lower.contains("your_code_here") ||
               lower.contains("placeholder") ||
               lower.contains("rest of the code") ||
               lower.contains("existing code") ||
               lower.contains("not modified");
    }

    /**
     * Maps a file path extension to a syntax-highlighter language string.
     * Mirrors the logic in ExecutorAgent.detectLanguage() — kept here to avoid
     * a dependency on ExecutorAgent from the service layer.
     */
    private String detectLanguage(String filePath) {
        if (filePath == null) return "plaintext";
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "html";
        if (lower.endsWith(".py"))   return "python";
        if (lower.endsWith(".js"))   return "javascript";
        if (lower.endsWith(".ts"))   return "typescript";
        if (lower.endsWith(".jsx"))  return "javascript";
        if (lower.endsWith(".tsx"))  return "typescript";
        if (lower.endsWith(".java")) return "java";
        if (lower.endsWith(".css"))  return "css";
        if (lower.endsWith(".json")) return "json";
        if (lower.endsWith(".xml"))  return "xml";
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) return "yaml";
        if (lower.endsWith(".md"))   return "markdown";
        if (lower.endsWith(".sh"))   return "shell";
        return "plaintext";
    }
}
