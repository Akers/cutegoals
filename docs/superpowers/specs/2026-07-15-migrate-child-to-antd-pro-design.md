---
comet_change: migrate-child-to-antd-pro
role: technical-design
canonical_spec: openspec
---

# Child 页面 Ant Design Pro 迁移 — 深度技术设计

- Date: 2026-07-15
- Change: migrate-child-to-antd-pro

## 1. 范围

| 页面 | 主要功能 |
|------|---------|
| ChildHomePage | 今日任务列表、任务状态 |
| ChildTasksPage | 全部任务，完成/提交审核 |
| ChildPrizesPage | 积分商城，兑换奖品 |
| ChildBlindBoxesPage | 惊喜盲盒，抽取 |
| ChildExchangesPage | 兑换历史记录 |

## 2. 组件映射

复用 Admin/Parent 已验证映射：

| 自建/Tailwind | antd |
|---------------|------|
| PageShell / Layout | 移除（ProLayout 提供） |
| Tailwind className | Row/Col/Space |
| PageHeader / `<h1>` | Typography.Title level={4} |
| StatusBadge | Tag + statusLabel() |
| CardSection | Card |
| native table | Table + pagination + onChange |
| native select | Select |
| native input | Input |
| Modal | Modal |
| useToast() | App.useApp().message |
| button | Button |

## 3. 迁移策略

- 5 页 ~618 行，单次提交
- 所有 Table 必须加 onChange + page state

## 4. 风险

- Child 页面使用了 `useLowPerformance`/`useReducedMotion` → 保留 hooks 不变
- 盲盒动画逻辑保留，仅替换 UI 外壳
