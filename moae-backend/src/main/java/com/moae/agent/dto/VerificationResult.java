package com.moae.agent.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Immutable result record for the Verifier Agent's quality assessment.
 *
 * Built by VerifierAgent after calling Ollama and parsing the JSON response.
 * Written to WorkflowRun by WorkflowOrchestrator as the final DB state.
 *
 * Score ranges: all int fields are 0–100.
 *
 * Fields:
 *   verdict            → "SUCCESS" or "FAIL" (exact strings — checked by orchestrator)
 *   overallScore       → average of the 4 sub-scores (computed in VerifierAgent)
 *   taskCompletion     → computed programmatically from step success count (NOT by Ollama)
 *   decisionAccuracy   → from Ollama: how correctly each decision matched the goal
 *   executionEfficiency → from Ollama: speed and clean execution quality
 *   contextRelevance   → from Ollama: how well steps matched the original intent
 *   summary            → Verifier's plain-English paragraph explanation
 */
@Getter
@Builder
public class VerificationResult {

    private String verdict;
    private int    overallScore;
    private int    taskCompletion;
    private int    decisionAccuracy;
    private int    executionEfficiency;
    private int    contextRelevance;
    private String summary;
}
