# Brainstorm Summary

- Change: migrate-parent-to-antd-pro
- Date: 2026-07-15

## 确认的技术方案

复用 Admin 已验证的组件映射模式，将 Parent 端 10 页面 + 2 子组件迁移到 antd：

| 替换项 | 目标 |
|--------|------|
| PageShell wrapper | 移除（ProLayout 已接管） |
| PageHeader/Tailwind 标题 | Typography.Title |
| Tailwind grid/flex/padding | Row/Col/Space |
| StatusBadge | Tag + 中文映射 statusLabel() |
| native table | Table（含分页 onChange） |
| native select | Select |
| native input/textarea | Input/Input.TextArea |
| CardSection | Card |
| self-built Modal | Modal |
| useToast() | App.useApp().message |

## 关键取舍与风险

- 10 页 1677 行，建议分批提交（每批 2-3 页）
- 子组件 TaskTypeFilter/TaskTypeConfigForms 同步替换
- 任务模板/批量分配为最复杂页，需保留核心逻辑

## 测试策略

- 保持 94 tests 通过（测试覆盖核心逻辑，不依赖 Tailwind class）

## Spec Patch

无
