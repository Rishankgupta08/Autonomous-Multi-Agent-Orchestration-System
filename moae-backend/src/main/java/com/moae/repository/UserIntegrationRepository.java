package com.moae.repository;

import com.moae.entity.UserIntegration;
import com.moae.enums.IntegrationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access layer for the UserIntegration entity (maps to 'user_integrations' table).
 *
 * Custom query methods:
 *
 *   findByUserIdAndIntegrationTypeAndIsActiveTrue
 *     → SELECT * FROM user_integrations
 *          WHERE user_id = ? AND integration_type = ? AND is_active = true
 *       Used by the Executor Agent to retrieve the ACTIVE Jira or Slack credentials
 *       for the current user before making an API call.
 *       Returns Optional because a user may not have configured that integration yet.
 *
 *   findByUserId
 *     → SELECT * FROM user_integrations WHERE user_id = ?
 *       Used by the dashboard / settings endpoint to list all of a user's integrations
 *       (both active and inactive) for display in the UI.
 *
 * No business logic here — pure data access only.
 */
@Repository
public interface UserIntegrationRepository extends JpaRepository<UserIntegration, UUID> {

    /**
     * Find the ACTIVE integration of a specific type for a given user.
     *
     * Used by Executor Agent at runtime:
     *   executorService.getActiveJiraConfig(userId)  → type = JIRA
     *   executorService.getActiveSlackConfig(userId) → type = SLACK
     *
     * @param userId          UUID of the owning User
     * @param integrationType JIRA or SLACK
     * @return Optional<UserIntegration> — empty if user has not connected this service
     */
    Optional<UserIntegration> findByUserIdAndIntegrationTypeAndIsActiveTrue(
            UUID userId,
            IntegrationType integrationType
    );

    /**
     * Find ALL integrations for a user, regardless of isActive flag.
     *
     * Used by settings/profile endpoints to show connected and disconnected services.
     *
     * @param userId UUID of the owning User
     * @return list of all UserIntegration rows for this user (may be empty)
     */
    List<UserIntegration> findByUserId(UUID userId);
}
