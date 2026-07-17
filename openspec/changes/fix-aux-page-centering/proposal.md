# Proposal: fix-aux-page-centering

## Why（动机）

三连 hotfix（`fix-parent-login-fullscreen` / `redesign-tri-login-pages` / `fix-global-styles-not-loaded`）后，三端登录页布局已正确全屏居中，但 4 个辅助页面仍存在布局缺陷：

1. `web/src/shared/auth/AuthGuard.tsx`（loading / error / 受保护守卫页）
2. `web/src/admin/pages/AdminInitPage.tsx`（首次部署初始化向导）
3. `web/src/child/pages/ChildBindPage.tsx`（设备绑定流程）
4. `web/src/child/pages/ChildLoginPage.tsx:107`（无 `childId` 时过渡 spinner）

这些页面的外层容器 `className` 混有失效的 Tailwind utility 类：`flex min-h-screen flex-col items-center justify-center`（垂直居中意图）、`w-full max-w-md` / `w-full max-w-sm`（宽度限制）、`p-6`（padding）、`flex justify-center py-12`（spinner 居中）、`flex flex-col gap-4`（表单堆叠）。**项目无 `tailwindcss` 依赖**，这些类从未生效。

`fix-global-styles-not-loaded` 引入全局 CSS 后，`.cg-page`（水平居中限宽）和 `.cg-card`（卡片表面）首次生效，辅助页面获得水平居中 + 卡片样式，但**仍缺垂直居中**——`.cg-page` 不是 flex 容器，内容贴在页面顶部，与"全屏居中"的设计意图不符。

## What Changes（改动内容）

1. **`web/src/styles/index.css`**：新增 `.cg-screen` 类（全屏 flex 垂直水平居中容器，**无渐变背景**，区别于 `.cg-login-bg`），复用 body 的 `--cg-bg` 主题色
2. **`web/src/shared/auth/AuthGuard.tsx`**：
   - L16 spinner：`flex justify-center py-12` → inline style
   - L29 / L54 两处外层：`cg-page flex min-h-screen flex-col items-center justify-center` → `cg-screen`
   - L30 / L55 两处卡片：`w-full max-w-sm cg-card p-6` → `cg-login-card`
3. **`web/src/child/pages/ChildBindPage.tsx`**：
   - L74 / L93 两处外层：同上 → `cg-screen`
   - L75 / L94 两处卡片：`w-full max-w-md cg-card p-6` → `cg-login-card`
4. **`web/src/admin/pages/AdminInitPage.tsx`**：
   - L73 外层 → `cg-screen`
   - L74 卡片 → `cg-login-card`
   - L81 表单：`flex flex-col gap-4` → `cg-login-form`（已存在）
5. **`web/src/child/pages/ChildLoginPage.tsx:107`**：spinner `flex justify-center py-12` → inline style

## Capabilities（能力规格影响）

无新增 capability，无 spec 文本变更。本次仅清理失效 CSS 类并补全辅助页面布局，不改变功能行为。

## Impact（影响范围）

- **受益页面**：AuthGuard（loading/error/守卫）、AdminInitPage（首次部署）、ChildBindPage（设备绑定）、ChildLoginPage（无 childId 过渡）
- **无 regression**：`.cg-page` / `.cg-card` / `.cg-login-bg` / `.cg-login-card` / `.cg-login-form` 定义不变；新增 `.cg-screen` 不影响现有类
- **视觉一致性**：辅助页面采用与登录页相同的卡片骨架（`.cg-login-card`，max-width 28rem），但使用中性 `.cg-screen`（无渐变），符合守卫/初始化/绑定页的视觉定位

## Out of Scope（不在本次范围）

- **CI 加 ESLint `import/no-unused-modules` 规则**：拆分为第二个 tweak（工程配置维度）
- **卡片内部元素的失效 tailwind 类**：如 `ChildBindPage.tsx:101` 设备选项按钮的 `flex items-center gap-4 rounded-cg-lg bg-cg-surface p-4 min-h-touch` 等——这些是交互组件内部样式，需单独评估是否引入组件级 CSS 类，不在本次"页面级垂直居中"范围
- 不改已有 CSS 类定义、不改路由、不改 AuthGuard wrapper 逻辑、不改 API 调用
