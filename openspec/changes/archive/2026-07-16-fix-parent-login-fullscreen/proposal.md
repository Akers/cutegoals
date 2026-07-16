## Why

访问 `http://localhost:8000/parent/login` 时，登录页被嵌套在带左侧边栏菜单（Sider）的 `ParentLayout` 中，导致：

1. 页面左侧始终显示家长端的完整菜单（家庭概览、家庭管理等 10 项），而用户此时尚未登录，不应看到任何导航。
2. 登录框被挤压到 Sider 右侧的内容区，无法占据整个视口。
3. 登录页外层使用的 Tailwind 风格类（`flex min-h-screen items-center justify-center` 等）在当前项目（无 tailwindcss / postcss 依赖）下完全不生效，实际只有 `.cg-page`（`max-width: 80rem; margin: 0 auto`）起作用，因此登录框既未真正水平垂直居中，也未全屏。

期望：登录页是全屏页面，登录框水平垂直居中，不显示左侧边栏菜单。

## What Changes

- `web/src/layouts/ParentLayout.tsx`：增加对 `/parent/login` 路由的条件分支。当 `location.pathname === '/parent/login'` 时，跳过 `ProLayout`（不渲染 Sider 与顶部导航），仅保留 `ConfigProvider`（主题/locale）包裹 `<Outlet />`，让登录页直接占据整个视口。其他 `/parent/*` 路由的布局行为不变。
- `web/src/styles/index.css`：新增语义化类 `.cg-login-screen`，实现 `min-height: 100vh; display: flex; align-items: center; justify-content: center; padding`，提供项目内可复用的全屏居中容器，与 CSS 变量主题一致。
- `web/src/parent/pages/ParentLoginPage.tsx`：将外层容器 className 由不生效的 `cg-page flex min-h-screen flex-col items-center justify-center` 改为 `cg-login-screen`，使水平垂直居中真正生效；内层卡片保持 `.cg-card` 样式与 `max-w-md` 约束（通过 inline style 落实宽度，因 tailwind 类不生效）。

## Capabilities

### New Capabilities

（无新 capability）

### Modified Capabilities

（无 spec 文本变更。本次仅修复已有家长端登录页的布局行为，不改变其验收场景的功能语义；登录流程、字段、错误提示、登录成功跳转均保持不变。）

## Impact

- 前端：
  - `web/src/layouts/ParentLayout.tsx`（核心修复，根因所在）
  - `web/src/styles/index.css`（新增 `.cg-login-screen` 类）
  - `web/src/parent/pages/ParentLoginPage.tsx`（替换不生效的 Tailwind 类）
- 后端：无改动。
- 测试：本次不新增自动化测试（项目 `web` 侧无布局/视觉回归测试基建，`vitest` 仅覆盖 hook/逻辑）；以本地 `umi dev` 手动验证为主。

### 已知相同根因（不在本次范围）

`web/src/layouts/ChildLayout.tsx`、`web/src/layouts/AdminLayout.tsx` 及对应登录页存在完全相同的「登录页被嵌入带 Sider 的 ProLayout」问题。用户本次仅报告 `/parent/login`，为保持 hotfix 聚焦，三端统一修复留作后续 follow-up change。
