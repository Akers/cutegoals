# 目标

在 WSL 发行版 Ubuntu_26_04 内部署并验证运行 CuteGoals dev 服务所需的完整环境，使后端（`./scripts/start-dev.sh`）和前端（console / kid）都能在 WSL 中启动并正常工作。

# 范围

- 在 Ubuntu_26_04 内安装 Docker CE 引擎（docker-ce + docker compose 插件），不依赖 Windows 侧 docker-desktop。
- 使用仓库 `deploy/docker-compose.yml` + `deploy/docker-compose.dev.yml` 叠加，在 WSL 内以 docker compose 启动 PostgreSQL（127.0.0.1:35432）和 Redis（127.0.0.1:36379）两个容器。
- 在 WSL 内补齐/校验工具链：JDK 21+（不可用 25：Lombok 不兼容，实际安装 openjdk-21）、Maven 3.9+、Node 20+、pnpm@11.15.1。
- 代码直接使用 `/mnt/d/projects/cutegoals`，不在 WSL 内另行 clone。
- 验证 dev 服务：后端通过 `./scripts/start-dev.sh`（dev profile）启动成功，前端 `web/apps/console`（Vite）与 `web/apps/kid`（Umi，端口 8001）可启动。

# 非目标

- 不修改 Windows 侧 docker-desktop 配置，不删除 docker-desktop。
- 不改动仓库业务代码；如启动脚本因 WSL 环境需微调，仅限环境层面并在验收说明。
- 不部署生产编排（nginx、server 容器镜像等 deploy/docker-compose.yml 的其余服务）。
- 不做 e2e 测试套件运行（沿用现有 .e2e-stub 方案，与本次无关）。

# 验收示例

- Scenario: WSL 内 docker 可用 — 在 Ubuntu_26_04 中 `docker version` 与 `docker compose version` 均成功返回，且 Docker 引擎运行在 WSL 内（非 docker-desktop）。
- Scenario: PG/Redis 容器运行 — `docker compose --env-file .env -f deploy/docker-compose.yml -f deploy/docker-compose.dev.yml up -d mit-modelide-core-postgres mit-modelide-core-redis` 后，PostgreSQL 在 127.0.0.1:35432、Redis 在 127.0.0.1:36379 可连通（库/用户/密码 cutegoals）。
- Scenario: 后端 dev 启动 — 在 /mnt/d/projects/cutegoals 下运行 `./scripts/start-dev.sh`（或 --use-env）后，Spring Boot dev profile 成功启动，健康检查/日志无连接错误。
- Scenario: 前端 dev 启动 — `web` 下 pnpm install 后，console（Vite dev）与 kid（Umi dev，8001）均可启动无致命错误。
- Scenario: 环境可复现 — 关键安装与启动步骤记录为可重复执行的说明/脚本（写入仓库文档或部署说明），重开 WSL 后按记录可恢复服务。

# 约束与不变量

- 所有安装仅在 Ubuntu_26_04 发行版内进行，不影响 Windows 宿主与其他 WSL 发行版。
- 代码单副本：使用 /mnt/d/projects/cutegoals，不产生第二份 clone。
- 容器数据卷与配置遵循 deploy/docker-compose.yml 现有定义，不私自改端口/口令。

# 决策

- D1（用户已确认）：PostgreSQL 与 Redis 以容器方式部署，Docker 引擎使用 WSL 内安装的 docker-ce，通过 `docker compose` 启动，不使用 docker-desktop。
- D2（用户已确认）：代码直接使用 /mnt/d 现有目录运行，不在 WSL 内 clone。
- D3（Agent 决定，已被事实修正）：原拟沿用 WSL 内 JDK 25，实际编译触发 Lombok TypeTag UNKNOWN（JDK 25 不兼容），已在 WSL 内安装 openjdk-21 并以 JAVA_HOME=java-21-openjdk 启动；Node 24 / Maven 3.9.16 沿用；pnpm@11.15.1 经 corepack 启用。

# 待解决问题

（无）

# 验证预期

- 在 Ubuntu_26_04 中逐项执行上述 Scenario 验证：docker/compose 版本命令、容器端口连通性、后端启动日志、前端 dev 进程启动。
- 验证命令均可只读复跑；不接受"装了就算"的推断结论。
