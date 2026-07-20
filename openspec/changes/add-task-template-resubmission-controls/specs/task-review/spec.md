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

#### Scenario: 列表返回已达上限状态

- **WHEN** 孩子查询任务列表，某 assignment 处于 `PENDING` 状态、`allow_resubmit=true`、当前已通过 3 次、`max_submissions=3`
- **THEN** 系统在该 assignment 响应中返回 `canSubmit=false`、`submissionBlockReason='MAX_REACHED'`

#### Scenario: 未启用重复提交控制时不填阻塞原因

- **WHEN** 孩子查询任务列表，某 assignment 处于 `PENDING` 状态、`allow_resubmit=false`
- **THEN** 系统在该 assignment 响应中按既有审核规则计算 `canSubmit`，且 `submissionBlockReason=null`
