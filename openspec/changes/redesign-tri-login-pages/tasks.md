- [x] 在 `web/src/styles/index.css` 新增登录页视觉类族：`.cg-login-bg` + 三端差异化（`--parent`/`--child`/`--admin`）+ `.cg-login-card` + `.cg-login-logo` + `.cg-login-title` + `.cg-login-subtitle`（可选：`.cg-pin-key` 用于 Child PIN 键盘视觉优化）
- [x] `web/src/layouts/ChildLayout.tsx`：在 `useLocation()` 之后加 `/child/login` guard clause（复用 ParentLayout:25-32 模式，仅渲染 `<ConfigProvider theme={childTheme} locale={zhCN}><Outlet /></ConfigProvider>`）
- [x] `web/src/layouts/AdminLayout.tsx`：在 `useLocation()` 之后加 `/admin/login` guard clause（同上模式，使用 adminTheme）
- [x] `web/src/parent/pages/ParentLoginPage.tsx`：外层升级为 `cg-login-bg cg-login-bg--parent`；卡片改 `cg-login-card`；新增 logo 区 + `.cg-login-title`/`.cg-login-subtitle`；input 加 `MobileOutlined`/`LockOutlined` prefix icon；保留登录逻辑、字段、错误提示、跳转不变
- [x] `web/src/child/pages/ChildLoginPage.tsx`：外层改为 `cg-login-bg cg-login-bg--child`（替换失效的 tailwind 类）；卡片改 `cg-login-card`；PIN 数字键盘按钮视觉优化（圆角、大尺寸、悬浮）；**保留 PIN 自动提交、5 次失败锁定 15 分钟、离线检测、`childId/deviceId` URL 参数依赖等全部交互逻辑**
- [x] `web/src/admin/pages/AdminLoginPage.tsx`：外层改为 `cg-login-bg cg-login-bg--admin`（替换失效的 tailwind 类）；卡片改 `cg-login-card`；新增 logo 区 + 标题 hierarchy + input prefix icon；保留登录逻辑不变
- [x] 运行 `cd web && npm run lint`（tsc --noEmit）确认无类型错误
- [x] 运行 `cd web && npm run test`（vitest run）确认现有用例不回归（86/86 通过；2 个预存在的 react-router-dom collection 失败与本 hotfix 无关）
- [x] 运行 `cd web && npm run build`（umi build）确认生产构建通过

> 注：手动浏览器视觉验证（三端全屏 + 居中 + 无侧边栏 + ant pro 风格 + 功能回归）在 verify 阶段执行；提交归档在 archive 阶段执行。
