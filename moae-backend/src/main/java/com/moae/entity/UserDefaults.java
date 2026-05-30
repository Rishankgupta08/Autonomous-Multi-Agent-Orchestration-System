package com.moae.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity mapping to the 'user_defaults' table.
 *
 * Stores per-user planner defaults so the AI can pre-fill GitHub owner/repo,
 * Jira project key, and Slack channel without the user having to repeat them
 * in every prompt.
 *
 * Design notes:
 *   - @OneToOne: exactly one defaults row per user.  The FK lives on THIS table
 *     (user_id column), making UserDefaults the owning side.
 *   - All payload columns are nullable — the row can exist with only some fields
 *     filled in; missing values fall back to the planner's hardcoded defaults.
 *   - @UpdateTimestamp → Hibernate sets updatedAt on every INSERT and UPDATE
 *     automatically (no application code needed).
 *   - No @CreationTimestamp: updatedAt doubles as the creation timestamp for
 *     a simple "last saved" semantic.
 *   - @Data is NEVER used on JPA entities (infinite loop risk on bidirectional
 *     relationships); use @Getter + @Setter instead.
 */
@Entity
@Table(
    name = "user_defaults",
    uniqueConstraints = @UniqueConstraint(
        name = "unique_user_defaults",
        columnNames = "user_id"
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDefaults {

    // -------------------------------------------------------------------------
    // Primary Key
    // GenerationType.UUID → Hibernate 6 native UUID generation (Spring Boot 3.x).
    // -------------------------------------------------------------------------
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // -------------------------------------------------------------------------
    // Parent relationship: FK → users.id
    //
    // @OneToOne + @JoinColumn: the foreign key column (user_id) lives on this
    // table, so UserDefaults is the *owning* side of the association.
    //
    // optional = false  → user_id is NOT NULL (matches the DDL constraint).
    // FetchType.LAZY    → User is not hydrated unless explicitly accessed;
    //                     avoids a redundant join on every defaults lookup.
    // No cascade        → UserDefaults does not manage the User lifecycle;
    //                     deletion is handled by ON DELETE CASCADE in the DB.
    // -------------------------------------------------------------------------
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // -------------------------------------------------------------------------
    // Planner default fields — all nullable.
    // Missing values → Planner falls back to its own hardcoded defaults.
    // -------------------------------------------------------------------------

    /** GitHub organisation or personal account login (e.g. "acme-corp"). */
    @Column(name = "github_owner", length = 255)
    private String githubOwner;

    /** Default repository name within githubOwner (e.g. "backend-api"). */
    @Column(name = "github_default_repo", length = 255)
    private String githubDefaultRepo;

    /** Jira project key to use when the user doesn't mention one (e.g. "EC"). */
    @Column(name = "jira_project_key", length = 50)
    private String jiraProjectKey;

    /** Slack channel for notifications (e.g. "#devops"). */
    @Column(name = "slack_default_channel", length = 100)
    private String slackDefaultChannel;

    // -------------------------------------------------------------------------
    // Audit timestamp
    // @UpdateTimestamp → Hibernate sets this on every INSERT and UPDATE.
    // -------------------------------------------------------------------------
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
