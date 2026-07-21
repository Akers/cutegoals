# Proposal: 奖品模型配置体系

## Why

当前奖品管理只有基础字段（名称、描述、积分价格、库存），所有奖品共享同一表单。业务需要区分**虚拟奖品**（电视时长卡、电脑时长卡、公园游玩卡、通用虚拟奖品、旅游卡）和**实物奖品**，每种类型有各自的特殊配置项。因此需要引入奖品模型体系，支持按类型动态渲染配置表单。

## What Changes

### 数据模型

**Prize 表新增字段**：
- `prize_type`：奖品类型枚举 `VIRTUAL` | `PHYSICAL`
- `prize_category`：虚拟奖品子类型 `TV_TIME` | `COMPUTER_TIME` | `PARK_PLAY` | `GENERAL` | `TRAVEL`
- `title_image`：标题图 URL
- `detail_image`：详情图 URL
- `valid_from` / `valid_to`：有效期时间范围
- `type_config`：JSON 列，存储各类型的特有配置

**type_config 格式**（JSON）：

| 奖品类型 | category | type_config 字段 |
|---------|----------|------------------|
| 电视时长卡 | TV_TIME | `{ durationType: "DAILY"\|"WEEKLY"\|"MONTHLY"\|"SUPPLEMENT", duration: 60 }` |
| 电脑时长卡 | COMPUTER_TIME | 同上 |
| 公园游玩卡 | PARK_PLAY | `{ maxUses: 3 }` |
| 通用虚拟奖品 | GENERAL | `{ maxUses: 1 }` |
| 旅游卡 | TRAVEL | `{ destination: "三亚", travelDays: 3, travelNights: 2, actualValue: 5000 }` |
| 实物奖品 | (null) | `{ actualValue: 199 }` |

### 前端

- 新增奖品表单增加「奖品类型」选择（虚拟/实物）
- 虚拟奖品增加「奖品分类」下拉（电视时长卡/电脑时长卡/公园游玩卡/通用虚拟奖品/旅游卡）
- 公共配置区：名称、描述、标题图、详情图、兑换积分、库存、有效期
- 根据选择的分类动态渲染特有配置表单
- 奖品列表展示适配新字段

### 后端

- Prize entity 新增字段
- PrizeService 创建/更新/查询逻辑适配
- Flyway 数据库迁移脚本

### 影响范围

- 前端：`ParentPrizesPage`、`Prize` interface、可能新增配置子表单组件
- 后端：`Prize` entity、`PrizeService`、`PrizeController`、`PrizeMapper`
- 数据库：新 Flyway migration
- child 端：可选适配（奖品展示页展示虚拟奖品使用信息）
