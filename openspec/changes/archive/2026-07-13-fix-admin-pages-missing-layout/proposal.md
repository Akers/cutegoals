# Proposal: fix-admin-pages-missing-layout

## Why（问题与根因）

用户点击管理后台「审计」菜单进入页面时，整页只显示「暂无审计日志 / 当前列表为空」，**上方菜单栏（概览 / 账号 / 审计 / 健康）消失，整页没有 header / footer**。

**根因**：`web/src/admin/pages/index.tsx` 中所有 admin page 组件的 early return 分支（offline / loading / error / empty）直接返回单组件，**未用 `<Layout>` 包裹**。

具体位置：

- `AdminOverviewPage` L43-46：4 个 early return（`<OfflineState>` / `<LoadingState>` / `<ErrorState>` / `<EmptyState>`）
- `AdminConfigPage` L111-114：4 个 early return
- `AdminAccountsPage` L162-165：4 个 early return
- `AdminAuditPage` L224-227：4 个 early return
- `AdminHealthPage` L292-295：4 个 early return

共 **5 个组件 × 4 个 early return = 20 处** 缺陷代码。

`Layout` 组件（`web/src/shared/components/Layout.tsx` L37-86）渲染整个页面骨架：

- `<header>`：CuteGoals logo + `<NavLinks>`（admin 显示「概览 / 账号 / 审计 / 健康」四个导航）+ 登出按钮
- `<main>`：含 children
- `<footer>`：版权信息

当 data 为空（或 loading / error / offline）时，early return 绕开 `<Layout>`，页面只剩孤零零的 `<EmptyState>` / `<LoadingState>` / `<ErrorState>` / `<OfflineState>` 组件，所以菜单栏消失。

**为什么用户只在审计页看到**：dev 环境当前没有审计日志数据，`AdminAuditPage` L227 `if (!data || data.content.length === 0) return <EmptyState title="暂无审计日志" />` 触发；其他 admin 页面（overview / config / accounts / health）在 dev 环境都有数据，所以正常进入 `<Layout>` 渲染流程，菜单栏正常。但 loading / error / offline 场景下，**所有 5 个 admin 页面都会复现菜单消失**，这是系统性缺陷。

**为什么前几个 hotfix 没暴露**：fix-admin-401 修复认证前，admin 页面在 `AuthGuard` 阶段就 redirect 到登录页，根本进不到 admin page 组件；fix-admin-pages-500 修复 HTTP 500 前，`useApi` 拿到 error，`<ErrorState>` 同样绕过 Layout（但用户感知是「加载失败」，菜单消失被错误状态掩盖）；fix-admin-pages-data-shape 修复数据形状前，审计页 `data.map is not a function` 抛错被 ErrorBoundary 捕获。现在 200 + 正确数据形状 + 空数据 → `<EmptyState>` 绕过 Layout → 菜单消失被用户感知。

## What Changes（修复内容）

仅修改前端单文件 `web/src/admin/pages/index.tsx`（5 个组件同处一文件）：

1. **`AdminOverviewPage`**：将 L43-46 四个 early return 用 `<Layout>` 包裹（children 是原 `<OfflineState>` / `<LoadingState>` / `<ErrorState>` / `<EmptyState>`）。
2. **`AdminConfigPage`**：将 L111-114 四个 early return 用 `<Layout>` 包裹。
3. **`AdminAccountsPage`**：将 L162-165 四个 early return 用 `<Layout>` 包裹。
4. **`AdminAuditPage`**：将 L224-227 四个 early return 用 `<Layout>` 包裹。
5. **`AdminHealthPage`**：将 L292-295 四个 early return 用 `<Layout>` 包裹（注意：原 L297-320 `checks` 数组派生在 L295 之后，需移到 L295 之前或一并保留在 `<Layout>` 内的 JSX 中——见 design.md 决策 3）。

**不做的事**：

- 不改 `Layout` 组件本身（header / nav / footer 结构稳定，被多个角色页面共享）
- 不改后端、不改 API 契约、不改业务逻辑
- 不改 `AdminInitPage` / `AdminLoginPage`（这两个是登录前页面，intentionally 无菜单栏）
- 不抽公共 HOC / wrapper（5 个组件 × 4 处 = 20 处，加 wrapper 反而扩大改动面，见 design.md 决策 4）

## Capabilities（能力规格影响）

无新增、无修改、无删除。本次仅修复前端 UI 渲染层的 Layout 包裹缺失，不影响 `admin-access-control` capability 的验收场景（认证、授权、审计记录仍由前序 hotfix 保证）。不创建 delta spec。

## Impact（影响范围）

- **改动文件数**：1（`web/src/admin/pages/index.tsx`）
- **改动性质**：纯前端 JSX 包裹层补充，不改任何业务逻辑、不改类型、不改 API 调用
- **回归风险**：极低。`<Layout>` 是纯展示组件，包裹它不改变状态、不改变数据流，只补充缺失的页面骨架
- **下游影响**：无。前端单文件改动，不涉及任何外部契约
- **可验证性**：
  - 前端 `npx tsc -b` 类型检查通过
  - 浏览器端访问 5 个 admin 页面（含 loading / error / empty / offline 场景）均显示完整 header + 菜单 + footer
  - 当前 dev 环境：审计页（空数据）能再现修复效果，菜单栏回归
