---
change: core-features
design-doc: docs/superpowers/specs/2026-07-10-core-features-design.md
base-ref: 1ee61b63fa21919e0a5c4255ab694d90e5f55c2c
---

# CuteGoals 2.0 core-features 实施计划

> **产物语言**: zh-CN
> **关联文档**:
> - 任务边界：`openspec/changes/core-features/tasks.md`（共 95 项任务）
> - 技术设计：`docs/superpowers/specs/2026-07-10-core-features-design.md`
> - 验收事实源：`openspec/changes/core-features/specs/*/spec.md`（12 个 capability spec）
> **总阶段数**：10 阶段（工程基线 → 数据库 → 6 业务阶段 → Web 前端 → 部署运维 → 综合验证）

---

## 计划概览

本计划将 95 项任务按 capability 依赖关系划分为 10 个顺序阶段，每阶段包含若干可并行的子任务。阶段间存在严格的前置依赖——前一阶段必须通过集成测试门禁后方可进入下一阶段。

**任务标注规范**：
- 🔒 **安全敏感任务**：涉及凭据、密码、PIN、会话、CSRF、审计
- ⚡ **并发/幂等/事务边界任务**：涉及多请求竞争、幂等键、原子事务
- 🧪 **测试任务**：单元测试、集成测试、E2E 测试、非功能测试
- 标注格式：`[原任务编号] [capability] [优先级标记]`

**建议提交粒度**：每个独立子任务可作为一个 commit，commit message 格式：`{capability}: {简短描述} (task {任务编号})`。

---

## 阶段 0：工程基线与模块边界（7 项任务）

**目标**：建立 Maven 聚合工程、React 前端工程、统一配置分层、CI 基线和部署目录骨架。本阶段无业务逻辑，仅工程结构。

### 任务 0.1：建立 Server Maven 聚合工程
- **原任务编号**：1.1
- **capability**：design
- **目标**：创建 `server/` 目录下的聚合父 POM 及 `common`、`auth`、`family`、`task`、`points`、`prize`、`exchange`、`instance-management`、`web` 子模块，配置 Spring Boot 3.2.x + Java 21 + MyBatis-Plus 3.5.x + Flyway 10.x 依赖版本管理。
- **输入**：技术设计文档 §2.3；Maven 安装环境
- **输出**：可编译的 Maven 多模块工程，模块依赖图无循环
- **验收标准**：
  - `mvn compile` 全部模块通过
  - 模块依赖图符合设计：`web → (auth|family|task|points|prize|exchange|instance-management) → common`
- **依赖任务**：无
- **建议提交粒度**：1 个 commit（聚合 POM + 各模块 pom.xml）

### 任务 0.2：建立 Web 前端工程
- **原任务编号**：1.2
- **capability**：design
- **目标**：在 `web/` 目录下搭建 React 18 + TypeScript 5.x + Vite 5.x 工程，配置 ESLint、Prettier、Vitest、路径别名 (`@shared/`, `@admin/`, `@parent/`, `@child/`)。
- **输入**：技术设计文档 §2.4
- **输出**：`npm run lint`、`npm run test`、`npm run build` 全部通过的前端工程骨架
- **验收标准**：
  - `npm run build` 生成三入口构建产物（admin/parent/child）
  - TypeScript 严格模式，无 any 类型泄漏
- **依赖任务**：无
- **建议提交粒度**：1 个 commit（package.json + vite.config.ts + tsconfig + 共享配置）

### 任务 0.3：配置三角色路由与懒加载入口
- **原任务编号**：1.3
- **capability**：web-app
- **目标**：在 React SPA 中实现 `/admin/*`、`/parent/*`、`/child/*` 路由前缀、React.lazy 懒加载和角色级路由守卫占位。
- **输入**：技术设计文档 §2.4、§7.1；`web-app/spec.md` §路由入口
- **输出**：三角色路由分别可访问各自占位首页，不匹配角色返回 403 语义
- **验收标准**：
  - `/admin`、`/parent`、`/child` 各自渲染独立布局占位
  - 深层链接刷新不产生 404（Nginx `try_files` 回退配置）
- **依赖任务**：0.2
- **建议提交粒度**：1 个 commit（路由配置 + 守卫组件 + Nginx 配置片段）

### 任务 0.4：建立共享 API 客户端与错误码映射
- **原任务编号**：1.4
- **capability**：web-app
- **目标**：在 `shared/` 包中实现统一 API 客户端（基于 fetch/Axios），包含 Cookie 会话传递、CSRF token 注入、响应拦截器（统一错误码→toast）、请求重试策略。
- **输入**：技术设计文档 §5.1、§5.2、§7.2
- **输出**：可复用的 `apiClient`，契约测试覆盖 200/400/401/403/404/409/429 场景
- **验收标准**：
  - 自动附带 `X-CSRF-TOKEN` 请求头
  - 401 响应自动触发登出重定向
  - 错误码映射表包含设计文档 §5.2 全部前缀
- **依赖任务**：0.2
- **建议提交粒度**：1 个 commit（`shared/api/` + `shared/errors/`）

### 任务 0.5：建立统一配置分层与结构化日志
- **原任务编号**：1.5
- **capability**：design
- **目标**：在 `server/common/` 中实现配置分层加载（`application.yml` → `application-{env}.yml`）、`Asia/Shanghai` 默认时区、Logback 结构化日志（JSON 格式）、敏感字段脱敏（密码/PIN/令牌/手机号）。
- **输入**：技术设计文档 §2.1、§5 错误码规范
- **输出**：启动日志格式为 JSON，敏感字段显示 `***MASKED***`
- **验收标准**：
  - 配置加载测试覆盖覆盖规则
  - 日志脱敏测试：密码/PIN/令牌不出现于日志正文
- **依赖任务**：0.1
- **建议提交粒度**：1 个 commit（`common/src/main/resources/` + Logback 配置 + 脱敏工具类）

### 任务 0.6：建立 deploy 目录与管理脚本入口
- **原任务编号**：1.6
- **capability**：deployment-operations
- **目标**：创建 `deploy/` 目录骨架，包含 `build.sh`、`build.ps1`、Docker Compose 模板、环境变量 `.env.template` 以及帮助信息。
- **输入**：技术设计文档 §8.1、§8.2
- **输出**：`./deploy/build.sh --help` 输出所有子命令；`./deploy/build.ps1 -Help` 等效
- **验收标准**：
  - 脚本参数检查（缺少必填参数时提示而非静默失败）
- **依赖任务**：无
- **建议提交粒度**：1 个 commit

### 任务 0.7：建立 CI 基线流水线
- **原任务编号**：1.7
- **capability**：design
- **目标**：配置 CI（GitHub Actions 或等效），串联：后端 `mvn verify`、前端 `npm run lint && npm run test`、OpenSpec 验证、Docker 镜像构建检查。
- **输入**：无
- **输出**：push 到主分支触发全流水线，PR 触发增量检查
- **验收标准**：
  - 干净环境全流水线通过（所有阶段绿色）
- **依赖任务**：0.1、0.2、0.6
- **建议提交粒度**：1 个 commit（`.github/workflows/ci.yml`）
- 🧪 **测试任务**

---

## 阶段 1：数据库模型与迁移（8 项任务）

**目标**：通过 Flyway 迁移脚本建立全部核心实体表、唯一约束、索引和外键。本阶段可与阶段 0 并行。**门禁**：所有迁移脚本在空 MySQL 8 上可重复执行，schema 版本一致。

### 任务 1.1：接入 Flyway 并创建空库基线
- **原任务编号**：2.1
- **capability**：deployment-operations
- **目标**：在 `server/common/src/main/resources/db/migration/` 配置 Flyway，创建 `V1__baseline.sql` 空基线。
- **验收标准**：`mvn flyway:migrate` 在空 MySQL 8 上执行成功，`flyway_schema_history` 表创建，版本一致
- **依赖任务**：0.1
- **建议提交粒度**：1 个 commit（Flyway 配置 + V1 基线）

### 任务 1.2：创建认证相关表 🔒
- **原任务编号**：2.2
- **capability**：auth
- **目标**：创建 `account`、`role_binding`、`initialization_token`、`session`、`refresh_token`、`login_rate_limit` 等表。
- **验收标准**：
  - `account.phone` 唯一约束
  - `initialization_token` 单次消费约束（token + consumed 复合索引）
  - `refresh_token` 家族链撤销支持
- **依赖任务**：1.1
- **建议提交粒度**：1 个 commit（V2 迁移脚本）
- 🔒 **安全敏感任务**

### 任务 1.3：创建家庭与成员表
- **原任务编号**：2.3
- **capability**：family
- **目标**：创建 `family`（单实例唯一）、`family_member`、`child_profile`、`parent_invitation`、`device_binding` 表。
- **验收标准**：
  - `family` 单实例唯一约束
  - `family_member` 最后家长移除保护（至少一个 active parent）
  - `device_binding` 凭据不可从 API 读取
- **依赖任务**：1.1
- **建议提交粒度**：1 个 commit

### 任务 1.4：创建任务模板与分配表
- **原任务编号**：2.4
- **capability**：task-template、task-assignment
- **目标**：创建 `task_template`、`task_difficulty`、`task_recurrence_rule`、`task_assignment`（含快照字段）、`task_assignment_snapshot` 表。
- **验收标准**：
  - 分配快照字段完整（模板名称、难度名称、奖励积分、分类）
  - 周期发生键唯一约束
- **依赖任务**：1.1
- **建议提交粒度**：1 个 commit

### 任务 1.5：创建提交与审核表
- **原任务编号**：2.5
- **capability**：task-review
- **目标**：创建 `task_attempt`、`task_review` 表，均为不可变设计（无 UPDATE 仅有 INSERT）。
- **验收标准**：
  - `task_attempt` 按孩子 + 分配递增序号
  - `task_review` 同一 attempt 最多一条审核记录的唯一约束
- **依赖任务**：1.1
- **建议提交粒度**：1 个 commit

### 任务 1.6：创建积分流水与余额表
- **原任务编号**：2.6
- **capability**：points
- **目标**：创建 `points_ledger`（不可变流水）、`points_balance`（余额投影，含 version 乐观锁列）表。
- **验收标准**：
  - `points_ledger.business_ref` 在当前家庭内唯一
  - `points_balance.version` 乐观锁列
  - 退款引用原消费（`refund_source_id`）唯一约束
- **依赖任务**：1.1
- **建议提交粒度**：1 个 commit
- ⚡ **并发/幂等/事务边界**

### 任务 1.7：创建奖品、盲盒与兑换表
- **原任务编号**：2.7
- **capability**：prize、blind-box、exchange
- **目标**：创建 `prize`、`blind_box_pool`、`blind_box_item`、`exchange`、`exchange_snapshot` 表。
- **验收标准**：
  - `blind_box_item.weight` 正整数约束
  - `exchange.idempotency_key` 在孩子 + 操作范围内唯一
  - `exchange_snapshot` 记录兑换时全部快照字段
- **依赖任务**：1.1
- **建议提交粒度**：1 个 commit
- ⚡ **并发/幂等/事务边界**

### 任务 1.8：创建实例管理与审计表
- **原任务编号**：2.8
- **capability**：instance-management
- **目标**：创建 `instance_config`、`audit_log`、`backup_run`、`recovery_drill` 表及相应索引。
- **验收标准**：
  - `audit_log` 按操作者、事件类型、时间范围查询的复合索引
  - `instance_config` 密钥字段脱敏标记
- **依赖任务**：1.1
- **建议提交粒度**：1 个 commit
- 🔒 **安全敏感任务**

---

## 阶段 2：初始化、认证与家庭成员（12 项任务）

**目标**：实现实例初始化→家长登录→会话管理→家庭成员→孩子设备绑定的完整认证闭环。**门禁**：auth + family 集成测试通过。

### Phase 2-A：认证基础设施（任务 2.1-2.7）

#### 任务 2.1：首次启动初始化令牌 🔒⚡
- **原任务编号**：3.1
- **capability**：auth
- **优先级**：🔒⚡
- **目标**：实现一次性初始化令牌的生成、过期验证和单次消费逻辑。令牌在 `deploy/` 本地脚本生成，有效期 24 小时。
- **输入**：`auth/spec.md` §首次启动身份初始化
- **输出**：`POST /api/auth/initialize` 端点，消费令牌并创建首位用户
- **验收标准**：
  - 有效令牌 → 创建 INSTANCE_ADMIN + PARENT 双角色用户
  - 已使用/过期令牌 → `INITIALIZATION_NOT_ALLOWED`
  - 并发提交同一令牌 → 仅一个成功，其余返回 `INITIALIZATION_NOT_ALLOWED`
  - 日志不出现令牌明文
- **依赖任务**：1.2（auth 表）
- **建议提交粒度**：2 个 commits（令牌服务 + 初始化端点）

#### 任务 2.2：实例初始化与家庭创建 ⚡
- **原任务编号**：3.2
- **capability**：auth、family
- **优先级**：⚡
- **目标**：在单数据库事务中完成：消费初始化令牌 → 创建 account（双角色）→ 创建唯一 family → 创建 family_member。
- **输入**：`auth/spec.md`、`family/spec.md` §唯一家庭
- **输出**：`POST /api/auth/initialize` 完成端到端初始化
- **验收标准**：
  - 并发初始化请求：仅一个成功
  - 失败时无残留数据（family 未创建或 account 孤立的场景）
  - 实例标记为 `INITIALIZED`
- **依赖任务**：2.1、1.3
- **建议提交粒度**：1 个 commit（事务服务）
- ⚡ **并发/幂等/事务边界**

#### 任务 2.3：手机号+密码登录 🔒
- **原任务编号**：3.3
- **capability**：auth
- **优先级**：🔒
- **目标**：实现手机号规范化（中国大陆规则）、密码 bcrypt 校验、不枚举账号的失败响应。
- **输入**：`auth/spec.md` §家长手机号与密码登录
- **输出**：`POST /api/auth/login` 端点
- **验收标准**：
  - 正确凭据 → 返回 session cookie
  - 错误凭据/不存在账号/已停用账号 → 统一返回 `AUTHENTICATION_FAILED`，响应结构完全一致
  - 不依赖短信服务
- **依赖任务**：2.2
- **建议提交粒度**：2 个 commits（登录服务 + Controller）
- 🔒 **安全敏感任务**

#### 任务 2.4：可插拔短信认证接口 🔒
- **原任务编号**：3.4
- **capability**：auth
- **优先级**：🔒
- **目标**：定义短信认证 SPI 接口，提供空实现（默认关闭），仅在配置完整且显式启用时激活。
- **输入**：`auth/spec.md` §可选短信验证码登录
- **输出**：`SmsAuthProvider` 接口 + `NoOpSmsAuthProvider` 默认实现
- **验收标准**：
  - 未配置时 `/api/auth/sms/send` 返回 `SMS_LOGIN_NOT_CONFIGURED`
  - 密码登录不受影响
- **依赖任务**：2.3
- **建议提交粒度**：1 个 commit

#### 任务 2.5：短期访问令牌与刷新令牌轮换 🔒
- **原任务编号**：3.5
- **capability**：auth
- **优先级**：🔒
- **目标**：实现 JWT 短期访问令牌（15 分钟）+ Redis 刷新令牌（7 天），HttpOnly Secure SameSite Cookie 交付，CSRF token 双 cookie 模式。
- **输入**：`auth/spec.md` §短期访问令牌与刷新令牌轮换
- **输出**：`POST /api/auth/refresh` 端点，轮换刷新令牌
- **验收标准**：
  - 有效刷新 → 新访问令牌 + 新刷新令牌，旧刷新令牌立即可撤销
  - 重放已轮换刷新令牌 → `REFRESH_TOKEN_REUSED`，撤销整个会话链
  - Cookie 为 HttpOnly，JS 不可读
- **依赖任务**：2.3
- **建议提交粒度**：2 个 commits（令牌服务 + 刷新端点）
- 🔒 **安全敏感任务**

#### 任务 2.6：登出、密码变更与会话撤销 🔒
- **原任务编号**：3.6
- **capability**：auth
- **优先级**：🔒
- **目标**：实现登出撤销当前会话、密码变更撤销全部账号会话、账号停用撤销全部会话。
- **输入**：`auth/spec.md` §会话撤销
- **输出**：`POST /api/auth/logout`、`PUT /api/auth/password` 端点
- **验收标准**：
  - 登出后刷新令牌立即失效 → `SESSION_REVOKED`
  - 密码变更后全部会话失效 → 必须重新登录
- **依赖任务**：2.5
- **建议提交粒度**：1 个 commit
- 🔒 **安全敏感任务**

#### 任务 2.7：本地管理员恢复命令 🔒
- **原任务编号**：3.7
- **capability**：auth、deployment-operations
- **优先级**：🔒
- **目标**：实现 `deploy/recover-admin.sh` 脚本，在实例主机本地生成一次性恢复凭据，用于重置首位管理员密码。
- **输入**：`auth/spec.md`、`instance-management/spec.md` §首位管理员本地凭据恢复
- **输出**：CLI 命令 + `POST /api/auth/recover` 端点
- **验收标准**：
  - 仅本地主机可触发恢复（非远程 API）
  - 恢复凭据单次使用，过期 15 分钟
  - 成功后旧密码/会话全部失效
  - 不依赖厂商网络
- **依赖任务**：2.3、0.6
- **建议提交粒度**：2 个 commits（恢复服务 + CLI 脚本）
- 🔒 **安全敏感任务**

### Phase 2-B：家庭与成员（任务 2.8-2.12）

#### 任务 2.8：家庭信息查看与编辑
- **原任务编号**：3.8
- **capability**：family
- **目标**：实现家长查看/编辑当前家庭信息的 API。编辑字段白名单校验，变更审计。
- **输入**：`family/spec.md` §家庭信息查看与编辑
- **输出**：`GET /api/family`、`PUT /api/family` 端点
- **验收标准**：
  - 返回内容不包含密码/PIN/令牌/设备绑定秘密
  - 白名单外字段提交 → `VALIDATION_FAILED`
  - 变更记录审计事件
- **依赖任务**：2.2、1.3
- **建议提交粒度**：1 个 commit

#### 任务 2.9：家长邀请 🔒
- **原任务编号**：3.9
- **capability**：family
- **优先级**：🔒
- **目标**：实现家长邀请的创建、接受、拒绝、撤销、自动过期和幂等处理。
- **输入**：`family/spec.md` §限时家长邀请、§邀请接受/拒绝/撤销与过期
- **输出**：`POST /api/family/invitations`、`POST /api/family/invitations/{id}/accept` 等端点
- **验收标准**：
  - 邀请秘密具有足够随机性，不落日志
  - 接受邀请时原子创建 `PARENT` 角色 + `family_member`
  - 过期/已撤销邀请 → `INVITATION_NOT_AVAILABLE`
  - 幂等重试不重复创建
- **依赖任务**：2.8
- **建议提交粒度**：2 个 commits（邀请服务 + Controller）
- 🔒 **安全敏感任务**（邀请秘密处理）

#### 任务 2.10：孩子档案管理 🔒
- **原任务编号**：3.10
- **capability**：family
- **优先级**：🔒
- **目标**：实现孩子档案的创建、编辑、停用、导出和删除匿名化。
- **输入**：`family/spec.md` §最小化孩子档案、§孩子删除与历史匿名化
- **输出**：`POST /api/family/children`、`PUT /api/family/children/{id}`、`DELETE /api/family/children/{id}` 端点
- **验收标准**：
  - 最小化数据：昵称+头像+PIN，不要求手机号/精确生日
  - 删除后历史记录显示"已删除孩子"匿名标签
  - 删除不可逆确认
- **依赖任务**：2.8
- **建议提交粒度**：2 个 commits（档案管理 + 删除匿名化）
- 🔒 **安全敏感任务**（孩子隐私）

#### 任务 2.11：成员移除与家长保护 ⚡
- **原任务编号**：3.11
- **capability**：family、instance-management
- **优先级**：⚡
- **目标**：实现家长成员移除、退出家庭的 API，保护最后一位有效家长和实例管理员不被移除。
- **输入**：`family/spec.md` §家长成员生命周期
- **输出**：`DELETE /api/family/members/{id}`、`POST /api/family/members/me/leave` 端点
- **验收标准**：
  - 移除最后一位家长 → `LAST_ACTIVE_PARENT`
  - 并发移除多位家长 → 仅保留一位
  - 被移除家长全部会话撤销
- **依赖任务**：2.8
- **建议提交粒度**：1 个 commit
- ⚡ **并发/幂等/事务边界**

#### 任务 2.12：孩子设备绑定与 PIN 登录 🔒
- **原任务编号**：3.12
- **capability**：auth、family
- **优先级**：🔒
- **目标**：实现家长授权设备绑定 → 孩子在已绑定设备上选择档案 → 输入 PIN → 建立孩子会话。PIN 失败 5 次锁定 15 分钟。
- **输入**：`auth/spec.md` §已绑定设备上的孩子 PIN 登录、`family/spec.md` §家长授权的孩子设备绑定
- **输出**：
  - `POST /api/family/devices/bind`（家长授权）
  - `POST /api/auth/child/login`（孩子 PIN 登录）
- **验收标准**：
  - 未绑定设备 → `DEVICE_NOT_AUTHORIZED`
  - PIN 连续 5 次失败 → `PIN_LOCKED`（15 分钟），不锁其他档案
  - 撤销绑定 → 孩子会话立即失效
  - 绑定凭据不可从 API/日志读取
- **依赖任务**：2.3、2.5、2.10
- **建议提交粒度**：2 个 commits（设备绑定 + PIN 登录）
- 🔒 **安全敏感任务**

---

## 阶段 3：任务模板与任务分配（10 项任务）

**目标**：实现任务模板 CRUD + 周期规则 + 难度管理 → 单次/批量/周期任务分配及其快照固化。**门禁**：任务模板与分配集成测试通过。

### 任务 3.1：任务模板创建与字段验证 🔒
- **原任务编号**：4.1
- **capability**：task-template
- **优先级**：🔒（权限校验）
- **目标**：实现模板创建、更新、分页查询、分类筛选和启停。名称 1-100 字符，分类 1-50 字符，说明最长 2000 字符。
- **输入**：`task-template/spec.md` §创建任务模板与字段验证
- **输出**：`POST /api/task-templates`、`PUT /api/task-templates/{id}`、`GET /api/task-templates` 端点
- **验收标准**：
  - 非法字段 → `TASK_TEMPLATE_VALIDATION_FAILED`
  - 孩子访问 → `TASK_TEMPLATE_FORBIDDEN`
  - 分页默认 20 条，按更新时间降序
- **依赖任务**：1.4、2.8
- **建议提交粒度**：2 个 commits（模板 CRUD + 分页查询）

### 任务 3.2：周期规则配置
- **原任务编号**：4.2
- **capability**：task-template
- **目标**：实现可选周期规则 `DAILY`/`WEEKDAYS`/`WEEKENDS`/`CUSTOM_WEEKDAYS`（ISO 1-7），`Asia/Shanghai` 时区校验。
- **输入**：`task-template/spec.md` §可选周期规则与本地日历语义
- **验收标准**：
  - 空 `CUSTOM_WEEKDAYS` → `TASK_TEMPLATE_INVALID_RECURRENCE`
  - 星期 0 或 8 → 拒绝
  - 模板无周期规则仍可用于人工分配
- **依赖任务**：3.1
- **建议提交粒度**：1 个 commit

### 任务 3.3：难度与奖励积分管理
- **原任务编号**：4.3
- **capability**：task-template
- **目标**：实现每个模板 1-20 个难度配置，每个难度名称（1-50 字符）、唯一展示顺序、奖励积分（1-1,000,000 正整数）。
- **输入**：`task-template/spec.md` §配置多个难度与正整数奖励
- **验收标准**：
  - 零/负数/小数积分 → `TASK_TEMPLATE_VALIDATION_FAILED`
  - 停用最后一个启用难度 → `TASK_TEMPLATE_REQUIRES_ACTIVE_DIFFICULTY`
  - 被历史引用的难度禁止物理删除
- **依赖任务**：3.1
- **建议提交粒度**：1 个 commit

### 任务 3.4：模板软删除与历史保护 ⚡
- **原任务编号**：4.4
- **capability**：task-template
- **优先级**：⚡（版本冲突）
- **目标**：实现模板软删除、并发版本冲突检测、已删除模板不用于新分配。
- **输入**：`task-template/spec.md` §更新模板仅影响未来分配、§停用与恢复模板、§删除模板不得破坏历史
- **验收标准**：
  - 并发更新版本冲突 → `TASK_TEMPLATE_VERSION_CONFLICT`
  - 已删除/停用模板分配 → `TASK_TEMPLATE_INACTIVE`
  - 历史分配不受模板删除影响
- **依赖任务**：3.1
- **建议提交粒度**：2 个 commits（软删除 + 版本控制）
- ⚡ **并发/幂等/事务边界**

### 任务 3.5：单次任务分配与快照 ⚡
- **原任务编号**：4.5
- **capability**：task-assignment
- **优先级**：⚡
- **目标**：实现人工单次分配，在创建时原子固化模板名称/难度/积分/截止时间/迟交策略快照。
- **输入**：`task-assignment/spec.md` §人工单次分配与数据快照
- **输出**：`POST /api/task-assignments` 端点
- **验收标准**：
  - 模板后续修改不妨碍已有分配
  - 截止时间不得早于请求时刻
  - 初始状态 `PENDING`
- **依赖任务**：3.1、1.4
- **建议提交粒度**：1 个 commit
- ⚡ **并发/幂等/事务边界**

### 任务 3.6：批量分配与幂等键 ⚡
- **原任务编号**：4.6
- **capability**：task-assignment
- **优先级**：⚡
- **目标**：实现批量分配（一个或多个孩子 + 日期范围），幂等键防重。日期范围最长 366 个本地日，包含两端。
- **输入**：`task-assignment/spec.md` §人工创建请求幂等、§原子批量分配
- **输出**：`POST /api/task-assignments/batch` 端点
- **验收标准**：
  - 相同幂等键+相同内容 → 返回原结果，不创建重复
  - 相同幂等键+不同内容 → `TASK_ASSIGNMENT_IDEMPOTENCY_CONFLICT`
  - 任一项目无效 → 整体回滚
- **依赖任务**：3.5
- **建议提交粒度**：1 个 commit
- ⚡ **并发/幂等/事务边界**

### 任务 3.7：周期任务滚动生成 ⚡
- **原任务编号**：4.7
- **capability**：task-assignment
- **优先级**：⚡
- **目标**：根据模板周期规则按日期范围生成分配，使用唯一业务键（家庭+孩子+模板+本地日期）防重。
- **输入**：`task-assignment/spec.md` §周期生成必须确定且幂等
- **输出**：`POST /api/task-assignments/generate-recurring` 端点
- **验收标准**：
  - 重跑相同范围 → 不重复创建
  - 并发生成同一发生项 → 唯一业务键保证仅一条
  - 停用模板不再生成新分配
- **依赖任务**：3.2、3.6
- **建议提交粒度**：1 个 commit
- ⚡ **并发/幂等/事务边界**

### 任务 3.8：迟交策略管理
- **原任务编号**：4.8
- **capability**：task-assignment
- **目标**：实现家庭默认迟交策略（`ALLOW`/`REJECT`）+ 单任务显式覆盖。
- **输入**：`task-assignment/spec.md` §截止时间、逾期标记与迟交策略
- **验收标准**：
  - 分配给创建时固化家庭默认策略
  - 单条覆盖不影响其他分配
  - 家庭默认值变更仅影响新分配
- **依赖任务**：3.5
- **建议提交粒度**：1 个 commit

### 任务 3.9：日历查询与逾期派生
- **原任务编号**：4.9
- **capability**：task-assignment
- **目标**：实现按日/月/孩子筛选的日历查询，`overdue` 为派生标记（严格晚于截止时间为 true）。
- **输入**：`task-assignment/spec.md` §分页列表与日历查询
- **验收标准**：
  - `Asia/Shanghai` 日期边界正确
  - 月历按本地自然日聚合各状态计数
  - 日期范围最长 366 天
- **依赖任务**：3.5
- **建议提交粒度**：1 个 commit

### 任务 3.10：分配取消与审批保护 ⚡
- **原任务编号**：4.10
- **capability**：task-assignment
- **优先级**：⚡（批准与取消并发）
- **目标**：实现未批准分配的取消（记录原因、保留历史），已批准不可取消。
- **输入**：`task-assignment/spec.md` §取消分配必须保留审计与历史
- **验收标准**：
  - 已批准取消 → `TASK_ASSIGNMENT_ALREADY_APPROVED`
  - 批准与取消并发 → 仅一个成功
  - 取消标记独立于审核状态，保留提交历史
- **依赖任务**：3.5
- **建议提交粒度**：1 个 commit
- ⚡ **并发/幂等/事务边界**

---

## 阶段 4：提交审核与积分账本（9 项任务）

**目标**：实现孩子提交→家长审核→积分发放的完整闭环，以及积分流水与余额投影。**门禁**：task→points 闭环集成测试通过。

### 任务 4.1：孩子任务提交与不可变尝试 ⚡
- **原任务编号**：5.1
- **capability**：task-review
- **优先级**：⚡
- **目标**：实现孩子提交任务（说明 ≤2000 字符，佐证 ≤10 个每项 ≤500 字符），创建不可变 `task_attempt`。
- **输入**：`task-review/spec.md` §每次提交形成不可覆盖的尝试
- **输出**：`POST /api/task-review/submissions` 端点
- **验收标准**：
  - 非本人任务 → `TASK_REVIEW_FORBIDDEN`
  - 重复提交已 `SUBMITTED` 任务 → `TASK_REVIEW_INVALID_STATE`
  - 非法内容 → `TASK_SUBMISSION_VALIDATION_FAILED`
- **依赖任务**：3.5、1.5
- **建议提交粒度**：1 个 commit
- ⚡ **并发/幂等/事务边界**

### 任务 4.2：提交请求幂等 ⚡
- **原任务编号**：（隐含于 5.1 的幂等要求）
- **capability**：task-review
- **优先级**：⚡
- **目标**：提交请求携带客户端请求标识（1-128 字符），相同幂等键+相同内容返回原结果。
- **验收标准**：
  - 重试返回同一 attempt
  - 相同键不同内容 → `TASK_SUBMISSION_IDEMPOTENCY_CONFLICT`
- **依赖任务**：4.1
- **建议提交粒度**：与 4.1 合并提交
- ⚡ **并发/幂等/事务边界**

### 任务 4.3：截止边界与迟交拒绝
- **原任务编号**：5.2（部分）
- **capability**：task-review
- **目标**：提交时校验迟交策略，`REJECT` 策略下逾期提交返回 `TASK_SUBMISSION_LATE_NOT_ALLOWED`。
- **输入**：`task-review/spec.md` §截止边界与迟交拒绝
- **验收标准**：
  - 恰好等于截止时间 → 按时
  - 策略 `REJECT` + 逾期 → 拒绝，不创建 attempt
  - 策略 `ALLOW` + 逾期 → 接受，标记迟交
- **依赖任务**：4.1、3.8
- **建议提交粒度**：1 个 commit

### 任务 4.4：家长驳回与强制原因
- **原任务编号**：5.2（审核部分）
- **capability**：task-review
- **目标**：实现家长驳回，强制非空原因（1-1000 字符），创建不可变驳回记录。
- **输入**：`task-review/spec.md` §审核决定形成不可覆盖的历史
- **验收标准**：
  - 驳回缺原因 → `TASK_REVIEW_REASON_REQUIRED`
  - 驳回后不发积分
  - 审核记录不可变
- **依赖任务**：4.1、1.5
- **建议提交粒度**：1 个 commit

### 任务 4.5：驳回后重新提交
- **原任务编号**：5.3
- **capability**：task-review
- **目标**：`REJECTED` 状态保持可观察，孩子修改后创建下一序号 attempt，旧驳回历史保留。
- **输入**：`task-review/spec.md` §驳回保持可观察并允许修改后再提交
- **验收标准**：
  - 新 attempt 序号递增，状态恢复 `SUBMITTED`
  - 旧驳回记录不可覆盖
  - 批准新 attempt 不覆盖旧驳回
- **依赖任务**：4.4、4.1
- **建议提交粒度**：1 个 commit

### 任务 4.6：批准与积分发放同事务 ⚡
- **原任务编号**：5.4
- **capability**：task-review、points
- **优先级**：⚡
- **目标**：批准当前 attempt 时，在同一事务中：创建 `APPROVED` 审核记录 + `EARN` 流水（金额来自分配快照）+ 更新余额。
- **输入**：`task-review/spec.md` §批准与积分发放必须同事务、`points/spec.md` §任务批准仅按快照获得一次积分
- **验收标准**：
  - 使用分配快照奖励积分，不取模板当前值
  - 任一步失败 → 整体回滚，任务保持 `SUBMITTED`
  - 积分流水 `business_ref` 指向被批准 attempt
- **依赖任务**：4.1、1.6
- **建议提交粒度**：1 个 commit
- ⚡ **并发/幂等/事务边界**

### 任务 4.7：同一提交只允许一次成功审核 ⚡
- **原任务编号**：5.5
- **capability**：task-review、points
- **优先级**：⚡
- **目标**：审核请求携带审核请求标识，使用一致性控制保证同一 attempt 最多一个成功决定。
- **输入**：`task-review/spec.md` §同一提交只允许一次成功审核
- **验收标准**：
  - 双家长并发审核 → 仅一个成功，另一个 `TASK_REVIEW_ALREADY_DECIDED`
  - 仅一笔 `EARN` 流水
  - 审核过期 attempt → `TASK_REVIEW_STALE_ATTEMPT`
- **依赖任务**：4.6
- **建议提交粒度**：1 个 commit
- ⚡ **并发/幂等/事务边界**

### 任务 4.8：积分流水与余额投影 ⚡
- **原任务编号**：5.6
- **capability**：points
- **优先级**：⚡
- **目标**：实现 `EARN`/`SPEND`/`REFUND`/`ADJUST` 四种不可变流水 + 余额投影重建。乐观锁保护余额更新。
- **输入**：`points/spec.md` §不可变流水是积分事实源、§余额投影不得为负且写入必须一致
- **验收标准**：
  - 余额 = 按时间顺序应用流水方向
  - `SELECT ... FOR UPDATE` 或乐观锁防并发
  - 余额为负拒绝 → `POINTS_INSUFFICIENT_BALANCE`
- **依赖任务**：1.6
- **建议提交粒度**：1 个 commit
- ⚡ **并发/幂等/事务边界**

### 任务 4.9：家长手工调整与审计 🔒
- **原任务编号**：5.7
- **capability**：points
- **优先级**：🔒
- **目标**：家长对当前家庭孩子发起 `ADJUST`（正/负整数，绝对值 ≤1,000,000,000），强制原因（1-500 字符）+ 唯一业务引用。
- **输入**：`points/spec.md` §家长手工正负调整必须有原因与审计
- **验收标准**：
  - 孩子发起 → `POINTS_FORBIDDEN`
  - 负调整致负余额 → `POINTS_INSUFFICIENT_BALANCE`
  - 缺原因 → `POINTS_ADJUST_REASON_REQUIRED`
- **依赖任务**：4.8
- **建议提交粒度**：1 个 commit
- 🔒 **安全敏感任务**（审计记录）

### 任务 4.10：分页查询余额、流水与家庭汇总
- **原任务编号**：5.8、5.9（部分）
- **capability**：points
- **目标**：实现孩子个人流水查询、家长家庭汇总查询，支持类型/日期/孩子筛选，按发生时间降序。
- **输入**：`points/spec.md` §分页查询余额、流水与家庭汇总
- **验收标准**：
  - 孩子仅查看自己数据
  - 家庭汇总包含期初/期末余额 + 四类流水分项
  - 日期边界使用 `Asia/Shanghai` 本地自然日
- **依赖任务**：4.8
- **建议提交粒度**：1 个 commit

---

## 阶段 5：奖品、盲盒与兑换履约（11 项任务）

**目标**：实现奖品/盲盒管理、孩子兑换（原子事务+幂等+盲盒抽取）、家长履约与取消退款。**门禁**：prize→blind-box→exchange→points 闭环集成测试通过。

### 任务 5.1：奖品 CRUD 与字段校验
- **原任务编号**：6.1
- **capability**：prize
- **目标**：实现奖品创建、更新、分页查询、启停。`points_cost` 正整数，`stock` 非负整数。
- **输入**：`prize/spec.md` §奖品资料与数值边界
- **验收标准**：
  - `points_cost=0` → `PRIZE_INVALID_POINTS_COST`
  - 负数库存 → `PRIZE_INVALID_STOCK`
  - 孩子仅可见启用且库存 >0 的奖品
- **依赖任务**：1.7、2.8
- **建议提交粒度**：2 个 commits（管理 API + 孩子查询 API）

### 任务 5.2：库存调整与历史保护
- **原任务编号**：6.2
- **capability**：prize
- **目标**：实现库存调整、软删除及引用保护。被兑换引用的奖品不可物理删除。
- **输入**：`prize/spec.md` §奖品删除与历史完整性
- **验收标准**：
  - 软删除后库存>0仍不可兑换
  - 取消退款时恢复停用/删除奖品的库存（不重新展示）
- **依赖任务**：5.1
- **建议提交粒度**：1 个 commit

### 任务 5.3：盲盒奖池 CRUD
- **原任务编号**：6.3
- **capability**：blind-box
- **目标**：实现奖池创建、更新、`cost_points` 正整数、启用/停用。奖池至少含一个有效奖品项。
- **输入**：`blind-box/spec.md` §盲盒奖池资料与生命周期
- **验收标准**：
  - 空奖池 → `BLIND_BOX_EMPTY_POOL`
  - `cost_points=0` → `BLIND_BOX_INVALID_COST`
  - 全部奖品不可用时对孩子隐藏
- **依赖任务**：5.1、1.7
- **建议提交粒度**：1 个 commit

### 任务 5.4：奖品项与权重管理
- **原任务编号**：6.4
- **capability**：blind-box
- **目标**：实现奖池内奖品项管理，`weight` 正整数，同一奖池内奖品不重复。权重不要求合计 100。
- **输入**：`blind-box/spec.md` §奖品项与相对权重约束
- **验收标准**：
  - `weight=0` → `BLIND_BOX_INVALID_WEIGHT`
  - 重复奖品 → `BLIND_BOX_DUPLICATE_PRIZE`
  - 整次配置写入失败回滚
- **依赖任务**：5.3
- **建议提交粒度**：1 个 commit

### 任务 5.5：有效候选集与概率归一化
- **原任务编号**：6.5
- **capability**：blind-box
- **目标**：实现过滤缺货/停用/删除物品，归一化概率计算，生成 `availability_version`（SHA-256 内容哈希）。
- **输入**：`blind-box/spec.md` §当前有效候选集、§候选奖品与有效概率披露
- **验收标准**：
  - 概率 = `weight / sum(有效项weights)`
  - `availability_version` 确定性与固定长度
  - 孩子可查询候选概率和版本
- **依赖任务**：5.4
- **建议提交粒度**：1 个 commit

### 任务 5.6：加权随机抽取
- **原任务编号**：6.6
- **capability**：blind-box
- **目标**：实现可注入随机源的加权抽取算法，支持确定性测试。
- **输入**：`blind-box/spec.md` §加权随机抽取
- **验收标准**：
  - 确定性区间测试覆盖边界
  - 统计测试声明样本量与容差
  - 抽取结果必为恰好一个有效项
- **依赖任务**：5.5
- **建议提交粒度**：1 个 commit
- 🧪 **测试任务**（含统计测试）

### 任务 5.7：明牌兑换原子事务 ⚡
- **原任务编号**：6.7
- **capability**：exchange、points、prize
- **优先级**：⚡
- **目标**：在同一事务中完成：积分校验扣减 + 库存扣减 + 兑换记录创建 + `SPEND` 流水 + 快照。
- **输入**：`exchange/spec.md` §兑换原子创建与不可变快照
- **验收标准**：
  - 余额/库存不足 → 整体回滚，不残留部分数据
  - `exchange_snapshot` 记录全部快照字段
- **依赖任务**：5.1、4.8
- **建议提交粒度**：1 个 commit
- ⚡ **并发/幂等/事务边界**

### 任务 5.8：盲盒兑换原子事务 ⚡
- **原任务编号**：6.8
- **capability**：exchange、points、blind-box
- **优先级**：⚡
- **目标**：在同一事务中完成：`availability_version` 重检 + 有效候选过滤 + 抽取 + 积分扣减 + 库存扣减 + 快照。
- **输入**：`exchange/spec.md` §盲盒兑换、`blind-box/spec.md` §加权随机抽取
- **验收标准**：
  - 版本过期 → `BLIND_BOX_POOL_CHANGED`
  - 并发争抢唯一奖品 → 至多一个成功，另一个返回新候选集
  - 无有效项 → 回滚，`BLIND_BOX_UNAVAILABLE`
- **依赖任务**：5.7、5.6
- **建议提交粒度**：1 个 commit
- ⚡ **并发/幂等/事务边界**

### 任务 5.9：兑换幂等键 ⚡
- **原任务编号**：6.9
- **capability**：exchange
- **优先级**：⚡
- **目标**：实现兑换幂等键：孩子 ID + 键 + 规范化请求内容绑定，24 小时有效期。
- **输入**：`exchange/spec.md` §客户端幂等键
- **验收标准**：
  - 相同键+相同请求 → 返回原兑换，不重复扣减
  - 相同键+不同请求 → `EXCHANGE_IDEMPOTENCY_CONFLICT`
  - 幂等键优先级高于版本过期的错误码
  - 缺幂等键 → `EXCHANGE_IDEMPOTENCY_KEY_REQUIRED`
- **依赖任务**：5.7
- **建议提交粒度**：1 个 commit
- ⚡ **并发/幂等/事务边界**

### 任务 5.10：家长兑现 ⚡
- **原任务编号**：6.10
- **capability**：exchange
- **优先级**：⚡
- **目标**：家长将 `PENDING_FULFILLMENT` 兑换转为 `FULFILLED`，双家长并发仅一条成功。
- **输入**：`exchange/spec.md` §兑换状态与兑现权限
- **验收标准**：
  - 非家长兑现 → `FORBIDDEN`
  - 双家长并发兑现 → 仅一个成功，另一个 `EXCHANGE_INVALID_STATE`
  - 已兑现不可取消
- **依赖任务**：5.7、2.8
- **建议提交粒度**：1 个 commit
- ⚡ **并发/幂等/事务边界**

### 任务 5.11：取消退款与库存恢复 ⚡
- **原任务编号**：6.11
- **capability**：exchange、points
- **优先级**：⚡
- **目标**：家长取消 `PENDING_FULFILLMENT` 兑换，在同一事务中：退款（`REFUND` 流水）+ 库存恢复 + 状态变更。每笔兑换最多退款一次。
- **输入**：`exchange/spec.md` §取消的原子补偿与仅一次语义
- **验收标准**：
  - 重复取消 → `EXCHANGE_INVALID_STATE`
  - 孩子取消盲盒 → `FORBIDDEN`
  - 兑现与取消并发 → 仅一个终态
  - 补偿事务任一步失败 → 整体回滚 `EXCHANGE_CANCELLATION_FAILED`
- **依赖任务**：5.10、4.8
- **建议提交粒度**：1 个 commit
- ⚡ **并发/幂等/事务边界**

---

## 阶段 6：实例管理、安全与审计（8 项任务）

**目标**：实现实例状态、系统配置、账号启停、审计日志、统一错误码、安全加固。**门禁**：实例管理 API 权限与审计测试通过。

### 任务 6.1：实例初始化状态与健康查询 🔒
- **原任务编号**：7.1
- **capability**：instance-management
- **优先级**：🔒
- **目标**：实现 `GET /api/health`（未认证最小健康）、`GET /api/admin/health`（管理员详细健康）端点。
- **输入**：`instance-management/spec.md` §实例初始化状态、§版本与健康信息
- **验收标准**：
  - 未认证仅暴露布尔存活状态
  - 管理员可见：版本、构建信息、数据库迁移状态、备份状态、恢复演练结果
  - RPO 超过 24 小时返回 `RPO_EXCEEDED` 警告
- **依赖任务**：2.2
- **建议提交粒度**：1 个 commit
- 🔒 **安全敏感任务**（信息暴露控制）

### 任务 6.2：系统配置管理 🔒
- **原任务编号**：7.2
- **capability**：instance-management
- **优先级**：🔒
- **目标**：实现白名单配置的读取和更新，秘密字段脱敏显示或只写，配置变更记录审计。
- **输入**：`instance-management/spec.md` §系统配置管理
- **验收标准**：
  - 未知键 → `CONFIGURATION_INVALID`
  - 秘密字段仅显示"已配置"掩码
  - 短信配置不完整时短信登录保持关闭
- **依赖任务**：2.2、1.8
- **建议提交粒度**：1 个 commit
- 🔒 **安全敏感任务**（秘密保护）

### 任务 6.3：账号列表与启停管理 🔒⚡
- **原任务编号**：7.3
- **capability**：instance-management、auth
- **优先级**：🔒⚡
- **目标**：实现账号最小化状态列表（手机号脱敏）、启停操作。停用立即撤销全部会话。
- **输入**：`instance-management/spec.md` §当前实例账号启停
- **验收标准**：
  - 最后管理员保护 → `LAST_INSTANCE_ADMIN`
  - 最后家长保护 → `LAST_ACTIVE_PARENT`
  - 重新启用不恢复旧会话
  - 并发停用保护 → 原子约束
- **依赖任务**：2.2、2.11、2.6
- **建议提交粒度**：1 个 commit
- 🔒 **安全敏感任务** | ⚡ **并发/幂等/事务边界**

### 任务 6.4：审计日志分页与脱敏导出 🔒
- **原任务编号**：7.4
- **capability**：instance-management
- **优先级**：🔒
- **目标**：实现审计日志分页查询（按操作者/事件/日期筛选）和脱敏导出。
- **输入**：`instance-management/spec.md` §审计日志查询
- **验收标准**：
  - 仅 `INSTANCE_ADMIN` 可查询
  - 返回前再次脱敏字段
  - 查询范围过大 → `AUDIT_QUERY_LIMIT_EXCEEDED`
- **依赖任务**：1.8
- **建议提交粒度**：1 个 commit
- 🔒 **安全敏感任务**

### 任务 6.5：全链路审计接入 🔒
- **原任务编号**：7.5
- **capability**：auth、family、exchange、points
- **优先级**：🔒
- **目标**：为登录、绑定、成员变更、审核、积分调整、兑换取消、配置变更等接入统一审计事件写入。
- **输入**：各 capability spec 中的审计要求
- **验收标准**：
  - 成功与失败事件均可追踪
  - 秘密字段不出现在审计记录中
  - 审计写入失败时关键操作（PIN/删除/绑定）回滚 → `AUDIT_UNAVAILABLE`
- **依赖任务**：6.4（审计基础设施就绪）、已完成的所有业务模块
- **建议提交粒度**：分模块逐步接入，每个模块 1 个 commit
- 🔒 **安全敏感任务**

### 任务 6.6：统一认证限流与安全加固 🔒
- **原任务编号**：7.6
- **capability**：auth
- **优先级**：🔒
- **目标**：为登录、令牌刷新、PIN 校验、初始化等端点实施分层限流；Cookie 安全属性；CSRF 防护；账号枚举防护。
- **输入**：`auth/spec.md` §认证防滥用与审计
- **验收标准**：
  - 超过阈值 → HTTP 429 `RATE_LIMITED`
  - 生产环境 Cookie 含 `Secure`、`HttpOnly`、`SameSite=Lax`
  - 跨角色访问安全测试通过
- **依赖任务**：2.3、2.5、2.12
- **建议提交粒度**：1 个 commit
- 🔒 **安全敏感任务**

### 任务 6.7：数据导出与匿名化 🔒
- **原任务编号**：7.7
- **capability**：family、instance-management
- **优先级**：🔒
- **目标**：实现家庭数据导出（排除秘密字段、已删除孩子匿名标签）和删除匿名化作业。
- **输入**：`family/spec.md` §家庭数据导出、§孩子删除与历史匿名化
- **验收标准**：
  - 导出不包含密码/PIN/哈希/令牌/邀请秘密/设备绑定凭据
  - 已删除孩子 → 匿名标签
  - 非 PARENT 角色 → `FORBIDDEN`
- **依赖任务**：2.10、4.10
- **建议提交粒度**：1 个 commit
- 🔒 **安全敏感任务**（隐私数据）

### 任务 6.8：统一稳定错误码与请求关联 ID
- **原任务编号**：7.8
- **capability**：design
- **目标**：实现全局异常处理，统一 `ApiResponse<T>` 结构（`code` + `message` + `data` + `request_id`），所有错误码按 capability 前缀定义。
- **输入**：技术设计文档 §5.1、§5.2
- **验收标准**：
  - 所有 API 返回统一响应结构
  - 错误码契约快照测试通过
  - `request_id` 贯穿请求全生命周期
- **依赖任务**：0.5（日志配置就绪）
- **建议提交粒度**：1 个 commit（`common/exception/` + `web/GlobalExceptionHandler`）

---

## 阶段 7：三端 Web 体验（10 项任务）

**目标**：实现 admin/parent/child 三套完整前端 UI，覆盖所有业务页面的加载/空/错误/离线状态。**门禁**：三角色 E2E 测试通过。

### 任务 7.1：三角色主题与共享组件
- **原任务编号**：8.1
- **capability**：web-app
- **目标**：实现 admin/parent/child 三套 CSS 变量主题（颜色、排版、图标）、共享组件库（Button、Modal、Toast、Pagination、EmptyState、ErrorBoundary）、路由级主题切换。
- **输入**：`web-app/spec.md` §角色主题与移动优先布局
- **验收标准**：
  - 视觉回归测试通过
  - 主题不依赖颜色表达角色或状态
- **依赖任务**：0.3、0.4
- **建议提交粒度**：3 个 commits（主题变量 + 共享组件 + 布局组件）

### 任务 7.2：认证页面（初始化/登录/登出/会话过期） 🔒
- **原任务编号**：8.2
- **capability**：web-app、auth
- **优先级**：🔒
- **目标**：实现初始化向导页面、家长登录页（手机号+密码，可选短信入口）、登出确认、会话过期提示。
- **输入**：`web-app/spec.md` §路由权限守卫与安全会话
- **验收标准**：
  - 键盘、触摸操作流畅
  - 登录失败不枚举账号（统一错误提示）
  - 401 响应 → 自动清除状态并导向登录
- **依赖任务**：7.1、2.3
- **建议提交粒度**：2 个 commits（登录页 + 初始化向导）
- 🔒 **安全敏感任务**

### 任务 7.3：孩子设备绑定与 PIN 登录页 🔒
- **原任务编号**：8.3
- **capability**：web-app、auth、family
- **优先级**：🔒
- **目标**：实现孩子端设备绑定流程（家长授权 QR/码）、档案选择、数字 PIN 键盘、锁定提示。
- **输入**：`web-app/spec.md` §路由权限守卫与安全会话
- **验收标准**：
  - PIN 输入使用专用键盘组件
  - 锁定提示（15 分钟倒计时）
  - 撤销绑定后设备立即失效
- **依赖任务**：7.1、2.12
- **建议提交粒度**：2 个 commits（设备绑定页 + PIN 登录页）
- 🔒 **安全敏感任务**

### 任务 7.4：实例管理页面（概览/配置/账号/审计/健康）
- **原任务编号**：8.4
- **capability**：web-app、instance-management
- **目标**：实现实例概览（初始化状态、版本、备份状态、恢复演练结果）、系统配置、账号列表（启停）、审计日志查询、健康面板。
- **输入**：`web-app/spec.md`、`instance-management/spec.md`
- **验收标准**：
  - 不出现跨家庭 SaaS 功能
  - 账号列表手机号脱敏
  - 配置页面秘密字段掩码
- **依赖任务**：7.1、6.1、6.2、6.3、6.4
- **建议提交粒度**：3 个 commits（概览 + 配置/账号 + 审计/健康）

### 任务 7.5：家长端家庭与任务模板管理
- **原任务编号**：8.5
- **capability**：web-app、family、task-template
- **目标**：实现家庭成员列表、邀请管理、孩子档案、任务模板 CRUD、难度管理页面。
- **输入**：`web-app/spec.md`
- **验收标准**：
  - 核心 CRUD E2E 通过
  - 模板创建实时字段校验
- **依赖任务**：7.1、2.8、2.9、2.10、3.3
- **建议提交粒度**：2 个 commits（家庭 + 模板）

### 任务 7.6：家长端任务分配与审核页面
- **原任务编号**：8.6
- **capability**：web-app、task-assignment、task-review
- **目标**：实现任务日历视图、批量分配表单、待审核队列（滑动+按钮审核）、审核历史。
- **输入**：`web-app/spec.md` §大触控目标与键盘可操作性
- **验收标准**：
  - 滑动审核同时提供"通过"/"拒绝"按钮
  - 逾期标记视觉区分
  - 驳回原因输入框
- **依赖任务**：7.1、3.6、3.9、4.4
- **建议提交粒度**：2 个 commits（分配 + 审核）

### 任务 7.7：家长端积分/奖品/盲盒/兑换履约页面
- **原任务编号**：8.7
- **capability**：web-app、points、prize、blind-box、exchange
- **目标**：实现积分余额与流水查看、奖品管理、盲盒奖池管理、有效概率预览、兑换履约（兑现/取消）。
- **输入**：`web-app/spec.md`
- **验收标准**：
  - 取消与退款反馈一致（撤回已扣积分提示）
  - 盲盒候选概率可视化
- **依赖任务**：7.1、4.10、5.1、5.3、5.10
- **建议提交粒度**：2 个 commits（积分+奖品 + 盲盒+兑换）

### 任务 7.8：孩子端首页、任务与提交
- **原任务编号**：8.8
- **capability**：web-app、task-assignment、task-review
- **目标**：实现孩子首页（今日任务列表）、任务提交弹窗（说明+佐证）、驳回原因查看和重新提交。
- **输入**：`web-app/spec.md`
- **验收标准**：
  - 状态与历史显示一致
  - 驳回原因可读
  - 提交按钮防重复点击
- **依赖任务**：7.1、3.9、4.1、4.5
- **建议提交粒度**：1 个 commit

### 任务 7.9：孩子端积分/商城/盲盒/兑换历史
- **原任务编号**：8.9
- **capability**：web-app、points、prize、blind-box、exchange
- **目标**：实现孩子积分余额展示、积分商城、盲盒确认（概率+版本确认）、开箱动效、兑换历史。
- **输入**：`web-app/spec.md` §动效可选与低端设备降级
- **验收标准**：
  - 孩子不能触发取消操作
  - 盲盒动效中断后安全恢复（从幂等结果显示）
  - 减少动态效果偏好时跳过动效
- **依赖任务**：7.1、4.10、5.5、5.7、5.8
- **建议提交粒度**：2 个 commits（积分+商城 + 盲盒+历史）

### 任务 7.10：响应式、可访问性与性能优化
- **原任务编号**：8.10
- **capability**：web-app
- **目标**：完成所有页面 ≥320px 响应式适配、加载/空/错误/离线状态、减少动画偏好、低端设备降级、ARIA 标签、键盘导航。
- **输入**：`web-app/spec.md` §所有 UI 要求
- **验收标准**：
  - 主要页面 LCP ≤2.5 秒
  - axe-core 可访问性扫描通过
  - 200% 文字缩放不截断
  - 触控目标 ≥44×44 CSS 像素
- **依赖任务**：7.1-7.9
- **建议提交粒度**：3 个 commits（响应式 + 可访问性 + 性能）
- 🧪 **测试任务**

---

## 阶段 8：私有化部署、备份与升级（10 项任务）

**目标**：实现 Docker 构建、Compose 编排、备份恢复、升级迁移、安全加固和完整的部署文档。**门禁**：多架构烟雾测试通过。

### 任务 8.1：多阶段 Dockerfile（server + web + nginx）
- **原任务编号**：9.1
- **capability**：deployment-operations
- **目标**：为后端 JAR、前端静态资源、Nginx 反向代理编写多阶段 Dockerfile，标签 `mit-modelide-core-server/web/nginx:<version>`。
- **验收标准**：
  - `linux/amd64` 和 `linux/arm64` 构建成功
  - 镜像层不含源代码或秘密
- **依赖任务**：0.1、0.2
- **建议提交粒度**：3 个 commits（server Dockerfile + web Dockerfile + nginx Dockerfile）

### 任务 8.2：Docker Compose 编排与健康检查
- **原任务编号**：9.2
- **capability**：deployment-operations
- **目标**：编写 `docker-compose.yml`：MySQL 8 + Redis 7 + server + nginx + backup sidecar，命名卷持久化，健康检查依赖链。
- **验收标准**：
  - 全新主机 `docker compose up -d && docker compose ps` 均为 healthy
  - 数据卷重建容器后数据保留
  - 仅 nginx 暴露对外端口
- **依赖任务**：8.1
- **建议提交粒度**：1 个 commit

### 任务 8.3：Bash 与 PowerShell 构建脚本
- **原任务编号**：9.3
- **capability**：deployment-operations
- **目标**：完善 `deploy/build.sh`（Linux）和 `deploy/build.ps1`（Windows），支持 `--platform linux/amd64|linux/arm64`。
- **验收标准**：
  - Windows 和 Linux 完成相同版本构建
  - 不支持平台 → `UNSUPPORTED_PLATFORM`
- **依赖任务**：8.1
- **建议提交粒度**：2 个 commits（build.sh + build.ps1）

### 任务 8.4：环境模板与秘密校验 🔒
- **原任务编号**：9.4
- **capability**：deployment-operations
- **优先级**：🔒
- **目标**：提供 `.env.template`（仅占位说明），启动时校验所有必填秘密非空/非默认值。
- **验收标准**：
  - 空密码/示例密码 → 启动失败 `CONFIG_INVALID`
  - 失败信息包含字段名但不暴露机密值
- **依赖任务**：8.2
- **建议提交粒度**：1 个 commit
- 🔒 **安全敏感任务**

### 任务 8.5：生产 HTTPS 与安全头 🔒
- **原任务编号**：9.5
- **capability**：deployment-operations
- **优先级**：🔒
- **目标**：配置 Nginx HTTPS（自签或用户证书）、HSTS、CSP、X-Frame-Options 等安全头；开发模式允许 localhost HTTP。
- **验收标准**：
  - 生产 HTTP 被重定向或拒绝
  - 开发模式明确标识不可用于生产
- **依赖任务**：8.2
- **建议提交粒度**：1 个 commit
- 🔒 **安全敏感任务**

### 任务 8.6：健康检查与 doctor 命令
- **原任务编号**：9.6
- **capability**：deployment-operations
- **目标**：实现应用/MySQL/Redis/磁盘/迁移状态健康检查端点；`docker compose exec server doctor` 诊断命令。
- **验收标准**：
  - 依赖不可用 → 就绪检查返回未就绪 + `DEPENDENCY_UNHEALTHY`
  - doctor 命令标识故障依赖
- **依赖任务**：6.1
- **建议提交粒度**：1 个 commit

### 任务 8.7：每日备份与保留策略
- **原任务编号**：9.7
- **capability**：deployment-operations
- **目标**：实现 backup-sidecar 容器：每日 02:00 `mysqldump --single-transaction` + 上传文件打包，7 天+4 周保留策略，备份完整性校验。
- **验收标准**：
  - 备份成功/失败写入 `backup_run` 表
  - 保留清理先于过期备份删除前验证新备份
  - 备份日志不含数据库密码
- **依赖任务**：8.2
- **建议提交粒度**：2 个 commits（备份脚本 + 保留清理脚本）

### 任务 8.8：恢复脚本与恢复演练
- **原任务编号**：9.8
- **capability**：deployment-operations
- **目标**：实现 `deploy/restore.sh` 脚本，支持：校验备份 → 停止写流量 → 恢复 MySQL + 文件 → 启动 → 健康检查。恢复演练结果写入 `recovery_drill` 表。
- **验收标准**：
  - RPO ≤24 小时
  - RTO ≤2 小时（参考环境）
  - 备份校验失败 → `RESTORE_BACKUP_INVALID`（不覆盖现有数据）
- **依赖任务**：8.7
- **建议提交粒度**：1 个 commit

### 任务 8.9：升级流程与失败回滚
- **原任务编号**：9.9
- **capability**：deployment-operations
- **目标**：实现升级脚本：升级前强制备份 + Flyway 迁移 + 健康检查 + 失败时从备份恢复。
- **验收标准**：
  - 升级前备份失败 → 中止 `PRE_UPGRADE_BACKUP_FAILED`
  - 迁移失败 → `MIGRATION_FAILED`，恢复旧版本和数据
  - 原地降级 → `DOWNGRADE_NOT_SUPPORTED`
- **依赖任务**：8.8
- **建议提交粒度**：1 个 commit

### 任务 8.10：部署文档
- **原任务编号**：9.10
- **capability**：deployment-operations
- **目标**：编写 Windows/Linux 安装、配置、HTTPS、备份、恢复、升级、故障排查文档。
- **验收标准**：
  - 未参与开发的审阅者按文档完成部署演练
- **依赖任务**：8.2-8.9
- **建议提交粒度**：1 个 commit

---

## 阶段 9：综合验证与交付门禁（10 项任务）

**目标**：全链路集成测试、性能测试、安全扫描、OpenSpec 验证。**最终门禁**：全部任务通过后方可勾选 change 完成。

### 任务 9.1：需求→任务→测试追踪清单
- **原任务编号**：10.1
- **capability**：all
- **目标**：建立 Requirement/Scenario → 任务编号 → 测试用例的完整追踪矩阵。
- **验收标准**：每条 Requirement 至少有一项验证证据（测试报告/手动验收记录）
- **依赖任务**：全部
- **建议提交粒度**：1 个 commit（追踪清单文档）
- 🧪 **测试任务**

### 任务 9.2：初始化、认证与成员生命周期集成测试 🧪
- **原任务编号**：10.2
- **capability**：auth、family
- **目标**：集成测试覆盖：初始化→家长登录→邀请→成员→孩子绑定→PIN→账号停用→恢复。
- **验收标准**：成功、权限、限流和恢复边界全部通过
- **依赖任务**：阶段 2 全部 + 阶段 6 审计接入
- **建议提交粒度**：1 个 commit（`auth/family` 模块 `*IT.java`）
- 🧪 **测试任务**

### 任务 9.3：任务→积分闭环集成测试 🧪⚡
- **原任务编号**：10.3
- **capability**：task-template、task-assignment、task-review、points
- **目标**：集成测试覆盖：模板创建→分配→提交→驳回→重提→批准→积分发放→流水查询。
- **验收标准**：
  - 驳回重提、迟交、幂等、并发和回滚全部通过
  - 并发批准同一 attempt → 仅一笔 EARN
- **依赖任务**：阶段 3 + 阶段 4 全部
- **建议提交粒度**：1 个 commit
- 🧪 **测试任务** | ⚡ **并发/幂等/事务边界**

### 任务 9.4：奖励兑换闭环集成测试 🧪⚡
- **原任务编号**：10.4
- **capability**：prize、blind-box、exchange、points
- **目标**：集成测试覆盖：奖品/盲盒管理→孩子兑换→盲盒抽取→积分扣减→家长兑现→取消退款。
- **验收标准**：
  - 并发争抢库存 → 无超卖
  - 概率统计测试在容差范围内
  - 退款/取消/并发兑现全部通过
- **依赖任务**：阶段 5 全部
- **建议提交粒度**：1 个 commit
- 🧪 **测试任务** | ⚡ **并发/幂等/事务边界**

### 任务 9.5：三角色权限与 E2E 测试 🧪
- **原任务编号**：10.5
- **capability**：instance-management、web-app
- **目标**：Playwright E2E 测试：孩子越权访问家长/管理员 API、管理员访问家庭数据、角色守卫与路由重定向。
- **验收标准**：
  - 越权读写 → 403/404
  - 不可见资源不泄露
  - 账号停用后立即失效
- **依赖任务**：阶段 7 全部
- **建议提交粒度**：1 个 commit（`e2e/` 目录下测试）
- 🧪 **测试任务**

### 任务 9.6：多架构 Compose 烟雾测试 🧪
- **原任务编号**：10.6
- **capability**：deployment-operations
- **目标**：在 Linux amd64、Linux arm64、Windows Docker 环境执行 `docker compose up` 烟雾测试。
- **验收标准**：
  - 三环境构建成功、启动健康
  - 基础闭环（初始化→登录→创建家庭→查看健康）通过
- **依赖任务**：阶段 8 全部
- **建议提交粒度**：1 个 commit（CI 配置 + 烟雾测试脚本）
- 🧪 **测试任务**

### 任务 9.7：容量与性能测试 🧪
- **原任务编号**：10.7
- **capability**：deployment-operations
- **目标**：在 2 vCPU、4 GB、10 万条业务记录、20 个并发会话下，验证核心 API P95 ≤300ms，前端 LCP ≤2.5s。
- **验收标准**：
  - P95 和 LCP 达标
  - 数据量达到十万条后分页有界
- **依赖任务**：9.6
- **建议提交粒度**：1 个 commit（性能测试脚本 + 结果报告）
- 🧪 **测试任务**

### 任务 9.8：备份恢复与升级故障演练 🧪
- **原任务编号**：10.8
- **capability**：deployment-operations
- **目标**：执行备份恢复演练、升级失败演练，验证 RPO≤24h、RTO≤2h、数据一致性。
- **验收标准**：
  - 备份恢复成功验证
  - 升级失败后数据完整恢复
  - 演练报告记录 RPO/RTO 实际值
- **依赖任务**：8.7、8.8、8.9
- **建议提交粒度**：1 个 commit（演练脚本 + 报告模板）
- 🧪 **测试任务**

### 任务 9.9：安全扫描与无障碍检查 🧪🔒
- **原任务编号**：10.9
- **capability**：web-app、auth
- **目标**：执行依赖漏洞扫描、秘密扫描、Cookie/CSRF 测试、日志脱敏验证、axe-core 可访问性扫描。
- **验收标准**：
  - 无未处置高风险漏洞
  - 日志不含密码/PIN/令牌
  - axe-core 关键页面通过
- **依赖任务**：9.5、9.6
- **建议提交粒度**：1 个 commit（安全扫描配置 + 报告）
- 🧪 **测试任务** | 🔒 **安全敏感任务**

### 任务 9.10：最终完整性检查与 OpenSpec 验证 🧪
- **原任务编号**：10.10
- **capability**：all
- **目标**：运行 `mvn verify`（全部单元+集成测试）、`npm run test`（前端测试）、`docker compose build && up -d && docker compose ps`（健康检查）、`openspec validate core-features --strict`。
- **验收标准**：全部通过
- **依赖任务**：9.1-9.9
- **建议提交粒度**：1 个 commit（验证报告文档）
- 🧪 **测试任务**

---

## 附录 A：跨阶段依赖图

```
阶段 0（工程基线，7项）
  │
  ├──→ 阶段 1（数据库，8项）── 可与阶段 0 并行
  │
  ├──→ 阶段 2（认证+家庭，12项）── 依赖阶段 1 表结构
  │      │
  │      ├──→ 阶段 3（任务，10项）── 依赖阶段 2 家庭基础
  │      │      │
  │      │      └──→ 阶段 4（审核+积分，9项）── 依赖阶段 3 任务分配
  │      │              │
  │      │              ├──→ 阶段 5（奖品+兑换，11项）── 依赖阶段 4 积分
  │      │              │      │
  │      │              │      └──→ 阶段 6（实例管理，8项）── 可与阶段 5 部分并行
  │      │              │              │
  │      │              │              └──→ 阶段 7（Web 前端，10项）── 依赖所有后端
  │      │              │                      │
  │      │              │                      └──→ 阶段 8（部署运维，10项）
  │      │              │                              │
  │      │              │                              └──→ 阶段 9（综合验证，10项）
  │      │              │
  │      │              └──→ 阶段 5（奖品+兑换）── 也依赖阶段 3 的任务模板
  │      │
  │      └──→ 阶段 3（任务）── 也依赖阶段 1 表结构
```

## 附录 B：关键并发/幂等/事务边界任务清单

| 任务编号 | 名称 | 并发/事务关键点 |
|---|---|---|
| 2.2 | 实例初始化与家庭创建 | 并发初始化仅一个成功，单事务 |
| 3.4 | 模板软删除与版本冲突 | 乐观锁版本控制 |
| 3.6 | 批量分配与幂等键 | 幂等键 + 整体回滚 |
| 3.7 | 周期任务滚动生成 | 唯一业务键防重 |
| 3.10 | 分配取消与审批保护 | 批准/取消并发仅一个成功 |
| 4.6 | 批准与积分发放同事务 | 审核+流水+余额同一事务 |
| 4.7 | 同一提交只允许一次审核 | 唯一约束防双审核 |
| 4.8 | 积分流水与余额投影 | SELECT FOR UPDATE / 乐观锁 |
| 5.7 | 明牌兑换原子事务 | 积分+库存+记录同一事务 |
| 5.8 | 盲盒兑换原子事务 | availability_version 重检+事务 |
| 5.9 | 兑换幂等键 | 键绑定防重复扣减 |
| 5.10 | 家长兑现 | 双家长并发仅一个成功 |
| 5.11 | 取消退款与库存恢复 | 退款+库存+状态同一事务，单次退款 |
| 6.3 | 账号启停管理 | 最后管理员/家长原子约束 |
| 2.11 | 成员移除保护 | 并发移除仅保留一位 |

## 附录 C：安全敏感任务清单

| 任务编号 | 名称 | 安全要点 |
|---|---|---|
| 1.2 | 创建认证相关表 | 密码哈希策略 |
| 2.1 | 首次启动初始化令牌 | 令牌单次消费，不落日志 |
| 2.3 | 手机号+密码登录 | 不枚举账号 |
| 2.5 | 刷新令牌轮换 | 重用检测 + 全链撤销 |
| 2.6 | 会话撤销 | 密码变更/停用全局撤销 |
| 2.7 | 本地管理员恢复 | 本地主机授权 |
| 2.9 | 家长邀请 | 邀请秘密不落日志 |
| 2.10 | 孩子档案管理 | 最小化数据 + 匿名化 |
| 2.12 | 孩子 PIN 登录 | 5 次锁定，秘密保护 |
| 4.9 | 家长手工调整 | 审计记录 |
| 6.1 | 健康查询 | 未认证最小信息 |
| 6.2 | 系统配置管理 | 秘密字段掩码 |
| 6.3 | 账号启停 | 最后角色保护 |
| 6.5 | 全链路审计 | 审计失败回滚关键操作 |
| 6.6 | 统一认证限流 | 分层限流 + CSRF |
| 6.7 | 数据导出匿名化 | 秘密过滤 + 匿名标签 |
| 7.2 | 认证页面 | 令牌不落 localStorage |
| 9.9 | 安全扫描 | 漏洞/秘密/CSRF 全扫描 |

---

*计划版本: 1.0 | 生成日期: 2026-07-10 | 对应 change: core-features*
