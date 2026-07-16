## 根因分析

三端登录页视觉不佳是**三层问题叠加**：

### Layer 1：Layout 层（Child / Admin 未修）

`ChildLayout.tsx` 第 23-69 行、`AdminLayout.tsx` 第 20-50 行直接渲染完整 `ProLayout layout="side"`，未根据 `location.pathname` 判断是否为登录页。导致 `/child/login`、`/admin/login` 的登录表单被嵌在 sider 右侧内容区，不是全屏。

**对比**：`ParentLayout.tsx` 第 25-32 行已在上个 hotfix 加 guard clause，登录页跳过 ProLayout 直接渲染 `<Outlet />`。本次将此模式复制到 Child/Admin。

### Layer 2：样式层（Child / Admin 类失效）

`ChildLoginPage` 第 112 行、`AdminLoginPage` 第 51 行外层 className `cg-page flex min-h-screen flex-col items-center justify-center` 中，tailwind 类（`flex min-h-screen flex-col items-center justify-center`、`w-full max-w-sm/max-w-md p-6`）在当前项目**完全不生效**——`web/package.json` 不含 `tailwindcss` / `postcss` 依赖（已核对）。实际只有 `.cg-page`（`max-width:80rem; margin:0 auto; padding`）部分起作用，结果登录页只在有限宽度的内容区里显示，无垂直居中、无全屏。

**对比**：`ParentLoginPage` 第 52 行已替换为 `cg-login-screen`，但卡片仍是基础 `.cg-card`。

### Layer 3：视觉层级（三端共性）

即使 Layer 1/2 修复，三端卡片视觉层级仍不足。当前 `.cg-card`（`index.css:78-83`）仅有：

```css
background-color: var(--cg-surface);
border: 1px solid var(--cg-border);
border-radius: var(--cg-radius-lg);
box-shadow: var(--cg-shadow-sm);
```

缺少 ant pro 模板的：
- 全屏渐变背景（当前是默认 body 白底）
- 顶部品牌 logo 区
- 标题/副标题字号字重 hierarchy
- input prefix icon（手机/锁）
- 按钮与卡片的视觉权重对比

## 参考布局：Ant Design Pro `/user/login`

经典 ant pro 登录页结构（https://preview.pro.ant.design/user/login）：

```
┌─────────────────────────────────────┐
│  全屏渐变背景（top-left → bottom-right）
│         ┌───────────────────┐       │
│         │   [品牌 Logo]     │       │
│         │   页面标题        │       │
│         │   副标题说明      │       │
│         │                   │       │
│         │   📱 [手机号]     │       │
│         │   🔒 [密码]       │       │
│         │                   │       │
│         │   [ 登 录 ]       │       │
│         │                   │       │
│         │  （可选 footer）  │       │
│         └───────────────────┘       │
└─────────────────────────────────────┘
```

**本次采纳**：全屏渐变背景、中央卡片、顶部 logo 区、标题/副标题 hierarchy、input prefix icon、primary 全宽按钮、卡片大阴影。

**本次不采纳**（用户已确认"仅视觉重构"）：Tab 切换（账号/手机）、记住我、忘记密码、第三方登录图标、footer 版权。

## 修改方案

### 文件 1：`web/src/styles/index.css`

在现有 `.cg-login-screen`（第 69-75 行）之后、`.cg-card`（第 78 行）之前，新增登录页视觉类族：

```css
/* 登录页全屏背景容器（三端通用骨架） */
.cg-login-bg {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1.5rem;
}

/* 三端差异化渐变背景 */
.cg-login-bg--parent {
  background: linear-gradient(135deg, #e0f2fe 0%, #fce7f3 100%);
}
.cg-login-bg--child {
  background: linear-gradient(135deg, #fef3c7 0%, #fed7aa 100%);
}
.cg-login-bg--admin {
  background: linear-gradient(135deg, #e0e7ff 0%, #f1f5f9 100%);
}

/* 登录卡片（比 .cg-card 更强的视觉权重） */
.cg-login-card {
  width: 100%;
  max-width: 28rem;
  background-color: var(--cg-surface);
  border-radius: var(--cg-radius-lg);
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.08), 0 4px 6px rgba(0, 0, 0, 0.04);
  padding: 2rem;
}

/* logo 区 */
.cg-login-logo {
  display: flex;
  justify-content: center;
  margin-bottom: 1rem;
}

/* 标题层级 */
.cg-login-title {
  text-align: center;
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--cg-text);
  margin-bottom: 0.25rem;
}
.cg-login-subtitle {
  text-align: center;
  font-size: 0.875rem;
  color: var(--cg-text-secondary);
  margin-bottom: 1.5rem;
}
```

具体色值由 designer 在实现时按端调性微调（Parent 温馨粉蓝、Child 童趣橙黄、Admin 专业蓝灰）。三端差异化仅通过 modifier 类实现，骨架共享。

### 文件 2/3：`ChildLayout.tsx`、`AdminLayout.tsx`

在 `useLocation()` 之后插入 guard clause（复用 `ParentLayout:25-32` 已验证模式）：

```tsx
// ChildLayout.tsx
if (location.pathname === '/child/login') {
  return (
    <ConfigProvider theme={childTheme} locale={zhCN}>
      <Outlet />
    </ConfigProvider>
  );
}

// AdminLayout.tsx
if (location.pathname === '/admin/login') {
  return (
    <ConfigProvider theme={adminTheme} locale={zhCN}>
      <Outlet />
    </ConfigProvider>
  );
}
```

保留原有 ProLayout 渲染逻辑不动。

### 文件 4/5/6：三端 LoginPage

- **外层**：`<div className="cg-login-bg cg-login-bg--<端>">`
- **卡片**：`<div className="cg-login-card">`
- **logo 区**：`<div className="cg-login-logo">`（可放 emoji、SVG、或文字 logo——由 designer 选）
- **标题**：用 `.cg-login-title`、副标题 `.cg-login-subtitle`
- **input**：加 `prefix={<MobileOutlined />}` / `prefix={<LockOutlined />}`（来自 `@ant-design/icons`，项目已依赖）
- **按钮**：保持 `type="primary"` + `className="w-full"`（或等效全宽样式）
- **ChildLoginPage 特殊**：PIN 数字键盘按钮可加 `.cg-pin-key` 类（大尺寸、圆角、悬浮），但 4-6 位自动提交、5 次失败锁定、离线检测等交互逻辑**完全不动**

## 边界

- **不改**：路由结构（`routes.ts`）、AuthGuard wrapper、登录 API、表单提交与校验逻辑、PIN 键盘交互、5 次失败锁定策略、`childId/deviceId` URL 参数依赖
- **不改**：`ParentLayout` 已有的 guard clause（保持不变）
- **不改**：已归档的 `2026-07-16-fix-parent-login-fullscreen` 任何产物
- **不改**：三端主题色变量（`themes.css`）——差异化仅通过登录页背景渐变实现

## 测试

### 手动验证（开发服务器）

1. `cd web && npm run start` 启动 dev server（:8000）
2. 访问 `/parent/login`：温馨粉蓝渐变全屏背景，中央卡片，logo + 标题 + 手机号/密码（带 prefix icon）+ 登录按钮，**无侧边栏**
3. 访问 `/child/login?childId=...&deviceId=...`：童趣橙黄渐变全屏背景，PIN 数字键盘卡片居中，**无侧边栏**，PIN 输入 4-6 位自动提交仍工作
4. 访问 `/admin/login`：专业蓝灰渐变全屏背景，中央卡片，logo + 标题 + 手机号/密码（带 prefix icon），**无侧边栏**
5. 三端登录功能回归：错误密码提示、5 次失败锁定 15 分钟（Child）、登录成功跳转

### 自动化验证

- `cd web && npm run lint`（tsc --noEmit）通过
- `cd web && npm run test`（vitest run）：86/86 现有用例不回归（2 个预存在的 `react-router-dom` 解析失败与本 hotfix 无关）
- `cd web && npm run build`（umi build）通过，dist 输出三端 LoginPage/Layout 产物
