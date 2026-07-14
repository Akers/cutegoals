## ADDED Requirements

### Requirement: 迁移脚本为 JSON 列赋值时 MUST 显式 CAST

当 Flyway 迁移脚本向 `task_template.type_config` 等 JSON 类型列赋值时，赋值表达式 MUST 使用 `CAST(... AS JSON)` 或数据库双方均支持的等效语法，不得依赖隐式类型转换。

#### Scenario: PostgreSQL 启动时成功应用 V11 迁移

- **Given**: 数据库为 PostgreSQL，`task_template.type_config` 为 JSON 类型，存在旧版 `task_recurrence_rule` 数据
- **When**: 后端启动，Flyway 执行 `V11__add_frequency_to_type_config.sql`
- **Then**: 所有 `SET type_config = ...` 语句成功执行，生成合法 JSON 值
- **And**: 同样的脚本在 H2 测试环境中也成功执行
