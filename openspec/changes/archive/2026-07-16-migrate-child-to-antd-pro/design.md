## Context

本变更为 Ant Design Pro 迁移拆分的第四阶段——Child 端页面重建。依赖 Change 1（`migrate-to-antd-pro-infra`）提供 UmiJS 构建、ProLayout 布局、ConfigProvider child 主题和共享组件映射。

## Goals / Non-Goals

**Goals:**
- 将 child 端 5 个页面 UI 层从自建组件替换为 antd/pro-components
- 保持全部业务逻辑不变（API 调用、状态管理、权限控制）
- 保持 child 角色视觉主题

**Non-Goals:**
- 不修改页面功能或业务流程
- 不新增或删除页面
- 不修改后端 API
- 不修改 ChildLayout（在 Change 1 中实现）

## Decisions

### D1: 使用 Card + List 重构任务列表页
今日任务、全部任务使用 antd `Card` + `List` + `Checkbox` 组合实现，替代自建列表组件。任务完成操作使用 `Button` + loading 状态。

### D2: 使用 Card + Tag 重构商城/盲盒页
积分商城、惊喜盲盒使用 antd `Card` 网格布局 + `Tag` 标签 + `Button` 操作，替代自建卡片组件。

### D3: 使用 Timeline + Table 重构兑换历史
兑换历史使用 antd `Timeline` + `Table` 组合实现时间线展示，替代自建列表。

## Risks / Trade-offs

- **[风险] 儿童端 UI 需要活泼友好风格** → 缓解：通过 ConfigProvider child 主题 token 确保鲜艳色彩，通过 `Card` 圆角和大尺寸适配儿童交互

## Open Questions
无。此变更为 Change 1 的延续，技术方案由 Change 1 的 design.md 定义。
