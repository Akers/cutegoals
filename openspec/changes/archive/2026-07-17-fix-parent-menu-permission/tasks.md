# 修复任务清单

- [x] 1. 新增回归测试 `web/src/wrappers/__tests__/AuthGuard.test.tsx`（双角色账号访问 `/parent` 放行；仅 CHILD 角色访问 `/parent` 403；未认证重定向 `/parent/login`），运行确认双角色用例失败（RED）
- [x] 2. `web/src/shared/role.ts` 新增 `normalizeRoles()` 纯函数
- [x] 3. `web/src/wrappers/AuthGuard.tsx` 改用 `useAuth().account.roles` + `normalizeRoles()` 成员检查
- [x] 4. `web/src/app.tsx` 的 `deriveRole` 复用 `normalizeRoles()`（行为不变）
- [x] 5. 回归测试转绿，运行 `cd web && npm test` 全量前端测试通过（91 通过；2 个既有套件因环境缺 `react-router-dom` 无法加载，已 stash 验证为预存问题，与本修复无关）
- [x] 6. 运行 `cd web && npm run lint` 与 `npm run build` 通过
