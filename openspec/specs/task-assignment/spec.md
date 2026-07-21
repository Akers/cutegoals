# task-assignment Specification

## Purpose
TBD - created by archiving change core-features. Update Purpose after archive.
## Requirements
### Requirement: 分配的家庭边界与角色权限
MVP 每个实例 MUST 仅服务一个家庭。只有该家庭的家长角色 SHALL 创建、批量创建、周期生成、修改、取消和查询家庭内全部任务分配；孩子角色 SHALL 仅能查询分配给自己的任务。系统 MUST 从已认证身份确定家庭和孩子，不得信任客户端传入的家庭标识或用孩子标识扩大访问范围。越权操作 MUST 返回稳定错误码 `TASK_ASSIGNMENT_FORBIDDEN`；不存在或不属于当前家庭的分配 MUST 返回 `TASK_ASSIGNMENT_NOT_FOUND`，且不得泄露其他家庭数据。

#### Scenario: 家长为本家庭孩子分配任务
- **WHEN** 已认证家长为当前家庭内的有效孩子创建任务分配
- **THEN** 系统执行创建并记录创建人、创建时间和来源

#### Scenario: 孩子查询其他孩子的分配
- **WHEN** 孩子请求查询或修改不属于自己的任务分配
- **THEN** 系统拒绝请求并返回错误码 `TASK_ASSIGNMENT_FORBIDDEN`，且不返回该分配的任何字段

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

### Requirement: 人工创建请求幂等
所有人工单次和批量创建请求 MUST 携带长度为 1 至 128 个字符的幂等键。系统 MUST 在家庭和操作类型范围内唯一约束幂等键，并保存请求语义摘要及成功响应。同一幂等键和相同语义请求的重试 MUST 返回首次成功结果且不得新增分配；同一幂等键被用于不同语义请求 MUST 返回稳定错误码 `TASK_ASSIGNMENT_IDEMPOTENCY_CONFLICT`。首次事务失败 MUST 不得占用可成功重试的幂等结果。

#### Scenario: 单次分配因超时重试
- **WHEN** 客户端以相同幂等键和相同请求内容重试一个已成功的单次分配请求
- **THEN** 系统返回首次创建的同一分配标识，且数据库中仅存在一条对应分配

#### Scenario: 幂等键复用于不同请求
- **WHEN** 客户端以已成功使用的幂等键改为另一孩子、模板、难度或截止时间再次请求
- **THEN** 系统返回错误码 `TASK_ASSIGNMENT_IDEMPOTENCY_CONFLICT`，且不创建任何新分配

#### Scenario: 失败事务后重试
- **WHEN** 首次请求在任何分配落库前整体回滚，随后客户端以相同幂等键重试合法请求
- **THEN** 系统允许重试成功并仅创建一次结果

### Requirement: 原子批量分配

家长 SHALL 能够为一个或多个家庭内孩子按本地日期范围批量创建分配。起止日期 MUST 使用实例时区解释且包含两端，开始日期不得晚于结束日期，单次范围最长 MUST 为 366 个本地日；每个生成项 MUST 具有明确截止本地时间并转换为可持久化时刻。批量请求 MUST 使用一个幂等键，且在任何项目验证或写入失败时整体回滚，不得返回未声明的部分成功。

#### Scenario: 按包含两端的日期范围批量分配
- **WHEN** 家长在 `Asia/Shanghai` 实例中为一个孩子提交 2026-04-08 至 2026-04-14 的每日批量分配
- **THEN** 系统原子创建 7 条分配，每条分别固化对应本地日期的截止时间和创建时模板快照

#### Scenario: 批量项目中存在无效孩子
- **WHEN** 批量请求包含一个有效孩子和一个不属于当前家庭的孩子
- **THEN** 系统返回错误码 `TASK_ASSIGNMENT_CHILD_NOT_FOUND`，且两个孩子均不创建任何分配

#### Scenario: 日期范围超过上限
- **WHEN** 家长提交超过 366 个本地日或开始日期晚于结束日期的批量范围
- **THEN** 系统返回错误码 `TASK_ASSIGNMENT_INVALID_DATE_RANGE`，且不创建任何分配

#### Scenario: 批量请求中的 childIds 为 JSON 整数数组
- **WHEN** 家长提交批量分配请求，childIds 数组元素为 JSON 整数（如 `[1]`）
- **THEN** 系统正确解析为长整数，不抛出 `ClassCastException`，并正常创建分配

### Requirement: 周期生成必须确定且幂等
系统 SHALL 根据模板的可选周期规则为指定孩子、难度和本地日期范围生成分配。周期生成 MUST 使用实例时区，默认 `Asia/Shanghai`，并仅为规则命中的本地日期创建分配。每个周期实例 MUST 具有由家庭、孩子、模板和本地发生日期确定的唯一业务键；重复运行、任务调度重试或多个生成器并发执行 MUST 对同一业务键最多创建一条分配。周期生成时 MUST 固化当时有效的模板与难度快照；已停用或删除的模板和难度 MUST 不再生成新分配，并在结果中报告跳过原因。

#### Scenario: 按周期规则生成一周任务
- **WHEN** 家长为每日规则模板、一个孩子和 2026-04-08 至 2026-04-14 的范围触发周期生成
- **THEN** 系统为七个本地发生日期各创建一条分配，并返回创建数与跳过数

#### Scenario: 重跑相同周期范围
- **WHEN** 相同模板、孩子和日期范围的周期生成被重复执行
- **THEN** 系统返回既有发生项为已存在，且不创建任何重复分配

#### Scenario: 两个生成器并发创建同一发生项
- **WHEN** 两个执行者并发生成同一家庭、孩子、模板和本地发生日期
- **THEN** 唯一业务键只允许一个创建成功，另一个复用已存在结果，最终仅有一条分配

#### Scenario: 模板没有周期规则
- **WHEN** 家长对未配置周期规则的模板请求周期生成
- **THEN** 系统拒绝请求并返回错误码 `TASK_ASSIGNMENT_RECURRENCE_NOT_CONFIGURED`

### Requirement: 截止时间、逾期标记与迟交策略
所有时刻 MUST 以无歧义时刻保存，日历日期和未携带偏移量的本地时间 MUST 按实例时区解释，默认时区为 `Asia/Shanghai`。对未取消且当前状态不是 `APPROVED` 的分配，`overdue` MUST 在当前时刻严格晚于截止时间时派生为真；恰好等于截止时间时 MUST 为假。逾期 MUST 仅为派生标记，不得自动删除分配、改写 `PENDING`、`SUBMITTED` 或 `REJECTED` 状态，也不得自动发放或扣除积分。每条分配 MUST 在创建时从家庭默认值固化有效迟交策略 `ALLOW` 或 `REJECT`，家长 SHALL 能够对单条未批准分配显式覆盖该策略；家庭默认值后续变化 MUST 仅影响新分配。

#### Scenario: 跨过截止时间边界
- **WHEN** 一条未取消且未批准分配的当前时刻从等于截止时间推进到晚于截止时间
- **THEN** `overdue` 从假变为真，而分配审核状态和历史保持不变

#### Scenario: 家庭默认拒绝迟交但单条允许
- **WHEN** 家庭默认策略为 `REJECT`，家长在该分配上覆盖为 `ALLOW`
- **THEN** 系统保存单条覆盖及审计，之后该分配按 `ALLOW` 校验迟交，其他既有分配不受影响

#### Scenario: 家庭默认策略后续改变
- **WHEN** 家长在一条分配创建后修改家庭默认迟交策略
- **THEN** 该分配继续使用创建时固化或之后被显式覆盖的策略，新分配使用新的家庭默认值

### Requirement: 修改未进入审核中的分配
家长 SHALL 能够修改未取消且当前状态为 `PENDING` 或 `REJECTED` 的分配之难度、截止时间和单条迟交策略。改选难度 MUST 从当前启用难度重新固化难度名称和奖励积分；修改 MUST 增加分配版本并记录变更前后值。当前状态为 `SUBMITTED` 或 `APPROVED` 的分配 MUST NOT 被修改。基于过期版本的并发修改 MUST 返回 `TASK_ASSIGNMENT_VERSION_CONFLICT`，状态不允许时 MUST 返回 `TASK_ASSIGNMENT_NOT_EDITABLE`。

#### Scenario: 驳回后修改难度
- **WHEN** 家长把一条 `REJECTED` 分配改为另一个当前启用难度
- **THEN** 系统更新该分配的难度与奖励快照并增加版本，同时保留此前提交和驳回历史

#### Scenario: 修改已提交分配
- **WHEN** 家长尝试修改当前状态为 `SUBMITTED` 的分配
- **THEN** 系统拒绝请求并返回错误码 `TASK_ASSIGNMENT_NOT_EDITABLE`，且快照不变

#### Scenario: 两个家长并发修改
- **WHEN** 两个家长基于同一版本修改同一分配且其中一个先成功
- **THEN** 系统拒绝另一个过期版本请求并返回错误码 `TASK_ASSIGNMENT_VERSION_CONFLICT`

### Requirement: 取消分配必须保留审计与历史
家长 SHALL 仅能取消尚未批准的分配。取消 MUST 记录取消标记、必填且去除首尾空白后非空的原因、操作者和时间，并使分配不可再提交或审核；取消标记 MUST 与审核状态分开保存，不得覆盖 `PENDING`、`SUBMITTED` 或 `REJECTED` 状态，不得删除任何提交、审核或快照历史。系统 MUST NOT 提供会物理删除分配的操作。重复取消同一分配 MUST 幂等返回既有取消结果；已批准分配的取消 MUST 返回 `TASK_ASSIGNMENT_ALREADY_APPROVED`。

#### Scenario: 取消待完成分配
- **WHEN** 家长以非空原因取消一条 `PENDING` 分配
- **THEN** 系统记录一次取消审计，使该分配不可操作，并保留其原审核状态和快照

#### Scenario: 取消已提交分配
- **WHEN** 家长取消一条已有提交尝试但尚未批准的分配
- **THEN** 系统保留该提交尝试和 `SUBMITTED` 状态记录，并阻止之后的提交或审核

#### Scenario: 取消已批准分配
- **WHEN** 家长请求取消一条 `APPROVED` 分配
- **THEN** 系统返回错误码 `TASK_ASSIGNMENT_ALREADY_APPROVED`，且分配、审核和积分流水均不变

#### Scenario: 批准与取消并发
- **WHEN** 批准和取消同一条 `SUBMITTED` 分配的事务并发执行
- **THEN** 系统以一致性控制保证仅一个操作成功；批准先成功时取消返回 `TASK_ASSIGNMENT_ALREADY_APPROVED`，取消先成功时审核返回 `TASK_ASSIGNMENT_CANCELLED`，且历史和积分一致

### Requirement: 分页列表与日历查询
家长 SHALL 能够查询家庭内分配，孩子 SHALL 仅能查询自己的分配。列表 SHALL 支持按孩子、模板、审核状态、取消标记、逾期标记以及本地截止日期范围筛选；默认页大小 MUST 为 20，页大小 MUST 为 1 至 100，结果 MUST 按截止时间升序、分配标识升序稳定排序。日期范围 MUST 使用实例时区且最长为 366 个本地日。月历查询 SHALL 接受合法的本地年月，并按本地自然日返回每天的总数以及各审核状态、取消和逾期计数；月历和列表 MUST 使用同一可见性规则和派生逻辑。无效查询 MUST 返回 `TASK_ASSIGNMENT_INVALID_QUERY`。

#### Scenario: 家长按孩子和日期筛选
- **WHEN** 家长查询某孩子在 2026-04-01 至 2026-04-30 的分配且页大小为 20
- **THEN** 系统仅返回当前家庭内符合条件的分配、稳定分页信息及其创建时快照

#### Scenario: 孩子查看自己的日历
- **WHEN** 孩子请求 2026-04 月历
- **THEN** 系统按 `Asia/Shanghai` 的本地自然日聚合且仅返回分配给该孩子的状态、取消和逾期计数

#### Scenario: 日期筛选边界
- **WHEN** 查询本地日期范围为 2026-04-08 至 2026-04-08
- **THEN** 系统包含截止本地日期恰好为 2026-04-08 的全部可见分配，不包含相邻日期

#### Scenario: 查询参数不合法
- **WHEN** 请求提交页大小 101、非法年月、未知状态或超过 366 日的日期范围
- **THEN** 系统返回错误码 `TASK_ASSIGNMENT_INVALID_QUERY`，且不返回部分结果

