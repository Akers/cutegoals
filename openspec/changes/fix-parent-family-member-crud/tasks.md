# Tasks: fix-parent-family-member-crud

- [x] 1.1 在 `ParentFamilyPage` 中新增孩子状态（`usePaginatedData''/child-profiles`）与表单状态。
- [x] 1.2 渲染孩子列表，每个孩子展示「移除」按钮。
- [x] 1.3 添加「添加孩子」按钮及 Modal 表单，调用 `POST /api/family/children`。
- [x] 1.4 实现移除孩子确认对话框，调用 `DELETE /api/family/children/{id}`，成功后刷新。
- [x] 1.5 验证 `npx tsc -b` 0 errors。
- [x] 1.6 验证 `npm test` 与 baseline 一致（14 failed / 65 passed），0 新增失败。
- [x] 1.7 验证 `mvn -pl :web -am test` 通过。
- [x] 1.8 git commit：`fix(parent): add child management to family page`。
- [x] 1.9 运行 build guard 推进到 verify。
