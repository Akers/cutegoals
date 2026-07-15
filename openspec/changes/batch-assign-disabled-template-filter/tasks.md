## 修复任务

- [x] 1. 修改 `web/src/parent/pages/index.tsx` 中批量分配任务弹窗的模板列表请求，从 `/task-templates` 改为 `/task-templates?enabled=true`。
- [x] 2. 确认模板管理页面仍使用无 `enabled` 筛选的分页接口，行为不变。
- [x] 3. 运行前端类型检查/构建（如 `npm run build` 或 `tsc --noEmit`）确保无错误。
- [ ] 4. 提交修复并进入 verify。
