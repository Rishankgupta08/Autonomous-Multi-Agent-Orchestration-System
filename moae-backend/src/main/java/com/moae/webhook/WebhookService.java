package com.moae.webhook;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moae.client.JiraClient;
import com.moae.client.SlackClient;
import com.moae.enums.IntegrationType;
import com.moae.entity.UserIntegration;
import com.moae.entity.WorkflowRun;
import com.moae.enums.WorkflowStatus;
import com.moae.repository.UserIntegrationRepository;
import com.moae.repository.WorkflowRunRepository;
import com.moae.sse.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
public class WebhookService {

    private final WorkflowRunRepository workflowRunRepository;
    private final UserIntegrationRepository userIntegrationRepository;
    private final JiraClient jiraClient;
    private final SlackClient slackClient;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final ObjectMapper objectMapper;

    @Value("${moae.github.webhook-secret:}")
    private String webhookSecret;

    @Value("${moae.webhook.verify-signature:true}")
    private boolean verifySignature;

    public boolean verifySignature(byte[] rawBody, String signatureHeader) {
        if (!verifySignature) {
            log.warn("Webhook signature verification is DISABLED — accepting all webhooks");
            return true;
        }

        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("Webhook secret is not configured — rejecting webhook");
            return false;
        }

        if (signatureHeader == null || signatureHeader.isBlank()) {
            log.warn("No X-Hub-Signature-256 header present");
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] expectedBytes = mac.doFinal(rawBody);

            StringBuilder hexString = new StringBuilder();
            for (byte b : expectedBytes) {
                hexString.append(String.format("%02x", b));
            }
            String expectedSignature = "sha256=" + hexString.toString();

            return MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Signature verification error: {}", e.getMessage());
            return false;
        }
    }

    public void handlePullRequestEvent(byte[] rawBody) {
        try {
            GitHubWebhookPayload payload = objectMapper.readValue(rawBody, GitHubWebhookPayload.class);
            String action = payload.getAction();
            PullRequestPayload pr = payload.getPull_request();

            if (pr == null) {
                log.warn("Webhook payload has no pull_request field — ignoring");
                return;
            }

            if (!"closed".equals(action) || !pr.isMerged()) {
                log.info("PR event ignored — action={}, merged={}", action, pr.isMerged());
                return;
            }

            log.info("PR #{} merged — triggering post-merge automation", pr.getNumber());
            handlePrMerge(pr.getNumber());
        } catch (Exception e) {
            log.error("Error handling pull request event: {}", e.getMessage(), e);
        }
    }

    private void handlePrMerge(int prNumber) {
        Optional<WorkflowRun> runOpt = workflowRunRepository.findByPrNumber(prNumber);
        if (runOpt.isEmpty()) {
            log.warn("No workflow found for PR #{} — ignoring merge event", prNumber);
            return;
        }
        
        WorkflowRun run = runOpt.get();
        String workflowId = run.getId().toString();
        UUID userId = run.getUser().getId();
        log.info("Found workflow {} for PR #{}", workflowId, prNumber);

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
                
                String ticketId = extractTicketIdFromGoal(run.getGoal());
                if (ticketId != null) {
                    jiraClient.updateStatus(domain, email, apiToken, ticketId, "41");
                    log.info("Jira ticket {} transitioned to Done", ticketId);
                } else {
                    log.warn("Could not extract ticketId from goal: {}", run.getGoal());
                }
            } else {
                log.warn("Jira not connected for userId {} — skipping Jira update", userId);
            }
        } catch (Exception e) {
            log.error("Jira update failed for workflow {} — continuing: {}", workflowId, e.getMessage());
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
                String message = "✅ PR #" + prNumber + " has been merged. "
                        + "Workflow " + workflowId.substring(0, 8) + "... is now COMPLETED.";
                slackClient.sendMessage(botToken, "#general", message);
                log.info("Slack merge notification sent for PR #{}", prNumber);
            } else {
                log.warn("Slack not connected for userId {} — skipping Slack notification", userId);
            }
        } catch (Exception e) {
            log.error("Slack notification failed for workflow {} — continuing: {}", workflowId, e.getMessage());
        }

        try {
            Map<String, Object> ssePayload = new HashMap<>();
            ssePayload.put("workflowId", workflowId);
            ssePayload.put("status", "COMPLETED");
            ssePayload.put("prNumber", prNumber);
            ssePayload.put("message", "PR #" + prNumber + " merged. Workflow complete.");
            sseEmitterRegistry.send(workflowId, "final_merge", ssePayload);
            log.info("SSE final_merge event emitted for workflow {}", workflowId);
        } catch (Exception e) {
            log.info("SSE emitter no longer active for workflow {} — frontend may be disconnected", workflowId);
        }
    }

    private String extractTicketIdFromGoal(String goal) {
        if (goal == null || goal.isBlank()) return null;

        Pattern pattern = Pattern.compile("([A-Z]+-\\d+)");
        Matcher matcher = pattern.matcher(goal);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
