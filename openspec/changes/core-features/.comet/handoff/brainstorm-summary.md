# Brainstorm Summary

- Change: core-features
- Date: 2026-07-10

## 确认的技术方案

采用单一 core-features change 实施，但严格按 capability 拆分后端 Maven 模块、前端角色入口和数据库表，确保每个 capability 可独立测试与演进：

- **后端**：Spring Boot 3 + Maven 多模块，模块划分为 `common`, `auth`, `family`, `task`, `points`, `prize`, `exchange`, `instance-management`, `web`（REST 入口）。
- **前端**：React 18 + TypeScript + Vite + React Router 6 单页应用，按角色拆分为 `admin/`, `parent/`, `child/` 三个独立入口，共享组件库。
- **数据**：MySQL 8 主库 + Redis（会话、幂等键、限流、缓存、盲盒概率/库存临时锁）。
- **部署**：Docker Compose，Windows/Linux 主机均可，镜像统一前缀 `mit-modelide`，多架构 linux/amd64 + linux/arm64。

核心数据表包括：账号/角色/会话、家庭/儿童档案、任务模板/分配/快照、不可变提交与审核记录、积分流水与余额投影、奖品/盲盒池/奖池项、兑换记录与快照、审计日志、备份与恢复演练记录。

安全：家长使用手机号+密码；孩子使用设备授权+家庭 PIN；Redis 会话+HttpOnly Cookie；RBAC 角色隔离；凭据环境注入，不落入镜像。

API 采用统一返回结构，错误码按 capability 前缀命名；兑换使用幂等键绑定，盲盒 `availability_version` 采用候选集确定性内容哈希；积分/库存/兑换记录在同一数据库事务内原子完成。

部署包含 backup-sidecar 每日备份与保留策略，恢复演练由部署方命令触发，结果写入数据库供 instance-management 查询；健康接口分未认证最小探针与管理员详细接口。

测试覆盖单元、集成（Testcontainers）、E2E（Playwright）与非功能（并发、备份、可访问性）。

## 关键取舍与风险

- **单 change 范围大**：按 tasks.md 阶段门禁拆分，优先跑通 auth → family → task-review → points 闭环，再扩展 prize/exchange/deployment。
- **盲盒概率与库存并发**：availability_version 内容哈希 + 事务内重检 + 行锁；概率计算在服务端完成。
- **隐私与数据泄露**：最小化数据收集、审计日志、敏感字段脱敏、凭据环境注入、不默认遥测。
- **备份失败不可知**：备份状态与恢复演练结果纳入 instance-management 查询，RPO 超标返回 `RPO_EXCEEDED` 警告。

## 测试策略

- 单元测试：JUnit 5 + Mockito，覆盖服务层、状态机、错误码、积分计算。
- 集成测试：Testcontainers（MySQL + Redis），覆盖 API 端点、并发场景、幂等、退款、库存竞态。
- E2E：Playwright，覆盖家长/孩子完整业务流程。
- 非功能：并发兑换压力测试、备份/恢复演练、跨浏览器可访问性检查。

## Spec Patch

- exchange/spec.md: 错误码统一为 `POINTS_INSUFFICIENT_BALANCE` / `EXCHANGE_IDEMPOTENCY_KEY_REQUIRED` / `EXCHANGE_IDEMPOTENCY_CONFLICT` / `EXCHANGE_INVALID_QUERY`；增加「双家长并发兑现同一兑换」scenario；明确幂等键绑定优先于过期 `availability_version`。
- task-review/spec.md: 增加「驳回后重提但策略禁止迟交」scenario。
- blind-box/spec.md: 定义 `availability_version` 为候选集合（奖品标识、权重、成本）的确定性内容哈希。
- instance-management/spec.md: 概览/健康信息增加最近备份时间/状态、下次计划备份、最近恢复演练结果；RPO 超标返回 `RPO_EXCEEDED` 警告。
- tasks.md: 7.1 任务增加备份状态与恢复演练结果查询。

以上补丁已应用并通过了 `openspec validate core-features --strict`。
