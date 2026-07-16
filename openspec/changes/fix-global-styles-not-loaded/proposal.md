# Proposal: fix-global-styles-not-loaded

## Why

用户报告三端登录页（`/parent/login`、`/child/login`、`/admin/login`）表单仍然占满浏览器宽度且顶部对齐，未实现预期的居中布局。这是 redesign-tri-login-pages 归档后的遗留问题。

**根因**：`web/src/styles/index.css`（项目全局自定义样式表）从未被应用入口 import，所有自定义 CSS 类完全不生效。

证据链：
1. `grep -r "styles/index.css" web/src/` 返回 **0 匹配**（无任何文件 import 它）
2. dist build 输出中 `.cg-login-bg{`、`.cg-page{`、`.cg-card{` 等 CSS 定义 **0 匹配**（CSS 未被打包）
3. 只有 `@/styles/themes`（TypeScript 解析为 `themes.ts`）被 layout 引入，但那是 TS 模块（导出 antd ThemeConfig），不是 `themes.css`；`index.css` 顶部的 `@import './themes.css'` 也因 `index.css` 自身未被加载而从未触发

前两次 hotfix（fix-parent-login-fullscreen、redesign-tri-login-pages）添加的 `.cg-login-screen`、`.cg-login-bg`、`.cg-login-card` 等类全部是"死代码"，从未生效。用户一直看到的是无样式的默认 block 元素（占满宽度 + 顶部对齐）。

## What Changes

1. **`web/src/app.tsx`**：顶部新增 `import '@/styles/index.css';`，让全局样式表在应用启动时加载

仅此 1 行改动。

## Capabilities

无新增 capability，无 spec 文本变更。本次仅修复样式加载遗漏，不改变任何功能行为。

## Impact

- **登录页**（`/parent/login`、`/child/login`、`/admin/login`）：获得预期全屏居中布局（`.cg-login-bg` flex 居中 + `.cg-login-card` max-width 28rem）
- **其他使用 `.cg-page` / `.cg-card` 的页面**（AuthGuard、AdminInitPage、ChildBindPage）：获得预期限宽 + 卡片样式（这些类就是为此设计的，net positive）
- 不影响 antd 组件自带样式（cssinjs 独立注入）
- themes.css 通过 index.css 的 `@import` 链式加载，CSS 变量生效

## Out of Scope

- 其他页面的失效 tailwind 类（如 `AuthGuard.tsx:29` 的 `flex min-h-screen flex-col`、`AdminInitPage.tsx:73` 等）：这些是预存在的 tailwind 依赖问题（项目无 tailwindcss），本次不修，留 follow-up
- PIN 键盘交互、登录逻辑、路由结构、Layout guard clause：均不改
