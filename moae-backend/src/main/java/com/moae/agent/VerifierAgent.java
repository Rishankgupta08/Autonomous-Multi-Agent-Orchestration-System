package com.moae.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moae.agent.dto.StepResult;
import com.moae.agent.dto.VerificationResult;
import com.moae.client.MoaeClientException;
import com.moae.enums.StepStatus;
import com.moae.service.GroqLlmService;
import com.moae.util.JsonExtractUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class VerifierAgent {

    private final GroqLlmService groqLlmService;
    private final ObjectMapper objectMapper;

    public VerificationResult verify(String goal, List<StepResult> stepResults) {

        // ── STEP A: Compute taskCompletion from ground truth ──────────
        long successCount = stepResults.stream()
                .filter(s -> s.getStatus() == StepStatus.SUCCESS)
                .count();
        int taskCompletion = stepResults.isEmpty() ? 0 :
                (int) Math.round((successCount * 100.0) / stepResults.size());

        // ── STEP B: Build steps summary for prompt ────────────────────
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stepResults.size(); i++) {
            StepResult result = stepResults.get(i);
            sb.append("Step ").append(i + 1).append(": ")
              .append(result.getTool()).append(".").append(result.getAction())
              .append(" → ").append(result.getStatus());
            if (result.getStatus() == StepStatus.FAILED) {
                sb.append(" [").append(result.getFailureReason()).append("] ")
                  .append(result.getErrorMessage());
            }
            sb.append("\n");
        }

        // ── STEP C: Build verifier prompt ─────────────────────────────
        String verifierPrompt = """
                You are a workflow verifier for MOAE, a developer automation system.
                Analyse the execution below and return a JSON evaluation.

                Original goal: """ + goal + """

                Execution results:
                """ + sb + """

                Return ONLY a valid JSON object with exactly these fields. No explanation. No markdown.

                {
                  "verdict": "SUCCESS" or "FAIL",
                  "decisionAccuracy": <integer 0-100>,
                  "executionEfficiency": <integer 0-100>,
                  "contextRelevance": <integer 0-100>,
                  "summary": "<one paragraph plain-English explanation>"
                }

                Verdict rules:
                - "SUCCESS" only if ALL steps succeeded
                - "FAIL" if ANY step failed or the goal appears not achieved

                IMPORTANT: All numeric fields MUST be plain integer literals (e.g. 90, 75, 100).
                Do NOT write numbers as words (e.g. "ninety" is invalid — write 90).
                Do NOT wrap the JSON in markdown or backticks.
                Do NOT include <think> tags or any reasoning text.
                Output ONLY the raw JSON object starting with { and ending with }.
                """;

        String rawResponse = null;

        try {
            // ── STEP D: Call Groq ─────────────────────────────────────
            rawResponse = groqLlmService.verifierCall(verifierPrompt);
            log.debug("VerifierAgent raw response: {}", rawResponse);

            // ── STEP E: Extract and sanitize JSON object ──────────────
            String jsonObj = JsonExtractUtil.extractJsonObject(rawResponse);
            jsonObj = sanitizeJsonNumbers(jsonObj);
            Map<String, Object> parsed = objectMapper.readValue(
                    jsonObj, new TypeReference<Map<String, Object>>() {});

            // ── STEP F: Extract fields safely ─────────────────────────
            String verdict     = (String) parsed.getOrDefault("verdict", "FAIL");
            int decisionAcc    = toInt(parsed.get("decisionAccuracy"),   50);
            int execEfficiency = toInt(parsed.get("executionEfficiency"), 50);
            int contextRel     = toInt(parsed.get("contextRelevance"),    50);
            String summary     = (String) parsed.getOrDefault("summary",
                    "Verification complete.");

            // ── STEP G: Compute overallScore ──────────────────────────
            int overallScore = (taskCompletion + decisionAcc +
                                execEfficiency + contextRel) / 4;

            log.info("VerifierAgent verdict: {} | score: {}", verdict, overallScore);

            return VerificationResult.builder()
                    .verdict(verdict)
                    .overallScore(overallScore)
                    .taskCompletion(taskCompletion)
                    .decisionAccuracy(decisionAcc)
                    .executionEfficiency(execEfficiency)
                    .contextRelevance(contextRel)
                    .summary(summary)
                    .build();

        } catch (MoaeClientException e) {
            log.error("VerifierAgent: Groq unavailable ({}). " +
                      "Using programmatic fallback scoring.", e.getMessage());
            return buildFallbackResult(stepResults,
                    "Groq unavailable: " + e.getMessage());

        } catch (JsonProcessingException e) {
            log.warn("VerifierAgent: Failed to parse Groq JSON response. " +
                     "Using programmatic fallback. Raw response: {}", rawResponse);
            return buildFallbackResult(stepResults, "AI response parsing failed.");

        } catch (Exception e) {
            log.error("VerifierAgent unexpected error: {}", e.getMessage(), e);
            return buildFallbackResult(stepResults,
                    "Unexpected error: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // SANITIZER: rewrite bare-word number literals before JSON parsing.
    // Some LLMs write "decisionAccuracy": ninety instead of 90.
    // This regex replaces known English word-numbers with their integer
    // equivalents so Jackson can parse the JSON without blowing up.
    // ─────────────────────────────────────────────────────────────────
    private static final Map<String, String> WORD_NUMBERS = Map.ofEntries(
            Map.entry("zero",         "0"),
            Map.entry("ten",         "10"),
            Map.entry("twenty",      "20"),
            Map.entry("thirty",      "30"),
            Map.entry("forty",       "40"),
            Map.entry("fifty",       "50"),
            Map.entry("sixty",       "60"),
            Map.entry("seventy",     "70"),
            Map.entry("eighty",      "80"),
            Map.entry("ninety",      "90"),
            Map.entry("hundred",    "100"),
            Map.entry("one hundred","100")
    );

    private String sanitizeJsonNumbers(String json) {
        String result = json;
        for (Map.Entry<String, String> entry : WORD_NUMBERS.entrySet()) {
            // Match the bare word after a colon+whitespace, surrounded by optional whitespace
            // e.g. "decisionAccuracy": ninety,
            result = result.replaceAll(
                    "(?i)(:\\s*)" + entry.getKey() + "(\\s*[,}])",
                    "$1" + entry.getValue() + "$2");
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────
    // FALLBACK: honest score computed from actual step execution results
    // Used when AI verification is unavailable (rate limit, timeout, etc.)
    // ─────────────────────────────────────────────────────────────────
    private VerificationResult buildFallbackResult(
            List<StepResult> stepResults, String reason) {

        if (stepResults == null || stepResults.isEmpty()) {
            return VerificationResult.builder()
                    .verdict("FAIL")
                    .overallScore(0)
                    .taskCompletion(0)
                    .decisionAccuracy(0)
                    .executionEfficiency(0)
                    .contextRelevance(0)
                    .summary("No steps executed. Reason: " + reason)
                    .build();
        }

        long successCount = stepResults.stream()
                .filter(s -> s.getStatus() == StepStatus.SUCCESS)
                .count();

        int taskCompletion = (int) Math.round(
                (successCount * 100.0) / stepResults.size());

        // Verdict from ground truth — not from AI
        String verdict = (taskCompletion == 100) ? "SUCCESS" : "FAIL";

        // Conservative estimate: use taskCompletion for all AI-derived scores
        int fallbackScore = taskCompletion;

        String summary = String.format(
                "AI verification unavailable (%s). " +
                "Score computed from execution results: %d/%d steps succeeded. " +
                "All sub-scores estimated from task completion rate.",
                reason, successCount, stepResults.size());

        log.info("VerifierAgent fallback: verdict={} | score={} | steps={}/{}",
                verdict, fallbackScore, successCount, stepResults.size());

        return VerificationResult.builder()
                .verdict(verdict)
                .overallScore(fallbackScore)
                .taskCompletion(taskCompletion)
                .decisionAccuracy(fallbackScore)
                .executionEfficiency(fallbackScore)
                .contextRelevance(fallbackScore)
                .summary(summary)
                .build();
    }

    /**
     * Safely converts a Jackson-parsed JSON value to int.
     *
     * Jackson deserialises JSON numbers without a declared type as Double by
     * default, so we must handle Double explicitly. We also cover Integer,
     * Long, Number (generic), and String (for quote-wrapped numbers like "90").
     */
    private int toInt(Object val, int defaultVal) {
        if (val == null)           return defaultVal;
        if (val instanceof Integer i) return i;
        if (val instanceof Long l)    return l.intValue();
        if (val instanceof Double d)  return d.intValue();
        if (val instanceof Number n)  return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s.trim()); }
            catch (NumberFormatException e) { return defaultVal; }
        }
        return defaultVal;
    }
}

