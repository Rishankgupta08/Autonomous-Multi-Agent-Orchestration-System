package com.moae.repository;

import com.moae.entity.UserDefaults;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Data access layer for the UserDefaults entity (maps to 'user_defaults' table).
 *
 * Custom query methods:
 *
 *   findByUserId
 *     → SELECT * FROM user_defaults WHERE user_id = ?
 *       Used by the Planner Agent to load per-user defaults before generating
 *       a plan, so the user doesn't need to repeat their repo, project key, or
 *       Slack channel in every prompt.
 *       Returns Optional because a user may not have saved any defaults yet —
 *       callers fall back to hardcoded planner defaults when empty.
 *
 * No business logic here — pure data access only.
 */
@Repository
public interface UserDefaultsRepository extends JpaRepository<UserDefaults, UUID> {

    /**
     * Find the defaults row for a specific user.
     *
     * Used by the Planner Agent at workflow start:
     *   plannerService.loadDefaults(userId)
     *
     * UNIQUE constraint on user_id guarantees at most one row, so Optional
     * is the correct return type (not List).
     *
     * @param userId UUID of the owning User
     * @return Optional<UserDefaults> — empty if the user has never saved defaults
     */
    Optional<UserDefaults> findByUserId(UUID userId);
}
