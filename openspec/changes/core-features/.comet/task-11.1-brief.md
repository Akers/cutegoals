# Task 11.1 Brief: Implement task_type/type_config DB + entity + error codes

## Context
This is a verify-fail repair task. The task-template spec requires three task types (`LIMITED`, `REPEAT`, `STANDING`) with `task_type` and `type_config` fields. The current implementation lacks these entirely. This task adds the database column, entity fields, and error codes. Subsequent tasks will add the business logic.

## Files to change

### 1. Database migration: `server/common/src/main/resources/db/migration/V10__add_task_type_and_type_config.sql`

Create a new Flyway migration (V10) that:
- Adds `task_type VARCHAR(20) NOT NULL DEFAULT 'LIMITED'` to `task_template`
- Adds `type_config JSON NULL` to `task_template`
- Adds `submission_count INT DEFAULT 0` to `task_assignment`

The `task_type` column is required (NOT NULL, default 'LIMITED') — existing rows without a type become LIMITED.

### 2. TaskTemplate entity: `server/common/src/main/java/com/cutegoals/common/entity/task/TaskTemplate.java`

Add fields:
- `private String taskType;` — maps to `task_type`
- `private String typeConfig;` — maps to `type_config` (stored as JSON string)

Add Lombok `@TableField` annotations if needed for column name mapping. Do NOT change the existing fields.

### 3. TaskAssignment entity: `server/common/src/main/java/com/cutegoals/common/entity/task/TaskAssignment.java`

Add field:
- `private Integer submissionCount;` — maps to `submission_count`

### 4. ErrorCode: `server/common/src/main/java/com/cutegoals/common/exception/ErrorCode.java`

Add these enum constants in the `// === Task Template (TASK_TEMPLATE_) ===` section (after line 58):
- `TASK_TEMPLATE_TYPE_IMMUTABLE("TASK_TEMPLATE_TYPE_IMMUTABLE")`
- `TASK_TEMPLATE_LIMITED_NOT_STARTED("TASK_TEMPLATE_LIMITED_NOT_STARTED")` — note: spec says `TASK_LIMITED_NOT_STARTED` but this follows project prefix convention
- `TASK_TEMPLATE_LIMITED_EXPIRED("TASK_TEMPLATE_LIMITED_EXPIRED")` — follows same prefix convention

Add these in the `// === Task Assignment (TASK_ASSIGNMENT_) ===` section (after line 74):
- `TASK_ASSIGNMENT_STANDING_LIMIT_REACHED("TASK_ASSIGNMENT_STANDING_LIMIT_REACHED")`
- `TASK_ASSIGNMENT_REPEAT_NOT_TRIGGER_DAY("TASK_ASSIGNMENT_REPEAT_NOT_TRIGGER_DAY")`

## Verification
1. `mvn -f server/pom.xml compile` passes
2. `mvn -f server/pom.xml test -DskipITs` passes (existing tests still pass)
3. `mvn flyway:migrate` on MySQL 8 runs V10 migration without errors (if available — compile test is sufficient)

## Constraints
- Do NOT modify V4 migration (already applied in prod)
- Do NOT modify service/controller/business logic — this task is only DB + entity + error codes
- Follow existing project patterns (Lombok `@Data`, MyBatis-Plus field naming, etc.)
