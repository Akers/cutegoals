# Outcome

用 naive-ui-admin（https://github.com/jekip/naive-ui-admin，Vue 3 + Naive UI + Vite + Pinia + Vue Router）作为纯前端框架，重写 `web/apps/console` 中的家长端（`/parent/*`）与管理端（`/admin/*`）前端。重写后的应用功能与现有 React 实现对齐（功能对等），通过现有后端 API（Cookie 会话 + CSRF）正常工作，严格遵循 naive-ui-admin 官方文档的项目结构、路由、HTTP 层与构建约定。后端、API 契约、孩子端（kid）均不改动。

# Scope

- 以 naive-ui-admin v2.1.0（最新 release tag）为基座，替换 `web/apps/console` 为 Vue 3 单页应用，保留现有 URL 体系与单容器部署拓扑（端口 8000，静态 SPA）。
- 家长端页面（`/parent` 前缀）：login、home（工作台）、family（家庭设置）、children（孩子档案）、templates（任务模板）、tasks（任务）、reviews（审核）、points（积分）、prizes（奖品）、blind-boxes（盲盒）、devices（设备授权）、exchanges（兑换）——全部功能对等重写（含 TaskCalendar、任务类型配置表单、奖品类型配置表单、任务类型筛选等组件的 Vue 等价实现）。
- 管理端页面（`/admin` 前缀）：init（系统初始化）、login、overview（概览）、config（配置）、accounts（账号）、audit（审计）、health（健康）。
- 按官方文档约定组织代码：`src/router/modules/*.ts` 自动注册路由、`src/views/` 页面、`src/api/` 接口模块、`src/settings/`（projectSetting/designSetting 等）、`src/utils/http/alova/` HTTP 层、`src/store/`（Pinia）。
- HTTP 层适配现有后端（不改后端）：会话 Cookie（credentials include）+ `X-CSRF-TOKEN` 头；响应包 `ApiResponse{code, message, data, request_id}`，成功码 `"SUCCESS"`、载荷取 `data` 字段；401 按区域跳转 `/parent/login` 或 `/admin/login`。
- 权限模式 FIXED（前端路由 + 角色过滤）：按 `/auth/me` 返回的角色区分家长/管理员菜单与路由访问。
- 界面语言 zh-CN；禁用模板 mock（真实后端）。
- 更新 `e2e/` 中针对 console 路由的 Playwright 用例以匹配新 UI，保持测试套件有效。
- 清理被替换的 console 旧 React 实现及其专属文件（git 历史保留可回溯）。

# Non-goals

- 不修改 `server/` 任何后端逻辑、Controller、API 路径、请求/响应契约。
- 不改动 `web/apps/kid`（孩子端）与 `web/packages/shared`（kid 仍依赖）。
- 不新增后端 API；不为前端需要而改造后端返回结构。
- 不改变部署拓扑与入口网关分流（`/child/*` → kid、其余 → console；console 容器仍为纯静态 SPA）。
- 不做移动端适配改造（家长端/管理端保持桌面后台形态）。
- 不引入 naive-ui-admin 商业版（NaiveAdmin Pro）组件。

# Acceptance examples

1. `cd web && pnpm install && pnpm run dev:console` 启动 Vue 版 console 于 `http://localhost:8000`；`/` 重定向到 `/parent`。
2. 未登录访问 `/parent` 跳转 `/parent/login`；使用手机号+密码登录成功（走现有 `/api/auth/*` 会话接口）后进入家长端工作台，刷新页面会话保持（`/auth/me` 恢复）。
3. 家长端 12 个页面（login + 11 功能页）全部可从菜单到达，任务发布/审核/积分/奖品/盲盒/兑换/设备授权等核心流程可对现有后端完成真实操作，功能与旧 React 版一致。
4. 未登录访问 `/admin` 跳转 `/admin/login`；首次部署访问 `/admin` 时可完成 init 初始化流程（INIT_TOKEN + 管理员手机号 + 密码）；管理员可使用概览/配置/账号/审计/健康页面（走 `/api/admin*`）。
5. 家长角色访问 `/admin/*` 被拒绝/跳转，管理员角色看不到家长端业务菜单——角色隔离行为与旧版一致（e2e auth-guard 场景覆盖）。
6. HTTP 层：成功响应取 `data` 字段渲染；业务错误展示后端 `message`；会话过期（401）跳转对应端登录页；请求携带会话 Cookie 与 `X-CSRF-TOKEN`。
7. `pnpm run build`（workspace 递归）成功产出 console 静态产物，mock 关闭，kid 构建不受影响。
8. `e2e/` 中 console 相关 Playwright 用例针对新 UI 更新后通过（含 auth-guard、家长任务日历、重新提交控制场景）。
9. `git status` 显示 `web/apps/kid/**` 与 `web/packages/shared/**` 无改动。

# Constraints and invariants

- 严格按 naive-ui-admin 官方文档（https://docs.naiveadmin.com）约定开发：目录结构、路由模块自动注册、route meta 字段、settings 配置文件、HTTP 层位置、环境变量（`VITE_GLOB_API_URL`、`VITE_PROXY` 等）、构建命令与产物（dist + postBuild）。
- 模板基线为 v2.1.0；文档与模板代码不一致处（HTTP 层 axios→alova）以模板实际代码为准（文档滞后于 2.0.0 重构）。
- Node ≥ 22（当前环境 v24.18.0 满足）、pnpm；路径不含中文/空格。
- 保持 URL 与端口：`/parent`、`/admin`、`/`→`/parent`、dev 端口 8000、`/api` 代理到后端（`API_PROXY_TARGET` 可覆盖）。
- 认证不变：服务端 Cookie 会话 + CSRF 头，不引入 token/localStorage 登录态。
- UI 语言 zh-CN；不删除模板的暗色/主题设置能力（默认亮色）。
- 不把任何凭据（INIT_TOKEN、密码等）写入仓库文件。

# Decisions

- D1 原地替换：`web/apps/console` 直接替换为 Vue 版（保留目录名、脚本名 `dev:console`、端口与部署方式），重写完成后删除旧 React console 实现与 console 专属共享代码；git 历史保留回滚能力。（依据：用户明确"重写"且不修改后端，并存双实现无收益）
- D2 单应用双区：家长端与管理端继续同处一个 app，用模板 Layout + 角色区分菜单与路由区（`/parent`、`/admin` 两个子树），permissionMode=FIXED。
- D3 HTTP 适配：遵循模板代码的 alova 封装位置与写法，替换其拦截/转换逻辑以适配 `ApiResponse{code,message,data,request_id}`（成功码 `"SUCCESS"`、载荷 `data`）、Cookie 会话与 CSRF 头；401 按路由前缀跳对应登录页。不改后端。
- D4 kid 与 shared 不动：`web/apps/kid`、`web/packages/shared` 保持原样；Vue 版 console 自带 API/鉴权层，不共享 React 代码。
- D5 禁用 mock：`VITE_USE_MOCK=false`，全部走真实后端；dev 代理 `/api` → `http://localhost:8080`。
- D6 采用模板默认视觉：naive-ui-admin 默认布局（侧边菜单/头部/面包屑/多标签）、默认主题色，两端共用；不逐像素复刻旧 antd 风格。（依据：用户要求"使用该框架重写并严格按官方文档"）
- D7 e2e 更新：console 相关用例改为适配新 UI 的选择器与流程，保持既有覆盖意图（守卫隔离、日历、重新提交）。

# Open questions

（无 —— 2026-09-02 用户确认共享理解，进入 Build。）

# Verification expectations

- 构建：`web` workspace `pnpm run build` 成功（console 产出 dist）；模板自带 lint/类型检查通过。
- 运行验证：dev 启动后用真实后端手动冒烟（登录、核心页面加载、角色隔离、401 跳转），或以 Playwright e2e 更新后通过为准。
- 一致性：抽查路由/目录/HTTP 层是否符合官方文档约定；对照旧 React 版逐页核对功能对等清单。
- 回归：确认 `web/apps/kid`、`web/packages/shared`、`server/` 零改动。
