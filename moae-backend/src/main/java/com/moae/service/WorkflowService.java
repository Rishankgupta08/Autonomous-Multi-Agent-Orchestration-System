package com.moae.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moae.dto.ApproveCodeRequest;
import com.moae.dto.IterateCodeRequest;
import com.moae.dto.IterateCodeResponse;
import com.moae.dto.PendingCodeResponse;
import com.moae.entity.WorkflowRun;
import com.moae.enums.WorkflowStatus;
import com.moae.repository.WorkflowRunRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.moae.agent.IdeAgent;
import com.moae.client.GitHubClient;
import com.moae.ide.IdeSession;
import com.moae.ide.IdeSessionRegistry;
import com.moae.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

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
    private final ObjectMapper           objectMapper;
    private final IdeAgent               ideAgent;
    private final IdeSessionRegistry     ideSessionRegistry;
    private final GitHubClient           githubClient;
    private final UserRepository         userRepository;

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
    @Transactional
    public IterateCodeResponse iterateCode(UUID workflowRunId, UUID userId,
                                           IterateCodeRequest request) {
        if (request.getPrompt() == null || request.getPrompt().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "prompt field is required and cannot be blank");
        }

        // 1. Load workflow and verify ownership
        WorkflowRun run = workflowRunRepository.findByIdAndUserId(workflowRunId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Workflow not found"));
        
        if (run.getStatus() != WorkflowStatus.AWAITING_CODE_REVIEW) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Workflow is not in code review state");
        }
        
        // 2. Get or validate IdeSession
        IdeSession session = ideSessionRegistry.get(workflowRunId.toString())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "IDE session expired. Please restart the workflow."));
        
        // 3. Determine target file
        // Default: primary file. But if request specifies a different file, use that.
        String targetFile = request.getTargetFilePath() != null 
            ? request.getTargetFilePath() 
            : run.getPendingFilePath();
        
        log.info("WorkflowService.iterateCode | workflowId={} | target={} | " +
                 "prompt='{}' | openFiles={}", 
                 workflowRunId, targetFile, request.getPrompt(),
                 session.getCurrentFiles().size());
        
        // 4. Delegate to IdeAgent
        IdeAgent.IdeModifyResult result = ideAgent.modifyFile(
            workflowRunId.toString(),
            targetFile,
            request.getPrompt()
        );
        
        // 5. If target is the primary file, update DB pending_code
        if (targetFile.equals(run.getPendingFilePath())) {
            run.setPendingCode(result.updatedCode());
            workflowRunRepository.save(run);
        }
        // If target is a secondary file, store in additionalFilesJson
        else {
            updateAdditionalFile(run, targetFile, result.updatedCode());
            workflowRunRepository.save(run);
        }
        
        // 6. Build response message
        String message;
        if (result.charDiff() == 0) {
            message = "⚠️ No changes detected. Try being more specific.";
        } else if (result.charDiff() > 0) {
            message = "✓ Updated " + targetFile.split("/")[targetFile.split("/").length - 1]
                    + " (+" + result.charDiff() + " chars)";
        } else {
            message = "✓ Updated " + targetFile.split("/")[targetFile.split("/").length - 1]
                    + " (" + result.charDiff() + " chars, code simplified)";
        }
        
        return IterateCodeResponse.builder()
                .updatedCode(result.updatedCode())
                .message(message)
                .build();
    }

    // Helper — update or add a file in additionalFilesJson
    private void updateAdditionalFile(WorkflowRun run, String filePath, String content) {
        try {
            Map<String, String> files = new HashMap<>();
            if (run.getAdditionalFilesJson() != null && !run.getAdditionalFilesJson().isBlank()) {
                files = objectMapper.readValue(run.getAdditionalFilesJson(),
                    new TypeReference<>() {});
            }
            files.put(filePath, content);
            run.setAdditionalFilesJson(objectMapper.writeValueAsString(files));
        } catch (JsonProcessingException e) {
            log.error("Failed to update additionalFilesJson: {}", e.getMessage());
        }
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

        // Persist additional files JSON so the orchestrator can push them on resume
        if (request.getAdditionalFiles() != null && !request.getAdditionalFiles().isEmpty()) {
            try {
                String json = objectMapper.writeValueAsString(request.getAdditionalFiles());
                run.setAdditionalFilesJson(json);
                log.info("approveCode | {} additional files approved",
                        request.getAdditionalFiles().size());
            } catch (JsonProcessingException e) {
                log.warn("approveCode | failed to serialize additionalFiles: {}", e.getMessage());
            }
        }

        run.setStatus(WorkflowStatus.RUNNING);  // back to RUNNING while pipeline resumes
        workflowRunRepository.save(run);

        log.info("approveCode | workflowId={} | resumeFromStep={} | codeLength={}",
                workflowRunId, run.getResumeFromStep(), request.getApprovedCode().length());

        // Cleanup IDE session — it has served its purpose
        ideSessionRegistry.remove(workflowRunId.toString());
        log.info("WorkflowService.approveCode | IdeSession cleaned up for workflowId={}", workflowRunId);

        // Hand off to orchestrator — runs @Async on the thread pool
        workflowOrchestrator.resumeFromCodeApproval(workflowRunId, userId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. GET /api/workflow/{workflowId}/repo-file
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, String> getRepoFile(UUID workflowId, UUID userId, String filePath) {
        WorkflowRun run = workflowRunRepository.findByIdAndUserId(workflowId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
        
        // Fetch from GitHub
        String token = userRepository.findById(userId)
            .orElseThrow().getGithubAccessToken();
        
        com.moae.client.dto.GitHubFileResponse fileData = githubClient.getFile(
            run.getPendingOwner(), run.getPendingRepo(), filePath, token
        );
        
        String content = fileData.content();
        String language = detectLanguage(filePath);
        
        // Register in IdeSession so future iterate calls can use it as context
        ideSessionRegistry.get(workflowId.toString()).ifPresent(session -> {
            session.registerFile(filePath, content);
            log.info("WorkflowService.getRepoFile | registered '{}' in IdeSession | {} chars",
                filePath, content.length());
        });
        
        Map<String, String> result = new HashMap<>();
        result.put("content", content);
        result.put("language", language);
        result.put("filePath", filePath);
        return result;
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

    /**
     * Strips markdown code block formatting if the LLM ignored instructions
     * and wrapped the output in ```language ... ```.
     */
    private String stripMarkdown(String text) {
        if (text == null) return null;
        text = text.trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline != -1) {
                text = text.substring(firstNewline + 1);
            } else {
                text = ""; // Just a block with no content
            }
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3).trim();
        }
        return text;
    }
}
