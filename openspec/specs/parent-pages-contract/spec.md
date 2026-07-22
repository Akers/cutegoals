# parent-pages-contract Specification

## Purpose
TBD - created by archiving change fix-parent-pages-contract. Update Purpose after archive.
## Requirements
### Requirement: Parent 端点契约一致性

系统 SHALL 保证 parent 角色（`PARENT`）前端页面调用的所有 `/api/**` 列表端点返回与前端类型声明一致的数据形状。具体地：parent 端列表端点 MUST 统一返回 `PageResult<T>` 形状（`{content: T[], page, pageSize, totalElements, totalPages}`），前端 `useApi<T>` 与 `usePaginatedData<T>` 的类型参数 MUST 与后端实际返回形状一一对应（即前端使用 `useApi<PageResult<T>>` 或 `usePaginatedData<T>` 解包 `content` 字段）。

#### Scenario: 前端期望 PageResult 时后端返回 PageResult

- **WHEN** parent 前端 `useApi<PageResult<ChildProfile>>('/family/children')` 或 `useApi<PageResult<TaskTemplate>>('/task-templates')` 或 `useApi<PageResult<ReviewItem>>('/task-review/pending')` 调用任一 parent 端列表端点
- **THEN** 系统 MUST 返回 HTTP 200，且响应体 `data` 字段为 `{content: T[], page, pageSize, totalElements, totalPages}` 形状

#### Scenario: usePaginatedData 自动解包 PageResult

- **WHEN** parent 前端 `usePaginatedData<T>(path)` hook 调用任一 parent 端列表端点
- **THEN** hook 内部 MUST 将 `useApi<PageResult<T>>` 返回的 `data.content` 解包为 `items` 字段，`data.totalElements` 解包为 `total` 字段，调用方代码无需感知 PageResult 形状

#### Scenario: 渲染期间前端不应触发 React 崩溃

- **WHEN** parent 前端任一页面渲染期间后端返回数据形状与前端类型声明一致
- **THEN** React 渲染 MUST NOT 抛出 `TypeError: data.map is not a function` 或类似形状错配异常

### Requirement: Child 列表端点可用性

系统 SHALL 提供 `GET /api/family/children` 端点供 parent 角色账号查询当前家庭的活跃孩子档案。该端点 MUST 返回 `PageResult<ChildProfile>`（不含 `DELETED` 状态的孩子），且 MUST 复用与现有 POST/PUT/DELETE 端点相同的认证与家庭上下文解析逻辑。返回形状 MUST 与其他 parent 端列表端点（如 `/api/task-templates`、`/api/task-review/pending`）保持一致的 PageResult 契约。

#### Scenario: PARENT 角色账号列出孩子档案

- **WHEN** 一个已认证且持有 `PARENT` 角色的账号发起 `GET /api/family/children?page=1&pageSize=20`
- **THEN** 系统 MUST 返回 HTTP 200，响应体 `data` 为 `PageResult<ChildProfile>`（`{content: ChildProfile[], page, pageSize, totalElements, totalPages}`），`content` 字段仅包含当前家庭中 `status != 'DELETED'` 的孩子档案

#### Scenario: 未认证请求被拒绝

- **WHEN** 一个未携带有效 access token 的请求发起 `GET /api/family/children`
- **THEN** 系统 MUST 返回 HTTP 401，响应体错误码为 `UNAUTHORIZED`

#### Scenario: 非 PARENT 角色被拒绝

- **WHEN** 一个仅持有 `CHILD` 或 `INSTANCE_ADMIN`（非 `PARENT`）角色的账号发起 `GET /api/family/children`
- **THEN** 系统 MUST 返回 HTTP 403，响应体错误码为 `FORBIDDEN`

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

### Requirement: 错误边界保留全局布局

前端 `ErrorBoundary` 组件的默认 fallback MUST 用 `<Layout>` 包裹错误状态，确保 React 渲染崩溃场景下用户仍可见主导航菜单与 footer。

#### Scenario: React 渲染崩溃时仍显示菜单

- **WHEN** parent 前端任一子组件在渲染期间抛出异常被 ErrorBoundary 捕获
- **THEN** ErrorBoundary 的 fallback MUST 渲染 `<Layout><ErrorState /></Layout>`，保留 header（含主导航菜单）与 footer

#### Scenario: 非渲染崩溃场景不影响正常页面

- **WHEN** 任意 parent 页面正常渲染（无异常）
- **THEN** ErrorBoundary MUST NOT 替换正常渲染输出，页面显示 PageShell 包裹的正常内容

