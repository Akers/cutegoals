# dev-environment：WSL Ubuntu_26_04 开发环境规格

## 能力概述

CuteGoals 的完整 dev 运行环境可在一台 Windows 机器的 WSL 发行版 Ubuntu_26_04 内独立搭建与复现，包含 Docker CE 引擎、PostgreSQL/Redis 容器、JDK/Maven/Node/pnpm 工具链，以及后端与前端 dev 服务的启动能力。代码仓库单副本，位于 Windows 侧 `/mnt/d/projects/cutegoals`，由 WSL 直接访问。

## 环境

- Docker 引擎：在 Ubuntu_26_04 内安装的 docker-ce（含 docker compose 插件），不依赖 docker-desktop；`docker version` 与 `docker compose version` 可用。
- 数据服务：用仓库 `deploy/docker-compose.yml` 叠加 `deploy/docker-compose.dev.yml`（`--env-file .env`）启动 PostgreSQL（127.0.0.1:35432，库/用户/密码 cutegoals）与 Redis（127.0.0.1:36379，密码 cutegoals）容器；端口、口令、数据卷遵循这两个文件的现有定义。
- 工具链：JDK 21~24（25 与项目 Lombok 不兼容，WSL 内使用 openjdk-21）、Maven ≥3.9、Node ≥20、pnpm@11.15.1（以仓库 README 与 web/package.json 的 packageManager 为准）。

## 行为

### Scenario: WSL 内 docker 可用

在 Ubuntu_26_04 中执行 `docker version` 与 `docker compose version` 均成功返回引擎与插件版本；引擎为 WSL 内 docker-ce，而非 docker-desktop 集成。

### Scenario: PG/Redis 容器运行

执行 `docker compose --env-file .env -f deploy/docker-compose.yml -f deploy/docker-compose.dev.yml up -d mit-modelide-core-postgres mit-modelide-core-redis` 后：PostgreSQL 在 127.0.0.1:35432 以 cutegoals/cutegoals 凭据可登录并存在 cutegoals 库；Redis 在 127.0.0.1:36379 可连通。

### Scenario: 后端 dev 启动

在 `/mnt/d/projects/cutegoals` 下运行 `./scripts/start-dev.sh`（首次交互生成 `.env.dev`，后续可 `--use-env`）：脚本以 dev profile 启动 Spring Boot（`mvn spring-boot:run`），应用成功完成启动、连接上述 PG/Redis，日志（logs/cutegoals-dev.log）无连接类错误。

### Scenario: 前端 dev 启动

在 `web` 下执行 `pnpm install` 后：`apps/console` 的 `pnpm dev`（Vite）与 `apps/kid` 的 `pnpm start`（Umi，监听 8001）均可启动且无致命错误。

### Scenario: 环境可复现

安装与启动步骤以文档或脚本形式保存在仓库中；按该记录在重开 WSL 后可恢复 docker 引擎、PG/Redis 容器与 dev 服务，无需重新探索。

## 约束

- 所有安装仅作用于 Ubuntu_26_04，不改动 Windows 宿主、其他 WSL 发行版或 docker-desktop。
- 不改动仓库业务代码与 deploy/docker-compose.yml 的端口/口令定义。
- 环境验证结论必须来自实际执行的命令结果，而非安装动作推断。
