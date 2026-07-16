- [x] 修改 `web/src/layouts/ParentLayout.tsx`：新增 `/parent/login` 路由分支，跳过 ProLayout 仅渲染 ConfigProvider + Outlet
- [x] 在 `web/src/styles/index.css` 新增 `.cg-login-screen` 全屏居中容器类
- [x] 修改 `web/src/parent/pages/ParentLoginPage.tsx`：外层用 `cg-login-screen`，内层卡片用 inline style 落实宽度/内边距
- [x] 运行 `cd web && npm run lint`（tsc --noEmit）确认无类型错误
- [x] 运行 `cd web && npm run test` 确认现有 vitest 用例不回归（86/86 通过；2 个预存在的 react-router-dom collection 失败与本 hotfix 无关）
- [x] 运行 `cd web && npm run build`（umi build）确认生产构建通过（dist 已输出 ParentLoginPage.js/ParentLayout.js）

> 注：手动浏览器视觉验证在 verify 阶段执行；提交归档在 archive 阶段执行。
