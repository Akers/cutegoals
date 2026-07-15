## Why

家长端“批量分配任务”弹窗的模板下拉框目前会展示已停用的任务模板，例如模板“测试1”。这会导致家长误选无效模板，并在后端触发 `TASK_TEMPLATE_INACTIVE` 错误。本次修复让下拉框仅展示启用中的模板，避免无效选择。

## What Changes

- 修改家长端批量分配任务弹窗的模板列表请求，仅查询 `enabled=true` 的任务模板。
- 不修改后端接口、数据库 schema、业务规则或模板管理页面行为。
- 不新增 capability。

## Capabilities

### New Capabilities

无

### Modified Capabilities

无（实现层修复，不涉及需求变更）

## Impact

- `web/src/parent/pages/index.tsx`：批量分配任务弹窗模板下拉框的数据获取路径。
- 可能影响的端点：`GET /api/task-templates`（已支持 `enabled` 查询参数）。
