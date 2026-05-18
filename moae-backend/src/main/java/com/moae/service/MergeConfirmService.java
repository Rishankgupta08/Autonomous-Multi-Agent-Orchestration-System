package com.moae.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moae.client.JiraClient;
import com.moae.client.SlackClient;
import com.moae.entity.UserIntegration;
import com.moae.entity.WorkflowRun;
import com.moae.enums.IntegrationType;
import com.moae.enums.WorkflowStatus;
import com.moae.sse.SseEmitterRegistry;
import com.moae.repository.UserIntegrationRepository;
import com.moae.repository.WorkflowRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class MergeConfirmService {

    private final WorkflowRunRepository workflowRunRepository;
    private final UserIntegrationRepository userIntegrationRepository;
    private final JiraClient jiraClient;
    private final SlackClient slackClient;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final ObjectMapper objectMapper;

    public Map<String, Object> confirmMerge(UUID workflowId, UUID userId, boolean merged) {
        WorkflowRun run = workflowRunRepository.findByIdAndUserId(workflowId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Workflow not found or does not belong to current user"));

        if (run.getStatus() != WorkflowStatus.AWAITING_MERGE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Workflow is not in AWAITING_MERGE state. Current status: " + run.getStatus());
        }

        log.info("Merge confirmation received for workflow {} — merged={}", workflowId, merged);

        if (!merged) {
            run.setStatus(WorkflowStatus.FAILED);
            run.setPrMerged(false);
            run.setCompletedAt(LocalDateTime.now());
            workflowRunRepository.save(run);
            log.info("Workflow {} marked FAILED — PR was not merged", workflowId);

            try {
                sseEmitterRegistry.send(workflowId.toString(), "merge_confirmed",
                        Map.of("status", "FAILED",
                                "message", "PR was not merged. Workflow marked as failed."));
            } catch (Exception e) {
                log.info("SSE emitter expired for workflow {} — frontend may be disconnected", workflowId);
            }

            return Map.of("status", "FAILED",
                    "message", "Workflow marked as FAILED — PR was not merged");
        }

        run.setStatus(WorkflowStatus.COMPLETED);
        run.setPrMerged(true);
        run.setCompletedAt(LocalDateTime.now());
        workflowRunRepository.save(run);
        log.info("Workflow {} marked COMPLETED", workflowId);

        try {
            Optional<UserIntegration> jiraOpt = userIntegrationRepository
                    .findByUserIdAndIntegrationTypeAndIsActiveTrue(userId, IntegrationType.JIRA);

            if (jiraOpt.isPresent()) {
                Map<String, Object> config = objectMapper.readValue(
                        jiraOpt.get().getConfigJson(),
                        new TypeReference<Map<String, Object>>() {}
                );
                String domain = (String) config.get("domain");
                String email = (String) config.get("email");
                String apiToken = (String) config.get("apiToken");
                String ticketId = extractTicketId(run.getGoal());

                if (ticketId != null) {
                    jiraClient.updateStatus(domain, email, apiToken, ticketId, "41");
                    log.info("Jira ticket {} transitioned to Done", ticketId);
                } else {
                    log.warn("No ticketId found in goal — skipping Jira update");
                }
            } else {
                log.warn("Jira not connected for userId {} — skipping", userId);
            }
        } catch (Exception e) {
            log.error("Jira update failed — continuing: {}", e.getMessage());
        }

        try {
            Optional<UserIntegration> slackOpt = userIntegrationRepository
                    .findByUserIdAndIntegrationTypeAndIsActiveTrue(userId, IntegrationType.SLACK);

            if (slackOpt.isPresent()) {
                Map<String, Object> config = objectMapper.readValue(
                        slackOpt.get().getConfigJson(),
                        new TypeReference<Map<String, Object>>() {}
                );
                String botToken = (String) config.get("botToken");
                String text = "✅ PR #" + run.getPrNumber() + " merged. "
                        + "Workflow " + workflowId.toString().substring(0, 8)
                        + "... is now COMPLETED.";
                slackClient.sendMessage(botToken, "#general", text);
                log.info("Slack COMPLETED notification sent");
            } else {
                log.warn("Slack not connected for userId {} — skipping", userId);
            }
        } catch (Exception e) {
            log.error("Slack notification failed — continuing: {}", e.getMessage());
        }

        try {
            Map<String, Object> ssePayload = new HashMap<>();
            ssePayload.put("status", "COMPLETED");
            ssePayload.put("prNumber", run.getPrNumber());
            ssePayload.put("message", "PR merged. Workflow complete.");
            sseEmitterRegistry.send(workflowId.toString(), "merge_confirmed", ssePayload);
        } catch (Exception e) {
            log.info("SSE emitter expired — frontend may be disconnected");
        }

        return Map.of(
                "status", "COMPLETED",
                "message", "Workflow completed successfully",
                "prNumber", run.getPrNumber(),
                "prUrl", run.getPrUrl() != null ? run.getPrUrl() : ""
        );
    }

    private String extractTicketId(String goal) {
        if (goal == null || goal.isBlank()) return null;
        Pattern pattern = Pattern.compile("([A-Z]+-\\d+)");
        Matcher matcher = pattern.matcher(goal);
        if (matcher.find()) return matcher.group(1);
        return null;
    }
}
