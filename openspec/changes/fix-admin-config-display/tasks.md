# Tasks: fix-admin-config-display

- [ ] 1. 新增失败回归测试 `web/src/__tests__/admin-config-page.test.tsx`（渲染契约 + 保存契约，mock `getClient()`），运行确认按预期失败（RED 证据）
- [ ] 2. 修复 `web/src/admin/pages/index.tsx` 的 `AdminConfigPage`：`ConfigEntry[]` 类型、按 key 渲染标签/描述、masked 项用 password 输入框、保存仅提交变更键并处理成功/失败反馈
- [ ] 3. 运行回归测试确认转绿，再运行 `web` 全部 vitest 与构建（`npm run build`）确认通过
