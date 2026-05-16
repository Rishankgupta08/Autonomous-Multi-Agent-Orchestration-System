package com.moae.repository;

import com.moae.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Data access layer for the User entity (maps to 'users' table).
 *
 * Extends JpaRepository which provides:
 *   save(), findById(), findAll(), delete(), count(), existsById() … out of the box.
 *
 * Custom query methods use Spring Data JPA's method-name derivation:
 *   findByGithubLogin → SELECT * FROM users WHERE github_login = ?
 *
 * No business logic here — repositories are pure data access contracts.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Look up a user by their GitHub login handle (e.g. "rishankgupta").
     *
     * Used by OAuth2LoginSuccessHandler (Phase 2) to determine whether
     * the authenticated GitHub user already has a MOAE account, or if
     * a new User row needs to be created.
     *
     * @param githubLogin the GitHub username (case-sensitive, unique)
     * @return Optional<User> — empty if first login; present if returning user
     */
    Optional<User> findByGithubLogin(String githubLogin);
}
