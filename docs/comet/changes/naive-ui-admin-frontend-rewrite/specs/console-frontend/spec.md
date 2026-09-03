# Capability: console-frontend（家长端 + 管理端前端）

重写完成后，`web/apps/console` 是一个基于 naive-ui-admin v2.1.0（Vue 3.5 + Naive UI 2.43 + Vite 5 + Pinia + Vue Router 4 + Tailwind + Less）的单页应用，承载家长端（`/parent/*`）与管理端（`/admin/*`），通过现有 Spring Boot 后端 API（Cookie 会话 + CSRF）提供与旧 React 实现功能对等的能力。后端、`web/apps/kid`、`web/packages/shared` 零改动。

## 1. 应用基座与目录结构

- 基座：naive-ui-admin v2.1.0 模板（clone 于 https://github.com/jekip/naive-ui-admin），保留其官方目录约定：
  - `build/`（Vite 配置与脚本）、`mock/`（保留目录但 `VITE_USE_MOCK=false`）、`src/api/`、`src/components/`、`src/design|styles/`、`src/enums/`、`src/hooks/`、`src/layouts/`（default/iframe/page）、`src/locales/`、`src/logics/`、`src/router/`、`src/settings/`（projectSetting、designSetting、componentSetting、localeSetting、encryptionSetting、siteSetting）、`src/store/`（Pinia modules）、`src/utils/http/alova/`、`src/views/`。
- 包名 `@cutegoals/console`；workspace 根脚本名不变：`dev:console`（端口 8000）、`build`（递归 apps/*）。
- 环境变量遵循模板约定：`VITE_PORT=8000`、`VITE_PUBLIC_PATH=/`、`VITE_GLOB_API_URL` 指向后端基址、`VITE_PROXY=[["/api","http://localhost:8080"]]`（`API_PROXY_TARGET` 可覆盖）、`VITE_USE_MOCK=false`、生产 `VITE_DROP_CONSOLE`。`VITE_GLOB_*` 经 `_app.config.js` 运行时读取（useGlobSetting）。

## 2. 路由与菜单

- `src/router/modules/*.ts` 自动注册；仅布局层路径以 `/` 开头；路由 `name` 全局唯一；keepAlive 的路由 `name` 必须等于组件名。
- 路由树（与旧版一致）：
  - `/admin` 子树（AdminLayout 区）：`/admin/init`（公开）、`/admin/login`（公开）、`/admin`（概览，默认重定向目标）、`/admin/config`、`/admin/accounts`、`/admin/audit`、`/admin/health`。
  - `/parent` 子树（ParentLayout 区）：`/parent/login`（公开）、`/parent`（工作台，默认重定向目标）、`/parent/family`、`/parent/children`、`/parent/templates`、`/parent/tasks`、`/parent/reviews`、`/parent/points`、`/parent/prizes`、`/parent/blind-boxes`、`/parent/devices`、`/parent/exchanges`。
  - `/` 重定向 `/parent`。
- route meta 使用模板字段：`title`（中文）、`icon`、`hidden`（login/init 不进菜单）、`sort`、`keepAlive`、`permissions`（按需）。
- 权限模式 `permissionMode: FIXED`：登录后按 `/auth/me` 角色过滤路由与菜单；家长访问 `/admin/*` 拒绝，管理员不显示家长业务菜单；按钮级权限用 `usePermissions` / `v-permissions`（如有需求）。
- 未登录访问受保护路由 → 跳对应登录页（`/parent/*`→`/parent/login`，`/admin/*`→`/admin/login`）；已登录访问登录页 → 跳对应工作台。

## 3. 认证与会话

- 会话为服务端 Cookie（`credentials: 'include'`），不使用 token/localStorage 登录态。
- 启动时调用 `GET /api/auth/me` 恢复会话（Pinia user store）；401 视为未登录。
- 登录：家长 `POST /api/auth/login`（手机号+密码）；管理员 init 流程（无初始化时 `INIT_TOKEN` + 管理员手机号 + ≥8 位含字母数字密码）与 `POST /api/auth/admin/login` 按后端现有契约。
- CSRF：从 `<meta name="csrf-token">` 或 `csrf_token` Cookie 读取，写入请求头 `X-CSRF-TOKEN`。
- 登出：调用后端登出接口并清理本地用户态，跳对应登录页。

## 4. HTTP 层（src/utils/http/alova/）

- 保持模板 alova 封装的结构与调用方式（`vaRequest`/实例创建、拦截/转换钩子位置），替换转换逻辑适配后端：
  - 响应包 `ApiResponse{code: string, message: string, data: T, request_id: string|null}`；`code === 'SUCCESS'` 时向调用方返回 `data`，否则以 `message`（含后端业务错误码语义）抛出/提示。
  - 请求自动携带会话 Cookie 与 `X-CSRF-TOKEN`；超时/网络错误有统一提示；401 触发按路由前缀的登录跳转（替换模板默认的「登录超时」+ token 清除逻辑）。
  - 不修改后端返回结构，不做后端适配层。
- API 模块按域组织于 `src/api/`：`auth.js|ts`、`family`、`task`（模板/任务/提交/审核）、`points`、`prize`（奖品/盲盒）、`exchange`、`device`、`admin`（config/accounts/audit-logs/health/init），路径与后端现有一致（`/api/auth/*`、`/api/family/*`、`/api/task-*`、`/api/points/*`、`/api/prizes/*`、`/api/exchanges/*`、`/api/blind-boxes/*`、`/api/admin/*`）。
- 详细字段以 `server/*/src/main/java/com/cutegoals/**/controller/` 与 `docs/API.md` 为准。

## 5. 页面与功能对等（对照旧 React 版逐页核对）

- 家长端：工作台（概览统计/快捷入口）、家庭设置（家庭信息、家长账号管理）、孩子档案 CRUD、任务模板 CRUD（含任务类型配置表单的 Vue 等价）、任务（列表/发布/任务日历 TaskCalendar 等价实现）、审核（提交审核/驳回/重新提交控制）、积分（流水/调整）、奖品（普通奖品/类型配置表单）、盲盒（创建/配置）、设备授权（deviceId 授权/解绑）、兑换（申请列表/核销）。
- 管理端：init（初始化表单）、概览、配置（实例配置查看/编辑，敏感项掩码）、账号（管理员启用/禁用）、审计（登录与操作日志查询）、健康（数据库/Redis 状态）。
- 交互语义（成功/失败提示、确认弹窗、加载态、空态、分页）与旧版一致；文案 zh-CN。

## 6. 布局与主题

- 采用模板默认布局：侧边菜单（vertical, dark）+ 头部 + 面包屑 + 多标签页（multiTabs 按模板默认）、默认主题色 `#2d8cf0`、亮色 darkTheme: false；保留模板的主题/暗色设置能力。
- 家长端与管理端共用同一 Layout 组件实例，菜单数据按角色区动态生成（两套路由子树）。

## 7. 构建与部署

- `pnpm build`（vite build + postBuild）产出 `dist/` 静态资源；`VITE_BUILD_COMPRESS` 按需；生产关闭 mock 与 console。
- 产物仍由现有 console 容器 nginx 以静态 SPA 方式服务：history 路由需 `try_files $uri $uri/ /index.html;`（与现有容器配置核对，必要时仅调整前端容器 nginx 配置文件，不动后端与网关拓扑）；index.html 响应加 no-store/no-cache。
- 本地开发：`/api` 代理到 `http://localhost:8080`（`API_PROXY_TARGET` 覆盖）。

## 8. 旧实现清理

- 删除 `web/apps/console` 内旧 React/Umi 实现与 console 专属文件；`web/packages/shared`、`web/apps/kid` 不动（kid 继续使用 shared）。
- workspace 根脚本 `dev:console`、`build` 行为保持（新 app 以等价脚本提供）。

## 9. 测试

- 更新 `e2e/tests/` 中 console 相关用例（auth-guard.spec.ts、parent-task-calendar.spec.ts、resubmission-controls.spec.ts）以新 UI 选择器/流程表达原覆盖意图并通过。
- 模板自带 lint/类型检查通过；不强制为每页新增单测（与旧版对齐，旧版仅有 e2e + 少量 vitest）。

## 10. 不变量

- `server/**`、`web/apps/kid/**`、`web/packages/shared/**` 零改动。
- URL、端口、代理、认证方式、API 契约与重写前一致。
- 仓库中不出现任何凭据明文。
