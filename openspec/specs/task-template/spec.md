# task-template Specification

## Purpose
TBD - created by archiving change core-features. Update Purpose after archive.
## Requirements
### Requirement: 模板的家庭边界与操作权限
MVP 每个实例 MUST 仅服务一个家庭。只有该家庭的家长角色 SHALL 创建、修改、停用、恢复、删除和查询任务模板；孩子角色 MUST NOT 直接管理模板。系统 MUST 从已认证身份确定家庭,不得信任客户端提交的家庭标识。越权操作 MUST 返回稳定错误码 `TASK_TEMPLATE_FORBIDDEN`,访问不存在或不属于当前家庭的模板 MUST 返回 `TASK_TEMPLATE_NOT_FOUND`,且响应不得泄露其他家庭的数据。

#### Scenario: 家长管理本家庭模板
- **WHEN** 已认证家长创建或修改当前实例家庭内的任务模板
- **THEN** 系统执行操作并记录操作者、操作时间和变更类型

#### Scenario: 孩子尝试修改模板
- **WHEN** 孩子请求创建、修改、停用或删除任务模板
- **THEN** 系统拒绝请求并返回错误码 `TASK_TEMPLATE_FORBIDDEN`,且不产生任何模板变更

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

### Requirement: 配置多个难度与正整数奖励
每个模板 MUST 至少保留一个启用难度,并 SHALL 支持最多 20 个难度。每个难度 MUST 具有在模板内稳定且唯一的标识、去除首尾空白后长度为 1 至 50 个字符的名称、唯一的展示顺序,以及 1 至 1000000 的正整数奖励积分。家长 SHALL 能够新增、修改、排序和停用难度;已被分配或审核历史引用的难度 MUST NOT 被物理删除,停用后 MUST NOT 用于新分配。

#### Scenario: 配置多个难度
- **WHEN** 家长为一个模板配置"简单 1 分""普通 3 分"和"困难 5 分",且名称、顺序与奖励积分均合法
- **THEN** 系统保存三个可独立选择的启用难度及其稳定标识

#### Scenario: 奖励积分不是正整数
- **WHEN** 家长提交零、负数、小数或超过 1000000 的奖励积分
- **THEN** 系统返回错误码 `TASK_TEMPLATE_VALIDATION_FAILED`,并且不保存该次难度变更

#### Scenario: 停用被历史引用的难度
- **WHEN** 家长停用一个已被任务分配历史引用的难度
- **THEN** 系统保留该难度及历史引用,禁止其用于新分配,并且既有分配快照不发生变化

#### Scenario: 尝试停用最后一个启用难度
- **WHEN** 家长请求停用模板中最后一个启用难度
- **THEN** 系统拒绝请求并返回错误码 `TASK_TEMPLATE_REQUIRES_ACTIVE_DIFFICULTY`

### Requirement: 重复任务的触发日规则与本地日历语义
任务类型为 `REPEAT` 的模板 MUST 通过 `type_config.frequency` 描述重复周期,取值限定为 `DAILY`、`WEEKLY`、`MONTHLY` 或 `YEARLY`。`frequency=DAILY` 的模板 MUST NOT 携带 `trigger_day`,系统每日触发一次。`frequency=WEEKLY` 的模板 MUST 携带 `trigger_day.weekday`,取值为 ISO 星期 1 至 7(1 为周一,7 为周日)。`frequency=MONTHLY` 的模板 MUST 携带 `trigger_day.mode`,取值为 `FIRST_DAY`、`LAST_DAY` 或 `MID_MONTH`;`LAST_DAY` MUST 自动适应月份(平年 2 月 28、闰年 2 月 29、4/6/9/11 月 30、其余月 31);`MID_MONTH` MUST 解析为 15 日。`frequency=YEARLY` 的模板 MUST 携带 `trigger_day.month`(1 至 12)和 `trigger_day.day`(1 至 31),`day` MUST 按 `month` 自适应,非法组合(如 2 月 30 日) MUST 返回 `TASK_TEMPLATE_VALIDATION_FAILED`。所有触发日匹配 MUST 使用实例时区,实例默认时区 MUST 为 `Asia/Shanghai`。规则本身 MUST NOT 立即创建分配,只在模板被家长分配给孩子后生成首期。未知 `frequency`、缺失或越界 `trigger_day` 字段 MUST 返回 `TASK_TEMPLATE_INVALID_RECURRENCE`。

#### Scenario: 每日重复
- **WHEN** 家长配置 `task_type=REPEAT`、`frequency=DAILY` 的模板
- **THEN** 系统保存该模板,且每个 Asia/Shanghai 自然日生成一期可提交实例

#### Scenario: 每周自定义星期
- **WHEN** 家长在 `Asia/Shanghai` 实例中配置 `frequency=WEEKLY` 且 `trigger_day.weekday=3`
- **THEN** 系统将该规则解释为每个本地周三触发,并保存规范化后的配置

#### Scenario: 每月最后一日自适应
- **WHEN** 家长配置 `frequency=MONTHLY` 且 `trigger_day.mode=LAST_DAY`
- **THEN** 系统 MUST 在平年 2 月 28 日、闰年 2 月 29 日、4/6/9/11 月 30 日、其余月份 31 日触发

#### Scenario: 每年某月某日非法组合
- **WHEN** 家长提交 `frequency=YEARLY` 且 `trigger_day={month: 2, day: 30}`
- **THEN** 系统拒绝请求并返回错误码 `TASK_TEMPLATE_VALIDATION_FAILED`,且不创建模板

#### Scenario: 触发日配置不合法
- **WHEN** 家长提交 `frequency=WEEKLY` 但 `trigger_day.weekday=0` 或 `frequency=MONTHLY` 但 `trigger_day.mode` 取未知枚举
- **THEN** 系统拒绝请求并返回错误码 `TASK_TEMPLATE_INVALID_RECURRENCE`

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

### Requirement: 停用与恢复模板
家长 SHALL 能够停用或恢复未删除模板。停用模板 MUST 保留全部数据和历史引用,MUST NOT 继续用于人工分配或重复任务的下一期生成;恢复后 SHALL 可用于新分配。对已停用模板创建新分配的请求 MUST 返回稳定错误码 `TASK_TEMPLATE_INACTIVE`。停用模板中已生成的重复任务实例 MUST 按其既有生命周期完成或被取消,但 MUST NOT 生成新的下一期实例。

#### Scenario: 停用模板
- **WHEN** 家长停用一个已有历史分配的模板
- **THEN** 系统保留模板与全部历史记录,并从可用于新分配的模板集合中排除该模板

#### Scenario: 使用停用模板分配
- **WHEN** 家长请求使用已停用模板创建新任务分配
- **THEN** 系统拒绝请求并返回错误码 `TASK_TEMPLATE_INACTIVE`

#### Scenario: 停用重复模板不生成新期
- **WHEN** 家长停用一个 `task_type=REPEAT` 的模板,且其当前 OPEN 实例尚在触发日
- **THEN** 系统允许该实例按原生命周期完成或被取消,但 MUST NOT 在提交或下个触发日时生成新的下一期实例

### Requirement: 删除模板不得破坏历史
模板删除 MUST 采用逻辑删除并记录删除人和删除时间。无论模板是否被历史记录引用,系统 MUST NOT 级联物理删除模板、难度、任务分配、提交、审核或积分流水;被历史记录引用的模板和难度 MUST 永久保持可解析。已删除模板 MUST NOT 用于新分配或重复任务的下一期生成,重复删除同一模板 MUST 幂等成功且不得新增重复审计事件。

#### Scenario: 删除无历史引用的模板
- **WHEN** 家长删除一个尚无任务分配的模板
- **THEN** 系统逻辑删除模板,将其排除在默认列表和新分配候选项之外,并保留删除审计

#### Scenario: 删除被历史引用的模板
- **WHEN** 家长删除一个已被任务分配、审核或积分记录间接引用的模板
- **THEN** 系统仅逻辑删除模板,既有记录继续使用其快照展示,且任何历史数据均不被级联删除

#### Scenario: 重试删除请求
- **WHEN** 客户端因超时再次删除同一个已逻辑删除的模板
- **THEN** 系统返回与已删除状态一致的成功结果,且只保留一次有效删除审计事件

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

### Requirement: 限时任务的时间窗口
任务类型为 `LIMITED` 的模板产生的任务实例 MUST 遵循时间窗口状态机。`type_config.start_date` 为 null 时实例在分配那一刻进入可提交状态;`start_date` 非 null 时实例先处于 PENDING 状态,直到 Asia/Shanghai 当地 00:00 切换为 SUBMITTABLE。`end_date` 截止时间 MUST 解释为 Asia/Shanghai 当地 23:59:59.999。截止时间过后未提交的实例 MUST 自动切换为 EXPIRED,且不接受补交;已提交并通过审核的实例切换为 COMPLETED。`end_date` MUST 不早于 `start_date`(若 `start_date` 非 null);违反 MUST 返回 `TASK_TEMPLATE_VALIDATION_FAILED`。在孩子尝试提交时,若实例仍处于 PENDING MUST 返回 `TASK_LIMITED_NOT_STARTED`;若实例已 EXPIRED MUST 返回 `TASK_LIMITED_EXPIRED`。

#### Scenario: 配置开始日期和结束日期
- **WHEN** 家长创建 `task_type=LIMITED` 模板并配置 `start_date="2026-07-20"`、`end_date="2026-07-25"`
- **THEN** 系统按 Asia/Shanghai 时区解释为 7 月 20 日 00:00 开始,7 月 25 日 23:59:59.999 截止

#### Scenario: 空开始日期表示即时开始
- **WHEN** 家长配置 `start_date=null`、`end_date="2026-07-25"`
- **THEN** 实例在分配那一刻即处于 SUBMITTABLE 状态,直到 7 月 25 日 23:59:59.999

#### Scenario: 截止日期早于开始日期
- **WHEN** 家长提交 `start_date="2026-07-25"`、`end_date="2026-07-20"`
- **THEN** 系统返回错误码 `TASK_TEMPLATE_VALIDATION_FAILED`,且不创建模板

#### Scenario: 未到开始日期提交
- **WHEN** 孩子在 `start_date` 之前的日期尝试提交 LIMITED 实例
- **THEN** 系统返回错误码 `TASK_LIMITED_NOT_STARTED`

#### Scenario: 截止日期后提交
- **WHEN** 孩子在 `end_date` 之后尝试提交 LIMITED 实例
- **THEN** 系统返回错误码 `TASK_LIMITED_EXPIRED`

### Requirement: 常驻任务的提交计数与上限
任务类型为 `STANDING` 的模板产生的任务实例 MUST 长期处于 ACTIVE 状态,并按孩子独立计数。`task_assignment` 表 MUST 维护 `submission_count` 字段(默认 0),每有一个孩子提交且家长审核通过后,MUST 将对应实例的 `submission_count` 自增 1;审核驳回或拒绝 MUST NOT 影响 `submission_count`。`type_config.max_submissions=null` 时实例 MUST 永远保持 ACTIVE,且 SHALL 接受无限次审核通过的提交。`max_submissions` 为正整数 N 时,当 `submission_count` 自增至 N 后实例 MUST 切换为 COMPLETED,此后任何孩子提交尝试 MUST 返回 `TASK_STANDING_LIMIT_REACHED`。达上限后系统 MUST 在任务列表中向孩子展示提示"已达最大提交次数,不能再提交"。

#### Scenario: 无限次常驻任务
- **WHEN** 家长配置 `task_type=STANDING` 且 `max_submissions=null`
- **THEN** 系统为每个被分配的孩子维护独立 `submission_count`,实例永远保持 ACTIVE

#### Scenario: 审核通过才计数
- **WHEN** 孩子提交一次 STANDING 实例,家长先审核通过
- **THEN** 系统将 `submission_count` 自增 1

#### Scenario: 审核驳回不计计数
- **WHEN** 孩子提交一次 STANDING 实例,家长审核驳回或拒绝
- **THEN** 系统 MUST NOT 自增 `submission_count`,且实例保持 ACTIVE

#### Scenario: 达到最大提交次数
- **WHEN** 孩子的 STANDING 实例 `submission_count` 已等于 `max_submissions`,孩子再次尝试提交
- **THEN** 系统返回错误码 `TASK_STANDING_LIMIT_REACHED`,且 `submission_count` 不再增加

### Requirement: 重复任务的滚动生成
任务类型为 `REPEAT` 的模板产生的任务实例 MUST 通过双触发推进机制滚动生成,实例状态机限定为 PENDING_OPEN、OPEN、COMPLETED、EXPIRED。模板被家长分配给孩子那一刻 MUST 立刻生成首期实例:若分配当日即触发日则状态为 OPEN,否则为 PENDING_OPEN。**提交触发器**:孩子审核通过那一刻 MUST 同步将本期切换为 COMPLETED,并立刻计算下一触发日,创建新的下一期实例(状态 PENDING_OPEN,通过下个触发日切换为 OPEN)。**时间触发器**:每日 Asia/Shanghai 00:05 运行的定时任务 MUST 扫描所有 REPEAT 模板的当前 OPEN 实例,若 `trigger_day + 1 日 < now` 说明触发日已过但未提交,MUST 将其切换为 EXPIRED 并立刻创建下一期实例(若新触发日为今天则状态 OPEN,否则 PENDING_OPEN);同时 MUST 扫描所有 PENDING_OPEN 实例,若 `trigger_day == today` 则切换为 OPEN。系统 MUST 通过 `assignment_id + trigger_day` 唯一约束保证不重复创建实例。孩子尝试在 PENDING_OPEN 实例上提交 MUST 返回 `TASK_REPEAT_NOT_TRIGGER_DAY`;EXPIRED 实例 MUST NOT 接受补交。

#### Scenario: 模板分配生成首期
- **WHEN** 家长将 REPEAT 模板分配给孩子,且分配当日不是触发日
- **THEN** 系统立刻生成首期实例(状态 PENDING_OPEN),并在到达触发日时由时间触发器切换为 OPEN

#### Scenario: 提交触发下一期
- **WHEN** 孩子在 OPEN 实例上提交,家长审核通过
- **THEN** 系统将本期切换为 COMPLETED,并立刻创建下一期实例(状态 PENDING_OPEN)

#### Scenario: 非触发日提交
- **WHEN** 孩子在 PENDING_OPEN 实例上尝试提交
- **THEN** 系统返回错误码 `TASK_REPEAT_NOT_TRIGGER_DAY`

#### Scenario: 时间触发器推进过期实例
- **WHEN** Asia/Shanghai 00:05 定时任务运行,发现某 REPEAT 模板的当前 OPEN 实例 `trigger_day + 1 日 < today`
- **THEN** 系统将该实例切换为 EXPIRED,并立刻创建下一期实例(状态视新触发日决定)

#### Scenario: 时间触发器幂等
- **WHEN** 时间触发器因故障在同一天运行两次
- **THEN** 系统 MUST NOT 重复创建任何实例,`assignment_id + trigger_day` 唯一约束生效

### Requirement: 重复任务的时间触发器
重复任务的滚动生成 MUST 依赖每日 Asia/Shanghai 00:05 运行的时间触发器。触发器 MUST 扫描当前实例家庭内所有未删除且未停用的 REPEAT 模板的实例,执行两个动作:(a) 将所有 `trigger_day + 1 日 < today` 的 OPEN 实例切换为 EXPIRED 并创建下一期;(b) 将所有 `trigger_day == today` 的 PENDING_OPEN 实例切换为 OPEN。触发器 MUST 在事务内执行,失败 MUST 回滚且不影响其他模板。触发器 MUST 通过 `assignment_id + trigger_day` 唯一约束保证幂等,即使被并发或重复触发也不产生重复实例。触发器运行结果(扫描数量、推进数量、错误) MUST 写入审计日志,供运维查询。

#### Scenario: 触发器每日运行
- **WHEN** Asia/Shanghai 00:05 时刻到达
- **THEN** 系统运行重复任务时间触发器,完成 EXPIRED 推进与 PENDING_OPEN 切换两个动作

#### Scenario: 单模板失败不影响其他模板
- **WHEN** 触发器处理模板 A 时发生异常
- **THEN** 系统 MUST 回滚模板 A 的本次推进事务,且 MUST 继续处理模板 B 及后续模板

#### Scenario: 触发器结果审计
- **WHEN** 触发器完成一次运行
- **THEN** 系统 MUST 写入审计日志,记录扫描的实例数、推进为 EXPIRED 的数量、切换为 OPEN 的数量及任何错误详情

### Requirement: 迁移脚本 MUST 兼容 PostgreSQL 与 H2

为支持私有化部署中 PostgreSQL 生产环境，任务模板相关的 Flyway 迁移脚本 MUST 使用 ANSI 或两种数据库均支持的 SQL 函数，不得使用仅 H2/MySQL 特有的函数。

#### Scenario: PostgreSQL 启动时成功应用 V11 迁移

- **Given**: 数据库为 PostgreSQL，当前 schema 版本为 10，存在旧版 `task_recurrence_rule` 表及 `CUSTOM_WEEKDAYS` 规则数据
- **When**: 后端启动，Flyway 执行 `V11__add_frequency_to_type_config.sql`
- **Then**: 迁移成功，`task_template.type_config` 被正确填充，应用正常启动
- **And**: 同样的迁移脚本在 H2 测试环境中也能成功执行

