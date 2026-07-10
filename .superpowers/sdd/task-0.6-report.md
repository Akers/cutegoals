# 任务 0.6 完成报告：建立 deploy 目录与管理脚本入口

## 概述

成功创建 `deploy/` 目录骨架，包含构建脚本、Docker Compose 模板、环境变量模板、Dockerfile 以及帮助信息。

## 创建的文件

| 文件 | 说明 |
|------|------|
| `deploy/build.sh` | Bash 构建与部署管理脚本，支持 8 个子命令 |
| `deploy/build.ps1` | PowerShell 等效脚本，支持相同 8 个子命令 |
| `deploy/docker-compose.yml` | Docker Compose 编排，包含 4 个服务 |
| `deploy/.env.template` | 环境变量模板，敏感字段留空 |
| `deploy/docker/Dockerfile.server` | 后端 Spring Boot 多阶段构建 |
| `deploy/docker/Dockerfile.nginx` | 前端 Nginx 多阶段构建（含 SPA） |
| `deploy/nginx.conf` | 更新为 Docker Compose 环境配置（增加 API 反向代理） |

## 验收标准验证

### ✓ 脚本参数检查
- `./deploy/build.sh`（无参数）→ 输出警告并显示帮助，exit code 1
- `./deploy/build.sh unknown-command` → 输出错误提示，exit code 1

### ✓ 子命令支持（8 个）
- `help` - 显示帮助信息
- `build-server [--skip-tests]` - 构建后端 JAR
- `build-web` - 构建前端产物
- `build-docker [--no-cache]` - 构建 Docker 镜像
- `up [--detach]` - 启动服务
- `down` - 停止服务
- `logs [服务名]` - 查看日志
- `backup` - 手动备份

### ✓ Docker Compose 模板
- `mit-modelide-core-server`（后端，mit-modelide-server 前缀风格）
- `mit-modelide-core-nginx`（前端，mit-modelide-web 前缀风格）
- `mit-modelide-core-mysql`（MySQL 8，数据卷持久化）
- `mit-modelide-core-redis`（Redis 7，AOF 持久化）

### ✓ `.env.template`
- 包含 MySQL、Redis、JWT、日志、备份等所有必需变量
- 敏感字段（密码、密钥）为空/占位
- 附带中文注释说明

### ✓ 中文注释与帮助文本
- `build.sh` 和 `build.ps1` 所有输出使用 zh-CN
- `.env.template` 使用中文说明
- Dockerfile 和 docker-compose.yml 使用中文注释

## 注意事项
- PowerShell 脚本未在 Windows 环境实际运行验证（当前为 Linux），语法符合 PowerShell 7+ 标准
- 实际构建后端需要 JDK 21+ 和 Maven 3.9+，前端需要 Node.js 20+
- Docker 镜像构建需要 Docker 24+ with BuildKit
