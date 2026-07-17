# Tasks: fix-aux-page-centering

- [x] 在 `web/src/styles/index.css` 新增 `.cg-screen` 类（全屏 flex 居中，无渐变背景，置于 `.cg-login-bg` 之后）
- [x] `web/src/shared/auth/AuthGuard.tsx`：L16 spinner `flex justify-center py-12` → inline style；L29/L54 外层 → `cg-screen`；L30/L55 卡片 → `cg-login-card`
- [x] `web/src/child/pages/ChildBindPage.tsx`：L74/L93 外层 → `cg-screen`；L75/L94 卡片 → `cg-login-card`
- [x] `web/src/admin/pages/AdminInitPage.tsx`：L73 外层 → `cg-screen`；L74 卡片 → `cg-login-card`；L81 表单 `flex flex-col gap-4` → `cg-login-form`
- [x] `web/src/child/pages/ChildLoginPage.tsx:107`：spinner `flex justify-center py-12` → inline style
- [x] `cd web && npm run lint` 通过（tsc --noEmit）
- [x] `cd web && npm run build` 通过（umi build）+ grep `dist/umi.css` 确认 `.cg-screen{` 进入打包产物
- [x] `cd web && npm run test` 通过（vitest 86/86，预存在 2 collection 失败可接受）
- [x] 提交（verify 阶段分支处理时执行，commit message: `fix: 辅助页面全屏居中并清理失效 tailwind 类`）
