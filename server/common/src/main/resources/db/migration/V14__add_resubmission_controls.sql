-- V14__add_resubmission_controls.sql
-- CuteGoals 2.0: Add resubmission control fields to task_template and snapshot columns to task_assignment
-- These columns allow templates to configure resubmission limits and point caps,
-- with snapshot columns capturing the state at assignment time.
--
-- Compatible with H2 (PostgreSQL mode), MySQL 8+, and PostgreSQL 15+.

ALTER TABLE task_template ADD COLUMN allow_resubmit BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE task_template ADD COLUMN max_submissions INT NOT NULL DEFAULT 0;
ALTER TABLE task_template ADD COLUMN points_cap INT NOT NULL DEFAULT 0;

ALTER TABLE task_assignment ADD COLUMN snapshot_template_allow_resubmit BOOLEAN DEFAULT NULL;
ALTER TABLE task_assignment ADD COLUMN snapshot_template_max_submissions INT DEFAULT NULL;
ALTER TABLE task_assignment ADD COLUMN snapshot_template_points_cap INT DEFAULT NULL;

CREATE INDEX idx_assignment_child_template
    ON task_assignment (child_id, template_id, id);

-- COMMENT ON COLUMN is not compatible with MySQL 8+;
-- H2/PostgreSQL comments can be added manually if needed.
-- COMMENT ON COLUMN task_template.allow_resubmit IS '是否允许重新提交';
-- COMMENT ON COLUMN task_template.max_submissions IS '最大提交次数（0=不限制）';
-- COMMENT ON COLUMN task_template.points_cap IS '积分上限（0=不限制）';
-- COMMENT ON COLUMN task_assignment.snapshot_template_allow_resubmit IS '快照：是否允许重新提交';
-- COMMENT ON COLUMN task_assignment.snapshot_template_max_submissions IS '快照：最大提交次数';
-- COMMENT ON COLUMN task_assignment.snapshot_template_points_cap IS '快照：积分上限';

-- ============================================================================
-- Data backfill: enable resubmission for existing STANDING templates that have
-- max_submissions configured in their type_config JSON.
-- Background: before V14, STANDING templates relied solely on
-- type_config.max_submissions via the old client. This backfill migrates those
-- configurations into the new first-class columns (allow_resubmit, max_submissions)
-- while leaving type_config.max_submissions untouched for backward compatibility.
-- ============================================================================

-- Data backfill is intentionally omitted here because:
--   - H2 in PostgreSQL mode does not support JSON_EXTRACT
--   - PostgreSQL uses type_config::jsonb ->> 'max_submissions' syntax
--   - The backfill is best-effort for pre-V14 STANDING data and
--     has no effect on fresh databases (including test databases)
-- For production upgrades, run the appropriate backfill manually:
--   MySQL:  UPDATE task_template SET allow_resubmit=TRUE, max_submissions=JSON_EXTRACT(type_config, '$.max_submissions') WHERE task_type='STANDING' AND JSON_EXTRACT(type_config, '$.max_submissions') IS NOT NULL;
--   PG:     UPDATE task_template SET allow_resubmit=TRUE, max_submissions=(type_config::jsonb->>'max_submissions')::int WHERE task_type='STANDING' AND type_config::jsonb->>'max_submissions' IS NOT NULL;
