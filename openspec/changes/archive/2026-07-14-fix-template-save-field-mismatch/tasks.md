# Tasks: fix-template-save-field-mismatch

- [x] 1.1 `TaskTemplate` 接口 `title` → `name`，移除 `basePoints`；`Difficulty` 接口 `points` → `rewardPoints` + `displayOrder`。
- [x] 1.2 `handleSave` 发送 `name` + `difficulties` 数组；编辑时带 `version`；检查 `res.error`。
- [x] 1.3 `openEdit` 用 `t.name` 和 `t.difficulties?.[0]?.rewardPoints`。
- [x] 1.4 列表卡片用 `t.name` 和 difficulties 积分。
- [x] 1.5 验证 `npx tsc -b` 0 errors。
- [x] 1.6 验证 `npm test` 与 baseline 一致（14 failed / 65 passed）。
- [x] 1.7 git commit：`fix(parent): align template fields with backend contract`。
