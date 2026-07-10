## ADDED Requirements

### Requirement: 直接兑换与盲盒兑换资格
系统 SHALL 仅允许已认证孩子使用自己的可用积分兑换本家庭的可用奖品或盲盒。直接兑换 MUST 使用奖品当前积分价格并扣减对应奖品 1 件库存；盲盒兑换 MUST 使用孩子已确认的当前成本与 `availability_version`，从当前有效候选集中抽取并扣减选中奖品 1 件库存。

#### Scenario: 成功直接兑换
- **WHEN** 孩子有 100 可用积分，并兑换价格为 50、已启用且库存大于 0 的本家庭奖品
- **THEN** 系统 MUST 原子扣除 50 积分和 1 件库存，创建状态为 `PENDING_FULFILLMENT` 的直接兑换并返回其唯一标识

#### Scenario: 成功兑换盲盒
- **WHEN** 孩子有 100 可用积分，并用当前 `availability_version` 兑换成本为 30 且至少有一个有效候选项的本家庭盲盒
- **THEN** 系统 MUST 原子扣除 30 积分、抽取一个有效奖品、扣减该奖品 1 件库存，并创建状态为 `PENDING_FULFILLMENT` 的盲盒兑换
#### Scenario: 积分不足

- **WHEN** 孩子可用积分少于兑换事务中的实际成本
- **THEN** 系统 MUST 返回 HTTP 409 和错误码 `POINTS_INSUFFICIENT_BALANCE`，且积分、库存、兑换记录和流水 MUST 全部保持不变

#### Scenario: 非孩子发起兑换
- **WHEN** 家长或实例管理员尝试发起直接兑换或盲盒兑换
- **THEN** 系统 MUST 返回 HTTP 403 和错误码 `FORBIDDEN`，且不得产生任何兑换副作用

#### Scenario: 兑换其他家庭资源
- **WHEN** 孩子提交不属于其家庭的奖品或盲盒标识
- **THEN** 系统 MUST 返回 HTTP 404 和对应的 `PRIZE_NOT_FOUND` 或 `BLIND_BOX_NOT_FOUND`，且不得泄露其他家庭数据

### Requirement: 兑换原子创建与不可变快照
每次成功兑换 SHALL 在同一事务内完成积分余额扣减、选中奖品库存扣减、兑换记录创建和积分扣减流水创建。兑换记录 MUST 保存兑换类型、孩子、家庭、实际成本、奖品名称与图片快照；盲盒兑换还 MUST 保存奖池名称、已确认候选概率版本、各候选有效概率和最终抽取结果快照。所有快照和原始扣减流水 MUST 在奖品、奖池或成本后续更新、停用、删除后保持不可变。

#### Scenario: 直接兑换保存快照和流水
- **WHEN** 一次直接兑换成功提交
- **THEN** 系统 MUST 在同一事务中保存实际成本、奖品资料快照、库存扣减、兑换记录和一条关联该兑换的积分扣减流水

#### Scenario: 盲盒兑换保存抽取快照
- **WHEN** 一次盲盒兑换成功提交
- **THEN** 系统 MUST 保存兑换时的奖池成本、候选奖品及概率、选中奖品资料、选中时概率和抽取结果，且这些快照 MUST 与扣分和扣库存同时提交

#### Scenario: 后续修改不改写历史
- **WHEN** 家长在兑换后修改奖品名称、图片、`points_cost`、盲盒成本或权重
- **THEN** 系统 MUST 在历史查询中继续返回兑换创建时的原始快照和实际成本

### Requirement: 客户端幂等键
每个兑换创建请求 MUST 携带由客户端生成的非空幂等键。系统 SHALL 在孩子身份与兑换操作范围内绑定成功提交的键、规范化请求内容和兑换结果；相同键与相同请求的重试 MUST 返回同一兑换结果且不得重复扣分、扣库存、抽取或写流水，相同键用于不同请求 MUST 返回 `EXCHANGE_IDEMPOTENCY_CONFLICT`。当请求同时携带已绑定幂等键与过期 `availability_version` 时，系统 MUST 优先返回 `EXCHANGE_IDEMPOTENCY_CONFLICT`；仅当幂等键未绑定时，对过期版本 MUST 返回 `BLIND_BOX_POOL_CHANGED`。
#### Scenario: 缺少幂等键

- **WHEN** 客户端提交兑换但未提供有效幂等键
- **THEN** 系统 MUST 返回 HTTP 400 和错误码 `EXCHANGE_IDEMPOTENCY_KEY_REQUIRED`，且不得开始兑换事务

#### Scenario: 成功响应丢失后重试
- **WHEN** 首次兑换已经提交但客户端未收到响应，并以相同幂等键和相同请求内容重试
- **THEN** 系统 MUST 返回首次创建的同一兑换及抽取结果，积分和库存 MUST 不再变化
#### Scenario: 相同键承载不同请求

- **WHEN** 客户端使用已绑定的幂等键兑换不同奖品、不同盲盒或以不同确认版本提交
- **THEN** 系统 MUST 返回 HTTP 409 和错误码 `EXCHANGE_IDEMPOTENCY_CONFLICT`，且不得执行第二个请求

#### Scenario: 并发提交相同幂等键
- **WHEN** 两个内容相同且使用同一幂等键的兑换请求并发到达
- **THEN** 系统 MUST 仅创建一条兑换、一条原始扣减流水和一次库存扣减，并向两次调用返回同一逻辑结果

### Requirement: 事务失败与并发余额保护
积分检查与扣减、盲盒有效候选重检和抽取、库存扣减、兑换记录与快照、积分流水 MUST 作为单一原子事务提交；任一步骤失败 SHALL 使全部步骤回滚。并发兑换 MUST 不得使积分余额或库存为负，且内部事务失败 MUST 返回可诊断错误而不得伪装成成功。

#### Scenario: 库存扣减失败时回滚
- **WHEN** 积分扣减已执行但库存条件更新失败
- **THEN** 系统 MUST 回滚积分变更、抽取结果、兑换记录和流水，并返回 HTTP 409 `PRIZE_OUT_OF_STOCK` 或 `BLIND_BOX_POOL_CHANGED`

#### Scenario: 记录或流水写入失败时回滚
- **WHEN** 库存和积分步骤可执行但兑换记录、快照或积分流水写入失败
- **THEN** 系统 MUST 回滚全部变更并返回 HTTP 500 和错误码 `EXCHANGE_TRANSACTION_FAILED`

#### Scenario: 并发消耗有限积分
- **WHEN** 孩子的积分只够一次兑换，而两个使用不同幂等键的合法兑换并发到达
- **THEN** 系统 MUST 至多提交一次兑换，另一请求 MUST 返回 HTTP 409 `POINTS_INSUFFICIENT_BALANCE`，最终积分余额 MUST 不小于 0

### Requirement: 兑换状态与兑现权限
新兑换状态 MUST 为 `PENDING_FULFILLMENT`，且唯一允许的终态转换 SHALL 为 `PENDING_FULFILLMENT → FULFILLED` 或 `PENDING_FULFILLMENT → CANCELLED`。只有兑换所属家庭的家长 MUST 能兑现或在兑现前取消；孩子和实例管理员 MUST 不得执行终态转换。
#### Scenario: 家长兑现待处理兑换

- **WHEN** 同家庭家长兑现状态为 `PENDING_FULFILLMENT` 的兑换
- **THEN** 系统 MUST 将状态原子变更为 `FULFILLED` 并记录操作者与时间，且不得再次改变积分或库存

#### Scenario: 两个家长并发兑现同一兑换

- **WHEN** 两个家长并发请求兑现同一条 `PENDING_FULFILLMENT` 兑换
- **THEN** 系统 MUST 至多将一个请求转换为 `FULFILLED`，另一请求 MUST 返回 HTTP 409 和错误码 `EXCHANGE_INVALID_STATE`，且不重复兑现或二次扣减

#### Scenario: 已兑现后取消
- **WHEN** 家长尝试取消状态为 `FULFILLED` 的兑换
- **THEN** 系统 MUST 返回 HTTP 409 和错误码 `EXCHANGE_INVALID_STATE`，且不得退款或恢复库存

#### Scenario: 孩子取消盲盒结果
- **WHEN** 孩子尝试取消自己的盲盒兑换或重抽结果
- **THEN** 系统 MUST 返回 HTTP 403 和错误码 `FORBIDDEN`，原抽取快照、状态、积分和库存 MUST 保持不变

#### Scenario: 其他家庭家长操作兑换
- **WHEN** 家长尝试兑现或取消不属于其家庭的兑换
- **THEN** 系统 MUST 返回 HTTP 404 和错误码 `EXCHANGE_NOT_FOUND`，且不得泄露或修改该兑换

### Requirement: 取消的原子补偿与仅一次语义
取消待兑现兑换 SHALL 在同一事务中将状态变为 `CANCELLED`、向原孩子退款实际扣除的积分、恢复原兑换所扣减的恰好 1 件奖品库存，并写入关联原扣减流水的退款流水。补偿 MUST 最多成功一次；奖品已停用或软删除时仍 MUST 恢复底层库存，但不得重新启用、取消删除或重新展示该奖品。

#### Scenario: 取消直接兑换
- **WHEN** 同家庭家长取消一条待兑现的直接兑换
- **THEN** 系统 MUST 原子退款实际成本、恢复对应奖品 1 件库存、写入一条退款流水并将状态变为 `CANCELLED`

#### Scenario: 取消盲盒兑换
- **WHEN** 同家庭家长取消一条待兑现的盲盒兑换
- **THEN** 系统 MUST 仅恢复抽取结果快照对应奖品 1 件库存并退款实际盲盒成本，不得恢复其他候选奖品库存或重新抽取

#### Scenario: 取消停用或已删除奖品兑换
- **WHEN** 兑换对应奖品在取消前已停用或软删除
- **THEN** 系统 MUST 完成一次库存恢复和退款，但奖品 MUST 继续保持停用或已删除并从兑换入口排除

#### Scenario: 并发重复取消
- **WHEN** 两个家长请求并发取消同一条 `PENDING_FULFILLMENT` 兑换
- **THEN** 系统 MUST 仅允许一个请求提交退款、库存恢复和 `CANCELLED` 状态，另一请求 MUST 返回 HTTP 409 `EXCHANGE_INVALID_STATE`

#### Scenario: 兑现与取消并发竞争
- **WHEN** 对同一条待兑现兑换的兑现与取消请求并发到达
- **THEN** 系统 MUST 只提交一个终态；若为 `FULFILLED` 则不得退款或恢复库存，若为 `CANCELLED` 则 MUST 恰好退款和恢复库存一次，失败方 MUST 返回 HTTP 409 `EXCHANGE_INVALID_STATE`

#### Scenario: 补偿事务失败
- **WHEN** 取消过程中的退款、库存恢复、退款流水或状态更新任一步骤失败
- **THEN** 系统 MUST 回滚整个取消操作，兑换 MUST 保持 `PENDING_FULFILLMENT`，并返回 HTTP 500 `EXCHANGE_CANCELLATION_FAILED`

### Requirement: 角色范围内的历史分页筛选
孩子 SHALL 只能分页查询自己的兑换历史，家长 SHALL 能分页查询本家庭全部孩子的兑换历史。查询 MUST 支持按兑换类型、状态、孩子（仅家长）和创建时间范围组合筛选，按 `created_at` 降序并以唯一标识稳定排序；`page_size` MUST 限制在 1 至 100，响应 MUST 返回不可变快照而非当前奖品资料。

#### Scenario: 孩子查询自己的历史
- **WHEN** 孩子按 `BLIND_BOX` 类型和 `PENDING_FULFILLMENT` 状态查询合法分页
- **THEN** 系统 MUST 仅返回该孩子自己的匹配记录、快照和下一页游标

#### Scenario: 家长筛选某个孩子的历史
- **WHEN** 家长按本家庭孩子标识、状态和创建时间范围查询
- **THEN** 系统 SHALL 返回本家庭内满足全部条件的稳定分页记录

#### Scenario: 孩子查询其他孩子历史
- **WHEN** 孩子提交其他孩子标识或尝试读取其他孩子的兑换详情
- **THEN** 系统 MUST 返回 HTTP 403 `FORBIDDEN` 或 HTTP 404 `EXCHANGE_NOT_FOUND`，且不得返回他人记录

#### Scenario: 非法筛选或分页边界
- **WHEN** 查询包含结束时间早于开始时间、未知状态、`page_size=0` 或 `page_size>100`
- **THEN** 系统 MUST 返回 HTTP 400 和错误码 `EXCHANGE_INVALID_QUERY`，且不得返回部分结果
