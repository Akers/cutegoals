# CuteGoals 2.0 — 环境变量配置指南

本文档说明 `deploy/.env.template` 中各项环境变量的含义、默认值及配置建议。部署前请复制模板为 `.env` 并根据实际环境填写。

```bash
cp deploy/.env.template .env
```

> 警告：`.env` 文件包含敏感信息，请勿提交到 Git 仓库（已包含在 `.gitignore` 中）。

## 目录

- [实例标识](#实例标识)
- [PostgreSQL](#postgresql)
- [Redis](#redis)
- [应用配置](#应用配置)
- [短信服务（可选）](#短信服务可选)
- [日志](#日志)
- [备份](#备份)
- [安全配置建议](#安全配置建议)
- [生成强密码](#生成强密码)
- [开发环境 vs 生产环境](#开发环境-vs-生产环境)

## 实例标识

| 变量 | 说明 | 默认值 | 是否必填 |
|------|------|--------|----------|
| `INIT_TOKEN` | 实例初始化一次性令牌 | 空 | **首次部署必填** |

- 首次部署时，`INIT_TOKEN` 用于调用 `/api/auth/initialize` 创建首位管理员。
- 初始化完成后，建议清空或删除该变量，避免重复初始化风险。
- 建议使用至少 32 字符的随机字符串。

## PostgreSQL

| 变量 | 说明 | 默认值 | 是否必填 |
|------|------|--------|----------|
| `PG_DATABASE` | 数据库名 | `cutegoals` | 是 |
| `PG_USER` | 应用数据库用户 | `cutegoals` | 是 |
| `PG_PASSWORD` | PostgreSQL 密码 | 空 | **是** |
| `PG_SCHEMA` | 数据库 schema | `cutegoals` | 是 |

- `PG_PASSWORD` 必须设置为强密码。
- 数据库首次启动时会自动创建用户、数据库和 schema，Flyway 负责表结构迁移。

## Redis

| 变量 | 说明 | 默认值 | 是否必填 |
|------|------|--------|----------|
| `REDIS_PASSWORD` | Redis 访问密码 | 空 | **是** |

- Redis 用于会话缓存、限流计数器和分布式锁。
- 必须设置强密码，否则容器无法通过安全认证启动。

## 应用配置

| 变量 | 说明 | 默认值 | 是否必填 |
|------|------|--------|----------|
| `SERVER_PORT` | 后端服务端口（容器内） | `8080` | 是 |
| `JWT_SECRET` | JWT 签名密钥 | 空 | **是** |
| `APP_DOMAIN` | 部署域名 | `localhost` | 是 |
| `APP_PRODUCTION` | 生产模式标识 | `true` | 是 |
| `APP_VERSION` | 应用版本（镜像标签） | `latest` | 是 |

- `JWT_SECRET`：至少 32 字符的随机字符串，用于签名访问令牌和刷新令牌。修改后所有已登录用户会失效。
- `APP_DOMAIN`：影响 Cookie 的 `Domain` 属性和 CSP 策略。生产环境应设置为实际域名。
- `APP_PRODUCTION`：`true` 时启用 HTTPS-only Cookie 等安全策略；本地开发可设为 `false`。
- `APP_VERSION`：Docker 镜像标签，建议使用语义化版本号，如 `2.0.0`。

## 短信服务（可选）

| 变量 | 说明 | 默认值 | 是否必填 |
|------|------|--------|----------|
| `SMS_PROVIDER` | 短信服务商 | 空 | 否 |
| `SMS_ACCESS_KEY` | 短信服务 Access Key | 空 | 否 |
| `SMS_ACCESS_SECRET` | 短信服务 Access Secret | 空 | 否 |

- 未配置时，短信验证码登录功能自动关闭，不影响手机号+密码登录。
- 如需接入，请实现 `SmsAuthProvider` 接口并配置对应参数。

## 日志

| 变量 | 说明 | 默认值 | 是否必填 |
|------|------|--------|----------|
| `LOG_LEVEL` | 应用日志级别 | `INFO` | 是 |

可选值：`TRACE`、`DEBUG`、`INFO`、`WARN`、`ERROR`。

生产环境建议保持 `INFO`；调试问题时可临时设置为 `DEBUG`。

## 备份

| 变量 | 说明 | 默认值 | 是否必填 |
|------|------|--------|----------|
| `BACKUP_DIR` | 备份文件存放目录（容器内） | `/backup` | 是 |
| `BACKUP_RETENTION_DAYS` | 每日备份保留天数 | `7` | 是 |
| `BACKUP_RETENTION_WEEKS` | 每周备份保留周数 | `4` | 是 |
| `BACKUP_RETENTION_MONTHS` | 每月备份保留月数 | `3` | 是 |

- 备份 Sidecar 每日 02:00 执行 `pg_dump`。
- 保留策略：每日备份保留 7 天、每周备份保留 4 周、每月备份保留 3 个月。
- 建议将 `BACKUP_DIR` 挂载到宿主机持久化目录，避免容器销毁后丢失。

## 安全配置建议

1. **所有密码类变量必须唯一且随机**，不要在多个环境复用同一套密码。
2. **生产环境**必须设置 `APP_PRODUCTION=true` 和正确的 `APP_DOMAIN`。
3. **`JWT_SECRET` 长度不少于 32 字符**，且定期轮换。
4. **初始化后立即移除 `INIT_TOKEN`**，或设置为空字符串。
5. 定期审查备份文件权限，确保只有授权用户可以访问。

## 生成强密码

### Linux / macOS

```bash
# 生成 JWT_SECRET（32 字节 ≈ 44 字符 Base64）
openssl rand -base64 32

# 生成 PG_PASSWORD / REDIS_PASSWORD
openssl rand -base64 16
```

### Windows PowerShell

```powershell
[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Max 256 } | ForEach-Object { [byte]$_ }))
```

## 开发环境 vs 生产环境

### 本地开发最小 `.env`

```dotenv
INIT_TOKEN=dev-init-token
PG_PASSWORD=devpgpass
REDIS_PASSWORD=devredispass
JWT_SECRET=dev-jwt-secret-at-least-32-characters
APP_DOMAIN=localhost
APP_PRODUCTION=false
APP_VERSION=latest
```

### 生产环境 `.env` 示例

```dotenv
INIT_TOKEN=
PG_PASSWORD=<strong-random-password>
REDIS_PASSWORD=<strong-random-password>
JWT_SECRET=<strong-random-base64-string>
APP_DOMAIN=goals.example.com
APP_PRODUCTION=true
APP_VERSION=2.0.0
LOG_LEVEL=INFO
BACKUP_DIR=/backup
BACKUP_RETENTION_DAYS=7
BACKUP_RETENTION_WEEKS=4
BACKUP_RETENTION_MONTHS=3
```

配置完成后，即可执行 `bash deploy/build.sh up --detach` 启动服务。
