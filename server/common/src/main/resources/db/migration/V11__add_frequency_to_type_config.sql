-- V11__add_frequency_to_type_config.sql
-- CuteGoals 2.0: Seed frequency data into task_template.type_config
-- from legacy task_recurrence_rule table (if any data exists).
--
-- The type_config JSON structure for frequency/trigger_day:
--   {"frequency":"DAILY|WEEKLY|MONTHLY|YEARLY","trigger_day":{...}}
--
-- This migration is idempotent: only updates rows where type_config IS NULL.
-- Safe to run on empty/development databases (no-op if no recurrence rules exist).
-- Uses H2-compatible SQL (no json_build_object / SPLIT_PART).

-- Migrate DAILY recurrence rules
UPDATE task_template t
SET type_config = CAST('{"frequency":"DAILY"}' AS JSON)
WHERE t.type_config IS NULL
  AND EXISTS (
    SELECT 1 FROM task_recurrence_rule r
    WHERE r.template_id = t.id AND r.rule_type = 'DAILY'
  );

-- Migrate WEEKDAYS/WEEKENDS → WEEKLY with Monday as default weekday
UPDATE task_template t
SET type_config = CAST('{"frequency":"WEEKLY","trigger_day":{"weekday":1}}' AS JSON)
WHERE t.type_config IS NULL
  AND EXISTS (
    SELECT 1 FROM task_recurrence_rule r
    WHERE r.template_id = t.id AND r.rule_type IN ('WEEKDAYS', 'WEEKENDS')
  );

-- Migrate CUSTOM_WEEKDAYS → WEEKLY with first custom weekday.
-- Uses POSITION(...) instead of INSTR for PostgreSQL/H2 compatibility.
UPDATE task_template t
SET type_config = CAST('{"frequency":"WEEKLY","trigger_day":{"weekday":' ||
    TRIM(SUBSTR(r.custom_weekdays, 1, CASE
        WHEN POSITION(',' IN r.custom_weekdays) > 0
        THEN POSITION(',' IN r.custom_weekdays) - 1
        ELSE LENGTH(r.custom_weekdays)
    END)) || '}}' AS JSON)
FROM task_recurrence_rule r
WHERE r.template_id = t.id
  AND r.rule_type = 'CUSTOM_WEEKDAYS'
  AND t.type_config IS NULL;

-- For any remaining templates with recurrence rules but no type_config,
-- set a default WEEKLY config so the template isn't broken
UPDATE task_template t
SET type_config = CAST('{"frequency":"WEEKLY","trigger_day":{"weekday":1}}' AS JSON)
WHERE t.type_config IS NULL
  AND EXISTS (
    SELECT 1 FROM task_recurrence_rule r
    WHERE r.template_id = t.id
  );
