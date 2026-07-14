## Context

`core-features` 归档后，后端在 PostgreSQL 环境启动时 Flyway 执行 `V11__add_frequency_to_type_config.sql` 失败。该迁移脚本在 H2 测试环境通过，但在 PostgreSQL 中使用了不存在的 `INSTR()` 函数。

## Goals / Non-Goals

**Goals：**
- 用 PostgreSQL 与 H2 均支持的 SQL 函数替换 `INSTR()`
- 保持迁移逻辑不变（`CUSTOM_WEEKDAYS` 向 `task_template.type_config` 生成 JSON 频率配置）
- 确保后端在 PostgreSQL 生产环境可正常启动

**Non-Goals：**
- 不修改 `type_config` JSON 结构
- 不修改业务逻辑或对外接口
- 不引入新的 capability 或 delta spec

## Decisions

**决策 1：使用 `POSITION(substring IN column)` 替换 `INSTR(column, substring)`**

- `POSITION` 是 ANSI SQL 标准函数，PostgreSQL 与 H2 均支持。
- 替代方案 `STRPOS(column, substring)` 为 PostgreSQL 特有，H2 不支持，因此不选。
- 修改范围严格限定在 `V11__add_frequency_to_type_config.sql` 第 32-38 行。

## Risks / Trade-offs

- [Risk] 已卡在 V10 的 PostgreSQL 实例可能因 V11 已被标记为失败而需要手动修复 `flyway_schema_history` → 迁移脚本本身已保持幂等，但如果 Flyway 已记录失败，需要用户手动删除该失败记录后重试。本 hotfix 仅保证脚本本身可正确执行。
- [Risk] 其他迁移脚本可能也有类似 `INSTR()` 用法 → 通过全文搜索确认只有 V11 使用 `INSTR()`，已一并处理。

## Migration Plan

1. 提交修复后的 `V11__add_frequency_to_type_config.sql`
2. 运行 `mvn -f server/pom.xml test -DskipITs` 验证 H2 兼容性
3. 在 PostgreSQL 环境重启应用，确认 Flyway 成功应用 V11
