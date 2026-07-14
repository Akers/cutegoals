# task-review Specification

## Purpose
TBD - created by archiving change core-features. Update Purpose after archive.
## Requirements
### Requirement: 审核生命周期、家庭边界与角色权限
每条未取消任务分配的当前审核状态 MUST 遵循 `PENDING → SUBMITTED → APPROVED / REJECTED`。孩子 SHALL 仅能提交分配给自己的任务；当前家庭的家长 SHALL 仅能审核该家庭孩子的提交。系统 MUST 从已认证身份确定家庭和孩子，不得信任客户端提交的家庭标识。越权操作 MUST 返回稳定错误码 `TASK_REVIEW_FORBIDDEN`；不存在或不属于当前家庭的资源 MUST 返回 `TASK_REVIEW_NOT_FOUND` 且不得泄露其他家庭数据。

#### Scenario: 孩子提交自己的任务
- **WHEN** 已认证孩子提交当前状态为 `PENDING` 且分配给自己的未取消任务
- **THEN** 系统接受提交并把当前状态转换为 `SUBMITTED`

#### Scenario: 孩子提交其他孩子的任务
- **WHEN** 孩子尝试提交分配给同家庭另一孩子或不可见孩子的任务
- **THEN** 系统返回错误码 `TASK_REVIEW_FORBIDDEN`，且不创建提交尝试或修改状态

#### Scenario: 家长审核本家庭提交
- **WHEN** 当前家庭家长审核该家庭内当前状态为 `SUBMITTED` 的任务
- **THEN** 系统按审核决定执行 `APPROVED` 或 `REJECTED` 转换并记录审核人

### Requirement: 每次提交形成不可覆盖的尝试
孩子 SHALL 能够为自己的 `PENDING` 任务创建首次提交尝试，并在任务被驳回且完成修改后创建后续提交尝试。每次成功提交 MUST 新建不可覆盖的尝试，包含单调递增的尝试序号、分配标识和版本、提交内容、提交时间、截止时间快照、是否迟交以及当时有效迟交策略。提交说明 MUST 最长为 2000 个字符，佐证引用 MUST 最多 10 个且每个最长 500 个字符；不合法输入 MUST 返回 `TASK_SUBMISSION_VALIDATION_FAILED`。提交成功 MUST NOT 发放积分。

#### Scenario: 首次提交
- **WHEN** 孩子以合法内容提交自己的 `PENDING` 任务
- **THEN** 系统创建序号为 1 的不可变提交尝试，将当前状态设为 `SUBMITTED`，且积分余额不变

#### Scenario: 提交内容不合法
- **WHEN** 孩子提交超过 2000 字符的说明或超过 10 个佐证引用
- **THEN** 系统返回错误码 `TASK_SUBMISSION_VALIDATION_FAILED`，且不创建尝试、不改变状态

#### Scenario: 重复提交当前已提交任务
- **WHEN** 孩子对当前状态为 `SUBMITTED` 的任务发起新的非幂等重试提交
- **THEN** 系统返回错误码 `TASK_REVIEW_INVALID_STATE`，且保留原提交尝试不变

### Requirement: 提交请求必须幂等
每次提交请求 MUST 携带长度为 1 至 128 个字符的客户端请求标识。系统 MUST 在孩子和提交操作范围内唯一约束该标识，并保存请求语义摘要。相同标识及相同内容的重试 MUST 返回首次创建的同一尝试；相同标识用于不同分配或不同内容 MUST 返回 `TASK_SUBMISSION_IDEMPOTENCY_CONFLICT`。失败并整体回滚的提交 MUST NOT 占用可成功重试的幂等结果。

#### Scenario: 提交响应丢失后重试
- **WHEN** 孩子以相同请求标识和相同内容重试已成功的提交
- **THEN** 系统返回首次创建的同一尝试和 `SUBMITTED` 状态，且不会新增第二次尝试

#### Scenario: 提交标识复用于不同内容
- **WHEN** 孩子以已使用的请求标识提交另一任务或修改后的内容
- **THEN** 系统返回错误码 `TASK_SUBMISSION_IDEMPOTENCY_CONFLICT`，且所有任务与尝试均保持不变

### Requirement: 截止边界与迟交拒绝
系统 MUST 使用分配中固化或经家长显式覆盖的有效迟交策略校验每次提交。提交时间严格晚于截止时间时 MUST 标记为迟交；提交时间恰好等于截止时间时 MUST 视为按时。有效策略为 `REJECT` 的迟交请求 MUST 返回稳定错误码 `TASK_SUBMISSION_LATE_NOT_ALLOWED`，不得创建尝试或改变审核状态；有效策略为 `ALLOW` 时 SHALL 接受并在尝试中记录迟交。时刻比较 MUST 基于无歧义时刻，默认实例时区 `Asia/Shanghai` 仅用于解释无偏移量本地输入和日期筛选。

#### Scenario: 截止时刻提交
- **WHEN** 系统记录的提交时刻恰好等于分配截止时间
- **THEN** 系统将该尝试标记为按时，并按正常提交规则处理

#### Scenario: 默认策略拒绝迟交
- **WHEN** 分配有效迟交策略为 `REJECT` 且提交时刻晚于截止时间
- **THEN** 系统返回错误码 `TASK_SUBMISSION_LATE_NOT_ALLOWED`，当前状态保持 `PENDING` 或 `REJECTED`，且不新增尝试

#### Scenario: 单条覆盖允许迟交
- **WHEN** 分配有效迟交策略已由家长覆盖为 `ALLOW` 且孩子在截止时间后提交
- **THEN** 系统创建标记为迟交的新尝试并将当前状态设为 `SUBMITTED`

### Requirement: 审核决定形成不可覆盖的历史
家长 SHALL 能够对当前 `SUBMITTED` 的明确尝试作出批准或驳回决定。每个成功决定 MUST 新建不可覆盖的审核记录，包含分配标识、尝试标识与序号、决定、审核人、审核时间和原因。驳回原因 MUST 去除首尾空白后长度为 1 至 1000 个字符；批准原因 MUST 可选且最长为 1000 个字符。缺失或不合法原因 MUST 返回 `TASK_REVIEW_REASON_REQUIRED` 或 `TASK_REVIEW_VALIDATION_FAILED`，且状态与历史不得变化。

#### Scenario: 批准当前提交
- **WHEN** 家长批准当前 `SUBMITTED` 尝试并提交合法的可选原因
- **THEN** 系统创建不可变批准记录并将当前状态转换为 `APPROVED`

#### Scenario: 驳回时提供原因
- **WHEN** 家长以非空且不超过 1000 字符的原因驳回当前 `SUBMITTED` 尝试
- **THEN** 系统创建不可变驳回记录并将当前状态转换为 `REJECTED`

#### Scenario: 驳回时缺少原因
- **WHEN** 家长驳回提交但原因缺失、为空或仅含空白
- **THEN** 系统返回错误码 `TASK_REVIEW_REASON_REQUIRED`，提交仍为 `SUBMITTED` 且不创建审核记录

### Requirement: 驳回保持可观察并允许修改后再提交
`REJECTED` MUST 作为当前状态保持可观察，系统 MUST NOT 在驳回后自动将任务重置为 `PENDING`。孩子 SHALL 能够修改提交内容并从 `REJECTED` 创建下一序号的提交尝试，使当前状态再次成为 `SUBMITTED`；此前被驳回的尝试、原因、审核人和时间 MUST 永久保留。新尝试 MUST 独立审核，批准新尝试不得覆盖旧驳回历史。

#### Scenario: 驳回后仍显示驳回状态
- **WHEN** 家长驳回一次提交且孩子尚未再次提交
- **THEN** 任务当前状态保持 `REJECTED`，家长和该孩子均可查看驳回原因及对应尝试
#### Scenario: 修改后再次提交

- **WHEN** 孩子在 `REJECTED` 状态下修改完成说明或佐证并提交合法的新请求
- **THEN** 系统创建下一序号的不可变尝试，将当前状态设为 `SUBMITTED`，且旧尝试仍显示为已驳回

#### Scenario: 驳回后重提但策略禁止迟交

- **WHEN** 分配有效迟交策略已改为 `REJECT` 且孩子在 `REJECTED` 状态下重提时已逾期
- **THEN** 系统返回错误码 `TASK_SUBMISSION_LATE_NOT_ALLOWED`，当前状态保持 `REJECTED`，且不新增尝试

### Requirement: 批准与积分发放必须同事务
批准当前提交时，系统 MUST 使用任务分配中固化的奖励积分创建类型为 `EARN` 的不可变积分流水，并更新该孩子积分账户投影。状态转换为 `APPROVED`、批准审核记录、积分流水和余额更新 MUST 在同一事务内全部成功；任何一步失败 MUST 整体回滚，使提交继续保持 `SUBMITTED`。积分流水的唯一业务引用 MUST 指向被批准的提交尝试，模板或难度的当前值不得影响奖励金额。

#### Scenario: 批准发放快照奖励
- **WHEN** 当前提交对应分配快照奖励为 3 分而模板当前奖励已改为 5 分
- **THEN** 系统在同一事务中批准该尝试、创建唯一的 3 分 `EARN` 流水并更新余额

#### Scenario: 积分记账失败
- **WHEN** 批准事务在创建积分流水或更新余额时失败
- **THEN** 系统回滚审核记录和状态变化，任务保持 `SUBMITTED`，且不存在部分积分结果

### Requirement: 同一提交只允许一次成功审核
每个审核请求 MUST 指明预期提交尝试，并携带长度为 1 至 128 个字符的审核请求标识。相同标识与相同决定的重试 MUST 返回首次结果；相同标识用于不同决定 MUST 返回 `TASK_REVIEW_IDEMPOTENCY_CONFLICT`。系统 MUST 使用一致性控制保证同一提交尝试最多一个成功审核决定；后到或并发失败的决定 MUST 返回 `TASK_REVIEW_ALREADY_DECIDED`，且不得创建第二条审核记录或第二笔积分流水。针对已不再是当前提交的尝试进行审核 MUST 返回 `TASK_REVIEW_STALE_ATTEMPT`。

#### Scenario: 批准请求重试
- **WHEN** 客户端以相同审核请求标识重试一次已成功批准
- **THEN** 系统返回首次批准结果，且仅有一条审核记录和一笔 `EARN` 流水

#### Scenario: 两个家长并发审核同一提交
- **WHEN** 两个家长并发对同一提交尝试分别批准和驳回
- **THEN** 系统仅提交一个决定，另一个返回错误码 `TASK_REVIEW_ALREADY_DECIDED`，最终状态、审核历史和积分结果与成功决定一致

#### Scenario: 两个批准并发发放积分
- **WHEN** 两个批准请求以不同请求标识并发处理同一提交尝试
- **THEN** 仅一个批准成功且仅创建一笔具有唯一业务引用的 `EARN` 流水，另一请求返回 `TASK_REVIEW_ALREADY_DECIDED`

#### Scenario: 审核过期尝试
- **WHEN** 家长基于旧尝试页面发起审核，而该任务已在旧尝试驳回后产生了新的当前提交
- **THEN** 系统返回错误码 `TASK_REVIEW_STALE_ATTEMPT`，且旧、新尝试和当前状态均不改变

### Requirement: 取消任务不得继续提交或审核
取消标记 MUST 独立于审核状态并保留此前全部尝试及审核历史。孩子 MUST NOT 提交已取消任务，家长 MUST NOT 审核已取消任务；这两类请求 MUST 返回稳定错误码 `TASK_ASSIGNMENT_CANCELLED`。取消与审核并发时系统 MUST 保证只有一个状态变更事务成功，且不得出现已取消任务发放积分或已批准任务被取消的结果。

#### Scenario: 提交已取消任务
- **WHEN** 孩子尝试提交一条已取消但保留 `PENDING` 或 `REJECTED` 历史状态的任务
- **THEN** 系统返回错误码 `TASK_ASSIGNMENT_CANCELLED`，且不创建提交尝试

#### Scenario: 审核已取消的已提交任务
- **WHEN** 家长尝试审核一条取消前已存在 `SUBMITTED` 尝试的任务
- **THEN** 系统返回错误码 `TASK_ASSIGNMENT_CANCELLED`，保留原尝试且不创建审核记录或积分流水

### Requirement: 分页查询待审核与审核历史
家长 SHALL 能够分页查询当前家庭的待审核队列和审核历史；孩子 SHALL 能够只读查询自己的提交与审核历史。待审核队列 MUST 仅包含未取消且当前状态为 `SUBMITTED` 的任务，按提交时间升序、尝试标识升序稳定排序。审核历史 SHALL 支持按孩子、决定、审核人以及本地提交或审核日期范围筛选，按审核时间降序、审核记录标识升序稳定排序。默认页大小 MUST 为 20，页大小 MUST 为 1 至 100；日期范围 MUST 使用实例时区，默认 `Asia/Shanghai`，并包含起止本地自然日。结果 MUST 包含任务和奖励快照、尝试序号、迟交标记、决定、原因及积分流水引用。无效查询 MUST 返回 `TASK_REVIEW_INVALID_QUERY`。

#### Scenario: 查看待审核队列
- **WHEN** 家长查询第一页待审核任务且未提交筛选条件
- **THEN** 系统返回当前家庭最多 20 条未取消 `SUBMITTED` 任务，并按提交时间和尝试标识稳定排序

#### Scenario: 按孩子和驳回结果查询历史
- **WHEN** 家长按某孩子和 `REJECTED` 决定筛选审核历史
- **THEN** 系统仅返回当前家庭内匹配的不可变审核记录及其对应尝试快照

#### Scenario: 孩子查看自己的驳回历史
- **WHEN** 孩子查询自己的提交与审核历史
- **THEN** 系统仅返回该孩子的可见记录，包括仍可观察的旧驳回原因，不返回其他孩子记录

#### Scenario: 审核日期边界
- **WHEN** 家长筛选 2026-04-01 至 2026-04-01 的本地审核日期
- **THEN** 系统包含审核时刻落在 `Asia/Shanghai` 当日 00:00:00 至次日 00:00:00 前的记录

#### Scenario: 查询参数不合法
- **WHEN** 请求包含页大小 0、未知决定值或开始日期晚于结束日期
- **THEN** 系统返回错误码 `TASK_REVIEW_INVALID_QUERY`，且不返回部分结果

### Requirement: 提交与审核历史不可删除
提交尝试和审核记录 MUST 作为审计历史永久保留，MUST NOT 被更新覆盖或物理删除。任务模板停用或删除、难度停用、任务分配取消以及孩子显示名称变化 MUST NOT 破坏历史可读性；历史查询 MUST 使用当时保存的任务、难度、奖励、提交和审核快照。任何更正 MUST 通过后续允许的业务事件表达，不得篡改原记录。

#### Scenario: 模板删除后查看审核历史
- **WHEN** 家长查询一条其模板已逻辑删除的历史审核记录
- **THEN** 系统仍返回审核发生时的模板名称、难度、奖励、提交内容和审核决定快照

#### Scenario: 尝试删除审核历史
- **WHEN** 任一角色请求删除或覆盖既有提交尝试或审核记录
- **THEN** 系统拒绝请求并返回错误码 `TASK_REVIEW_HISTORY_IMMUTABLE`，原记录保持不变

