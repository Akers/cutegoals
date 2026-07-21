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
