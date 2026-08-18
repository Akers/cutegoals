# child-page-migration Specification

## Purpose
定义 Child 端「我的任务」列表的数据契约与展示规则，确保孩子登录后能正确看到其任务列表。
## Requirements
### Requirement: 我的任务列表数据契约与展示规则
Child 端「我的任务」页面 MUST 正确读取任务分配列表 API（`GET /api/task-assignments?childId=<childId>`）响应中的 `content` 字段（分页结构 `{ content, page, pageSize, totalElements, totalPages }`），不得读取未定义的 `items` 字段。页面 MUST 按以下规则展示任务：列出所有有效的可重复（REPEAT）任务，以及所有任务日期（deadline 的日期部分）不晚于当前日期的任务；「有效」定义为分配未取消（cancelled=false）。首页「今日任务」卡片 MUST 同样读取 `content` 字段。

#### Scenario: 我的任务列出有效 REPEAT 任务与已到期任务
- **WHEN** 孩子登录后进入「我的任务」页面，且该孩子存在未取消的 REPEAT 分配与任务日期不晚于今天的其他分配
- **THEN** 页面列出上述全部任务，每条显示任务名称、截止时间、状态与积分奖励

#### Scenario: 未来日期的非 REPEAT 任务不列出
- **WHEN** 孩子查看「我的任务」，且存在任务日期晚于今天的非 REPEAT 未取消分配
- **THEN** 该分配不在列表中展示

#### Scenario: 已取消任务不列出
- **WHEN** 孩子查看「我的任务」，且存在已取消（cancelled=true）的分配
- **THEN** 该分配不在列表中展示

#### Scenario: 空列表空态
- **WHEN** 后端返回 `content` 为空数组
- **THEN** 页面显示「暂无任务」空态
