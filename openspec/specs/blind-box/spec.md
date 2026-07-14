# blind-box Specification

## Purpose
TBD - created by archiving change core-features. Update Purpose after archive.
## Requirements
### Requirement: 盲盒奖池资料与生命周期
系统 SHALL 允许同家庭家长创建和更新盲盒奖池的名称、描述、正整数兑换成本 `cost_points`、启用状态及奖品项，且奖池配置 MUST 始终至少包含一个奖品项。奖池删除 MUST 采用软删除并停止展示和兑换；后续成本或配置更新 MUST 不得改写既有兑换中的奖池、成本、概率和抽取结果快照。

#### Scenario: 创建可用奖池
- **WHEN** 家长创建名称“惊喜宝箱”、`cost_points=30`、状态为启用且包含至少一个合法奖品项的奖池
- **THEN** 系统 MUST 在该家长所属家庭创建奖池并保存全部配置

#### Scenario: 拒绝非法兑换成本
- **WHEN** 家长创建或更新奖池时提交 `cost_points=0`、负数、小数或非数值内容
- **THEN** 系统 MUST 返回 HTTP 400 和错误码 `BLIND_BOX_INVALID_COST`，并保持原配置不变

#### Scenario: 拒绝空奖池
- **WHEN** 家长尝试创建、更新或启用不含任何奖品项的奖池
- **THEN** 系统 MUST 返回 HTTP 400 和错误码 `BLIND_BOX_EMPTY_POOL`，且不得创建或保存任何部分配置

#### Scenario: 删除已有兑换历史的奖池
- **WHEN** 家长删除一个已有盲盒兑换记录的奖池
- **THEN** 系统 MUST 软删除并停用奖池，且 MUST 保留历史记录中的奖池名称、成本、候选概率和抽取结果快照

### Requirement: 奖品项与相对权重约束
每个盲盒奖品项的 `weight` MUST 为正整数，且 SHALL 表示相对于当前有效候选项总权重的相对权重，不要求权重合计为 100。同一奖池 MUST 不得重复配置同一奖品，奖品 MUST 属于同一家庭且未被软删除；任何非法项均 MUST 使整次配置写入失败。

#### Scenario: 保存不合计为 100 的权重
- **WHEN** 家长为奖品 A 配置 `weight=2`、奖品 B 配置 `weight=3`
- **THEN** 系统 MUST 原样保存两个正整数相对权重，不得要求补足或换算为合计 100

#### Scenario: 拒绝零权重和全零配置
- **WHEN** 家长提交任一 `weight=0` 的奖品项或提交全部权重为 0 的配置
- **THEN** 系统 MUST 返回 HTTP 400 和错误码 `BLIND_BOX_INVALID_WEIGHT`，且不得保存任何部分变更

#### Scenario: 拒绝负数或非整数权重
- **WHEN** 家长提交负数、小数或非数值权重
- **THEN** 系统 MUST 返回 HTTP 400 和错误码 `BLIND_BOX_INVALID_WEIGHT`，且不得保存任何部分变更

#### Scenario: 拒绝重复奖品项
- **WHEN** 家长在同一奖池中第二次添加同一奖品
- **THEN** 系统 MUST 返回 HTTP 409 和错误码 `BLIND_BOX_DUPLICATE_PRIZE`，并保持奖池原配置不变

#### Scenario: 拒绝其他家庭奖品
- **WHEN** 家长向奖池添加不属于其家庭的奖品标识
- **THEN** 系统 MUST 返回 HTTP 404 和错误码 `PRIZE_NOT_FOUND`，且不得泄露其他家庭奖品信息

### Requirement: 当前有效候选集
系统 MUST 在展示概率和执行抽取前过滤奖品项，仅保留所属奖池已启用且未删除、奖品已启用且未删除、奖品库存大于 0、权重为正整数的当前有效项。奖池只有在至少存在一个当前有效项时 SHALL 对孩子展示并允许兑换；无有效项时 MUST 拒绝兑换且不得扣除积分。

#### Scenario: 过滤缺货与停用奖品
- **WHEN** 奖池含奖品 A（缺货）、奖品 B（停用）和奖品 C（启用且有库存）
- **THEN** 系统 MUST 仅将奖品 C 纳入孩子可见候选集和抽取集合

#### Scenario: 全部奖品不可用
- **WHEN** 启用奖池的所有奖品均缺货、停用或软删除
- **THEN** 系统 MUST 隐藏该奖池；直接调用兑换接口时 MUST 返回 HTTP 409 `BLIND_BOX_UNAVAILABLE`，且积分、库存、兑换记录和流水 MUST 保持不变

#### Scenario: 仅剩一个有效项
- **WHEN** 过滤后仅剩一个有效奖品项
- **THEN** 系统 MUST 展示该项为 100% 概率，并 MUST 在成功兑换时选择该项

### Requirement: 候选奖品与有效概率披露

孩子在确认兑换前 SHALL 能看到所有当前有效候选奖品及其归一化概率。每项概率 MUST 按“该项权重 ÷ 当前有效项权重总和”计算，响应 MUST 携带可识别该候选集合与成本的 `availability_version`；`availability_version` SHALL 是当前有效候选集合（奖品标识、权重、奖池成本）的确定性内容哈希，固定长度、字母数字，用于校验孩子确认时的候选集与兑换事务执行时的候选集是否一致。兑换请求 MUST 绑定孩子已确认的版本，版本过期时不得按未展示的新概率直接兑换。

#### Scenario: 展示归一化概率
- **WHEN** 当前有效候选只有权重为 2 的奖品 A 和权重为 3 的奖品 B
- **THEN** 系统 MUST 向孩子展示 A 为 40%、B 为 60%，并返回对应的 `availability_version`

#### Scenario: 缺货后重新归一化
- **WHEN** 原权重为 2 的奖品 A 缺货，而权重为 3 的奖品 B 仍有效
- **THEN** 系统 MUST 从候选中移除 A，并将 B 的当前有效概率重新归一化为 100%

#### Scenario: 使用过期概率版本兑换
- **WHEN** 孩子确认候选概率后，成本、权重、启用状态或有效候选集合发生变化，并使用旧 `availability_version` 提交兑换
- **THEN** 系统 MUST 返回 HTTP 409 和错误码 `BLIND_BOX_POOL_CHANGED`，同时返回最新候选与概率供重新确认，且不得扣积分或库存

### Requirement: 加权随机抽取
盲盒抽取 SHALL 仅在兑换事务内对已确认且再次校验为当前有效的候选集执行。每个有效项被选择的概率 MUST 等于其权重占有效项总权重的比例，结果 MUST 为集合中的恰好一个奖品；系统不得先选中无效项后以不透明方式替换结果。

#### Scenario: 相对权重决定分布
- **WHEN** 有效候选为奖品 A `weight=30` 和奖品 B `weight=70`
- **THEN** 系统 MUST 使 A 的理论选择概率为 30%、B 的理论选择概率为 70%，且任何单次结果 MUST 为 A 或 B

#### Scenario: 无有效项时不抽取
- **WHEN** 兑换事务重新校验后当前有效候选集为空
- **THEN** 系统 MUST 中止抽取并返回 HTTP 409 `BLIND_BOX_UNAVAILABLE`，整个兑换事务 MUST 回滚

### Requirement: 权限范围与并发防超卖
只有同家庭家长 SHALL 能管理盲盒配置，只有已认证孩子 SHALL 能使用自己的积分兑换本家庭盲盒。抽取、库存条件检查和选中奖品扣减 MUST 受同一原子事务和并发控制保护，库存不得为负；任何失败 MUST 不得留下抽取记录、积分流水或部分扣减。

#### Scenario: 孩子尝试管理奖池
- **WHEN** 孩子尝试创建、更新、启停或删除盲盒奖池
- **THEN** 系统 MUST 返回 HTTP 403 和错误码 `FORBIDDEN`，且配置 MUST 保持不变

#### Scenario: 访问其他家庭奖池
- **WHEN** 家长或孩子提交不属于其家庭的奖池标识
- **THEN** 系统 MUST 返回 HTTP 404 和错误码 `BLIND_BOX_NOT_FOUND`，且不得泄露其他家庭数据

#### Scenario: 并发争抢唯一有效奖品
- **WHEN** 两个使用不同幂等键且确认了同一版本的兑换并发争抢唯一有效且 `stock=1` 的奖品
- **THEN** 系统 MUST 至多成功一次抽取和库存扣减；另一请求 MUST 返回 HTTP 409 `BLIND_BOX_POOL_CHANGED` 并给出最新空候选集，最终库存 MUST 为 0 且失败请求不得扣积分

#### Scenario: 并发后仍有其他有效奖品
- **WHEN** 并发兑换使某一候选缺货，但奖池仍有其他有效奖品
- **THEN** 系统 MUST 拒绝使用旧 `availability_version` 的请求并返回最新归一化概率，孩子重新确认后 SHALL 能从剩余有效项兑换

