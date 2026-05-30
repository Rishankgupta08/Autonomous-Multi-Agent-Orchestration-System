package com.moae.service;

import com.moae.dto.UserDefaultsDTO;
import com.moae.entity.User;
import com.moae.entity.UserDefaults;
import com.moae.repository.UserDefaultsRepository;
import com.moae.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Business logic for reading and saving per-user planner defaults.
 *
 * Responsibilities:
 *   getDefaults  — load the user_defaults row and map it to a DTO.
 *                  Returns an all-null DTO (not null itself) when no row exists,
 *                  so callers never need a null check.
 *
 *   saveDefaults — upsert: update the existing row or create a new one.
 *                  Null-safe merge: only fields that arrive non-null in the
 *                  request DTO overwrite the stored value.  This lets the
 *                  frontend send a partial payload (e.g. only githubOwner)
 *                  without wiping the other three fields.
 *
 * Transaction strategy:
 *   @Transactional on saveDefaults — both the optional SELECT and the INSERT/UPDATE
 *   run inside a single transaction so there is no race between "not found" and "insert".
 *   getDefaults is read-only; @Transactional(readOnly = true) avoids acquiring a
 *   write lock and signals Hibernate to skip dirty-checking.
 *
 * No HTTP logic, no session access — those belong in the controller.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserDefaultsService {

    private final UserDefaultsRepository userDefaultsRepository;
    private final UserRepository userRepository;

    // =========================================================================
    // READ
    // =========================================================================

    /**
     * Returns the planner defaults for the given user.
     *
     * Never returns null: if the user has never saved defaults an all-null DTO
     * is returned so the frontend can render empty form fields gracefully.
     *
     * @param userId UUID of the authenticated user
     * @return UserDefaultsDTO populated from DB, or an empty DTO if no row exists
     */
    @Transactional(readOnly = true)
    public UserDefaultsDTO getDefaults(UUID userId) {
        Optional<UserDefaults> existing = userDefaultsRepository.findByUserId(userId);

        if (existing.isEmpty()) {
            log.debug("No defaults row found for userId={} — returning empty DTO", userId);
            // Return an all-null DTO; callers should treat every field as optional
            return UserDefaultsDTO.builder().build();
        }

        UserDefaults defaults = existing.get();
        log.debug("Loaded defaults for userId={}: owner={}, repo={}, jira={}, slack={}",
                userId,
                defaults.getGithubOwner(),
                defaults.getGithubDefaultRepo(),
                defaults.getJiraProjectKey(),
                defaults.getSlackDefaultChannel());

        return toDto(defaults);
    }

    // =========================================================================
    // UPSERT
    // =========================================================================

    /**
     * Saves (creates or updates) the planner defaults for the given user.
     *
     * Merge strategy — null-safe field update:
     *   If a field in the incoming DTO is non-null → overwrite the stored value.
     *   If a field in the incoming DTO is null     → keep the existing stored value.
     * This lets clients send a partial payload to update only specific fields.
     *
     * @param userId UUID of the authenticated user
     * @param dto    inbound DTO (may have some or all fields set)
     * @return UserDefaultsDTO reflecting the state after the save
     */
    @Transactional
    public UserDefaultsDTO saveDefaults(UUID userId, UserDefaultsDTO dto) {
        Optional<UserDefaults> existing = userDefaultsRepository.findByUserId(userId);

        UserDefaults defaults;
        if (existing.isPresent()) {
            // ── UPDATE: merge non-null dto fields onto the existing row ──────
            defaults = existing.get();
            log.info("Updating defaults for userId={}", userId);
        } else {
            // ── INSERT: create a new row linked to this user ─────────────────
            // getReferenceById returns a JPA proxy — avoids a redundant SELECT
            // just to obtain a User object for the FK reference.
            User userRef = userRepository.getReferenceById(userId);
            defaults = UserDefaults.builder()
                    .user(userRef)
                    .build();
            log.info("Creating new defaults row for userId={}", userId);
        }

        // Null-safe merge: only overwrite fields that are explicitly provided.
        // This means PATCH-style partial updates work even though the endpoint
        // is POST — the frontend can send only the changed field(s).
        if (dto.getGithubOwner() != null) {
            defaults.setGithubOwner(dto.getGithubOwner());
        }
        if (dto.getGithubDefaultRepo() != null) {
            defaults.setGithubDefaultRepo(dto.getGithubDefaultRepo());
        }
        if (dto.getJiraProjectKey() != null) {
            defaults.setJiraProjectKey(dto.getJiraProjectKey());
        }
        if (dto.getSlackDefaultChannel() != null) {
            defaults.setSlackDefaultChannel(dto.getSlackDefaultChannel());
        }

        UserDefaults saved = userDefaultsRepository.save(defaults);
        log.info("Defaults saved for userId={}: owner={}, repo={}, jira={}, slack={}",
                userId,
                saved.getGithubOwner(),
                saved.getGithubDefaultRepo(),
                saved.getJiraProjectKey(),
                saved.getSlackDefaultChannel());

        return toDto(saved);
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Maps a UserDefaults entity to a UserDefaultsDTO.
     * Kept private — the service owns the mapping; no MapStruct dependency needed
     * for a four-field object.
     */
    private UserDefaultsDTO toDto(UserDefaults entity) {
        return UserDefaultsDTO.builder()
                .githubOwner(entity.getGithubOwner())
                .githubDefaultRepo(entity.getGithubDefaultRepo())
                .jiraProjectKey(entity.getJiraProjectKey())
                .slackDefaultChannel(entity.getSlackDefaultChannel())
                .build();
    }
}
