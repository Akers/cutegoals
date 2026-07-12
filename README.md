# CuteGoals

CuteGoals 2.0 — 面向家庭的任务与奖励协作平台，帮助孩子通过完成任务赚取积分、兑换奖励，家长可管理家庭、任务、审核与奖品。

[![Contributors](https://img.shields.io/github/contributors/Akers/cutegoals.svg?style=flat-square)](https://github.com/Akers/cutegoals/graphs/contributors)
[![Forks](https://img.shields.io/github/forks/Akers/cutegoals.svg?style=flat-square)](https://github.com/Akers/cutegoals/network/members)
[![Stargazers](https://img.shields.io/github/stars/Akers/cutegoals.svg?style=flat-square)](https://github.com/Akers/cutegoals/stargazers)
[![Issues](https://img.shields.io/github/issues/Akers/cutegoals.svg?style=flat-square)](https://github.com/Akers/cutegoals/issues)
[![MIT License](https://img.shields.io/github/license/Akers/cutegoals.svg?style=flat-square)](https://github.com/Akers/cutegoals/blob/main/LICENSE)

### 面向开发者的 README

## 目录

- [上手指南](#上手指南)
  - [开发前的配置要求](#开发前的配置要求)
  - [安装步骤](#安装步骤)
  - [快速启动脚本](#快速启动脚本)
- [文件目录说明](#文件目录说明)
- [开发的架构](#开发的架构)
- [部署](#部署)
- [环境变量配置](#环境变量配置)
- [API 接口说明](#api-接口说明)
- [使用到的框架](#使用到的框架)
- [贡献者](#贡献者)
  - [如何参与开源项目](#如何参与开源项目)
- [版本控制](#版本控制)
- [作者](#作者)
- [版权说明](#版权说明)
- [鸣谢](#鸣谢)

### 上手指南

#### 开发前的配置要求

1. **JDK** 21+
2. **Maven** 3.9+
3. **Node.js** 20+ / **npm** 10+
4. **Docker** 24+（可选，用于部署或 Testcontainers 测试）
5. **Docker Compose** 2.x（可选，用于部署）

#### 安装步骤

1. 克隆仓库

```bash
git clone git@github.com:Akers/cutegoals.git
cd cutegoals
```

2. 启动数据库依赖

如果是手动安装的 PostgreSQL，请先使用 `deploy/postgres-init.sql` 初始化数据库和用户：

```bash
psql -U postgres -h localhost -p 35432 -f deploy/postgres-init.sql
```

如果使用 Docker Compose，直接启动 PostgreSQL 和 Redis 容器：

```bash
docker compose -f deploy/docker-compose.yml up -d mit-modelide-core-postgres mit-modelide-core-redis
```

3. 构建并运行后端

```bash
cd server
mvn clean install
mvn -pl web -am spring-boot:run -DskipTests
```

4. 运行前端

```bash
cd web
npm install
npm run dev
```

5. 初始化系统

首次启动后访问 http://localhost:5173，使用部署时配置的 `INIT_TOKEN` 完成管理员初始化。

#### 快速启动脚本

项目提供交互式开发启动脚本，支持自动检查 JDK/Maven、交互式输入 PostgreSQL/Redis 地址与凭据、自动生成/保存 `.env.dev`、启动后端并实时输出日志。

**Linux / macOS**

```bash
./scripts/start-dev.sh
```

**Windows（PowerShell）**

```powershell
.\scripts\start-dev.ps1
```

脚本会读取上一次保存的 `.env.dev` 作为默认值，回车即可复用。按 `Ctrl+C` 停止服务。

### 文件目录说明

```
cutegoals
├── LICENSE
├── README.md
├── deploy/                    # Docker 部署脚本与编排
│   ├── README.md
│   ├── build.sh
│   ├── build.ps1
│   ├── docker-compose.yml
│   ├── docker-compose.dev.yml
│   ├── postgres-init.sql      # PostgreSQL 开发环境初始化脚本
│   └── .env.template
├── docs/                      # 项目文档与 OpenSpec
│   ├── API.md                 # 后端 API 接口说明
│   └── ENV_CONFIG.md          # 环境变量配置指南
├── e2e/                       # Playwright 端到端测试
├── openspec/                  # OpenSpec 变更规范
├── scripts/                   # 开发辅助脚本
│   ├── start-dev.sh           # Linux/macOS 后端开发启动脚本
│   └── start-dev.ps1          # Windows 后端开发启动脚本
├── server/                    # Spring Boot 多模块后端
│   ├── pom.xml
│   ├── common/                # 公共 DTO、工具、异常
│   ├── auth/                  # 认证、授权、会话、审计
│   ├── family/                # 家庭、家庭成员、角色
│   ├── task/                  # 任务模板、任务分配、提交
│   ├── task-review/           # 任务审核流程
│   ├── points/                # 积分账户、流水、统计
│   ├── prize/                 # 奖品、盲盒、库存
│   ├── exchange/              # 积分兑换、核销
│   ├── instance-management/   # 多实例、初始化、配置
│   └── web/                   # 启动入口、Web 配置、全局处理器
└── web/                       # React + Vite 前端
    ├── src/
    │   ├── admin/             # 管理后台页面
    │   ├── parent/            # 家长端页面
    │   ├── child/             # 孩子端页面
    │   ├── shared/            # 共享组件、Hook、工具
    │   └── styles/            # 主题与样式
    └── package.json
```

### 开发的架构

CuteGoals 2.0 采用前后端分离架构：

- **后端**：Spring Boot 3 多模块服务，按业务领域（家庭、任务、积分、奖品）拆分模块，使用 PostgreSQL 持久化、Redis 缓存会话。
- **前端**：React 18 + Vite + TypeScript + TailwindCSS，按用户角色（admin / parent / child）组织页面。
- **部署**：Docker Compose 编排 Nginx、Server、PostgreSQL、Redis 与备份 Sidecar。

详细架构说明请参阅 `docs/` 目录。

### 部署

完整部署文档请参考 [deploy/README.md](deploy/README.md)。

快速开始：

```bash
cp deploy/.env.template .env
# 编辑 .env 填写 PG_PASSWORD、REDIS_PASSWORD、JWT_SECRET、INIT_TOKEN
bash deploy/build.sh build-docker
bash deploy/build.sh up --detach
```

### 环境变量配置

`.env` 配置说明请参考 [docs/ENV_CONFIG.md](docs/ENV_CONFIG.md)，包含 PostgreSQL、Redis、JWT、备份、日志等变量的详细说明和生成强密码的方法。

### API 接口说明

后端 REST API 列表、认证方式与示例请参考 [docs/API.md](docs/API.md)。

### 使用到的框架

- [Spring Boot 3.2](https://spring.io/projects/spring-boot)
- [MyBatis-Plus](https://baomidou.com/)
- [Flyway](https://flywaydb.org/)
- [PostgreSQL](https://www.postgresql.org/)
- [Redis](https://redis.io/)
- [React 18](https://react.dev/)
- [Vite](https://vitejs.dev/)
- [TailwindCSS](https://tailwindcss.com/)
- [Playwright](https://playwright.dev/)
- [Vitest](https://vitest.dev/)

### 贡献者

欢迎通过 Issue 和 Pull Request 参与项目。

#### 如何参与开源项目

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

### 版本控制

该项目使用 Git 进行版本管理。当前分支策略：

- `main`：稳定发布分支
- `feature/*`：功能开发分支
- `1.0`：历史版本维护分支

### 作者

AkersLiang

邮箱：akersliang@gmail.com

_您也可以在贡献者名单中参看所有参与该项目的开发者。_

### 版权说明

该项目签署了 MIT 授权许可，详情请参阅 [LICENSE](LICENSE)。

### 鸣谢

- [GitHub Emoji Cheat Sheet](https://www.webpagefx.com/tools/emoji-cheat-sheet)
- [Img Shields](https://shields.io/)
- [Choose an Open Source License](https://choosealicense.com/)
- [GitHub Pages](https://pages.github.com/)
- [Best README Template](https://github.com/shaojintian/Best_README_template)
