## Why

已归档的 `core-features` change 将任务模板 `task_type`（LIMITED/REPEAT/STANDING）及其配置 `type_config` 写入了 main spec，并在后端完成了基础字段存储。但当前代码中仍遗留若干属性的前端模型、接口、校验和错误码同步未实现，导致家长端无法完整配置任务类型，部分后端行为也与 main spec 不一致。本次 change 补齐这些实现遗漏，使产品行为与 `openspec/specs/task-template/spec.md` 保持一致。

## What Changes

- **前端模型与 UI**：家长端 `TaskTemplate` 类型增加 `taskType` 和 `typeConfig`，模板管理表单支持选择三类任务类型并配置对应字段（限时起止日期、重复周期与触发日、常驻最大提交次数）。
- **后端查询接口**：`GET /api/task-templates` 增加 `taskType` 单值/多值筛选参数，支持按任务类型过滤模板列表。
- **后端校验**：`TaskTemplateService` 创建/更新模板时，强制校验 `taskType` 必填、取值合法、`typeConfig` 与 `taskType` 匹配、子字段合法，并返回统一错误码。
- **分配快照**：`task_assignment` 表及实体增加 `snapshot_template_task_type` 和 `snapshot_template_type_config` 字段；创建分配时同步快照，确保模板后续修改不追溯既有分配。
- **错误码同步**：将 6 个新增错误码（`TASK_TEMPLATE_TYPE_IMMUTABLE`、`TASK_TEMPLATE_TYPE_CONFIG_MISMATCH`、`TASK_LIMITED_NOT_STARTED`、`TASK_LIMITED_EXPIRED`、`TASK_REPEAT_NOT_TRIGGER_DAY`、`TASK_STANDING_LIMIT_REACHED`）补充到前端 `ErrorCodes` 与中文错误映射。
- **回归验证**：补齐上述实现后，现有 task-template / task-assignment / task-review 测试继续通过，新增针对遗漏属性的单元与集成测试。

## Capabilities

### New Capabilities

<!-- 无新增能力；本次为已有能力实现补齐。 -->

### Modified Capabilities

<!-- main spec 中需求已完整，本次不修改需求定义。 -->
- `task-template`：本次 change 仅补齐其实现，不修改需求规格。

## Impact

- **后端**：`server/task`（模板服务、控制器）、`server/task-review`（错误码引用）、`server/common`（实体、数据库迁移）。
- **前端**：`web/src/shared/api/types.ts`、`web/src/shared/api/errors.ts`、`web/src/parent/pages/index.tsx`（模板管理 UI）。
- **数据库**：新增 `task_assignment.snapshot_template_task_type` 和 `snapshot_template_type_config` 列，并回填历史数据（nullable，不回填不影响既有分配展示）。
- **API**：`GET /api/task-templates` 新增可选查询参数 `taskType`，请求/响应体中的 `taskType`/`typeConfig` 字段完整生效。
