# Comet Design Handoff

- Change: add-task-template-resubmission-controls
- Phase: design
- Mode: compact
- Context hash: f1f7e68fb728233870419b35272d5930043f94a2ec507d962e241146782ca270

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/add-task-template-resubmission-controls/proposal.md

- Source: openspec/changes/add-task-template-resubmission-controls/proposal.md
- Lines: 1-90
- SHA256: 612db6ff2387b92a0b645eb92919aaead60e3dd7757c24de41f44bc14f9f853c

[TRUNCATED]

```md
## Why

当前任务模板只能通过 `task_type=STANDING` + `type_config.max_submissions` 限制单个 assignment 内的提交次数，存在两个产品空白：

1. 无法表达「积分上限」语义——家长无法控制「孩子通过这个模板最多累计获得多少积分」（同一模板的多次 REPEAT/LIMITED assignment 累计没有上限保护）。
2. 限制粒度耦合在 STANDING 类型的 `type_config` 内，REPEAT/LIMITED 类型完全无法配置任何重投上限；家长如果希望「每周家务模板累计最多奖励 50 积分」或「阅读打卡模板最多通过 30 次」目前无任何实现路径。

把孩子反复刷模板积分的场景纳入产品可控范围是 CuteGoals 激励机制的必要闭环。本次变更把「允许重复提交」「最大提交次数」「积分上限」提升为 task-template 的一等属性，并在 task-assignment 上固化快照、在提交接口前置校验、在孩子端 UI 上表达已达上限状态，让家长一次性配置、系统跨周期强制执行。

## What Changes

- **新增 task-template 一等属性**（替代 STANDING `type_config.max_submissions`）：
  - `allow_resubmit: BOOLEAN NOT NULL DEFAULT FALSE`：是否允许同一孩子在同一模板下重复提交（默认不勾选）
  - `max_submissions: INT NOT NULL DEFAULT 0`：最大「已通过」提交次数上限；`0` 表示不限制；仅在 `allow_resubmit=true` 时有意义
  - `points_cap: INT NOT NULL DEFAULT 0`：该孩子在该模板上累计可获得的积分上限；`0` 表示不限制；仅在 `allow_resubmit=true` 时有意义
- **BREAKING**：`task_type=STANDING` 不再读取 `type_config.max_submissions`，迁移到一等属性 `max_submissions`。`type_config` 内的 `max_submissions` 字段在迁移后视为只读历史字段；新创建的 STANDING 模板允许 `type_config` 内不再携带 `max_submissions`。`task_template.service.TaskTemplateService` / `TaskReviewService.parseMaxSubmissions` 中读取 `type_config.max_submissions` 的逻辑全部下线。
- **数据迁移 V14**：新增 `task_template` 三个字段 + `task_assignment` 三个 snapshot 字段；把所有 `task_type=STANDING` 模板的 `type_config.max_submissions`（null 或 1-10000）映射为 `allow_resubmit = (max_submissions IS NOT NULL)`、`max_submissions = COALESCE(原值, 0)`；其他类型默认 `allow_resubmit=FALSE`、`max_submissions=0`、`points_cap=0`；既有 `task_assignment.snapshot_template_type_config.max_submissions` 保留不动，新增同语义 snapshot 字段从 `task_template` 同步快照。
- **task-assignment 快照扩展**：分配创建时除了现有 snapshot_template_* 字段外，新增固化 `snapshot_template_allow_resubmit`、`snapshot_template_max_submissions`、`snapshot_template_points_cap`，使审核期校验与模板后续修改解耦。
- **task-review 提交前置校验**：`POST /api/task-review/submissions` 在执行现有「状态合法 / 迟交策略 / 幂等」校验之前，新增两道前置校验（仅当 `snapshot_template_allow_resubmit=true` 时启用）：
  - 「已通过次数」以 child + template 维度统计（跨 assignment 聚合：APPROVED 状态的 task_assignment 数 + REJECTED→重投历史中以 APPROVED 结束的尝试），达到 `snapshot_template_max_submissions` 时拒绝
  - 「累计获得积分」以 child + template 维度统计 PointsLedger 中通过该模板获得的积分总额，达到 `snapshot_template_points_cap` 时拒绝
  - 拒绝时分别返回稳定错误码 `TASK_SUBMISSION_MAX_REACHED` / `TASK_SUBMISSION_POINTS_CAP_REACHED`
- **孩子端任务列表可观察状态**：`GET /api/task-assignments?childId=...` 返回的每个 assignment 新增 `canSubmit: boolean` 和 `submissionBlockReason: 'MAX_REACHED' | 'POINTS_CAP_REACHED' | null` 字段（仅 PENDING / REJECTED 状态有判定意义）；前端按此禁用对应任务的提交按钮并显示 tooltip。
- **家长端模板编辑表单**：`ParentTemplatesPage` 表单新增「允许重复提交」复选框（默认不勾选）；勾选后条件显示「最大提交次数」`InputNumber`（min=0, max=10000, 默认 0）和「积分上限」`InputNumber`（min=0, max=100000000, 默认 0），提示文案 `0 = 不限制`。
- **管理端审计**：模板字段变更与提交拒绝事件复用现有审计通道（task-template audit log + task-review attempt history），不新增独立审计表。

## Capabilities

### New Capabilities

无。本次变更通过扩展现有 task-template、task-assignment、task-review 三个 capability 的 requirement 表达；家长端 / 孩子端 UI 行为作为 task-template 字段编辑契约和 task-review 提交校验的可观察 scenario 描述，不构成独立业务能力。

### Modified Capabilities

- `task-template`：新增「重复提交控制属性」requirement，定义 `allow_resubmit` / `max_submissions` / `points_cap` 三个一等字段的语义、取值范围和默认值；修改「创建任务模板与字段验证」requirement，移除 STANDING 类型对 `type_config.max_submissions` 的硬性要求（迁移到一等属性）；修改「更新模板仅影响未来分配」requirement，明确新字段可更新且仅影响新分配。
- `task-assignment`：扩展「人工单次分配与数据快照」requirement，在固化快照字段清单中追加三个 `snapshot_template_*` 字段；同步覆盖批量分配和周期生成路径。
- `task-review`：新增「重复提交次数与积分上限前置校验」requirement，定义提交接口的两道前置校验、两个稳定错误码、跨 assignment 统计口径，以及孩子端任务列表的 `canSubmit` / `submissionBlockReason` 字段语义。

## Impact

**受影响代码**：

- 后端 `server/common/`：
  - `entity/task/TaskTemplate.java`、`entity/task/TaskAssignment.java` 新增字段 + Lombok 注解
  - `resources/db/migration/V14__add_resubmission_controls.sql` 新增迁移脚本
- 后端 `server/task/`：
  - `service/TaskTemplateService.java`（约 740 行附近的 maxSubmissions 解析逻辑、字段校验、模板创建/更新 DTO）
  - `service/TaskAssignmentService.java`（snapshot 字段写入逻辑）
  - `controller/dto/`：请求/响应 DTO 新增字段
  - 现有 STANDING `parseMaxSubmissions` 相关代码全部下线
- 后端 `server/task-review/`：
  - `service/TaskReviewService.java`（submit 入口前置校验，约 189 行附近的现有 maxSubmissions 校验被替换）
  - `mapper/TaskReviewMapper`（新增按 template_id + child_id 聚合 APPROVED 数量的查询）
- 后端 `server/points/`：
  - `mapper/PointsLedgerMapper`（新增按 template_id + child_id 聚合积分总额的 JOIN 查询，依赖 `business_ref = "ATTEMPT_<id>"` → `task_attempt.assignment_id` → `task_assignment.template_id` 链路）
- 后端 `web/`（前端）：
  - `parent/pages/ParentTemplatesPage.tsx`（表单字段）
  - `parent/components/TaskTypeConfigForms.tsx`（STANDING 子表单移除「最大提交次数」字段，改为顶层公共字段）
  - `child/pages/index.tsx` 的 `ChildTasksPage` 和 `ChildHomePage`（按 `canSubmit` 禁用按钮 + tooltip）
  - 共享类型 `shared/api.ts` 或对应 task-assignment / task-template 类型定义

**受影响 API**：

- `POST /api/task-templates`、`PUT /api/task-templates/{id}`：请求体新增三个字段
- `GET /api/task-templates`、`GET /api/task-templates/{id}`：响应体新增三个字段
- `GET /api/task-assignments`：每个 assignment 响应体新增 `canSubmit` + `submissionBlockReason` + 三个 snapshot 字段
- `POST /api/task-review/submissions`：新增 `TASK_SUBMISSION_MAX_REACHED` / `TASK_SUBMISSION_POINTS_CAP_REACHED` 两个错误码

**数据迁移与兼容**：

- Flyway V14 单脚本，含 `task_template` 三列 + `task_assignment` 三列 + STANDING 数据回填
- H2（PostgreSQL 模式）/ MySQL 8+ / PostgreSQL 15+ 兼容
- 既有 STANDING 类型模板的 `type_config.max_submissions` 迁移到 `max_submissions` 一等字段；保留 `type_config` 内字段不动以避免破坏旧客户端
- 不需要重建既有 assignment；新字段在旧 assignment 上以 NULL 形式存在，审核接口读取时回退到 template 当前值（兼容快照语义）

**测试与验证**：

- `TaskTemplateServiceTest`：新增字段校验、STANDING 兼容、默认值
- `TaskAssignmentServiceTest`：snapshot 写入、跨周期生成一致性
- `TaskReviewServiceTest`：max / cap 双前置校验、跨 assignment 统计、错误码

```

Full source: openspec/changes/add-task-template-resubmission-controls/proposal.md

## openspec/changes/add-task-template-resubmission-controls/design.md

- Source: openspec/changes/add-task-template-resubmission-controls/design.md
- Lines: 1-234
- SHA256: 609b3a8f092237a2272ce1550a62299278f0d12c60ffb863044a6bd77bd5606e

[TRUNCATED]

```md
## Context

CuteGoals 2.0 当前任务模板有三种类型（LIMITED / REPEAT / STANDING），其中只有 STANDING 通过 `type_config.max_submissions` 表达「单 assignment 内最大提交次数」。该字段有两个产品空白：

1. **跨周期不可控**：REPEAT / LIMITED 完全没有上限保护，孩子通过同一模板的多期 assignment 可累计无限积分。
2. **维度单一**：现有 max_submissions 只在「同一 assignment 内」生效（STANDING 单 assignment 多次重投），无法表达「积分维度上限」。

任务审核与积分流水已有可观察基础：每条 APPROVED 审核会在同一事务内创建 `points_ledger` 流水，业务引用 `business_ref = "ATTEMPT_<attemptId>"`；通过 `task_attempt.assignment_id → task_assignment.template_id` 可反查聚合。本变更把三个新属性提升为 task-template 一等字段并在 task-assignment 上固化快照，让校验逻辑跨周期、跨 assignment 强制执行。

受影响核心不变量：

- 积分流水不可变 → 通过 JOIN 流水聚合统计，不破坏现有写入路径
- 审核幂等 → 新校验仅作为 submit 前置，不影响审核决定路径
- 任务分配快照固化 → 新增三个 snapshot 字段与现有 `snapshot_template_*` 同源同生命周期

## Goals / Non-Goals

**Goals**：

- 把 `allow_resubmit` / `max_submissions` / `points_cap` 提升为 `task_template` 一等字段，在数据库、DTO、Service、前端表单上端到端落地
- 在 `task_assignment` 上固化三个对应快照字段，保证审核期校验与模板后续修改解耦
- 在 `POST /api/task-review/submissions` 接口前置两道校验，分别覆盖「已通过次数」与「累计积分」维度，返回稳定错误码
- 在 `GET /api/task-assignments` 响应中暴露 `canSubmit` / `submissionBlockReason`，让孩子端 UI 可以表达「已达上限」状态
- 通过 Flyway V14 一次性迁移所有 STANDING 模板的旧 `type_config.max_submissions` 到新字段，零手工操作

**Non-Goals**：

- 不修改任务审核决定路径（approve / reject 内部逻辑保持不变）
- 不修改积分账户余额计算逻辑（仅新增只读聚合查询）
- 不修改任务模板的分类、难度、重复规则、任务类型本身
- 不引入新的审计表（沿用 task_template audit log 与 task_review attempt history）
- 不为其他类型（PRIZE / BLIND_BOX / EXCHANGE）添加积分上限保护
- 不为「积分上限已达」提供家长手动重置入口（家长通过编辑模板 `points_cap` 字段即可调整）
- 不实现「跨家庭」语义（MVP 每实例一个家庭不变）

## Decisions

### D1：三个新字段直接落到 task_template 表（不引入子表）

**选择**：在 `task_template` 表新增三列 `allow_resubmit BOOLEAN`、`max_submissions INT`、`points_cap INT`，不建立 `task_template_resubmission` 子表。

**理由**：

- 字段语义强绑定模板本身、无独立生命周期，子表增加 JOIN 成本无收益
- 与现有 `task_type` / `type_config` 共同表达「模板的提交控制策略」，是模板的固有属性
- 三个字段都是 NOT NULL，配合默认值（`FALSE` / `0` / `0`），DDL 兼容 H2 / MySQL / PostgreSQL

**备选方案**：

- 把字段塞回 `type_config` JSON 内并扩展到所有任务类型 → 拒绝，原因是 JSON 字段难以做数据库级约束、聚合查询性能差、跨表 JOIN 也复杂
- 新建 `task_template_resubmission_policy` 子表（1:1） → 拒绝，原因是字段集稳定不会扩张，子表徒增复杂度

### D2：task_assignment 同步固化三个 snapshot 字段

**选择**：在 `task_assignment` 表新增 `snapshot_template_allow_resubmit BOOLEAN`、`snapshot_template_max_submissions INT`、`snapshot_template_points_cap INT`，与现有 `snapshot_template_*` 字段一致语义。

**理由**：

- 任务审核期校验必须与模板后续修改解耦（家长在分配创建后调整 `max_submissions` 不应回溯影响旧 assignment）
- 与现有 `snapshot_template_task_type` / `snapshot_template_type_config` 同源同生命周期，模式一致

**备选方案**：

- 审核期直接读 `task_template` 当前值 → 拒绝，原因是会导致家长编辑模板后旧 assignment 校验口径漂移，破坏快照语义
- 只快照 `allow_resubmit`，max/cap 实时读模板 → 拒绝，原因是 max/cap 是审核决策依据，必须固化

### D3：「已通过次数」与「累计积分」按 child + template 维度跨 assignment 聚合

**选择**：

- 「已通过次数」 = `task_review JOIN task_attempt ON review.attempt_id = attempt.id JOIN task_assignment ON attempt.assignment_id = assignment.id` 中 `assignment.template_id = ?` 且 `assignment.child_id = ?` 且 `review.decision = 'APPROVED'` 的行数
- 「累计积分」 = `points_ledger JOIN task_attempt ON ledger.business_ref = CONCAT('ATTEMPT_', attempt.id) JOIN task_assignment ON attempt.assignment_id = assignment.id` 中 `assignment.template_id = ?` 且 `assignment.child_id = ?` 且 `ledger.type = 'EARN'` 的 `SUM(amount)`

**理由**：

- 与「积分流水不可变」核心不变量一致：通过流水聚合天然得到准确累计值
- 与「审核历史不可删除」核心不变量一致：通过 APPROVED 审核记录聚合得到准确已通过次数
- 跨 assignment 统计满足用户决策：REPEAT 类型的多期 assignment 在同一模板下被纳入同一个上限

**备选方案**：

```

Full source: openspec/changes/add-task-template-resubmission-controls/design.md

## openspec/changes/add-task-template-resubmission-controls/tasks.md

- Source: openspec/changes/add-task-template-resubmission-controls/tasks.md
- Lines: 1-42
- SHA256: e89e0290ca39ba2bfd8bfcf91514ad9b0c236b8f66c8811d8ef86686189abe73

```md
# Implementation Tasks

本任务清单按依赖顺序组织，覆盖数据库迁移、后端实体与 DTO、后端服务层、前端家长端、前端孩子端、E2E 验证六大部分。每项任务在完成后必须运行对应的单元测试 / 集成测试作为证据，证据缺失不得勾选。

## 1. 数据库迁移（task-template / task-assignment capability）

- [ ] 1.1 编写 `server/common/src/main/resources/db/migration/V14__add_resubmission_controls.sql`：为 `task_template` 表新增 `allow_resubmit BOOLEAN NOT NULL DEFAULT FALSE`、`max_submissions INT NOT NULL DEFAULT 0`、`points_cap INT NOT NULL DEFAULT 0` 三列；为 `task_assignment` 表新增 `snapshot_template_allow_resubmit BOOLEAN DEFAULT NULL`、`snapshot_template_max_submissions INT DEFAULT NULL`、`snapshot_template_points_cap INT DEFAULT NULL` 三列。脚本必须单事务原子，兼容 H2（PostgreSQL 模式）/ MySQL 8+ / PostgreSQL 15+。验证证据：本地启动 Spring Boot 自动执行 Flyway 迁移成功，三种方言下 schema 校验通过。
- [ ] 1.2 在 V14 脚本末尾追加 STANDING 模板数据回填 SQL：对所有 `task_type='STANDING'` 模板，将 `type_config` JSON 中的 `max_submissions`（若非 null）映射为 `allow_resubmit=TRUE, max_submissions=<原值>`。脚本必须支持 MySQL（`JSON_EXTRACT`）、PostgreSQL（`->>`）和 H2（PostgreSQL 模式，使用 `->>`）三种方言。验证证据：本地准备一份 STANDING 测试数据，迁移后查询验证三个字段值正确，原 `type_config.max_submissions` 字段保留不变。

## 2. 后端实体与 Mapper（task-template / task-assignment capability）

- [ ] 2.1 在 `server/common/src/main/java/com/cutegoals/common/entity/task/TaskTemplate.java` 新增 `allowResubmit` / `maxSubmissions` / `pointsCap` 字段及对应 `@TableField` 注解；默认值与 DDL 一致。验证证据：单元测试 `TaskTemplateTest` 通过，字段序列化 / 反序列化正确。
- [ ] 2.2 在 `server/common/src/main/java/com/cutegoals/common/entity/task/TaskAssignment.java` 新增 `snapshotTemplateAllowResubmit` / `snapshotTemplateMaxSubmissions` / `snapshotTemplatePointsCap` 三个 snapshot 字段及 `@TableField` 注解。验证证据：单元测试 `TaskAssignmentTest` 通过。
- [ ] 2.3 在 `server/task-review/src/main/java/com/cutegoals/taskreview/mapper/TaskReviewMapper.java`（或对应位置）新增按 `(template_id, child_id)` 聚合统计 APPROVED 数量的查询方法 `countApprovedByTemplateAndChild(long templateId, long childId)`，JOIN `task_review` ↔ `task_attempt` ↔ `task_assignment`。验证证据：单元测试覆盖跨 assignment 多次 APPROVED 计数、REJECTED 不计数、并发审核去重。
- [ ] 2.4 在 `server/points/src/main/java/com/cutegoals/points/mapper/PointsLedgerMapper.java` 新增按 `(template_id, child_id)` 聚合统计 EARN 积分总额的查询方法 `sumEarnByTemplateAndChild(long templateId, long childId)`，JOIN `points_ledger` ↔ `task_attempt` ↔ `task_assignment`，依赖 `business_ref = CONCAT('ATTEMPT_', attempt_id)`。验证证据：单元测试覆盖多次 EARN 累加、REFUND 不计入、跨 assignment 聚合。

## 3. 后端 Service 与 Controller（task-template / task-assignment / task-review capability）

- [ ] 3.1 修改 `server/task/src/main/java/com/cutegoals/task/service/TaskTemplateService.java`：在创建 / 更新模板路径上接受并校验 `allowResubmit` / `maxSubmissions` / `pointsCap`；移除对 `type_config.max_submissions` 的读取（约 line 740 附近的 `parseMaxSubmissions` 逻辑）；接受旧客户端 PUT 提交的 `type_config.max_submissions` 但忽略其值。验证证据：更新 `TaskTemplateServiceTest`，覆盖默认值、范围校验（max 0-10000、cap 0-100000000）、STANDING 类型不再读 type_config、旧客户端兼容路径。
- [ ] 3.2 修改 `server/task/src/main/java/com/cutegoals/task/service/TaskAssignmentService.java`：在创建 / 批量创建 / 周期生成路径上固化三个 snapshot 字段。验证证据：更新 `TaskAssignmentServiceTest`，覆盖单次分配、批量分配、周期生成三条路径都正确写入 snapshot。
- [ ] 3.3 修改 task 相关 controller 的请求 / 响应 DTO：`TaskTemplateCreateRequest` / `TaskTemplateUpdateRequest` 新增三字段；`TaskTemplateResponse` 新增三字段；`TaskAssignmentResponse` 新增三个 snapshot 字段 + `canSubmit` + `submissionBlockReason` 两个字段。验证证据：DTO 序列化测试通过，字段命名与 OpenAPI 文档一致。
- [ ] 3.4 修改 `server/task-review/src/main/java/com/cutegoals/taskreview/service/TaskReviewService.java` 的 `submitAttempt` 入口：在既有「状态合法 / 迟交策略 / 幂等」校验之前，新增 max / cap 双前置校验；调用 2.3 和 2.4 的聚合方法，按 snapshot 字段决定是否启用；失败时返回 `TASK_SUBMISSION_MAX_REACHED` / `TASK_SUBMISSION_POINTS_CAP_REACHED`。验证证据：更新 `TaskReviewServiceTest`，覆盖 6 个核心场景（默认不启用、max=0 不限制、cap=0 不限制、max 达上限拒绝、cap 达上限拒绝、跨 assignment 聚合）。
- [ ] 3.5 修改 `GET /api/task-assignments` 接口（在 task 或 task-assignment 模块的 controller）：每个 assignment 响应中按规格定义计算 `canSubmit` 与 `submissionBlockReason`；使用与 3.4 完全相同的聚合口径避免双写漂移。验证证据：集成测试覆盖列表展示「可提交 / 已达 max / 已达 cap」三种状态，与 submit 校验结果一致。
- [ ] 3.6 删除既有 STANDING 类型对 `type_config.max_submissions` 的依赖：在 `TaskTemplateService.parseMaxSubmissions`、`TaskReviewService.parseMaxSubmissions`（line 189 / 465 附近）及相关测试中清理该路径。验证证据：grep 全仓无残留引用；现有 STANDING 测试改为基于一等字段。

## 4. 前端家长端（task-template capability）

- [ ] 4.1 修改 `web/src/parent/pages/ParentTemplatesPage.tsx` 模板编辑表单：新增「允许重复提交」Checkbox（默认不勾选）；勾选后条件渲染「最大提交次数」`InputNumber`（min=0, max=10000, 默认 0）和「积分上限」`InputNumber`（min=0, max=100000000, 默认 0）；提示文案 `0 = 不限制`。验证证据：更新 `__tests__/parent-save-dialogs.test.tsx`，覆盖默认不勾选、勾选后显示、字段提交到后端。
- [ ] 4.2 修改 `web/src/parent/components/TaskTypeConfigForms.tsx`：从 `StandingConfigForm` 移除「无限提交」Checkbox 与「最大提交次数」`InputNumber`（line 240-303 附近），改为顶层公共字段；`TypeConfigValue.max_submissions` 类型字段在新模型下不再使用。验证证据：更新 `__tests__/TaskTypeConfigForms.test.tsx`，STANDING 子表单只显示说明类信息，无 max_submissions 字段。
- [ ] 4.3 修改家长端 API 客户端类型定义（`web/src/parent/api.ts` 或对应位置）：`TaskTemplate` 类型新增 `allow_resubmit: boolean` / `max_submissions: number` / `points_cap: number` 三个字段；`TaskTemplatePayload` 同步。验证证据：TypeScript 类型检查通过，前端构建无错误。

## 5. 前端孩子端（task-review capability）

- [ ] 5.1 修改孩子端任务列表 `web/src/child/pages/index.tsx` 的 `ChildTasksPage` 与 `ChildHomePage`：读取 assignment 的 `canSubmit` 字段，`canSubmit=false` 时禁用「提交」/「重新提交」按钮；`submissionBlockReason='MAX_REACHED'` 时 tooltip 文案「已达到最大提交次数」，`'POINTS_CAP_REACHED'` 时文案「已达到积分上限」。验证证据：更新 `__tests__/pages.test.tsx`，覆盖按钮 disabled、tooltip 文案、视觉灰色样式。
- [ ] 5.2 修改孩子端 API 客户端类型定义：`ChildAssignment` 类型新增 `canSubmit: boolean` / `submissionBlockReason: 'MAX_REACHED' | 'POINTS_CAP_REACHED' | null` 及三个 snapshot 字段。验证证据：TypeScript 类型检查通过。

## 6. E2E 与回归（task-template / task-assignment / task-review capability）

- [ ] 6.1 新增集成测试 `server/task-review/src/test/java/.../TaskReviewResubmissionControlIT.java`：覆盖端到端场景——家长配置 `allow_resubmit=true, max_submissions=3` → 孩子 3 次 APPROVED → 第 4 次提交被拒；积分 cap 同理；未启用时行为完全保持。验证证据：测试通过。
- [ ] 6.2 新增迁移回归测试 `server/common/src/test/java/.../MigrationV14Test.java`：准备 V13 之前的 STANDING 测试数据 → 执行 V14 → 验证 `task_template` 三字段值正确、`task_assignment.snapshot_template_*` 字段为 NULL（旧 assignment 不回填）→ 新生 assignment 正确写入 snapshot。验证证据：测试通过。
- [ ] 6.3 更新 E2E 测试 `web/e2e/` 或对应位置：新增「家长配置上限 → 孩子重投达上限 → 列表禁用按钮」全链路场景。验证证据：Playwright 或对应 E2E 测试通过。
- [ ] 6.4 全仓回归：运行 `mvn test` 后端全部通过，`pnpm test`（或对应命令）前端全部通过；grep 全仓确认无残留 `parseMaxSubmissions` / `type_config.max_submissions` 读取路径。验证证据：CI 全绿。

```

## openspec/changes/add-task-template-resubmission-controls/specs/task-assignment/spec.md

- Source: openspec/changes/add-task-template-resubmission-controls/specs/task-assignment/spec.md
- Lines: 1-20
- SHA256: fd7a1d70d2afbe52dc3330bc521c729b359b22ef72cd789192e920bb34e2e412

```md
## MODIFIED Requirements

### Requirement: 人工单次分配与数据快照
家长 SHALL 能够使用当前启用且未删除的模板及其启用难度，为当前家庭内的有效孩子创建单次分配。请求 MUST 包含模板标识、难度标识、孩子标识、截止时间和幂等键；截止时间 MUST 是可解析的时间且不得早于请求被系统接收的时刻。创建时系统 MUST 原子固化模板标识与版本、模板名称、分类、说明、图标、难度标识与名称、奖励积分、目标孩子、截止时间、有效迟交策略、创建来源，以及当时有效的模板重复提交控制属性（`snapshot_template_allow_resubmit`、`snapshot_template_max_submissions`、`snapshot_template_points_cap`）。分配初始审核状态 MUST 为 `PENDING`。模板、难度或家庭默认值后续变化 MUST NOT 改写该快照；包括 `allow_resubmit` / `max_submissions` / `points_cap` 后续修改 MUST NOT 影响既有分配的审核期校验口径。

#### Scenario: 创建单次分配
- **WHEN** 家长以合法模板、启用难度、家庭内孩子、未来截止时间和新幂等键创建单次分配
- **THEN** 系统创建一条 `PENDING` 分配，返回分配标识、版本及包含三个重复提交控制 snapshot 字段的完整快照

#### Scenario: 模板修改不影响既有分配
- **WHEN** 分配创建后家长修改模板名称、难度奖励积分或 `allow_resubmit` / `max_submissions` / `points_cap`
- **THEN** 既有分配继续显示创建时的模板名称、奖励积分与重复提交控制快照值，只有之后的新分配使用新值

#### Scenario: 分配输入不合法
- **WHEN** 请求引用已停用难度、已删除模板、其他家庭孩子、无法解析的截止时间或过去截止时间
- **THEN** 系统不创建分配，并分别返回稳定错误码 `TASK_ASSIGNMENT_DIFFICULTY_INACTIVE`、`TASK_ASSIGNMENT_TEMPLATE_INACTIVE`、`TASK_ASSIGNMENT_CHILD_NOT_FOUND` 或 `TASK_ASSIGNMENT_INVALID_DEADLINE`

#### Scenario: 重复提交控制 snapshot 写入
- **WHEN** 家长在 `allow_resubmit=true, max_submissions=3, points_cap=100` 的模板上创建分配
- **THEN** 系统在该分配的 snapshot 字段固化 `snapshot_template_allow_resubmit=true`、`snapshot_template_max_submissions=3`、`snapshot_template_points_cap=100`，且模板后续修改不影响这些值

```

## openspec/changes/add-task-template-resubmission-controls/specs/task-review/spec.md

- Source: openspec/changes/add-task-template-resubmission-controls/specs/task-review/spec.md
- Lines: 1-90
- SHA256: 7d613265ff900d7020f056442648caa3f32b74cdb66b87ac9aeed720bb842049

[TRUNCATED]

```md
## ADDED Requirements

### Requirement: 重复提交次数与积分上限前置校验

`POST /api/task-review/submissions` 接口在执行既有「状态合法、迟交策略、幂等」校验之前，MUST 按分配 snapshot 字段 `snapshot_template_allow_resubmit` 决定是否启用 max / cap 双前置校验。校验口径 MUST 以「同一孩子、同一模板」维度跨 assignment 聚合，覆盖该模板下所有历史的 assignment 与审核记录；REPEAT 类型模板的多期 assignment MUST 被纳入同一上限。

「已通过次数」的统计口径：在当前家庭和当前孩子范围内，针对该模板下所有 assignment 的全部 task_review 记录中 `decision='APPROVED'` 的去重计数；同一 assignment 内被驳回后重投再次通过 MUST 计入新的「已通过」次数。

「累计获得积分」的统计口径：在当前孩子和当前模板范围内，`points_ledger` 中 `type='EARN'` 的所有正积分流水 `amount` 总和，依赖 `business_ref = CONCAT('ATTEMPT_', attempt_id)` 与 `task_attempt.assignment_id`、`task_assignment.template_id` 的链路 JOIN；积分退款（`type='REFUND'`）MUST NOT 从累计值中扣减。

校验规则（按顺序执行）：

1. 当 `snapshot_template_allow_resubmit = false` 时，MUST 跳过 max / cap 校验，沿用既有审核规则
2. 当 `snapshot_template_allow_resubmit = true` 且 `snapshot_template_max_submissions > 0` 且「已通过次数 >= snapshot_template_max_submissions」时，MUST 拒绝提交并返回稳定错误码 `TASK_SUBMISSION_MAX_REACHED`，且 MUST NOT 创建提交尝试
3. 当 `snapshot_template_allow_resubmit = true` 且 `snapshot_template_points_cap > 0` 且「累计获得积分 + 当前 snapshot 难度奖励积分 > snapshot_template_points_cap」时，MUST 拒绝提交并返回稳定错误码 `TASK_SUBMISSION_POINTS_CAP_REACHED`，且 MUST NOT 创建提交尝试
4. `snapshot_template_max_submissions = 0` 表示不限制提交次数
5. `snapshot_template_points_cap = 0` 表示不限制积分

校验失败时，错误响应 MUST 仅包含稳定错误码与通用提示文案，MUST NOT 暴露「当前累计值」或「当前上限值」等内部状态。

#### Scenario: 提交达到最大次数上限被拒绝

- **WHEN** 孩子在 `allow_resubmit=true, max_submissions=3` 的模板上已完成 3 次 APPROVED 审核，尝试再次提交
- **THEN** 系统返回错误码 `TASK_SUBMISSION_MAX_REACHED`，不创建新提交尝试，且当前状态保持原值

#### Scenario: 提交达到积分上限被拒绝

- **WHEN** 孩子在 `allow_resubmit=true, points_cap=100` 的模板上历史累计获得 95 积分，当前 snapshot 难度奖励为 10 积分，尝试提交
- **THEN** 系统返回错误码 `TASK_SUBMISSION_POINTS_CAP_REACHED`，不创建新提交尝试

#### Scenario: 未启用重复提交控制时不应用上限

- **WHEN** 孩子在 `allow_resubmit=false` 的模板上提交
- **THEN** 系统沿用既有审核规则，不应用 max / cap 校验，按既有提交规则处理

#### Scenario: 已通过次数跨周期聚合

- **WHEN** 孩子在 REPEAT 类型模板的第一期 assignment 上通过 2 次，在第二期 assignment 上通过 1 次，模板 `max_submissions=3`，尝试在第二期再次提交
- **THEN** 系统统计「已通过次数 = 3」达到上限，返回错误码 `TASK_SUBMISSION_MAX_REACHED`

#### Scenario: 驳回不计入已通过次数

- **WHEN** 孩子在 `allow_resubmit=true, max_submissions=3` 的模板上有 2 次 APPROVED + 1 次 REJECTED 历史，尝试再次提交
- **THEN** 系统统计「已通过次数 = 2」未达上限，正常接受提交

#### Scenario: max=0 表示不限制

- **WHEN** 孩子在 `allow_resubmit=true, max_submissions=0, points_cap=0` 的模板上已完成多次 APPROVED
- **THEN** 系统接受每次新提交，且 MUST NOT 应用上限校验

#### Scenario: cap 边界值校验

- **WHEN** 孩子在 `allow_resubmit=true, points_cap=100` 的模板上历史累计获得 95 积分，当前 snapshot 难度奖励为 5 积分，尝试提交
- **THEN** 系统判断「95 + 5 > 100」为假（恰好等于不超），接受提交；审核通过后该模板累计积分变为 100，再次提交时 MUST 返回 `TASK_SUBMISSION_POINTS_CAP_REACHED`

#### Scenario: 错误响应不暴露内部状态

- **WHEN** 系统返回 `TASK_SUBMISSION_MAX_REACHED` 或 `TASK_SUBMISSION_POINTS_CAP_REACHED`
- **THEN** 错误响应体 MUST NOT 包含「当前已通过次数」、「当前累计积分」、「配置上限值」等数值字段

### Requirement: 任务分配响应携带可提交状态

`GET /api/task-assignments`（按孩子维度查询）的响应中，每个 assignment MUST 携带综合判断字段 `canSubmit: boolean` 和阻塞原因字段 `submissionBlockReason: 'MAX_REACHED' | 'POINTS_CAP_REACHED' | null`。

`canSubmit` 字段 MUST 同时满足以下条件：

1. 当前审核状态为 `PENDING` 或 `REJECTED`
2. 未被取消
3. 未到截止时间（按有效迟交策略解释）
4. 当 `snapshot_template_allow_resubmit = true` 且 `snapshot_template_max_submissions > 0` 时，「已通过次数 < snapshot_template_max_submissions」
5. 当 `snapshot_template_allow_resubmit = true` 且 `snapshot_template_points_cap > 0` 时，「累计获得积分 + snapshot 难度奖励积分 <= snapshot_template_points_cap」

`submissionBlockReason` 字段 MUST 仅在 `canSubmit=false` 且阻塞原因是 max 或 cap 时填值；其他原因（已审核、已取消、已逾期、`allow_resubmit=false` 时既有审核规则不允许）下 MUST 取 `null`。

字段计算 MUST 使用与 `POST /api/task-review/submissions` 完全相同的聚合口径，保证列表展示与提交校验双写一致。

#### Scenario: 列表返回可提交状态

- **WHEN** 孩子查询任务列表，某 assignment 处于 `PENDING` 状态、`allow_resubmit=true`、当前已通过 2 次、`max_submissions=3`
- **THEN** 系统在该 assignment 响应中返回 `canSubmit=true`、`submissionBlockReason=null`

```

Full source: openspec/changes/add-task-template-resubmission-controls/specs/task-review/spec.md

## openspec/changes/add-task-template-resubmission-controls/specs/task-template/spec.md

- Source: openspec/changes/add-task-template-resubmission-controls/specs/task-template/spec.md
- Lines: 1-91
- SHA256: da81dd2ebb49dc65c84547c5209a5fd9d3a42523861fda17836d71dc45fb058a

[TRUNCATED]

```md
## ADDED Requirements

### Requirement: 重复提交控制的一等属性

任务模板 SHALL 通过三个一等字段表达「同一孩子在该模板上的重复提交控制策略」：

- `allow_resubmit`：布尔值，标记该模板是否启用重复提交上限控制；`FALSE` 时其他两个字段无业务含义；默认 `FALSE`
- `max_submissions`：非负整数，取值范围 `0` 至 `10000`；表示该孩子在该模板上「已通过次数」的最大上限；`0` 表示不限制
- `points_cap`：非负整数，取值范围 `0` 至 `100000000`；表示该孩子在该模板上「累计获得积分」的最大上限；`0` 表示不限制

系统 MUST 在创建模板时持久化这三个字段，MUST 在更新模板时按字段校验规则验证后持久化。`max_submissions` 与 `points_cap` 仅在 `allow_resubmit=true` 时被审核接口读取；`allow_resubmit=false` 时审核接口 MUST 沿用既有审核规则，不应用 max / cap 上限校验。

当 `task_type=STANDING` 时，系统 MUST NOT 再读取 `type_config.max_submissions`；既有 STANDING 模板在数据迁移后将上限值固化到 `max_submissions` 一等字段。系统 MUST 接受旧客户端在 PUT 请求中继续提交 `type_config.max_submissions` 字段，但 MUST 忽略其值且 MUST NOT 据此调整 `max_submissions` 一等字段。

字段验证规则：

- `allow_resubmit` 缺省时取 `FALSE`
- `max_submissions` 缺省或为 `0` 时取 `0`；非负整数且不超过 `10000`
- `points_cap` 缺省或为 `0` 时取 `0`；非负整数且不超过 `100000000`
- 字段类型、范围或语义不合法 MUST 返回稳定错误码 `TASK_TEMPLATE_VALIDATION_FAILED` 和对应字段错误，且 MUST NOT 创建或更新模板

#### Scenario: 默认值创建模板

- **WHEN** 家长创建任务模板时未提交 `allow_resubmit` / `max_submissions` / `points_cap` 字段
- **THEN** 系统以 `allow_resubmit=FALSE`、`max_submissions=0`、`points_cap=0` 创建模板，且不报错

#### Scenario: 配置重复提交控制

- **WHEN** 家长提交 `allow_resubmit=true`、`max_submissions=3`、`points_cap=100` 创建模板
- **THEN** 系统以提交值创建模板，并返回当前版本

#### Scenario: 字段取值越界

- **WHEN** 家长提交 `max_submissions=-1`、`max_submissions=10001`、`points_cap=-5` 或 `points_cap=100000001` 创建或更新模板
- **THEN** 系统返回错误码 `TASK_TEMPLATE_VALIDATION_FAILED` 和对应字段错误，且不创建或更新模板

#### Scenario: STANDING 类型不再读取 type_config

- **WHEN** 家长创建 `task_type=STANDING` 模板并在 `type_config` 中携带 `max_submissions=5`，同时在一等字段中提交 `max_submissions=3`
- **THEN** 系统以一等字段 `max_submissions=3` 持久化，且忽略 `type_config.max_submissions` 的值

#### Scenario: 旧客户端兼容

- **WHEN** 客户端调用 PUT 更新模板时仅在 `type_config.max_submissions` 提交新值，未在一等字段提交
- **THEN** 系统保留模板既有 `max_submissions` 一等字段值不变，且不报错

### Requirement: 重复提交控制属性仅影响新分配

家长 SHALL 能够更新未删除模板的 `allow_resubmit` / `max_submissions` / `points_cap` 字段。每次更新 MUST 增加模板版本并保留审计记录。这三个字段的后续修改 MUST 仅影响修改后创建的新分配的快照值，不得改写既有分配中已固化的 snapshot 字段，也不得追溯改变既有分配的审核期校验口径。

#### Scenario: 更新字段不回溯影响既有分配

- **WHEN** 家长在分配创建后将模板的 `max_submissions` 从 3 改为 5
- **THEN** 系统增加模板版本，之后的新分配使用新快照值 `max_submissions=5`，既有分配的 snapshot 值仍保持 3 且审核期按 3 校验

#### Scenario: 更新字段不影响既有分配积分上限

- **WHEN** 家长在分配创建后将模板的 `points_cap` 从 100 改为 200，孩子在该既有分配上继续提交
- **THEN** 系统按既有分配的 snapshot `points_cap=100` 进行积分上限校验

## MODIFIED Requirements

### Requirement: 创建任务模板与字段验证
家长 SHALL 能够创建任务模板。模板 MUST 包含去除首尾空白后长度为 1 至 100 个字符的名称、长度为 1 至 50 个字符的分类、至少一个启用的难度,以及取值为 `LIMITED`、`REPEAT` 或 `STANDING` 之一的任务类型 `task_type`。说明和图标 MUST 为可选字段,说明最长 2000 个字符,图标标识最长 500 个字符。任务类型配置 `type_config` MUST 与 `task_type` 匹配:`LIMITED` 必须包含 `end_date`,可选 `start_date`;`REPEAT` 必须包含 `frequency` 取值 `DAILY`、`WEEKLY`、`MONTHLY` 或 `YEARLY`,并按 `frequency` 携带合法的 `trigger_day`;`STANDING` 必须不再强制要求 `type_config.max_submissions`,该上限改由 `max_submissions` 一等字段表达（详见「重复提交控制的一等属性」）。系统 MUST 拒绝空白名称、空白分类、超长字段、未知字段类型、不合法难度、未知任务类型、缺失 `type_config`、`type_config` 与 `task_type` 不匹配或子字段不合法,并以稳定错误码 `TASK_TEMPLATE_VALIDATION_FAILED` 返回逐字段错误;验证失败 MUST NOT 创建部分模板或部分难度。

#### Scenario: 使用完整字段创建限时模板
- **WHEN** 家长提交合法的名称、分类、说明、图标、`task_type=LIMITED` 与 `{start_date: "2026-07-20", end_date: "2026-08-20"}`,以及两个合法难度
- **THEN** 系统在一个原子操作中创建模板及其难度,返回模板标识和当前版本

#### Scenario: 使用最小字段创建常驻模板
- **WHEN** 家长提交合法名称、分类、一个合法难度、`task_type=STANDING`,且未提交 `type_config.max_submissions`、说明或图标
- **THEN** 系统创建模板,并将说明、图标保存为未设置,`allow_resubmit` 默认为 `FALSE`

#### Scenario: 创建输入不合法
- **WHEN** 家长提交仅含空白的名称、超过长度上限的说明、奖励积分不合法的难度、未知 `task_type` 或 `type_config` 与 `task_type` 不匹配
- **THEN** 系统返回错误码 `TASK_TEMPLATE_VALIDATION_FAILED` 和对应字段错误,且模板与难度均不落库

### Requirement: 更新模板仅影响未来分配
家长 SHALL 能够更新未删除模板的名称、分类、说明、图标、`type_config` 内字段、难度、`allow_resubmit`、`max_submissions` 与 `points_cap`。每次更新 MUST 增加模板版本并保留审计记录。模板或难度的后续修改 MUST 仅影响修改后创建的新分配,不得改写既有分配中已固化的模板名称、难度、奖励积分、截止时间、重复提交控制属性或其他快照字段。模板的 `task_type` 字段 MUST NOT 可修改;尝试修改 `task_type` MUST 返回稳定错误码 `TASK_TEMPLATE_TYPE_IMMUTABLE`,且不增加模板版本。


```

Full source: openspec/changes/add-task-template-resubmission-controls/specs/task-template/spec.md
