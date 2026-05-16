package com.moae.enums;

/**
 * Represents the lifecycle state of a WorkflowRun.
 *
 * Stored as a VARCHAR STRING in DB (never ORDINAL — ordinal breaks if enum order changes).
 *
 * RUNNING  → The Planner has created steps; Executor is actively working.
 * SUCCESS  → All steps completed successfully; Verifier approved.
 * PARTIAL  → Some steps succeeded, at least one failed — workflow still had value.
 * FAILED   → Critical failure; the workflow could not achieve its goal.
 */
public enum WorkflowStatus {
    RUNNING,
    SUCCESS,
    PARTIAL,
    FAILED,
    AWAITING_MERGE,
    COMPLETED
}
