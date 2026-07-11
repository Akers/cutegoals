# CuteGoals 2.0 — 部署文档

## 概述

本文档描述 CuteGoals 2.0 私有化部署的全流程，包括：

- Docker 构建与编排
- PostgreSQL 数据库配置
- Nginx HTTPS 配置
- 每日自动备份与保留策略
- 数据恢复与恢复演练
- 版本升级与失败回滚
- 故障排查与系统诊断

### 架构

```
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│   Nginx      │─────▶│   Server     │◀────▶│  PostgreSQL  │
│  :80/:443    │      │  :8080       │      │  :5432       │
│  (反向代理)   │      │  (Spring     │      │  (数据持久化) │
│              │      │   Boot)      │      │              │
└──────────────┘      └──────┬───────┘      └──────────────┘
                             │
                             ▼
                      ┌──────────────┐
                      │    Redis     │
                      │   :6379      │
                      │  (会话/缓存) │
                      └──────────────┘

┌────────────────────────────────────────────────────────┐
│  Backup Sidecar                                        │
│  每日 02:00 pg_dump → 7天+4周保留策略                  │
│  写入 backup_run 表                                    │
└────────────────────────────────────────────────────────┘
```

### 镜像前缀

所有 Docker 镜像均使用 `mit-modelide-core-` 前缀：

- `mit-modelide-core-server:<version>` — Spring Boot 后端
- `mit-modelide-core-web:<version>` — 前端静态资源构建产物
- `mit-modelide-core-nginx:<version>` — Nginx 反向代理
- `mit-modelide-core-postgres` — PostgreSQL 16（官方镜像）
- `mit-modelide-core-redis` — Redis 7（官方镜像）
- `mit-modelide-core-backup` — 备份 Sidecar（基于 PostgreSQL 镜像）

---

## 一、系统要求

### 最低配置（参考）

| 资源 | 要求 |
|------|------|
| vCPU | 2 核 |
| 内存 | 4 GB |
| 磁盘 | 20 GB 可用空间 |
| Docker | 24+ |
| Docker Compose | 2.x（已集成到 Docker CLI） |

### 支持平台

- **Linux**: amd64 (x86_64) 和 arm64 (aarch64)
- **Windows**: 通过 Docker Desktop (WSL2 后端)

### 依赖软件

| 软件 | 版本 | 用途 |
|------|------|------|
| Docker | 24+ | 容器运行时 |
| Java（可选） | JDK 21+ | 本地构建后端 |
| Maven（可选） | 3.9+ | 本地构建后端 |
| Node.js（可选） | 20+ | 本地构建前端 |
| npm（可选） | 10+ | 前端依赖管理 |

> **说明**：如果仅使用 `build-docker` 命令，不需要本地安装 Java/Maven/Node.js，构建在 Docker 容器内完成。

---

## 二、安装步骤

### 2.1 克隆仓库

```bash
git clone <仓库地址> cutegoals
cd cutegoals
```

### 2.2 配置环境变量

```bash
cp deploy/.env.template .env
```

编辑 `.env` 文件，填写以下必填项：

| 变量 | 说明 | 要求 |
|------|------|------|
| `PG_PASSWORD` | PostgreSQL 密码 | 必填，强密码 |
| `REDIS_PASSWORD` | Redis 密码 | 必填，强密码 |
| `JWT_SECRET` | JWT 签名密钥 | 必填，至少 32 字符随机字符串 |
| `INIT_TOKEN` | 初始化一次性令牌 | 首次部署必填，初始化后建议移除 |

建议使用以下命令生成安全密码：

```bash
# Linux/Mac
openssl rand -base64 32   # 生成 JWT_SECRET
openssl rand -base64 16   # 生成 PG_PASSWORD

# Windows (PowerShell)
# [Convert]::ToBase64String((1..32|%{Get-Random -Max 256}))
```

### 2.3 构建 Docker 镜像

#### Linux (Bash)

```bash
# 自动检测架构构建
bash deploy/build.sh build-docker

# 指定平台构建
bash deploy/build.sh build-docker --platform linux/amd64
bash deploy/build.sh build-docker --platform linux/arm64

# 禁用缓存
bash deploy/build.sh build-docker --no-cache
```

#### Windows (PowerShell)

```powershell
# 自动检测架构构建
.\deploy\build.ps1 build-docker

# 指定平台构建
.\deploy\build.ps1 build-docker --platform linux/amd64
```

### 2.4 启动服务

```bash
# 前台启动（查看启动日志）
bash deploy/build.sh up

# 后台启动
bash deploy/build.sh up --detach

# 或直接使用 docker compose
docker compose --env-file .env -f deploy/docker-compose.yml up -d
```

### 2.5 验证部署

```bash
# 查看所有服务状态
docker compose --env-file .env -f deploy/docker-compose.yml ps

# 健康检查（未认证）
curl http://localhost/api/health

# 系统诊断
bash deploy/build.sh doctor
```

预期输出：

```json
{
  "status": "UP",
  "initialized": false
}
```

---

## 三、初始化

首次部署后，需要通过 API 完成实例初始化：

### 3.1 注册首位管理员

```bash
curl -X POST http://localhost/api/auth/initialize \
  -H "Content-Type: application/json" \
  -d '{
    "initToken": "你的INIT_TOKEN",
    "phone": "13800138000",
    "password": "管理员密码",
    "nickname": "管理员"
  }'
```

### 3.2 验证初始化状态

```bash
curl http://localhost/api/instance/status
```

预期输出：

```json
{
  "instanceStatus": "INITIALIZED"
}
```

初始化成功后，建议从 `.env` 中移除 `INIT_TOKEN` 或将其置空。

---

## 四、HTTPS 配置

### 4.1 自签名证书（开发/测试）

```bash
mkdir -p ssl
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout ssl/key.pem \
  -out ssl/cert.pem \
  -subj "/CN=${APP_DOMAIN:-localhost}"
```

### 4.2 配置 Nginx HTTPS

编辑 `deploy/nginx.conf`，取消注释 HTTPS server 块并配置 SSL 证书路径。

### 4.3 挂载证书

创建卷挂载或修改 `docker-compose.yml`：

```yaml
mit-modelide-core-nginx:
  volumes:
    - ./nginx.conf:/etc/nginx/conf.d/default.conf:ro
    - ./ssl/cert.pem:/etc/nginx/ssl/cert.pem:ro
    - ./ssl/key.pem:/etc/nginx/ssl/key.pem:ro
```

### 4.4 生产模式说明

- 生产环境 `APP_PRODUCTION=true`（默认）
- 配置文件校验将拒绝空值/示例密码
- 开发模式 `APP_PRODUCTION=false` 允许 HTTP localhost 访问
- 开发模式明确标识不可用于生产

---

## 五、备份

### 5.1 自动备份

备份 Sidecar 每天早上 02:00 自动执行备份：

- 使用 `pg_dump` 创建一致性备份（custom 格式）
- 包含数据库 schema 和数据
- 备份包为 tar.gz 格式，含 SHA256 校验和
- 备份状态写入 `backup_run` 表

### 5.2 保留策略

| 类型 | 保留数量 |
|------|----------|
| 每日备份 | 最近 7 天 |
| 每周备份 | 最近 4 周 |

清理流程在新备份验证成功后执行，不会删除最后一份有效备份。

### 5.3 手动触发备份

```bash
# 通过构建脚本
bash deploy/build.sh backup

# 或直接执行
docker compose --env-file .env -f deploy/docker-compose.yml exec mit-modelide-core-backup /usr/local/bin/backup.sh
```

### 5.4 备份文件位置

备份文件保存在命名卷 `mit-modelide-core-backup-data` 中，容器内路径为 `/backup/`。

### 5.5 备份安全

- 备份日志不含数据库密码
- 备份文件不含环境变量文件
- 校验和确保完整性

---

## 六、恢复

### 6.1 标准恢复流程

```bash
# 从最新备份恢复
bash deploy/restore.sh /backup/cutegoals_backup_20260101_020000.tar.gz
```

恢复步骤：
1. 校验备份完整性（SHA256 / 格式验证）
2. 停止 Server 服务（暂停写流量）
3. 恢复 PostgreSQL 数据库（pg_restore --clean）
4. 重启 Server 服务
5. 等待健康检查通过

### 6.2 恢复演练

```bash
bash deploy/restore.sh /backup/cutegoals_backup_20260101_020000.tar.gz --drill
```

演练结果写入 `recovery_drill` 表，包含 RPO/RTO 实际值和完整性结果。

### 6.3 错误码

| 错误码 | 说明 |
|--------|------|
| `RESTORE_BACKUP_INVALID` | 备份校验失败，不会覆盖现有数据 |
| `RESTORE_FAILED` | 恢复过程中某阶段失败 |

### 6.4 目标

- **RPO** ≤ 24 小时（每日备份）
- **RTO** ≤ 2 小时（参考环境）

---

## 七、升级

### 7.1 正常升级流程

```bash
# 升级到 2.1.0 版本
bash deploy/upgrade.sh 2.1.0
```

升级步骤：
1. 自动执行升级前备份
2. 拉取新版本镜像 / 本地构建
3. 更新 Server 容器（触发 Flyway 迁移）
4. 等待健康检查通过
5. 更新 Nginx 容器

### 7.2 失败恢复

如果迁移或健康检查失败，升级脚本自动回滚：

1. 恢复旧版本镜像
2. 从升级前备份恢复数据
3. 报告 `MIGRATION_FAILED` 或 `UPGRADE_HEALTHCHECK_FAILED`

### 7.3 错误码

| 错误码 | 说明 |
|--------|------|
| `PRE_UPGRADE_BACKUP_FAILED` | 升级前备份失败，升级中止 |
| `MIGRATION_FAILED` | Flyway 迁移失败 |
| `UPGRADE_HEALTHCHECK_FAILED` | 升级后健康检查失败 |
| `DOWNGRADE_NOT_SUPPORTED` | 不支持原地降级 |

### 7.4 降级说明

> **系统不支持原地降级。** 如需回退到旧版本，请使用 `deploy/restore.sh` 从升级前备份恢复。

---

## 八、系统诊断

### 8.1 Doctor 命令

```bash
bash deploy/build.sh doctor
```

检查项：
- Docker 运行状态
- 各服务健康状态
- PostgreSQL 连接
- 磁盘使用率
- 健康检查端点

### 8.2 配置校验

```bash
bash deploy/build.sh env-validate
```

检查必填配置项是否已正确设置。

### 8.3 查看日志

```bash
# 查看所有服务日志
bash deploy/build.sh logs

# 查看特定服务日志
bash deploy/build.sh logs server
bash deploy/build.sh logs nginx
bash deploy/build.sh logs postgres
```

---

## 九、开发环境

### 9.1 使用开发 Compose

```bash
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.dev.yml up -d
```

开发模式特性：
- 源码热加载（需配合 spring-boot-devtools）
- JDWP 调试端口 5005
- 本地构建产物挂载到 Nginx
- Maven 缓存卷加速重复构建
- 允许 HTTP 访问

### 9.2 本地构建

```bash
# 构建后端（跳过测试）
bash deploy/build.sh build-server --skip-tests

# 构建前端
bash deploy/build.sh build-web
```

---

## 十、故障排查

### 10.1 服务无法启动

**症状**：`docker compose ps` 显示服务为 `unhealthy` 或 `exited`

**排查步骤**：

```bash
# 查看日志
bash deploy/build.sh logs server

# 检查配置
bash deploy/build.sh env-validate

# 运行诊断
bash deploy/build.sh doctor
```

**常见原因**：

| 问题 | 解决方案 |
|------|----------|
| PostgreSQL 密码错误 | 检查 `.env` 中的 `PG_PASSWORD` |
| JWT 密钥强度不足 | 检查 `JWT_SECRET` 至少 32 字符 |
| 端口冲突 | 确保 80/443 端口未被占用 |
| 磁盘空间不足 | `docker system prune -a` 清理 |
| 镜像构建失败 | 检查 `bash deploy/build.sh build-docker` 输出 |

### 10.2 CONFIG_INVALID 错误

启动时如果配置校验失败，服务会以非零状态退出并报告 `CONFIG_INVALID`。

**解决方案**：

1. 检查 `.env` 文件是否完整
2. 确保所有密码/密钥不为空
3. 确保 JWT_SECRET 至少 32 字符
4. 删除示例值（如 `changeit`、`your-password` 等）

### 10.3 数据库连接失败

```bash
# 检查 PostgreSQL 是否健康
docker exec mit-modelide-core-postgres pg_isready -U cutegoals

# 测试连接
docker exec mit-modelide-core-postgres psql -U cutegoals -d cutegoals -c "SELECT 1"
```

### 10.4 恢复数据卷

```bash
# 如果需要重建数据卷（会丢失数据！）
docker compose down -v
docker compose up -d
```

### 10.5 备份恢复失败

如果 `restore.sh` 返回 `RESTORE_BACKUP_INVALID`：

1. 检查备份文件是否存在且未损坏
2. 检查校验和文件是否匹配
3. 确认备份格式为 PostgreSQL custom 格式

---

## 十一、生产 Checklist

- [ ] 所有密码/密钥已替换为强密码
- [ ] `INIT_TOKEN` 在初始化后已移除
- [ ] HTTPS 证书已配置
- [ ] 防火墙仅开放 80/443 端口
- [ ] 已测试备份流程
- [ ] 已测试恢复流程
- [ ] 备份日志已配置轮转
- [ ] Docker 守护进程配置了日志限制

---

## 十二、参考

| 服务 | 内部端口 | 对外端口 | 认证 |
|------|----------|----------|------|
| Nginx | 80/443 | 80/443 | — |
| Server API | 8080 | — | 需要 JWT |
| PostgreSQL | 5432 | — | 需要密码 |
| Redis | 6379 | — | 需要密码 |

> **安全提示**：默认 Compose 仅暴露 Nginx 端口（80/443），内部服务不对外暴露。在生产环境部署时，建议配合防火墙进一步限制访问来源。
