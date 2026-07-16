# Design: fix-global-styles-not-loaded

## 根因分析

### 现象
用户访问 `/parent/login` 看到表单占满浏览器宽度 + 顶部对齐，而非 redesign-tri-login-pages 期望的全屏居中卡片。

### 根因
`web/src/styles/index.css` 定义了所有自定义 CSS 类（`.cg-page`、`.cg-card`、`.cg-login-bg`、`.cg-login-card`、`.cg-badge` 等），但**从未被任何模块 import**。

CSS 类仅在三个 LoginPage 的 JSX 中以 `className` 字符串形式出现，但 CSS 规则本身从未进入打包产物，所以浏览器收到的 DOM 元素没有匹配任何样式规则，以浏览器默认 `display: block` 渲染：
- `.cg-login-bg` div：block，width auto（占满父），height auto（内容高度）→ 顶部对齐
- `.cg-login-card` div：block，width auto（占满父），无 max-width 限制 → 占满宽度

### 验证证据
1. `grep -r "styles/index\.css" web/src/` → 0 匹配（无 import）
2. `grep -l "cg-login-bg{" web/dist/*.js` → 0 匹配（CSS 规则未打包）
3. `grep -l "cg-page{" web/dist/*.js` → 0 匹配
4. layout 文件 import 的是 `@/styles/themes`（无扩展名），TypeScript 解析为 `themes.ts`（导出 antd ThemeConfig），不是 `themes.css`

### 为什么前两次 hotfix 没发现
- `npm run lint`（tsc --noEmit）：不检查 CSS 引入
- `npm run build`（umi build）：不 fail 如果 CSS 未引入（只是静默不打包）
- `npm run test`（vitest + jsdom）：jsdom 环境不加载真实 CSS
- `verify_mode=light` 不强制浏览器视觉验证
- 派发的 code review（oracle）聚焦正确性/安全/边界，未覆盖"CSS 是否真的加载"这个运行时事实

## 修复方案

在 `web/src/app.tsx` 顶部添加 side-effect import：

```tsx
import '@/styles/index.css';
```

### 为什么选 app.tsx

- `app.tsx` 是 umi 4 的运行时配置入口（导出 `rootContainer`），必然被 umi 加载
- 在文件顶部 import 确保 CSS 在应用启动时加载，先于任何组件渲染
- side-effect import 是 Vite/webpack 标准做法，无副作用风险

### 替代方案（不采纳）

1. **创建 `src/global.css` 并 `@import './styles/index.css'`**：umi 4 约定自动加载 `src/global.{css,less,scss}`，但需新建中间文件，不如直接在 app.tsx import 清晰直接
2. **在每个 layout 单独 import**：重复且易遗漏，违反 DRY
3. **把类改为 antd cssinjs**：超出 hotfix 范围，属架构重构

## 影响范围评估

grep 显示 `.cg-*` 类的使用文件：

| 文件 | 使用的类 | 修复后效果 |
|------|----------|------------|
| ParentLoginPage.tsx | .cg-login-bg/.cg-login-card/.cg-login-* | 全屏居中卡片（本次目标） |
| ChildLoginPage.tsx | 同上 | 全屏居中卡片 |
| AdminLoginPage.tsx | 同上 | 全屏居中卡片 |
| AuthGuard.tsx | .cg-page/.cg-card | 限宽 + 卡片样式（loading/error 守卫页） |
| AdminInitPage.tsx | .cg-page/.cg-card | 限宽 + 卡片样式（初始化向导） |
| ChildBindPage.tsx | .cg-page/.cg-card | 限宽 + 卡片样式（设备绑定页） |

修复后所有这些页面都会获得 `.cg-page`（max-width 80rem, margin 0 auto, padding）/ `.cg-card`（surface bg + border + radius + shadow）样式。这是**期望行为**（这些类就是为此设计的），net positive，不引入 regression。

**注意**：AuthGuard/AdminInitPage/ChildBindPage 的 className 还混了失效的 tailwind 类（`flex min-h-screen flex-col items-center justify-center`、`w-full max-w-md` 等），这些仍不生效（预存在 tailwind 问题，项目无 tailwindcss 依赖），不在本次范围。修复后这些页面会获得 `.cg-page`/`.cg-card` 样式改善，但垂直居中仍缺失（留 follow-up）。

## 边界

- 不改 `index.css` 内容（CSS 定义本身正确）
- 不改 LoginPage 组件（className 应用正确）
- 不改 Layout guard clause（已正确）
- 不改 themes.css（通过 index.css @import 链式加载）
- 不处理其他页面的 tailwind 失效类（留 follow-up）

## 测试策略

1. `npm run lint`（tsc）：类型检查
2. `npm run build`（umi build）：编译 + 确认 CSS 进入打包产物（grep dist 验证 `.cg-login-bg{` 存在）
3. `npm run test`（vitest）：单元测试不回归
4. **浏览器实际验证**（关键，弥补之前缺失的运行时验证）：
   - dev server 已在 :8000 运行
   - 访问 `/parent/login`、`/child/login`、`/admin/login`，确认 `.cg-login-bg` flex 居中生效，卡片 max-width 28rem
   - 检查 AuthGuard/AdminInitPage 等页面无 visual regression
