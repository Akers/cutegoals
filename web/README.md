# CuteGoals 2.0 — 前端（双工程 monorepo）

`web/` 是一个 pnpm workspace，承载两个独立可部署的前端应用：

| 应用 | 路径 | 角色 | 容器 | 默认端口 |
|---|---|---|---|---|
| `console` | `web/apps/console/` | 家长端 + 管理端 | `mit-modelide-core-console` | 8000（dev）/ 8080（容器） |
| `kid` | `web/apps/kid/` | 孩子端（移动优先 + 童趣设计） | `mit-modelide-core-kid` | 8001（dev）/ 8080（容器） |

共享代码（API 客户端、类型、auth、role 归一化、hooks、dayjs 配置）放在 `web/packages/shared/`，通过 `workspace:*` 协议被两个 app 直接引用源码（不构建中间产物）。

## 目录结构

```
web/
├── package.json                 # pnpm workspace 根
├── pnpm-workspace.yaml          # apps/*, packages/*
├── apps/
│   ├── console/                 # 家长端 + 管理端
│   │   ├── config/routes.ts     # /admin/*, /parent/*
│   │   ├── config/config.ts     # umi 配置（含 vite/proxy）
│   │   ├── src/admin/           # 管理端页面
│   │   ├── src/parent/          # 家长端页面
│   │   ├── src/layouts/         # AdminLayout, ParentLayout
│   │   ├── src/wrappers/        # AuthGuard（console 版）
│   │   ├── nginx.conf           # 容器内 nginx 配置
│   │   └── Dockerfile
│   └── kid/                     # 孩子端
│       ├── config/routes.ts     # /child/*
│       ├── config/config.ts     # publicPath=/child/, proxy /child/api→后端
│       ├── src/design/          # 设计令牌 + 自绘童趣组件层
│       │   ├── tokens.css        # 明亮糖果色设计系统
│       │   ├── kid.css           # 组件样式（底部 Tab/Chip/Card...）
│       │   └── components.tsx   # KidPage, KidCard, KidButton, ChipGroup, ...
│       ├── src/pages/           # Home/Tasks/Prizes/BlindBoxes/Exchanges/Login/Bind
│       ├── src/layouts/         # KidLayout（底部 Tab + Header）
│       ├── src/wrappers/        # AuthGuard（kid 版）
│       ├── nginx-kid-api-whitelist.conf  # 孩子端容器 API 白名单（唯一权威）
│       ├── nginx-api-proxy-headers.conf  # 反代公共头
│       ├── nginx.conf           # 容器内 nginx 配置
│       ├── scripts/
│       │   ├── check-api-whitelist.mjs   # 白名单一致性 lint 守卫
│       │   ├── check-css-loads.mjs       # CSS 引用检查
│       │   └── dev-static-server.mjs     # dev 静态服务器（模拟 kid 容器 /child/ alias）
│       └── Dockerfile
├── packages/
│   └── shared/                  # API 客户端、auth、role、theme、dayjs、hooks
└── node_modules/                # pnpm 共享（gitignore）
```

## 常用命令

所有命令在 `web/` 目录下执行。`pnpm` 通过 corepack 启用（项目根 `package.json` 已固定 `packageManager: pnpm@11.15.1`）。

```bash
cd web

# 安装依赖（首次或 package.json 变更后）
pnpm install

# 开发（两个独立终端，分别启动）
pnpm run dev:console   # → http://localhost:8000
pnpm run dev:kid       # → http://localhost:8001/child/

# 测试 + 类型检查 + lint（三个 workspace 一起）
pnpm -r run test        # 205 用例（shared 53 + console 124 + kid 28）
pnpm -r run lint        # tsc + CSS 引用 + kid API 白名单一致性

# 生产构建（构建两个 app 的 dist 产物供 Docker 镜像拷贝）
pnpm --filter @cutegoals/console run build
pnpm --filter @cutegoals/kid run build
# 或一行：pnpm -r --filter "./apps/*" run build

# 修改后端地址（默认 http://localhost:8080）
API_PROXY_TARGET=http://your-backend:8080 pnpm run dev:console
API_PROXY_TARGET=http://your-backend:8080 pnpm run dev:kid
```

详细 npm scripts 见各 app 的 `package.json`。

## console 应用（家长端 + 管理端）

- **路由**：`/admin/*`、`/parent/*`（`web/apps/console/config/routes.ts`）
- **开发代理**：`/api` → `http://localhost:8080`（`config.ts` 中 `proxy`）
- **生产容器**：纯静态（`web/apps/console/Dockerfile` → node:22-alpine 构建 + nginx:1.25-alpine 运行），不提供 API 反代；API 反代由入口网关完成
- **状态管理**：与重构前一致（共享 `packages/shared/auth`、`RoleContext`、ProLayout）

## kid 应用（孩子端）

孩子端是本次重构重点：移动优先 + 童趣设计。

### 设计系统（`web/apps/kid/src/design/`）

- `tokens.css` — 唯一设计令牌源：明亮糖果色（天空蓝主色 + 蜜桃橙/薄荷绿/柠檬黄点缀）、16-20px 圆角、系统圆粗字体栈、emoji 装饰
- `components.tsx` — 自绘童趣组件层：`KidPage`、`KidCard`、`KidButton`、`ChipGroup`、`KidModal`（底部弹层）、`EmptyState`、`PointsHero`、`KidSpinner`、`StateView`、底部 TabBar
- 引用约定：页面与组件**只**引用 `var(--kid-*)` token，不写硬编码颜色/圆角/字号

### 移动优先与响应式

- **断点**：<768px 手机（一等）、768~1023px 平板（一等）、≥1024px PC（辅助形态，居中 560px）
- **触控目标**：≥44px（按钮、Tab、Chip）
- **底部 Tab 导航**：移动/平板固定底部，PC 同样固定居中

### API baseURL 与白名单

- kid 全部 API 走 `baseUrl = "/child/api"`（`web/apps/kid/src/app.tsx` 中 `AuthProvider` 的 `apiBaseUrl` 参数）
- dev 时 umi proxy 剥离 `/child/api` → `/api` 转发后端
- 生产时路径经入口网关 → kid 容器 → `web/apps/kid/nginx-kid-api-whitelist.conf` 白名单过滤后转发后端
- 详见下方"API 白名单"小节与 `deploy/README.md` 的"前端双应用部署"章节

## 共享包（packages/shared）

API 客户端（`@cutegoals/shared`）是 app 间的**源码直接引用**（pnpm workspace 不构建中间产物），不发布 npm。

- `api/`：fetch 客户端、CSRF、错误处理、重试
- `auth/`：`AuthProvider`、`AuthGuard`、`useAuth`，可通过 `apiBaseUrl` prop 切换（console 默认 `/api`，kid 默认 `/child/api`）
- `hooks/useApi.ts`、`hooks/useMutation`
- `role.ts`：后端角色字符串归一化
- `theme.ts`：document `data-role` 切换
- `dayjs.ts`：dayjs 扩展插件注册（必须在 antd 组件前导入）

## API 白名单（kid 容器）

`web/apps/kid/nginx-kid-api-whitelist.conf` 是孩子端容器 nginx 的 API 白名单**唯一权威源**。仅放行孩子端运行时需要的端点前缀，其余一律 403。

**当前白名单（10 个前缀）：**

```
/child/api/auth/child/login
/child/api/auth/logout
/child/api/auth/me
/child/api/family/devices/children
/child/api/task-assignments
/child/api/points/balance
/child/api/prizes
/child/api/blind-boxes
/child/api/exchanges
/child/api/task-review/submissions
```

### 维护流程

1. **新增孩子端功能时**若需调用新端点，先在 kid 源码用 `getClient().get/post` 或 `useApi` 调用，路径以 `/xxx` 开头
2. 在 `web/apps/kid/nginx-kid-api-whitelist.conf` 对应 location 块中加入该前缀的 `proxy_pass http://backend/api/<prefix>`
3. **运行 kid 的 lint 守卫**（自动检查一致）：

   ```bash
   pnpm --filter @cutegoals/kid run lint
   ```

   `scripts/check-api-whitelist.mjs` 会从 kid 源码中提取全部 `/api/...` 与 `/child/api/...` 调用，与白名单双向比对：
   - 源码中每个调用必须命中白名单某条前缀
   - 白名单中每条前缀必须被至少一个源码调用使用（防腐化）
   不一致即 exit 1。
4. 提交修改（白名单 conf 与使用方一起）。

### 与入口网关的关系

- dev 模式：umi proxy 已在 dev 阶段剥离 `/child/api` 前缀
- 生产：入口网关 `deploy/nginx.conf` 收到 `/child/api/*` → 代理到 kid 容器 8080 → kid 容器 nginx 应用白名单（`location` 匹配 + `proxy_pass` 转发后端，剥离 `/child/api` 前缀回 `/api/`）→ 后端
- 开发网关（`deploy/nginx.dev.conf`）通过挂载同一白名单文件 + `includes/api-proxy-headers.conf` 实现等价行为（不需 kid 容器）

## 测试

```bash
cd web
pnpm -r run test
```

- shared：53 用例（role 归一化、dayjs 扩展、API client 错误处理）
- console：124 用例（auth-pages、admin-config、parent-save、task-list 过滤、ProComponents 行为）
- kid：28 用例（五分类筛选、提交受限、登录绑定、AuthGuard）

## 生产构建 → Docker 镜像

```bash
# 两个镜像独立构建（由 deploy/build.sh 的 build-docker 自动执行）
podman build -f web/apps/console/Dockerfile -t mit-modelide-core-console:tag web/
podman build -f web/apps/kid/Dockerfile     -t mit-modelide-core-kid:tag     web/
```

镜像构建：
- 阶段 1（builder）：node:22-alpine + pnpm@11.15.1（packageManager 字段固定），安装整个 monorepo 依赖，构建对应 app，产物在 `/build/apps/<app>/dist`
- 阶段 2（runtime）：nginx:1.25-alpine，COPY 对应 nginx.conf + 白名单文件 + 反代公共头 + 阶段 1 的 dist 产物
- kid 镜像的 nginx 额外 include `/etc/nginx/includes/kid-api-whitelist.conf` 与 `api-proxy-headers.conf`

完整编排与网关配置见 `deploy/README.md`。

## 相关文档

- `deploy/README.md` — 部署、网关、HTTPS、备份、恢复、升级、诊断
- `deploy/docker-compose.yml` — 生产编排（mit-modelide-core-console + mit-modelide-core-kid + 网关 + 后端 + DB + Redis + 备份）
- `deploy/docker-compose.dev.yml` — 开发编排（挂载两个本地 dist + 白名单 + 假后端）
- `deploy/nginx.conf` — 入口网关生产配置（路径分流）
- `deploy/nginx.dev.conf` — 入口网关开发配置（挂载本地 dist）
- `web/apps/console/nginx.conf` — console 容器内 nginx
- `web/apps/kid/nginx.conf` — kid 容器内 nginx（含白名单 include）
- `web/apps/kid/nginx-kid-api-whitelist.conf` — 孩子端容器 API 白名单唯一权威源
- `README.md`（根）— 整体快速启动、API 代理配置、使用入口
- `docs/API.md` — 后端 API 契约
- `docs/ENV_CONFIG.md` — 环境变量说明
