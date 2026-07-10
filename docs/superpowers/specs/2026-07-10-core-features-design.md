---
comet_change: core-features
role: technical-design
canonical_spec: openspec
---

# CuteGoals 2.0 core-features 技术设计文档

## 1. 设计目标

本文档将 `openspec/changes/core-features/` 中的 delta spec 转化为可实施的技术设计，覆盖后端、前端、数据库、部署、测试与运维。OpenSpec delta spec 是验收事实源；本文档仅说明如何实现这些规格，不引入新的需求或范围变更。

## 2. 技术栈与高层架构

### 2.1 技术栈

- **后端**：Spring Boot 3.2.x + Java 21 + Maven 多模块
- **持久化**：MySQL 8.0 + MyBatis-Plus 3.5.x + Flyway 10.x
- **缓存/会话**：Redis 7.x
- **前端**：React 18 + TypeScript 5.x + Vite 5.x + React Router 6.x
- **测试**：JUnit 5 + Mockito + Testcontainers + Playwright
- **部署**：Docker 24.x + Docker Compose 2.x，支持 Windows 10+/Linux 主机，目标镜像多架构 linux/amd64 + linux/arm64
- **运维**：backup-sidecar（mysqldump + 保留策略）、健康探针、审计日志

### 2.2 部署形态

- 每个 Docker Compose 部署对应一个私有化实例，MVP 限制为一个实例一个家庭。
- 所有镜像名称统一前缀 `mit-modelide-`；训练用容器不在本 change 中，但本 change 的所有容器以 `mit-modelide-core` 开头。
- 默认联网本地构建，不预置离线镜像归档。

### 2.3 后端模块划分

```
server/
├── pom.xml                       # 聚合父 POM，管理依赖版本
├── common/                       # 通用：异常、错误码、分页、工具、审计基础、安全工具
├── web/                          # REST 入口、全局异常处理、拦截器、路由注册
├── auth/                         # 实例初始化、账号、会话、角色、设备绑定、PIN
├── family/                       # 家庭、成员、家长邀请、孩子档案
├── task/                         # 任务模板、任务分配、快照、提交、审核、迟交策略
├── points/                       # 积分流水、余额投影、退款、调整
├── prize/                        # 奖品资料、库存、历史快照
├── exchange/                     # 兑换、盲盒、幂等、履约、取消退款
└── instance-management/          # 配置、账号启停、审计、健康、备份/恢复状态
```

每个模块拥有自己的 `application-*.yml` 配置段、Mapper、Service、Controller（在 web 中注册）。模块间通过接口依赖，避免循环依赖。

### 2.4 前端结构

```
web/
├── packages/
│   ├── shared/                   # 共享组件、hooks、API 客户端、类型、工具、错误码映射
│   ├── admin/                  # 实例管理员入口
│   ├── parent/                 # 家长入口
│   └── child/                  # 孩子入口
├── package.json
└── vite.config.ts
```

每个入口为独立 Vite 构建产物，通过 Nginx 按路径路由；共享包通过 monorepo/workspace 或相对路径导入。

## 3. 数据模型

### 3.1 核心实体表

| 表 | 说明 | 关键约束 |
|---|---|---|
| `account` | 账号（手机号、密码哈希、状态、角色） | 手机号唯一，密码 bcrypt |
| `role_binding` | 账号与角色多对多 | 角色字符串枚举 |
| `session` | 会话记录（Redis 为主，DB 异步审计） | 过期时间，设备指纹 |
| `family` | 家庭 | 单实例唯一约束；保留 `family_id` 以支持未来扩展 |
| `child_profile` | 孩子档案 | 归属家庭，昵称/头像/生日最小化 |
| `device_binding` | 设备绑定 | 家长授权后生效，失败限流 |
| `task_template` | 任务模板 | 分类、积分、周期规则、版本 |
| `task_assignment` | 任务分配 | 快照模板内容、截止时间、迟交策略 |
| `task_attempt` | 孩子提交尝试 | 不可变，序号递增 |
| `task_review` | 审核记录 | 不可变，关联 attempt |
| `points_ledger` | 积分流水 | 唯一事实源，不可变 |
| `points_balance` | 余额投影 | 可重建，基于 ledger 聚合 |
| `prize` | 奖品主数据 | 价格、库存、启用状态 |
| `prize_snapshot` | 奖品历史快照 | 兑换时记录 |
| `blind_box_pool` | 盲盒奖池 | 启用状态、成本、availability_version |
| `blind_box_item` | 奖池项 | 奖品、权重、库存过滤 |
| `exchange` | 兑换记录 | 状态机、幂等键、快照 |
| `exchange_snapshot` | 兑换快照 | 奖品/盲盒信息、成本、概率 |
| `points_transaction` | 积分流水明细（冗余链路） | 与 `points_ledger` 同时写入 |
| `audit_log` | 审计日志 | 操作者、操作类型、对象、变更摘要、时间 |
| `backup_run` | 备份运行记录 | 时间、状态、保留策略 |
| `recovery_drill` | 恢复演练结果 | 时间、成功状态、RPO、RTO |

### 3.2 关键状态机

- **任务分配**：`PENDING` → `IN_PROGRESS` → `SUBMITTED` → `APPROVED`/`REJECTED` → `COMPLETED`/`CANCELLED`
- **提交尝试**：每次提交创建新的 `task_attempt`，状态为 `SUBMITTED`；审核后 `task_review` 记录结果。
- **兑换**：`PENDING_FULFILLMENT` → `FULFILLED`/`CANCELLED`/`REFUNDED`；取消后积分与库存原子回滚。

### 3.3 快照语义

- `task_assignment` 在创建时复制 `task_template` 的标题、描述、积分、分类，避免模板变更影响历史分配。
- `exchange_snapshot` 在兑换时记录奖品/盲盒的价格、库存、概率、版本，确保事后可追溯。
- `prize_snapshot` 在库存/价格变化时保留历史版本，用于审计和兑换历史展示。

## 4. 认证与授权

### 4.1 家长认证

- 首次初始化：通过本地维护命令创建首位管理员账号，设置密码。
- 登录：手机号 + 密码；短信验证码仅在 `deployment-operations` 配置服务商后可选启用。
- 会话：服务端 Redis 存储会话，HttpOnly Cookie 返回会话 ID；Cookie 设置 `SameSite=Lax`, `Secure`（生产环境）。
- 敏感写操作：请求头携带 CSRF token，与 session 绑定。

### 4.2 孩子认证

- 家长先在家长端授权设备（记录设备标识、家庭 ID）。
- 孩子在该设备选择档案并输入家庭 PIN（与 account 密码独立）。
- 失败 5 次后锁定 15 分钟，记录审计日志。
- 会话与家长会话隔离，权限仅限 `CHILD` 和所属家庭数据。

### 4.3 角色与授权

- 角色：`INSTANCE_ADMIN`, `PARENT`, `CHILD`
- 使用 Spring Security 方法级安全注解（`@PreAuthorize`）与自定义拦截器组合。
- `INSTANCE_ADMIN` 即使同时是 `PARENT`，也不得访问家庭业务数据；反之亦然。

## 5. API 设计

### 5.1 统一响应结构

```json
{
  "code": "EXCHANGE_IDEMPOTENCY_CONFLICT",
  "message": "...",
  "data": null,
  "request_id": "uuid"
}
```

### 5.2 错误码规范

- 按 capability 前缀：`AUTH_`, `FAMILY_`, `TASK_ASSIGNMENT_`, `TASK_SUBMISSION_`, `TASK_REVIEW_`, `POINTS_`, `PRIZE_`, `BLIND_BOX_`, `EXCHANGE_`, `INSTANCE_MANAGEMENT_`, `DEPLOYMENT_OPERATIONS_`。
- 通用错误码：`RATE_LIMITED`, `INTERNAL_ERROR`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `METHOD_NOT_ALLOWED`。
- 错误码完整列表以 `openspec/changes/core-features/specs/*/spec.md` 为准。

### 5.3 幂等机制

- 兑换创建：`POST /api/exchanges` 请求体包含 `idempotency_key`（UUID v4）。
- 服务端将孩子 ID + 键 + 规范化请求内容（JSON 字段排序）生成绑定记录。
- 相同键+相同请求：返回已存在兑换及结果；不同请求：返回 `EXCHANGE_IDEMPOTENCY_CONFLICT`。
- 键有效期：24 小时；未绑定成功键可被清理。

## 6. 并发与事务

### 6.1 兑换事务

积分检查、扣减、库存扣减、盲盒重检与抽取、兑换记录、快照、流水写入 MUST 在同一数据库事务中。任一失败全部回滚。

### 6.2 并发控制

- 积分余额：`SELECT ... FOR UPDATE` 或乐观锁（`version` 列）。
- 库存：`prize` 表行锁，扣减前重检 `available_stock > 0`。
- 兑换状态：数据库 `status` 约束与状态机校验，确保 `PENDING_FULFILLMENT` 只能转换为 `FULFILLED` 或 `CANCELLED` 一次。
- 双家长并发兑现：仅一条成功，另一条返回 `EXCHANGE_INVALID_STATE`。

### 6.3 盲盒版本

- `availability_version` = `SHA-256(排序后的盲盒池ID+候选项ID+权重+成本)`，十六进制，固定 64 字符。
- 孩子确认兑换时携带该版本；服务端在事务中重检版本是否一致，不一致返回 `BLIND_BOX_POOL_CHANGED`。
- 当请求同时携带已绑定幂等键和过期版本时，优先返回 `EXCHANGE_IDEMPOTENCY_CONFLICT`。

## 7. 前端设计

### 7.1 三端入口

- `/admin/*`：实例管理员（初始化、配置、账号、审计、健康、备份/恢复）。
- `/parent/*`：家长（家庭、任务、审核、积分、奖品、盲盒、兑换履约）。
- `/child/*`：孩子（任务看板、提交、积分、兑换、历史）。

### 7.2 共享能力

- 统一 API 客户端（错误码处理、自动刷新、重试、toast）。
- 角色守卫（未登录/无权限重定向）。
- 主题与响应式（CSS 变量、深色/浅色、≥320px 适配）。
- 无障碍：ARIA 标签、键盘导航、聚焦管理、减少动画偏好。

### 7.3 关键页面

- 家长：任务日历、任务分配表单、待审核队列、积分余额、奖品管理、盲盒池管理、兑换履约。
- 孩子：今日任务、提交弹窗、积分商城、盲盒确认、兑换历史。
- 管理员：初始化向导、系统配置、账号列表、审计日志、健康面板、备份状态。

## 8. 部署与运维

### 8.1 构建流程

1. 克隆仓库到 Windows/Linux 主机。
2. 运行 `./deploy/build.sh`：构建后端 JAR、前端产物、Docker 镜像。
3. 镜像标签：`mit-modelide-core-server:<version>`, `mit-modelide-core-web:<version>`, `mit-modelide-core-nginx:<version>`。

### 8.2 Docker Compose 组成

- `mit-modelide-core-mysql`: MySQL 8，数据卷持久化。
- `mit-modelide-core-redis`: Redis 7，会话与缓存。
- `mit-modelide-core-server`: Spring Boot 应用。
- `mit-modelide-core-nginx`: 静态前端 + API 反向代理。
- `mit-modelide-core-backup`: backup-sidecar，执行备份与演练。

### 8.3 备份与恢复

- 每日 02:00 执行 `mysqldump --single-transaction`。
- 保留策略：每天保留最近 7 天，每周保留最近 4 周，每月保留最近 3 个月。
- 备份状态写入 `backup_run` 表，供管理员查询。
- 恢复演练：`docker compose exec backup run-drill`，将备份恢复到临时库并验证，结果写入 `recovery_drill`。
- 实际 RPO = 当前时间 - 最近成功备份时间；超过 24 小时返回 `RPO_EXCEEDED` 警告。

### 8.4 升级与迁移

- 数据库迁移使用 Flyway，脚本位于 `server/common/src/main/resources/db/migration/`。
- 升级前自动备份；升级失败可回滚到上一版本镜像。
- 版本信息通过 `/api/health` 与管理员接口暴露。

## 9. 测试策略

### 9.1 单元测试

- JUnit 5 + Mockito，覆盖：
  - 状态机转换（任务、兑换）。
  - 错误码映射。
  - 积分计算与余额投影。
  - 盲盒概率归一化与版本哈希。
  - 密码/PIN 校验与限流。

### 9.2 集成测试

- Testcontainers 启动 MySQL + Redis。
- 覆盖：
  - 注册/登录/设备绑定。
  - 任务分配 → 提交 → 审核 → 积分发放。
  - 奖品兑换、盲盒兑换、幂等重试。
  - 并发兑换（双线程同时扣减库存/积分）。
  - 取消退款与库存恢复。
  - 双家长并发兑现同一兑换。
  - 管理员备份/演练状态查询。

### 9.3 E2E 测试

- Playwright，覆盖：
  - 家长创建任务模板并分配给孩子。
  - 孩子提交任务，家长审核通过。
  - 孩子兑换奖品，家长履约。
  - 管理员查看备份状态。
- 跨 Chrome/Firefox/WebKit 与移动端视口。

### 9.4 非功能测试

- 并发压力：100 并发兑换请求，验证无超卖、无重复扣减。
- 备份/恢复：模拟备份失败、恢复演练失败，验证告警与状态。
- 可访问性：axe-core 扫描关键页面。
- 安全：依赖扫描、凭据泄漏扫描、CSRF 测试。

## 10. 关键风险与缓解

| 风险 | 影响 | 缓解 |
|---|---|---|
| 单 change 范围大，实施周期长 | 难以验证，容易遗漏 | 按 tasks.md 阶段门禁拆分，每阶段先端到端跑通；优先 auth→family→task→points 闭环 |
| 盲盒概率与库存并发复杂 | 超卖、概率不一致 | availability_version 内容哈希 + 事务内重检 + 行锁；概率服务端计算 |
| 积分/兑换退款事务失败 | 数据不一致 | 同一 DB 事务；失败回滚；状态机约束 |
| 家庭隐私泄露 | 合规风险 | 最小化数据、审计日志、凭据环境注入、不默认遥测 |
| 备份失败不可知 | 数据丢失风险 | backup-sidecar 状态写入 DB；管理员接口展示；RPO 超标告警 |
| 前端三端复用与隔离复杂 | 构建产物大、权限泄漏 | 独立入口 + 共享包；路由守卫 + 角色守卫双层校验 |

## 11. 实施顺序建议

按 `tasks.md` 的阶段依赖，建议分六阶段实施：

1. **基础与认证**：数据库、Flyway、Redis、Spring Security、账号/会话/角色、初始化流程。
2. **家庭与孩子**：家庭、家长邀请、孩子档案、设备绑定、PIN。
3. **任务闭环**：任务模板、分配、快照、提交、审核、迟交策略、积分发放。
4. **积分与奖品**：积分流水、余额投影、奖品/盲盒资料与库存管理。
5. **兑换与履约**：兑换、幂等、盲盒抽取、履约、取消退款、历史查询。
6. **部署与运维**：Docker Compose、备份、恢复演练、健康检查、升级迁移、E2E 验收。

每个阶段结束后运行对应集成测试，未通过不得进入下一阶段。

## 12. 与 OpenSpec 的关系

- `openspec/changes/core-features/specs/*/spec.md` 是验收事实源。
- 本 Design Doc 中所有技术决策均服务于这些 spec，不引入新需求。
- 若实施中发现 spec 仍存歧义，必须回写 spec 并重新运行 `openspec validate core-features --strict`。
