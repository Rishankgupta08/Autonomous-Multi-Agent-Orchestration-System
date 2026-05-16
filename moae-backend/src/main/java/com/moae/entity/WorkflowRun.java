package com.moae.entity;

import com.moae.enums.WorkflowStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity mapping to the 'workflow_runs' table.
 *
 * Represents one end-to-end execution of the three-agent pipeline
 * (Planner → Executor → Verifier) triggered by a user's natural language goal.
 *
 * DQ (Decision Quality) score fields (overallScore, taskCompletion, etc.) are
 * all nullable in Phase 1 — they are populated by the Verifier Agent in Phase 3.
 * scoreSummary holds the Verifier's narrative explanation (can be very long → TEXT).
 *
 * completedAt is intentionally nullable:
 *   - NULL  when status = RUNNING  (pipeline is still executing)
 *   - non-NULL once the Verifier writes its final verdict
 *
 * Design notes:
 *   - @Builder.Default on steps list → prevents Lombok builder from setting it NULL.
 *   - CascadeType.ALL → deleting a WorkflowRun cascades to its WorkflowStep rows.
 *   - FetchType.LAZY  → step list is not hydrated unless explicitly accessed.
 */
@Entity
@Table(name = "workflow_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // -------------------------------------------------------------------------
    // Parent user — FK → users.id
    // @ManyToOne child side: NO cascade (parent User@OneToMany owns the cascade).
    // -------------------------------------------------------------------------
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // -------------------------------------------------------------------------
    // Natural language goal entered by the user.
    // TEXT → supports arbitrarily long goal strings without truncation.
    // -------------------------------------------------------------------------
    @Column(name = "goal", nullable = false, columnDefinition = "TEXT")
    private String goal;

    // -------------------------------------------------------------------------
    // Workflow lifecycle status — STRING enum, never ORDINAL.
    // -------------------------------------------------------------------------
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private WorkflowStatus status;

    // -------------------------------------------------------------------------
    // DQ (Decision Quality) score fields — all nullable in Phase 1.
    // Populated by the Verifier Agent in Phase 3.
    // -------------------------------------------------------------------------
    @Column(name = "overall_score")
    private Integer overallScore;

    @Column(name = "task_completion")
    private Integer taskCompletion;

    @Column(name = "decision_accuracy")
    private Integer decisionAccuracy;

    @Column(name = "execution_efficiency")
    private Integer executionEfficiency;

    @Column(name = "context_relevance")
    private Integer contextRelevance;

    // Verifier's narrative explanation of the score — can be a paragraph → TEXT.
    @Column(name = "score_summary", columnDefinition = "TEXT")
    private String scoreSummary;

    @Column(name = "pr_url", length = 1024)
    private String prUrl;

    @Column(name = "pr_number")
    private Integer prNumber;

    @Column(name = "pr_merged")
    private Boolean prMerged;

    // -------------------------------------------------------------------------
    // Timestamps
    // createdAt → auto-set on INSERT, never updated.
    // completedAt → NULL while RUNNING; set once Verifier finishes.
    // -------------------------------------------------------------------------
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;  // intentionally nullable — NULL while RUNNING

    // -------------------------------------------------------------------------
    // Child steps
    // -------------------------------------------------------------------------
    @OneToMany(mappedBy = "workflowRun", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<WorkflowStep> steps = new ArrayList<>();
}
