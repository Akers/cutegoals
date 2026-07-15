## 1. 环境初始化

- [x] 1.1 安装 Ant Design Pro 依赖：将 `umi`、`@ant-design/pro-components`、`@ant-design/icons`、`antd` 添加到 `web/package.json`，执行 `npm install`
- [x] 1.2 创建 UmiJS 配置文件 `web/config/config.ts`，配置路径别名 `@/` → `src/`、history 类型为 browser、开启 hash 路由
- [x] 1.3 配置 UmiJS TypeScript：更新 `web/tsconfig.json`，添加 `@/` 路径映射、JSX 配置、确保 UmiJS 类型定义可用
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
