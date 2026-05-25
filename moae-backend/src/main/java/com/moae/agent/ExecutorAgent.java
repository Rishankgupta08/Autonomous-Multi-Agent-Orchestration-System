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
import com.moae.entity.WorkflowStep;
import com.moae.enums.FailureReason;
import com.moae.enums.IntegrationType;
import com.moae.enums.StepStatus;
import com.moae.repository.UserIntegrationRepository;
import com.moae.repository.UserRepository;
import com.moae.repository.WorkflowStepRepository;
import com.moae.repository.WorkflowRunRepository;
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
    private final SseEmitterRegistry sseEmitterRegistry;
    private final ObjectMapper objectMapper;

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

        String generatedCode = null;        // carries LLM output from generateCode → pushFile
        String fetchedFileContent = null;   // carries file content from getFile → generateCode
        String fetchedFilePath = null;      // carries file path from getFile → generateCode

        List<StepResult> results = new ArrayList<>();

        // ── STEP B: Execution loop ────────────────────────────────────────────
        for (int i = 0; i < plan.size(); i++) {
            Map<String, Object> step = plan.get(i);
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) step.get("params");
            String tool = (String) step.get("tool");
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
                String resultJson = routeStep(tool, action, params,
                        githubToken, githubOwner, jiraConfig, slackConfig,
                        generatedCode, fetchedFileContent, fetchedFilePath);

                // Track fetched file content for subsequent generateCode steps
                if ("getFile".equals(action) && resultJson != null) {
                    Map<String, Object> fileResult = objectMapper.readValue(
                            resultJson, new TypeReference<>() {});
                    fetchedFileContent = (String) fileResult.get("content");
                    fetchedFilePath = (String) fileResult.get("filePath");
                    log.info("Stored fetched file | path={} | contentLength={}",
                            fetchedFilePath,
                            fetchedFileContent != null ? fetchedFileContent.length() : 0);
                }

                // Carry generated code forward to any subsequent pushFile step
                if ("generateCode".equals(action) && resultJson != null) {
                    generatedCode = resultJson;
                }

                long timeTaken = System.currentTimeMillis() - startTime;

                // Persist SUCCESS
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
            String fetchedFileContent, String fetchedFilePath) throws JsonProcessingException {

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
                        String repo = p.apply("repo", "");
                        String filePath = p.apply("filePath", "");
                        String commitMessage = p.apply("commitMessage", "MOAE automated commit");
                        String branchName = p.apply("branchName", "main");
                        String owner = p.apply("owner", githubOwner);

                        // Prefer params content; fall back to LLM-generated code
                        String content = p.apply("content", "");
                        if ((content == null || content.isBlank()) && generatedCode != null) {
                            try {
                                // generatedCode is stored as JSON: {"generatedCode": "...actual code..."}
                                Map<String, Object> codeResult = objectMapper.readValue(
                                        generatedCode, new TypeReference<>() {
                                        });
                                Object codeVal = codeResult.get("generatedCode");
                                content = codeVal != null ? codeVal.toString() : generatedCode;
                            } catch (Exception e) {
                                content = generatedCode; // fallback — use raw string
                            }
                        }
                        if (content == null || content.isBlank()) {
                            throw new MoaeClientException(
                                    "No content to push — 'content' param empty and no prior generateCode step",
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
                                "status", "success",
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
                        String repo = p.apply("repo", "");
                        String workflowId = p.apply("workflowId", "");
                        String ref = p.apply("ref", "main");
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
                        String existingContent = fetchedFileContent != null
                                ? fetchedFileContent
                                : "(file is new — write from scratch)";

                        String codePrompt = """
                                You are a precise code generator.
                                Return ONLY the complete updated file content.

                                RULES — follow these exactly:
                                1. Return raw code only. No markdown. No backticks. No explanation.
                                2. Never write placeholder text like <generated code>, TODO,
                                   PASTE_HERE, or any angle-bracket placeholders.
                                3. Keep ALL existing code unless the instruction says to remove it.
                                4. If adding a function, preserve all existing functions.
                                5. The output must be valid, complete, runnable code.

                                File: """ + filePath + """

                                Existing content:
                                """ + existingContent + """

                                Instruction: """ + instruction + """

                                Write the complete updated file now:""";

                        String generatedResult = groqLlmService.codeGeneratorCall(codePrompt);

                        // Validate — reject placeholder responses
                        List<String> PLACEHOLDERS = List.of(
                                "<generated", "PASTE_", "TODO",
                                "// your code here", "# your code here",
                                "previous step", "generated code"
                        );
                        boolean isPlaceholder = PLACEHOLDERS.stream()
                                .anyMatch(p2 -> generatedResult.toLowerCase().contains(p2.toLowerCase()));

                        if (isPlaceholder) {
                            log.warn("generateCode: Groq returned placeholder text — retrying once");
                            String retry = groqLlmService.codeGeneratorCall(
                                    "IMPORTANT: Return ONLY real code. No placeholders. No angle brackets.\n\n"
                                            + codePrompt);
                            boolean retryIsPlaceholder = PLACEHOLDERS.stream()
                                    .anyMatch(p2 -> retry.toLowerCase().contains(p2.toLowerCase()));
                            if (retryIsPlaceholder) {
                                throw new MoaeClientException(
                                        "Code generation produced invalid placeholder output after retry",
                                        FailureReason.SERVER_ERROR, 0);
                            }
                            return objectMapper.writeValueAsString(
                                    Map.of("generatedCode", retry));
                        }

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
}
