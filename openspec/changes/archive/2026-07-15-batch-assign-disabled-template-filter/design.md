## Context

家长端首页“批量分配任务”弹窗的模板下拉框通过 `GET /api/task-templates` 获取模板列表，当前未传递 `enabled` 筛选条件，因此返回了全部未删除模板（含已停用模板）。用户期望已停用模板不应出现在分配任务的可选列表中。

现有 `TaskTemplateService.queryTemplates` 已支持 `enabled` 查询参数，`TaskTemplateController` 也将其作为 `@RequestParam(required = false) Boolean enabled` 接收。因此修复只需在前端请求中加上 `enabled=true`。

## Goals / Non-Goals

**Goals:**
- 批量分配任务弹窗的模板下拉框仅显示启用中的模板。
- 保持模板管理页面仍能看到已停用模板（方便家长重新启用）。

**Non-Goals:**
- 不修改后端接口签名或行为。
- 不修改数据库 schema。
- 不新增 capability 或公共 API。

## Decisions

### 决策 1：前端请求增加 `enabled=true` 查询参数
- **原因**：影响面最小，复用后端已有能力；模板管理页面继续使用无 `enabled` 参数的 `usePaginatedData('/task-templates')`，行为不变。
- **实现**：将 `ParentHomePage` 中 `useApi<PageResult<TaskTemplate>>('/task-templates')` 改为 `useApi<PageResult<TaskTemplate>>('/task-templates?enabled=true')`。

### 决策 2：不在前端本地过滤
- **原因**：避免一次性拉取大量已停用模板后仅过滤展示；后端分页筛选更节省带宽。
- **风险**：无。

## Risks / Trade-offs

- **分页限制**：如果启用模板超过默认页大小，下拉框仍只显示第一页。这是现有行为，本次修复不改变。
- **URL 编码**：`enabled=true` 无需额外编码，直接拼接在路径后可行。
