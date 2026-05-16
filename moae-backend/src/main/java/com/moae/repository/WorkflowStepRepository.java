package com.moae.repository;

import com.moae.entity.WorkflowStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access layer for the WorkflowStep entity (maps to 'workflow_steps' table).
 *
 * Custom query methods:
 *
 *   findByWorkflowRunIdOrderByStepIdAsc
 *     → SELECT * FROM workflow_steps WHERE workflow_id = ? ORDER BY step_id ASC
 *       Reconstructs the Planner's original execution order (stepId 1, 2, 3 …).
 *       Used by GET /api/workflow/{id}/steps to display the step-by-step breakdown.
 *
 *   findByWorkflowRunId
 *     → SELECT * FROM workflow_steps WHERE workflow_id = ?
 *       Returns ALL steps for a run without a guaranteed order.
 *       Used by the Verifier Agent which needs all step results to reason over,
 *       not just the ordered display list.
 *
 * No business logic here — pure data access only.
 */
@Repository
public interface WorkflowStepRepository extends JpaRepository<WorkflowStep, UUID> {

    /**
     * Return all steps for a workflow run, ordered by their plan position (ascending).
     *
     * Preserves the exact sequence the Planner intended:
     *   step 1 → step 2 → step 3 …
     *
     * Used by the REST controller (GET /api/workflow/{id}/steps) so the UI renders
     * the timeline in the correct top-to-bottom order.
     *
     * @param workflowRunId UUID of the parent WorkflowRun
     * @return ordered list of steps; empty if no steps have been created yet
     */
    List<WorkflowStep> findByWorkflowRunIdOrderByStepIdAsc(UUID workflowRunId);

    /**
     * Return all steps for a workflow run (no ordering guarantee).
     *
     * Used by the Verifier Agent which reads every step's resultJson to
     * reason about the overall outcome. Ordering does not matter for LLM input.
     *
     * @param workflowRunId UUID of the parent WorkflowRun
     * @return unordered list of all steps for this run
     */
    List<WorkflowStep> findByWorkflowRunId(UUID workflowRunId);

    /**
     * Find a specific step by its parent run and ordinal position.
     *
     * The UUID PK of a WorkflowStep is not known to ExecutorAgent at runtime —
     * only the workflowRunId and the 1-based stepId position are available.
     * This method is the lookup key for updating PENDING → ACTIVE → SUCCESS/FAILED
     * as the Executor processes each step in sequence.
     *
     * @param workflowRunId UUID of the parent WorkflowRun
     * @param stepId        1-based ordinal position from the Planner's plan
     * @return Optional<WorkflowStep> — empty only if plan vs DB are out of sync
     */
    Optional<WorkflowStep> findByWorkflowRunIdAndStepId(UUID workflowRunId, int stepId);
}
