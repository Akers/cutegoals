## Why

三端登录页（`/parent/login`、`/child/login`、`/admin/login`）当前视觉表现不佳，与 Ant Design Pro 经典登录页模板（https://preview.pro.ant.design/user/login）相比存在三层差距：

1. **布局缺陷（Child / Admin）**：`ChildLayout`、`AdminLayout` 未加登录页 guard clause，`/child/login`、`/admin/login` 仍被 `ProLayout layout="side"` 侧边栏包裹，登录页只能渲染在 sider 右侧内容区。ParentLayout 已在上个 hotfix（`69d56cf` / `2026-07-16-fix-parent-login-fullscreen`）修复，但本次范围扩展到 Child/Admin。
2. **样式失效（Child / Admin）**：`ChildLoginPage`、`AdminLoginPage` 外层 className 仍是 `cg-page flex min-h-screen flex-col items-center justify-center`，其中 tailwind 类在当前项目（`web/package.json` 无 tailwindcss/postcss 依赖）完全不生效，只有 `.cg-page`（`max-width:80rem; margin:0 auto`）部分起作用，未真正垂直居中、未全屏。
3. **视觉层级不足（三端共性）**：即使布局与居中正确（ParentLoginPage 已达此层），卡片样式过于朴素（`.cg-card` 仅基础圆角+边框+小阴影），缺少 ant pro 模板的品牌 logo 区、标题/副标题 hierarchy、渐变背景、input prefix icon 等视觉要素。用户反馈"现在的布局还是不好看"。

用户已确认本次范围：**仅视觉重构**（不加 Tab 切换、记住我、忘记密码、第三方登录等需要后端配合的功能），三端**共享布局骨架但配色/氛围按端差异化**（Parent 温馨、Child 童趣、Admin 专业）。

## What Changes

**视觉重构范围**（不改登录逻辑、字段、API、路由结构、AuthGuard、PIN 键盘交互）：

- `web/src/styles/index.css`：基于现有 `.cg-login-screen` / `.cg-card` 扩展，新增登录页视觉类族——
  - `.cg-login-bg`（全屏 flex 居中 + padding）
  - `.cg-login-bg--parent` / `--child` / `--admin`（三端差异化渐变背景）
  - `.cg-login-card`（卡片容器：圆角、大阴影、内边距、max-width）
  - `.cg-login-logo`（顶部 logo 区，居中）
  - `.cg-login-title` / `.cg-login-subtitle`（标题层级，居中、字号/字重梯度）
- `web/src/layouts/ChildLayout.tsx`：在 `useLocation()` 之后增加 `/child/login` guard clause，复用 `ParentLayout` 第 25-32 行已验证模式（跳过 ProLayout，仅渲染 `<ConfigProvider theme={childTheme} locale={zhCN}><Outlet /></ConfigProvider>`）
- `web/src/layouts/AdminLayout.tsx`：同上，针对 `/admin/login` 路径
- `web/src/parent/pages/ParentLoginPage.tsx`：外层从 `.cg-login-screen` 升级为 `.cg-login-bg.cg-login-bg--parent`；卡片改用 `.cg-login-card`；新增 logo 区 + 标题/副标题 hierarchy；input 加 prefix icon（手机/锁图标，来自 `@ant-design/icons`）
- `web/src/child/pages/ChildLoginPage.tsx`：外层从失效 tailwind 类改为 `.cg-login-bg.cg-login-bg--child`；卡片改用 `.cg-login-card`；PIN 数字键盘按钮视觉优化（圆角、大尺寸、悬浮效果）；**保留 PIN 自动提交、5 次失败锁定 15 分钟、离线检测、`childId/deviceId` URL 参数依赖等全部交互逻辑**
- `web/src/admin/pages/AdminLoginPage.tsx`：外层从失效 tailwind 类改为 `.cg-login-bg.cg-login-bg--admin`；卡片改用 `.cg-login-card`；新增 logo 区 + 标题 hierarchy + input prefix icon

## Capabilities

### New Capabilities

（无新 capability）

### Modified Capabilities

（无 spec 文本变更。本次仅重构三端登录页的视觉呈现，不改变已有验收场景的功能语义；登录流程、字段、错误提示、PIN 键盘交互、登录成功跳转、5 次失败锁定策略均保持不变。）

## Impact

- 前端：
  - `web/src/styles/index.css`（新增登录页视觉类族）
  - `web/src/layouts/ChildLayout.tsx`（加 guard clause）
  - `web/src/layouts/AdminLayout.tsx`（加 guard clause）
  - `web/src/parent/pages/ParentLoginPage.tsx`（视觉升级）
  - `web/src/child/pages/ChildLoginPage.tsx`（视觉升级，PIN 逻辑不动）
  - `web/src/admin/pages/AdminLoginPage.tsx`（视觉升级）
- 后端：无改动。
- 测试：本次不新增自动化测试（项目 `web` 侧无视觉回归测试基建）；以本地 `umi dev` 手动浏览器验证 + 既有 vitest 用例不回归为主。

### 相关历史

本次 change 承接 `2026-07-16-fix-parent-login-fullscreen` 留下的 follow-up：该次仅修复 ParentLayout 的 guard clause 与 ParentLoginPage 的全屏居中，明确记录"ChildLayout / AdminLayout 及对应登录页存在完全相同问题，留作后续 follow-up"。本次正是该 follow-up 的执行，同时把视觉层级从"可用"提升到"参照 ant pro 模板"。

### Out of Scope

- 不加 Tab 切换（账号密码 / 手机号）——当前每端只有一种登录方式
- 不加"记住我"复选框、"忘记密码"链接、第三方登录（GitHub/微信等）——均需后端配合新接口，属未来 capability
- 不加底部 footer 版权/备案——内容待定
- 不重构 PIN 键盘组件的内部按钮结构（仅视觉样式调整）
- 不改 `web/config/routes.ts` 路由结构
- 不改 AuthGuard wrapper 行为
- 不改 ParentLayout 已有的 guard clause（保持不变）
