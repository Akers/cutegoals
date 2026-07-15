# Comet Design Handoff

- Change: migrate-to-antd-pro-infra
- Phase: design
- Mode: compact
- Context hash: ef88783f44337209d7888b1d002f765b3a481d525393318d0afbc73eab87d203

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/migrate-to-antd-pro-infra/proposal.md

- Source: openspec/changes/migrate-to-antd-pro-infra/proposal.md
- Lines: 1-31
- SHA256: 4f7cdb26eb896e9508ccb837cab3289fe70368894adad14c7655bb576c32328e

```md
## Why

CuteGoals 前端当前完全依赖自建 UI 组件（Button/Modal/Form/Pagination/Toast 等 10+ 组件），无任何第三方 UI 库。自建组件在边界场景（无障碍、键盘导航、响应式断点、表单校验反馈、加载态提示一致性）上存在稳定性隐患，且维护成本随页面增长持续上升。迁移至 Ant Design Pro 可借助其久经考验的企业级组件体系，从根本上减少渲染 bug 和交互不一致问题。

## What Changes

- **构建工具替换**：Vite 5.4 → UmiJS 4（Ant Design Pro 标准构建系统）
- **布局系统替换**：自建 `Layout`/`PageHeader` → ProLayout（带侧边导航、面包屑、响应式折叠）
- **主题系统迁移**：CSS 自定义属性 (`--cg-*`) → ConfigProvider `theme.token`，保留 admin/parent/child 三套角色色系
- **共享 UI 组件替换**：`shared/components/` 中所有自建组件映射到 antd/pro-components 等价组件
- **消息通知替换**：自建 `ToastProvider`/`useToast` → antd `App.useApp().message` / `notification`
- **路由系统迁移**：React Router DOM v7 → UmiJS 约定路由（`config/routes.ts`），保持 `/admin`/`/parent`/`/child` 三入口结构
- **保留不变**：`shared/api/client.ts`、`shared/auth/AuthContext`、`shared/hooks/`、角色上下文、幂等 hooks 等业务基础设施

## Capabilities

### New Capabilities
- `antd-pro-framework`: UmiJS 4 构建系统、ProLayout 布局、ConfigProvider 主题令牌（三角色色系）、antd 组件库集成
- `shared-component-migration`: 自建组件到 antd/pro-components 的映射体系，包含 Button/Modal/Form/Pagination/Toast/Input/Select/TextArea/EmptyState/ErrorState/LoadingState/OfflineState/ErrorBoundary/CardSection/StatusBadge

### Modified Capabilities
- `web-app`: **BREAKING** — 前端构建工具从 Vite 切换为 UmiJS，路由从 React Router 迁移到 UmiJS 约定路由。业务需求不变，但部署构建流程和开发启动命令变更

## Impact

- **代码**：`web/` 目录全面重构（构建配置、共享组件层、入口文件、所有角色 App 入口）
- **构建配置**：`web/vite.config.ts` → `web/.umirc.ts` 或 `web/config/config.ts`，`web/tailwind.config.js` 按需保留或迁移至 antd 主题
- **依赖**：`package.json` 新增 `@ant-design/pro-components`、`@ant-design/icons`、`antd`、`umi`；移除 `react-router-dom`（如不再需要）
- **开发命令**：`npm run dev` → `npm run start`（UmiJS 约定）
- **部署**：构建产物路径和结构可能变更，需同步 Dockerfile 和 nginx 配置
- **测试**：Vite 特有测试配置需适配 UmiJS 环境，组件测试用例需按 antd 组件行为更新

```

## openspec/changes/migrate-to-antd-pro-infra/design.md

- Source: openspec/changes/migrate-to-antd-pro-infra/design.md
- Lines: 1-130
- SHA256: 81a34d094729d25c8d1c8c27d8f6a8d287869b46e99c4dac11474bf82aec247f

[TRUNCATED]

```md
## Context

CuteGoals 前端当前为 React 18 + Vite + Tailwind CSS + 自建组件体系，按 admin/parent/child 三角色分路由入口，通过 `React.lazy` 按需加载子 App。所有 UI 组件（Button/Modal/Form/Pagination/Toast 等）均为自行实现，无第三方 UI 库。

本次变更为「Ant Design Pro 全量迁移」的第一阶段——基础设施层迁移，不涉及具体页面。后续三个阶段（admin/parent/child 页面重建）依赖本阶段产物。

## Goals / Non-Goals

**Goals:**
- 搭建 UmiJS 4 构建环境，替换 Vite 构建流程
- 引入 ProLayout 替换自建 `Layout`/`PageHeader` 组件，支持三角色导航菜单
- 通过 ConfigProvider `theme.token` 实现三套角色色系，保留视觉差异化
- 建立自建组件 → antd/pro-components 的完整映射体系
- 保留 `shared/api/client.ts`、`shared/auth/`、`shared/hooks/` 等全部业务基础设施

**Non-Goals:**
- 不迁移任何具体业务页面（admin/parent/child pages）
- 不修改后端 API 或数据结构
- 不新增业务功能
- 不修改 Playwright E2E 测试

## Decisions

### D1: 采用 UmiJS 4 约定路由，三入口通过路由表配置

**决策**：使用 UmiJS 约定路由模式（`config/routes.ts`），为 `/admin`、`/parent`、`/child` 各自配置独立路由段，每段通过 `wrappers` 注入对应角色 AuthGuard。

**备选方案**：
- *保留 React Router DOM*：与 Ant Design Pro 体系割裂，ProLayout 的菜单/面包屑需手动同步路由状态，复杂度高
- *UmiJS 配置路由*：与 ProLayout 深度集成，菜单自动从路由配置生成，面包屑自动推导

**实现要点**：
```ts
// config/routes.ts 结构示意
export default [
  {
    path: '/admin',
    component: '@/layouts/AdminLayout',
    routes: [
      { path: '/admin/init', component: '@/pages/admin/init' },
      { path: '/admin/login', component: '@/pages/admin/login' },
      { path: '/admin', component: '@/pages/admin/dashboard', wrappers: ['@/wrappers/AuthGuard'] },
      // ... 其余 admin 页面
    ]
  },
  // parent/child 同理
]
```

### D2: ProLayout 按角色配置实例，每个角色端独立菜单

**决策**：为 admin/parent/child 各自创建独立 Layout 组件（`AdminLayout`/`ParentLayout`/`ChildLayout`），内部使用 ProLayout 并传入角色对应的 `route`（菜单配置）和 `avatarProps`。

**备选方案**：
- *单一 Layout + 运行时切换菜单*：代码集中但菜单切换逻辑复杂，权限边界模糊
- *独立 Layout per 角色*：职责清晰，各角色端独立演进，菜单配置无交叉污染

### D3: ConfigProvider theme.token 映射三套角色色系

**决策**：将当前 `themes.css` 中定义的 CSS 自定义属性（`--cg-*`）映射为 ConfigProvider `theme.token` 值，每个角色 Layout 包裹独立的 `ConfigProvider`。保留 `theme.ts` 工具函数，重构为 token 生成器。

**映射示例**：
```
--cg-color-primary   → token.colorPrimary
--cg-color-bg         → token.colorBgContainer
--cg-radius           → token.borderRadius
--cg-touch-target     → token.controlHeight (调整为 44px)
```

**备选方案**：
- *保留 Tailwind CSS*：与 antd CSS-in-JS 体系冲突，样式优先级难以控制；antd 组件不受 Tailwind utility 影响，两套体系并存导致调试困难
- *纯 ConfigProvider*：统一使用 antd 主题系统，移除 Tailwind 依赖，样式一致性有保证

### D4: 自建组件 → antd 等价映射，不保留自建组件

**决策**：将 `shared/components/` 下所有自建组件映射为 antd/pro-components 的薄封装（或直接使用），移除原实现。映射关系如下：

| 自建组件 | antd/pro-components 等价 |
|---------|------------------------|
| `Button` | `antd Button` |

```

Full source: openspec/changes/migrate-to-antd-pro-infra/design.md

## openspec/changes/migrate-to-antd-pro-infra/tasks.md

- Source: openspec/changes/migrate-to-antd-pro-infra/tasks.md
- Lines: 1-71
- SHA256: f05836f3115457adc620fc53a5d343b4b8879b1645ea329e4ed71aa646c45aed

```md
## 1. 环境初始化

- [ ] 1.1 安装 Ant Design Pro 依赖：将 `umi`、`@ant-design/pro-components`、`@ant-design/icons`、`antd` 添加到 `web/package.json`，执行 `npm install`
- [ ] 1.2 创建 UmiJS 配置文件 `web/config/config.ts`，配置路径别名 `@/` → `src/`、history 类型为 browser、开启 hash 路由
- [ ] 1.3 配置 UmiJS TypeScript：更新 `web/tsconfig.json`，添加 `@/` 路径映射、JSX 配置、确保 UmiJS 类型定义可用
- [ ] 1.4 验证开发服务器：执行 `npm run start`，确认 UmiJS 开发服务器正常启动（页面可为空白占位）

## 2. ProLayout 布局系统

- [ ] 2.1 创建 AdminLayout 组件：使用 ProLayout 实现 admin 端布局，配置侧边菜单项（实例概览/系统配置/账号管理/审计日志/健康面板），注入 admin 角色权限标识
- [ ] 2.2 创建 ParentLayout 组件：使用 ProLayout 实现 parent 端布局，配置侧边菜单项（家庭概览/家庭管理/孩子档案/任务模板/任务分配/任务审核/积分/奖品/盲盒/兑换履约）
- [ ] 2.3 创建 ChildLayout 组件：使用 ProLayout 实现 child 端布局，配置侧边菜单项（今日任务/全部任务/积分商城/惊喜盲盒/兑换历史）
- [ ] 2.4 实现 Layout 通用功能：头像下拉菜单（含登出）、面包屑自动生成、响应式折叠
- [ ] 2.5 验证布局：三个角色 Layout 在桌面端和移动端均可正常渲染，菜单导航可点击

## 3. ConfigProvider 角色主题

- [ ] 3.1 创建 admin 主题 token 配置：基于原 `themes.css` admin 色系，映射为 ConfigProvider `theme.token`（主色、背景色、边框色、圆角、字号）
- [ ] 3.2 创建 parent 主题 token 配置：基于 parent 色系映射 token 值
- [ ] 3.3 创建 child 主题 token 配置：基于 child 色系映射 token 值
- [ ] 3.4 主题注入：在三个角色 Layout 中包裹对应的 `ConfigProvider`，确保组件渲染使用正确主题
- [ ] 3.5 验证主题：打开 admin/parent/child 页面，确认颜色、圆角、字号等视觉风格符合原设计

## 4. 路由系统迁移

- [ ] 4.1 创建 UmiJS 路由配置 `config/routes.ts`：定义 `/admin`、`/parent`、`/child` 三组路由段，配置 `wrappers` 注入 AuthGuard
- [ ] 4.2 创建 AuthGuard wrapper 组件：读取 AuthContext 认证状态，未认证重定向登录页，角色不匹配显示 403
- [ ] 4.3 重构 `main.tsx` 入口：移除 React Router DOM 的 `BrowserRouter`，适配 UmiJS 运行时入口
- [ ] 4.4 配置子 App 懒加载：使用 UmiJS 动态导入（`React.lazy` 或 `dynamic import`）按角色拆分 bundle
- [ ] 4.5 验证路由：直接访问 `/admin`/`/parent`/`/child` 及各子路由，确认页面正确加载、深层链接刷新不 404

## 5. 认证与权限集成

- [ ] 5.1 保留 AuthContext 不变：`shared/auth/AuthContext.tsx` 所有代码保持原样，认证状态、登录/登出逻辑不修改
- [ ] 5.2 创建 UmiJS access 文件 `src/access.ts`：定义 `canAdmin`/`canParent`/`canChild` 权限函数，读取 AuthContext 角色
- [ ] 5.3 路由接入 access：在 `routes.ts` 中为各路由段设置 `access` 字段
- [ ] 5.4 验证权限：孩子角色访问 `/admin` 路由显示 403，未登录访问受保护路由跳转登录页

## 6. 共享组件迁移

- [ ] 6.1 Button 迁移：将 `shared/components/Button.tsx` 映射为 antd Button，支持 primary/secondary/danger/ghost 变体、small/middle/large 尺寸、loading 和 icon
- [ ] 6.2 Modal 迁移：将 `shared/components/Modal.tsx` 映射为 antd Modal，支持确认弹窗、ESC 关闭、遮罩关闭
- [ ] 6.3 Toast 迁移：将 `shared/components/Toast.tsx` 的 `ToastProvider`/`useToast` 替换为 antd `App.useApp().message`/`notification`，在根组件注入 `App` 包裹器
- [ ] 6.4 Form 组件迁移：将 `Input`/`TextArea`/`Select`/`Label`/`FormField` 映射为 antd `Input`/`Input.TextArea`/`Select`/`Form.Item`，保留 `useFormField` hook
- [ ] 6.5 Pagination 迁移：将 `shared/components/Pagination.tsx` 映射为 antd Pagination
- [ ] 6.6 状态组件迁移：将 `EmptyState`/`ErrorState`/`LoadingState`/`OfflineState` 映射为 antd `Empty`/`Result`/`Spin`
- [ ] 6.7 其余组件迁移：将 `ErrorBoundary`/`CardSection`/`StatusBadge`/`PageHeader` 映射为 antd 等价组件
- [ ] 6.8 更新 `shared/components/index.tsx` 导出：所有组件导出指向新的 antd 映射

## 7. 基础设施保留与共存

- [ ] 7.1 确认 `shared/api/client.ts` 无改动：ApiClient 类、CSRF 注入、重试逻辑、错误映射全部保持原样
- [ ] 7.2 确认 `shared/auth/AuthContext.tsx` 无改动：认证 Provider、login/logout 函数不变
- [ ] 7.3 确认 `shared/role.ts` 无改动：RoleProvider、useRole hook 返回值结构不变
- [ ] 7.4 确认 `shared/hooks/` 全部保留：`useApi`/`useMutation`/`useFormField`/`useIdempotencyKey`/`useOnline`/`useReducedMotion` 等保持不变

## 8. 清理与移除

- [ ] 8.1 移除 Vite 配置：删除 `web/vite.config.ts`、移除 `web/package.json` 中的 Vite 插件依赖
- [ ] 8.2 移除 Tailwind CSS：删除 `web/tailwind.config.js`、`web/postcss.config.js`，清理 `src/index.css` 中的 `@tailwind` 指令
- [ ] 8.3 移除 React Router DOM 依赖：如果路由完全迁移到 UmiJS 约定路由，从 `package.json` 移除 `react-router-dom`
- [ ] 8.4 移除自建组件原始实现：删除 `shared/components/` 下的自建组件文件（Button.tsx/Modal.tsx/Toast.tsx 等）
- [ ] 8.5 更新 `package.json` scripts：`dev` → `start`、`build` → `build`（UmiJS 约定）

## 9. 验证与测试

- [ ] 9.1 TypeScript 编译检查：执行 `npx tsc --noEmit`，确保无类型错误
- [ ] 9.2 单元测试适配：更新 `web/src/__tests__/` 中受影响的测试用例，执行 `npm test` 确保通过
- [ ] 9.3 构建验证：执行 `npm run build`，确认生产构建成功无报错
- [ ] 9.4 开发体验验证：确认热更新、错误提示、Source Map 等开发功能正常
- [ ] 9.5 组件覆盖检查：确认 `shared/components/` 下所有组件均已映射，无遗漏

```

## openspec/changes/migrate-to-antd-pro-infra/specs/antd-pro-framework/spec.md

- Source: openspec/changes/migrate-to-antd-pro-infra/specs/antd-pro-framework/spec.md
- Lines: 1-85
- SHA256: 0d6534069d21cc3bff04470484684ffd520b1394b8863c91bb55f721559b5b69

[TRUNCATED]

```md
# antd-pro-framework Specification

## Purpose
定义 CuteGoals 前端从 Vite + 自建组件体系迁移至 Ant Design Pro 框架的基础设施层需求。

## ADDED Requirements

### Requirement: UmiJS 4 构建系统
前端 SHALL 使用 UmiJS 4 作为开发构建工具，替换原有的 Vite 5.4 构建流程。开发服务器 MUST 提供热更新支持，生产构建 MUST 输出静态资源到标准 UmiJS 产物目录。构建配置 MUST 支持路径别名 `@/` 指向 `src/` 目录。

#### Scenario: 开发服务器启动
- **WHEN** 执行 `npm run start`（或等价的开发启动命令）
- **THEN** UmiJS dev server MUST 在默认端口启动并提供热更新，页面修改 MUST 在浏览器自动反映

#### Scenario: 生产构建
- **WHEN** 执行 `npm run build`
- **THEN** UmiJS MUST 输出优化后的 JS/CSS/HTML 产物，产物目录 MUST 可通过环境变量或配置定制，且构建过程 MUST 不包含未使用代码（tree shaking）

#### Scenario: 路径别名解析
- **WHEN** TypeScript/JavaScript 代码中使用 `@/shared/api/client` 等路径别名导入
- **THEN** UmiJS MUST 正确解析并打包对应模块

### Requirement: ProLayout 角色感知布局
每个角色入口（admin/parent/child）SHALL 使用 ProLayout 提供导航布局。角色布局 MUST 包含：角色专属侧边导航菜单、面包屑、用户头像下拉菜单（含登出入口）。菜单项 MUST 基于 UmiJS 路由配置自动生成，选中状态 MUST 与当前路由保持同步。

#### Scenario: Admin 端导航菜单
- **WHEN** 管理员登录后进入 `/admin` 区域
- **THEN** ProLayout MUST 展示 admin 专属菜单项（实例概览、系统配置、账号管理、审计日志、健康面板），当前页面菜单项 MUST 高亮

#### Scenario: Parent 端导航菜单
- **WHEN** 家长登录后进入 `/parent` 区域
- **THEN** ProLayout MUST 展示 parent 专属菜单项，且不在导航中暴露 admin 或 child 功能入口

#### Scenario: Child 端导航菜单
- **WHEN** 孩子登录后进入 `/child` 区域
- **THEN** ProLayout MUST 展示 child 专属菜单项，且不在导航中暴露 admin 或 parent 功能入口

#### Scenario: 响应式菜单折叠
- **WHEN** 浏览器视口宽度缩小至移动端尺寸
- **THEN** ProLayout MUST 自动将侧边菜单折叠为汉堡菜单，且折叠状态下用户 MUST 仍可访问所有菜单项

#### Scenario: 登出操作
- **WHEN** 用户点击头像下拉菜单中的"退出登录"
- **THEN** 系统 MUST 清除认证状态并导向对应角色的登录页

### Requirement: ConfigProvider 三角色主题
前端 SHALL 通过 antd `ConfigProvider` 的 `theme.token` 机制提供 admin/parent/child 三套差异化视觉主题。每个角色 Layout MUST 包裹独立的 `ConfigProvider`，注入该角色对应的色彩、圆角、字号等设计令牌。主题切换 MUST 在用户进入不同角色入口时自动生效，无需手动干预。

#### Scenario: Admin 主题色彩
- **WHEN** 用户进入 `/admin` 任意页面
- **THEN** antd 所有组件 SHALL 使用 admin 主题配置（如主色、背景色、边框色），视觉风格 MUST 呈现冷静克制的专业感

#### Scenario: Parent 主题色彩
- **WHEN** 用户进入 `/parent` 任意页面
- **THEN** antd 所有组件 SHALL 使用 parent 主题配置，视觉风格 MUST 呈现温暖稳重的家庭感

#### Scenario: Child 主题色彩
- **WHEN** 用户进入 `/child` 任意页面
- **THEN** antd 所有组件 SHALL 使用 child 主题配置，视觉风格 MUST 呈现活泼友好的成长感

#### Scenario: 主题隔离
- **WHEN** 管理员同时在不同标签页打开 admin 和 parent 页面
- **THEN** 各标签页 MUST 独立使用其对应角色主题，不得互相污染

### Requirement: 自建 API 客户端保留
前端 SHALL 保留 `shared/api/client.ts` 中的自建 `ApiClient` 类作为唯一 HTTP 客户端，不对 umi-request 产生依赖。ApiClient 的 CSRF Token 自动注入、请求重试、错误码映射和幂等 key 生成 MUST 全部保持不变。

#### Scenario: CSRF Token 自动注入
- **WHEN** ApiClient 发送写请求（POST/PUT/DELETE）
- **THEN** 请求 MUST 自动携带 CSRF Token，不存在时不发送请求并报错

#### Scenario: 请求重试
- **WHEN** GET 请求遇到网络错误
- **THEN** ApiClient MUST 按配置的重试次数和间隔自动重试

#### Scenario: 幂等 key
- **WHEN** 调用 `useIdempotencyKey` hook 获取幂等 key 并附加到兑换/取消/兑现请求
- **THEN** ApiClient MUST 将幂等 key 注入请求头，行为与迁移前完全一致

### Requirement: 角色上下文保留

```

Full source: openspec/changes/migrate-to-antd-pro-infra/specs/antd-pro-framework/spec.md

## openspec/changes/migrate-to-antd-pro-infra/specs/shared-component-migration/spec.md

- Source: openspec/changes/migrate-to-antd-pro-infra/specs/shared-component-migration/spec.md
- Lines: 1-95
- SHA256: c774ba1563dd3310276bc9961f79b59d9ac7f9d33d3db57e4ab58c6dbbdfb14b

[TRUNCATED]

```md
# shared-component-migration Specification

## Purpose
定义 CuteGoals 自建 UI 组件到 antd/pro-components 的映射关系和兼容性需求。

## ADDED Requirements

### Requirement: Button 组件迁移
自建 `Button` 组件 SHALL 替换为 antd `Button`。所有变体（primary/secondary/danger/ghost）和尺寸（small/middle/large）MUST 通过 antd Button 的 `type` 和 `size` props 等价实现。loading 状态 MUST 通过 antd `loading` prop 实现，图标 MUST 通过 `icon` prop 实现。

#### Scenario: 主要按钮
- **WHEN** 组件使用 `<Button variant="primary">确认</Button>`
- **THEN** antd Button 渲染为 `type="primary"` 的主色按钮，视觉效果与交互符合 antd 规范

#### Scenario: 危险按钮
- **WHEN** 组件使用 `<Button variant="danger">删除</Button>`
- **THEN** antd Button 渲染为 `danger` 类型的红色按钮

#### Scenario: 加载状态
- **WHEN** 组件使用 `<Button loading>提交中</Button>`
- **THEN** antd Button 显示加载图标并禁用点击

### Requirement: Modal 组件迁移
自建 `Modal` 和 `ConfirmModal` 组件 SHALL 替换为 antd `Modal`。ESC 关闭、遮罩层点击关闭、焦点管理 MUST 由 antd Modal 原生支持。确认弹窗 MUST 使用 `Modal.confirm` 静态方法实现。

#### Scenario: 确认对话框
- **WHEN** 调用 `Modal.confirm({ title: '确认删除', content: '此操作不可撤销', onOk: handleDelete })`
- **THEN** antd 渲染确认对话框，点击"确定"执行 callback，"取消"关闭对话框

#### Scenario: ESC 关闭
- **WHEN** Modal 打开且用户按下 ESC 键
- **THEN** Modal MUST 关闭，且 `onCancel` callback 被触发

### Requirement: Toast/Message 通知迁移
自建 `ToastProvider`/`useToast` SHALL 替换为 antd `App.useApp().message` 和 `App.useApp().notification` API。四种通知类型（info/success/warning/error）MUST 映射到 antd 对应方法。通知 MUST 支持自动消失时间配置，且 Provider MUST 在应用根层级注入。

#### Scenario: 成功通知
- **WHEN** 操作成功后调用 `message.success('保存成功')`
- **THEN** 页面顶部显示绿色成功提示，N 秒后自动消失

#### Scenario: 错误通知
- **WHEN** 操作失败后调用 `message.error('保存失败')`
- **THEN** 页面顶部显示红色错误提示

### Requirement: Form 表单组件迁移
自建 `Input`/`TextArea`/`Select`/`Label`/`FormField` SHALL 替换为 antd `Input`/`Input.TextArea`/`Select`/`Form.Item`。表单校验反馈 MUST 通过 `Form.Item` 的 `rules` 和 `validateStatus` 实现。表单字段状态管理 MUST 继续使用自建 `useFormField` hook，与 antd `Form` 组件无冲突。

#### Scenario: 文本输入
- **WHEN** 页面渲染 `<Input placeholder="请输入名称" />`
- **THEN** antd Input 渲染为带边框的文本输入框，获得焦点时显示蓝色边框

#### Scenario: 下拉选择
- **WHEN** 页面渲染 `<Select options={options} />`
- **THEN** antd Select 渲染带下拉箭头的选择器，点击展开选项列表

#### Scenario: 表单校验错误
- **WHEN** antd `Form.Item` 的 rules 校验不通过
- **THEN** 表单项 MUST 显示红色边框和错误提示文字

### Requirement: Pagination 分页组件迁移
自建 `Pagination` 组件 SHALL 替换为 antd `Pagination`。页码切换、每页条数切换、总数显示 MUST 通过 antd Pagination props 等价实现。

#### Scenario: 分页导航
- **WHEN** 列表数据超过单页容量且用户点击下一页
- **THEN** antd Pagination MUST 触发 `onChange` 回调并传入新页码

### Requirement: 状态组件迁移
自建 `EmptyState`/`ErrorState`/`LoadingState`/`OfflineState` SHALL 替换为 antd 等价组件：`EmptyState` → `Empty`、`ErrorState` → `Result status="error"`、`LoadingState` → `Spin`、`OfflineState` → `Result status="warning"`。各状态组件 MUST 保持原有的说明文案和操作按钮能力。

#### Scenario: 空数据状态
- **WHEN** 数据列表返回空数组
- **THEN** antd `Empty` 组件渲染空数据图示和"暂无数据"文案

#### Scenario: 加载状态
- **WHEN** 数据正在请求中
- **THEN** antd `Spin` 组件渲染加载动画，不得阻塞页面导航

#### Scenario: 错误状态
- **WHEN** 数据请求失败
- **THEN** antd `Result status="error"` 渲染错误图标、错误说明和"重试"按钮

```

Full source: openspec/changes/migrate-to-antd-pro-infra/specs/shared-component-migration/spec.md

## openspec/changes/migrate-to-antd-pro-infra/specs/web-app/spec.md

- Source: openspec/changes/migrate-to-antd-pro-infra/specs/web-app/spec.md
- Lines: 1-30
- SHA256: 9e3a175f1b96b47e15e3d0fff29e97702f66ff914f68eb63f116c819deb5fa93

```md
# web-app Delta Specification

> 本 delta spec 记录 web-app 能力在 Ant Design Pro 基础设施迁移中的变更。所有原有 behavioral requirements 保持不变；本阶段仅变更构建工具和路由实现，不影响运行时业务行为。

## MODIFIED Requirements

### Requirement: 单一 React SPA 与三角色入口
前端 SHALL 作为单一 React SPA 提供三个固定路由前缀：`/admin` 用于实例管理、`/parent` 用于家长操作、`/child` 用于孩子操作。各入口 MUST 共享认证状态但按路由拆分非首屏资源；反向代理 MUST 将受支持的深层链接回退至 SPA 入口，浏览器刷新不得产生代理层 404。

**实现变更**：路由实现从 React Router DOM v7 迁移至 UmiJS 约定路由（`config/routes.ts`），构建工具从 Vite 5.4 迁移至 UmiJS 4。SPA 入口行为、三角色路由前缀、深层链接兼容性保持不变。

#### Scenario: 访问实例管理入口
- **WHEN** 已认证实例管理员打开 `/admin` 下的受支持页面
- **THEN** SPA MUST 呈现实例管理导航和管理员工作区，且不得加载家长或孩子的受限业务数据

#### Scenario: 访问家长入口
- **WHEN** 已认证家长打开 `/parent` 下的受支持页面
- **THEN** SPA MUST 呈现家长导航及本家庭的任务审核、奖品和兑换管理能力

#### Scenario: 访问孩子入口
- **WHEN** 已认证孩子打开 `/child` 下的受支持页面
- **THEN** SPA MUST 呈现孩子导航及本人任务、积分、奖品和盲盒能力

#### Scenario: 刷新深层链接
- **WHEN** 用户直接访问或刷新 `/parent/exchanges`、`/child/prizes` 等有效深层链接
- **THEN** 反向代理和 SPA MUST 恢复对应页面，不得返回静态文件 404 或跳回错误角色首页

#### Scenario: 未知前端路由
- **WHEN** 用户访问三个入口下不存在的页面
- **THEN** SPA MUST 呈现可返回当前角色首页的 404 状态页，且不得显示空白页面

```
