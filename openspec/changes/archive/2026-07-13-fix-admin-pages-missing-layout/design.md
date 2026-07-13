# Design: fix-admin-pages-missing-layout

## Context（背景）

CuteGoals 管理后台 5 个 admin page 组件（Overview / Config / Accounts / Audit / Health）的 early return 分支（offline / loading / error / empty）在 fix-admin-pages-data-shape 修复数据形状之后暴露出 UI 骨架缺失：**这些 early return 直接返回单组件（`<EmptyState>` 等），未用 `<Layout>` 包裹，导致 header（含主导航菜单）+ footer 消失**。

**根因链**：

1. `Layout` 组件（`web/src/shared/components/Layout.tsx` L37-86）渲染整页骨架：`<header>`（logo + `<NavLinks>` + 登出按钮）+ `<main>`（children）+ `<footer>`（版权）。
2. `<NavLinks>` 根据 `role` 渲染对应菜单项（admin → 概览 / 账号 / 审计 / 健康）。
3. admin pages 的 early return 模式（复制粘贴自同一模板）在所有状态分支（offline / loading / error / empty）都直接返回单组件，未补 `<Layout>`。
4. 正常 happy path（有数据）走 `<Layout>` 包裹的主 JSX，菜单正常；其他状态分支绕开 `<Layout>`，菜单消失。
5. 用户当前只在审计页复现，是因为 dev 环境审计日志为空触发 empty 分支；其他 4 个 admin 页面在 dev 环境都有数据，但 loading / error / offline 场景同样会复现。

**底层约束**：

- `Layout` 组件被所有角色（admin / parent / child）的多个页面共享，**不能改其结构**（会引入跨页面回归）。
- admin page 组件的 early return 模式是 React 常见写法（early return 最小化嵌套），不应彻底重构为「单 JSX 树 + 条件渲染」——那样改动面过大、可读性下降（见决策 4）。
- `AdminInitPage` / `AdminLoginPage` 是登录前页面，intentionally 无菜单栏（用户还未认证，不应看到导航），不在本修复范围。

## Goals / Non-Goals

**Goals**：

1. 让 admin 5 个 page 组件的所有状态分支（offline / loading / error / empty / happy）都显示完整的页面骨架（header + 菜单 + footer）。
2. 保持单文件改动范围（`web/src/admin/pages/index.tsx`），不改 `Layout` 组件、不改后端、不改业务逻辑。
3. 保持现有 early return 代码风格（不重构为单 JSX 树），仅补充 `<Layout>` 包裹。

**Non-Goals**：

1. ❌ 不改 `Layout` 组件结构。
2. ❌ 不抽公共 HOC / wrapper（`withAdminLayout` 等）——5 个组件 × 4 处 = 20 处，加 wrapper 需重写所有组件签名，扩大改动面（见决策 4）。
3. ❌ 不改 `AdminInitPage` / `AdminLoginPage`（登录前页面 intentionally 无菜单）。
4. ❌ 不改 parent / child 端页面（本次只修 admin；parent / child 端若有同样问题应另起 change，且需先确认是否复现）。
5. ❌ 不引入 Suspense / ErrorBoundary 替代 early return（架构改动过大，超出 hotfix 范围）。

## Decisions

### 决策 1：仅修 admin 5 个 page 组件，不动 Layout

**理由**：

- `Layout` 是被所有角色共享的展示组件，改它会影响 parent / child 端页面，扩大回归面。
- 缺陷在调用方（admin pages 未包 Layout），不在被调用方（Layout 本身行为正确）。
- 修复点应在缺陷产生处，符合「最小扰动」原则。

### 决策 2：包裹所有 4 类 early return，而非只修 EmptyState

**理由**：

- 用户报告的 empty 分支只是冰山一角。loading（首次进入页面加载中）、error（API 返回 401/500/网络错误）、offline（断网）三个分支同样缺 Layout，下次会被报为新 bug。
- 修复成本几乎一样：每处加 `<Layout>...</Layout>` 包裹。
- 一次性修复 20 处 vs 分 4 次修 5 处，前者改动集中、review 一次到位、回归风险一次承担。
- 如果只修 EmptyState，loading / error / offline 场景下菜单消失仍存在，是「假修复」。

### 决策 3：AdminHealthPage 的 `checks` 派生数组位置

**背景**：`AdminHealthPage` 在 happy path 主 JSX 之前（L297-320）有一个 `const checks: HealthCheck[] = [...]` 派生数组，依赖 `data` 字段。这个派生在 L295 `if (!data) return <EmptyState .../>` 之后。

**决策**：保持现状（派生在 early return 之后）。所有 early return 仍按决策 2 加 `<Layout>` 包裹，主 JSX（含 `checks` 派生与 `<Layout>`）保持不动。

**理由**：

- TypeScript 类型 narrowing：early return 之后 `data` 被 narrow 为 defined，无需 `data?.` 可选链；如果把派生移到 early return 之前，需要重写为 `data?.database?.status` 等可选链，扩大改动面。
- 现有结构是 React 常见 pattern，不应在 hotfix 中重构。
- 修复目标只是「early return 加 Layout 包裹」，不涉及主 JSX 改动。

### 决策 4：不抽公共 wrapper（HOC / render prop）

**理由**：

- 抽 `withAdminLayout(Component)` 或 `<AdminPageLayout>` 需要把 5 个组件的签名从 `export function AdminXxxPage()` 改为 `export const AdminXxxPage = withAdminLayout(function AdminXxxPage() {...})`，5 处签名重写 + 5 个 early return 模式调整。
- 当前缺陷是「缺 Layout 包裹」，最小修复就是「加 Layout 包裹」。抽 wrapper 是设计改进，不是 bug 修复。
- 5 个组件共享同一文件，未来若要统一抽 wrapper 可在独立 change 中做（带完整 design + review）。
- hotfix 原则是「最小改动聚焦根因」，wrapper 抽取超出范围。

## Risks（风险）

| 风险 | 评估 | 缓解 |
|------|------|------|
| early return 内的 Layout 包裹后，loading 状态下 `<Layout>` 内部 `useAuth()` / `useRole()` 是否安全？ | `Layout` 仅读取 context（`role` / `isAuthenticated` / `logout`），不触发额外 API 调用；admin page 早期阶段 `AuthGuard` 已确保认证完成，`Layout` 在 early return 阶段读取的 context 与 happy path 一致 | 无需额外缓解，`Layout` 设计上就支持任意子组件 |
| `<Layout>` 在 loading / error 状态下显示 header / footer 是否合理？ | 合理且是常见 UX 模式：用户应始终能看到导航菜单以便切换页面或登出，而不是被「卡」在某个无 menu 的状态 | 无需缓解，这是修复目标 |
| parent / child 端是否有同样问题？ | 本次不查（Non-Goals 4）。若用户后续报告，另起 change | 在 Non-Goals 明确声明范围 |
| 改动后视觉测试 | CLI agent 无浏览器工具，采用等价证据覆盖（见 tasks.md 验证段） | `npx tsc -b` + grep 根因消除 + admin App.test.tsx 单测 + 浏览器端留给用户最终确认 |

## Open Questions

无。所有不确定点（Layout 是否含菜单、是否影响其他角色、AdminHealthPage checks 派生位置）都已在 Context 与 Decisions 中确认。
