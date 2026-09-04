# child-page-migration Specification

## Purpose
定义 Child 端「我的任务」列表的数据契约、状态筛选归类规则、筛选选中态高亮与提交受限展示，确保孩子能正确查看、筛选任务并清晰辨认当前筛选状态与提交受限状态。
## Requirements
### Requirement: 我的任务列表数据契约与状态筛选
Child 端「我的任务」页面 MUST 正确读取任务分配列表 API（`GET /api/task-assignments?childId=<childId>`）响应中的 `content` 字段（分页结构 `{ content, page, pageSize, totalElements, totalPages }`），不得读取未定义的 `items` 字段。首页「今日任务」卡片 MUST 同样读取 `content` 字段。

页面 MUST 提供「进行中」「已逾期」「已提交」「已完成」「已取消」五个互斥状态筛选分类，进入页面时 MUST 默认选中并列出「进行中」分类的任务。归类规则：每个任务 MUST 只归入一个分类，按以下优先级从高到低判定：

1. 已取消：`cancelled = true` 的任务；
2. 已完成：审核状态为 `APPROVED` 或 `COMPLETED`（孩子已提交且家长审核通过）的任务；
3. 已提交：审核状态为 `SUBMITTED`（孩子已提交、家长未审核）的任务；
4. 已逾期：非 REPEAT 任务、截止时间已过、且审核状态为 `PENDING` 或 `REJECTED`（仍未提交或驳回待重提）的任务；REPEAT 任务 MUST NOT 归入已逾期；
5. 进行中：其余任务，包括未逾期的 `PENDING`/`REJECTED` 任务、未提交未取消的 REPEAT 任务（含截止已过的周期）以及未来任务。

「进行中」分类内，任务 MUST 按截止时间升序排列，且任务日期（deadline 日期部分）晚于今天的未来任务 MUST 排在末尾；未来任务 MUST 以灰色标注「未开始」状态，其提交按钮 MUST 不可用（前端禁用，后端提交接口行为不变）。

各分类无任务时 MUST 展示空态。筛选切换 MUST 为客户端分类，不改变后端列表接口契约。

#### Scenario: 我的任务按默认分类列出任务
- **WHEN** 孩子登录后进入「我的任务」页面
- **THEN** 页面默认选中「进行中」分类，列出归类为「进行中」的任务，每条显示任务名称、截止时间、状态与积分奖励

#### Scenario: 按分类筛选
- **WHEN** 孩子依次选择「已逾期」「已提交」「已完成」「已取消」
- **THEN** 列表分别仅展示归类为对应分类的任务，其他分类的任务不出现

#### Scenario: 已提交且逾期的任务归入已提交
- **WHEN** 一条非 REPEAT 任务截止时间已过、状态为 SUBMITTED（家长未审核）
- **THEN** 该任务归入「已提交」，不出现在「已逾期」中

#### Scenario: 驳回任务归类
- **WHEN** 一条非 REPEAT 任务状态为 REJECTED 且截止时间未过
- **THEN** 该任务归入「进行中」；若截止时间已过，则归入「已逾期」

#### Scenario: 重复任务不逾期
- **WHEN** 一条 REPEAT 任务截止时间已过且未提交、未取消、未通过
- **THEN** 该任务归入「进行中」，不出现在「已逾期」中

#### Scenario: 未来任务展示为未开始
- **WHEN** 孩子查看「我的任务」，存在任务日期晚于今天的未取消分配
- **THEN** 这些分配归入「进行中」并排在列表末尾，以灰色标注「未开始」，提交按钮不可用

#### Scenario: 已取消任务列入已取消分类
- **WHEN** 孩子查看「我的任务」，且存在已取消（cancelled=true）的分配
- **THEN** 该分配仅出现在「已取消」分类，不出现在其他分类中

#### Scenario: 空列表空态
- **WHEN** 当前分类无任务（含后端返回 `content` 为空数组）
- **THEN** 页面显示「暂无任务」空态

### Requirement: 状态筛选选中态高亮
「我的任务」页面的五分类筛选器 MUST 清晰高亮当前应用的筛选分类：选中项 MUST 以孩子端主题色（antd `colorPrimary`）背景与白色文字展示，与未选中项形成明确视觉区分；进入页面默认选中的「进行中」以及孩子切换后的任一选中项 MUST 同样以该高亮样式展示。高亮 MUST 为纯视觉呈现，MUST NOT 改变筛选归类逻辑、列表内容或后端接口契约。

#### Scenario: 默认选中项高亮
- **WHEN** 孩子进入「我的任务」页面
- **THEN** 「进行中」筛选项以主题色背景 + 白色文字的高亮样式展示，其余四项为未选中样式

#### Scenario: 切换筛选项后高亮跟随
- **WHEN** 孩子点击「已逾期」「已提交」「已完成」或「已取消」任一筛选项
- **THEN** 被点击项以高亮样式展示，原先高亮的项恢复未选中样式，且列表同步切换为对应分类

### Requirement: 提交受限展示
「我的任务」页面 MUST 依据列表接口返回的 `canSubmit` 与 `submissionBlockReason` 字段展示提交入口。当 `canSubmit=false` 且 `submissionBlockReason` 为 `MAX_REACHED` 或 `POINTS_CAP_REACHED` 时，原提交按钮位置 MUST 显示文案「该任务已达最大提交次数」，且 MUST NOT 提供可点击的提交按钮；达到最大提交次数与达到积分限额两种情形 MUST 使用同一文案。其他原因导致的 `canSubmit=false`（如已逾期且迟交策略为 REJECT）保持禁用按钮展示，MUST NOT 显示该文案。

#### Scenario: 达到最大提交次数
- **WHEN** 某任务 `canSubmit=false` 且 `submissionBlockReason='MAX_REACHED'`
- **THEN** 该任务卡片在原提交按钮位置显示「该任务已达最大提交次数」，无提交按钮

#### Scenario: 达到积分限额
- **WHEN** 某任务 `canSubmit=false` 且 `submissionBlockReason='POINTS_CAP_REACHED'`
- **THEN** 该任务卡片在原提交按钮位置显示「该任务已达最大提交次数」，无提交按钮

#### Scenario: 其他不可提交原因
- **WHEN** 某任务 `canSubmit=false` 且 `submissionBlockReason=null`
- **THEN** 提交按钮保持禁用态，不显示「该任务已达最大提交次数」文案
