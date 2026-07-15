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
| `Modal` / `ConfirmModal` | `antd Modal` / `Modal.confirm` |
| `ToastProvider` / `useToast` | `App.useApp().message` / `notification` |
| `Pagination` | `antd Pagination` |
| `Input` | `antd Input` |
| `TextArea` | `antd Input.TextArea` |
| `Select` | `antd Select` |
| `Label` | `antd Form.Item label` |
| `FormField` | `antd Form.Item` |
| `StatusBadge` | `antd Tag` / `Badge` |
| `Layout` / `PageHeader` | `ProLayout` |
| `EmptyState` | `antd Empty` |
| `ErrorState` | `antd Result status="error"` |
| `LoadingState` | `antd Spin` |
| `OfflineState` | `antd Result status="warning"` |
| `ErrorBoundary` | `antd Result status="500"` + React error boundary |
| `CardSection` | `antd Card` |

**备选方案**：
- *保留自建组件作为 wrapper*：增加不必要的抽象层，维护两套 API 理解成本高
- *直接替换*：简洁，但需要在迁移过程中确保 API 兼容性

### D5: 保留自建 API 客户端，不引入 umi-request

**决策**：保持 `shared/api/client.ts`（含 CSRF 保护、重试、错误映射、幂等 key）作为唯一 HTTP 客户端。不为 ProLayout 引入 umi-request。

**备选方案**：
- *迁移到 umi-request*：需要重新实现 CSRF、重试、幂等逻辑；当前 API 客户端功能完备，无迁移必要
- *双客户端共存*：增加认知负担，统一使用自建客户端更清晰

### D6: AuthContext 保留，接入 UmiJS access 权限机制

**决策**：保持 `shared/auth/AuthContext`（认证状态、登录/登出逻辑）不变。在 `src/access.ts` 中读取 AuthContext 的用户角色，定义 `canAdmin`/`canParent`/`canChild` 权限函数，供路由 `access` 字段使用。路由守卫 `AuthGuard` 迁移为 UmiJS `wrappers` 形式。

### D7: 渐进移除 Tailwind CSS

**决策**：本阶段移除 Tailwind CSS 依赖（`tailwind.config.js`、`postcss.config.js`），清理 `src/index.css` 中的 Tailwind 指令。任何需要自定义样式的场景使用 antd `ConfigProvider` 或 CSS Modules。

## Risks / Trade-offs

- **[风险] UmiJS 构建产物与当前 nginx/Docker 部署配置不兼容** → 缓解：在 build 阶段识别产物路径变化，同步更新 Dockerfile 和 nginx.conf；提前在 Docker 环境验证
- **[风险] ProLayout 菜单折叠/响应式行为与当前 Layout 不一致** → 缓解：在 design 阶段列出功能匹配清单，验收时逐项验证
- **[风险] 路由迁移中深层链接兼容性（用户书签）** → 缓解：确保 UmiJS 路由配置覆盖所有现有路径，验证浏览器直接访问深层链接不返回 404
- **[风险] 移除 Tailwind 后一些细节样式丢失** → 缓解：逐页审查迁移后的视觉效果，利用 ConfigProvider 和组件级 CSS 补充
- **[取舍] ProLayout 侧边导航 vs 当前顶栏导航** → ProLayout 默认侧边栏，可能与当前顶栏布局习惯不同；但 ProLayout 支持 `layout="top"` 顶栏模式，可在验收时与用户确认偏好

## Open Questions

1. ProLayout 导航模式：使用默认侧边栏（`side`）还是顶栏（`top`）？当前自建 Layout 是顶栏 + 底部结构
2. 是否需要保留 `web/index.html` 中的多入口 HTML（admin.html/parent.html/child.html）？Ant Design Pro 单入口模型通常只有一个 `index.html`
3. Tailwind 移除后，是否需要引入 CSS Modules 用于 antd 无法覆盖的自定义样式场景？
