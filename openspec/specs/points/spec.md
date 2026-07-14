# points Specification

## Purpose
TBD - created by archiving change core-features. Update Purpose after archive.
## Requirements
### Requirement: 积分账户的家庭边界与角色权限
MVP 每个实例 MUST 仅服务一个家庭。系统 SHALL 在孩子加入家庭的同一事务中为其创建且仅创建一个积分账户，初始可用余额和累计获得积分 MUST 均为零。孩子 SHALL 只读查看自己的账户和流水，MUST NOT 直接创建、修改或删除任何积分记录；家长 SHALL 查看当前家庭全部孩子的余额、流水和汇总，并仅通过获授权的业务操作发起手工调整。系统 MUST 从已认证身份确定家庭和孩子，不得信任客户端传入的家庭标识。越权访问 MUST 返回 `POINTS_FORBIDDEN`，不可见账户 MUST 返回 `POINTS_ACCOUNT_NOT_FOUND` 且不得泄露其他家庭数据。

#### Scenario: 新孩子创建积分账户
- **WHEN** 家长成功把一个新孩子加入当前家庭
- **THEN** 系统在同一事务中创建唯一积分账户，其可用余额和累计获得积分均为 0

#### Scenario: 账户创建失败回滚孩子创建
- **WHEN** 孩子创建流程无法创建其唯一积分账户
- **THEN** 系统整体回滚孩子和账户创建，不留下无账户的孩子或孤立账户

#### Scenario: 孩子访问其他孩子账户
- **WHEN** 孩子请求查看同家庭另一孩子或不可见孩子的余额或流水
- **THEN** 系统返回错误码 `POINTS_FORBIDDEN`，且不返回任何账户或流水字段

### Requirement: 不可变流水是积分事实源
系统 MUST 以不可变积分流水作为账户事实源，并仅支持 `EARN`、`SPEND`、`REFUND` 和 `ADJUST` 四种类型。`EARN`、`SPEND` 和 `REFUND` 的金额 MUST 为 1 至 1000000000 的正整数；`ADJUST` 金额 MUST 为绝对值不超过 1000000000 的非零整数，其中正数增加余额、负数减少余额。`EARN` 和 `REFUND` MUST 增加余额，`SPEND` MUST 减少余额。每条流水 MUST 包含稳定标识、账户、类型、金额、变动后余额、发生时刻、操作者或系统来源、唯一业务引用和来源快照。金额或类型不合法 MUST 返回 `POINTS_INVALID_TRANSACTION`。

#### Scenario: 记录任务奖励
- **WHEN** 一个已批准任务以 3 分快照奖励产生积分
- **THEN** 系统创建金额为 3 的 `EARN` 流水，记录变动后余额和任务来源快照

#### Scenario: 记录兑换支出
- **WHEN** 获授权兑换流程成功使用 20 积分
- **THEN** 系统创建金额为 20 的 `SPEND` 流水并将账户余额减少 20

#### Scenario: 拒绝未知类型或非法金额
- **WHEN** 请求使用未知流水类型、零金额、小数金额或超过上限的金额
- **THEN** 系统返回错误码 `POINTS_INVALID_TRANSACTION`，且流水和余额均不变化

### Requirement: 流水与来源快照不可修改或删除
积分流水一旦成功提交 MUST NOT 被更新、覆盖或物理删除。来源快照 MUST 足以在相关业务对象之后改名、停用或逻辑删除时解释该笔变动：任务奖励 MUST 保存任务名称、难度和奖励积分快照，兑换支出 MUST 保存兑换项名称和成本快照，退款 MUST 保留原支出引用，手工调整 MUST 保存原因。错误记账的更正 MUST 通过新的合法 `ADJUST` 或 `REFUND` 流水表达，不得篡改原流水。

#### Scenario: 模板删除后查看奖励流水
- **WHEN** 家长查看一笔其任务模板已逻辑删除的 `EARN` 流水
- **THEN** 系统仍返回记账时保存的任务名称、难度、奖励积分和业务引用快照

#### Scenario: 尝试删除积分流水
- **WHEN** 任一角色请求删除或改写既有积分流水
- **THEN** 系统拒绝请求并返回错误码 `POINTS_LEDGER_IMMUTABLE`，原流水与余额保持不变

### Requirement: 唯一业务引用保证记账幂等
每条流水 MUST 具有在当前家庭内唯一的业务引用，业务引用 MUST 包含来源类型和来源标识。系统 MUST 保存该引用对应的账户、流水类型、金额和语义摘要。相同业务引用与相同语义的重试 MUST 返回首次流水且不得再次改变余额；相同业务引用被用于不同账户、类型、金额或语义 MUST 返回 `POINTS_REFERENCE_CONFLICT`。事务失败并整体回滚时 MUST 不得留下已占用引用或部分流水。

#### Scenario: 奖励记账因响应丢失而重试
- **WHEN** 审核流程以相同提交尝试引用和相同奖励金额重试已成功的 `EARN` 记账
- **THEN** 系统返回首次创建的同一流水，余额只增加一次

#### Scenario: 同一引用用于不同金额
- **WHEN** 已有业务引用对应 3 分 `EARN`，随后请求以同一引用记 5 分
- **THEN** 系统返回错误码 `POINTS_REFERENCE_CONFLICT`，且不新增流水或改变余额

#### Scenario: 失败事务后复用引用
- **WHEN** 首次记账在事务提交前失败并回滚，随后以相同业务引用重试合法请求
- **THEN** 系统允许重试成功并只产生一条最终流水

### Requirement: 余额投影不得为负且写入必须一致
账户可用余额 MUST 是按流水顺序计算的投影，并在任何已提交事务后保持为非负整数。所有积分写操作 MUST 使用行级串行化、乐观版本检查或等效一致性手段，在同一事务中校验前置条件、追加流水并更新余额投影；任何一步失败 MUST 整体回滚。系统 MUST NOT 仅修改余额而不写流水，也 MUST NOT 写入流水而不更新余额。余额不足 MUST 返回稳定错误码 `POINTS_INSUFFICIENT_BALANCE`，版本或一致性冲突 MUST 返回 `POINTS_ACCOUNT_CONFLICT` 或安全重试后给出确定结果。

#### Scenario: 并发兑换争用同一余额
- **WHEN** 账户余额为 5 且两个独立兑换并发各请求支出 4 分
- **THEN** 系统最多允许一个 `SPEND` 成功，另一个返回 `POINTS_INSUFFICIENT_BALANCE`，最终余额为 1 且仅有一条成功支出流水

#### Scenario: 流水写入后余额更新失败
- **WHEN** 事务已尝试追加流水但余额投影更新失败
- **THEN** 系统整体回滚，既不保留流水也不改变余额

#### Scenario: 检测过期账户版本
- **WHEN** 两个写操作基于同一账户旧版本且其中一个已先成功
- **THEN** 系统拒绝或安全重试另一个操作，不得丢失更新、产生负余额或产生无对应余额变化的流水

### Requirement: 任务批准仅按快照获得一次积分
只有任务审核成功批准 SHALL 创建任务奖励 `EARN` 流水。奖励金额 MUST 来自任务分配快照，唯一业务引用 MUST 指向被批准的提交尝试；模板或难度当前值 MUST NOT 改变该金额。同一事务 MUST 同时提交任务 `APPROVED` 状态、审核历史、`EARN` 流水和余额投影，同一提交尝试最多成功获得一次积分。

#### Scenario: 模板奖励修改后批准旧分配
- **WHEN** 分配快照奖励为 3 分而模板当前奖励已改为 5 分，家长批准该分配的当前提交
- **THEN** 系统创建唯一的 3 分 `EARN` 流水并增加余额 3 分

#### Scenario: 两次并发批准同一提交
- **WHEN** 两个审核事务并发批准同一提交尝试
- **THEN** 唯一业务引用和审核一致性控制保证仅一笔 `EARN` 流水成功，余额仅增加一次

### Requirement: 支出必须引用兑换且余额充足
每笔 `SPEND` MUST 引用当前家庭内一笔有效兑换，金额 MUST 等于兑换时固化的积分成本，并包含兑换项名称与成本快照。创建支出前系统 MUST 在一致事务内校验账户归属、兑换状态、唯一业务引用和余额充足；任一条件不满足 MUST 不得创建流水。余额不足 MUST 返回 `POINTS_INSUFFICIENT_BALANCE`，无效兑换来源 MUST 返回 `POINTS_SPEND_SOURCE_INVALID`。

#### Scenario: 余额充足时兑换
- **WHEN** 孩子账户余额为 30 且有效兑换快照成本为 20
- **THEN** 系统在兑换事务中创建一笔 20 分 `SPEND` 流水，余额变为 10

#### Scenario: 余额不足时兑换
- **WHEN** 孩子账户余额为 10 且兑换快照成本为 20
- **THEN** 系统返回错误码 `POINTS_INSUFFICIENT_BALANCE`，兑换、流水和余额全部保持原状

### Requirement: 每笔兑换最多成功退款一次
退款 MUST 使用 `REFUND` 流水，MUST 明确引用当前账户的原始 `SPEND` 流水及其兑换，退款金额 MUST 等于原始支出金额。每笔兑换和原始支出最多 SHALL 成功退款一次；系统 MUST 对原始支出退款资格建立唯一约束，并在同一事务中更新兑换退款状态、追加 `REFUND` 流水和增加余额。原始流水不存在、类型不是 `SPEND`、账户不一致或金额不一致 MUST 返回 `POINTS_REFUND_SOURCE_INVALID`；已经退款 MUST 返回 `POINTS_ALREADY_REFUNDED`。

#### Scenario: 全额退款一笔兑换
- **WHEN** 一笔 20 分 `SPEND` 尚未退款且获授权流程请求退款
- **THEN** 系统创建引用原支出的 20 分 `REFUND` 流水，余额增加 20，并标记该兑换已退款

#### Scenario: 重复退款
- **WHEN** 已成功退款的同一兑换再次以不同请求引用请求退款
- **THEN** 系统返回错误码 `POINTS_ALREADY_REFUNDED`，且不创建第二笔退款或再次增加余额

#### Scenario: 两个退款并发
- **WHEN** 两个事务并发退款同一原始 `SPEND`
- **THEN** 唯一退款资格只允许一个事务成功，另一个返回 `POINTS_ALREADY_REFUNDED`，余额只恢复一次

#### Scenario: 退款事务部分失败
- **WHEN** 兑换退款状态更新成功但流水或余额更新无法提交
- **THEN** 系统整体回滚兑换状态、退款流水和余额，之后仍可安全重试

### Requirement: 家长手工正负调整必须有原因与审计
家长 SHALL 能够对当前家庭孩子账户发起正数或负数 `ADJUST`，孩子 MUST NOT 发起调整。调整请求 MUST 包含长度为 1 至 128 个字符的唯一业务引用，以及去除首尾空白后长度为 1 至 500 个字符的原因；金额 MUST 为非零整数且绝对值不超过 1000000000。负调整后余额若会小于零 MUST 返回 `POINTS_INSUFFICIENT_BALANCE`。每笔成功调整 MUST 记录家长身份、原因、发生时间、调整前后余额和业务引用，且不得伪装成 `EARN`、`SPEND` 或 `REFUND`。

#### Scenario: 家长正向调整
- **WHEN** 家长以新业务引用、合法原因和金额 5 为孩子手工调整
- **THEN** 系统创建正数 `ADJUST` 流水、记录完整审计并将余额增加 5

#### Scenario: 家长负向调整但余额不足
- **WHEN** 账户余额为 3 且家长请求金额 -5 的调整
- **THEN** 系统返回错误码 `POINTS_INSUFFICIENT_BALANCE`，且不创建调整流水或改变余额

#### Scenario: 调整缺少原因
- **WHEN** 家长提交缺失、为空或仅含空白的调整原因
- **THEN** 系统返回错误码 `POINTS_ADJUST_REASON_REQUIRED`，且账户保持不变

#### Scenario: 孩子尝试手工调整
- **WHEN** 孩子请求对自己或其他账户发起 `ADJUST`
- **THEN** 系统返回错误码 `POINTS_FORBIDDEN`，且不创建流水

### Requirement: 累计获得积分不得周期清零
累计获得积分 MUST 定义为该账户全部已提交 `EARN` 流水金额之和，MUST NOT 因 `SPEND`、`REFUND`、正负 `ADJUST` 或周、月、年边界而减少或清零。可用余额 MUST 分别应用全部四类流水的方向；周期汇总 MUST 通过查询指定时间段的流水计算，不得通过重置账户累计值实现。

#### Scenario: 跨年保持累计获得积分
- **WHEN** 实例本地时间从 2026 年最后一刻进入 2027 年且没有新的积分流水
- **THEN** 账户可用余额与累计获得积分均保持不变，系统不创建清零流水

#### Scenario: 支出不减少累计获得积分
- **WHEN** 累计获得积分为 100、可用余额为 100 的账户成功支出 20
- **THEN** 可用余额变为 80，累计获得积分仍为 100

#### Scenario: 正向调整不计入累计获得积分
- **WHEN** 家长通过 `ADJUST` 为账户增加 5 分
- **THEN** 可用余额增加 5，但累计获得积分不变

### Requirement: 分页查询余额、流水与家庭汇总
孩子 SHALL 能够只读查询自己的可用余额、累计获得积分和流水；家长 SHALL 能够查询家庭内每个孩子的余额及家庭汇总。流水查询 SHALL 支持按孩子、类型、业务来源类型和本地发生日期范围筛选，默认页大小 MUST 为 20，页大小 MUST 为 1 至 100，并按发生时间降序、流水标识降序稳定排序。家庭汇总 SHALL 对指定本地日期范围按孩子返回期初余额、期末余额以及 `EARN`、`SPEND`、`REFUND`、正调整和负调整的分项总额。日期边界 MUST 使用实例时区，默认 `Asia/Shanghai`，包含起始日且截止于结束日次日 00:00:00 之前。无效查询 MUST 返回 `POINTS_INVALID_QUERY`。

#### Scenario: 孩子查看自己的流水
- **WHEN** 孩子查询自己的第一页积分流水且未指定筛选条件
- **THEN** 系统返回最多 20 条自己的流水和稳定分页信息，不返回其他孩子数据

#### Scenario: 家长查看家庭月度汇总
- **WHEN** 家长查询 2026-04-01 至 2026-04-30 的家庭积分汇总
- **THEN** 系统按 `Asia/Shanghai` 本地日期边界为每个孩子返回期初、期末余额及四类流水分项汇总

#### Scenario: 按类型和日期筛选
- **WHEN** 家长筛选某孩子在单个本地自然日内的 `EARN` 和 `ADJUST` 流水
- **THEN** 系统仅返回发生时刻落在该日本地 00:00:00 至次日 00:00:00 前且类型匹配的流水

#### Scenario: 查询参数不合法
- **WHEN** 请求包含页大小 0、页大小 101、未知流水类型或开始日期晚于结束日期
- **THEN** 系统返回错误码 `POINTS_INVALID_QUERY`，且不返回部分结果

