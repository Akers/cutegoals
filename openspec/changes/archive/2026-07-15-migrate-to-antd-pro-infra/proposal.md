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
