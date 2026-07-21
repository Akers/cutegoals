# Comet Design Handoff

- Change: prize-model-configuration
- Phase: design
- Mode: compact
- Context hash: 48922df8e7fa19265d363e5857c41bdd82f63ab26fe2f18dbd317d610ce2a342

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/prize-model-configuration/proposal.md

- Source: openspec/changes/prize-model-configuration/proposal.md
- Lines: 1-49
- SHA256: fd4ef9a1a134b4254cf8b43dcd0c68ba8b59f5ceba22149234327933b4daf9b7

```md
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

```

## openspec/changes/prize-model-configuration/design.md

- Source: openspec/changes/prize-model-configuration/design.md
- Lines: 1-60
- SHA256: bd18f72247688127af44acba447d7d5723daf099e7f6bd0e23a033bdbea29285

```md
# Design: 奖品模型配置体系

## 架构决策

### 奖品类型采用枚举+JSON混合模式

- `prize_type`（VIRTUAL/PHYSICAL）和 `prize_category` 用独立列，方便索引和筛选
- 各类特有配置用 `type_config` JSON 列存储，避免为每种类型建独立表（MVP 阶段组合爆炸可控）
- 这种方式平衡了查询效率和扩展灵活性

### 前端采用动态表单模式

- 顶层选择「奖品类型」→ 二级选择「奖品分类」→ 动态渲染特有配置子表单
- 复用已有 `TaskTypeConfigForms` 的模式（类型选择器 + 条件子表单）

## 数据模型

### Prize.java 新增字段

```java
@TableField("prize_type")     private String prizeType;     // VIRTUAL / PHYSICAL
@TableField("prize_category") private String prizeCategory; // TV_TIME / COMPUTER_TIME / PARK_PLAY / GENERAL / TRAVEL
@TableField("title_image")    private String titleImage;
@TableField("detail_image")   private String detailImage;
@TableField("valid_from")     private LocalDateTime validFrom;
@TableField("valid_to")       private LocalDateTime validTo;
@TableField("type_config")    private String typeConfig;    // JSON
```

### type_config JSON 规格

| category | type_config |
|----------|-------------|
| TV_TIME | `{"durationType":"DAILY","duration":60}` |
| COMPUTER_TIME | 同上 |
| PARK_PLAY | `{"maxUses":3}` |
| GENERAL | `{"maxUses":1}` |
| TRAVEL | `{"destination":"三亚","travelDays":3,"travelNights":2,"actualValue":5000}` |
| PHYSICAL | `{"actualValue":199}` |

## 前端设计

### 新增`PrizeTypeConfigForms` 组件
仿照`TaskTypeConfigForms`模式：
1. 奖品类型选择器（VIRTUAL / PHYSICAL）
2. 虚拟奖品二级分类选择器
3. 公共配置区（名称/描述/图片/积分/库存/有效期）
4. 按 category 动态渲染特有配置子表单

### 改动文件清单

| 层级 | 文件 | 改动 |
|------|------|------|
| DB | `server/task/src/main/resources/db/migration/V13__prize_model_fields.sql` | 新增迁移 |
| Entity | `server/common/.../prize/Prize.java` | 新增 7 个字段 |
| Service | `server/prize/.../PrizeService.java` | createPrize/updatePrize 适配新字段 |
| Controller | `server/prize/.../PrizeController.java` | toPrizeMap 输出新字段 |
| 前端组件 | `web/src/parent/components/PrizeTypeConfigForms.tsx` | 新建：类型选择+动态配置表单 |
| 前端页面 | `web/src/parent/pages/index.tsx` | ParentPrizesPage 重构表单 |
| 前端类型 | `web/src/shared/api/types.ts` 或内联 | PrizeTypeValue、PrizeCategoryValue 类型 |

```

## openspec/changes/prize-model-configuration/tasks.md

- Source: openspec/changes/prize-model-configuration/tasks.md
- Lines: 1-13
- SHA256: 5bc1029593eb227909ed84555cca9ea0f6536efcef084a9c74b559bd30046e3d

```md
# Tasks: 奖品模型配置体系

## Phase 1: 数据层
- [ ] V13 数据库迁移脚本
- [ ] Prize entity 新增字段

## Phase 2: 后端
- [ ] PrizeService 创建/更新/查询适配
- [ ] PrizeController toPrizeMap 适配

## Phase 3: 前端
- [ ] 新建 PrizeTypeConfigForms 组件
- [ ] ParentPrizesPage 表单重构 + 列表适配

```
