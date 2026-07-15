## Context

本变更为 Ant Design Pro 迁移拆分的第三阶段——Parent 端页面重建。依赖 Change 1（`migrate-to-antd-pro-infra`）提供 UmiJS 构建、ProLayout 布局、ConfigProvider parent 主题和共享组件映射。

## Goals / Non-Goals

**Goals:**
- 将 parent 端 10 个页面 UI 层从自建组件替换为 antd/pro-components
- 保持全部业务逻辑不变（API 调用、状态管理、权限控制）
- 保持 parent 角色视觉主题

**Non-Goals:**
- 不修改页面功能或业务流程
- 不新增或删除页面
- 不修改后端 API
- 不修改 ParentLayout（在 Change 1 中实现）

## Decisions

### D1: 使用 ProTable 重构列表类页面
家庭管理、孩子档案、任务模板、任务分配、积分、奖品、盲盒、兑换履约使用 ProTable 实现，利用内置搜索栏、分页、列排序、行操作。

### D2: 使用 ProForm 重构表单类页面
任务审核使用 ProForm + Form.List 实现批量审核表单，奖品/盲盒创建使用 ProForm 实现。

### D3: 家庭概览使用多种 antd 组件组合
家庭概览类似仪表板，使用 antd `Card`、`Statistic`、`Table` 组合实现，替代自建组件。

## Risks / Trade-offs

- **[风险] 家长端 10 页为最大子模块，逐页迁移工作量大** → 缓解：按页面顺序迁移，每页独立可验证
- **[风险] 任务审核页面交互复杂（批量审批+滑动）** → 缓解：使用 antd Table rowSelection + Button 组合替代滑动操作，符合 antd 设计范式

## Open Questions
无。此变更为 Change 1 的延续，技术方案由 Change 1 的 design.md 定义。
