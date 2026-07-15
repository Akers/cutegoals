---
comet_change: migrate-to-antd-pro-infra
role: technical-design
canonical_spec: openspec
---

# Ant Design Pro 基础设施迁移 — 深度技术设计

- **Date**: 2026-07-15
- **Change**: migrate-to-antd-pro-infra

## 1. 架构总览

```
UmiJS 4 Build (.umirc.ts)
  └── src/app.tsx —— 全局入口
        ├── antd App 包裹器（message/notification 根注入）
        ├── AuthProvider（保留 shared/auth/AuthContext）
        ├── RoleProvider（保留 shared/role.ts）
        └── 路由分发
              ├── AdminLayout (ProLayout + ConfigProvider admin-theme)
              │     └── admin/ 路由段 → 5 pages (antd)
              ├── ParentLayout (ProLayout + ConfigProvider parent-theme)
              │     └── parent/ 路由段 → 10 pages (antd)
              └── ChildLayout (ProLayout + ConfigProvider child-theme)
                    └── child/ 路由段 → 5 pages (antd)
```

```
保留层：shared/api/client.ts  shared/auth/  shared/hooks/  shared/role.ts
新增层：src/access.ts          src/app.tsx    config/routes.ts
移除层：Vite/*.config.ts       Tailwind       React Router DOM  自建组件
```

## 2. 技术决策

### D1: ProLayout 侧边栏导航

- **选型**: `layout="side"`，侧边栏为主导航，顶栏为头像 + 面包屑
- **理由**: Parent 端 10 个菜单项，侧边栏空间充裕；ProLayout 原生响应式，窄屏自动折叠为汉堡菜单；与 Ant Design Pro 生态一致
- **实现**: 每个角色创建独立 Layout 组件，通过 `route` prop 注入菜单配置，`avatarProps` 注入用户信息和登出

### D2: antd-style 替代 Tailwind CSS

- **选型**: antd-style (`@ant-design/cssinjs` 封装)，`createStyles` API
- **理由**: 与 antd ConfigProvider 主题系统深度集成；支持动态 token 引用；TypeScript 类型安全
- **迁移策略**: 逐页将 Tailwind `className` 替换为 `useStyles()` hook；常用布局模式（flex-center、gap-*、p-*）封装为 `createStyles` 工厂函数

### D3: 单入口 index.html

- **选型**: 移除 `admin.html`/`parent.html`/`child.html`，仅保留 `index.html`
- **理由**: 当前多入口实际加载同一 SPA（均指向 `main.tsx`），无独立价值；UmiJS 单入口标准
- **影响**: nginx 反向代理的 try_files 规则不变（深层链接回退到 index.html）

### D4: UmiJS 约定路由 + Wrappers

- **选型**: `config/routes.ts` 约定路由，`wrappers` 注入 AuthGuard
- **实现**: 三角色路由段各自配置 `wrappers: ['@/wrappers/AuthGuard']`；AuthGuard 读取 AuthContext，未认证跳登录页，角色不匹配显示 antd `Result status="403"`

```ts
// config/routes.ts 结构
export default [
  {
    path: '/admin',
    component: '@/layouts/AdminLayout',
    routes: [
      { path: '/admin/init', component: '@/pages/admin/init' },
      { path: '/admin/login', component: '@/pages/admin/login' },
      { path: '/admin', component: '@/pages/admin/dashboard',
        wrappers: ['@/wrappers/AuthGuard'], access: 'canAdmin' },
      // ...其余 admin 页面
    ]
  },
  // parent/child 同理
]
```

### D5: ConfigProvider theme.token 三角色主题

- **选型**: 三个角色 Layout 各自包裹 `ConfigProvider`，注入对应的 `theme.token`
- **映射**: 从 `themes.css` 的 `--cg-*` CSS 变量映射到 antd token
  - `--cg-color-primary` → `token.colorPrimary`
  - `--cg-color-bg` → `token.colorBgContainer`
  - `--cg-radius` → `token.borderRadius`
  - `--cg-touch-target (44px)` → `token.controlHeight`
- **隔离**: 不同标签页独立 ConfigProvider，不互相污染

### D6: 组件直接替换

- **选型**: 页面直接 `import { Button } from 'antd'`，删除 `shared/components/` 下自建组件
- **映射表**:

| 自建 | antd |
|------|------|
| Button | antd Button (type/size/loading/icon) |
| Modal/ConfirmModal | antd Modal / Modal.confirm |
| ToastProvider/useToast | App.useApp().message / notification |
| Input/TextArea/Select | antd Input/TextArea/Select |
| Pagination | antd Pagination |
| EmptyState | antd Empty |
| ErrorState | antd Result status="error" |
| LoadingState | antd Spin |
| OfflineState | antd Result status="warning" |
| ErrorBoundary | React ErrorBoundary + antd Result status="500" |
| StatusBadge | antd Tag |
| CardSection | antd Card |
| FormField/Label | antd Form.Item |

### D7: 保留自建 ApiClient

- **选型**: `shared/api/client.ts` 保持不变，不引入 umi-request
- **理由**: 自建客户端已覆盖 CSRF 注入、重试、错误映射、幂等 key；迁移到 umi-request 需全部重建，无业务价值

### D8: AuthContext + UmiJS access.ts

- **选型**: 保留 `AuthContext`（认证状态、login/logout），新增 `src/access.ts`
- **实现**: `access.ts` 读取 AuthContext 角色，导出 `canAdmin`/`canParent`/`canChild`；路由通过 `access` 字段引用

### D9: 构建输出与部署

- **选型**: UmiJS 默认 `web/dist/`，同步更新 Dockerfile 中的 nginx `root` 路径
- **Dev 命令**: `npm run start`（UmiJS dev server）
- **Build 命令**: `npm run build`（UmiJS production build）

## 3. 文件变更清单

| 操作 | 文件/目录 | 说明 |
|------|----------|------|
| **新增** | `web/config/config.ts` | UmiJS 主配置 |
| **新增** | `web/config/routes.ts` | 三角色路由表 |
| **新增** | `web/src/app.tsx` | UmiJS 全局入口（App/message 注入） |
| **新增** | `web/src/access.ts` | 权限定义 |
| **新增** | `web/src/layouts/AdminLayout.tsx` | Admin ProLayout |
| **新增** | `web/src/layouts/ParentLayout.tsx` | Parent ProLayout |
| **新增** | `web/src/layouts/ChildLayout.tsx` | Child ProLayout |
| **新增** | `web/src/wrappers/AuthGuard.tsx` | 路由守卫 wrapper |
| **新增** | `web/src/styles/themes.ts` | ConfigProvider token 配置 |
| **修改** | `web/package.json` | 添加 antd/umi/pro-components/antd-style 依赖 |
| **修改** | `web/tsconfig.json` | UmiJS 路径别名 |
| **修改** | `Dockerfile` / nginx conf | 构建产物路径适配 |
| **移除** | `web/vite.config.ts` | Vite 配置 |
| **移除** | `web/tailwind.config.js` | Tailwind 配置 |
| **移除** | `web/postcss.config.js` | PostCSS 配置 |
| **移除** | `web/admin.html/parent.html/child.html` | 多余 HTML 入口 |
| **移除** | `web/src/shared/components/*.tsx` | 自建组件（除 index.tsx） |
| **修改** | `web/src/shared/components/index.tsx` | 导出清空 |
| **移除** | `web/src/main.tsx` | 替换为 UmiJS 入口约定 |

## 4. 风险与缓解

| 风险 | 级别 | 缓解 |
|------|------|------|
| ProTable 列定义与自建表格差异大 | 中 | 逐列映射到 ProTable columns API，保持原列顺序和 field name |
| antd-style 替代 Tailwind 的迁移量 | 中 | 先建 `createStyles` 工厂函数覆盖常用模式（flex/gap/padding），逐页替换 |
| 移除 React Router DOM 后测试用例失败 | 中 | 先运行 `npm test`，逐条适配 UmiJS 路由上下文 |
| 构建产物路径变化导致 Docker 部署失败 | 低 | UmiJS 默认 dist/ 与 Vite 同路径，仅需确认 nginx root |
| 深层链接（书签）兼容性 | 低 | 确保 routes.ts 覆盖所有现有路径，验证直接访问不 404 |

## 5. 测试策略

| 层级 | 范围 | 工具 |
|------|------|------|
| 类型检查 | 全项目 | `npx tsc --noEmit` |
| 单元测试 | 组件渲染 + hooks | Vitest + @testing-library/react |
| 路由集成 | AuthGuard + access | Vitest + 模拟 AuthContext |
| 构建验证 | 生产构建 | `npm run build` |
| E2E | 不修改 | Playwright（页面行为不变，测试应自然通过） |

## 6. 迁移步骤

1. 环境初始化：安装依赖，创建 UmiJS 配置
2. ProLayout 布局：三个角色 Layout 组件
3. ConfigProvider 主题：三套 token 配置
4. 路由迁移：routes.ts + AuthGuard wrapper + access.ts
5. 组件映射：自建 → antd 直接替换
6. 清理：移除 Vite/Tailwind/React Router DOM/自建组件
7. 验证：类型检查 → 单元测试 → 构建 → E2E
