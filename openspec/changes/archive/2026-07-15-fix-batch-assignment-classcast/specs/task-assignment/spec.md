## MODIFIED Requirements

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
