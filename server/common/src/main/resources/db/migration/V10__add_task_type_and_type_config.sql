-- V10__add_task_type_and_type_config.sql
-- CuteGoals 2.0: Add task_type, type_config columns to task_template
-- and submission_count column to task_assignment

ALTER TABLE task_template
    ADD COLUMN task_type VARCHAR(20) NOT NULL DEFAULT 'LIMITED';

ALTER TABLE task_template
    ADD COLUMN type_config JSON DEFAULT NULL;

COMMENT ON COLUMN task_template.task_type IS '任务类型：LIMITED/REPEAT/STANDING';
COMMENT ON COLUMN task_template.type_config IS '类型配置（JSON，不同类型存储不同配置）';

ALTER TABLE task_assignment
    ADD COLUMN submission_count INT DEFAULT 0;

COMMENT ON COLUMN task_assignment.submission_count IS '已提交次数（用于 STANDING/REPEAT 类型）';
