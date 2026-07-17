# Design: fix-aux-page-centering

## 根因分析

三层根因（与前几次 hotfix 同源）：

1. **Tailwind 类失效**：项目无 `tailwindcss` 依赖（已多次核对 `web/package.json`），所有 utility 类（`flex`、`min-h-screen`、`items-center`、`justify-center`、`w-full`、`max-w-md`、`p-6`、`gap-4`、`py-12`）从未生效
2. **`.cg-page` 非 flex 容器**：`fix-global-styles-not-loaded` 引入全局 CSS 后，`.cg-page`（`width:100%; min-width:20rem; max-width:80rem; margin:0 auto; padding:1rem`）生效，只做水平居中限宽，**不做垂直居中**
3. **辅助页面缺独立的中性居中容器**：登录页有 `.cg-login-bg`（全屏 flex 居中 + 渐变背景），但辅助页面（AuthGuard/AdminInit/ChildBind）不需要渐变背景，直接复用 `.cg-login-bg` 会引入不合适的视觉

## 修改方案

### 文件 1：`web/src/styles/index.css`

在 `.cg-login-bg` 定义之后新增 `.cg-screen` 类：

```css
/* 辅助页面全屏居中容器（无渐变背景，中性） */
.cg-screen {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1.5rem;
}
```

与 `.cg-login-bg` 的差异：不含 `background` 声明，由 body 的 `--cg-bg` 主题色决定（中性）。

### 文件 2-4：AuthGuard / ChildBindPage / AdminInitPage

| 元素 | 当前 className | 改为 |
|---|---|---|
| 外层容器 | `cg-page flex min-h-screen flex-col items-center justify-center` | `cg-screen` |
| 卡片 | `w-full max-w-sm cg-card p-6` 或 `w-full max-w-md cg-card p-6` | `cg-login-card` |
| 表单（仅 AdminInitPage:81） | `flex flex-col gap-4` | `cg-login-form` |

**复用 `.cg-login-card` 的理由**：已定义 `width:100%; max-width:28rem; padding:2rem; border-radius; box-shadow`，与原 tailwind `max-w-md`（28rem）/ `max-w-sm`（24rem）+ `p-6`（1.5rem）的意图一致，差异极小且统一卡片视觉。

**复用 `.cg-login-form` 的理由**：已定义 `display:flex; flex-direction:column; gap:1rem`，等效于原 `flex flex-col gap-4`（gap-4 = 1rem）。

### 文件 5：AuthGuard.tsx:16 + ChildLoginPage.tsx:107（局部 spinner）

```tsx
// 当前
<Spin className="flex justify-center py-12" />
// 改为
<Spin style={{ display: 'flex', justifyContent: 'center', padding: '3rem 0' }} />
```

**用 inline style 的理由**：spinner 是局部居中（非全屏），不值得新增 CSS 类；inline style 最小改动且语义清晰。

## 边界

- 仅影响 4 个辅助页面的视觉布局，不改功能逻辑：
  - `AuthGuard` 的 loading/error 判断、`AdminInit` 表单提交、`ChildBind` 设备绑定流程、`ChildLogin` PIN 交互全部零改动
- 不改路由、不改 AuthGuard wrapper 逻辑、不改 API 调用
- 不改已有 CSS 类定义

## 替代方案（不采纳）

| 方案 | 不采纳原因 |
|---|---|
| 改 `.cg-page` 加 flex 垂直居中 | 会影响所有用 `.cg-page` 的地方，可能破坏未知页面布局，耦合面太广 |
| 直接复用 `.cg-login-bg` | 会给辅助页面加渐变背景，不符合守卫/初始化/绑定页的中性视觉定位 |
| 引入 Tailwind 依赖 | 超出本次范围，是架构级决策，需独立 change 评估 |
| 为 spinner 新增 `.cg-spinner-wrap` 类 | 仅 2 处使用，inline style 足够，避免类膨胀 |

## 测试策略

- `cd web && npm run lint`（tsc --noEmit）类型检查
- `cd web && npm run build`（umi build）+ grep `dist/umi.css` 确认 `.cg-screen{` 规则进入打包
- `cd web && npm run test`（vitest）确认 86/86 用例无 regression（2 collection 预存在失败可接受）
- 手动视觉验证受限于辅助页面触发条件（loading/error 需模拟、AdminInit 需空库、ChildBind 需无 childId），以 CSS 规则进入打包产物 + className 正确替换为运行时证据
