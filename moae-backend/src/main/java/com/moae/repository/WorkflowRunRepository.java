package com.moae.repository;

import com.moae.entity.WorkflowRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access layer for the WorkflowRun entity (maps to 'workflow_runs' table).
 *
 * Custom query methods:
 *
 *   findByUserIdOrderByCreatedAtDesc
 *     → SELECT * FROM workflow_runs WHERE user_id = ? ORDER BY created_at DESC
 *       Returns a user's workflow history, newest first.
 *       Used by GET /api/workflow/history.
 *
 *   findByIdAndUserId
 *     → SELECT * FROM workflow_runs WHERE id = ? AND user_id = ?
 *       Fetches a specific run only if it belongs to the requesting user.
 *       This dual-key lookup is a lightweight authorization guard that prevents
 *       User A from fetching User B's workflow run by guessing a UUID.
 *       Used by GET /api/workflow/{id}.
 *
 * No business logic here — pure data access only.
 */
@Repository
public interface WorkflowRunRepository extends JpaRepository<WorkflowRun, UUID> {

    /**
     * Return all workflow runs for a user, ordered newest-first.
     *
     * @param userId UUID of the owning User
     * @return ordered list; may be empty for a new user
     */
    List<WorkflowRun> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Return a specific workflow run ONLY if it belongs to the given user.
     *
     * This method doubles as a data-ownership guard:
     *   - If the run exists but belongs to a different user → returns empty.
     *   - If the run does not exist at all → returns empty.
     * Either way the controller returns HTTP 404, revealing nothing about
     * whether the UUID belongs to someone else.
     *
     * @param id     UUID of the WorkflowRun
     * @param userId UUID of the requesting User
     * @return Optional<WorkflowRun>
     */
    Optional<WorkflowRun> findByIdAndUserId(UUID id, UUID userId);

    Optional<WorkflowRun> findByPrNumber(Integer prNumber);
}
