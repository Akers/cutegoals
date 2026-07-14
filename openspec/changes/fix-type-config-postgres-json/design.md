## Context

`task_template` 表与 `task_assignment` 表的 `type_config`/`snapshot_template_type_config` 列已声明为 `JSON` 类型（PostgreSQL）。Java 实体中对应字段为 `String`（JSON 文本）。初步使用 MyBatis-Plus 内置 `JacksonTypeHandler` 后，在 PostgreSQL 运行时仍报 `column is of type json but expression is of type character varying`，原因是 `JacksonTypeHandler` 最终通过 `PreparedStatement.setString(...)` 写入参数，PostgreSQL 驱动将该参数识别为 `character varying`，无法匹配 `json` 列。H2 单元测试默认不严格校验 JSON 类型，因此问题未在 CI 中暴露。

## Goals / Non-Goals

**Goals:**
- 修复 PostgreSQL 下 `TaskTemplate` 与 `TaskAssignment` 的 JSON 字段写入/更新失败的 bug。
- 保持 H2 单元测试与 PostgreSQL 行为一致。
- 不修改业务规则、接口契约或前端行为。

**Non-Goals:**
- 不新增 capability、public API 或数据库 schema 变更。
- 不调整 `typeConfig` 的业务语义（仍存储 JSON 文本）。
- 不引入外部 JSON 库（复用 Jackson 用于业务层校验，但持久化层不再需要序列化）。

## Decisions

### 决策 1：使用自定义 `JsonTypeHandler` 处理 JSON 列
- **原因**：MyBatis-Plus 内置 `JacksonTypeHandler` 对 `String` 字段使用 `setString(...)` 传参，PostgreSQL 不接受该类型写入 `json` 列。自定义 `JsonTypeHandler` 可在连接数据库为 PostgreSQL 时通过反射创建 `org.postgresql.util.PGobject` 并以 `setObject(...)` 绑定到 JSON 列；其他数据库（如 H2）回退到 `setString(...)`。
- **备选**：使用 `Map` 或 `JsonNode` 字段 + `JacksonTypeHandler` — 需要改动业务 DTO/校验层，范围过大；当前修复仅聚焦持久化绑定。
- **实现**：
  1. 新增 `com.cutegoals.common.config.handler.JsonTypeHandler`。
  2. 在 `TaskTemplate.typeConfig` 和 `TaskAssignment.snapshotTemplateTypeConfig` 字段上替换为 `@TableField(typeHandler = JsonTypeHandler.class)`。

### 决策 2：实体字段保持 `String` 类型
- **原因**：`typeConfig` 的内容结构由 `taskType` 动态决定（LIMITED/REPEAT/STANDING），保持 `String` 可避免引入强类型 DTO 并减少本次修复范围。
- **风险**：字符串字段无法享受编译期类型检查，但校验逻辑在 `TaskTemplateService.validateTypeConfig` 中集中处理，风险可控。

## Risks / Trade-offs

- **H2 与 PostgreSQL 行为差异**：H2 的 JSON 类型对参数绑定较宽松，可能导致 H2 通过后仍遗留 PostgreSQL 问题。缓解：自定义处理器显式区分 PostgreSQL 与其他数据库；如条件允许，补充基于 Testcontainers 的 PostgreSQL 集成测试。
- **反射依赖**：`JsonTypeHandler` 通过反射实例化 `PGobject`，避免 `common` 模块引入 postgresql 编译依赖。该依赖在 `web` 运行时已存在，运行路径可正常工作。
- **读取回显**：从 JSON 列读取时仍通过 `getString` 返回 JSON 文本字符串，与现有逻辑兼容。
- **空值处理**：`BaseTypeHandler` 已统一处理 `null`，自定义处理器只负责非空值。
