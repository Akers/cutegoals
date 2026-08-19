# frontend-split Specification

## Purpose
定义 CuteGoals 前端从单一 SPA 拆分为两个独立可部署前端工程（console = 家长端 + 管理端，kid = 孩子端）后的工程边界、构建与部署拓扑、入口分流规则与孩子端 API 白名单隔离，确保两端在静态产物（bundle）与网络入口两个层面实现权限隔离，后端零改动。

## Requirements

### Requirement: 双前端工程结构
仓库 MUST 提供两个可独立安装依赖、独立构建、独立测试、独立产出 Docker 镜像的前端工程：console（承载家长端 /parent 与管理端 /admin 全部页面）与 kid（承载孩子端 /child 全部页面，含登录与绑定）。两端共享代码（API client、类型定义、工具函数、认证上下文）MUST 以共享包形式复用，MUST NOT 通过复制源码维护两份。console 工程 MUST NOT import kid 页面代码；kid 工程 MUST NOT import console（家长/管理端）页面代码。

#### Scenario: 独立构建
- **WHEN** 分别执行 console 与 kid 工程的依赖安装与构建命令
- **THEN** 两个工程各自成功产出独立 dist 产物，互不依赖对方的构建结果

#### Scenario: bundle 物理隔离
- **WHEN** 检查 kid 工程构建产物的静态资源
- **THEN** 其中不包含家长端/管理端页面代码与路由（管理配置、账号、审计、家长模板/任务/审核等页面符号与 chunk 均不存在）

### Requirement: 同域路径分流部署拓扑
同一 docker-compose 编排 MUST 运行两个独立 frontend 容器（console、kid），并保留入口网关 nginx 容器作为唯一对外入口（80/443）。网关 MUST 按路径分流：`/child/*` 请求代理至 kid 容器；`/child/api/*` 请求代理至 kid 容器的 API 入口；其余前端路径请求代理至 console 容器；`/api/*` 请求直接反代至后端服务（承载 console 流量）。两个 frontend 容器 MUST 能独立启动、停止与重建，互不影响对方可用性。孩子端 SPA MUST 以 base 路径 `/child/` 构建与服务。

#### Scenario: 路径分流
- **WHEN** 通过入口网关分别请求 `/child/tasks`、`/parent/tasks`、`/admin/config`
- **THEN** 第一个请求由 kid 容器响应，后两个请求由 console 容器响应，各页面正常加载

#### Scenario: 容器独立运维
- **WHEN** 停止并重建 kid 容器
- **THEN** console 容器不受影响，家长端/管理端页面持续可用；kid 容器恢复后孩子端页面恢复可用

### Requirement: 孩子端 API 白名单
kid 前端 MUST 以 `/child/api` 为 API baseURL（而非 `/api`）。kid 容器 nginx MUST 仅放行以下端点（路径以前缀匹配，均相对 `/api`）并将请求转发至后端服务，其余请求 MUST 返回 403：

- POST /auth/child/login、POST /auth/logout、GET /auth/me
- GET /family/devices/children
- GET /task-assignments
- GET /points/balance/*
- GET /prizes、GET /prizes/*
- GET /blind-boxes/* 
- GET /exchanges、POST /exchanges/direct、POST /exchanges/blind-box
- POST /task-review/submissions

白名单 MUST 与孩子端运行时实际调用保持一致；新增孩子端功能调用新端点时 MUST 同步更新白名单。

#### Scenario: 白名单内端点放行
- **WHEN** 孩子端页面经 `/child/api/task-assignments?childId=1` 发起请求
- **THEN** kid 容器将请求转发至后端并返回正常响应

#### Scenario: 白名单外端点拒绝
- **WHEN** 向 kid 容器请求 `/child/api/admin/config` 或 `/child/api/task-templates`
- **THEN** kid 容器返回 403，请求不触达后端

### Requirement: 既有部署要素适配与旧工程退役
docker-compose.yml、docker-compose.dev.yml、build.sh、前端 Dockerfile、nginx.conf、nginx.dev.conf、环境变量模板与 e2e 测试入口 MUST 适配双前端拓扑。原单一 web 工程与单 frontend 容器 MUST 退役，仓库 MUST NOT 同时维护新旧两套可构建前端。

#### Scenario: 部署脚本可用
- **WHEN** 使用 deploy/build.sh 执行构建与启动
- **THEN** console 与 kid 两个 frontend 镜像被构建并以独立容器运行，入口网关分流正确