-- V12__add_task_type_snapshot_columns.sql
-- CuteGoals 2.0: Add task_type and type_config snapshot columns to task_assignment
-- These columns capture the task_type/type_config state at assignment time,
-- mirroring the existing snapshot_template_* pattern.
--
-- Compatible with H2 (PostgreSQL mode), MySQL 8+, and PostgreSQL 15+.

ALTER TABLE task_assignment
    ADD COLUMN snapshot_template_task_type VARCHAR(20) DEFAULT NULL;

ALTER TABLE task_assignment
    ADD COLUMN snapshot_template_type_config JSON DEFAULT NULL;

-- COMMENT ON COLUMN is not compatible with MySQL 8+;
-- H2/PostgreSQL comments can be added manually if needed.
-- COMMENT ON COLUMN task_assignment.snapshot_template_task_type IS '快照：任务类型（LIMITED/REPEAT/STANDING）';
-- COMMENT ON COLUMN task_assignment.snapshot_template_type_config IS '快照：类型配置（JSON，与 task_template.type_config 结构一致）';
