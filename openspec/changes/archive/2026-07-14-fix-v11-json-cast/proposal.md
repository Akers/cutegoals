## Why

`fix-v11-postgresql-instr` hotfix 归档后，后端在 PostgreSQL 环境启动时 Flyway 执行 `V11__add_frequency_to_type_config.sql` 仍然失败。虽然 `INSTR()` 已被替换为 `POSITION()`，但迁移脚本中 `SET type_config = '...'` 的字符串字面量无法直接赋给 `task_template.type_config` JSON 列（PostgreSQL 要求显式 cast）。这导致应用仍无法启动。

## What Changes

- 将 `V11__add_frequency_to_type_config.sql` 中所有 4 处 `SET type_config = '...'` 改为 `SET type_config = CAST('...' AS JSON)`，使其在 PostgreSQL 与 H2 测试环境中均兼容。
- 迁移逻辑与数据语义保持不变。

## Capabilities

### New Capabilities

无新增 capability。

### Modified Capabilities

- `task-template`: 在 delta spec 中明确迁移脚本对 JSON 列赋值时必须显式 cast。本 hotfix 为之前同一问题的补充修复。

## Impact

- 影响文件：`server/common/src/main/resources/db/migration/V11__add_frequency_to_type_config.sql`
- 无接口变更、无 schema 变更、无数据语义变更
- 修复已归档的 `fix-v11-postgresql-instr` 未覆盖的 JSON cast 问题
