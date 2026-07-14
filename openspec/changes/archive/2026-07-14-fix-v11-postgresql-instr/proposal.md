## Why

`core-features` 归档后，在 PostgreSQL 生产环境启动应用时 Flyway 迁移 `V11__add_frequency_to_type_config.sql` 失败。原因是迁移脚本中使用了 H2 兼容的 `INSTR()` 函数，而 PostgreSQL 不提供该函数，导致后端无法启动。

## What Changes

- 将 `V11__add_frequency_to_type_config.sql` 中的 `INSTR(column, substring)` 替换为 ANSI 标准的 `POSITION(substring IN column)`，使其在 PostgreSQL 与 H2 测试环境中均兼容。
- 迁移逻辑与数据语义保持不变：仍按 `CUSTOM_WEEKDAYS` 规则向 `task_template.type_config` 生成 JSON 频率配置。

## Capabilities

### New Capabilities

无新增 capability。

### Modified Capabilities

- `task-template`: 添加对 Flyway 迁移脚本 PostgreSQL/H2 双兼容的验收要求。本 hotfix 不修改任务模板业务行为，仅修复 V11 迁移脚本的数据库兼容性。需创建 delta spec。

## Impact

- 影响文件：`server/common/src/main/resources/db/migration/V11__add_frequency_to_type_config.sql`
- 无接口变更、无 schema 变更、无数据语义变更
- 部署影响：已卡在 V10 的 PostgreSQL 实例下次启动时 Flyway 可成功应用 V11
