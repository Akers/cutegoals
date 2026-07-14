## ADDED Requirements

### Requirement: 前端模板管理支持任务类型选择与配置
家长端任务模板管理 UI MUST 在创建和编辑模板时展示任务类型选择器，支持 `LIMITED`、`REPEAT`、`STANDING` 三种选项。选择不同任务类型时，表单 MUST 动态显示对应的配置字段：`LIMITED` 显示开始日期（可空）和结束日期；`REPEAT` 显示重复周期（DAILY/WEEKLY/MONTHLY/YEARLY）和触发日配置；`STANDING` 显示最大提交次数（可设为无限或正整数）。前端提交前 MUST 按后端期望的 JSON 结构组装 `typeConfig`，不得以错误结构调用 API。

#### Scenario: 创建限时模板
- **WHEN** 家长在模板表单中选择“限时任务”并填写结束日期
- **THEN** 前端提交 `taskType=LIMITED` 与 `typeConfig={end_date:"2026-08-20"}` 到后端

#### Scenario: 创建重复模板
- **WHEN** 家长选择“重复任务”，设置频率为“每周”，并选择周三
- **THEN** 前端提交 `taskType=REPEAT` 与 `typeConfig={frequency:"WEEKLY",trigger_day:{weekday:3}}`

#### Scenario: 创建常驻模板并设置无限次
- **WHEN** 家长选择“常驻任务”并勾选“无限次提交”
- **THEN** 前端提交 `taskType=STANDING` 与 `typeConfig={max_submissions:null}`

### Requirement: 模板列表接口支持按任务类型筛选
后端 `GET /api/task-templates` 接口 MUST 支持 `taskType` 查询参数，允许传入单值或逗号分隔的多值。系统 MUST 返回当前家庭中 `task_type` 与查询值匹配的模板列表；传入未知任务类型值 MUST 返回错误码 `TASK_TEMPLATE_INVALID_QUERY`。

#### Scenario: 按单值筛选
- **WHEN** 家长请求 `GET /api/task-templates?taskType=REPEAT`
- **THEN** 系统仅返回任务类型为 REPEAT 的模板

#### Scenario: 按多值筛选
- **WHEN** 家长请求 `GET /api/task-templates?taskType=LIMITED,STANDING`
- **THEN** 系统返回 LIMITED 或 STANDING 类型的模板

#### Scenario: 传入未知任务类型
- **WHEN** 家长请求 `GET /api/task-templates?taskType=UNKNOWN`
- **THEN** 系统返回错误码 `TASK_TEMPLATE_INVALID_QUERY`

### Requirement: 后端完整校验任务类型与类型配置
后端在创建和更新模板时 MUST 校验 `taskType` 必填且取值必须为 `LIMITED`、`REPEAT`、`STANDING` 之一；`typeConfig` 必填且结构必须与 `taskType` 匹配。校验失败 MUST 返回错误码 `TASK_TEMPLATE_VALIDATION_FAILED` 并附带字段级错误。`taskType` 字段 MUST NOT 在更新时修改；尝试修改 MUST 返回 `TASK_TEMPLATE_TYPE_IMMUTABLE` 且不增加模板版本。

#### Scenario: 缺失 taskType
- **WHEN** 家长提交创建模板请求但未提供 `taskType`
- **THEN** 系统返回错误码 `TASK_TEMPLATE_VALIDATION_FAILED` 并提示 taskType 缺失

#### Scenario: typeConfig 与 taskType 不匹配
- **WHEN** 家长提交 `taskType=REPEAT` 但 `typeConfig` 缺少 `frequency`
- **THEN** 系统返回错误码 `TASK_TEMPLATE_VALIDATION_FAILED` 并提示 typeConfig 不合法

#### Scenario: 更新时修改 taskType
- **WHEN** 家长更新模板时将 `taskType` 从 LIMITED 改为 STANDING
- **THEN** 系统返回错误码 `TASK_TEMPLATE_TYPE_IMMUTABLE` 且版本不增加

### Requirement: 任务分配创建时快照任务类型与类型配置
创建任务分配时，系统 MUST 将当前模板的 `task_type` 和 `type_config` 写入 `task_assignment.snapshot_template_task_type` 和 `snapshot_template_type_config` 字段。模板后续修改 MUST NOT 影响既有分配中的快照字段。数据库表 MUST 支持这两个字段，并允许现有数据为 NULL（历史数据不回填）。

#### Scenario: 新分配携带快照
- **WHEN** 家长基于 `taskType=LIMITED` 的模板创建分配
- **THEN** 系统创建的 `task_assignment` 记录包含 `snapshot_template_task_type=LIMITED` 和对应 `snapshot_template_type_config`

#### Scenario: 模板修改不影响既有分配快照
- **WHEN** 家长修改模板名称后，查询既有分配详情
- **THEN** 系统展示的快照名称不变，且 `snapshot_template_task_type` 保持原值

### Requirement: 新增错误码同步到前端
后端已定义的 6 个错误码（`TASK_TEMPLATE_TYPE_IMMUTABLE`、`TASK_TEMPLATE_TYPE_CONFIG_MISMATCH`、`TASK_LIMITED_NOT_STARTED`、`TASK_LIMITED_EXPIRED`、`TASK_REPEAT_NOT_TRIGGER_DAY`、`TASK_STANDING_LIMIT_REACHED`）MUST 在前端 `ErrorCodes` 常量表和中文错误映射中同时存在。当后端返回这些错误码时，前端 MUST 展示对应中文提示，不得以“未知错误”兜底。

#### Scenario: 错误码常量完整
- **WHEN** 前端工程编译时
- **THEN** `ErrorCodes` 包含全部 6 个新增错误码

#### Scenario: 中文提示完整
- **WHEN** 后端返回 `TASK_STANDING_LIMIT_REACHED`
- **THEN** 前端展示中文提示“已达最大提交次数，不能再提交”

## MODIFIED Requirements

### Requirement: 创建任务模板与字段验证
家长 SHALL 能够创建任务模板。模板 MUST 包含去除首尾空白后长度为 1 至 100 个字符的名称、长度为 1 至 50 个字符的分类、至少一个启用的难度,以及取值为 `LIMITED`、`REPEAT` 或 `STANDING` 之一的任务类型 `task_type`。说明和图标 MUST 为可选字段,说明最长 2000 个字符,图标标识最长 500 个字符。任务类型配置 `type_config` MUST 与 `task_type` 匹配:`LIMITED` 必须包含 `end_date`,可选 `start_date`;`REPEAT` 必须包含 `frequency` 取值 `DAILY`、`WEEKLY`、`MONTHLY` 或 `YEARLY`,并按 `frequency` 携带合法的 `trigger_day`;`STANDING` 必须包含为 null 或 1 至 10000 正整数的 `max_submissions`。系统 MUST 拒绝空白名称、空白分类、超长字段、未知字段类型、不合法难度、未知任务类型、缺失 `type_config`、`type_config` 与 `task_type` 不匹配或子字段不合法,并以稳定错误码 `TASK_TEMPLATE_VALIDATION_FAILED` 返回逐字段错误;验证失败 MUST NOT 创建部分模板或部分难度。

#### Scenario: 使用完整字段创建限时模板
- **WHEN** 家长提交合法的名称、分类、说明、图标、`task_type=LIMITED` 与 `{start_date: "2026-07-20", end_date: "2026-08-20"}`,以及两个合法难度
- **THEN** 系统在一个原子操作中创建模板及其难度,返回模板标识和当前版本

#### Scenario: 使用最小字段创建常驻模板
- **WHEN** 家长提交合法名称、分类、一个合法难度、`task_type=STANDING` 与 `{max_submissions: null}`,且未提交说明或图标
- **THEN** 系统创建模板,并将说明、图标保存为未设置

#### Scenario: 创建输入不合法
- **WHEN** 家长提交仅含空白的名称、超过长度上限的说明、奖励积分不合法的难度、未知 `task_type` 或 `type_config` 与 `task_type` 不匹配
- **THEN** 系统返回错误码 `TASK_TEMPLATE_VALIDATION_FAILED` 和对应字段错误,且模板与难度均不落库

### Requirement: 分页筛选模板列表
家长 SHALL 能够分页查询当前家庭的模板,并按分类、启用状态、删除状态、名称关键词和任务类型筛选。默认页大小 MUST 为 20,页大小 MUST 为 1 至 100;结果 MUST 按更新时间降序、模板标识升序稳定排序,并返回总数或下一页信息。默认查询 MUST 排除已删除模板;只有家长显式请求历史视图时 SHALL 返回已删除模板。`task_type` 筛选 MUST 支持单值或多值;传入未知任务类型值 MUST 返回 `TASK_TEMPLATE_INVALID_QUERY`。无效分页或筛选参数 MUST 返回 `TASK_TEMPLATE_INVALID_QUERY`。

#### Scenario: 默认查询模板
- **WHEN** 家长不带筛选条件查询第一页模板
- **THEN** 系统返回最多 20 个未删除模板,并按更新时间降序及模板标识升序稳定排序

#### Scenario: 按分类和任务类型筛选
- **WHEN** 家长按分类"学习"和 `task_type=REPEAT` 筛选模板
- **THEN** 系统仅返回当前家庭中同时满足两个条件的模板

#### Scenario: 查询参数不合法
- **WHEN** 家长提交页大小 0、页大小 101、未知状态值或未知 `task_type` 值
- **THEN** 系统返回错误码 `TASK_TEMPLATE_INVALID_QUERY`,且不返回部分查询结果

### Requirement: 更新模板仅影响未来分配
家长 SHALL 能够更新未删除模板的名称、分类、说明、图标、`type_config` 内字段和难度。每次更新 MUST 增加模板版本并保留审计记录。模板或难度的后续修改 MUST 仅影响修改后创建的新分配,不得改写既有分配中已固化的模板名称、难度、奖励积分、截止时间或其他快照字段。模板的 `task_type` 字段 MUST NOT 可修改;尝试修改 `task_type` MUST 返回稳定错误码 `TASK_TEMPLATE_TYPE_IMMUTABLE`,且不增加模板版本。

#### Scenario: 更新名称和难度奖励
- **WHEN** 家长修改模板名称并将某难度奖励从 3 分改为 5 分
- **THEN** 系统增加模板版本,之后的新分配使用新名称和 5 分奖励,既有分配仍保留原名称和 3 分奖励

#### Scenario: 并发更新版本冲突
- **WHEN** 两个家长基于同一旧版本并发修改同一模板,且其中一个修改已先成功
- **THEN** 系统拒绝另一个过期版本的修改并返回错误码 `TASK_TEMPLATE_VERSION_CONFLICT`,不得静默覆盖已成功的修改

#### Scenario: 尝试修改任务类型
- **WHEN** 家长在更新请求中将 `task_type` 从 `LIMITED` 改为 `STANDING`
- **THEN** 系统拒绝请求并返回错误码 `TASK_TEMPLATE_TYPE_IMMUTABLE`,且模板版本不增加
