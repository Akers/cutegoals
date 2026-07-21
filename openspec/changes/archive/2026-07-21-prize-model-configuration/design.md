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
