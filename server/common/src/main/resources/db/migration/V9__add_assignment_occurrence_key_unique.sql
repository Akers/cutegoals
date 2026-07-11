-- V9__add_assignment_occurrence_key_unique.sql
-- CuteGoals 2.0: Add UNIQUE constraint on occurrence_key for concurrent generation safety
-- See Phase 3 review: I2 — occurrenceKey 无数据库 UNIQUE 约束
--
-- Note: V4 already has uk_assignment_occurrence on (family_id, child_id, occurrence_key).
-- This adds a direct unique constraint on occurrence_key for direct lookup protection.
-- The composite index is dropped to avoid redundancy.

DROP INDEX IF EXISTS uk_assignment_occurrence;

ALTER TABLE task_assignment ADD CONSTRAINT uk_assignment_occurrence_key UNIQUE (occurrence_key);
