# Tasks: fix-parent-family-members-management

- [x] 1.1 在 `ParentFamilyPage` 中为 `PARENT` 成员渲染「移除」按钮（排除当前用户）。
- [x] 1.2 在页面合适位置添加「退出家庭」按钮。
- [x] 1.3 添加确认对话框，调用 `DELETE /api/family/members/{id}` 或 `POST /api/family/members/me/leave`。
- [x] 1.4 操作成功后 `refetch()` 刷新家庭数据。
- [x] 1.5 验证 `npx tsc -b` 0 errors。
- [x] 1.6 验证 `npm test` 与 baseline 一致（14 failed / 65 passed），0 新增失败。
- [x] 1.7 git commit：`fix(parent): add family member management actions`。
- [x] 1.8 运行 build guard 推进到 verify。
