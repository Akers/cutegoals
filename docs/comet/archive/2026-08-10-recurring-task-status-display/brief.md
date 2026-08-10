# Outcome

家长端「任务分配」页的任务列表中，重复任务（REPEAT）不再被标记为「已逾期」、不再显示「已逾期」样式；同时每条重复任务卡片显示 [当前提交次数/最大提交次数] 与 [当前已获得积分/积分上限]。

# Scope

- 后端任务列表/详情 API 响应增强（`server/task` 模块 `TaskAssignmentService`）：
  - `enrichAssignment`（776-780 行）：REPEAT 分配一律派生 overdue=false；
  - `getCalendar`（552-558 行）：日历逾期计数使用同一派生逻辑，REPEAT 不计入逾期；
  - 新增聚合查询：按 (childId, templateId) 统计「已通过次数」（task_review decision='APPROVED'）与「累计获得积分」（points_ledger type='EARN'），与 `ResubmissionPolicyEvaluator` 现有口径一致，并在列表/详情响应中返回（批量查询避免 N+1）。
- 家长端任务分配页任务列表：`web/src/parent/pages/index.tsx` 的 `ParentTasksPage`（任务卡片渲染，约 1559-1594 行）：REPEAT 卡片显示 [当前提交次数/最大提交次数] 与 [当前已获得积分/积分上限]；逾期样式随 API overdue 字段变化自动对 REPEAT 失效（孩子端同理，无需改动）。
- 规格修订：task-assignment capability 的逾期派生要求与列表响应要求。

# Non-goals

- 不改变审核状态流转、迟交策略（latePolicy）、积分发放逻辑。
- 不改变 LIMITED / STANDING 任务的逾期标记与显示。
- 不改变孩子端提交时的 max/cap 前置校验行为。
- 不改变重复任务调度器（RepeatTaskScheduler）的 OPEN→EXPIRED 生命周期。

# Acceptance examples

- Given 一条 REPEAT 分配，deadline 已过且状态非 APPROVED，When 家长查看任务分配页任务列表，Then 该卡片不显示「已逾期」文本，也不显示逾期左侧橙色边框样式。
- Given 一条 REPEAT 分配，模板 maxSubmissions=5、pointsCap=100，该孩子在该模板上已有 2 次 APPROVED 审核、累计 EARN 积分 30，When 家长查看任务列表，Then 该卡片显示提交次数「2/5」与积分「30/100」。
- Given 一条 LIMITED 分配且已逾期，When 家长查看任务列表，Then 仍显示「已逾期」文本与逾期样式（行为不变）。

# Constraints and invariants

- 「当前提交次数」口径 MUST 与现有重复提交前置校验一致：按「同一孩子、同一模板」跨 assignment 聚合，仅统计 task_review 中 decision='APPROVED' 的去重计数（见 openspec/specs/task-review/spec.md「重复提交次数与积分上限前置校验」）。
- 「当前已获得积分」口径 MUST 与现有一致：points_ledger 中 type='EARN' 的流水 amount 总和，REFUND 不扣减。
- maxSubmissions=0 表示不限制提交次数；pointsCap=0 表示不限制积分（现有规格语义）。
- 列表接口分页上限 100 条，聚合查询 MUST 避免逐条 N+1 查询导致显著性能退化。

# Decisions

- D1（调查结论，非用户决定）：「当前提交次数」「当前已获得积分」采用与 ResubmissionPolicyEvaluator 相同的 (childId, templateId) 聚合口径，保证显示与提交拦截行为一致。
- D2（调查结论）：task 模块当前不依赖 task-review / points 模块（会形成循环依赖），聚合查询在 task 模块内直接以 SQL 访问同库表 task_review / points_ledger / task_attempt。
- D3（用户决定，2026-08-07）：采用方案A——后端对 REPEAT 分配一律不再派生 overdue=true（API 层统一），家长端/孩子端/日历逾期计数同步生效；同步修订 task-assignment 规格中的逾期派生要求。
- D4（用户确认，2026-08-07）：最大提交次数/积分上限为 0（不限制）或空时以「不限」渲染（如「2/不限」「30/不限」）；REPEAT 卡片进度显示不受模板 allow_resubmit 开关影响。

# Open questions

（无；用户已于 2026-08-07 确认共享理解摘要）

# Verification expectations

- 前端单元测试（vitest）：ParentTasksPage 中 REPEAT 卡片不渲染逾期样式与文本，且渲染提交次数/积分上限；LIMITED 逾期卡片行为不变。
- 后端测试：任务列表 API 对 REPEAT 分配的 overdue 派生结果与新字段（提交次数、已获积分）返回值符合预期。
- 既有相关测试（RepeatTaskSchedulerTest、TaskAssignmentServiceTest、ParentTasksPage.test.tsx 等）保持通过。
