# Task 11.1 Report: task_type/type_config DB + entity + error codes

## RED Evidence (before changes)
```
mvn -f server/pom.xml test -DskipITs
Tests run: 66, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Files Changed

### New file
- `server/common/src/main/resources/db/migration/V10__add_task_type_and_type_config.sql`
  - `ALTER TABLE task_template ADD COLUMN task_type VARCHAR(20) NOT NULL DEFAULT 'LIMITED'`
  - `ALTER TABLE task_template ADD COLUMN type_config JSON DEFAULT NULL`
  - `ALTER TABLE task_assignment ADD COLUMN submission_count INT DEFAULT 0`
  - H2-compatible: separate `ALTER TABLE ... ADD COLUMN` statements per column

### Modified files

1. **ErrorCode.java** — Added 5 error codes:
   - `TASK_TEMPLATE_TYPE_IMMUTABLE`
   - `TASK_TEMPLATE_LIMITED_NOT_STARTED`
   - `TASK_TEMPLATE_LIMITED_EXPIRED`
   - `TASK_ASSIGNMENT_STANDING_LIMIT_REACHED`
   - `TASK_ASSIGNMENT_REPEAT_NOT_TRIGGER_DAY`

2. **TaskTemplate.java** — Added 2 fields:
   - `private String taskType;` (`@TableField("task_type")`)
   - `private String typeConfig;` (`@TableField("type_config")`)

3. **TaskAssignment.java** — Added 1 field:
   - `private Integer submissionCount;` (`@TableField("submission_count")`)

4. **FlywayMigrationTest.java** — Updated expected migration count from 9 to 10, added V10 version assertion.

## GREEN Evidence (after changes)
```
mvn -f server/pom.xml clean test -DskipITs
Tests run: 66, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```
- `mvn -f server/pom.xml compile` — **PASS**
- `mvn -f server/pom.xml test -DskipITs` — **PASS** (66 tests, 0 failures)

## Concerns
- TDD skill not available in environment; followed Red-Green cycle manually
- Auth module tests had stale build artifacts on first non-clean run; resolved with `mvn clean`
- No service/controller logic modified — strictly DB + entity + error codes as specified
