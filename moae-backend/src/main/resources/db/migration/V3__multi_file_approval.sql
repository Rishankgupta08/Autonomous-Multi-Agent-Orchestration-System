-- ==============================================================================
-- V3 — Multi-file approval support on workflow_runs
--
-- NOTE: This project uses spring.jpa.hibernate.ddl-auto=update.
--       Hibernate applies the schema automatically from @Entity classes.
--       This file is the authoritative SQL reference / audit trail for the
--       changes introduced in this version. Run it manually against a
--       production database where ddl-auto is set to 'validate' or 'none'.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- CHANGE 1: workflow_runs — owner/repo captured at code-review pause time
--
--   pending_owner — GitHub user/org login (e.g. "acme-org") captured when the
--                   workflow pauses after a generateCode step.  Used by the
--                   orchestrator to push additional files on approval without
--                   re-parsing the stored plan JSON.
--
--   pending_repo  — GitHub repository name (e.g. "my-project") captured at the
--                   same point.  Pair with pending_owner to form the full repo
--                   reference.
--
-- Both are nullable: only populated while a workflow is paused for code review.
-- Existing rows are unaffected.
-- ------------------------------------------------------------------------------
ALTER TABLE workflow_runs ADD COLUMN IF NOT EXISTS pending_owner VARCHAR(255);
ALTER TABLE workflow_runs ADD COLUMN IF NOT EXISTS pending_repo  VARCHAR(255);

-- ------------------------------------------------------------------------------
-- CHANGE 2: workflow_runs — additional approved files (multi-file IDE panel)
--
--   additional_files_json — JSON object mapping repository-relative file paths
--                           to their full content, as submitted by the user when
--                           they click "Approve" in the code-review panel.
--
--   Example value:
--     {
--       "src/utils.py": "def helper(): ...",
--       "README.md": "# Updated README\n..."
--     }
--
--   Stored as TEXT because source file content is arbitrarily large.
--   Null when the approval request included no additional files.
--   Parsed and processed by WorkflowOrchestrator.resumeFromCodeApproval()
--   immediately after approval — each entry is pushed to the feature branch
--   before the main pipeline resumes.
-- ------------------------------------------------------------------------------
ALTER TABLE workflow_runs ADD COLUMN IF NOT EXISTS additional_files_json TEXT;
