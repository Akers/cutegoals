## 修复方案

### 根因

`/parent/login` 是 `/parent` 路由组的子路由，`web/config/routes.ts:15-31` 将 `ParentLayout` 作为整个 `/parent` 组的 `component`，登录页与所有受保护页面共享同一个 Layout。而 `web/src/layouts/ParentLayout.tsx:25-61` 无条件返回一个 `layout="side"` 的 `ProLayout`，该组件始终渲染左侧 Sider 菜单（10 个菜单项），登录页只能在 `<Outlet />`（第 58 行）即 Sider 右侧的内容区渲染。

叠加问题：`ParentLoginPage.tsx:52` 的外层 className 依赖 Tailwind 工具类（`flex min-h-screen flex-col items-center justify-center`），但项目 `web/package.json` 既无 `tailwindcss` 也无 `postcss`，这些类完全不生效。实际只有 `.cg-page`（`index.css:48`，`max-width: 80rem; margin: 0 auto; padding`）起作用——只做水平方向有限宽度的居中，没有垂直居中、没有全屏。

### 修改点

1. **`ParentLayout.tsx`（核心）**：在 `const location = useLocation();` 之后插入路由判断。当 `location.pathname === '/parent/login'` 时，提前 `return` 一个只含 `ConfigProvider`（保留 `parentTheme` 与 `zhCN` locale，视觉与其他页面一致）+ `<Outlet />` 的 JSX，完全跳过 `ProLayout`，从而不渲染 Sider、顶部导航与 avatar。这样登录页直接挂载在根视口下，成为全屏页面。

2. **`index.css`**：在 `@layer components` 中新增 `.cg-login-screen`：
   ```css
   .cg-login-screen {
     min-height: 100vh;
     display: flex;
     align-items: center;
     justify-content: center;
     padding: 1rem;
   }
   ```
   覆盖 `.cg-page` 的 `max-width: 80rem` 约束，专门用于全屏居中场景。后续 child/admin 登录页修复时可复用。

3. **`ParentLoginPage.tsx`**：
   - 外层容器 className 从 `cg-page flex min-h-screen flex-col items-center justify-center` 改为 `cg-login-screen`。
   - 内层卡片 className 从 `w-full max-w-md cg-card p-6` 改为 `cg-card`，并通过 `style={{ width: '100%', maxWidth: '28rem', padding: '1.5rem' }}` 落实宽度与内边距（因 `w-full max-w-md p-6` 等 Tailwind 类不生效）。
   - 其余结构（PageHeader、错误提示、form、FormField、Button、底部提示）保持不变；登录请求逻辑、字段、跳转目标 `/parent` 均不变。

### 边界

- 仅影响 `/parent/login`。其他 `/parent/*` 路由（家庭概览、任务分配等）仍走带 Sider 的 `ProLayout`，行为不变。
- 不改动路由配置 `routes.ts`，不引入新 Layout 文件，不改 `ProLayout` 的菜单项或主题。
- 不改动登录的 API 调用、AuthGuard、token 处理；登录成功后仍 `history.push('/parent')`。
- 不修复 child/admin 端的相同问题（见 proposal「已知相同根因」）。
- `.cg-login-screen` 的 `min-height: 100vh` 在移动端浏览器地址栏动态伸缩时可能略有出入，但 ProLayout 移动端本身不显示 Sider（`actionsRender` 已处理 `isMobile`），且本次范围是桌面端布局修复，可接受。

## 测试

- 手动验证（主要）：
  1. `cd web && npm run start`，浏览器访问 `http://localhost:8000/parent/login`。
  2. 确认：左侧无菜单栏，登录框在视口中水平垂直居中，resize 浏览器窗口时居中保持。
  3. 确认：未登录直接访问 `/parent` 仍被 AuthGuard 拦截到登录页（行为不变）。
  4. 确认：登录成功后跳转 `/parent`，此时出现正常的 Sider 菜单（布局恢复）。
- 类型检查：`cd web && npm run lint`（`tsc --noEmit`）确认无类型错误。
- 单元测试：`cd web && npm run test` 确认现有 vitest 用例不回归（登录页本身无测试，不应受影响）。
