# antd-pro-framework Specification

## Purpose
TBD - created by archiving change migrate-to-antd-pro-infra. Update Purpose after archive.
## Requirements
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
前端 SHALL 保留 `shared/role.ts` 中的 `RoleProvider` 和角色上下文机制不变。角色判定逻辑、`useRole` hook 的返回值结构 MUST 保持不变。ProLayout 的角色识别 MUST 通过读取同一 RoleContext 实现。

#### Scenario: 角色判定
- **WHEN** 用户登录后进入某个角色页面
- **THEN** `useRole` hook MUST 返回当前活动的角色（admin/parent/child），不得返回 null 或错误角色

