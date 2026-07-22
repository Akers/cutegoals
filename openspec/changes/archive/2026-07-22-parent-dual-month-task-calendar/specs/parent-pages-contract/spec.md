# parent-pages-contract Specification (Delta)

## MODIFIED Requirements

### Requirement: Task 按日期列表查询契约

parent 前端任务分配页 MUST 使用 `GET /api/task-assignments` 分页查询端点，支持以下查询模式：

1. **日历模式**：当用户在双月日历中点击日期/周/月时，前端 MUST 传入 `startDate` + `endDate` 过滤对应时间范围内任务列表
2. **查看全部模式**：当用户点击「查看全部」按钮时，前端 MUST 不传 `startDate` 和 `endDate` 参数，保留 `taskType` 筛选条件，查询符合类型的所有分配记录

前端调用 `GET /api/task-assignments` MUST 支持 `page`、`pageSize`、`startDate`、`endDate`、`taskType`（可选，逗号分隔多值）、`childId`（可选）查询参数。

前端 MUST NOT 调用 `/api/task-assignments/calendar` 端点获取任务列表（该端点为日历聚合视图专用，返回 `{year, month, days: {...聚合统计}}` 形状，非任务列表）。获取日历颜色标记数据时，前端 SHALL 调用 `/api/task-assignments/calendar` 端点。

#### Scenario: 前端按日期范围查询当天任务列表

- **WHEN** parent 前端任务分配页 `useApi<PageResult<TaskAssignment>>('/task-assignments?page=1&pageSize=50&startDate=2026-07-13&endDate=2026-07-13')` 调用 `GET /api/task-assignments`
- **THEN** 系统 MUST 返回 HTTP 200，响应体 `data` 为 `PageResult<TaskAssignment>`（`{content: TaskAssignment[], page, pageSize, totalElements, totalPages}`），`content` 字段仅包含 `deadline` 在 `[startDate 00:00, endDate 23:59:59]` 区间内的任务分配

#### Scenario: 前端按日历点击查询当天任务列表

- **WHEN** parent 前端任务分配页 `useApi<PageResult<TaskAssignment>>('/task-assignments?page=1&pageSize=50&startDate=2026-07-13&endDate=2026-07-13')` 调用 `GET /api/task-assignments`
- **THEN** 系统 MUST 返回 HTTP 200，响应体 `data` 为 `PageResult<TaskAssignment>`（`{content: TaskAssignment[], page, pageSize, totalElements, totalPages}`），`content` 字段仅包含 `deadline` 在 `[startDate 00:00, endDate 23:59:59]` 区间内的任务分配

#### Scenario: 前端查看全部模式不传日期参数

- **WHEN** parent 前端任务分配页 `useApi<PageResult<TaskAssignment>>('/task-assignments?page=1&pageSize=20&taskType=LIMITED')` 调用 `GET /api/task-assignments`
- **THEN** 系统 MUST 返回 HTTP 200，`content` 字段包含所有 `snapshotTemplateTaskType` 为 `LIMITED` 的分配记录（分页），不受日期范围限制

#### Scenario: 后端 task-assignments 端点支持日期过滤

- **WHEN** 任意请求调用 `GET /api/task-assignments?page=1&pageSize=50&startDate=2026-07-13&endDate=2026-07-13` 且已认证为 PARENT 角色
- **THEN** 系统 MUST 返回 HTTP 200，`PageResult.content` 中的 TaskAssignment 其 `deadline` MUST 落在 `startDate` 至 `endDate` 区间内（端到端验证前端按日期过滤正确）

#### Scenario: 后端 task-assignments 端点支持任务类型过滤

- **WHEN** 请求调用 `GET /api/task-assignments?page=1&pageSize=50&taskType=LIMITED,REPEAT` 且已认证为 PARENT 角色
- **THEN** 系统 MUST 返回 HTTP 200，`content` 仅包含 `snapshotTemplateTaskType` 为 `LIMITED` 或 `REPEAT` 的分配
