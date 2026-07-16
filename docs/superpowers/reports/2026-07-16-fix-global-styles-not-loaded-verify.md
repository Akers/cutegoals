# Verify Report: fix-global-styles-not-loaded

**Date**: 2026-07-16
**Change**: `fix-global-styles-not-loaded` (hotfix)
**verify_mode**: light（手动覆盖；scale 自动判 full 因 tasks=8>3，但实际改动 1 文件 +1 行 import）
**review_mode**: standard（手动设置；本次根因是前两次 code review 漏检 CSS 加载，加 review 弥补）

## 改动规模

- 文件：`web/src/app.tsx`（1 文件）
- 行数：+1 行（第 1 行 `import '@/styles/index.css';`）
- delta spec：无
- 新增 capability：无

## 根因

`web/src/styles/index.css` 从未被任何 JS/TS 模块 import，导致所有自定义 CSS 类（`.cg-page`/`.cg-card`/`.cg-login-bg`/`.cg-login-card` 等）完全不生效。前两次 hotfix（fix-parent-login-fullscreen、redesign-tri-login-pages）加的 CSS 类全是死代码——div 以浏览器默认 block 渲染，占满宽度+顶部对齐。

## 6 项轻量验证

| # | 检查项 | 结果 | 证据 |
|---|--------|------|------|
| 1 | tasks.md 全部 [x] | PASS | 8/8 项勾选 |
| 2 | 改动文件与 tasks 一致 | PASS | `git diff --stat` 显示 app.tsx 1 文件 +1 行 |
| 3 | 编译通过 | PASS | `npm run lint`（tsc --noEmit）exit 0；`npm run build`（umi build）✓ built in 5.68s |
| 4 | 测试通过 | PASS | `npm run test`（vitest）86/86 PASS；2 collection 失败 react-router-dom 解析（预存在环境问题，与本次无关，已在干净 main 复现） |
| 5 | 无安全问题 | PASS | 纯 CSS side-effect import，无密钥/无 unsafe/无新攻击面 |
| 6 | 代码审查（review_mode=standard） | PASS | oracle（ses_095001f80ffePj7c6VH4yGMVuJ）Ready to merge: Yes，0 Critical / 0 Important |

## 运行时证据（Iron Law：CSS 真的加载）

本次修复的核心是让 CSS 进入打包产物。三重交叉验证：

1. **build 产物**：`dist/umi.css`（7.1K）出现（之前 dist 无 .css 文件），grep 确认含完整规则：
   - `.cg-login-bg{min-height:100vh;display:flex;align-items:center;justify-content:center;padding:1.5rem}`
   - `.cg-login-card{width:100%;max-width:28rem;background-color:var(--cg-surface);...}`
   - `.cg-page{width:100%;min-width:20rem;max-width:80rem;margin:0 auto;padding:1rem}` + media query
2. **dev server CSS 模块**：`curl -s http://localhost:8000/src/styles/index.css` 返回完整 CSS 字符串 + Vite HMR 包装 `__vite__updateStyle(__vite__id, __vite__css)` → dev server 加载 CSS 运行时确认
3. **import 链**：`app.tsx:1` → `@/styles/index.css`（@ 别名指向 src/，config.ts:9 + tsconfig.json:20 双重确认）→ `@import './themes.css'`（CSS 级联，Vite 构建时内联）

## Oracle Code Review 结论

**Ready to merge: Yes**

### Strengths
- 极简修复：单行 side-effect import，零逻辑改动
- 根因精准：直接消除"CSS 未 import"根因，非继续打补丁
- 验证充分：tsc/umi build/vitest/dev server curl 四道防线全绿
- 无死代码残留：前两次 hotfix 的 CSS 类本次全部激活

### Minor（不阻塞合并）

1. **AuthGuard/AdminInitPage/ChildBindPage 使用失效 Tailwind 类**：这三个页面的 className 混有 `flex min-h-screen flex-col items-center justify-center` 等 Tailwind utility（项目无 tailwindcss 依赖，一直不生效）。`.cg-page`/`.cg-card` 激活后获得水平居中+限宽+卡片样式，但**不会垂直居中**（`.cg-page` 不含 flex centering）。功能上不构成 regression（之前几乎无样式），但布局与设计意图不符。**留 follow-up**。

2. **缺少 CSS 加载的静态检查**：建议 CI 加规则（如 ESLint import/no-unused-modules 或自定义检查）检测 `src/styles/` 下 CSS 文件是否至少被一个模块 import，避免同类问题复现。**本次不阻塞**。

### 教训反思（oracle 特别指出）

前两次 code review 漏检的根因：**只审查"改了哪些文件/diff 是否正确"，没有验证"修改是否实际生效"**。

CSS review 的必备步骤（本次采纳）：
1. 确认 CSS 文件被 import（grep `import.*\.css`）
2. 确认产物包含该 CSS（检查构建输出）
3. 确认 className 与 CSS 选择器匹配（grep 交叉验证）

## 浏览器视觉验证限制

agent-browser CLI 未安装（安装需下载 Chromium，较重），未做 computed style 检查。以 dev server CSS 模块 curl + dist/umi.css grep 作为运行时证据替代（JS 模块系统保证：app.tsx import 触发 Vite CSS 模块加载，`__vite__updateStyle` 注入 `<style>` 标签，`.cg-login-bg` flex 居中必然生效）。dev server :8000 在线，用户可自行访问 /parent/login 确认视觉。

## 分支处理

- 环境：normal repo（GIT_DIR==GIT_COMMON），main 分支
- 用户选择：「Commit 到 main 并推送」
- commit：`fix: 引入全局样式表修复登录页布局`（含根因解释）
- push：`origin/main`

## 影响范围（net positive）

- ParentLoginPage / ChildLoginPage / AdminLoginPage：`.cg-login-bg` 全屏 flex 居中 + `.cg-login-card` 限宽卡片（本次目标）
- AuthGuard.tsx（loading/error 守卫）：`.cg-page` 限宽 + `.cg-card` 卡片样式首次生效
- AdminInitPage.tsx（初始化向导）：同上
- ChildBindPage.tsx（设备绑定）：同上

后三者从"几乎无样式"变为"水平居中限宽卡片"，net positive，不构成 regression。
