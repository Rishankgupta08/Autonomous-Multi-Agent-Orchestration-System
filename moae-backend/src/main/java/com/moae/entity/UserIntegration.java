package com.moae.entity;

import com.moae.enums.IntegrationType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity mapping to the 'user_integrations' table.
 *
 * Stores the credentials / configuration for a user's external service connection.
 * Each row is one integration for one user (a user can have one JIRA + one SLACK row).
 *
 * config_json structure (stored verbatim as TEXT):
 *   JIRA:  {"domain": "myorg.atlassian.net", "email": "dev@co.com", "apiToken": "..."}
 *   SLACK: {"botToken": "xoxb-..."}
 *
 * The Executor Agent reads config_json at runtime to construct HTTP client headers.
 *
 * Design notes:
 *   - @ManyToOne side does NOT use cascade (only the parent @OneToMany uses cascade).
 *   - FetchType.LAZY → User object is not loaded unless explicitly accessed.
 *   - @Enumerated(STRING) → IntegrationType stored as "JIRA" or "SLACK", never 0/1.
 *   - connectedAt is nullable (no nullable = false) — represents a lifecycle timestamp.
 */
@Entity
@Table(name = "user_integrations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserIntegration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // -------------------------------------------------------------------------
    // Parent relationship: FK → users.id
    // No cascade here — cascading is declared on the User entity's @OneToMany side.
    // -------------------------------------------------------------------------
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // -------------------------------------------------------------------------
    // Integration type — stored as string "JIRA" or "SLACK"
    // -------------------------------------------------------------------------
    @Enumerated(EnumType.STRING)
    @Column(name = "integration_type", nullable = false)
    private IntegrationType integrationType;

    // -------------------------------------------------------------------------
    // JSON config blob — TEXT type to handle arbitrarily long JSON strings.
    // columnDefinition = "TEXT" maps to PostgreSQL TEXT (unlimited length).
    // -------------------------------------------------------------------------
    @Column(name = "config_json", nullable = false, columnDefinition = "TEXT")
    private String configJson;

    // -------------------------------------------------------------------------
    // Soft-active flag — used instead of hard-deleting integrations.
    // The Executor queries WHERE is_active = true to get current credentials.
    // -------------------------------------------------------------------------
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // -------------------------------------------------------------------------
    // Timestamp — auto-set by Hibernate on INSERT, locked thereafter.
    // Nullable is acceptable (no nullable = false annotation).
    // -------------------------------------------------------------------------
    @CreationTimestamp
    @Column(name = "connected_at", updatable = false)
    private LocalDateTime connectedAt;
}
