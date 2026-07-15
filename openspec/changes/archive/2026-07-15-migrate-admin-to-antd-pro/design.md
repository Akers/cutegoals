## Context

本变更为 Ant Design Pro 迁移拆分的第二阶段——Admin 端页面重建。依赖 Change 1（`migrate-to-antd-pro-infra`）提供 UmiJS 构建、ProLayout 布局、ConfigProvider admin 主题和共享组件映射。

## Goals / Non-Goals

**Goals:**
- 将 admin 端 5 个页面 UI 层从自建组件替换为 antd/pro-components
- 保持全部业务逻辑不变（API 调用、状态管理、权限控制）
- 保持 admin 角色视觉主题

**Non-Goals:**
- 不修改页面功能或业务流程
- 不新增或删除页面
- 不修改后端 API
- 不修改 AdminLayout（在 Change 1 中实现）

## Decisions

### D1: 使用 ProTable 重构列表/表格页面
实例概览、账号管理、审计日志使用 ProTable 实现，利用内置搜索栏、分页、列排序、行操作能力。数据请求使用自建 `useApi` hook，通过 ProTable `request` 属性对接。

### D2: 使用 ProForm 重构配置类页面
系统配置、实例初始化使用 ProForm 实现，利用内置布局（`layout="vertical"`）、校验反馈和提交 loading。

### D3: 使用 Descriptions/Card/Statistic 重构仪表板
健康面板使用 antd `Descriptions`、`Card`、`Statistic` 组件实现状态展示，替代自建 CardSection 和自建布局。

## Risks / Trade-offs

- **[风险] ProTable 列定义语法与自建表格差异大** → 缓解：逐列映射，保持相同的列顺序和字段名

## Open Questions
无。此变更为 Change 1 的延续，技术方案由 Change 1 的 design.md 定义。
