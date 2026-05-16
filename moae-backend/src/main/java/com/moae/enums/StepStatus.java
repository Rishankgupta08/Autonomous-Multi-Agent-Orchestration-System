package com.moae.enums;

/**
 * Fine-grained lifecycle state of a single WorkflowStep.
 *
 * Stored as VARCHAR STRING in DB.
 *
 * PENDING   → Step exists in the plan but execution has not started yet.
 * ACTIVE    → Executor is currently calling the external API for this step.
 * SUCCESS   → API call returned a 2xx response; result_json is populated.
 * FAILED    → API call failed definitively; failure_reason is set.
 * UNCERTAIN → Response received but Verifier could not confirm success/failure
 *             (e.g., the Jira API returned 200 but no issue was actually created).
 */
public enum StepStatus {
    PENDING,
    ACTIVE,
    SUCCESS,
    FAILED,
    UNCERTAIN
}
