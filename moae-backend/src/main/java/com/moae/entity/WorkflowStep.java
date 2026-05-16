package com.moae.entity;

import com.moae.enums.FailureReason;
import com.moae.enums.StepStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity mapping to the 'workflow_steps' table.
 *
 * Represents one atomic action in the Planner's execution plan.
 * The Planner generates an ordered list of steps (stepId = 1, 2, 3 …),
 * and the Executor Agent executes them sequentially, updating this row
 * as it goes.
 *
 * field guide:
 *   stepId      → ordinal position from the Planner's JSON plan (1-indexed).
 *   tool        → which external service ("github" | "jira" | "slack").
 *   action      → what to call (getFile, createPR, createIssue, sendMessage …).
 *   paramsJson  → the exact params the Planner specified (stored verbatim).
 *   resultJson  → raw API response body; the Verifier reads this to score the step.
 *   timeTakenMs → wall-clock duration of the HTTP call, in milliseconds.
 *
 * Nullable fields:
 *   paramsJson   → TEXT, nullable (step may have no params).
 *   failureReason → NULL unless status == FAILED.
 *   resultJson   → NULL while PENDING or ACTIVE.
 *   timeTakenMs  → NULL while PENDING or ACTIVE.
 *   completedAt  → NULL while PENDING or ACTIVE.
 */
@Entity
@Table(name = "workflow_steps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // -------------------------------------------------------------------------
    // Parent workflow run — FK → workflow_runs.id
    // @ManyToOne child side: NO cascade.
    // -------------------------------------------------------------------------
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_id", nullable = false)
    private WorkflowRun workflowRun;

    // -------------------------------------------------------------------------
    // Step ordinal — comes from the Planner's numbered plan.
    // Used to ORDER BY step_id ASC when reconstructing the execution sequence.
    // -------------------------------------------------------------------------
    @Column(name = "step_id", nullable = false)
    private Integer stepId;

    // -------------------------------------------------------------------------
    // Tool + action — tell the Executor which HTTP client method to invoke.
    // -------------------------------------------------------------------------
    @Column(name = "tool", nullable = false)
    private String tool;   // "github" | "jira" | "slack"

    @Column(name = "action", nullable = false)
    private String action; // e.g. "getFile", "createPR", "sendMessage"

    // -------------------------------------------------------------------------
    // JSON payloads — both use TEXT to handle arbitrarily large content.
    // -------------------------------------------------------------------------
    @Column(name = "params_json", columnDefinition = "TEXT")
    private String paramsJson;   // Planner-provided step parameters (nullable)

    @Column(name = "result_json", columnDefinition = "TEXT")
    private String resultJson;   // raw API response body (nullable while not done)

    // -------------------------------------------------------------------------
    // Step lifecycle status — STRING enum.
    // Starts as PENDING; transitions: PENDING → ACTIVE → SUCCESS | FAILED | UNCERTAIN
    // -------------------------------------------------------------------------
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StepStatus status;

    // -------------------------------------------------------------------------
    // Failure classification — nullable unless status == FAILED.
    // @Enumerated(STRING) so "CLIENT_ERROR" is stored, not ordinal 0/1/2.
    // -------------------------------------------------------------------------
    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason")
    private FailureReason failureReason;  // nullable — NULL unless FAILED

    // -------------------------------------------------------------------------
    // Execution timing — wall-clock milliseconds.
    // NULL while PENDING or ACTIVE; set by Executor after HTTP call returns.
    // BIGINT maps to Long in Java; sufficient for even very slow API calls.
    // -------------------------------------------------------------------------
    @Column(name = "time_taken_ms")
    private Long timeTakenMs;  // nullable while step hasn't run yet

    // -------------------------------------------------------------------------
    // Timestamps
    // -------------------------------------------------------------------------
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;  // nullable — NULL while PENDING or ACTIVE
}
