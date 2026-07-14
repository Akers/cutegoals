## Why

创建任务模板时，`type_config` 字段在 PostgreSQL 数据库中以 `JSON` 类型存储，但 MyBatis-Plus 将 Java 实体中的 `String` 值作为 `character varying` 绑定到参数，导致插入报错：`ERROR: column "type_config" is of type json but expression is of type character varying`。该问题在单元测试（H2）中未暴露，但在 PostgreSQL 生产/集成环境必现，阻塞任务模板创建。

## What Changes

- 为 `TaskTemplate.typeConfig` 添加 MyBatis JSON 类型处理器（TypeHandler），使 `String` 形式的 JSON 在写入 PostgreSQL 时正确转换为 `json` 类型。
- 同步修复 `TaskAssignment.snapshotTemplateTypeConfig` 的同样问题（同列类型）。
- 在现有模板服务测试中补充 PostgreSQL JSON 类型兼容的断言，或调整实体映射使 H2 与 PostgreSQL 行为一致。
- 不涉及接口变更、业务规则变更或新增 capability。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `task-template`: 修正 `typeConfig` 与 `snapshotTemplateTypeConfig` 的持久化行为，要求写入 PostgreSQL JSON 列时类型正确，不新增或修改业务验收场景。

## Impact

- `server/task/src/main/java/com/cutegoals/task/entity/TaskTemplate.java`
- `server/common/src/main/java/com/cutegoals/common/entity/task/TaskAssignment.java`
- 可能涉及的 MyBatis-Plus 类型处理器（JacksonTypeHandler 或自定义 TypeHandler）
- 仅影响数据持久化层，无 API/前端/UI 变化。
