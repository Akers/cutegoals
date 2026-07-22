# task-assignment Specification (Delta)

## MODIFIED Requirements

### Requirement: 分页列表与日历查询

家长 SHALL 能够查询家庭内分配，孩子 SHALL 仅能查询自己的分配。列表 SHALL 支持按孩子、模板、任务类型（基于 `snapshotTemplateTaskType` 字段，逗号分隔多值）、审核状态、取消标记、逾期标记以及本地截止日期范围筛选；默认页大小 MUST 为 20，页大小 MUST 为 1 至 100，结果 MUST 按截止时间升序、分配标识升序稳定排序。日期范围 MUST 使用实例时区且最长为 366 个本地日；日期范围参数为可选，不传入时 MUST 不应用日期过滤。月历查询 SHALL 接受合法的本地年月，并按本地自然日返回每天的总数、各任务类型（`taskTypes`：`{LIMITED: n, REPEAT: n, STANDING: n}`）计数、各审核状态、取消和逾期计数；月历和列表 MUST 使用同一可见性规则和派生逻辑。无效查询 MUST 返回 `TASK_ASSIGNMENT_INVALID_QUERY`。

#### Scenario: 家长按孩子和日期筛选

- **WHEN** 家长查询某孩子在 2026-04-01 至 2026-04-30 的分配且页大小为 20
- **THEN** 系统仅返回当前家庭内符合条件的分配、稳定分页信息及其创建时快照

#### Scenario: 孩子查看自己的日历

- **WHEN** 孩子请求 2026-04 月历
- **THEN** 系统按 `Asia/Shanghai` 的本地自然日聚合且仅返回分配给该孩子的状态、取消和逾期计数，以及按任务类型统计

#### Scenario: 日期筛选边界

- **WHEN** 查询本地日期范围为 2026-04-08 至 2026-04-08
- **THEN** 系统包含截止本地日期恰好为 2026-04-08 的全部可见分配，不包含相邻日期

#### Scenario: 查询参数不合法

- **WHEN** 请求提交页大小 101、非法年月、未知状态或超过 366 日的日期范围
- **THEN** 系统返回错误码 `TASK_ASSIGNMENT_INVALID_QUERY`，且不返回部分结果

#### Scenario: 按任务类型筛选

- **WHEN** 家长查询 `taskType=LIMITED` 的分配列表
- **THEN** 系统仅返回 `snapshotTemplateTaskType` 为 `LIMITED` 的分配

#### Scenario: 按多个任务类型筛选

- **WHEN** 家长查询 `taskType=LIMITED,REPEAT` 的分配列表
- **THEN** 系统返回 `snapshotTemplateTaskType` 为 `LIMITED` 或 `REPEAT` 的分配

#### Scenario: 月历查询包含任务类型统计

- **WHEN** 家长请求 2026-07 月历
- **THEN** 每日返回数据包含 `taskTypes` 字段，如 `{"LIMITED": 2, "REPEAT": 1, "STANDING": 0}`，与 `total`、`pending` 等状态计数并列

#### Scenario: 无日期范围查询全部

- **WHEN** 家长查询 `taskType=STANDING` 但不传入 `startDate` 和 `endDate`
- **THEN** 系统返回所有 `snapshotTemplateTaskType` 为 `STANDING` 的分配（分页），不受日期限制
