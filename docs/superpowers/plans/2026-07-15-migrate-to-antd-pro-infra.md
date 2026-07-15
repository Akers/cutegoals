---
change: migrate-to-antd-pro-infra
design-doc: docs/superpowers/specs/2026-07-15-migrate-to-antd-pro-infra-design.md
base-ref: d7e319c32b7fbfef1dc58a4d7fdbb49c8029f887
---

# Ant Design Pro 基础设施迁移 实施计划

> **产物语言**: zh-CN
> **关联文档**:
> - 任务边界：`openspec/changes/migrate-to-antd-pro-infra/tasks.md`（9 组 / 32 子任务）
> - 技术设计：`docs/superpowers/specs/2026-07-15-migrate-to-antd-pro-infra-design.md`（7 步迁移顺序 + 9 项关键决策 + 5 项风险）
> - 当前基线：`d7e319c`（React 18 + Vite + Tailwind + 自建组件）
> **实施顺序**：8 阶段，按 环境初始化 → 布局/主题 → 路由/认证 → 组件映射 → 保留验证 → 清理 → 测试 推进
> **核心变更**：Vite → UmiJS 4，Tailwind → antd-style，React Router → 约定路由，自建组件 → antd 直接 import

---

## 计划概览

本计划将 32 项子任务按 Design Doc §6 的迁移步骤归并为 **8 个阶段**，每阶段对应 tasks.md 的 1-2 个章，阶段间存在严格的前置依赖。

**基线约束**：
- 前端基线 = HEAD `d7e319c` 的 `npm test` 全部通过（迁移前基线快照）
- `npx tsc -b --noEmit` 必须零错误
- 构建产物路径从 Vite `dist/` 平滑迁移到 UmiJS 默认 `dist/`

**整体依赖图**：
```
Phase 1 (环境初始化) ──→ Phase 2 (ProLayout) ──→ Phase 4 (路由 + Auth)
         │                    │                        │
         │                    ├──→ Phase 3 (主题) ─────┘
         │                    │                        │
         │                    └────────────────────────┤
         │                                             │
         ├──→ Phase 5 (组件映射) ──────────────────────┤
         │         │                                   │
         │         └──→ Phase 7 (清理) ────────────────┤
         │                                             │
         └──→ Phase 6 (基础设施保留) ──────────────────┘
                                                       ↓
                                        Phase 8 (验证与测试)
```

---

## 阶段 1：环境初始化（4 子任务）

**目标**：安装 UmiJS + antd 生态依赖，创建 UmiJS 配置文件，适配 TypeScript 路径别名，确认开发服务器可启动。

**前置 verify**：
- ⚡ verify 当前 `web/node_modules` 存在，确保 npm install 可直接运行
- ⚡ verify `web/tsconfig.json` 中当前路径别名规则（`@shared`/`@admin`/`@parent`/`@child`），确认需迁移至 `@/` 统一别名
- ⚡ verify Dockerfile 中构建产物 `COPY` 路径和 nginx `root` 路径，确认后续仅需改产物目录名

**涉及 Design Decision**：D3（单入口 index.html）、D4（约定路由）、D9（构建输出 dist/）

---

### 任务 1.1：安装 Ant Design Pro 生态依赖

- **原任务编号**：1.1
- **capability**：web-app
- **目标**：修改 `web/package.json`，添加 `umi@4`、`@ant-design/pro-components`、`@ant-design/icons`、`antd@5`、`@ant-design/cssinjs` / `antd-style` 依赖；执行 `npm install`
- **实现方式**（Design Doc §2 D9 + §3 文件变更清单）：
  ```json
  {
    "dependencies": {
      "umi": "^4.0.0",
      "antd": "^5.0.0",
      "@ant-design/pro-components": "^2.0.0",
      "@ant-design/icons": "^5.0.0",
      "antd-style": "^3.0.0"
    }
  }
  ```
- **输入**：当前 `web/package.json`（React 18 + react-router-dom + Vite 生态）
- **输出**：依赖安装完成，`node_modules/umi` 可执行
- **验收标准**：
  - `node_modules/.bin/umi` 可执行
  - `import { Button } from 'antd'` 无类型错误
  - 现有 Vite 依赖暂不删除（共存期）
- **依赖任务**：无
- **运行验证**：
  ```bash
  cd web && npm install
  node -e "require('umi')"  # 确认 umi 可加载
  ```

---

### 任务 1.2：创建 UmiJS 配置文件

- **原任务编号**：1.2
- **capability**：web-app
- **目标**：创建 `web/config/config.ts`，配置 UmiJS 核心选项
- **实现方式**（Design Doc §2 D4 + D9）：
  ```ts
  // web/config/config.ts
  import { defineConfig } from 'umi';

  export default defineConfig({
    npmClient: 'npm',
    // 路径别名 — 统一为 @/ 前缀
    alias: {
      '@': require('path').resolve(__dirname, '../src'),
    },
    // 代理配置（迁移自 vite.config.ts proxy）
    proxy: {
      '/api': {
        target: process.env.API_PROXY_TARGET ?? 'http://localhost:8981',
        changeOrigin: true,
      },
    },
    // 开发服务器
    devServer: {
      port: 5173,
    },
    // 构建输出
    outputPath: 'dist',
    // 约定路由启用
    routes: [],
  });
  ```
- **输入**：Design Doc §3 文件变更清单「新增 `web/config/config.ts`」
- **输出**：`.umirc.ts` 等效的 `config/config.ts` 文件
- **验收标准**：
  - `npx umi dev` 不报配置错误
  - 路径别名 `@/shared/api/client` 可解析
- **依赖任务**：1.1（依赖已安装）
- **运行验证**：
  ```bash
  cd web && npx umi config list  # 查看最终合并配置
  ```

---

### 任务 1.3：配置 UmiJS TypeScript

- **原任务编号**：1.3
- **capability**：web-app
- **目标**：更新 `web/tsconfig.json`，添加 `@/` 路径映射，确保 UmiJS 类型定义可用
- **实现方式**（Design Doc §3 文件变更清单「修改 `web/tsconfig.json`」）：
  ```json
  {
    "compilerOptions": {
      "paths": {
        "@/*": ["./src/*"]
      },
      "jsx": "react-jsx",
      "esModuleInterop": true
    },
    "include": ["src/**/*.ts", "src/**/*.tsx", ".umi/**/*.ts"]
  }
  ```
- **输入**：当前 `web/tsconfig.json`
- **输出**：`tsc --noEmit` 可正确解析 `@/shared/auth` 等别名
- **验收标准**：
  - VS Code / IDE 自动补全 `@/` 路径
  - `.umi/` 类型文件生成后无报错
- **依赖任务**：1.2（UmiJS 配置存在）
- **运行验证**：
  ```bash
  cd web && npx tsc -b --noEmit  # 预期有大量暂时错误（旧代码未适配），但不得有路径解析错误
  ```

---

### 任务 1.4：验证 UmiJS 开发服务器启动

- **原任务编号**：1.4
- **capability**：web-app
- **目标**：创建最简 `src/app.tsx` + 空白占位路由，确认 UmiJS dev server 正常启动
- **实现方式**（Design Doc §3 文件变更清单「新增 `web/src/app.tsx`」）：
  ```tsx
  // 最小入口 — 后续逐阶段扩建
  export function rootContainer(container: React.ReactElement) {
    return container;
  }
  ```
  同时在 `config/routes.ts` 中定义最简路由：
  ```ts
  export default [
    { path: '/', component: '@/layouts/Welcome', exact: true },
  ];
  ```
- **输入**：Design Doc §6 迁移步骤第 1 步
- **输出**：访问 `http://localhost:5173/` 可见空白页面，无控制台错误
- **验收标准**：
  - `npx umi dev` 启动成功
  - 访问 `/` 不 404，不报错
  - HMR（热更新）改动文件后页面自动刷新
- **依赖任务**：1.2（配置就绪）
- **运行验证**：
  ```bash
  cd web && npx umi dev --port 5173
  # 手动验证：浏览器 http://localhost:5173/ 空白页面
  ```

---

## 阶段 2：ProLayout 布局系统（5 子任务）

**目标**：为 Admin/Parent/Child 三个角色各创建 ProLayout 布局组件，配置侧边菜单项，实现头像下拉菜单、面包屑、响应式折叠。

**前置 verify**：
- ⚡ verify 当前各角色 App.tsx 的菜单结构，确认需迁移的菜单项（名称、路径、图标）
  - Admin：5 个菜单项（实例概览/系统配置/账号管理/审计日志/健康面板）
  - Parent：10 个菜单项（家庭概览/家庭管理/孩子档案/任务模板/任务分配/任务审核/积分/奖品/盲盒/兑换履约）
  - Child：5 个菜单项（今日任务/全部任务/积分商城/惊喜盲盒/兑换历史）
- ⚡ verify 当前 `shared/auth/AuthContext` 中 `user` 对象的字段结构（username、avatar、role 等），供 `avatarProps` 使用

**涉及 Design Decision**：D1（侧边栏导航）、D4（约定路由 wrappers）

---

### 任务 2.1：创建 AdminLayout 组件

- **原任务编号**：2.1
- **capability**：web-app
- **目标**：创建 `web/src/layouts/AdminLayout.tsx`，使用 ProLayout 实现 admin 端布局
- **实现方式**（Design Doc §2 D1）：
  ```tsx
  import { ProLayout } from '@ant-design/pro-components';
  import { Outlet, useNavigate, useLocation } from 'umi';

  export default function AdminLayout() {
    return (
      <ProLayout
        title="CuteGoals Admin"
        logo="..."
        layout="side"
        route={{
          routes: [
            { path: '/admin', name: '实例概览', icon: 'DashboardOutlined' },
            { path: '/admin/config', name: '系统配置', icon: 'SettingOutlined' },
            { path: '/admin/accounts', name: '账号管理', icon: 'UserOutlined' },
            { path: '/admin/logs', name: '审计日志', icon: 'FileTextOutlined' },
            { path: '/admin/health', name: '健康面板', icon: 'HeartOutlined' },
          ],
        }}
        avatarProps={{
          src: user?.avatar,
          title: user?.username,
          render: (_, dom) => <Dropdown menu={menuItems}>{dom}</Dropdown>,
        }}
        location={location}
      >
        <Outlet />
      </ProLayout>
    );
  }
  ```
- **输入**：Design Doc §1 架构总览「AdminLayout (ProLayout + ConfigProvider admin-theme)」、当前 admin/pages/ 目录的页面列表
- **输出**：`web/src/layouts/AdminLayout.tsx` 文件
- **验收标准**：
  - 侧边栏展示 5 个菜单项，可点击切换
  - 头像区域显示用户名和下拉菜单
  - 面包屑自动生成
- **依赖任务**：1.4（UmiJS 开发服务器可启动）
- **运行验证**：
  ```bash
  cd web && npx umi dev
  # 浏览器访问 /admin → 侧边栏可见，菜单点击可导航
  ```

---

### 任务 2.2：创建 ParentLayout 组件

- **原任务编号**：2.2
- **capability**：web-app
- **目标**：创建 `web/src/layouts/ParentLayout.tsx`
- **实现方式**：与 AdminLayout 结构一致，菜单项替换为：
  - 家庭概览 `/parent`、家庭管理 `/parent/manage`、孩子档案 `/parent/profiles`
  - 任务模板 `/parent/profiles`、任务分配 `/parent/assignments`、任务审核 `/parent/review`
  - 积分 `/parent/points`、奖品 `/parent/rewards`、盲盒 `/parent/mystery`、兑换履约 `/parent/fulfillment`
- **输入**：当前 `parent/pages/` 目录的页面列表
- **输出**：`web/src/layouts/ParentLayout.tsx`
- **验收标准**：侧边栏展示 10 个菜单项，可点击切换
- **依赖任务**：2.1（AdminLayout 为参考模板）
- **运行验证**：
  ```bash
  # 浏览器访问 /parent → 侧边栏可见 10 个菜单
  ```

---

### 任务 2.3：创建 ChildLayout 组件

- **原任务编号**：2.3
- **capability**：web-app
- **目标**：创建 `web/src/layouts/ChildLayout.tsx`
- **实现方式**：与 AdminLayout 结构一致，菜单项替换为 Child 端更友好的样式：
  - 今日任务 `/child`、全部任务 `/child/all`、积分商城 `/child/shop`
  - 惊喜盲盒 `/child/mystery`、兑换历史 `/child/history`
- **输入**：当前 `child/pages/` 目录的页面列表
- **输出**：`web/src/layouts/ChildLayout.tsx`
- **验收标准**：侧边栏展示 5 个菜单项，视觉风格符合 Child 角色（后续阶段 3 注入主题）
- **依赖任务**：2.1
- **运行验证**：
  ```bash
  # 浏览器访问 /child → 侧边栏可见 5 个菜单
  ```

---

### 任务 2.4：实现 Layout 通用功能

- **原任务编号**：2.4
- **capability**：web-app
- **目标**：提供共享函数/组件实现三个 Layout 的通用功能：头像下拉菜单（含登出）、面包屑自动生成、响应式折叠
- **实现方式**（Design Doc §2 D1）：
  - 头像下拉菜单（Dropdown）：
    ```tsx
    const menuItems = {
      items: [
        { key: 'profile', label: '个人信息' },
        { key: 'logout', label: '退出登录', danger: true, onClick: handleLogout },
      ],
    };
    ```
  - 响应式折叠：ProLayout `layout="side"` 原生支持，窄屏自动折叠为汉堡菜单
  - 面包屑：ProLayout `breadcrumbRender` 自动从 route 配置生成
- **输入**：Design Doc §2 D1 的 `avatarProps` 和 `breadcrumbRender` 说明
- **输出**：三个 Layout 组件均包含登出交互
- **验收标准**：
  - 点击头像 → 下拉菜单出现「个人信息」「退出登录」
  - 点击「退出登录」→ 调用 `authContext.logout()` → 跳转登录页
  - 浏览器窗口缩窄到 768px 以下 → 侧边栏折叠为汉堡菜单
- **依赖任务**：2.1-2.3（Layout 骨架存在）
- **运行验证**：
  ```bash
  # 手动验证：三个角色 Layout 在桌面端和移动端均可正常渲染，菜单导航可点击
  ```

---

### 任务 2.5：验证布局

- **原任务编号**：2.5
- **capability**：web-app
- **目标**：三角色 Layout 在桌面端和移动端均可正常渲染，菜单导航可点击
- **实现方式**：综合检验 2.1-2.4 的产物
- **验收标准**：
  - 桌面端（1280px+）：侧边栏常驻，内容区跟随菜单切换
  - 移动端（<768px）：侧边栏默认折叠，点击汉堡菜单展开抽屉
  - 三个角色的菜单项数量、顺序、图标均与实际产品一致
- **依赖任务**：2.1-2.4
- **运行验证**：
  ```bash
  # 手动验证：Chrome DevTools Device Mode 切换 iPad / iPhone 视图
  ```

---

## 阶段 3：ConfigProvider 角色主题（5 子任务）

**目标**：基于 `themes.css` 的 CSS 变量映射为 antd `ConfigProvider` 的 `theme.token`，为三个角色各创建独立主题配置，注入到 Layout 中。

**前置 verify**：
- ⚡ verify `themes.css` 中 `--cg-*` 变量的完整列表（3 角色 × ~30 变量）
- ⚡ verify antd 5 `theme.token` 支持哪些属性（colorPrimary、colorBgContainer、borderRadius、controlHeight、fontFamily 等）
- ⚡ verify 三个 Layout 组件中 `ConfigProvider` 包裹位置是否一致

**涉及 Design Decision**：D5（ConfigProvider theme.token 三角色主题）

---

### 任务 3.1：创建 admin 主题 token 配置

- **原任务编号**：3.1
- **capability**：web-app
- **目标**：创建 `web/src/styles/themes.ts`，导出 `adminTheme` token 对象
- **实现方式**（Design Doc §3 文件变更清单「新增 `web/src/styles/themes.ts`」 + §2 D5）：
  ```ts
  // web/src/styles/themes.ts
  import type { ThemeConfig } from 'antd';

  export const adminTheme: ThemeConfig = {
    token: {
      colorPrimary: '#334155',
      colorBgContainer: '#ffffff',
      colorBgLayout: '#f8fafc',
      colorText: '#0f172a',
      colorTextSecondary: '#64748b',
      colorBorder: '#e2e8f0',
      borderRadius: 6,      // --cg-radius-md: 0.375rem
      controlHeight: 44,    // --cg-touch-target: 2.75rem
      fontFamily: "'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif",
    },
    // 语义色：success/warning/info/error
    components: {
      Tag: {
        colorSuccess: '#15803d',
        colorWarning: '#b45309',
        colorInfo: '#0369a1',
        colorError: '#dc2626',
      },
    },
  };
  ```
- **输入**：`themes.css` 中 `[data-role='admin']` 的 CSS 变量值
- **输出**：`adminTheme` 对象，可用于 `ConfigProvider`
- **验收标准**：
  - theme.token 映射覆盖所有主要变量（主色、背景色、文本色、边框色、圆角、字号、触控高度）
  - 缺失的 token（如 shadow）采用 antd 默认值
- **依赖任务**：无（纯静态配置）
- **运行验证**：
  ```bash
  cd web && npx tsc -b --noEmit  # 确认 themes.ts 无类型错误
  ```

---

### 任务 3.2：创建 parent 主题 token 配置

- **原任务编号**：3.2
- **capability**：web-app
- **目标**：在 `themes.ts` 中导出 `parentTheme`
- **实现方式**：结构同 `adminTheme`，改为 parent 色系：
  - `colorPrimary: '#92400e'`（暖棕色）
  - `colorBgLayout: '#faf7f2'`（米色背景）
  - `borderRadius: 8`（0.5rem）
  - `colorBorder: '#e7e5e4'`
- **输入**：`themes.css` 中 `[data-role='parent']` 的 CSS 变量值
- **输出**：`parentTheme` 对象
- **验收标准**：token 映射完整，编译通过
- **依赖任务**：3.1（复用同一 `themes.ts` 文件）
- **运行验证**：
  ```bash
  cd web && npx tsc -b --noEmit
  ```

---

### 任务 3.3：创建 child 主题 token 配置

- **原任务编号**：3.3
- **capability**：web-app
- **目标**：在 `themes.ts` 中导出 `childTheme`
- **实现方式**：结构同 `adminTheme`，改为 child 色系（活泼、友好）：
  - `colorPrimary: '#0284c7'`（亮蓝）
  - `colorBgLayout: '#f0f9ff'`（浅蓝背景）
  - `borderRadius: 12`（1rem，更大的圆角）
  - `colorBorder: '#bae6fd'`
- **输入**：`themes.css` 中 `[data-role='child']` 的 CSS 变量值
- **输出**：`childTheme` 对象
- **验收标准**：token 映射完整，编译通过
- **依赖任务**：3.1
- **运行验证**：
  ```bash
  cd web && npx tsc -b --noEmit
  ```

---

### 任务 3.4：主题注入到三个 Layout

- **原任务编号**：3.4
- **capability**：web-app
- **目标**：在 AdminLayout / ParentLayout / ChildLayout 中包裹对应的 `ConfigProvider`
- **实现方式**（Design Doc §2 D5）：
  ```tsx
  // AdminLayout.tsx
  import { ConfigProvider } from 'antd';
  import { adminTheme } from '@/styles/themes';

  export default function AdminLayout() {
    return (
      <ConfigProvider theme={adminTheme}>
        <ProLayout ...>
          <Outlet />
        </ProLayout>
      </ConfigProvider>
    );
  }
  ```
  ParentLayout 包裹 `parentTheme`，ChildLayout 包裹 `childTheme`
- **输入**：Design Doc §2 D5「三个角色 Layout 各自包裹 ConfigProvider」
- **输出**：三个 Layout 组件包含 `ConfigProvider` 包裹层
- **验收标准**：
  - 访问 `/admin` → antd Button 主色为 `#334155`
  - 访问 `/parent` → antd Button 主色为 `#92400e`
  - 访问 `/child` → antd Button 主色为 `#0284c7`
- **依赖任务**：2.1-2.3（Layout 存在）、3.1-3.3（theme 配置就绪）
- **运行验证**：
  ```bash
  cd web && npx umi dev
  # 浏览器分别访问 /admin /parent /child，确认颜色主题切换
  ```

---

### 任务 3.5：验证主题

- **原任务编号**：3.5
- **capability**：web-app
- **目标**：打开 admin/parent/child 页面，确认颜色、圆角、字号等视觉风格符合原 `themes.css` 设计
- **验收标准**：
  - Admin 端：深灰蓝主色，小圆角（6px），专业稳重
  - Parent 端：暖棕色主色，中等圆角（8px），温馨家庭感
  - Child 端：亮蓝色主色，大圆角（12px），活泼友好
  - 三个角色之间主题不互相污染
- **依赖任务**：3.4
- **运行验证**：
  ```bash
  # 手动视觉审查：Admin / Parent / Child 页面分别在浏览器中打开，对比原 themes.css 截图
  ```

---

## 阶段 4：路由系统 + 认证权限集成（9 子任务）

**目标**：创建 UmiJS 路由配置 `config/routes.ts`，实现 AuthGuard wrapper 组件，创建 `src/access.ts` 权限定义，保留 AuthContext 不变，实现路由级权限控制。

**前置 verify**：
- ⚡ verify 当前各角色的页面路径（`/admin`、`/parent`、`/child` 下所有子路由），确保 routes.ts 覆盖所有路径
- ⚡ verify `shared/auth/AuthContext.tsx` 中暴露的 API（`user`、`isAuthenticated`、`login`、`logout`）
- ⚡ verify 当前 `shared/role.ts` 或 `RoleContext.tsx` 中角色类型定义（`Admin` / `Parent` / `Child`）

**涉及 Design Decision**：D4（约定路由 + wrappers AuthGuard）、D8（AuthContext + access.ts）

---

### 任务 4.1：创建 UmiJS 路由配置

- **原任务编号**：4.1
- **capability**：web-app
- **目标**：创建 `web/config/routes.ts`，定义 `/admin`、`/parent`、`/child` 三组路由段
- **实现方式**（Design Doc §2 D4 + D8）：
  ```ts
  // web/config/routes.ts
  import type { IBestAFSRoute } from 'umi';

  const routes: IBestAFSRoute[] = [
    {
      path: '/admin',
      component: '@/layouts/AdminLayout',
      routes: [
        { path: '/admin/login', component: '@/pages/admin/login' },
        { path: '/admin/init', component: '@/pages/admin/init' },
        { path: '/admin', component: '@/pages/admin/dashboard', access: 'canAdmin',
          wrappers: ['@/wrappers/AuthGuard'] },
        { path: '/admin/config', component: '@/pages/admin/config', access: 'canAdmin',
          wrappers: ['@/wrappers/AuthGuard'] },
        { path: '/admin/accounts', component: '@/pages/admin/accounts', access: 'canAdmin',
          wrappers: ['@/wrappers/AuthGuard'] },
        { path: '/admin/logs', component: '@/pages/admin/logs', access: 'canAdmin',
          wrappers: ['@/wrappers/AuthGuard'] },
        { path: '/admin/health', component: '@/pages/admin/health', access: 'canAdmin',
          wrappers: ['@/wrappers/AuthGuard'] },
      ],
    },
    {
      path: '/parent',
      component: '@/layouts/ParentLayout',
      routes: [
        { path: '/parent/login', component: '@/pages/parent/login' },
        // ... 其余 parent 页面，access: 'canParent', wrappers: ['@/wrappers/AuthGuard']
      ],
    },
    {
      path: '/child',
      component: '@/layouts/ChildLayout',
      routes: [
        { path: '/child/login', component: '@/pages/child/login' },
        // ... 其余 child 页面，access: 'canChild', wrappers: ['@/wrappers/AuthGuard']
      ],
    },
    { path: '/', redirect: '/parent' },
  ];

  export default routes;
  ```
  - 登录页和初始化页**不设置** wrappers/access（允许未认证访问）
- **输入**：Design Doc §2 D4 的 routes.ts 结构示例
- **输出**：`web/config/routes.ts` 文件
- **验收标准**：
  - 所有现有路径均已覆盖
  - 受保护路径配置 `wrappers` 和 `access`
  - 深层链接直接访问不 404（刷新保留路由）
- **依赖任务**：2.1-2.3（Layout 组件存在）、3.4（主题注入完成）
- **运行验证**：
  ```bash
  cd web && npx umi routes  # 查看生成的路由表
  ```

---

### 任务 4.2：创建 AuthGuard wrapper 组件

- **原任务编号**：4.2
- **capability**：web-app
- **目标**：创建 `web/src/wrappers/AuthGuard.tsx`，实现路由级认证守卫
- **实现方式**（Design Doc §2 D4）：
  ```tsx
  // web/src/wrappers/AuthGuard.tsx
  import { Navigate, Outlet, useAccess } from 'umi';
  import { useAuth } from '@/shared/auth';
  import { Result } from 'antd';

  export default function AuthGuard() {
    const { isAuthenticated, user } = useAuth();
    const access = useAccess();

    if (!isAuthenticated) {
      // 未登录 → 跳转登录页
      const loginPath = `/${user?.role ?? 'parent'}/login`;
      return <Navigate to={loginPath} replace />;
    }

    // 检查 access 权限（roles 定义在 access.ts 中）
    if (!access.canAccess) {
      return <Result status="403" title="403" subTitle="抱歉，您没有权限访问此页面" />;
    }

    return <Outlet />;
  }
  ```
- **输入**：Design Doc §2 D4「AuthGuard 读取 AuthContext，未认证跳登录，角色不匹配显示 403」
- **输出**：`web/src/wrappers/AuthGuard.tsx`
- **验收标准**：
  - 未认证 → 重定向到对应角色登录页
  - 角色不匹配 → antd Result status="403"
  - 已认证 + 角色匹配 → 正常渲染子页面
- **依赖任务**：4.1（routes.ts 引用 wrappers）
- **运行验证**：
  ```bash
  # 手动验证：直接访问 /admin → 重定向 /admin/login
  # 登录后访问 /parent → 可正常访问（admin 角色访问 /parent 应 403）
  ```

---

### 任务 4.3：重构 main.tsx → app.tsx 入口

- **原任务编号**：4.3
- **capability**：web-app
- **目标**：将 `main.tsx` 中的 React Router DOM 逻辑迁移到 UmiJS `src/app.tsx` runtime 入口
- **实现方式**（Design Doc §1 架构总览 + §3 文件变更清单）：
  ```tsx
  // web/src/app.tsx
  import { App } from 'antd';
  import { AuthProvider } from '@/shared/auth';
  import { RoleProvider } from '@/shared/role';

  export function rootContainer(container: React.ReactElement) {
    return (
      <RoleProvider role={/* 从路径推断 */}>
        <AuthProvider>
          <App notification={{ maxCount: 3 }} message={{ maxCount: 3 }}>
            {container}
          </App>
        </AuthProvider>
      </RoleProvider>
    );
  }
  ```
  - 保留原有 `AuthProvider` + `RoleProvider` 的嵌套顺序
  - 新增 antd `App` 包裹器（message/notification/modal 根注入）
- **输入**：Design Doc §1 架构总览「antd App 包裹器」
- **输出**：`web/src/app.tsx` 完整 runtime 入口
- **验收标准**：
  - UmiJS 启动后 AuthProvider + RoleProvider 正常工作
  - `App.useApp().message.success('test')` 可在任意子组件中调用
- **依赖任务**：1.4（app.tsx 骨架存在）
- **运行验证**：
  ```bash
  cd web && npx umi dev
  # 确认 React DevTools 中 AuthProvider / RoleProvider 在组件树中
  ```

---

### 任务 4.4：配置子 App 懒加载

- **原任务编号**：4.4
- **capability**：web-app
- **目标**：确保 UmiJS 按路由自动拆分代码（Code Splitting），Admin / Parent / Child 各为独立 chunk
- **实现方式**（Design Doc §2 D4 + umi 内置）：
  - UmiJS 默认按页面懒加载，无需手动 `React.lazy`
  - 配置 `dynamicImport`：在 `config/config.ts` 中启用：
    ```ts
    dynamicImport: {
      loading: '@/components/PageLoading',
    },
    ```
  - 确保代码分割产物大小合理
- **输入**：Design Doc §1 架构总览「按角色拆分 bundle」
- **输出**：构建产物中 admin/parent/child 路由对应的独立 JS chunk
- **验收标准**：
  - `npm run build` 产物中包含按路由命名的 async chunk
  - 页面切换时 Network 面板可见异步加载 JS
- **依赖任务**：4.1（routes.ts 就绪）
- **运行验证**：
  ```bash
  cd web && npm run build
  ls -lh dist/  # 查看产物 chunk 结构
  ```

---

### 任务 4.5：验证路由

- **原任务编号**：4.5
- **capability**：web-app
- **目标**：直接访问 `/admin`/`/parent`/`/child` 及各子路由，确认页面正确加载、深层链接刷新不 404
- **验收标准**：
  - 浏览器直接输入 URL `/admin/config` 可正常加载（不是 404）
  - `/admin` → `/parent` → `/child` 之间切换不出现白屏
  - 刷新任何深层链接均正确渲染对应页面
- **依赖任务**：4.1-4.4
- **运行验证**：
  ```bash
  cd web && npm run build && npx serve dist/
  # 手动验证：所有路由直接访问，刷新均正常运行
  ```

---

### 任务 4.6：保留 AuthContext 不变

- **原任务编号**：5.1
- **capability**：web-app
- **目标**：确认 `shared/auth/AuthContext.tsx` 所有代码保持原样
- **实现方式**：Design Doc §2 D7 + D8「保留 AuthContext」
  - 认证状态（`user`、`isAuthenticated`）
  - 登录/登出函数（`login`、`logout`）
  - 不引入 umi 自身的权限管理替代
- **验收标准**：
  - AuthContext 文件无任何修改
  - 现有 AuthContext 消费者代码仍然正常运行
- **依赖任务**：4.3（app.tsx 需注入 AuthProvider）
- **运行验证**：
  ```bash
  git diff --stat src/shared/auth/  # 预期无改动
  ```

---

### 任务 4.7：创建 UmiJS access.ts 权限定义

- **原任务编号**：5.2
- **capability**：web-app
- **目标**：创建 `web/src/access.ts`，定义 `canAdmin`/`canParent`/`canChild` 权限函数
- **实现方式**（Design Doc §2 D8）：
  ```ts
  // web/src/access.ts
  import { useAuth } from '@/shared/auth';

  export default function access(initialState: { currentUser?: API.CurrentUser } | undefined) {
    const { user } = useAuth();

    return {
      canAdmin: user?.role === 'admin',
      canParent: user?.role === 'parent',
      canChild: user?.role === 'child',
      canAccess: Boolean(user),
    };
  }
  ```
- **输入**：Design Doc §2 D8「access.ts 读取 AuthContext 角色，导出权限函数」
- **输出**：`web/src/access.ts`
- **验收标准**：
  - `useAccess().canAdmin` 在 admin 用户上下文中返回 true
  - `useAccess().canParent` 在 admin 用户上下文中返回 false
- **依赖任务**：4.6（AuthContext 已确认不变）
- **运行验证**：
  ```bash
  cd web && npx tsc -b --noEmit  # 确认 access.ts 无类型错误
  ```

---

### 任务 4.8：路由接入 access 字段

- **原任务编号**：5.3
- **capability**：web-app
- **目标**：在 `routes.ts` 中为各路由段设置 `access` 字段
- **实现方式**（Design Doc §2 D4 + D8）：
  - admin 路由段：`access: 'canAdmin'`
  - parent 路由段：`access: 'canParent'`
  - child 路由段：`access: 'canChild'`
  - login/init 页面：无 access 限制
- **输入**：4.7（access.ts 定义就绪）、4.1（routes.ts 已有结构）
- **输出**：更新后的 `routes.ts`，所有受保护路由均标记 access
- **验收标准**：
  - 孩子角色访问 `/admin` 路由 → 重定向或 403
- **依赖任务**：4.7
- **运行验证**：
  ```bash
  cd web && npx tsc -b --noEmit
  ```

---

### 任务 4.9：验证权限

- **原任务编号**：5.4
- **capability**：web-app
- **目标**：验证完整权限流程
- **验收标准**：
  - 未登录访问 `/admin` → 重定向 `/admin/login`
  - 孩子角色访问 `/admin` → 显示 403
  - 未登录访问 `/parent/assignments` → 重定向 `/parent/login`
  - 合法角色访问合法路由 → 正常渲染
- **依赖任务**：4.8
- **运行验证**：
  ```bash
  # 手动验证三个场景：未登录 / 角色不匹配 / 合法访问
  ```

---

## 阶段 5：共享组件映射（8 子任务）

**目标**：将 `shared/components/` 下的自建组件逐一替换为 antd 等价组件，保留 `shared/components/index.tsx` 重新导出层。

**前置 verify**：
- ⚡ verify 每个自建组件的 props 接口，确认与 antd 组件的映射关系
- ⚡ verify 每个自建组件在项目中的使用位置（grep import），评估影响面
- ⚡ verify `shared/components/index.tsx` 当前导出结构

**涉及 Design Decision**：D6（组件直接替换，不留 wrapper 层）

---

### 任务 5.1：Button 迁移

- **原任务编号**：6.1
- **capability**：web-app
- **目标**：页面中所有自建 `Button` import 替换为 `antd Button`
- **实现方式**（Design Doc §2 D6 映射表）：
  | 自建 | antd |
  |------|------|
  | `<Button variant="primary">` | `<Button type="primary">` |
  | `<Button variant="secondary">` | `<Button>`（默认） |
  | `<Button variant="danger">` | `<Button danger>` |
  | `<Button variant="ghost">` | `<Button type="dashed">` |
  | `<Button size="small">` | `<Button size="small">` |
  | `<Button loading>` | `<Button loading>` |
  | `<Button icon={...}>` | `<Button icon={...}>` |
  - 更新 `shared/components/index.tsx`，`Button` 导出指向 antd
- **输入**：当前 `shared/components/Button.tsx` 的定义
- **输出**：页面中所有 `<Button>` 使用来自 `antd` 或 `@shared/components`→antd
- **验收标准**：
  - 所有四种变体（primary/secondary/danger/ghost）渲染正确
  - 尺寸（small/middle/large）映射正确
  - loading 和 icon 属性正常工作
- **依赖任务**：阶段 4（路由可用，页面可渲染）
- **建议提交粒度**：每个组件 1 个 commit
- **运行验证**：
  ```bash
  cd web && npx tsc -b --noEmit
  # 浏览器验证各页面的 Button 渲染
  ```

---

### 任务 5.2：Modal 迁移

- **原任务编号**：6.2
- **capability**：web-app
- **目标**：将所有自建 `Modal`/`ConfirmModal` 替换为 `antd Modal` / `Modal.confirm`
- **实现方式**（Design Doc §2 D6 映射表）：
  - 普通弹窗 → `<Modal open={visible} onCancel={close}>...</Modal>`
  - 确认弹窗 → `Modal.confirm({ title, content, onOk })`
  - ESC 关闭、遮罩关闭 → antd Modal 默认行为
- **输入**：当前 `shared/components/Modal.tsx` 的使用方式
- **输出**：所有 Modal 使用来自 antd
- **验收标准**：
  - 确认弹窗「确定/取消」按钮行为一致
  - ESC 可关闭弹窗
  - 点击遮罩可关闭弹窗
- **依赖任务**：5.1（Button 迁移后确认弹窗按钮正常）
- **运行验证**：
  ```bash
  cd web && npx tsc -b --noEmit
  # 手动验证：打开弹窗 → 点击取消/确认 → 结果正确
  ```

---

### 任务 5.3：Toast 迁移

- **原任务编号**：6.3
- **capability**：web-app
- **目标**：将 `ToastProvider`/`useToast` 替换为 antd `App.useApp().message`/`notification`
- **实现方式**（Design Doc §2 D6 映射表 + §1 架构总览「antd App 包裹器」）：
  ```tsx
  // 替换前
  const { toast } = useToast();
  toast.success('操作成功');

  // 替换后
  const { message } = App.useApp();
  message.success('操作成功');
  ```
  - Toast 根本性变更：从 context 转为 hook
  - 根组件已注入 antd `App` 包裹器（见任务 4.3）
- **输入**：当前 `shared/components/Toast.tsx` 的导出
- **输出**：所有 `useToast()` 调用替换为 `App.useApp().message`
- **验收标准**：
  - success/info/warning/error 四种 toast 均正常弹出
  - message.config maxCount=3 限制生效
  - notification 支持自定义持续时长
- **依赖任务**：3.4（主题注入）、4.3（App 包裹器）
- **运行验证**：
  ```bash
  cd web && npx tsc -b --noEmit
  npm test -- --run  # toast 相关测试
  ```

---

### 任务 5.4：Form 组件迁移

- **原任务编号**：6.4
- **capability**：web-app
- **目标**：将 `Input`/`TextArea`/`Select`/`Label`/`FormField` 映射为 antd 表单组件
- **实现方式**（Design Doc §2 D6 映射表）：
  | 自建 | antd |
  |------|------|
  | `Input` | `Input` — 直接 import antd |
  | `TextArea` | `Input.TextArea` |
  | `Select` | `Select` |
  | `Label` | 纯 `<label>` 或 antd `Form.Item label` |
  | `FormField` | `Form.Item`（含 label/help/validateStatus） |
  - 保留 `useFormField` hook（业务逻辑无 antd 直接等价物）
- **输入**：当前 `shared/components/Form.tsx` 的定义
- **输出**：所有表单组件使用 antd
- **验收标准**：
  - 表单字段值绑定（受控组件）正常工作
  - 错误状态展示（红色边框 + 提示文字）正常
  - 必填标记（*）渲染正确
- **依赖任务**：阶段 4（路由可用）
- **运行验证**：
  ```bash
  cd web && npx tsc -b --noEmit
  ```

---

### 任务 5.5：Pagination 迁移

- **原任务编号**：6.5
- **capability**：web-app
- **目标**：将自建 `Pagination` 替换为 `antd Pagination`
- **实现方式**：
  - 直接 import antd `Pagination`，props 大致等价（current/pageSize/total/onChange）
- **输入**：当前 `shared/components/Pagination.tsx`
- **输出**：所有列表页使用 antd Pagination
- **验收标准**：
  - 页码切换正常，数据更新正确
  - pageSize 切换（10/20/50/100）正常
  - total 显示正确
- **依赖任务**：阶段 4（路由可用）
- **运行验证**：
  ```bash
  cd web && npx tsc -b --noEmit
  # 手动验证：TaskTemplate 列表页分页功能
  ```

---

### 任务 5.6：状态组件迁移

- **原任务编号**：6.6
- **capability**：web-app
- **目标**：将 `EmptyState`/`ErrorState`/`LoadingState`/`OfflineState` 映射为 antd 组件
- **实现方式**（Design Doc §2 D6 映射表）：
  | 自建 | antd |
  |------|------|
  | `EmptyState` | `Empty`（带 description/children） |
  | `ErrorState` | `Result status="error"`（带 subTitle/extra） |
  | `LoadingState` | `Spin`（带 tip） |
  | `OfflineState` | `Result status="warning" title="网络已断开"` |
  - 更新 `shared/components/index.tsx` 导出
- **输入**：当前 `shared/components/States.tsx` 的定义
- **输出**：所有使用状态组件的地方渲染一致的 antd 组件
- **验收标准**：
  - 列表空 → antd Empty 显示
  - API 错误 → antd Result status="error" 显示
  - 加载中 → antd Spin 显示
  - 断网 → antd Result status="warning" 显示
- **依赖任务**：阶段 4（路由可用）
- **运行验证**：
  ```bash
  cd web && npx tsc -b --noEmit
  # 手动验证各状态的切换场景
  ```

---

### 任务 5.7：其余组件迁移

- **原任务编号**：6.7
- **capability**：web-app
- **目标**：将 `ErrorBoundary`/`CardSection`/`StatusBadge`/`PageHeader` 映射为 antd 等价组件
- **实现方式**（Design Doc §2 D6 映射表）：
  | 自建 | antd |
  |------|------|
  | `ErrorBoundary` | React ErrorBoundary + antd `Result status="500"` |
  | `CardSection` | antd `Card`（含 title/extra/children） |
  | `StatusBadge` | antd `Tag`（含 color 映射） |
  | `PageHeader` | ProLayout 内置面包屑 / antd `PageHeader` |
- **输入**：当前各组件文件
- **输出**：所有替换完成
- **验收标准**：
  - ErrorBoundary 捕获错误 → 显示 500 Result
  - Card 渲染标题和内容区一致
  - Tag 颜色映射正确
- **依赖任务**：阶段 4
- **运行验证**：
  ```bash
  cd web && npx tsc -b --noEmit
  ```

---

### 任务 5.8：更新 shared/components/index.tsx 导出

- **原任务编号**：6.8
- **capability**：web-app
- **目标**：所有组件导出指向新的 antd 映射
- **实现方式**（Design Doc §3 文件变更清单「导出清空」）：
  ```ts
  // web/src/shared/components/index.tsx
  export { Button } from 'antd';
  export { Modal } from 'antd';
  export { Input, InputNumber, Select, Form } from 'antd';
  export { Pagination } from 'antd';
  export { Empty } from 'antd';
  export { Result } from 'antd';
  export { Spin } from 'antd';
  export { Tag } from 'antd';
  export { Card } from 'antd';
  ```
  - 实质上变为 antd 的重新导出层；保留此层以避免所有 import 路径一次性变更
- **输入**：当前 `index.tsx`
- **输出**：`index.tsx` 全部导出指向 antd
- **验收标准**：
  - `import { Button } from '@shared/components'` 仍然有效，指向 antd
  - 所有消费组件无需改动 import 路径
- **依赖任务**：5.1-5.7（所有组件已迁移）
- **运行验证**：
  ```bash
  cd web && npx tsc -b --noEmit
  ```

---

## 阶段 6：基础设施保留确认（4 子任务）

**目标**：验证底层基础设施未被改动，保持向后兼容。

**前置 verify**：
- ⚡ verify `shared/api/client.ts` 无任何改动（运行 `git diff shared/api/client.ts`）
- ⚡ verify `shared/auth/AuthContext.tsx` 已在 4.6 确认
- ⚡ verify `shared/hooks/` 下 6 个 hook 文件均无改动

**涉及 Design Decision**：D7（保留自建 ApiClient）、D8（保留 AuthContext）

---

### 任务 6.1：确认 ApiClient 无改动

- **原任务编号**：7.1
- **capability**：web-app
- **目标**：`shared/api/client.ts` 保持原样
- **验证方式**：运行 `git diff` + 运行现有 API 测试
- **验收标准**：
  - CSRF 注入、重试逻辑、错误映射、幂等 key 全部保持原样
  - 无需引入 umi-request
- **依赖任务**：无（纯验证）
- **运行验证**：
  ```bash
  git diff d7e319c -- web/src/shared/api/client.ts  # 预期无输出
  ```

---

### 任务 6.2：确认 AuthContext 无改动

- **原任务编号**：7.2
- **capability**：web-app
- **目标**：`shared/auth/AuthContext.tsx` 保持原样
- **验证方式**：`git diff`
- **验收标准**：login/logout 函数签名和行为不变
- **运行验证**：
  ```bash
  git diff d7e319c -- web/src/shared/auth/AuthContext.tsx
  ```

---

### 任务 6.3：确认 RoleContext 无改动

- **原任务编号**：7.3
- **capability**：web-app
- **目标**：`shared/role.ts` 和 `RoleContext.tsx` 保持原样
- **验证方式**：`git diff`
- **验收标准**：RoleProvider、useRole hook 返回值结构不变
- **运行验证**：
  ```bash
  git diff d7e319c -- web/src/shared/role.ts web/src/shared/RoleContext.tsx
  ```

---

### 任务 6.4：确认 hooks 全部保留

- **原任务编号**：7.4
- **capability**：web-app
- **目标**：`shared/hooks/` 下所有 hooks 保持不变
- **涉及的 hooks**：`useApi`/`useMutation`/`useFormField`/`useIdempotencyKey`/`useOnline`/`useReducedMotion`
- **验收标准**：所有 hooks 文件零改动
- **运行验证**：
  ```bash
  git diff d7e319c -- web/src/shared/hooks/
  ```

---

## 阶段 7：清理与移除（5 子任务）

**目标**：移除所有已被替代的 Vite/Tailwind/React Router DOM/自建组件文件。

**前置 verify**：
- ⚡ verify 阶段 5（组件迁移）已完成，所有自建组件不再被 import
- ⚡ verify `package.json` 中待移除的依赖项列表
- ⚡ verify Dockerfile 中构建产物路径（确保后续更新正确）

**涉及 Design Decision**：D2（移除 Tailwind）、D3（移除多余 HTML）、D9（dist/ 输出）

---

### 任务 7.1：移除 Vite 配置

- **原任务编号**：8.1
- **capability**：web-app
- **目标**：删除 `web/vite.config.ts`、`web/vite.config.d.ts`，从 `package.json` 移除 Vite 插件依赖
- **需移除**：
  - 文件：`vite.config.ts`、`vite.config.d.ts`、`tsconfig.node.json`
  - devDependencies：`vite`、`@vitejs/plugin-react`
- **验收标准**：`node_modules` 中无 vite 残留；`npm ls vite` 返回空
- **依赖任务**：阶段 5（所有组件已迁移至 antd）
- **运行验证**：
  ```bash
  cd web && npm install  # 移除后重新安装确认无报错
  ```

---

### 任务 7.2：移除 Tailwind CSS

- **原任务编号**：8.2
- **capability**：web-app
- **目标**：删除 `web/tailwind.config.js`、`web/postcss.config.js`，清理 `src/index.css` 中的 `@tailwind` 指令
- **需移除**：
  - 文件：`tailwind.config.js`、`postcss.config.js`
  - devDependencies：`tailwindcss`、`autoprefixer`、`postcss`
  - CSS：`src/index.css` 中的 `@tailwind base; @tailwind components; @tailwind utilities;`
- **验收标准**：
  - 构建产物中无 Tailwind utility class
  - 所有 className 已替换为 antd-style `createStyles`
- **依赖任务**：5.6（所有 Tailwind className 已通过 antd-style 替代）— 注意：Tailwind className 替换在阶段 2-5 各页面适配中逐步完成，此任务为全局清理
- **运行验证**：
  ```bash
  cd web && npm run build
  # 验证构建产物中无 Tailwind 特有 class
  ```

---

### 任务 7.3：移除 React Router DOM 依赖

- **原任务编号**：8.3
- **capability**：web-app
- **目标**：确认所有路由已迁移到 UmiJS 约定路由后，从 `package.json` 移除 `react-router-dom`
- **实现方式**：
  ```bash
  npm uninstall react-router-dom
  ```
- **验收标准**：
  - `npm ls react-router-dom` 返回空
  - 无任何文件 import `react-router-dom`
- **依赖任务**：阶段 4（路由系统完全迁移到 UmiJS）
- **运行验证**：
  ```bash
  cd web && npm ls react-router-dom  # 预期空
  cd web && npx tsc -b --noEmit  # 确认无 broken imports
  ```

---

### 任务 7.4：移除自建组件原始实现

- **原任务编号**：8.4
- **capability**：web-app
- **目标**：删除 `shared/components/` 下的自建组件文件，仅保留 `index.tsx`（重新导出层）
- **需删除的文件**：
  - `shared/components/Button.tsx`
  - `shared/components/Modal.tsx`
  - `shared/components/Toast.tsx`
  - `shared/components/Form.tsx`
  - `shared/components/Pagination.tsx`
  - `shared/components/States.tsx`
  - `shared/components/Layout.tsx`
  - `shared/components/ErrorBoundary.tsx`
- **注意**：移除前需确认 `__tests__/` 中引用这些文件的测试已适配
- **验收标准**：
  - 无任何文件 import 已删除的自建组件
  - `shared/components/index.tsx` 仅保留 antd 重新导出
- **依赖任务**：5.8（index.tsx 已更新）、7.3（路由已迁移）
- **运行验证**：
  ```bash
  cd web && npx tsc -b --noEmit
  ```

---

### 任务 7.5：更新 package.json scripts + HTML 入口清理

- **原任务编号**：8.5
- **capability**：web-app
- **目标**：更新构建命令，移除多余 HTML 入口
- **实现方式**：
  ```json
  {
    "scripts": {
      "start": "umi dev",
      "build": "umi build",
      "lint": "umi lint",
      "test": "vitest run",
      "test:watch": "vitest",
      "format": "prettier --write \"src/**/*.{ts,tsx}\""
    }
  }
  ```
  删除 `admin.html`/`parent.html`/`child.html`（多入口改为单入口）
- **验收标准**：
  - `npm run start` 启动 UmiJS dev server
  - `npm run build` 输出到 `dist/` 目录
  - `index.html` 作为唯一入口
- **依赖任务**：4.1（单入口路由）、7.1-7.4
- **运行验证**：
  ```bash
  cd web && npm run build
  ls dist/  # 确认产物结构
  ```

---

## 阶段 8：验证与测试（5 子任务）

**目标**：全量 TypeScript 编译检查，适配单元测试，验证生产构建，确保开发体验正常。

**前置 verify**：
- ⚡ verify 当前 `npm test` 基线（阶段 1 已记录）
- ⚡ verify CI/CD 构建流程是否依赖 Vite 特定命令（如 `vite build` → 需更新为 `npm run build`）
- ⚡ verify Dockerfile 中 `COPY --from=build /app/web/dist /usr/share/nginx/html` 路径不受影响（UmiJS 默认也是 dist/）

**涉及 Design Decision**：D9（构建输出与部署）、Design Doc §5 测试策略

---

### 任务 8.1：TypeScript 编译检查

- **原任务编号**：9.1
- **capability**：web-app
- **目标**：执行 `npx tsc -b --noEmit`，确保无类型错误
- **验收标准**：
  - exit code 0
  - 0 errors
  - 无 `any` 泄漏（建议配置 `strict: true`）
- **依赖任务**：所有代码变更阶段
- **运行验证**：
  ```bash
  cd web && npx tsc -b --noEmit
  ```

---

### 任务 8.2：单元测试适配

- **原任务编号**：9.2
- **capability**：web-app
- **目标**：更新 `web/src/__tests__/` 中受影响的测试用例，适配 UmiJS 路由上下文和 antd 组件
- **重点适配项**：
  - 依赖 `react-router-dom` 的测试 → 替换为 UmiJS 路由测试辅助
  - 依赖自建组件的测试 → 替换为 antd 组件渲染断言
  - 依赖 Tailwind className 的测试 → 替换为 antd-style CSS 断言或移除
  - Toast 相关测试 → 适配 `App.useApp().message`
- **验收标准**：
  - `npm test` 全部通过（不得有新增失败）
  - 既存测试如无等价实现则显式 skip
- **依赖任务**：阶段 5（组件迁移）、阶段 4（路由迁移）
- **运行验证**：
  ```bash
  cd web && npm test -- --run
  ```

---

### 任务 8.3：构建验证

- **原任务编号**：9.3
- **capability**：web-app
- **目标**：执行 `npm run build`，确认生产构建成功无报错
- **验收标准**：
  - `npm run build` exit code 0
  - 构建产物在 `dist/` 目录
  - 产物大小合理（与迁移前 Vite 构建产物对比）
  - 静态资源（JS/CSS）路径正确
- **依赖任务**：7.5（scripts 已更新）
- **运行验证**：
  ```bash
  cd web && npm run build
  ls -lh dist/
  du -sh dist/
  ```

---

### 任务 8.4：开发体验验证

- **原任务编号**：9.4
- **capability**：web-app
- **目标**：确认热更新、错误提示、Source Map 等开发功能正常
- **验收标准**：
  - 修改 `.tsx` 文件 → HMR 自动更新（无整页刷新）
  - 语法错误 → 浏览器显示错误叠加层（Error Overlay）
  - Source Map → Chrome DevTools 中可查看原始 `.tsx` 代码
- **依赖任务**：三个阶段完成（布局/路由/组件）
- **运行验证**：
  ```bash
  cd web && npm run start
  # 手动验证：修改代码 → 确认 HMR 生效
  # 手动验证：DevTools Sources 面板确认 source map 映射
  ```

---

### 任务 8.5：组件覆盖检查

- **原任务编号**：9.5
- **capability**：web-app
- **目标**：确认 `shared/components/` 下所有自建组件均已映射，无遗漏
- **检查清单**：
  | 自建组件 | antd 映射 | 状态 |
  |----------|----------|------|
  | Button.tsx | antd Button | ✅ |
  | Modal.tsx | antd Modal / Modal.confirm | ✅ |
  | Toast.tsx | App.useApp().message | ✅ |
  | Form.tsx (Input/TextArea/Select/Label/FormField) | antd Input/Input.TextArea/Select/Form.Item | ✅ |
  | Pagination.tsx | antd Pagination | ✅ |
  | States.tsx (EmptyState/ErrorState/LoadingState/OfflineState) | antd Empty/Result/Spin | ✅ |
  | ErrorBoundary.tsx | React ErrorBoundary + antd Result 500 | ✅ |
  | Layout.tsx (CardSection/PageHeader) | antd Card / ProLayout breadcrumb | ✅ |
  | StatusBadge (在 States.tsx 中) | antd Tag | ✅ |
- **验收标准**：表格中所有项标记 ✅，无 ❌
- **依赖任务**：5.7
- **运行验证**：
  ```bash
  cd web && npx tsc -b --noEmit
  # 确认 import 自建组件路径不存在
  ```

---

## 附加：增量验证点汇总

| # | 验证点 | 来源 | 影响 | 决策时机 |
|---|---|---|---|---|
| 1 | UmiJS `npm run start` 开发服务器正常启动 | §1.4 | 全部后续阶段 | 阶段 1 结束 |
| 2 | 三套 Layout 侧边栏渲染、菜单可点击 | §2.5 | 路由系统可用性 | 阶段 2 结束 |
| 3 | 三角色主题颜色不互相污染 | §3.5 | 视觉一致性 | 阶段 3 结束 |
| 4 | 深层链接刷新不 404 | §4.5 | 用户体验、SEO | 阶段 4 结束 |
| 5 | 所有自建组件均有 antd 等价替换 | §5.8 | 组件覆盖率 | 阶段 5 结束 |
| 6 | AuthContext / ApiClient / hooks 零改动 | §6.1-6.4 | 向后兼容 | 阶段 6 结束 |
| 7 | Vite/Tailwind/react-router-dom 彻底移除 | §7.1-7.5 | 构建纯净度 | 阶段 7 结束 |
| 8 | `npm test` 零新增失败 + `npm run build` 成功 | §8.1-8.5 | 项目健康度 | 阶段 8 结束 |

---

## 附加：风险与缓解措施（来自 Design Doc §4）

| # | 风险 | 级别 | 缓解措施 | 影响阶段 |
|---|---|---|---|---|
| 1 | ProTable 列定义与自建表格差异大 | 中 | 逐列映射到 ProTable columns API，保持原列顺序和 field name | 阶段 5 |
| 2 | antd-style 替代 Tailwind 迁移量大 | 中 | 先建 `createStyles` 工厂函数覆盖常用模式，逐页替换 | 阶段 2-5 |
| 3 | 移除 React Router DOM 后测试失败 | 中 | 先运行 `npm test` 记录基线，逐条适配 UmiJS 路由上下文 | 阶段 8 |
| 4 | 构建产物路径变化导致部署失败 | 低 | UmiJS 默认 dist/ 与 Vite 同路径，仅需确认 nginx root | 阶段 8 |
| 5 | 深层链接（书签）兼容性 | 低 | 确保 routes.ts 覆盖所有现有路径 | 阶段 4 |

---

## 附加：测试策略汇总（来自 Design Doc §5）

| 验证层级 | 命令/方式 | 通过标准 | 阶段 |
|---|---|---|---|
| 类型检查 | `cd web && npx tsc -b --noEmit` | exit 0, 0 errors | 每阶段末尾 |
| 单元测试 | `cd web && npm test -- --run` | 全部通过，0 新增失败 | 阶段 8 |
| 路由集成 | Vitest + 模拟 AuthContext | AuthGuard + access 逻辑正确 | 阶段 8 (8.2) |
| 生产构建 | `cd web && npm run build` | exit 0, 产物大小合理 | 阶段 8 |
| E2E | Playwright（不修改） | 页面行为不变，测试应自然通过 | 阶段 8+ |
| 视觉审查 | 手动浏览器对比 | 三角色主题颜色、圆角、字号符合原设计 | 阶段 3 |

---

## 附录：文件改动清单

| 操作 | 文件/目录 | 估计行数 | 说明 |
|------|----------|----------|------|
| **新增** | `web/config/config.ts` | +30 行 | UmiJS 主配置（别名、代理、构建） |
| **新增** | `web/config/routes.ts` | +40 行 | 三角色路由表 + access + wrappers |
| **新增** | `web/src/app.tsx` | +20 行 | UmiJS 全局入口（Auth/Role/App 注入） |
| **新增** | `web/src/access.ts` | +12 行 | 权限定义（canAdmin/canParent/canChild） |
| **新增** | `web/src/layouts/AdminLayout.tsx` | +40 行 | Admin ProLayout（侧边栏 + 头像个区） |
| **新增** | `web/src/layouts/ParentLayout.tsx` | +40 行 | Parent ProLayout |
| **新增** | `web/src/layouts/ChildLayout.tsx` | +40 行 | Child ProLayout |
| **新增** | `web/src/wrappers/AuthGuard.tsx` | +25 行 | 路由守卫（未认证→登录，403→禁止） |
| **新增** | `web/src/styles/themes.ts` | +80 行 | 三套 ConfigProvider token 配置 |
| **修改** | `web/package.json` | ±15 行 | 添加 umi/antd/pro-components/antd-style，移除 Vite 生态 |
| **修改** | `web/tsconfig.json` | ±5 行 | 统一 `@/` 别名 |
| **修改** | `Dockerfile` / nginx conf | ±2 行 | 确认产物路径 `dist/` 一致 |
| **修改** | `web/src/shared/components/index.tsx` | ±10 行 | 导出全部指向 antd |
| **移除** | `web/vite.config.ts` | -50 行 | Vite 配置 |
| **移除** | `web/vite.config.d.ts` | -3 行 | Vite 类型声明 |
| **移除** | `web/tsconfig.node.json` | -9 行 | Vite 专用 tsconfig |
| **移除** | `web/tailwind.config.js` | -30 行 | Tailwind 配置 |
| **移除** | `web/postcss.config.js` | -6 行 | PostCSS 配置 |
| **移除** | `web/admin.html` | -13 行 | 多余 HTML 入口 |
| **移除** | `web/parent.html` | -13 行 | 多余 HTML 入口 |
| **移除** | `web/child.html` | -13 行 | 多余 HTML 入口 |
| **移除** | `web/src/main.tsx` | -22 行 | 替换为 UmiJS 入口约定 |
| **移除** | `web/src/shared/components/Button.tsx` | -~40 行 | 自建 Button |
| **移除** | `web/src/shared/components/Modal.tsx` | -~60 行 | 自建 Modal |
| **移除** | `web/src/shared/components/Toast.tsx` | -~40 行 | 自建 Toast |
| **移除** | `web/src/shared/components/Form.tsx` | -~80 行 | 自建表单组件 |
| **移除** | `web/src/shared/components/Pagination.tsx` | -~50 行 | 自建 Pagination |
| **移除** | `web/src/shared/components/States.tsx` | -~60 行 | 自建状态组件 |
| **移除** | `web/src/shared/components/ErrorBoundary.tsx` | -~30 行 | 自建 ErrorBoundary |
| **移除** | `web/src/shared/components/Layout.tsx` | -~50 行 | 自建 Layout 组件 |

**预估总变更量**：+~350 行（新增）、-~560 行（删除），净减少 ~210 行。
