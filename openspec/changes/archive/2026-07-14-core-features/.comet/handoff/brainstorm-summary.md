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

---

## 增量：任务类型扩展（2026-07-14）

### 增量背景

用户需求：任务模板实体新增 `task_type` 属性，支持三类任务类型：
1. **限时任务（LIMITED）**：开始日期（可空，空=即时开始）+ 结束日期，期限内完成
2. **重复任务（REPEAT）**：重复周期[每日/每周/每月/每年]；每周触发日=周一到周日任一；每月触发日=首日/末日/月中；每年触发日=某月某日；**只能在触发日提交**；**每次提交后自动创建下一周期新任务**
3. **常驻任务（STANDING）**：最大提交次数（可设"无限"或限值），长期常驻任务列表，达上限后提交提醒不能再提交

### 确认的技术方案

**数据模型（task_template 表扩展）**
- 新增两列：`task_type ENUM('LIMITED','REPEAT','STANDING') NOT NULL` + `type_config JSON NULL`
- `type_config` 按 task_type 分支：
  - LIMITED：`{start_date: date|null, end_date: date}`（end_date 必填，Asia/Shanghai 当地 23:59:59.999 截止）
  - REPEAT：`{frequency: DAILY|WEEKLY|MONTHLY|YEARLY, trigger_day: {...}}`
    - DAILY：trigger_day 缺省
    - WEEKLY：`{weekday: 1~7}`（ISO 8601，1=周一）
    - MONTHLY：`{mode: FIRST_DAY|LAST_DAY|MID_MONTH}`（月末自适应：2 月 28/29，4/6/9/11 月 30）
    - YEARLY：`{month: 1~12, day: 1~31}`（day 按 month 自适应）
  - STANDING：`{max_submissions: int|null}`（null=无限；正整数上限 10000）

**与原 recurrence 字段的吸收合并**
- 原 `recurrence` 字段（DAILY/WEEKDAYS/WEEKENDS/CUSTOM_WEEKDAYS）废弃
- 因 `server/business/` 实际未实施，无数据迁移负担
- 在 spec 层面以 MODIFIED Requirement 反映吸收合并
- 原 DAILY 等价 REPEAT+DAILY；WEEKDAYS/WEEKENDS/CUSTOM_WEEKDAYS 因 WEEKLY 只支持单一 weekday 不做等价迁移，家长需重建

**三类任务的行为语义**

LIMITED 状态机：`PENDING → SUBMITTABLE → COMPLETED/EXPIRED`
- start_date 为 null = 模板激活那一刻可提交
- end_date 当地 23:59:59.999 截止，过期不可补交

STANDING 状态机：`ACTIVE → COMPLETED`（按孩子独立计数）
- task_assignment 表新增 `submission_count INT NOT NULL DEFAULT 0`
- 只有审核通过的提交才计数（驳回/未审核不计）
- max_submissions=null 永远 ACTIVE；max_submissions=N 时 count==N 切换 COMPLETED

REPEAT 双触发推进机制：
- **提交触发器**（同步，审核通过事件钩子）：审核通过 → 本期 COMPLETED → 计算下一触发日 → 创建新实例（PENDING_OPEN）→ 到日切换 OPEN
- **时间触发器**（异步，每日 Asia/Shanghai 00:05 运行）：扫描触发日已过未提交的 OPEN 实例 → EXPIRED + 创建下一期；扫描到日的 PENDING_OPEN → 切换 OPEN
- 模板激活即生成首期（PENDING_OPEN 或 OPEN，取决于激活日与首触发日关系）
- 单实例状态机：`PENDING_OPEN → OPEN → COMPLETED/EXPIRED`（模板层面永不终结）
- 通过 `assignment_id + trigger_day` 唯一约束保证幂等

### 关键取舍与风险

- **task_type 不可改**：模板创建后锁定 task_type，要换类型只能新建（避免历史实例语义混乱）。错误码 `TASK_TEMPLATE_TYPE_IMMUTABLE`
- **STANDING 计数与审核绑定**：只有审核通过的提交计数。如果用户希望「提交即计数」（不管审核结果），需要重新评估
- **REPEAT 模板激活即生成首期**：家长配置后系统立刻创建首期实例，没有显式「启动」步骤。简化但可能与某些家长预期不符
- **时间触发器每日 1 次**：Asia/Shanghai 00:05 运行。考虑「只能当天提交」的硬规则，每日 1 次足够；如需要更高实时性可改每小时
- **WEEKDAYS/WEEKENDS/CUSTOM_WEEKDAYS 不等价迁移**：因 WEEKLY 只支持单一 weekday，多选场景需要家长重建。代码未实施所以无影响
- **type_config 用 JSON 字段**：业务规则约束在应用层校验，数据库只管结构。MySQL JSON 类型在工程实践稳定
- **时区固定 Asia/Shanghai**：与现有 recurrence 字段一致，不支持跨时区家庭（MVP 简化）

### 测试策略

- **单元测试**（JUnit 5 + Mockito）
  - 触发日计算函数 `nextTriggerDate()` 四种 frequency 各覆盖
  - type_config 校验逻辑（每种 task_type 的合法/非法输入）
  - LIMITED/STANDING/REPEAT 各自状态机切换
- **集成测试**（Testcontainers MySQL + Redis）
  - REPEAT 双触发推进：模拟提交触发 + 时间触发两条路径
  - 时间触发器幂等性：多次运行同一日期不重复创建
  - STANDING 计数器并发提交（多孩子同时提交，count 不串号）
  - task_type 不可改性验证（PUT 尝试修改返回错误码）
- **端到端测试**（Playwright）
  - 家长配置三类模板的完整流程
  - 孩子在不同状态下提交的边界（LIMITED 过期、REPEAT 非触发日、STANDING 达上限）

### Spec Patch（待回写）

**task-template/spec.md 的 MODIFIED Requirements**：
1. 「创建任务模板与字段验证」→ 增加 task_type 必填 + type_config 校验
2. 「可选周期规则和本地日历语义」→ 重命名「重复任务的触发日规则与本地日历语义」，归属 REPEAT，frequency 扩展为 DAILY/WEEKLY/MONTHLY/YEARLY
3. 「更新模板仅影响未来分配」→ 增加 task_type 不可改约束
4. 「分页筛选模板列表」→ 筛选条件增加 task_type

**task-template/spec.md 的 ADDED Requirements（4 条新增）**：
1. 「限时任务的时间窗口」— LIMITED 类型语义、状态机、时区、错误码 TASK_LIMITED_NOT_STARTED / TASK_LIMITED_EXPIRED
2. 「常驻任务的提交计数与上限」— STANDING 类型语义、按孩子计数、null=无限、审核通过计数、错误码 TASK_STANDING_LIMIT_REACHED
3. 「重复任务的滚动生成」— REPEAT 类型语义、双触发推进、状态机、激活即生成首期、只能触发日提交、错误码 TASK_REPEAT_NOT_TRIGGER_DAY
4. 「重复任务的时间触发器」— 每日 00:05 Asia/Shanghai 运行、扫描过期 + 推进 PENDING_OPEN→OPEN、幂等

**新增错误码清单**：
- `TASK_TEMPLATE_TYPE_IMMUTABLE`
- `TASK_TEMPLATE_TYPE_CONFIG_MISMATCH`
- `TASK_LIMITED_NOT_STARTED`
- `TASK_LIMITED_EXPIRED`
- `TASK_REPEAT_NOT_TRIGGER_DAY`
- `TASK_STANDING_LIMIT_REACHED`

**tasks.md 更新点**：
- 修改 4.1（CRUD 加 task_type/type_config）、4.2（周期规则重写为触发日规则）、4.5/4.6（分配加 task_type 维度）、4.7（实现 REPEAT 双触发推进）、4.8（迟交策略增加 EXPIRED）、4.9（日历查询显示三类状态）、4.10（取消支持三类语义）
- 新增 4.11-4.18 共八个任务：LIMITED 时间窗口、STANDING 计数器、STANDING 达上限、REPEAT 触发日计算、REPEAT 提交触发钩子、REPEAT 时间触发器、REPEAT 状态机、task_type 不可改性验证

**Design Doc 追加章节**：
- 在 `docs/superpowers/specs/2026-07-10-core-features-design.md` 追加「附录 B：任务类型扩展设计」
- 内容：type_config JSON Schema、三类任务状态机图、双触发推进流程、错误码清单、与原 recurrence 吸收合并说明

以上增量待用户最终确认后回写 spec/design/tasks。
