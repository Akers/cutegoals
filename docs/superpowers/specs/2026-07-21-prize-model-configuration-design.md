---
comet_change: prize-model-configuration
role: technical-design
canonical_spec: openspec
archived-with: 2026-07-21-prize-model-configuration
status: final
---

# 奖品模型配置体系 — 技术设计

## 架构

```
┌──────────────┐    ┌─────────────────┐    ┌──────────┐
│  ParentPrizes │───▶│ PrizeTypeConfig │───▶│  Prize   │
│     Page      │    │     Forms       │    │  Entity  │
└──────────────┘    └─────────────────┘    └──────────┘
       │                                         │
       ▼                                         ▼
┌──────────────┐                        ┌──────────────┐
│   Upload     │──── FormData ────────▶ │  UploadCtrl  │
│  Component   │                        │ /prizes/up.. │
└──────────────┘                        └──────┬───────┘
                                               │
                                        uploads/prizes/
```

## 数据模型

### Prize 表新增列 (V13)

```sql
ALTER TABLE prize ADD COLUMN prize_type    VARCHAR(20) NOT NULL DEFAULT 'PHYSICAL';
ALTER TABLE prize ADD COLUMN prize_category VARCHAR(30) NULL;
ALTER TABLE prize ADD COLUMN title_image   VARCHAR(500) NULL;
ALTER TABLE prize ADD COLUMN detail_image  VARCHAR(500) NULL;
ALTER TABLE prize ADD COLUMN valid_from    TIMESTAMP NULL;
ALTER TABLE prize ADD COLUMN valid_to      TIMESTAMP NULL;
ALTER TABLE prize ADD COLUMN type_config   JSON NULL;
```

### Entity (Prize.java)

```java
@TableField("prize_type")    private String prizeType;     // VIRTUAL | PHYSICAL
@TableField("prize_category")  private String prizeCategory;  // TV_TIME|COMPUTER_TIME|PARK_PLAY|GENERAL|TRAVEL
@TableField("title_image")   private String titleImage;
@TableField("detail_image")  private String detailImage;
@TableField("valid_from")    private LocalDateTime validFrom;
@TableField("valid_to")      private LocalDateTime validTo;
@TableField("type_config")   private String typeConfig;    // JSON
```

### type_config JSON Schema

| prizeCategory | 字段 |
|--------------|------|
| TV_TIME, COMPUTER_TIME | `{ durationType: "DAILY"\|"WEEKLY"\|"MONTHLY"\|"SUPPLEMENT", duration: Int }` |
| PARK_PLAY, GENERAL | `{ maxUses: Int }` |
| TRAVEL | `{ destination: Str, travelDays: Int, travelNights: Int, actualValue: Int }` |
| PHYSICAL (prizeCategory=null) | `{ actualValue: Int }` |

## 后端实现

### PrizeService 改造

- `createPrize/updatePrize`: 解析 prizeType，校验 type_config JSON 不为空且合法
- `queryPrizes`: 无需改，MyBatis-Plus 自动映射

### UploadController (新建)

```java
@PostMapping("/api/prizes/upload")
public Map<String, String> upload(@RequestParam("file") MultipartFile file) {
    String filename = UUID + "_" + originalFilename;
    file.transferTo(new File("uploads/prizes/" + filename));
    return Map.of("url", "/uploads/prizes/" + filename);
}
```

### WebMvcConfigurer

```java
registry.addResourceHandler("/uploads/**")
        .addResourceLocations("file:uploads/");
```

## 前端实现

### PrizeTypeConfigForms 组件 (web/src/parent/components/PrizeTypeConfigForms.tsx)

```
选择奖品类型 [虚拟|实物]
  ├── 公共配置区: 名称/描述/标题图(上传)/详情图(上传)/积分/库存/有效期
  └── 虚拟 → 选择分类 [电视时长卡|电脑时长卡|公园游玩卡|通用|旅游卡]
        ├── 电视/电脑时长卡: 时长类型[每日|每周|每月|补充包] + 时长(分钟)
        ├── 公园/通用: 可用次数
        └── 旅游: 目的地 + 旅行天数 + 旅行夜数 + 实际价值
  └── 实物 → 实际价值
```

### ParentPrizesPage 改造

- Modal width 增加到 640px
- 集成 PrizeTypeConfigForms
- handleSave 序列化：`prizeType, prizeCategory, titleImage, detailImage, validFrom, validTo, typeConfig: JSON.stringify(config)`
- 列表页表格新增「类型」列

### 图片上传逻辑

```tsx
const handleUpload = async (file: File): Promise<string> => {
  const formData = new FormData();
  formData.append('file', file);
  const res = await getClient().post('/prizes/upload', formData, { headers: { 'Content-Type': 'multipart/form-data' } });
  return res.data.url;
};
```

## 测试策略

| 层级 | 测试内容 |
|------|---------|
| 后端单元 | PrizeService 创建各类型奖品，验证 type_config 校验和持久化 |
| 集成 | UploadController 文件上传返回正确 URL |
| 前端组件 | PrizeTypeConfigForms 分类型渲染正确性 |
