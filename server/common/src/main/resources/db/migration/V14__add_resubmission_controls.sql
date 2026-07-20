-- V14__add_resubmission_controls.sql
-- CuteGoals 2.0: Add resubmission control fields to task_template and snapshot columns to task_assignment
-- These columns allow templates to configure resubmission limits and point caps,
-- with snapshot columns capturing the state at assignment time.
--
-- Compatible with H2 (PostgreSQL mode), MySQL 8+, and PostgreSQL 15+.

ALTER TABLE task_template
    ADD COLUMN allow_resubmit BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN max_submissions INT NOT NULL DEFAULT 0,
    ADD COLUMN points_cap INT NOT NULL DEFAULT 0;

ALTER TABLE task_assignment
    ADD COLUMN snapshot_template_allow_resubmit BOOLEAN DEFAULT NULL,
    ADD COLUMN snapshot_template_max_submissions INT DEFAULT NULL,
    ADD COLUMN snapshot_template_points_cap INT DEFAULT NULL;

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
