- [x] 修改 `web/src/app.tsx` 的 `deriveRole`：把每项 role 字符串归一化（`toUpperCase()` + 去 `ROLE_` 前缀）后比较 `INSTANCE_ADMIN`/`PARENT`，使后端返回的大写角色被正确识别为 `'admin'`/`'parent'`
- [x] 修改 `web/src/admin/pages/AdminInitPage.tsx:68`：初始化成功分支 `history.replace('/admin')` 改为 `history.replace('/admin/login')`
- [x] 运行 `cd web && npm run lint`（`tsc --noEmit` + css 检查）确认无类型错误
- [x] 运行 `cd web && npm run test` 确认现有 vitest 用例不回归（86/86 通过；2 个预存在的 react-router-dom collection 失败与本 hotfix 无关，环境层依赖问题）
- [x] 运行 `cd web && npm run build`（`umi build`）确认生产构建通过（dist 已输出，构建耗时 5.84s）

> 注：手动浏览器视觉验证在 verify 阶段执行；提交归档在 archive 阶段执行。
