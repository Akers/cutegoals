## Context

`task_template` 表与 `task_assignment` 表的 `type_config`/`snapshot_template_type_config` 列已声明为 `JSON` 类型（PostgreSQL）。Java 实体中对应字段为 `String`（JSON 文本），MyBatis-Plus 默认参数绑定未声明类型处理器，导致插入/更新时 PostgreSQL 将参数视为 `character varying`，触发 `BadSqlGrammarException`。H2 单元测试默认不严格校验 JSON 类型，因此该问题未在 CI 中暴露。

## Goals / Non-Goals

**Goals:**
- 修复 PostgreSQL 下 `TaskTemplate` 与 `TaskAssignment` 的 JSON 字段写入/更新失败的 bug。
- 保持 H2 单元测试与 PostgreSQL 行为一致。
- 不修改业务规则、接口契约或前端行为。

**Non-Goals:**
- 不新增 capability、public API 或数据库 schema 变更。
- 不调整 `typeConfig` 的业务语义（仍存储 JSON 文本）。
- 不引入外部 JSON 库（复用 Jackson）。

## Decisions

### 决策 1：使用 MyBatis-Plus `JacksonTypeHandler` 处理 JSON 列
- **原因**：MyBatis-Plus 内置 `JacksonTypeHandler`（`com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler`）可直接将 Java `String` 字段与数据库 JSON 列互转，无需自定义 TypeHandler。
- **备选**：自定义 `JsonTypeHandler` — 更可控，但增加维护成本；当前 bug 修复无需过度设计。
- **实现**：在 `TaskTemplate.typeConfig` 和 `TaskAssignment.snapshotTemplateTypeConfig` 字段上添加 `@TableField(typeHandler = JacksonTypeHandler.class)`。

### 决策 2：实体字段保持 `String` 类型
- **原因**：`typeConfig` 的内容结构由 `taskType` 动态决定（LIMITED/REPEAT/STANDING），保持 `String` 可避免引入强类型 DTO 并减少本次修复范围。
- **风险**：字符串字段无法享受编译期类型检查，但校验逻辑在 `TaskTemplateService.validateTypeConfig` 中集中处理，风险可控。

## Risks / Trade-offs

- **H2 与 PostgreSQL 行为差异**：H2 的 JSON 类型对参数绑定较宽松，可能在 H2 通过后仍遗留 PostgreSQL 问题。缓解：通过调整 MyBatis 类型处理器确保驱动层绑定正确类型；如条件允许，补充基于 Testcontainers 的 PostgreSQL 集成测试。
- **JacksonTypeHandler 序列化/转义**：`String` 值必须是合法 JSON 文本；`validateTypeConfig` 已在写入前校验 JSON 合法性，因此不会传入非 JSON 字符串。
- **读取回显**：`JacksonTypeHandler` 从 JSON 列读取时会返回 JSON 文本字符串，与现有逻辑兼容。
