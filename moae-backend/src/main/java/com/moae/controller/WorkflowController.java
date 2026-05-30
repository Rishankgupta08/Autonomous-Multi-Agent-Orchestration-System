package com.moae.controller;

import com.moae.dto.DQScoreDTO;
import com.moae.dto.MergeConfirmRequest;
import com.moae.dto.ApproveCodeRequest;
import com.moae.dto.IterateCodeRequest;
import com.moae.dto.IterateCodeResponse;
import com.moae.dto.PendingCodeResponse;
import com.moae.dto.WorkflowDetailDTO;
import com.moae.dto.WorkflowExecuteRequest;
import com.moae.dto.WorkflowExecuteResponse;
import com.moae.dto.WorkflowHistoryItemDTO;
import com.moae.dto.WorkflowStepDetailDTO;
import com.moae.dto.WorkflowStepSummaryDTO;
import com.moae.entity.User;
import com.moae.entity.WorkflowRun;
import com.moae.entity.WorkflowStep;
import com.moae.enums.WorkflowStatus;
import com.moae.repository.UserRepository;
import com.moae.repository.WorkflowRunRepository;
import com.moae.repository.WorkflowStepRepository;
import com.moae.service.MergeConfirmService;
import com.moae.service.WorkflowOrchestrator;
import com.moae.service.WorkflowService;
import com.moae.sse.SseEmitterRegistry;
import com.moae.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Workflow trigger, SSE stream, history, and detail endpoints.
 *
 * Controller contract:
 *   - ZERO business logic — all reads go directly to repositories (thin reads only)
 *   - ZERO agent calls
 *   - Every method calls SessionUtil.getUserId(session) FIRST for authentication
 *   - Write operations delegate to WorkflowOrchestrator
 *   - Read operations query repositories directly (no service layer needed for simple reads)
 *
 * Endpoints:
 *   POST /api/workflow/execute          → (Step 4) creates WorkflowRun, fires @Async pipeline
 *   GET  /api/workflow/stream/{id}      → (Step 4) opens SSE channel for the given workflowId
 *   GET  /api/workflow/history          → (Step 7) returns user's workflow history, newest first
 *   GET  /api/workflow/{id}             → (Step 7) returns full detail for one workflow run
 *   GET  /api/workflow/{id}/steps       → (Step 7) returns ordered step breakdown for one run
 *
 * Security note on 404 vs 403:
 *   All ownership-enforced queries use findByIdAndUserId which returns empty for both
 *   "not found" and "found but not yours". The controller returns 404 in both cases —
 *   intentionally, to avoid leaking whether a UUID belongs to another user.
 */
@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
@Slf4j
public class WorkflowController {

    private final WorkflowRunRepository  workflowRunRepository;
    private final WorkflowStepRepository workflowStepRepository;
    private final WorkflowOrchestrator   workflowOrchestrator;
    private final SseEmitterRegistry     sseEmitterRegistry;
    private final UserRepository         userRepository;
    private final MergeConfirmService    mergeConfirmService;
    private final WorkflowService        workflowService;
    private final com.moae.client.GitHubClient gitHubClient;

    // ─────────────────────────────────────────────────────────────────────────
    // STEP 4: POST /api/workflow/execute
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Accepts a workflow goal, creates a WorkflowRun in RUNNING state,
     * fires the @Async pipeline, and immediately returns 202 Accepted.
     *
     * HTTP 202: "accepted for processing but not yet complete".
     * The frontend must open the SSE stream to receive progress events.
     *
     * @param request WorkflowExecuteRequest with a non-blank goal string
     * @param session current HttpSession — validated for authentication
     * @return 202 with { workflowId } | 400 on blank goal | 401 on no session
     */
    @PostMapping("/execute")
    public ResponseEntity<?> execute(
            @RequestBody WorkflowExecuteRequest request,
            HttpSession session) {

        // Validation
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "message field is required and cannot be blank"));
        }

        // Session
        UUID userId = SessionUtil.getUserId(session);

        // Create WorkflowRun in DB
        WorkflowRun run = WorkflowRun.builder()
            .user(userRepository.getReferenceById(userId))
            .goal(request.getMessage())          // store raw message as the goal
            .status(WorkflowStatus.RUNNING)
            .build();
        WorkflowRun savedRun = workflowRunRepository.save(run);
        String workflowId = savedRun.getId().toString();

        // Emit initial SSE registration (so frontend can connect immediately)
        // This is already handled by SseEmitterRegistry — no change needed here

        // Fire async pipeline
        workflowOrchestrator.executeWorkflow(
            workflowId,
            request.getMessage(),   // pass raw message — Planner extracts intent
            userId
        );

        return ResponseEntity.accepted()
            .body(Map.of("workflowId", workflowId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STEP 4: GET /api/workflow/stream/{workflowId}
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Opens a Server-Sent Events channel for the given workflowId.
     *
     * The frontend calls this immediately after receiving the 202 from /execute.
     * Returns SseEmitter directly — Spring MVC handles the SSE handshake automatically.
     *
     * @param workflowId UUID string from the 202 response body
     * @param session    current HttpSession — validated for authentication
     * @return SseEmitter connected to the WorkflowOrchestrator's event stream
     */
    @GetMapping("/stream/{workflowId}")
    public SseEmitter streamWorkflow(
            @PathVariable String workflowId,
            HttpSession session) {

        SessionUtil.getUserId(session);

        UUID id;
        try {
            id = UUID.fromString(workflowId);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid workflowId format in SSE stream request: '{}'", workflowId);
            SseEmitter dead = new SseEmitter(0L);
            dead.complete();
            return dead;
        }

        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L); // 30-minute timeout

        emitter.onTimeout(() -> {
            log.debug("SSE timeout for workflowId={}", workflowId);
            sseEmitterRegistry.remove(UUID.fromString(workflowId));
        });
        emitter.onCompletion(() -> {
            sseEmitterRegistry.remove(UUID.fromString(workflowId));
        });
        emitter.onError(e -> {
            log.debug("SSE error for workflowId={}: {}", workflowId, e.getMessage());
            sseEmitterRegistry.remove(UUID.fromString(workflowId));
        });

        sseEmitterRegistry.register(id, emitter);
        return emitter;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STEP 7: GET /api/workflow/history
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns all workflow runs for the current user, newest first.
     *
     * For each run, fetches the step list to populate the lightweight summary.
     * N+1 queries intentional for Phase 1 (one extra query per run for steps).
     * Phase 2 would optimise this with a JOIN or batch fetch.
     *
     * Always returns 200 — an empty list is valid (new users have no history).
     *
     * @param session current HttpSession — validated for authentication
     * @return 200 with List<WorkflowHistoryItemDTO>; empty list if no history
     */
    @GetMapping("/history")
    public ResponseEntity<List<WorkflowHistoryItemDTO>> getHistory(HttpSession session) {

        UUID userId = SessionUtil.getUserId(session);

        List<WorkflowRun> runs =
                workflowRunRepository.findByUserIdOrderByCreatedAtDesc(userId);

        List<WorkflowHistoryItemDTO> result = runs.stream().map(run -> {

            List<WorkflowStep> steps =
                    workflowStepRepository.findByWorkflowRunIdOrderByStepIdAsc(run.getId());

            List<WorkflowStepSummaryDTO> stepSummaries = steps.stream()
                    .map(step -> WorkflowStepSummaryDTO.builder()
                            .tool(step.getTool())
                            .action(step.getAction())
                            .status(step.getStatus().name())
                            .build())
                    .collect(Collectors.toList());

            return WorkflowHistoryItemDTO.builder()
                    .id(run.getId().toString())
                    .goal(run.getGoal())
                    .status(run.getStatus().name())
                    .score(run.getOverallScore())
                    .createdAt(run.getCreatedAt() != null
                            ? run.getCreatedAt().toString() : null)
                    .steps(stepSummaries)
                    .build();

        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STEP 7: GET /api/workflow/{workflowId}
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns full detail for a single workflow run.
     *
     * Ownership enforced via findByIdAndUserId — returns 404 for both
     * "not found" and "found but belongs to another user" (no 403 leakage).
     *
     * score field is null (and omitted from JSON) while the run is RUNNING.
     * completedAt field is null (and omitted from JSON) while RUNNING.
     *
     * @param workflowId UUID string of the workflow run
     * @param session    current HttpSession — validated for authentication
     * @return 200 WorkflowDetailDTO | 400 invalid UUID | 404 not found/wrong user
     */
    @GetMapping("/{workflowId}")
    public ResponseEntity<?> getWorkflowDetail(
            @PathVariable String workflowId,
            HttpSession session) {

        UUID userId = SessionUtil.getUserId(session);

        UUID runId;
        try {
            runId = UUID.fromString(workflowId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid workflowId format"));
        }

        Optional<WorkflowRun> runOpt =
                workflowRunRepository.findByIdAndUserId(runId, userId);

        if (runOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        WorkflowRun run = runOpt.get();

        List<WorkflowStep> steps =
                workflowStepRepository.findByWorkflowRunIdOrderByStepIdAsc(runId);

        List<WorkflowStepDetailDTO> stepDetails = steps.stream()
                .map(step -> WorkflowStepDetailDTO.builder()
                        .stepId(step.getStepId())
                        .tool(step.getTool())
                        .action(step.getAction())
                        .status(step.getStatus().name())
                        .failureReason(step.getFailureReason() != null
                                ? step.getFailureReason().name() : null)
                        .timeTakenMs(step.getTimeTakenMs())
                        .resultJson(step.getResultJson())
                        .build())
                .collect(Collectors.toList());

        // DQScoreDTO is null (and omitted by @JsonInclude NON_NULL) while RUNNING
        DQScoreDTO scoreDTO = null;
        if (run.getOverallScore() != null) {
            scoreDTO = DQScoreDTO.builder()
                    .overall(run.getOverallScore())
                    .taskCompletion(run.getTaskCompletion())
                    .decisionAccuracy(run.getDecisionAccuracy())
                    .executionEfficiency(run.getExecutionEfficiency())
                    .contextRelevance(run.getContextRelevance())
                    .summary(run.getScoreSummary())
                    .build();
        }

        WorkflowDetailDTO detail = WorkflowDetailDTO.builder()
                .id(run.getId().toString())
                .goal(run.getGoal())
                .status(run.getStatus().name())
                .score(scoreDTO)
                .steps(stepDetails)
                .createdAt(run.getCreatedAt() != null
                        ? run.getCreatedAt().toString() : null)
                .completedAt(run.getCompletedAt() != null
                        ? run.getCompletedAt().toString() : null)
                .build();

        return ResponseEntity.ok(detail);
    }

    @PostMapping("/{workflowId}/confirm-merge")
    public ResponseEntity<?> confirmMerge(
            @PathVariable String workflowId,
            @RequestBody MergeConfirmRequest request,
            HttpSession session) {

        UUID userId = SessionUtil.getUserId(session);
        UUID wfId   = UUID.fromString(workflowId);

        Map<String, Object> result = mergeConfirmService.confirmMerge(
                wfId, userId, request.isMerged());

        return ResponseEntity.ok(result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STEP 7: GET /api/workflow/{workflowId}/steps
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the ordered step breakdown for a single workflow run.
     *
     * Ownership verified first via findByIdAndUserId before fetching steps.
     * Returns 404 if the run doesn't exist or belongs to another user.
     *
     * WHY a separate endpoint when /{id} already includes steps:
     *   /{id} returns run metadata + DQ score + steps — for the full result screen.
     *   /{id}/steps returns ONLY the steps array — for a lightweight step-only view,
     *   debugging, or future paginated step browsing.
     *
     * @param workflowId UUID string of the workflow run
     * @param session    current HttpSession — validated for authentication
     * @return 200 List<WorkflowStepDetailDTO> ordered by stepId ASC | 400 | 404
     */
    @GetMapping("/{workflowId}/steps")
    public ResponseEntity<?> getWorkflowSteps(
            @PathVariable String workflowId,
            HttpSession session) {

        UUID userId = SessionUtil.getUserId(session);

        UUID runId;
        try {
            runId = UUID.fromString(workflowId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid workflowId format"));
        }

        // Ownership check — returns 404 for both "not found" and "wrong user"
        boolean owned = workflowRunRepository.findByIdAndUserId(runId, userId).isPresent();
        if (!owned) {
            return ResponseEntity.notFound().build();
        }

        List<WorkflowStep> steps =
                workflowStepRepository.findByWorkflowRunIdOrderByStepIdAsc(runId);

        List<WorkflowStepDetailDTO> stepDetails = steps.stream()
                .map(step -> WorkflowStepDetailDTO.builder()
                        .stepId(step.getStepId())
                        .tool(step.getTool())
                        .action(step.getAction())
                        .status(step.getStatus().name())
                        .failureReason(step.getFailureReason() != null
                                ? step.getFailureReason().name() : null)
                        .timeTakenMs(step.getTimeTakenMs())
                        .resultJson(step.getResultJson())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(stepDetails);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CODE REVIEW ENDPOINTS
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/{workflowId}/pending-code")
    public ResponseEntity<PendingCodeResponse> getPendingCode(
            @PathVariable String workflowId,
            HttpSession session) {
        UUID userId = SessionUtil.getUserId(session);
        UUID wfId;
        try {
            wfId = UUID.fromString(workflowId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(workflowService.getPendingCode(wfId, userId));
    }

    @PostMapping("/{workflowId}/iterate-code")
    public ResponseEntity<IterateCodeResponse> iterateCode(
            @PathVariable String workflowId,
            @RequestBody IterateCodeRequest request,
            HttpSession session) {
        UUID userId = SessionUtil.getUserId(session);
        UUID wfId;
        try {
            wfId = UUID.fromString(workflowId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(workflowService.iterateCode(wfId, userId, request));
    }

    @PostMapping("/{workflowId}/approve-code")
    public ResponseEntity<?> approveCode(
            @PathVariable String workflowId,
            @RequestBody ApproveCodeRequest request,
            HttpSession session) {
        UUID userId = SessionUtil.getUserId(session);
        UUID wfId;
        try {
            wfId = UUID.fromString(workflowId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        workflowService.approveCode(wfId, userId, request);
        return ResponseEntity.accepted().body(Map.of("message", "Code approved. Resuming workflow..."));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CODE REVIEW — GET /{workflowId}/repo-file
    // Fetches the current content of any file in the repo so the user can
    // open and edit additional files in the IDE panel during code review.
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the decoded content of a single file from the GitHub repository
     * linked to the paused workflow.
     *
     * Security:
     *   - Session required (getUserId throws 401 if no session).
     *   - Ownership checked via run.getUserId().equals(userId) — 403 if mismatch.
     *   - Only available while workflow is AWAITING_CODE_REVIEW — 409 otherwise.
     *
     * @param workflowId UUID string of the paused WorkflowRun
     * @param filePath   repo-relative path of the file to fetch (e.g. "src/utils.py")
     * @param session    current HttpSession
     * @return 200 { content, language, filePath } | 403 | 404 | 409
     */
    @GetMapping("/{workflowId}/repo-file")
    public ResponseEntity<Map<String, String>> getRepoFile(
            @PathVariable UUID workflowId,
            @RequestParam String filePath,
            HttpSession session) {

        UUID userId = SessionUtil.getUserId(session);

        WorkflowRun run = workflowRunRepository.findById(workflowId).orElse(null);
        if (run == null) {
            return ResponseEntity.notFound().build();
        }
        if (!run.getUser().getId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }
        if (run.getStatus() != com.moae.enums.WorkflowStatus.AWAITING_CODE_REVIEW) {
            return ResponseEntity.status(409)
                    .body(Map.of("error", "Workflow is not awaiting code review"));
        }

        String token = userRepository.findById(userId)
                .orElseThrow()
                .getGithubAccessToken();

        String owner  = run.getPendingOwner() != null ? run.getPendingOwner() : "";
        String repo   = run.getPendingRepo()  != null ? run.getPendingRepo()  : "";

        try {
            com.moae.client.dto.GitHubFileResponse file =
                    gitHubClient.getFile(owner, repo, filePath, token);

            return ResponseEntity.ok(Map.of(
                    "content",  file.content(),
                    "language", detectLanguageFromPath(filePath),
                    "filePath", filePath
            ));
        } catch (Exception e) {
            log.warn("getRepoFile | not found: {}/{}/{} — {}", owner, repo, filePath, e.getMessage());
            return ResponseEntity.status(404)
                    .body(Map.of("error", "File not found: " + filePath));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /** Maps a file-path extension to a Monaco editor language identifier. */
    private static String detectLanguageFromPath(String path) {
        if (path == null) return "plaintext";
        if (path.endsWith(".py"))   return "python";
        if (path.endsWith(".js"))   return "javascript";
        if (path.endsWith(".ts"))   return "typescript";
        if (path.endsWith(".jsx"))  return "javascript";
        if (path.endsWith(".tsx"))  return "typescript";
        if (path.endsWith(".java")) return "java";
        if (path.endsWith(".html") || path.endsWith(".htm")) return "html";
        if (path.endsWith(".css"))  return "css";
        if (path.endsWith(".md"))   return "markdown";
        if (path.endsWith(".json")) return "json";
        if (path.endsWith(".xml"))  return "xml";
        if (path.endsWith(".yml") || path.endsWith(".yaml")) return "yaml";
        if (path.endsWith(".sh"))   return "shell";
        return "plaintext";
    }
}
