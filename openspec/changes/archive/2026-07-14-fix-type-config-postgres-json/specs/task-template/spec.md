## ADDED Requirements

### Requirement: 任务模板 typeConfig 持久化兼容 PostgreSQL JSON 列
任务模板的 `type_config` 字段以及任务分配的 `snapshot_template_type_config` 字段在持久化到 PostgreSQL 数据库时，MUST 正确绑定到 `JSON` 列类型，不得产生 `character varying` 到 `json` 的类型不匹配错误。系统 MUST 保持 H2 单元测试与 PostgreSQL 生产环境的行为一致，且不修改业务规则或接口契约。

#### Scenario: 在 PostgreSQL 中创建含 type_config 的任务模板
- **WHEN** 家长创建 `task_type=LIMITED` 且 `type_config` 为合法 JSON 文本的任务模板
- **THEN** 数据库插入成功，`type_config` 以 JSON 类型存储
- **AND** 参数绑定使用 PostgreSQL 原生 `json` 类型（`PGobject`）而非 `character varying`

#### Scenario: 在 PostgreSQL 中创建含快照 type_config 的任务分配
- **WHEN** 系统基于已有模板创建任务分配
- **THEN** `snapshot_template_type_config` 字段成功写入 PostgreSQL JSON 列，无类型转换错误
- **AND** 参数绑定使用 PostgreSQL 原生 `json` 类型

#### Scenario: H2 单元测试行为不变
- **WHEN** 运行 `TaskTemplateServiceTest` 和 `TaskAssignmentServiceTest`
- **THEN** 所有测试通过，JSON 字段读写与修复前一致
