package com.moae.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moae.agent.ExecutorAgent;
import com.moae.agent.PlannerAgent;
import com.moae.agent.VerifierAgent;
import com.moae.agent.dto.StepResult;
import com.moae.agent.dto.VerificationResult;
import com.moae.client.GitHubClient;
import com.moae.dto.UserDefaultsDTO;
import com.moae.entity.User;
import com.moae.entity.WorkflowRun;
import com.moae.entity.WorkflowStep;
import com.moae.enums.StepStatus;
import com.moae.enums.WorkflowStatus;
import com.moae.repository.UserIntegrationRepository;
import com.moae.repository.UserRepository;
import com.moae.repository.WorkflowRunRepository;
import com.moae.repository.WorkflowStepRepository;
import com.moae.sse.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates the three-agent pipeline (Planner → Executor → Verifier)
 * asynchronously, streaming real-time progress to the frontend via SSE.
 *
 * This class replaced the Step 4 stub in Step 6 with real agent calls.
 *
 * Execution model:
 * POST /api/workflow/execute → creates WorkflowRun (RUNNING), fires @Async
 * GET /api/workflow/stream → frontend registers SSE channel
 * 
 * @Async thread → this method runs on Spring's thread pool
 *
 *        Phase breakdown:
 *        Phase 1 — Planning: PlannerAgent generates the step plan + DB rows
 *        created
 *        Phase 2 — Execution: ExecutorAgent runs each step + SSE
 *        substep_complete events
 *        Phase 3 — Verification: VerifierAgent scores the execution quality
 *        Phase 4 — Persist: WorkflowRun updated with final status + DQ scores
 *        Phase 5 — Complete: SSE workflow_complete event + emitter closed
 *
 *        Error handling:
 *        Any unhandled exception from Phase 1–3 → caught by outer try-catch →
 *        failWorkflow()
 *        ExecutorAgent never throws — all step failures are handled internally.
 *        VerifierAgent never throws — has fallback return for all error paths.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowOrchestrator {

    private final PlannerAgent plannerAgent;
    private final ExecutorAgent executorAgent;
    private final VerifierAgent verifierAgent;
    private final WorkflowRunRepository workflowRunRepository;
    private final WorkflowStepRepository workflowStepRepository;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final ObjectMapper objectMapper;
    private final com.moae.client.JiraClient jiraClient;
    private final UserIntegrationRepository userIntegrationRepository;
    private final UserDefaultsService userDefaultsService;
    private final GitHubClient gitHubClient;
    private final UserRepository userRepository;
    private final com.moae.ide.IdeSessionRegistry ideSessionRegistry;
    private final com.moae.sse.HeartbeatRegistry heartbeatRegistry;

    /**
     * Executes the full pipeline on a Spring @Async thread pool thread.
     * Returns void — all output via SSE events and DB writes.
     *
     * @param workflowId string UUID of the WorkflowRun (already persisted as
     *                   RUNNING)
     * @param goal       the raw natural language goal string
     * @param userId     UUID of the authenticated user (used by ExecutorAgent for
     *                   credentials)
     */
    @Async
    public void executeWorkflow(String workflowId, String message, UUID userId) {
        log.info("ORCHESTRATOR STARTED");
        try {

            // ── PHASE 1: PLANNING ─────────────────────────────────────────────

            WorkflowRun run = workflowRunRepository
                    .findById(UUID.fromString(workflowId))
                    .orElseThrow(() -> new RuntimeException("WorkflowRun not found: " + workflowId));

            sseEmitterRegistry.send(workflowId, "log",
                    Map.of("msg", "Analysing your request...", "cls", "ok"));

            // Load user defaults BEFORE planning so the planner can use them
            // as fallback context for owner, repo, Jira key, and Slack channel.
            // Returns an all-null DTO (never null itself) when no defaults are saved.
            UserDefaultsDTO defaults = userDefaultsService.getDefaults(userId);
            log.debug("Loaded defaults for userId={}: owner={}, repo={}, jiraKey={}, slack={}",
                    userId, defaults.getGithubOwner(), defaults.getGithubDefaultRepo(),
                    defaults.getJiraProjectKey(), defaults.getSlackDefaultChannel());

            List<Map<String, Object>> plan = plannerAgent.planFromNaturalLanguage(
                    message,
                    defaults,
                    jiraClient,
                    userIntegrationRepository,
                    userId,
                    objectMapper);

            sseEmitterRegistry.send(workflowId, "log",
                    Map.of("msg", "Plan ready — " + plan.size() + " steps", "cls", "ok"));

            // Create ALL WorkflowStep rows as PENDING before execution starts
            for (int i = 0; i < plan.size(); i++) {
                Map<String, Object> step = plan.get(i);
                @SuppressWarnings("unchecked")
                Map<String, Object> params = (Map<String, Object>) step.get("params");

                String paramsJson;
                try {
                    paramsJson = objectMapper.writeValueAsString(params);
                } catch (JsonProcessingException e) {
                    paramsJson = "{}";
                }

                WorkflowStep ws = new WorkflowStep();
                ws.setWorkflowRun(run);
                ws.setStepId(i + 1);
                ws.setTool((String) step.get("tool"));
                ws.setAction((String) step.get("action"));
                ws.setParamsJson(paramsJson);
                ws.setStatus(StepStatus.PENDING);
                workflowStepRepository.save(ws);
            }

            sseEmitterRegistry.send(workflowId, "plan_ready", Map.of("steps", plan));
            sseEmitterRegistry.send(workflowId, "log",
                    Map.of("msg", "Plan generated with " + plan.size() + " steps. Starting execution...",
                            "cls", "ok"));

            // ── PHASE 2: EXECUTION ────────────────────────────────────────────

            List<StepResult> stepResults = executorAgent.execute(
                    plan, run.getId(), userId, workflowId);

            // Fetch the latest state to see if execution was paused
            run = workflowRunRepository.findById(run.getId()).orElse(run);
            if (run.getStatus() == WorkflowStatus.AWAITING_CODE_REVIEW) {
                log.info("Workflow {} paused for code review. Orchestrator exiting early.", workflowId);
                return;
            }

            sseEmitterRegistry.send(workflowId, "log",
                    Map.of("msg", "Execution complete. Starting verification...", "cls", "ok"));

            finalizeWorkflow(run, stepResults, message, workflowId);

        } catch (Exception e) {
            log.error("FULL STACKTRACE", e);
            failWorkflow(workflowId, e);
        }
    }

    /**
     * Resumes a paused workflow after the user approves the pending code.
     */
    @Async
    public void resumeFromCodeApproval(UUID workflowRunId, UUID userId) {
        String workflowId = workflowRunId.toString();
        log.info("ORCHESTRATOR RESUMING: {}", workflowId);
        heartbeatRegistry.cancel(workflowId);
        log.info("resumeFromCodeApproval | heartbeat cancelled for {}", workflowId);
        try {
            WorkflowRun run = workflowRunRepository
                    .findById(workflowRunId)
                    .orElseThrow(() -> new RuntimeException("WorkflowRun not found: " + workflowId));

            sseEmitterRegistry.send(workflowId, "log",
                    Map.of("msg", "Resuming execution after code approval...", "cls", "ok"));

            // Restore IdeSession from DB state if missing
            if (ideSessionRegistry.get(workflowId).isEmpty()) {
                log.warn("resumeFromCodeApproval | IdeSession missing — restoring from DB");
                com.moae.ide.IdeSession restored = new com.moae.ide.IdeSession(
                    workflowId,
                    run.getPendingOwner(),
                    run.getPendingRepo(),
                    run.getPendingBranchName() != null ? run.getPendingBranchName() : "main",
                    run.getPendingFilePath(),
                    run.getPendingCode(),       // approved code from DB
                    "Restored session"
                );
                ideSessionRegistry.register(workflowId, restored);
            }

            // 1. Reconstruct the plan
            List<WorkflowStep> steps = workflowStepRepository.findByWorkflowRunIdOrderByStepIdAsc(workflowRunId);
            List<Map<String, Object>> plan = new ArrayList<>();
            for (WorkflowStep step : steps) {
                Map<String, Object> planStep = new HashMap<>();
                planStep.put("tool", step.getTool());
                planStep.put("action", step.getAction());
                try {
                    Map<String, Object> params = objectMapper.readValue(step.getParamsJson(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
                    planStep.put("params", params);
                } catch (Exception e) {
                    planStep.put("params", new HashMap<>());
                }
                plan.add(planStep);
            }

            // 2. Fetch completed steps results
            List<StepResult> previousResults = new ArrayList<>();
            for (WorkflowStep step : steps) {
                if (step.getStepId() < run.getResumeFromStep()) {
                    previousResults.add(StepResult.builder()
                            .stepId(step.getStepId())
                            .tool(step.getTool())
                            .action(step.getAction())
                            .paramsJson(step.getParamsJson())
                            .status(step.getStatus())
                            .resultJson(step.getResultJson())
                            .timeTakenMs(step.getTimeTakenMs() != null ? step.getTimeTakenMs() : 0L)
                            .failureReason(step.getFailureReason())
                            .build());
                }
            }

            // 3. Push additional files to the branch (if any were included in approval)
            if (run.getAdditionalFilesJson() != null && !run.getAdditionalFilesJson().isBlank()) {
                try {
                    Map<String, String> additionalFiles = objectMapper.readValue(
                            run.getAdditionalFilesJson(), new TypeReference<>() {});

                    // Resolve GitHub credentials for the file pushes
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new RuntimeException("User not found: " + userId));
                    String githubToken = user.getGithubAccessToken();
                    String owner = run.getPendingOwner() != null ? run.getPendingOwner() : user.getGithubLogin();
                    String repo  = run.getPendingRepo()  != null ? run.getPendingRepo()  : "";
                    String branch = run.getPendingBranchName() != null ? run.getPendingBranchName() : "main";

                    for (Map.Entry<String, String> entry : additionalFiles.entrySet()) {
                        String filePath = entry.getKey();
                        String content  = entry.getValue();
                        try {
                            // Auto-fetch SHA in case the file already exists on the branch
                            String fileSha = null;
                            try {
                                fileSha = gitHubClient.getFile(owner, repo, filePath, githubToken).sha();
                            } catch (Exception ignored) {
                                // New file — SHA stays null
                            }
                            gitHubClient.pushFile(
                                    owner, repo, filePath, content,
                                    "MOAE: Update " + filePath,
                                    branch, fileSha, githubToken);
                            log.info("resumeFromCodeApproval | pushed additional file: {}", filePath);
                        } catch (Exception pushEx) {
                            log.warn("resumeFromCodeApproval | failed to push additional file {}: {}",
                                    filePath, pushEx.getMessage());
                        }
                    }
                } catch (Exception parseEx) {
                    log.warn("resumeFromCodeApproval | could not parse additionalFilesJson: {}",
                            parseEx.getMessage());
                }
            }

            // 4. Resume execution
            List<StepResult> newResults = executorAgent.resume(
                    plan, run.getId(), userId, workflowId, run.getResumeFromStep(), run.getPendingCode());

            List<StepResult> allResults = new ArrayList<>(previousResults);
            allResults.addAll(newResults);

            // Fetch the latest state to see if execution was paused again (unlikely but safe)
            run = workflowRunRepository.findById(run.getId()).orElse(run);
            if (run.getStatus() == WorkflowStatus.AWAITING_CODE_REVIEW) {
                log.info("Workflow {} paused for code review again. Orchestrator exiting early.", workflowId);
                return;
            }

            sseEmitterRegistry.send(workflowId, "log",
                    Map.of("msg", "Execution complete. Starting verification...", "cls", "ok"));

            // 4. Continue to Phase 3
            finalizeWorkflow(run, allResults, run.getGoal(), workflowId);

        } catch (Exception e) {
            log.error("FULL STACKTRACE", e);
            failWorkflow(workflowId, e);
        }
    }

    private void finalizeWorkflow(WorkflowRun run, List<StepResult> stepResults, String message, String workflowId) {
        // ── PHASE 3: VERIFICATION ─────────────────────────────────────────

        VerificationResult verification = verifierAgent.verify(message, stepResults);

        // ── PHASE 4: PERSIST FINAL STATE ──────────────────────────────────

        // Check if any step was a successful PR creation
        String prUrl = null;
        Integer prNumber = null;
        for (StepResult sr : stepResults) {
            if ("github".equals(sr.getTool()) && "createPR".equals(sr.getAction())
                    && StepStatus.SUCCESS.equals(sr.getStatus())) {
                if (sr.getResultJson() != null) {
                    try {
                        Map<String, Object> prData = objectMapper.readValue(sr.getResultJson(),
                                new com.fasterxml.jackson.core.type.TypeReference<>() {
                                });
                        prUrl = (String) prData.get("prUrl");
                        prNumber = (Integer) prData.get("prNumber");
                    } catch (Exception e) {
                        log.warn("Failed to parse PR result json: {}", sr.getResultJson(), e);
                    }
                }
            }
        }

        WorkflowStatus finalStatus;
        if (prUrl != null || prNumber != null) {
            finalStatus = WorkflowStatus.COMPLETED;
            run.setPrUrl(prUrl);
            run.setPrNumber(prNumber);
            run.setPrMerged(false);
        } else {
            finalStatus = "SUCCESS".equals(verification.getVerdict())
                    ? WorkflowStatus.SUCCESS
                    : WorkflowStatus.FAILED;
        }

        run.setStatus(finalStatus);
        run.setOverallScore(verification.getOverallScore());
        run.setTaskCompletion(verification.getTaskCompletion());
        run.setDecisionAccuracy(verification.getDecisionAccuracy());
        run.setExecutionEfficiency(verification.getExecutionEfficiency());
        run.setContextRelevance(verification.getContextRelevance());
        run.setScoreSummary(verification.getSummary());
        run.setCompletedAt(LocalDateTime.now());
        workflowRunRepository.save(run);

        // ── PHASE 5: EMIT WORKFLOW_COMPLETE + CLOSE SSE ───────────────────

        Map<String, Object> completePayload = new HashMap<>();
        completePayload.put("workflowId", workflowId);
        completePayload.put("overallStatus", verification.getVerdict());
        completePayload.put("score", verification.getOverallScore());
        completePayload.put("taskCompletion", verification.getTaskCompletion());
        completePayload.put("decisionAccuracy", verification.getDecisionAccuracy());
        completePayload.put("executionEfficiency", verification.getExecutionEfficiency());
        completePayload.put("contextRelevance", verification.getContextRelevance());
        completePayload.put("summary", verification.getSummary());
        completePayload.put("results", buildStepResultsPayload(stepResults));

        sseEmitterRegistry.send(workflowId, "workflow_complete", completePayload);
        sseEmitterRegistry.complete(workflowId);

        log.info("Workflow {} completed → {} | score: {}",
                workflowId, verification.getVerdict(), verification.getOverallScore());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Called when an unhandled exception escapes Phase 1–3.
     * Persists FAILED status, emits a FAIL workflow_complete SSE event, closes
     * emitter.
     * Safe to call even if the DB update fails (wrapped in its own try-catch).
     */
    private void failWorkflow(String workflowId, Exception e) {
        log.error("WorkflowOrchestrator: workflow {} failed with unhandled exception", workflowId, e);

        // Persist FAILED status — wrapped in its own try-catch so SSE always fires
        try {
            workflowRunRepository.findById(UUID.fromString(workflowId)).ifPresent(run -> {
                run.setStatus(WorkflowStatus.FAILED);
                run.setScoreSummary("Workflow failed: " + e.getMessage());
                run.setCompletedAt(LocalDateTime.now());
                workflowRunRepository.save(run);
            });
        } catch (Exception dbEx) {
            log.error("FULL STACKTRACE", dbEx);
            log.error("failWorkflow: DB update also failed for workflowId={}", workflowId, dbEx);
        }

        // Always emit workflow_complete so the frontend doesn't hang waiting
        sseEmitterRegistry.send(workflowId, "workflow_complete",
                Map.of("overallStatus", "FAIL",
                        "score", 0,
                        "summary", "Workflow failed: " + e.getMessage()));
        sseEmitterRegistry.complete(workflowId);
    }

    /**
     * Builds the lightweight step summary payload for the workflow_complete SSE
     * event.
     * Excludes large fields (resultJson, paramsJson) — those are in DB only.
     */
    private List<Map<String, Object>> buildStepResultsPayload(List<StepResult> results) {
        List<Map<String, Object>> payload = new ArrayList<>();
        for (StepResult result : results) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("stepId", result.getStepId());
            entry.put("tool", result.getTool());
            entry.put("action", result.getAction());
            entry.put("status", result.getStatus().name());
            entry.put("timeTakenMs", result.getTimeTakenMs());
            entry.put("failureReason", result.getFailureReason() != null
                    ? result.getFailureReason().name()
                    : null);
            payload.add(entry);
        }
        return payload;
    }

    private Map<String, Object> parseConfigJson(String configJson) {
        try {
            return objectMapper.readValue(configJson, new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse integration config JSON: " + e.getMessage(), e);
        }
    }
}
