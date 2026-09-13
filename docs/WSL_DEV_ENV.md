# WSL Ubuntu_26_04 开发环境部署指南

本文记录在 WSL 发行版 `Ubuntu_26_04` 内从零搭建 CuteGoals dev 环境（后端 + 双前端）的完整步骤，已于 2026-09-07 实际验证。

## 架构

- Docker 引擎：WSL 内 docker-ce（不依赖 Windows 侧 docker-desktop）。
- PostgreSQL：容器，宿主机 `127.0.0.1:35432`（库/用户/密码均 `cutegoals`）。
- Redis：容器，宿主机 `127.0.0.1:36379`（密码 `cutegoals`）。
- 代码：直接使用 Windows 侧 `/mnt/d/projects/cutegoals`，单副本。
- 后端 dev：8080；console（Vite）：8000；kid（Umi）：8001。

## 一次性安装

```bash
# 1) Docker CE（需先配置 Docker 官方 apt 源，docker-ce 不在 Ubuntu 自带源中），如尚未安装
sudo apt-get install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
  https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.sources
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 2) JDK 21（JDK 25 会触发 Lombok TypeTag UNKNOWN 编译错误，必须 21~24）
sudo apt-get install -y openjdk-21-jdk-headless
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

# 3) pnpm 11.15.1（Node ≥20 已随发行版/nvm 存在）
corepack enable pnpm
corepack prepare pnpm@11.15.1 --activate

# 4) 关闭 pnpm 运行前依赖校验（否则 pnpm dev 因 core-js-pure 构建脚本被忽略而失败）
pnpm config set --global verifyDepsBeforeRun false
```

## 每次启动（重开 WSL 后）

```bash
cd /mnt/d/projects/cutegoals

# 1) 数据库（首次需先生成根目录 .env，见下）
docker compose --env-file .env -f deploy/docker-compose.yml -f deploy/docker-compose.dev.yml \
  up -d mit-modelide-core-postgres mit-modelide-core-redis

# 2) 前端依赖（首次或 lockfile 变化后；重建 node_modules 需 CI=true）
cd web && CI=true corepack pnpm install --frozen-lockfile

# 3) 后端（.env.dev 已存在时）
cd .. && ./scripts/start-dev.sh --use-env
#    脚本会自动把 JAVA_HOME 对齐到 PATH 中通过版本检查的 JDK（如 java=21、JAVA_HOME=25 时自动切 21）；
#    仍建议在 shell profile 中固定 JAVA_HOME 指向 JDK 21~24

# 4) console（家长/管理端）
cd web/apps/console && corepack pnpm dev

# 5) kid（孩子端）
cd web/apps/kid && PORT=8001 corepack pnpm start
```

### 首次生成的两个配置文件

```bash
# 仓库根 .env（compose 用；只起 PG/Redis 时仅 PG_*/REDIS_* 生效）
printf "PG_DATABASE=cutegoals\nPG_USER=cutegoals\nPG_PASSWORD=cutegoals\nPG_SCHEMA=cutegoals\nREDIS_PASSWORD=cutegoals\n" > .env

# 仓库根 .env.dev（后端用；正式做法是跑一次交互式 ./scripts/start-dev.sh）
# 关键值：PG_PORT=35432  REDIS_PORT=36379  REDIS_PASSWORD=cutegoals
```

## 验证

```bash
curl -s http://localhost:8080/api/health          # {"code":"SUCCESS",...}
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8000/          # 200
curl -s -H "Accept: text/html" -o /dev/null -w "%{http_code}\n" http://localhost:8001/child   # 200（Umi 对非 HTML 请求返回 404，属正常）
```

## 已知事项

- **CRLF（已修复）**：仓库根 `.gitattributes` 已强制 `*.sh text eol=lf` 并重规范化全部脚本；新 clone / 重检出后 WSL 内可直接执行 `./scripts/start-dev.sh`。旧检出需 `git add --renormalize . && sed -i "s/\r$//" $(git ls-files "*.sh")` 一次。
- **JDK 版本**：必须 21~24（25 与 Lombok 不兼容）；`start-dev.sh` 已内置 JAVA_HOME 自动对齐，会把 Maven 切到 PATH 中通过检查的 JDK。
- **跨文件系统性能**：/mnt/d 上 Maven 全量构建约 1 分钟，Umi 首次编译数分钟，属预期。
- **node_modules 平台切换**：在 WSL 内重建过 node_modules 后，Windows 侧需重新 `pnpm install` 才能继续在 Windows 跑前端。
- 与 WSL 内另一份 clone（`~/projects/cutegoals`，端口 45000/45100）互不冲突、互不影响。
- `scripts/start-dev.sh` 交互默认 `REDIS_PORT=6379`，与 dev compose 的 36379 不一致；本环境直接维护 `.env.dev`（`REDIS_PORT=36379`），脚本默认值属仓库既有问题，未在本次修改。
