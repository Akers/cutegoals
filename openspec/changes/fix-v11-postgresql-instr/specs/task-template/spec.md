## ADDED Requirements

### Requirement: 迁移脚本 MUST 兼容 PostgreSQL 与 H2

为支持私有化部署中 PostgreSQL 生产环境，任务模板相关的 Flyway 迁移脚本 MUST 使用 ANSI 或两种数据库均支持的 SQL 函数，不得使用仅 H2/MySQL 特有的函数。

#### Scenario: PostgreSQL 启动时成功应用 V11 迁移

- **Given**: 数据库为 PostgreSQL，当前 schema 版本为 10，存在旧版 `task_recurrence_rule` 表及 `CUSTOM_WEEKDAYS` 规则数据
- **When**: 后端启动，Flyway 执行 `V11__add_frequency_to_type_config.sql`
- **Then**: 迁移成功，`task_template.type_config` 被正确填充，应用正常启动
- **And**: 同样的迁移脚本在 H2 测试环境中也能成功执行
