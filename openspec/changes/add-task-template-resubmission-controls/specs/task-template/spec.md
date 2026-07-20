## ADDED Requirements

### Requirement: 重复提交控制的一等属性

任务模板 SHALL 通过三个一等字段表达「同一孩子在该模板上的重复提交控制策略」：

- `allow_resubmit`：布尔值，标记该模板是否启用重复提交上限控制；`FALSE` 时其他两个字段无业务含义；默认 `FALSE`
- `max_submissions`：非负整数，取值范围 `0` 至 `10000`；表示该孩子在该模板上「已通过次数」的最大上限；`0` 表示不限制
- `points_cap`：非负整数，取值范围 `0` 至 `100000000`；表示该孩子在该模板上「累计获得积分」的最大上限；`0` 表示不限制

系统 MUST 在创建模板时持久化这三个字段，MUST 在更新模板时按字段校验规则验证后持久化。`max_submissions` 与 `points_cap` 仅在 `allow_resubmit=true` 时被审核接口读取；`allow_resubmit=false` 时审核接口 MUST 沿用既有审核规则，不应用 max / cap 上限校验。

当 `task_type=STANDING` 时，系统 MUST NOT 再读取 `type_config.max_submissions`；既有 STANDING 模板在数据迁移后将上限值固化到 `max_submissions` 一等字段。系统 MUST 接受旧客户端在 PUT 请求中继续提交 `type_config.max_submissions` 字段，但 MUST 忽略其值且 MUST NOT 据此调整 `max_submissions` 一等字段。

字段验证规则：

- `allow_resubmit` 缺省时取 `FALSE`
- `max_submissions` 缺省或为 `0` 时取 `0`；非负整数且不超过 `10000`
- `points_cap` 缺省或为 `0` 时取 `0`；非负整数且不超过 `100000000`
- 字段类型、范围或语义不合法 MUST 返回稳定错误码 `TASK_TEMPLATE_VALIDATION_FAILED` 和对应字段错误，且 MUST NOT 创建或更新模板

#### Scenario: 默认值创建模板

- **WHEN** 家长创建任务模板时未提交 `allow_resubmit` / `max_submissions` / `points_cap` 字段
- **THEN** 系统以 `allow_resubmit=FALSE`、`max_submissions=0`、`points_cap=0` 创建模板，且不报错

#### Scenario: 配置重复提交控制

- **WHEN** 家长提交 `allow_resubmit=true`、`max_submissions=3`、`points_cap=100` 创建模板
- **THEN** 系统以提交值创建模板，并返回当前版本

#### Scenario: 字段取值越界

- **WHEN** 家长提交 `max_submissions=-1`、`max_submissions=10001`、`points_cap=-5` 或 `points_cap=100000001` 创建或更新模板
- **THEN** 系统返回错误码 `TASK_TEMPLATE_VALIDATION_FAILED` 和对应字段错误，且不创建或更新模板

#### Scenario: STANDING 类型不再读取 type_config

- **WHEN** 家长创建 `task_type=STANDING` 模板并在 `type_config` 中携带 `max_submissions=5`，同时在一等字段中提交 `max_submissions=3`
- **THEN** 系统以一等字段 `max_submissions=3` 持久化，且忽略 `type_config.max_submissions` 的值

#### Scenario: 旧客户端兼容

- **WHEN** 客户端调用 PUT 更新模板时仅在 `type_config.max_submissions` 提交新值，未在一等字段提交
- **THEN** 系统保留模板既有 `max_submissions` 一等字段值不变，且不报错

### Requirement: 重复提交控制属性仅影响新分配

家长 SHALL 能够更新未删除模板的 `allow_resubmit` / `max_submissions` / `points_cap` 字段。每次更新 MUST 增加模板版本并保留审计记录。这三个字段的后续修改 MUST 仅影响修改后创建的新分配的快照值，不得改写既有分配中已固化的 snapshot 字段，也不得追溯改变既有分配的审核期校验口径。

#### Scenario: 更新字段不回溯影响既有分配

- **WHEN** 家长在分配创建后将模板的 `max_submissions` 从 3 改为 5
- **THEN** 系统增加模板版本，之后的新分配使用新快照值 `max_submissions=5`，既有分配的 snapshot 值仍保持 3 且审核期按 3 校验

#### Scenario: 更新字段不影响既有分配积分上限

- **WHEN** 家长在分配创建后将模板的 `points_cap` 从 100 改为 200，孩子在该既有分配上继续提交
- **THEN** 系统按既有分配的 snapshot `points_cap=100` 进行积分上限校验

## MODIFIED Requirements

### Requirement: 创建任务模板与字段验证
家长 SHALL 能够创建任务模板。模板 MUST 包含去除首尾空白后长度为 1 至 100 个字符的名称、长度为 1 至 50 个字符的分类、至少一个启用的难度,以及取值为 `LIMITED`、`REPEAT` 或 `STANDING` 之一的任务类型 `task_type`。说明和图标 MUST 为可选字段,说明最长 2000 个字符,图标标识最长 500 个字符。任务类型配置 `type_config` MUST 与 `task_type` 匹配:`LIMITED` 必须包含 `end_date`,可选 `start_date`;`REPEAT` 必须包含 `frequency` 取值 `DAILY`、`WEEKLY`、`MONTHLY` 或 `YEARLY`,并按 `frequency` 携带合法的 `trigger_day`;`STANDING` 必须不再强制要求 `type_config.max_submissions`,该上限改由 `max_submissions` 一等字段表达（详见「重复提交控制的一等属性」）。系统 MUST 拒绝空白名称、空白分类、超长字段、未知字段类型、不合法难度、未知任务类型、缺失 `type_config`、`type_config` 与 `task_type` 不匹配或子字段不合法,并以稳定错误码 `TASK_TEMPLATE_VALIDATION_FAILED` 返回逐字段错误;验证失败 MUST NOT 创建部分模板或部分难度。

#### Scenario: 使用完整字段创建限时模板
- **WHEN** 家长提交合法的名称、分类、说明、图标、`task_type=LIMITED` 与 `{start_date: "2026-07-20", end_date: "2026-08-20"}`,以及两个合法难度
- **THEN** 系统在一个原子操作中创建模板及其难度,返回模板标识和当前版本

#### Scenario: 使用最小字段创建常驻模板
- **WHEN** 家长提交合法名称、分类、一个合法难度、`task_type=STANDING`,且未提交 `type_config.max_submissions`、说明或图标
- **THEN** 系统创建模板,并将说明、图标保存为未设置,`allow_resubmit` 默认为 `FALSE`

#### Scenario: 创建输入不合法
- **WHEN** 家长提交仅含空白的名称、超过长度上限的说明、奖励积分不合法的难度、未知 `task_type` 或 `type_config` 与 `task_type` 不匹配
- **THEN** 系统返回错误码 `TASK_TEMPLATE_VALIDATION_FAILED` 和对应字段错误,且模板与难度均不落库

### Requirement: 更新模板仅影响未来分配
家长 SHALL 能够更新未删除模板的名称、分类、说明、图标、`type_config` 内字段、难度、`allow_resubmit`、`max_submissions` 与 `points_cap`。每次更新 MUST 增加模板版本并保留审计记录。模板或难度的后续修改 MUST 仅影响修改后创建的新分配,不得改写既有分配中已固化的模板名称、难度、奖励积分、截止时间、重复提交控制属性或其他快照字段。模板的 `task_type` 字段 MUST NOT 可修改;尝试修改 `task_type` MUST 返回稳定错误码 `TASK_TEMPLATE_TYPE_IMMUTABLE`,且不增加模板版本。

#### Scenario: 更新名称和难度奖励
- **WHEN** 家长修改模板名称并将某难度奖励从 3 分改为 5 分
- **THEN** 系统增加模板版本,之后的新分配使用新名称和 5 分奖励,既有分配仍保留原名称和 3 分奖励

#### Scenario: 更新重复提交控制属性
- **WHEN** 家长将模板 `allow_resubmit` 从 `FALSE` 改为 `TRUE` 并设置 `max_submissions=5`
- **THEN** 系统增加模板版本,之后的新分配固化 `allow_resubmit=true, max_submissions=5`,既有分配的快照字段保持不变

#### Scenario: 尝试修改任务类型
- **WHEN** 家长提交请求修改 `task_type`
- **THEN** 系统返回错误码 `TASK_TEMPLATE_TYPE_IMMUTABLE`,且模板版本不增加
