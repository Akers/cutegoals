# Tasks: fix-parent-child-list-refresh

- [x] 1.1 `Family` 接口增加 `children: ChildProfile[]` 字段，补充 `ChildProfile` 的 `birthYear` 可选字段。
- [x] 1.2 `ParentFamilyPage` 移除 `usePaginatedData('/family/children')`，孩子板块改用 `data.children`。
- [x] 1.3 `handleSaveChild` / `handleRemoveChild` 仅调用 `refetch()`。
- [x] 1.4 验证 `npx tsc -b` 0 errors。
- [x] 1.5 验证 `npm test` 与 baseline 一致（14 failed / 65 passed），0 新增失败。
- [x] 1.6 git commit：`fix(parent): use family endpoint as single source for child list`。
- [x] 1.7 运行 build guard 推进到 verify。
