-- ==============================================================================
-- V2 — user_defaults table + code-review pause columns on workflow_runs
--
-- NOTE: This project uses spring.jpa.hibernate.ddl-auto=update.
--       Hibernate applies the schema automatically from @Entity classes.
--       This file is the authoritative SQL reference / audit trail for the
--       changes introduced in this version. Run it manually against a
--       production database where ddl-auto is set to 'validate' or 'none'.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- CHANGE 1: user_defaults
--
-- Stores per-user planner defaults.  The Planner Agent reads this row before
-- generating a plan so that the user never has to repeat their repo, project
-- key, or Slack channel in every prompt.
--
-- Design decisions:
--   - UNIQUE (user_id) → at most one defaults row per user.  Implemented as
--     a named constraint so it can be dropped / renamed without guessing the
--     generated name.
--   - ON DELETE CASCADE → when a user account is deleted all their data goes.
--   - All payload columns are nullable — the row may exist with only some
--     fields filled in; missing fields fall back to hardcoded planner defaults.
--   - updated_at uses DEFAULT NOW() so an INSERT with no explicit value is
--     automatically timestamped; application code sets it on every UPDATE.
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_defaults (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    github_owner         VARCHAR(255),
    github_default_repo  VARCHAR(255),
    jira_project_key     VARCHAR(50),
    slack_default_channel VARCHAR(100),
    updated_at           TIMESTAMP    DEFAULT NOW(),
    CONSTRAINT unique_user_defaults UNIQUE (user_id)
);

-- Index for the most common lookup pattern: findByUserId
CREATE INDEX IF NOT EXISTS idx_user_defaults_user_id ON user_defaults (user_id);

-- ------------------------------------------------------------------------------
-- CHANGE 2: workflow_runs — human-in-the-loop code-review pause columns
--
-- These four columns support pausing an in-flight workflow after generateCode
-- so the user can review and approve/reject the generated code before it is
-- pushed to GitHub.
--
--   pending_code        — the raw generated code awaiting approval (TEXT because
--                         source files can be arbitrarily large).
--   pending_file_path   — target file path the code will be written to.
--   pending_branch_name — branch the push will land on once resumed.
--   resume_from_step    — 1-based index of the step to restart from when the
--                         user approves; 0 = not paused / nothing pending.
--
-- All columns are nullable / defaulted so existing rows are unaffected.
-- ------------------------------------------------------------------------------
ALTER TABLE workflow_runs ADD COLUMN IF NOT EXISTS pending_code        TEXT;
ALTER TABLE workflow_runs ADD COLUMN IF NOT EXISTS pending_file_path   VARCHAR(500);
ALTER TABLE workflow_runs ADD COLUMN IF NOT EXISTS pending_branch_name VARCHAR(255);
ALTER TABLE workflow_runs ADD COLUMN IF NOT EXISTS resume_from_step    INT DEFAULT 0;
