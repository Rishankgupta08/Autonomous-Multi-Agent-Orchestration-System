package com.moae.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity mapping to the 'users' table.
 *
 * Represents a developer who has authenticated via GitHub OAuth2 (Phase 2).
 * In Phase 1 we define the schema only — the OAuth2 handler will be wired in Step 2.
 *
 * Design notes:
 *   - @Data is NEVER used on JPA entities (causes LazyInitializationException and
 *     infinite loops in bidirectional relationships).
 *   - @Builder.Default on list fields prevents the Lombok builder from setting
 *     them to NULL instead of an empty ArrayList.
 *   - FetchType.LAZY on all collections — avoids N+1 queries.
 *   - CascadeType.ALL — deleting a User also deletes their integrations and runs.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    // -------------------------------------------------------------------------
    // Primary Key
    // GenerationType.UUID → Hibernate 6 native UUID generation (Spring Boot 3.x).
    // NEVER use @GenericGenerator — that is Hibernate 5 / Spring Boot 2.x legacy.
    // -------------------------------------------------------------------------
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // -------------------------------------------------------------------------
    // GitHub identity fields
    // -------------------------------------------------------------------------
    @Column(name = "github_login", nullable = false, unique = true)
    private String githubLogin;

    @Column(name = "name")
    private String name;

    @Column(name = "email")
    private String email;

    // avatar URL can be long (GitHub CDN URLs with query params)
    @Column(name = "avatar_url", length = 1024)
    private String avatarUrl;

    // GitHub OAuth2 access token — used to call GitHub API on behalf of the user.
    // VARCHAR(1024) is sufficient; GitHub tokens are ~40 chars but we give headroom.
    @Column(name = "github_access_token", length = 1024)
    private String githubAccessToken;

    // -------------------------------------------------------------------------
    // Audit timestamp
    // @CreationTimestamp → Hibernate sets this automatically on INSERT.
    // updatable = false  → value is locked once written; never changed by Hibernate.
    // -------------------------------------------------------------------------
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // -------------------------------------------------------------------------
    // Relationships
    //
    // @OneToMany with CascadeType.ALL: when a User is deleted, Hibernate
    // automatically deletes all related UserIntegration and WorkflowRun rows.
    //
    // mappedBy = "user" → the foreign key lives on the child entity, not here.
    // FetchType.LAZY    → collections are not loaded until explicitly accessed.
    // @Builder.Default  → Lombok builder initialises list to new ArrayList<>()
    //                     instead of null (which would cause NullPointerException).
    // -------------------------------------------------------------------------

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<UserIntegration> integrations = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<WorkflowRun> workflowRuns = new ArrayList<>();
}
