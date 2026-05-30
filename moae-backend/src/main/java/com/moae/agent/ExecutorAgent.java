package com.moae.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moae.client.GitHubClient;
import com.moae.client.JiraClient;
import com.moae.client.MoaeClientException;
import com.moae.client.SlackClient;
import com.moae.client.dto.GitHubFileResponse;
import com.moae.agent.dto.StepResult;
import com.moae.entity.User;
import com.moae.entity.WorkflowRun;
import com.moae.entity.WorkflowStep;
import com.moae.ide.IdeSession;
import com.moae.ide.IdeSessionRegistry;
import com.moae.enums.FailureReason;
import com.moae.enums.IntegrationType;
import com.moae.enums.StepStatus;
import com.moae.enums.WorkflowStatus;
import com.moae.repository.UserIntegrationRepository;
import com.moae.repository.UserRepository;
import com.moae.repository.WorkflowStepRepository;
import com.moae.repository.WorkflowRunRepository;
import com.moae.repository.UserDefaultsRepository;
import com.moae.entity.UserDefaults;
import com.moae.service.GroqLlmService;
import com.moae.sse.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Executes each step in the Planner's plan by routing to the correct HTTP
 * client.
 *
 * Pipeline position: Phase 2 (called by WorkflowOrchestrator after
 * PlannerAgent).
 *
 * Key design rules:
 * - NEVER throws — all exceptions are caught per-step; loop always continues.
 * - Credentials loaded ONCE before the loop — not queried inside each step.
 * - generatedCode tracked across steps — LLM output flows into pushFile
 * content.
 * - fetchedFileContent/fetchedFilePath tracked — feed real file context into
 * generateCode prompt.
 * - Every step emits substep_complete SSE regardless of success or failure.
 * - MoaeClientException is caught first (typed failure info); then generic
 * Exception.
 *
 * DB lifecycle per step:
 * PENDING (created by orchestrator) → ACTIVE (set here on step start)
 * → SUCCESS or FAILED (set here after step completes or fails)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutorAgent {

    private final GitHubClient gitHubClient;
    private final JiraClient jiraClient;
    private final SlackClient slackClient;
    private final GroqLlmService groqLlmService;
    private final UserRepository userRepository;
    private final UserIntegrationRepository userIntegrationRepository;
    private final WorkflowStepRepository workflowStepRepository;
    private final WorkflowRunRepository workflowRunRepository;
    private final UserDefaultsRepository userDefaultsRepository;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final ObjectMapper objectMapper;
    private final IdeSessionRegistry ideSessionRegistry;
    private final org.springframework.scheduling.TaskScheduler taskScheduler;
    private final com.moae.sse.HeartbeatRegistry heartbeatRegistry;

    /**
     * Executes every step in the plan sequentially.
     *
     * Guarantees:
     * - Never throws — catches all exceptions per-step and continues.
     * - Always returns a result list with one entry per plan step.
     * - Each result has either SUCCESS or FAILED status.
     *
     * @param plan          ordered list of step maps from PlannerAgent
     * @param workflowRunId UUID of the WorkflowRun row (for DB updates)
     * @param userId        UUID of the authenticated user (for credential lookup)
     * @param workflowId    string workflowId (for SSE event emission)
     * @return list of StepResult — one per plan step, in plan order
     */
    public List<StepResult> execute(List<Map<String, Object>> plan,
            UUID workflowRunId,
            UUID userId,
            String workflowId) {
        return executeInternal(plan, workflowRunId, userId, workflowId, 0, null);
    }

    public List<StepResult> resume(List<Map<String, Object>> plan,
            UUID workflowRunId,
            UUID userId,
            String workflowId,
            int resumeFromStep,
            String approvedCode) {
        // resumeFromStep is 1-based, we need 0-based index for the loop
        return executeInternal(plan, workflowRunId, userId, workflowId, resumeFromStep - 1, approvedCode);
    }

    private List<StepResult> executeInternal(List<Map<String, Object>> plan,
            UUID workflowRunId,
            UUID userId,
            String workflowId,
            int startStepIndex,
            String initialGeneratedCode) {
        log.info("EXECUTOR STARTED");

        // ── STEP A: Load credentials ONCE before the loop ────────────────────
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        String githubToken = user.getGithubAccessToken();
        String githubOwner = user.getGithubLogin();

        Map<String, Object> jiraConfig = userIntegrationRepository
                .findByUserIdAndIntegrationTypeAndIsActiveTrue(userId, IntegrationType.JIRA)
                .map(i -> parseConfigJson(i.getConfigJson()))
                .orElse(null);

        Map<String, Object> slackConfig = userIntegrationRepository
                .findByUserIdAndIntegrationTypeAndIsActiveTrue(userId, IntegrationType.SLACK)
                .map(i -> parseConfigJson(i.getConfigJson()))
                .orElse(null);

        // Load the WorkflowRun once — needed for the code-review pause
        WorkflowRun workflowRun = workflowRunRepository
                .findById(workflowRunId)
                .orElseThrow(() -> new RuntimeException("WorkflowRun not found: " + workflowRunId));

        UserDefaults defaultsEntity = userDefaultsRepository.findByUserId(userId).orElse(null);

        String generatedCode = initialGeneratedCode;        // carries LLM output from generateCode → pushFile
        String fetchedFileContent = null;   // carries file content from getFile → generateCode
        String fetchedFilePath = null;      // carries file path from getFile → generateCode
        // tracks the branch created in a prior createBranch step for use in pause metadata
        String lastCreatedBranch = null;
        // tracks owner/repo seen in any github step so the generateCode pre-fetch can use them
        String lastKnownOwner = githubOwner;  // default to authenticated user's login
        String lastKnownRepo  = "";

        List<StepResult> results = new ArrayList<>();

        // ── STEP B: Execution loop ────────────────────────────────────────────
        for (int i = startStepIndex; i < plan.size(); i++) {
            Map<String, Object> step = plan.get(i);
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) step.get("params");
            String tool   = (String) step.get("tool");
            String action = (String) step.get("action");
            int stepId = i + 1;

            // Mark step ACTIVE in DB
            WorkflowStep workflowStep = workflowStepRepository
                    .findByWorkflowRunIdAndStepId(workflowRunId, stepId)
                    .orElseThrow(() -> new RuntimeException(
                            "WorkflowStep not found for run=" + workflowRunId + " stepId=" + stepId));
            workflowStep.setStatus(StepStatus.ACTIVE);
            workflowStepRepository.save(workflowStep);

            long startTime = System.currentTimeMillis();

            // ── TRY: execute the step ─────────────────────────────────────────
            try {
                // ── Pre-generateCode: emit repo file tree + optionally pre-fetch target file ──
                if ("generateCode".equals(action)) {
                    String gcOwner    = params != null && params.containsKey("owner")
                                        ? (String) params.get("owner") : lastKnownOwner;
                    String gcRepo     = params != null && params.containsKey("repo")
                                        ? (String) params.get("repo")  : lastKnownRepo;
                    String gcFilePath = params != null
                                        ? (String) params.getOrDefault("filePath", "") : "";

                    // Step A: fetch the repo file tree and emit it so the frontend
                    // can display it in the code-review panel.
                    try {
                        List<Map<String, Object>> fileTree =
                                gitHubClient.getFileTree(gcOwner, gcRepo, "main", githubToken);
                        sseEmitterRegistry.send(workflowId, "repo_tree_ready", Map.of(
                                "owner",      gcOwner,
                                "repo",       gcRepo,
                                "fileTree",   fileTree,
                                "targetFile", gcFilePath
                        ));
                        log.info("generateCode | emitted repo_tree_ready | {} files", fileTree.size());
                    } catch (Exception treeEx) {
                        // Non-critical — log and continue without the tree
                        log.warn("generateCode | failed to fetch repo file tree: {}", treeEx.getMessage());
                    }

                    // Step B: if no getFile step ran yet, try to pre-fetch the target file
                    // so the LLM has its existing content as context.
                    if (!gcFilePath.isBlank() && fetchedFileContent == null && !gcRepo.isBlank()) {
                        try {
                            com.moae.client.dto.GitHubFileResponse existing =
                                    gitHubClient.getFile(gcOwner, gcRepo, gcFilePath, githubToken);
                            fetchedFileContent = existing.content();
                            fetchedFilePath    = gcFilePath;
                            log.info("generateCode | pre-fetched existing file: {} ({} chars)",
                                    gcFilePath, fetchedFileContent.length());
                        } catch (Exception fileEx) {
                            // File doesn't exist yet (new file) — not an error
                            log.info("generateCode | file not found in repo (new file): {}", gcFilePath);
                        }
                    }
                }

                String resultJson = routeStep(tool, action, params,
                        githubToken, githubOwner, jiraConfig, slackConfig,
                        generatedCode, fetchedFileContent, fetchedFilePath,
                        defaultsEntity, workflowRun.getGoal());


                // Track fetched file content for subsequent generateCode steps
                if ("getFile".equals(action) && resultJson != null) {
                    Map<String, Object> fileResult = objectMapper.readValue(
                            resultJson, new TypeReference<>() {});
                    fetchedFileContent = (String) fileResult.get("content");
                    fetchedFilePath    = (String) fileResult.get("filePath");
                    log.info("Stored fetched file | path={} | contentLength={}",
                            fetchedFilePath,
                            fetchedFileContent != null ? fetchedFileContent.length() : 0);
                }

                // Track branch name created in a prior createBranch step
                if ("createBranch".equals(action) && params != null) {
                    String nb = (String) params.get("newBranchName");
                    if (nb != null && !nb.isBlank()) {
                        lastCreatedBranch = nb;
                        log.debug("Tracked created branch: {}", lastCreatedBranch);
                    }
                }

                // Track owner/repo from any github step so generateCode can use them
                if (params != null) {
                    if (params.containsKey("owner")) lastKnownOwner = (String) params.get("owner");
                    if (params.containsKey("repo"))  lastKnownRepo  = (String) params.get("repo");
                }

                // ── generateCode SUCCESS → pause for human review —————————————
                if ("generateCode".equals(action) && resultJson != null) {

                    // Unwrap the generated code string from the JSON envelope
                    String rawCode;
                    try {
                        Map<String, Object> codeResult = objectMapper.readValue(
                                resultJson, new TypeReference<>() {});
                        Object codeVal = codeResult.get("generatedCode");
                        rawCode = codeVal != null ? codeVal.toString() : resultJson;
                    } catch (Exception ex) {
                        rawCode = resultJson; // fallback: use raw string
                    }

                    if (rawCode == null || rawCode.isBlank()) {
                        throw new MoaeClientException(
                                "generateCode: LLM returned empty response",
                                FailureReason.CLIENT_ERROR, 0);
                    }

                    if (isPlaceholder(rawCode)) {
                        // Retry ONCE with stricter prompt before giving up
                        log.warn("generateCode | placeholder detected on attempt 1 — retrying with stricter prompt");
                        
                        String instruction = params != null ? (String) params.getOrDefault("instruction", "") : "";
                        String pauseFilePath = fetchedFilePath != null ? fetchedFilePath : "unknown file";
                        String contextBlock = (fetchedFileContent != null)
                                ? "EXISTING FILE TO MODIFY:\n```\n" + fetchedFileContent + "\n```\n"
                                : "CREATE A NEW FILE FROM SCRATCH.\n";
                                
                        String strictPrompt = "IMPORTANT: Return ONLY real code. No placeholders. No angle brackets.\n\n" +
                                "You are a professional software engineer. Your output will be committed DIRECTLY to a GitHub repository — a human will review it before push.\n\n" +
                                contextBlock + "File path: " + pauseFilePath + "\nTask: " + instruction + "\n\n" +
                                "STRICT RULES — violation means the build fails:\n" +
                                "1. Return ONLY the raw file content. Nothing else.\n" +
                                "2. ZERO placeholder text of any kind:\n" +
                                "   no TODO, no YOUR_CODE_HERE, no GENERATED_CODE,\n" +
                                "   no <...>, no \"add your implementation here\",\n" +
                                "   no angle-bracket tokens, no PASTE_HERE.\n" +
                                "3. No markdown code fences or backticks.\n" +
                                "4. No explanatory text before or after the code.\n" +
                                "5. The code must be complete and immediately runnable.\n" +
                                "6. For HTML: include full DOCTYPE, head with meta/title, complete body.\n" +
                                "7. For Python: include all imports, no stub functions.\n" +
                                "8. Keep ALL existing code unless the task explicitly says to remove it.\n" +
                                "9. If adding a function, preserve all existing functions.";

                        rawCode = groqLlmService.codeGeneratorCall(strictPrompt);
                        rawCode = stripMarkdown(rawCode);
                        
                        if (isPlaceholder(rawCode)) {
                            throw new MoaeClientException(
                                "generateCode: LLM returned placeholder on both attempts. Check instruction clarity.",
                                FailureReason.CLIENT_ERROR, 0);
                        }
                    }

                    generatedCode = rawCode;

                    // ── Persist SUCCESS for the generateCode step itself ──
                        long timeTaken = System.currentTimeMillis() - startTime;
                        workflowStep.setStatus(StepStatus.SUCCESS);
                        workflowStep.setResultJson(resultJson);
                        workflowStep.setTimeTakenMs(timeTaken);
                        workflowStep.setCompletedAt(LocalDateTime.now());
                        workflowStepRepository.save(workflowStep);

                        results.add(StepResult.builder()
                                .stepId(stepId)
                                .tool(tool)
                                .action(action)
                                .paramsJson(serializeParams(params))
                                .status(StepStatus.SUCCESS)
                                .resultJson(resultJson)
                                .timeTakenMs(timeTaken)
                                .build());

                        sseEmitterRegistry.send(workflowId, "substep_complete",
                                Map.of("stepIndex", i, "status", "SUCCESS",
                                        "tool", tool, "action", action));

                        // ── Resolve pause metadata ────────────────────────────
                        // Look ahead to the next step (which is usually pushFile) to grab filePath and branchName
                        Map<String, Object> nextStepParams = null;
                        if (i + 1 < plan.size()) {
                            Map<String, Object> nextStep = plan.get(i + 1);
                            if ("pushFile".equals(nextStep.get("action")) && nextStep.get("params") != null) {
                                nextStepParams = (Map<String, Object>) nextStep.get("params");
                            }
                        }

                        // filePath: prefer the plan's pushFile step params, then getFile path
                        String pauseFilePath = fetchedFilePath;
                        if (pauseFilePath == null && nextStepParams != null) {
                            pauseFilePath = (String) nextStepParams.get("filePath");
                        }
                        if (pauseFilePath == null && params != null) {
                            pauseFilePath = (String) params.get("filePath");
                        }
                        if (pauseFilePath == null) pauseFilePath = "generated_file";

                        // branchName: use the last created branch, or fall back to pushFile param
                        String pauseBranch = lastCreatedBranch;
                        if (pauseBranch == null && nextStepParams != null) {
                            pauseBranch = (String) nextStepParams.get("branchName");
                        }
                        if (pauseBranch == null && params != null) {
                            pauseBranch = (String) params.get("branchName");
                        }
                        if (pauseBranch == null) pauseBranch = "main";

                        // ── Detect Python overrides ──────────────────────────────
                        String detectedLang = detectLanguage(pauseFilePath);
                        if (rawCode.startsWith("import tkinter") || 
                            rawCode.startsWith("from tkinter") ||
                            (rawCode.contains("def ") && rawCode.contains("self"))) {
                            detectedLang = "python";
                        }

                        // === HANDOFF TO IDE AGENT ===
                        String instruction = params != null ? (String) params.getOrDefault("instruction", "") : "";
                        
                        // 1. Create IdeSession with the generated code as the primary file
                        IdeSession ideSession = new IdeSession(
                            workflowId,
                            lastKnownOwner,
                            lastKnownRepo,
                            pauseBranch,                   // branch
                            pauseFilePath,                 // primary file being worked on
                            rawCode,                       // the AI-generated code
                            instruction                    // original task instruction from step params
                        );
                        
                        // 2. If the plan had a preceding getFile step, 
                        //    register that file's original content too
                        if (fetchedFileContent != null && !fetchedFileContent.equals(rawCode)) {
                            ideSession.registerFile(pauseFilePath, fetchedFileContent);
                            // Then update to show the AI-generated version as current
                            ideSession.updateFile(pauseFilePath, rawCode);
                        }
                        
                        // 3. Register session
                        ideSessionRegistry.register(workflowId, ideSession);
                        log.info("ExecutorAgent | IdeSession created for workflowId={} | primaryFile={} | " +
                                 "owner={} | repo={}", workflowId, pauseFilePath, lastKnownOwner, lastKnownRepo);
                        
                        // 4. Save pause state to DB
                        workflowRun.setPendingCode(rawCode);
                        workflowRun.setPendingFilePath(pauseFilePath);
                        workflowRun.setPendingBranchName(pauseBranch);
                        workflowRun.setPendingOwner(lastKnownOwner);
                        workflowRun.setPendingRepo(lastKnownRepo);
                        workflowRun.setResumeFromStep(workflowStep.getStepId() + 1);
                        workflowRun.setStatus(WorkflowStatus.AWAITING_CODE_REVIEW);
                        workflowRunRepository.save(workflowRun);
                        log.info("ExecutorAgent | workflow paused | resumeFromStep={}", workflowStep.getStepId() + 1);
                        
                        // 5. Emit SSE to frontend with full context
                        sseEmitterRegistry.send(workflowId, "code_review_ready", Map.of(
                            "code",       rawCode,
                            "filePath",   pauseFilePath,
                            "language",   detectedLang,
                            "owner",      lastKnownOwner,
                            "repo",       lastKnownRepo,
                            "message",    "IDE Agent ready. Review and modify before pushing to GitHub."
                        ));
                        
                        java.util.concurrent.ScheduledFuture<?> heartbeat = taskScheduler.scheduleAtFixedRate(() -> {
                            try {
                                WorkflowRun current = workflowRunRepository.findById(workflowRun.getId()).orElse(null);
                                if (current == null || !"AWAITING_CODE_REVIEW".equals(current.getStatus())) {
                                    return;
                                }
                                sseEmitterRegistry.send(workflowId, "heartbeat", Map.of("t", System.currentTimeMillis()));
                            } catch (Exception e) {
                            }
                        }, java.time.Instant.now().plusSeconds(20), java.time.Duration.ofSeconds(20));
                        heartbeatRegistry.register(workflowId, heartbeat);
                        
                        // 6. STOP execute() — IdeAgent now handles everything until approve
                        return results;
                }

                // Normal SUCCESS handling for all non-generateCode steps
                // (and the unreachable placeholder-is-placeholder branch above)
                if (!"generateCode".equals(action)) {
                    long timeTaken = System.currentTimeMillis() - startTime;

                    workflowStep.setStatus(StepStatus.SUCCESS);
                    workflowStep.setResultJson(resultJson);
                    workflowStep.setTimeTakenMs(timeTaken);
                    workflowStep.setCompletedAt(LocalDateTime.now());
                    workflowStepRepository.save(workflowStep);

                    String paramsJson = serializeParams(params);
                    results.add(StepResult.builder()
                            .stepId(stepId)
                            .tool(tool)
                            .action(action)
                            .paramsJson(paramsJson)
                            .status(StepStatus.SUCCESS)
                            .resultJson(resultJson)
                            .timeTakenMs(timeTaken)
                            .build());

                    sseEmitterRegistry.send(workflowId, "substep_complete",
                            Map.of("stepIndex", i, "status", "SUCCESS",
                                    "tool", tool, "action", action));

                    log.info("Step {}/{} [{}:{}] SUCCESS in {}ms",
                            stepId, plan.size(), tool, action, timeTaken);
                }

            } catch (MoaeClientException e) {
                log.error("FULL STACKTRACE", e);
                // ── CATCH: typed HTTP client failure ─────────────────────────
                long timeTaken = System.currentTimeMillis() - startTime;

                workflowStep.setStatus(StepStatus.FAILED);
                workflowStep.setFailureReason(e.getFailureReason());
                workflowStep.setTimeTakenMs(timeTaken);
                workflowStep.setCompletedAt(LocalDateTime.now());
                workflowStepRepository.save(workflowStep);

                String paramsJson = serializeParams(params);
                results.add(StepResult.builder()
                        .stepId(stepId)
                        .tool(tool)
                        .action(action)
                        .paramsJson(paramsJson)
                        .status(StepStatus.FAILED)
                        .failureReason(e.getFailureReason())
                        .timeTakenMs(timeTaken)
                        .errorMessage(e.getMessage())
                        .build());

                sseEmitterRegistry.send(workflowId, "substep_complete",
                        Map.of("stepIndex", i, "status", "FAILED",
                                "tool", tool, "action", action, "error", e.getMessage()));

                log.error("Step {}/{} [{}:{}] FAILED ({}): {}",
                        stepId, plan.size(), tool, action, e.getFailureReason(), e.getMessage());
                // CONTINUE — do not break; execute remaining steps

            } catch (Exception e) {
                log.error("FULL STACKTRACE", e);
                // ── CATCH: unexpected failure — wrap as SERVER_ERROR ──────────
                long timeTaken = System.currentTimeMillis() - startTime;

                workflowStep.setStatus(StepStatus.FAILED);
                workflowStep.setFailureReason(FailureReason.SERVER_ERROR);
                workflowStep.setTimeTakenMs(timeTaken);
                workflowStep.setCompletedAt(LocalDateTime.now());
                workflowStepRepository.save(workflowStep);

                String paramsJson = serializeParams(params);
                results.add(StepResult.builder()
                        .stepId(stepId)
                        .tool(tool)
                        .action(action)
                        .paramsJson(paramsJson)
                        .status(StepStatus.FAILED)
                        .failureReason(FailureReason.SERVER_ERROR)
                        .timeTakenMs(timeTaken)
                        .errorMessage(e.getMessage())
                        .build());

                sseEmitterRegistry.send(workflowId, "substep_complete",
                        Map.of("stepIndex", i, "status", "FAILED",
                                "tool", tool, "action", action,
                                "error", e.getMessage() != null ? e.getMessage() : "Unexpected error"));

                log.error("Step {}/{} [{}:{}] FAILED (SERVER_ERROR — unexpected):",
                        stepId, plan.size(), tool, action, e);
                // CONTINUE
            }
        }

        return results;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIVATE: routeStep — maps tool+action → HTTP client call
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Routes a plan step to the correct HTTP client method.
     * Returns a JSON string result in all cases (success) or throws
     * MoaeClientException (failure).
     *
     * @param generatedCode      LLM-generated code from a prior "generateCode" step; may be null
     * @param fetchedFileContent raw file content fetched from a prior "getFile" step; may be null
     * @param fetchedFilePath    path of the file fetched in the prior "getFile" step; may be null
     * @return JSON string of the step result
     */
    private String routeStep(String tool, String action, Map<String, Object> params,
            String githubToken, String githubOwner,
            Map<String, Object> jiraConfig, Map<String, Object> slackConfig,
            String generatedCode,
            String fetchedFileContent, String fetchedFilePath,
            UserDefaults defaultsEntity, String goalText) throws JsonProcessingException {

        // Inline param extractor helper
        java.util.function.BiFunction<String, String, String> p = (key, fallback) -> params != null
                ? (String) params.getOrDefault(key, fallback)
                : fallback;

        switch (tool) {

            // ── GITHUB ───────────────────────────────────────────────────────
            case "github" -> {
                switch (action) {
                    case "getFile" -> {
                        String owner = p.apply("owner", githubOwner);
                        String repo = p.apply("repo", "");
                        String filePath = p.apply("filePath", "");
                        GitHubFileResponse file = gitHubClient.getFile(owner, repo, filePath, githubToken);
                        return objectMapper.writeValueAsString(Map.of(
                                "content", file.content(),
                                "sha", file.sha(),
                                "filePath", filePath));
                    }
                    case "createBranch" -> {
                        String owner = p.apply("owner", githubOwner);
                        String repo = p.apply("repo", "");
                        String newBranchName = p.apply("newBranchName", "");
                        String baseBranch = p.apply("baseBranch", "main");
                        gitHubClient.createBranch(owner, repo, newBranchName, baseBranch, githubToken);
                        return "{\"status\":\"success\",\"branch\":\"" + newBranchName + "\"}";
                    }
                    case "pushFile" -> {
                        String repo          = p.apply("repo", "");
                        String filePath      = p.apply("filePath", "");
                        String commitMessage = p.apply("commitMessage", "MOAE automated commit");
                        String branchName    = p.apply("branchName", "main");
                        String owner         = p.apply("owner", githubOwner);

                        // ALWAYS prefer generatedCode (user-approved) over params content
                        // params.get("content") is a planner placeholder — never trust it
                        String content = null;
                        
                        if (generatedCode != null && !isPlaceholder(generatedCode)) {
                            try {
                                // generatedCode might be stored as JSON: {"generatedCode": "...actual code..."}
                                Map<String, Object> codeResult = objectMapper.readValue(
                                        generatedCode, new TypeReference<>() {});
                                Object codeVal = codeResult.get("generatedCode");
                                content = codeVal != null ? codeVal.toString() : generatedCode;
                            } catch (Exception e) {
                                content = generatedCode; // fallback — use raw string
                            }
                        }
                        
                        if (content == null || content.isBlank() || isPlaceholder(content)) {
                            if (fetchedFileContent != null && !isPlaceholder(fetchedFileContent)) {
                                content = fetchedFileContent;
                            } else {
                                content = p.apply("content", "");
                            }
                        }
                        // ── HARD GATE: abort before committing garbage to GitHub ──
                        if (content == null || content.isBlank()) {
                            throw new MoaeClientException(
                                    "pushFile aborted: no content available. " +
                                    "generateCode or getFile must run first.",
                                    FailureReason.CLIENT_ERROR, 0);
                        }
                        if (isPlaceholder(content)) {
                            throw new MoaeClientException(
                                    "pushFile aborted: content contains placeholder text — " +
                                    "LLM failed to generate real code. Refusing to commit garbage.",
                                    FailureReason.CLIENT_ERROR, 0);
                        }

                        // Auto-fetch SHA — required when file already exists on branch
                        // Without SHA, GitHub returns 422 on updates to existing files
                        String fileSha = null;
                        try {
                            GitHubFileResponse existing = gitHubClient.getFile(
                                    owner, repo, filePath, githubToken);
                            fileSha = existing.sha();
                            log.info("pushFile: existing file found, SHA={}", fileSha);
                        } catch (Exception e) {
                            // File doesn't exist yet on this branch — SHA stays null
                            // GitHub will create it as a new file (no SHA needed)
                            log.info("pushFile: file not found on branch '{}' — creating new file", branchName);
                        }

                        gitHubClient.pushFile(owner, repo, filePath, content,
                                commitMessage, branchName, fileSha, githubToken);
                        return objectMapper.writeValueAsString(Map.of(
                                "status",   "success",
                                "pushedTo", branchName,
                                "filePath", filePath));
                    }
                    case "createPR" -> {
                        String repo = p.apply("repo", "");
                        String title = p.apply("title", "Automated PR by MOAE");
                        String head = p.apply("head", "");
                        String base = p.apply("base", "main");
                        com.moae.client.dto.GitHubPRResponse response = gitHubClient.createPR(githubOwner, repo, title,
                                head, base, githubToken);
                        return objectMapper.writeValueAsString(Map.of(
                                "prUrl", response.prUrl(),
                                "prNumber", response.prNumber()));
                    }
                    case "triggerAction" -> {
                        String repo       = p.apply("repo", "");
                        String workflowId = p.apply("workflowId", "");
                        String ref        = p.apply("ref", "main");
                        // Guard: a blank workflowId produces a malformed URL → GitHub 404.
                        // Fail fast with a clear error rather than a confusing 404.
                        if (workflowId == null || workflowId.isBlank()) {
                            throw new MoaeClientException(
                                "triggerAction requires a non-blank 'workflowId' param " +
                                "(e.g. 'ci.yml'). The LLM-generated plan omitted it.",
                                FailureReason.CLIENT_ERROR, 0);
                        }
                        gitHubClient.triggerAction(githubOwner, repo, workflowId, ref, githubToken);
                        return "{\"status\":\"success\"}";
                    }
                    default -> throw new MoaeClientException(
                            "Unknown GitHub action: " + action, FailureReason.CLIENT_ERROR, 0);
                }
            }

            // ── JIRA ─────────────────────────────────────────────────────────
            case "jira" -> {
                if (jiraConfig == null) {
                    throw new MoaeClientException(
                            "Jira not connected — go to Settings to link your Jira account",
                            FailureReason.CLIENT_ERROR, 0);
                }
                String domain = (String) jiraConfig.get("domain");
                String email = (String) jiraConfig.get("email");
                String apiToken = (String) jiraConfig.get("apiToken");

                switch (action) {
                    case "createTicket" -> {
                        String projectKey = p.apply("projectKey", "");
                        String summary = p.apply("summary", "");
                        String description = p.apply("description", "Created by MOAE automation");
                        // "assigneeEmail" is the current key; fall back to legacy "assigneeName"
                        // so that plans persisted before this rename still resolve correctly.
                        String assigneeEmail = p.apply("assigneeEmail", "");
                        if (assigneeEmail.isBlank()) {
                            assigneeEmail = p.apply("assigneeName", "");
                        }
                        String issueKey = jiraClient.createTicket(
                                domain, email, apiToken, projectKey, summary, description, assigneeEmail);
                        return objectMapper.writeValueAsString(Map.of("issueKey", issueKey));
                    }
                    case "updateStatus" -> {
                        String issueId = p.apply("issueId", "");
                        String defaultJiraProject = defaultsEntity != null ? defaultsEntity.getJiraProjectKey() : null;
                        issueId = normalizeJiraIssueId(issueId, defaultJiraProject);

                        String transitionParam = !p.apply("transitionName", "").isBlank()
                                ? p.apply("transitionName", "")
                                : p.apply("transitionId", "In Progress");

                        jiraClient.updateStatus(domain, email, apiToken, issueId, transitionParam);
                        return "{\"status\":\"success\"}";
                    }
                    default -> throw new MoaeClientException(
                            "Unknown Jira action: " + action, FailureReason.CLIENT_ERROR, 0);
                }
            }

            // ── SLACK ─────────────────────────────────────────────────────────
            case "slack" -> {
                if (slackConfig == null) {
                    throw new MoaeClientException(
                            "Slack not connected — go to Settings to link your Slack workspace",
                            FailureReason.CLIENT_ERROR, 0);
                }
                String botToken = (String) slackConfig.get("botToken");

                switch (action) {
                    case "sendMessage" -> {
                        String channel = p.apply("channel", "");
                        String defaultSlackChannel = defaultsEntity != null ? defaultsEntity.getSlackDefaultChannel() : null;
                        channel = normalizeSlackChannel(channel, goalText, defaultSlackChannel);

                        String text = p.apply("text", "");
                        slackClient.sendMessage(botToken, channel, text);
                        return "{\"status\":\"success\"}";
                    }
                    default -> throw new MoaeClientException(
                            "Unknown Slack action: " + action, FailureReason.CLIENT_ERROR, 0);
                }
            }

            // ── LLM ──────────────────────────────────────────────────────────
            case "llm" -> {
                switch (action) {
                    case "generateCode" -> {
                        String instruction = p.apply("instruction", "");
                        String filePath = fetchedFilePath != null ? fetchedFilePath : "unknown file";

                        // Build a context block that tells the LLM whether it is editing or creating
                        String contextBlock = (fetchedFileContent != null)
                                ? "EXISTING FILE TO MODIFY:\n```\n" + fetchedFileContent + "\n```\n"
                                : "CREATE A NEW FILE FROM SCRATCH.\n";

                        String codePrompt = """
                                You are a professional software engineer. Your output will be committed
                                DIRECTLY to a GitHub repository — a human will review it before push.

                                %s
                                File path: %s
                                Task: %s

                                STRICT RULES — violation means the build fails:
                                1. Return ONLY the raw file content. Nothing else.
                                2. ZERO placeholder text of any kind:
                                   no TODO, no YOUR_CODE_HERE, no GENERATED_CODE,
                                   no <...>, no "add your implementation here",
                                   no angle-bracket tokens, no PASTE_HERE.
                                3. No markdown code fences or backticks.
                                4. No explanatory text before or after the code.
                                5. The code must be complete and immediately runnable.
                                6. For HTML: include full DOCTYPE, head with meta/title, complete body.
                                7. For Python: include all imports, no stub functions.
                                8. Keep ALL existing code unless the task explicitly says to remove it.
                                9. If adding a function, preserve all existing functions.
                                """.formatted(contextBlock, filePath, instruction);

                        String generatedResult = groqLlmService.codeGeneratorCall(codePrompt);
                        generatedResult = stripMarkdown(generatedResult);

                        return objectMapper.writeValueAsString(
                                Map.of("generatedCode", generatedResult));
                    }
                    default -> throw new MoaeClientException(
                            "Unknown LLM action: " + action, FailureReason.CLIENT_ERROR, 0);
                }
            }

            // ── UNKNOWN TOOL ──────────────────────────────────────────────────
            default -> throw new MoaeClientException(
                    "Unknown tool: " + tool, FailureReason.CLIENT_ERROR, 0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIVATE helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private Map<String, Object> parseConfigJson(String configJson) {
        try {
            return objectMapper.readValue(configJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new RuntimeException(
                    "Failed to parse integration config JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Serializes params map to JSON string; returns "{}" on failure (never throws).
     */
    private String serializeParams(Map<String, Object> params) {
        try {
            return params != null ? objectMapper.writeValueAsString(params) : "{}";
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    /**
     * Returns true if the text contains any known placeholder token that indicates
     * the LLM failed to produce real code.
     *
     * This is the single authoritative check — both generateCode (inside routeStep)
     * and pushFile (hard gate) call this method, so the banned-string list is
     * maintained in exactly one place.
     *
     * Case-insensitive to catch "TODO", "todo", "Todo", etc.
     */
    private boolean isPlaceholder(String text) {
        if (text == null || text.isBlank()) return true;
        String lower = text.toLowerCase();
        return lower.contains("<generated")
                || lower.contains("generated_code_placeholder")
                || lower.contains("paste_")
                || lower.contains("your_code_here")
                || lower.contains("// your code here")
                || lower.contains("# your code here")
                || lower.contains("add your implementation")
                || lower.contains("previous step")
                || lower.contains("generated code from")
                || lower.contains("<generated code>")
                || lower.contains("todo: implement")
                || lower.contains("stub");
    }

    /**
     * Detects the syntax-highlighter language name from a file path extension.
     * Used to pass language metadata in the code_review_ready SSE event so the
     * frontend can activate the correct code editor mode.
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

    private String normalizeJiraIssueId(String issueId, String defaultProject) {
        if (issueId == null || issueId.isBlank()) return issueId;
        String trimmed = issueId.trim();
        if (trimmed.matches("\\d+")) {
            if (defaultProject != null && !defaultProject.isBlank()) {
                String normalized = defaultProject.toUpperCase().trim() + "-" + trimmed;
                log.info("ExecutorAgent | normalized bare number Jira ID to: {}", normalized);
                return normalized;
            }
        }
        return trimmed;
    }

    private String normalizeSlackChannel(String channel, String goal, String defaultChannel) {
        if (channel == null || channel.isBlank()) {
            return defaultChannel != null ? defaultChannel : "#general";
        }
        String normalized = channel.trim().toLowerCase();
        if (!normalized.startsWith("#")) {
            normalized = "#" + normalized;
        }
        
        if (goal != null) {
            String goalLower = goal.toLowerCase();
            String chanNoHash = normalized.replace("#", "");
            if (!goalLower.contains(chanNoHash)) {
                log.warn("ExecutorAgent | channel '{}' hallucinated (not in goal text), overriding with default '{}'",
                        normalized, defaultChannel);
                return defaultChannel != null ? defaultChannel : "#general";
            }
        }
        return normalized;
    }
}
