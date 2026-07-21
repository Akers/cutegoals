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

- 在 `task_assignment.submission_count` 上做单 assignment 内统计 → 拒绝，原因是无法表达跨周期上限（用户已确认需要按 child + template 维度）
- 在 `task_template` 上新增计数器列 → 拒绝，原因是需要维护「按孩子」维度，要么破坏模板表范式、要么需要额外子表
- 在 task_review 接口实时统计（不缓存） → 采纳，原因是私有化部署单家庭规模有限，N=几个孩子、每个孩子几十次审核，JOIN 成本可忽略；如未来出现性能问题再加 Redis 缓存

### D4：两道前置校验都在 submit 入口执行（不在 assignment 创建时执行）

**选择**：`POST /api/task-review/submissions` 在执行现有「状态合法 / 迟交策略 / 幂等」校验之前，先做 max / cap 校验。`GET /api/task-assignments` 在响应中通过同步聚合返回 `canSubmit` 与 `submissionBlockReason`。

**理由**：

- 校验口径单一：submit 路径与列表查询路径使用同一聚合查询方法，避免双写漂移
- 家长在审核期通过 reject 不会消耗 max 配额（max 只统计 APPROVED）
- 现有迟交策略 / 幂等校验位于 submit 路径中段，把 max / cap 放在最前面不会破坏后续逻辑

**备选方案**：

- 在 assignment 创建时拒绝分配 → 拒绝，原因是「积分上限」可能因为孩子后续 APPROVED 才到达，创建期无法预测；且会让家长无法在分配列表里看到这些任务（破坏任务可见性）
- 在 task_review approve 入口校验 → 拒绝，原因是家长审核后才发现超上限、用户体验差，且会破坏审核幂等

### D5：错误码 `TASK_SUBMISSION_MAX_REACHED` / `TASK_SUBMISSION_POINTS_CAP_REACHED` 与现有 `TASK_REVIEW_*` 错误码族并存

**选择**：新增两个稳定错误码，前缀沿用 `TASK_SUBMISSION_*`（与现有 `TASK_SUBMISSION_VALIDATION_FAILED` / `TASK_SUBMISSION_LATE_NOT_ALLOWED` / `TASK_SUBMISSION_IDEMPOTENCY_CONFLICT` 一致）。

**理由**：

- 校验发生在 submit 路径，前缀语义正确
- 客户端可以按错误码精确显示提示文案

**备选方案**：

- 复用 `TASK_SUBMISSION_VALIDATION_FAILED` → 拒绝，原因是该错误码附带逐字段错误，与上限语义不符
- 在 `TASK_REVIEW_INVALID_STATE` 上扩展原因字段 → 拒绝，原因是破坏错误码稳定性

### D6：Flyway V14 单脚本完成 DDL + 数据回填

**选择**：在 V14 脚本中按顺序执行：

1. `ALTER TABLE task_template ADD COLUMN allow_resubmit BOOLEAN NOT NULL DEFAULT FALSE`
2. `ALTER TABLE task_template ADD COLUMN max_submissions INT NOT NULL DEFAULT 0`
3. `ALTER TABLE task_template ADD COLUMN points_cap INT NOT NULL DEFAULT 0`
4. `ALTER TABLE task_assignment ADD COLUMN snapshot_template_allow_resubmit BOOLEAN DEFAULT NULL`
5. `ALTER TABLE task_assignment ADD COLUMN snapshot_template_max_submissions INT DEFAULT NULL`
6. `ALTER TABLE task_assignment ADD COLUMN snapshot_template_points_cap INT DEFAULT NULL`
7. **数据回填**：`UPDATE task_template SET allow_resubmit = TRUE, max_submissions = COALESCE(JSON_EXTRACT(type_config, '$.max_submissions'), 0) WHERE task_type = 'STANDING' AND JSON_EXTRACT(type_config, '$.max_submissions') IS NOT NULL`
8. 不删除 `type_config.max_submissions` 字段（保持只读兼容）

**理由**：

- Flyway 单脚本事务原子性，DDL + DML 一起成功或一起回滚
- 数据回填用 `JSON_EXTRACT`（MySQL）/ `type_config->>'$.max_submissions'`（PostgreSQL）/ `JSON_EXTRACT` 兼容 H2 PostgreSQL 模式；脚本中用方言判断或预留两套实现
- 旧 `type_config.max_submissions` 保留作为历史字段不破坏旧客户端

**备选方案**：

- 拆成 V14（DDL） + V15（DML） → 拒绝，原因是 DDL 后未回填会让 STANDING 模板短时间内表现为「无上限」，破坏产品预期
- 删除 `type_config.max_submissions` → 拒绝，原因是旧客户端可能仍读取该字段，删除会破坏向后兼容

### D7：前端表单字段层级——「允许重复提交」是顶层复选框，max/cap 是条件显示的 InputNumber

**选择**：在 `ParentTemplatesPage` 表单中：

- 「允许重复提交」是顶层 Checkbox，默认不勾选
- 勾选后条件显示「最大提交次数」和「积分上限」两个 InputNumber（min=0, max=10000/100000000, 默认 0）
- 不勾选时 max/cap 字段被隐藏但保留 0 值，保证后端校验通过
- STANDING 子表单中的「无限提交」复选框 + 「最大提交次数」InputNumber（在 `TaskTypeConfigForms.tsx`）下线，由顶层字段统一表达

**理由**：

- 顶层复选框直接对应「该模板是否允许重复提交」语义，与任务类型无关
- max/cap 同时影响所有任务类型，子表单层级表达会割裂
- STANDING 子表单中的「无限提交」语义在顶层是「不勾选允许重复提交」即可表达，子表单字段冗余

**备选方案**：

- 在每个任务类型的子表单中重复字段 → 拒绝，原因是字段语义与任务类型无关，重复会导致用户困惑
- 永远显示 max/cap 字段（不勾选允许重复提交时禁用） → 拒绝，原因是 UI 噪音大、与「先勾选再配置」直觉不符

### D8：孩子端任务列表通过 `canSubmit` 字段禁用提交按钮

**选择**：`GET /api/task-assignments` 返回每个 assignment 时新增两个字段：

- `canSubmit: boolean` — 综合判断（status in [PENDING, REJECTED] && 未取消 && 未到截止时间 && 未达 max/cap 上限）
- `submissionBlockReason: 'MAX_REACHED' | 'POINTS_CAP_REACHED' | null` — 仅当 canSubmit=false 且原因是 max/cap 时填值

前端 `ChildTasksPage`：

- `canSubmit=false` 时按钮 disabled
- 鼠标 hover 显示 tooltip：「已达到最大提交次数」/「已达到积分上限」
- 视觉上对已达上限任务做轻微灰色处理（与现有「已逾期」高亮不同的样式）

**理由**：

- 后端聚合逻辑集中在 assignment service 一处，前端不做业务判断
- `canSubmit` 是综合字段，未来可以扩展更多原因（迟交、取消等）而不破坏 API

**备选方案**：

- 前端实时调用 `POST /api/task-review/submissions` 探测 → 拒绝，原因是会创建无意义尝试记录或破坏幂等
- 暴露 raw count / cap 让前端判断 → 拒绝，原因是前端业务规则双写、漂移风险高

### D9：STANDING 兼容路径——既有 assignment 的 snapshot 字段为 NULL 时回退到 template 当前值

**选择**：审核期校验读取 snapshot 字段时：

- snapshot 字段非 NULL → 使用 snapshot 值
- snapshot 字段为 NULL（V14 迁移前的旧 assignment）→ 使用 template 当前值

**理由**：

- 旧 assignment 在 V14 迁移时不需要回填 snapshot 字段（NULL 表示「使用当前模板」）
- 这种回退仅对 V14 之前已存在的 assignment 有效，新生 assignment 都会写入 snapshot

**备选方案**：

- 在 V14 脚本中回填所有既有 assignment 的 snapshot 字段 → 拒绝，原因是回填时模板可能已被多次编辑，无法准确还原「分配创建时的模板状态」
- 拒绝旧 assignment 提交、要求重新创建 → 拒绝，原因是破坏用户体验且无业务必要

## Risks / Trade-offs

- **[风险] 跨 assignment 聚合查询性能**：每次 submit 和 list 都会触发 JOIN。**缓解**：在 `task_review.decision` + `task_attempt.assignment_id` + `task_assignment.template_id` + `task_assignment.child_id` + `points_ledger.business_ref` 上确保索引覆盖；私有化部署单家庭规模有限（典型 N < 10 孩子、每孩子 N < 1000 审核记录），单次查询预期 <10ms。
- **[风险] Flyway V14 跨方言兼容**：JSON 函数在不同数据库语法不同。**缓解**：脚本内通过 Flyway 占位符或拆分为方言专属脚本（参考 V10/V11/V12 既有模式，单脚本多方言兼容）；本地测试矩阵覆盖 H2（PostgreSQL 模式） + MySQL 8 + PostgreSQL 15。
- **[风险] STANDING 旧客户端 BREAKING**：旧客户端调用 PUT 接口继续提交 `type_config.max_submissions`，服务端忽略时是否报错？**缓解**：服务端继续接受 `type_config.max_submissions` 字段但不读取，并在响应中通过 `X-Deprecation` header 提示；同时更新公开 API 文档。前端一次性升级即可。
- **[风险] 提交被拒绝但积分流水已存在**：如果家长在孩子 APPROVED 后调整 `points_cap` 到比历史累计更低的值，孩子再次提交时会被 cap 拒绝——这是预期行为，但前端需要清晰提示。**缓解**：错误响应中携带「当前累计 / 当前上限」上下文（敏感字段需评估），或仅返回 generic 提示文案由家长自查。
- **[风险] 并发 submit race**：孩子端快速双击提交按钮，可能两次都通过 max/cap 校验（count=N, max=N+1）并产生两次审核记录，导致最终通过数 = N+2 超出上限。**缓解**：现有 idempotency key 已覆盖此场景；同时审核决定路径有 `TASK_REVIEW_ALREADY_DECIDED` 守卫。新增的 max/cap 校验不引入新的并发风险。
- **[权衡] canSubmit 字段使 GET /api/task-assignments 响应变大**：每个 assignment 增加 ~100 字节。**缓解**：私有化部署网络成本低，可忽略。
- **[权衡] 不实现「积分已达上限自动隐藏任务」**：用户原话是「显示为不可提交」，所以保留可见性但禁用按钮。**缓解**：让孩子端用户能感知到任务存在但不能提交，避免困惑。

## Migration Plan

**部署步骤**：

1. 升级到包含 V14 迁移的版本
2. 启动后端服务，Flyway 自动执行 V14
3. STANDING 类型模板自动迁移到新字段
4. 升级前端（家长端 + 孩子端）到对应版本
5. 旧客户端在过渡期内仍可访问，但提交 `type_config.max_submissions` 不再生效

**回滚策略**：

- V14 不删除任何字段、不修改既有数据，回滚 Flyway 后 schema 兼容（旧版本忽略新列）
- 已经回填的 STANDING 模板 `max_submissions` 在新列上，旧版本读取 `type_config.max_submissions` 仍得到原值（因为未删除）
- 已经创建的 assignment snapshot 字段在旧版本中被忽略，无副作用

**数据一致性验证**：

- 部署后查询验证：所有 STANDING 模板的 `allow_resubmit=true` 且 `max_submissions = COALESCE(原 type_config 值, 0)`
- LIMITED / REPEAT 模板的 `allow_resubmit=false` 且 `max_submissions=0` 且 `points_cap=0`

## Open Questions

- **OQ1**：错误响应是否携带「当前累计 / 当前上限」上下文？例如 `{"errorCode": "TASK_SUBMISSION_POINTS_CAP_REACHED", "current": 100, "cap": 100}`。**默认决策**：仅返回错误码 + 通用文案，不携带数值（避免泄漏内部状态）；如果 UX 反馈不够清晰再调整。
- **OQ2**：家长编辑模板 `points_cap` 调低后，孩子已有 assignment 的 snapshot 仍是旧 cap 值，导致孩子继续可提交到达旧 cap。**默认决策**：这是预期行为（snapshot 固化语义），家长需要手动取消旧 assignment 或等新 assignment 生效。
- **OQ3**：未来是否需要「重置上限」运营操作？例如孩子换季度后清零。**默认决策**：本次不实现，未来通过新 change 引入。
