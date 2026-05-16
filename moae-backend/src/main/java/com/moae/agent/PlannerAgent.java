package com.moae.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moae.client.OllamaClient;
import com.moae.util.JsonExtractUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlannerAgent {

    private final OllamaClient ollamaClient;
    private final ObjectMapper objectMapper;

    // Hardcoded safety defaults
    private static final String DEFAULT_SLACK_CHANNEL = "#general"; // Or #devops as instructed in summary, wait. The summary says "fallback mechanisms (e.g., #devops for Slack". But the prompt from user in step 9b said "#general". Ah wait, Step 9b instruction: "#general". In step 1: "Hardcode #general as default Slack channel". So #general.
    private static final String DEFAULT_GITHUB_OWNER = "unknown-owner";
    private static final String DEFAULT_GITHUB_REPO = "unknown-repo";

    public List<Map<String, Object>> planFromNaturalLanguage(
            String userMessage,
            com.moae.client.JiraClient jiraClient,
            com.moae.repository.UserIntegrationRepository userIntegrationRepository,
            java.util.UUID userId,
            com.fasterxml.jackson.databind.ObjectMapper mapper) {

        log.info("PLANNER STARTED | Extracting intent from message: {}", userMessage);

        String extractionPrompt = """
            You are an Intent Extraction Engine.
            Read the user's message and extract ONLY the requested parameters as a JSON object.
            Do not hallucinate. Do not infer. If a value is missing, return null.

            Fields to extract:
            - "ticketId": any Jira ticket key mentioned (e.g. EC-12)
            - "githubOwner": the repository owner (e.g. from a URL or explicit mention)
            - "githubRepo": the repository name (e.g. from a URL or explicit mention)
            - "slackChannel": the slack channel to notify (e.g. #general, #alerts)

            User Message: """ + userMessage + """
            
            Return ONLY valid JSON.
            """;

        String rawIntent = ollamaClient.generate(extractionPrompt);
        log.info("EXTRACTED INTENT RAW: {}", rawIntent);

        String ticketId = null;
        String ghOwner = DEFAULT_GITHUB_OWNER;
        String ghRepo = DEFAULT_GITHUB_REPO;
        String slackChannel = "#general";

        try {
            String cleanJson = JsonExtractUtil.extractJsonArray(rawIntent);
            // extractJsonArray logic: actually we want object, wait. If JsonExtractUtil only extracts Arrays? Let's check JsonExtractUtil.
            // But we can parse rawIntent. Let's use `extractJsonArray` if it extracts object too? Or just use objectMapper.
            // Let's use custom string manipulation or just extract the first { to last }.
            int start = rawIntent.indexOf('{');
            int end = rawIntent.lastIndexOf('}');
            if (start != -1 && end != -1) {
                String jsonObjStr = rawIntent.substring(start, end + 1);
                Map<String, String> intentObj = mapper.readValue(jsonObjStr, new com.fasterxml.jackson.core.type.TypeReference<>() {});
                ticketId = intentObj.get("ticketId");
                
                if (intentObj.containsKey("githubOwner") && intentObj.get("githubOwner") != null) {
                    ghOwner = intentObj.get("githubOwner");
                }
                if (intentObj.containsKey("githubRepo") && intentObj.get("githubRepo") != null) {
                    ghRepo = intentObj.get("githubRepo");
                }
                if (intentObj.containsKey("slackChannel") && intentObj.get("slackChannel") != null) {
                    slackChannel = intentObj.get("slackChannel");
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse intent JSON, using fallbacks. Raw: {}", rawIntent);
        }

        log.info("FINAL INTENT -> ticketId={}, ghOwner={}, ghRepo={}, slackChannel={}", 
            ticketId, ghOwner, ghRepo, slackChannel);

        if (ticketId != null && !ticketId.trim().isEmpty() && !"null".equalsIgnoreCase(ticketId)) {
            // It's a ticket workflow
            log.info("Routing to Ticket-Aware Planning");
            
            Map<String, Object> jiraConfig = userIntegrationRepository
                .findByUserIdAndIntegrationTypeAndIsActiveTrue(userId, com.moae.enums.IntegrationType.JIRA)
                .map(this::parseConfigJson)
                .orElseThrow(() -> new RuntimeException("Jira not connected. Cannot process ticket."));

            String domain = (String) jiraConfig.get("domain");
            String email = (String) jiraConfig.get("email");
            String apiToken = (String) jiraConfig.get("apiToken");

            com.moae.client.dto.JiraTicketResponse ticket = jiraClient.getTicket(domain, email, apiToken, ticketId);
            
            // Pass the extracted intent values into the ticket prompt
            return planFromTicketWithIntent(ticket, ghOwner, ghRepo, slackChannel, userMessage);
        } else {
            // Text-based workflow
            log.info("Routing to Text-Based Planning");
            return plan(userMessage);
        }
    }

    private Map<String, Object> parseConfigJson(com.moae.entity.UserIntegration i) {
        try {
            return objectMapper.readValue(i.getConfigJson(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse integration config JSON: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> planFromTicketWithIntent(
            com.moae.client.dto.JiraTicketResponse ticket, 
            String owner, String repo, String slackChannel, String userMessage) {
        
        log.info("PLANNER STARTED (Ticket-Aware + Intent) | ticket={}", ticket.issueKey());

        String prompt = String.format("""
                You are a workflow planner for MOAE, a developer automation system.
                Your job is to decompose a Jira ticket into a sequence of executable steps.
                
                User Message: %s
                Ticket Summary: %s
                Ticket Description: %s
                Ticket Key: %s

                Target Repo Owner: %s
                Target Repo Name: %s
                Target Slack Channel: %s

                Available tools and their actions:
                - tool: "github" → actions: getFile, createBranch, pushFile, createPR, triggerAction
                - tool: "jira"   → actions: createTicket, updateStatus
                - tool: "slack"  → actions: sendMessage
                - tool: "llm"    → actions: generateCode

                Rules:
                1. Return ONLY a valid JSON array. No explanation. No markdown. No backticks.
                2. Each step must have exactly three fields: "tool", "action", "params"
                3. "params" must be a JSON object with the relevant parameters for that action
                4. Logical order matters: fetch before modify, create branch before push, push before PR
                5. Only include steps that are necessary to fulfill the ticket
                6. When creating a branch, use the ticket key in the branch name (e.g. %s-feature)
                7. When creating a PR or pushing, mention the ticket key in the title/message
                8. Use the EXACT Target Repo Owner, Target Repo Name, and Target Slack Channel provided above for any GitHub or Slack actions.
                9. Update ticket status to "In Progress" or "Done" as appropriate.
                
                Parameter names per action (use these exact param key names):
                - getFile:       owner, repo, filePath
                - createBranch:  repo, newBranchName, baseBranch
                - pushFile:      owner, repo, filePath, content, commitMessage, branchName
                - createPR:      owner, repo, title, head, base
                - triggerAction: owner, repo, workflowId, ref
                - createTicket:  projectKey, summary, description
                - updateStatus:  issueId, transitionId
                - sendMessage:   channel, text
                - generateCode:  instruction
                """, userMessage, ticket.summary(), ticket.description(), ticket.issueKey(), 
                owner, repo, slackChannel, ticket.issueKey());

        return executePlanGeneration(prompt, "Ticket: " + ticket.issueKey());
    }

    public List<Map<String, Object>> plan(String goal) {
        log.info("PLANNER STARTED | goal={}", goal);

        // ── Build prompt — NO hardcoded project keys or channels ──────
        String prompt = """
                You are a workflow planner for MOAE, a developer automation system.
                Your job is to decompose a developer goal into a sequence of executable steps.

                Available tools and their actions:
                - tool: "github" → actions: getFile, createBranch, pushFile, createPR, triggerAction
                - tool: "jira"   → actions: createTicket, updateStatus
                - tool: "slack"  → actions: sendMessage
                - tool: "llm"    → actions: generateCode

                Rules:
                1. Return ONLY a valid JSON array. No explanation. No markdown. No backticks.
                2. Each step must have exactly three fields: "tool", "action", "params"
                3. "params" must be a JSON object with the relevant parameters for that action
                4. Logical order matters: fetch before modify, create branch before push, push before PR
                5. Only include steps that are necessary for the goal
                6. Read the goal carefully — use any project keys, channel names, repo names,
                   or branch names mentioned explicitly in the goal text
                7. If the goal does not mention a specific value, use a sensible short placeholder
                   that matches the context (e.g. the topic of the task for a project key)

                Parameter names per action (use these exact param key names):
                - getFile:       owner, repo, filePath
                - createBranch:  repo, newBranchName, baseBranch
                - pushFile:      owner, repo, filePath, content, commitMessage, branchName
                - createPR:      owner, repo, title, head, base
                - triggerAction: owner, repo, workflowId, ref
                - createTicket:  projectKey, summary, description
                - updateStatus:  issueId, transitionId
                - sendMessage:   channel, text
                - generateCode:  instruction

                Goal: """ + goal + """
                """;

        return executePlanGeneration(prompt, goal);
    }

    public List<Map<String, Object>> planFromTicket(com.moae.client.dto.JiraTicketResponse ticket) {
        log.info("PLANNER STARTED (Ticket-Aware) | ticket={}", ticket.issueKey());

        String prompt = String.format("""
                You are a workflow planner for MOAE, a developer automation system.
                Your job is to decompose a Jira ticket into a sequence of executable steps.
                
                Ticket Summary: %s
                Ticket Description: %s
                Ticket Key: %s

                Available tools and their actions:
                - tool: "github" → actions: getFile, createBranch, pushFile, createPR, triggerAction
                - tool: "jira"   → actions: createTicket, updateStatus
                - tool: "slack"  → actions: sendMessage
                - tool: "llm"    → actions: generateCode

                Rules:
                1. Return ONLY a valid JSON array. No explanation. No markdown. No backticks.
                2. Each step must have exactly three fields: "tool", "action", "params"
                3. "params" must be a JSON object with the relevant parameters for that action
                4. Logical order matters: fetch before modify, create branch before push, push before PR
                5. Only include steps that are necessary to fulfill the ticket
                6. When creating a branch, use the ticket key in the branch name (e.g. %s-feature)
                7. When creating a PR or pushing, mention the ticket key in the title/message
                8. If the ticket description specifies a repo or file, use those parameters
                
                Parameter names per action (use these exact param key names):
                - getFile:       owner, repo, filePath
                - createBranch:  repo, newBranchName, baseBranch
                - pushFile:      owner, repo, filePath, content, commitMessage, branchName
                - createPR:      owner, repo, title, head, base
                - triggerAction: owner, repo, workflowId, ref
                - createTicket:  projectKey, summary, description
                - updateStatus:  issueId, transitionId
                - sendMessage:   channel, text
                - generateCode:  instruction
                """, ticket.summary(), ticket.description(), ticket.issueKey(), ticket.issueKey());

        return executePlanGeneration(prompt, "Ticket: " + ticket.issueKey());
    }

    private List<Map<String, Object>> executePlanGeneration(String prompt, String goalContext) {
        // ── Call OpenRouter ───────────────────────────────────────────
        String rawResponse = ollamaClient.generate(prompt);
        log.debug("PlannerAgent raw OpenRouter response: {}", rawResponse);

        // ── Extract JSON array from response ──────────────────────────
        String jsonArray;
        try {
            jsonArray = JsonExtractUtil.extractJsonArray(rawResponse);
            log.info("EXTRACTED JSON:\n{}", jsonArray);
        } catch (RuntimeException e) {
            log.error("PlannerAgent failed to extract JSON array from response. " +
                      "Raw response: {}", rawResponse);
            log.error("FULL STACKTRACE", e);
            throw new RuntimeException(
                    "Planner could not extract JSON plan from LLM response: "
                    + e.getMessage(), e);
        }

        // ── Parse JSON into List<Map> ─────────────────────────────────
        List<Map<String, Object>> plan;
        try {
            plan = objectMapper.readValue(
                    jsonArray,
                    new TypeReference<List<Map<String, Object>>>() {});
        } catch (JsonProcessingException e) {
            log.error("PlannerAgent failed to parse JSON. Extracted: {} | " +
                      "Raw: {}", jsonArray, rawResponse);
            log.error("FULL STACKTRACE", e);
            throw new RuntimeException(
                    "Failed to parse Planner JSON response: " + e.getMessage(), e);
        }

        // ── Validate plan ─────────────────────────────────────────────
        if (plan == null || plan.isEmpty()) {
            throw new RuntimeException("Planner returned empty plan for goal: " + goalContext);
        }

        for (int i = 0; i < plan.size(); i++) {
            Map<String, Object> step = plan.get(i);
            if (!step.containsKey("tool") ||
                !step.containsKey("action") ||
                !step.containsKey("params")) {
                throw new RuntimeException(
                        "Invalid step structure at index " + i +
                        " — missing tool, action, or params field. Step: " + step);
            }
        }

        log.info("PARSED STEPS SIZE: {}", plan.size());
        return plan;
    }
}
