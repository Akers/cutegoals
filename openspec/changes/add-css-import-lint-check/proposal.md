# Proposal: 新增 CSS 加载检查脚本

## Why

历史上 `web/src/styles/index.css` 从未被任何模块 import，导致前两次 hotfix 写入的 CSS 类（`.cg-login-bg` / `.cg-login-card` / `.cg-page` 等）全部为死代码，登录页布局问题（占满宽度+顶部对齐）经过三轮 hotfix 才定位到根因。需要 CI 守护机制预防同类问题复现。

## What Changes

1. 新增 `web/scripts/check-css-loads.mjs`：扫描 `src/styles/*.css`，对每个 CSS 文件在 `src/**/*.{ts,tsx,css}` 中查找引用（JS 的 `import '...<filename>'` 或 CSS 的 `@import '...<filename>'`），未匹配则报错并 exit 1
2. `web/package.json` 新增 `"lint:css": "node scripts/check-css-loads.mjs"` script
3. 将 `lint` script 改为 `tsc --noEmit && npm run lint:css`，让 `npm run lint` 一并检查 CSS 加载

## Capabilities

无新增 / 无 spec 变更。纯工程守护脚本。

## 方案调整说明（oracle 建议的技术修正）

前期 oracle 建议"CI ESLint `import/no-unused-modules` 规则"（来自 `eslint-plugin-import`）。技术评估后发现该规则**不适用于 CSS**：

- 该规则检测 JS/TS 模块的"导出但未使用"，针对 `export` 语法
- CSS 无 `export` 概念，`import './x.css'` 是 side-effect import（不绑定到标识符），该规则无法识别为"使用"
- 即便安装 `eslint-plugin-import`，该规则对 CSS 文件无效

改为更直接的方案：自定义 Node 脚本，glob `src/styles/*.css`，在源码中 grep import 字符串。零外部依赖、~50 行、检测准确。

## Impact

- 新增 1 文件 `web/scripts/check-css-loads.mjs`
- `web/package.json` 修改 1 行（`lint`）+ 新增 1 行（`lint:css`）
- 不改任何运行时代码、组件、样式
- CI 调用 `npm run lint` 即可预防 CSS 未加载回归
- 对历史 hotfix 流程无影响（`tsc --noEmit` 仍执行，额外加 CSS 检查）

## Out of Scope

- 不安装 `eslint-plugin-import`（其 `import/no-unused-modules` 不解决 CSS 检测问题，引入依赖无收益）
- 不改 `.eslintrc.cjs`（现有 eslint 工具链独立，本次不触碰）
- 不做 CSS 内容 lint（如 stylelint），仅检测"是否被 import"
- 不检测动态 import（`import(variable)`），项目无此场景
