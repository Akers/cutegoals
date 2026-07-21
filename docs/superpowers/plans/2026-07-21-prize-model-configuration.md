---
change: prize-model-configuration
design-doc: docs/superpowers/specs/2026-07-21-prize-model-configuration-design.md
base-ref: 7153c3dcf59ccab4f91ffb251bab1f1ec94de636
---

# prize-model-configuration 实施计划

> **产物语言**: zh-CN
> **关联文档**:
> - 任务边界：`openspec/changes/prize-model-configuration/tasks.md`（3 章 / 6 子任务）
> - 技术设计：`docs/superpowers/specs/2026-07-21-prize-model-configuration-design.md`（6 个模块变更）
> - DDL 参考：`V7__prize_blindbox_exchange_tables.sql`（`prize` 表原始结构）
> **实施顺序**：数据库 → 后端实体 → 后端 Service+Controller → 前端组件，按 3 个阶段推进
> **测试策略**：后端单元测试（PrizeService 创建各类型奖品）→ 集成测试（UploadController）→ 前端组件测试（PrizeTypeConfigForms 分类型渲染）

## 计划概览

本计划将 6 项子任务按 Design Doc 定义的模块变更归并为 **3 个阶段**，每阶段对应 tasks.md 的一个章。阶段间存在严格的前置依赖。每个阶段末尾标注可运行的验证命令。

**基线约束**：
- 后端基线 = HEAD `7153c3d` 的 `mvn test` = 全部通过
- 前端基线 = `pnpm test` = 全部通过
- tsc 编译必须零错误后方可进入 UI 阶段

**整体依赖图**：
```
Phase 1 (数据层: V13 迁移 + Entity) ──→ Phase 2 (后端: Service + Controller + Upload) ──→ Phase 3 (前端: 组件 + Page)
```

**注意**：`prize` 表 V7 迁移已创建 `image` 列（VARCHAR(500)），本计划新增的 `title_image`/`detail_image` 为独立字段，与既有 `image` 字段并存。

## 阶段 1：数据层（2 子任务 → tasks.md §1）

**目标**：创建 Flyway V13 迁移脚本为 `prize` 表新增 7 列，更新 `Prize` 实体映射。

**前置 verify**：
- ⚡ verify `server/common/src/main/resources/db/migration/` 目录中 V13 编号未被占用（当前最大：V14）
- ⚡ verify `prize` 表既有列的命名风格（`V7__prize_blindbox_exchange_tables.sql` 参考）
- ⚡ verify `Prize.java` 中既有 `@TableField` 注解的命名风格（snake_case vs camelCase）

**涉及决策**：Design Doc 数据模型（Prize 表新增列 + type_config JSON Schema）

### 任务 1.1：创建 V13 迁移脚本

- **原任务编号**：1.1（tasks.md）
- **capability**：prize
- **目标**：在 `server/common/src/main/resources/db/migration/` 下创建 `V13__add_prize_model_config.sql`
- **实现方式**（Design Doc 数据模型）：
  ```sql
  ALTER TABLE prize ADD COLUMN prize_type     VARCHAR(20)  NOT NULL DEFAULT 'PHYSICAL';
  ALTER TABLE prize ADD COLUMN prize_category VARCHAR(30)  DEFAULT NULL;
  ALTER TABLE prize ADD COLUMN title_image    VARCHAR(500) DEFAULT NULL;
  ALTER TABLE prize ADD COLUMN detail_image   VARCHAR(500) DEFAULT NULL;
  ALTER TABLE prize ADD COLUMN valid_from     TIMESTAMP    DEFAULT NULL;
  ALTER TABLE prize ADD COLUMN valid_to       TIMESTAMP    DEFAULT NULL;
  ALTER TABLE prize ADD COLUMN type_config    JSON         DEFAULT NULL;
  ```
  - `prize_type` 默认值 `'PHYSICAL'` 确保既有实物奖品数据兼容
  - `prize_category` 为空时表示实物（`PHYSICAL` 类别）
  - `type_config` 为 JSON 类型（MySQL `JSON` / H2 PG 模式 `JSON` / PostgreSQL `JSONB`）
  - 所有新增列均为 NULLABLE（`prize_type` 除外，因 `NOT NULL` + 默认值）
- **输入**：Design Doc 数据模型 §Prize 表新增列 (V13)
- **输出**：`V13__add_prize_model_config.sql` 文件
- **验收标准**：
  - Flyway 在 H2 PostgreSQL 模式 / MySQL 8+ / PostgreSQL 15+ 上均可成功迁移
  - 新列默认值正确：既有记录 `prize_type='PHYSICAL'`, 其他列为 NULL
  - 迁移幂等可重试（Flyway checksum 验证）
  - 既有 `V7` 迁移定义的 `prize.image` 列不受影响
- **依赖任务**：无（仅需确认 V13 编号空闲）
- **运行验证**：
  ```bash
  mvn flyway:migrate -pl :common -am           # MySQL profile
  mvn -pl :common -am test -Dtest=*Migration*  # H2 PG 模式自动迁移
  ```

### 任务 1.2：更新 Prize 实体

- **原任务编号**：1.2（tasks.md）
- **capability**：prize
- **目标文件**：`server/common/src/main/java/com/cutegoals/common/entity/prize/Prize.java`
- **实现内容**（Design Doc Entity）：
  ```java
  @TableField("prize_type")       private String prizeType;       // VIRTUAL | PHYSICAL
  @TableField("prize_category")   private String prizeCategory;   // TV_TIME|COMPUTER_TIME|PARK_PLAY|GENERAL|TRAVEL
  @TableField("title_image")      private String titleImage;
  @TableField("detail_image")     private String detailImage;
  @TableField("valid_from")       private LocalDateTime validFrom;
  @TableField("valid_to")         private LocalDateTime validTo;
  @TableField("type_config")      private String typeConfig;      // JSON String
  ```
  - `typeConfig` 使用 `String` 类型（MyBatis-Plus 自动 JSON ↔ String 映射）
  - `prizeType` 默认值逻辑：创建时缺省取 `"PHYSICAL"`（与 DDL DEFAULT 一致）
- **输入**：Design Doc 数据模型 §Entity (Prize.java)
- **输出**：`Prize.java` 编译通过
- **验收标准**：
  - 字段序列化/反序列化正确（Jackson JSON）
  - MyBatis-Plus 映射到 V13 新增列
  - `prizeType` 默认 `"PHYSICAL"`，与 DEFAULT 值一致
  - 无遗留 `@TableField(exist = false)` 等错误注解
  - 既有 `image` 字段保持不变
- **依赖任务**：1.1（V13 必须先执行）
- **运行验证**：
  ```bash
  mvn -pl :common -am compile
  mvn -pl :prize -am compile
  ```

## 阶段 2：后端（2 子任务 → tasks.md §2）

**目标**：改造 `PrizeService.createPrize/updatePrize` 解析新字段并校验 `type_config` JSON；改造 `PrizeController.toPrizeMap` 包含新字段；新建 `UploadController` 提供图片上传端点；配置 `WebMvcConfigurer` 静态资源映射。

**前置 verify**：
- ⚡ verify `PrizeService.java` createPrize 和 updatePrize 方法中字段提取风格（`extractString`/`extractInteger` helper）
- ⚡ verify `PrizeController.java` toPrizeMap 方法中既有字段映射列表
- ⚡ verify `WebMvcConfig.java` 在 `server/web/src/main/java/.../config/` 的位置和既有 `addResourceHandler` 配置

**涉及决策**：Design Doc §后端实现（PrizeService 改造 + UploadController + WebMvcConfigurer）

### 任务 2.1：PrizeService 字段适配 + type_config 校验

- **原任务编号**：2.1（tasks.md）
- **capability**：prize
- **目标文件**：`server/prize/src/main/java/com/cutegoals/prize/service/PrizeService.java`
- **实现内容**（Design Doc §后端实现 — PrizeService 改造）：

  **createPrize 新增字段解析（在既有 name/pointsCost/stock 校验之后）：**
  ```java
  // --- 新增字段解析 ---
  String prizeType = extractString(request, "prizeType");
  if (prizeType == null) prizeType = "PHYSICAL";
  // 校验 prizeType 枚举
  if (!"VIRTUAL".equals(prizeType) && !"PHYSICAL".equals(prizeType)) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED,
          "prizeType must be VIRTUAL or PHYSICAL");
  }

  String prizeCategory = extractString(request, "prizeCategory");
  String titleImage = extractString(request, "titleImage");
  String detailImage = extractString(request, "detailImage");

  // 有效期解析
  LocalDateTime validFrom = null;
  if (request.containsKey("validFrom")) {
      validFrom = LocalDateTime.parse(extractString(request, "validFrom"));
  }
  LocalDateTime validTo = null;
  if (request.containsKey("validTo")) {
      validTo = LocalDateTime.parse(extractString(request, "validTo"));
  }

  // type_config 解析与校验
  String typeConfig = extractString(request, "typeConfig");
  if (typeConfig != null && !typeConfig.isEmpty()) {
      validateTypeConfig(prizeType, prizeCategory, typeConfig);
  }

  // 设置到 entity
  prize.setPrizeType(prizeType);
  prize.setPrizeCategory(prizeCategory);
  prize.setTitleImage(titleImage);
  prize.setDetailImage(detailImage);
  prize.setValidFrom(validFrom);
  prize.setValidTo(validTo);
  prize.setTypeConfig(typeConfig);
  ```

  **updatePrize 新增字段更新（在既有字段更新之后）：**
  ```java
  if (request.containsKey("prizeType")) {
      String pt = extractString(request, "prizeType");
      if (!"VIRTUAL".equals(pt) && !"PHYSICAL".equals(pt)) {
          throw new BusinessException(ErrorCode.VALIDATION_FAILED,
              "prizeType must be VIRTUAL or PHYSICAL");
      }
      prize.setPrizeType(pt);
  }
  if (request.containsKey("prizeCategory")) {
      prize.setPrizeCategory(extractString(request, "prizeCategory"));
  }
  if (request.containsKey("titleImage")) {
      prize.setTitleImage(extractString(request, "titleImage"));
  }
  if (request.containsKey("detailImage")) {
      prize.setDetailImage(extractString(request, "detailImage"));
  }
  if (request.containsKey("validFrom")) {
      prize.setValidFrom(LocalDateTime.parse(extractString(request, "validFrom")));
  }
  if (request.containsKey("validTo")) {
      prize.setValidTo(LocalDateTime.parse(extractString(request, "validTo")));
  }
  if (request.containsKey("typeConfig")) {
      String tc = extractString(request, "typeConfig");
      if (tc != null && !tc.isEmpty()) {
          validateTypeConfig(
              prize.getPrizeType() != null ? prize.getPrizeType() : extractString(request, "prizeType"),
              prize.getPrizeCategory() != null ? prize.getPrizeCategory() : extractString(request, "prizeCategory"),
              tc);
      }
      prize.setTypeConfig(tc);
  }
  ```

  **validateTypeConfig 私有方法（JSON Schema 校验）：**
  ```java
  private void validateTypeConfig(String prizeType, String prizeCategory, String typeConfig) {
      try {
          // 解析 JSON
          ObjectMapper mapper = new ObjectMapper();
          JsonNode config = mapper.readTree(typeConfig);

          if ("PHYSICAL".equals(prizeType)) {
              // 实物：必须包含 actualValue（非负整数）
              if (!config.has("actualValue")) {
                  throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                      "PHYSICAL prize requires actualValue in typeConfig");
              }
          } else if ("VIRTUAL".equals(prizeType)) {
              if (prizeCategory == null) {
                  throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                      "VIRTUAL prize requires prizeCategory");
              }
              switch (prizeCategory) {
                  case "TV_TIME":
                  case "COMPUTER_TIME":
                      // 必须包含 durationType + duration
                      if (!config.has("durationType")) {
                          throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                              prizeCategory + " requires durationType");
                      }
                      if (!config.has("duration")) {
                          throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                              prizeCategory + " requires duration");
                      }
                      break;
                  case "PARK_PLAY":
                  case "GENERAL":
                      // 必须包含 maxUses
                      if (!config.has("maxUses")) {
                          throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                              prizeCategory + " requires maxUses");
                      }
                      break;
                  case "TRAVEL":
                      // 必须包含 destination + travelDays + travelNights + actualValue
                      if (!config.has("destination") || !config.has("travelDays")
                          || !config.has("travelNights") || !config.has("actualValue")) {
                          throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                              "TRAVEL requires destination, travelDays, travelNights, actualValue");
                      }
                      break;
                  default:
                      throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                          "Unknown prizeCategory: " + prizeCategory);
              }
          }
      } catch (BusinessException e) {
          throw e;
      } catch (Exception e) {
          throw new BusinessException(ErrorCode.VALIDATION_FAILED,
              "Invalid typeConfig JSON: " + e.getMessage());
      }
  }
  ```
  - 注意：Design Doc 中 `queryPrizes` 无需修改（MyBatis-Plus 自动映射），此处不涉及

- **输入**：Design Doc §PrizeService 改造
- **输出**：`PrizeService.java` 编译通过，createPrize/updatePrize 支持新字段
- **验收标准**：
  - `prizeType` 缺省 → 默认 `PHYSICAL`
  - `prizeType` 非法值 → `VALIDATION_FAILED`
  - VIRTUAL + 合法 category + 合法 typeConfig → 创建成功
  - PHYSICAL + 缺省 typeConfig → 创建成功（`type_config = NULL`）
  - 各分类 typeConfig 缺少必填字段 → `VALIDATION_FAILED`
  - 更新时部分字段更新 → 仅修改请求中存在的字段
- **依赖任务**：1.2（实体字段就绪）
- **运行验证**：
  ```bash
  mvn -pl :prize -am test -Dtest=PrizeServiceTest
  ```

### 任务 2.2：PrizeController toPrizeMap 适配 + UploadController 新建

- **原任务编号**：2.2（tasks.md，包含 Controller 适配和上传）
- **capability**：prize
- **涉及两个目标**：

  **子任务 2.2a：PrizeController.toPrizeMap 适配**
  - **目标文件**：`server/prize/src/main/java/com/cutegoals/prize/controller/PrizeController.java`
  - **实现内容**（Design Doc §PrizeController toPrizeMap 适配）：
    在 `toPrizeMap` 方法中追加新字段映射：
    ```java
    data.put("prizeType", prize.getPrizeType());
    data.put("prizeCategory", prize.getPrizeCategory());
    data.put("titleImage", prize.getTitleImage());
    data.put("detailImage", prize.getDetailImage());
    data.put("validFrom", prize.getValidFrom());
    data.put("validTo", prize.getValidTo());
    data.put("typeConfig", prize.getTypeConfig());
    ```
  - **注意**：`createPrize` 和 `updatePrize` 端点均通过 `toPrizeMap` 返回，一次修改覆盖两处

  **子任务 2.2b：UploadController 新建**
  - **目标文件**（新建）：`server/prize/src/main/java/com/cutegoals/prize/controller/UploadController.java`
  - **实现内容**（Design Doc §UploadController）：
    ```java
    package com.cutegoals.prize.controller;

    import org.springframework.http.ResponseEntity;
    import org.springframework.web.bind.annotation.*;
    import org.springframework.web.multipart.MultipartFile;

    import java.io.File;
    import java.util.Map;
    import java.util.UUID;

    @RestController
    @RequestMapping("/api/prizes")
    public class UploadController {

        @PostMapping("/upload")
        public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
            // 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String ext = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                ext = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String filename = UUID.randomUUID().toString().replace("-", "") + "_" + originalFilename;

            // 确保目录存在
            File uploadDir = new File("uploads/prizes/");
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }

            file.transferTo(new File(uploadDir, filename));
            return ResponseEntity.ok(Map.of("url", "/uploads/prizes/" + filename));
        }
    }
    ```
  - 文件大小和类型校验可留到后续迭代（当前 MVP 不做严格限制）

  **子任务 2.2c：WebMvcConfigurer 静态资源映射**
  - **目标文件**：`server/web/src/main/java/com/cutegoals/web/config/WebMvcConfig.java`
  - **实现内容**（Design Doc §WebMvcConfigurer）：
    检查是否有 `addResourceHandler` 配置，若无则追加：
    ```java
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:uploads/");
    }
    ```
    - 若 `WebMvcConfig` 已有 `addResourceHandlers` 方法，则只追加 `/uploads/**` 行

- **输入**：Design Doc §UploadController + §WebMvcConfigurer
- **输出**：
  - `PrizeController.java` 编译通过
  - `UploadController.java` 新建编译通过
  - `WebMvcConfig.java` 配置完成
- **验收标准**：
  - 所有 API 返回的 prize map 包含 7 个新字段（值为 entity 对应值）
  - POST `/api/prizes/upload` 返回 `{"url": "/uploads/prizes/xxx.jpg"}`
  - 上传文件实际写入 `uploads/prizes/` 目录
  - 访问 `/uploads/prizes/xxx.jpg` 返回正确的静态文件
- **依赖任务**：2.1（Service 字段适配就绪后 toPrizeMap 才有意义；UploadController 和 WebMvcConfigurer 可与 2.1 并行）
- **建议执行顺序**：上传模块（2.2b + 2.2c）可先做，toPrizeMap（2.2a）依赖 2.1
- **运行验证**：
  ```bash
  mvn -pl :prize -am compile
  mvn -pl :web -am compile
  # 手动验证（启动服务后）：
  # curl -F "file=@test.jpg" http://localhost:8080/api/prizes/upload
  # curl http://localhost:8080/uploads/prizes/test_xxx.jpg
  ```

## 阶段 3：前端（2 子任务 → tasks.md §3）

**目标**：新建 `PrizeTypeConfigForms` 组件实现分类型奖品配置表单；改造 `ParentPrizesPage` 集成新表单，增加类型列展示，支持图片上传。

**前置 verify**：
- ⚡ verify `web/src/parent/pages/index.tsx` 中 `ParentPrizesPage` 函数的精确行号范围（1568-1733）
- ⚡ verify `web/src/parent/pages/index.tsx` 中 Modal 的现有 width（默认 520px，需增大到 640px）
- ⚡ verify `web/src/shared/api/types.ts` 中是否存在 `errorCodes`（用于后端错误映射，新增 `ErrorCodes`）

**涉及决策**：Design Doc §前端实现（PrizeTypeConfigForms + ParentPrizesPage）

### 任务 3.1：新建 PrizeTypeConfigForms 组件

- **原任务编号**：3.1（tasks.md）
- **capability**：prize（前端）
- **目标文件**（新建）：`web/src/parent/components/PrizeTypeConfigForms.tsx`
- **实现内容**（Design Doc §PrizeTypeConfigForms 组件）：
  ```tsx
  import { Radio, Select, InputNumber, Input, DatePicker, Space, Typography } from 'antd';
  import dayjs from 'dayjs';

  type PrizeType = 'VIRTUAL' | 'PHYSICAL';
  type PrizeCategory = 'TV_TIME' | 'COMPUTER_TIME' | 'PARK_PLAY' | 'GENERAL' | 'TRAVEL';

  interface PrizeTypeConfig {
    prizeType: PrizeType;
    prizeCategory?: PrizeCategory;
    titleImage?: string;
    detailImage?: string;
    validFrom?: string;
    validTo?: string;
    typeConfig?: string; // JSON string
  }

  interface Props {
    value: PrizeTypeConfig;
    onChange: (v: PrizeTypeConfig) => void;
    onUpload: (file: File) => Promise<string>;
  }

  export function PrizeTypeConfigForms({ value, onChange, onUpload }: Props) {
    const { prizeType, prizeCategory } = value;

    return (
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        {/* 奖品类型选择 */}
        <div>
          <Typography.Text strong>奖品类型</Typography.Text>
          <Radio.Group
            value={prizeType}
            onChange={e => onChange({ ...value, prizeType: e.target.value, prizeCategory: undefined, typeConfig: undefined })}
          >
            <Radio value="PHYSICAL">实物</Radio>
            <Radio value="VIRTUAL">虚拟</Radio>
          </Radio.Group>
        </div>

        {/* 图片上传 */}
        <div>
          <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>标题图</Typography.Text>
          <ImageUpload value={value.titleImage} onUpload={onUpload}
            onChange={url => onChange({ ...value, titleImage: url })} />
        </div>
        <div>
          <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>详情图</Typography.Text>
          <ImageUpload value={value.detailImage} onUpload={onUpload}
            onChange={url => onChange({ ...value, detailImage: url })} />
        </div>

        {/* 有效期 */}
        <div>
          <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>有效期</Typography.Text>
          <DatePicker
            placeholder="开始日期"
            value={value.validFrom ? dayjs(value.validFrom) : null}
            onChange={d => onChange({ ...value, validFrom: d?.toISOString() })}
          />
          <span style={{ margin: '0 8px' }}>至</span>
          <DatePicker
            placeholder="结束日期"
            value={value.validTo ? dayjs(value.validTo) : null}
            onChange={d => onChange({ ...value, validTo: d?.toISOString() })}
          />
        </div>

        {/* 虚拟奖品 → 分类选择 + 类型配置 */}
        {prizeType === 'VIRTUAL' && (
          <>
            <div>
              <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>奖品分类</Typography.Text>
              <Select
                style={{ width: '100%' }}
                value={prizeCategory}
                onChange={cat => onChange({ ...value, prizeCategory: cat, typeConfig: undefined })}
                placeholder="选择分类"
                options={[
                  { label: '电视时长卡', value: 'TV_TIME' },
                  { label: '电脑时长卡', value: 'COMPUTER_TIME' },
                  { label: '公园游玩卡', value: 'PARK_PLAY' },
                  { label: '通用', value: 'GENERAL' },
                  { label: '旅游卡', value: 'TRAVEL' },
                ]}
              />
            </div>

            {prizeCategory && (
              <VirtualConfigForm
                category={prizeCategory}
                value={value.typeConfig}
                onChange={tc => onChange({ ...value, typeConfig: tc })}
              />
            )}
          </>
        )}

        {/* 实物 → 实际价值 */}
        {prizeType === 'PHYSICAL' && (
          <PhysicalConfigForm
            value={value.typeConfig}
            onChange={tc => onChange({ ...value, typeConfig: tc })}
          />
        )}
      </Space>
    );
  }

  /* 子组件：图片上传 */
  function ImageUpload({ value, onUpload, onChange }: {
    value?: string;
    onUpload: (file: File) => Promise<string>;
    onChange: (url: string) => void;
  }) {
    return (
      <div>
        {value && <img src={value} alt="preview" style={{ maxWidth: 200, maxHeight: 120, marginBottom: 8 }} />}
        <input type="file" accept="image/*" onChange={async e => {
          const file = e.target.files?.[0];
          if (file) {
            const url = await onUpload(file);
            onChange(url);
          }
        }} />
      </div>
    );
  }

  /* 虚拟奖品配置子表单 */
  function VirtualConfigForm({ category, value, onChange }: {
    category: PrizeCategory;
    value?: string;
    onChange: (json: string) => void;
  }) {
    const config = value ? JSON.parse(value) : {};

    const update = (partial: Record<string, unknown>) => {
      onChange(JSON.stringify({ ...config, ...partial }));
    };

    if (category === 'TV_TIME' || category === 'COMPUTER_TIME') {
      return (
        <Space direction="vertical" size="small" style={{ width: '100%' }}>
          <div>
            <Typography.Text strong>时长类型</Typography.Text>
            <Select
              style={{ width: '100%' }}
              value={config.durationType}
              onChange={v => update({ durationType: v })}
              options={[
                { label: '每日', value: 'DAILY' },
                { label: '每周', value: 'WEEKLY' },
                { label: '每月', value: 'MONTHLY' },
                { label: '补充包', value: 'SUPPLEMENT' },
              ]}
            />
          </div>
          <div>
            <Typography.Text strong>时长（分钟）</Typography.Text>
            <InputNumber min={1} style={{ width: '100%' }}
              value={config.duration}
              onChange={v => update({ duration: v })} />
          </div>
        </Space>
      );
    }

    if (category === 'PARK_PLAY' || category === 'GENERAL') {
      return (
        <div>
          <Typography.Text strong>可用次数</Typography.Text>
          <InputNumber min={1} style={{ width: '100%' }}
            value={config.maxUses}
            onChange={v => update({ maxUses: v })} />
        </div>
      );
    }

    if (category === 'TRAVEL') {
      return (
        <Space direction="vertical" size="small" style={{ width: '100%' }}>
          <div>
            <Typography.Text strong>目的地</Typography.Text>
            <Input value={config.destination}
              onChange={e => update({ destination: e.target.value })} />
          </div>
          <div>
            <Typography.Text strong>旅行天数</Typography.Text>
            <InputNumber min={1} style={{ width: '100%' }}
              value={config.travelDays}
              onChange={v => update({ travelDays: v })} />
          </div>
          <div>
            <Typography.Text strong>旅行夜数</Typography.Text>
            <InputNumber min={0} style={{ width: '100%' }}
              value={config.travelNights}
              onChange={v => update({ travelNights: v })} />
          </div>
          <div>
            <Typography.Text strong>实际价值</Typography.Text>
            <InputNumber min={0} style={{ width: '100%' }}
              value={config.actualValue}
              onChange={v => update({ actualValue: v })} />
          </div>
        </Space>
      );
    }

    return null;
  }

  /* 实物配置子表单 */
  function PhysicalConfigForm({ value, onChange }: {
    value?: string;
    onChange: (json: string) => void;
  }) {
    const config = value ? JSON.parse(value) : {};
    return (
      <div>
        <Typography.Text strong>实际价值</Typography.Text>
        <InputNumber min={0} style={{ width: '100%' }}
          value={config.actualValue}
          onChange={v => onChange(JSON.stringify({ actualValue: v }))} />
      </div>
    );
  }
  ```
- **输入**：Design Doc §PrizeTypeConfigForms 组件 UI 树
- **输出**：`web/src/parent/components/PrizeTypeConfigForms.tsx`
- **验收标准**：
  - 选择「虚拟」→ 显示分类 Select，选择后显示对应配置子表单
  - 选择「实物」→ 显示实际价值输入
  - 切换类型/分类时清除已选的 typeConfig（避免残留数据）
  - 上传组件调用 `onUpload` 回调并显示预览
- **依赖任务**：2.2b（上传 API 就绪后 onUpload 才可用）— 但组件本身可先独立开发和测试
- **运行验证**：
  ```bash
  cd web && npx tsc --noEmit
  cd web && pnpm test -- --testPathPattern="PrizeTypeConfigForms"
  ```

### 任务 3.2：ParentPrizesPage 集成 + 列表适配

- **原任务编号**：3.2（tasks.md）
- **capability**：prize（前端）
- **目标文件**：`web/src/parent/pages/index.tsx`
- **实现内容**（Design Doc §ParentPrizesPage 改造）：

  **3.2a：Prize 接口扩展**
  - 在 I156 (`interface Prize`) 位置，追加新字段：
    ```typescript
    interface Prize {
      // ...既有字段不变
      prizeType?: string;
      prizeCategory?: string;
      titleImage?: string;
      detailImage?: string;
      validFrom?: string;
      validTo?: string;
      typeConfig?: string;
    }
    ```

  **3.2b：新增状态和 handleUpload**
  - 在 `ParentPrizesPage` 函数中追加：
    ```typescript
    const prizeType = useFormField('PHYSICAL');
    const prizeCategory = useFormField();
    const titleImage = useFormField();
    const detailImage = useFormField();
    const validFrom = useFormField();
    const validTo = useFormField();
    const typeConfig = useFormField();

    const handleUpload = async (file: File): Promise<string> => {
      const formData = new FormData();
      formData.append('file', file);
      const res = await getClient().post('/prizes/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      return res.data.url;
    };
    ```

  **3.2c：openNew / openEdit 集成**
  - `openNew` 中重置新字段：
    ```typescript
    prizeType.setValue('PHYSICAL');
    prizeCategory.reset();
    titleImage.reset();
    detailImage.reset();
    validFrom.reset();
    validTo.reset();
    typeConfig.reset();
    ```
  - `openEdit` 中回填：
    ```typescript
    prizeType.setValue(p.prizeType ?? 'PHYSICAL');
    prizeCategory.setValue(p.prizeCategory ?? '');
    titleImage.setValue(p.titleImage ?? '');
    detailImage.setValue(p.detailImage ?? '');
    validFrom.setValue(p.validFrom ?? '');
    validTo.setValue(p.validTo ?? '');
    typeConfig.setValue(p.typeConfig ?? '');
    ```

  **3.2d：handleSave 序列化新字段**
  - 在 payload 中追加：
    ```typescript
    const payload = {
      name: name.value,
      description: description.value,
      pointsCost: Number(pointsCost.value),
      availableStock: Number(availableStock.value),
      prizeType: prizeType.value,
      prizeCategory: prizeCategory.value || undefined,
      titleImage: titleImage.value || undefined,
      detailImage: detailImage.value || undefined,
      validFrom: validFrom.value || undefined,
      validTo: validTo.value || undefined,
      typeConfig: typeConfig.value || undefined,
    };
    ```

  **3.2e：Modal 中集成 PrizeTypeConfigForms**
  - 替换 `Space direction="vertical"` 内容，在既有基础字段下方追加 `PrizeTypeConfigForms`：
    ```tsx
    <Modal
      open={showModal}
      onCancel={() => setShowModal(false)}
      title={editing ? '编辑奖品' : '新增奖品'}
      okText="保存"
      onOk={handleSave}
      confirmLoading={saving}
      width={640}  {/* Modal width 增加到 640px */}
    >
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        {saveError && <Alert message={saveError} type="error" showIcon />}
        {/* 基础字段（名称、描述、积分、库存）保持不变 */}
        <div>
          <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>名称</Typography.Text>
          <Input {...name.inputProps} />
        </div>
        <div>
          <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>描述</Typography.Text>
          <TextArea {...description.inputProps} />
        </div>
        <div>
          <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>积分价格</Typography.Text>
          <Input type="number" {...pointsCost.inputProps} />
        </div>
        <div>
          <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>库存</Typography.Text>
          <Input type="number" {...availableStock.inputProps} />
        </div>

        {/* 奖品类型配置 */}
        <PrizeTypeConfigForms
          value={{
            prizeType: prizeType.value as 'VIRTUAL' | 'PHYSICAL',
            prizeCategory: prizeCategory.value as any,
            titleImage: titleImage.value,
            detailImage: detailImage.value,
            validFrom: validFrom.value,
            validTo: validTo.value,
            typeConfig: typeConfig.value,
          }}
          onChange={v => {
            prizeType.setValue(v.prizeType);
            prizeCategory.setValue(v.prizeCategory ?? '');
            titleImage.setValue(v.titleImage ?? '');
            detailImage.setValue(v.detailImage ?? '');
            validFrom.setValue(v.validFrom ?? '');
            validTo.setValue(v.validTo ?? '');
            typeConfig.setValue(v.typeConfig ?? '');
          }}
          onUpload={handleUpload}
        />
      </Space>
    </Modal>
    ```

  **3.2f：列表新增「类型」列**
  - 在 Table columns 中 `{ title: '积分', ... }` 之后追加：
    ```typescript
    {
      title: '类型',
      key: 'prizeType',
      render: (_: unknown, record: Prize) => {
        if (record.prizeType === 'VIRTUAL') {
          const categoryLabels: Record<string, string> = {
            TV_TIME: '电视时长卡',
            COMPUTER_TIME: '电脑时长卡',
            PARK_PLAY: '公园游玩卡',
            GENERAL: '通用',
            TRAVEL: '旅游卡',
          };
          return <Tag color="blue">虚拟 · {categoryLabels[record.prizeCategory ?? ''] || record.prizeCategory}</Tag>;
        }
        return <Tag>实物</Tag>;
      },
    },
    ```

- **输入**：Design Doc §ParentPrizesPage 改造
- **输出**：`web/src/parent/pages/index.tsx` 修改完毕
- **验收标准**：
  - Modal width = 640px
  - 新建/编辑表单包含基础字段 + PrizeTypeConfigForms
  - handleSave 正确序列化所有新字段到 API payload
  - openEdit 回显正确（编辑时所有字段回填）
  - 列表表格显示「类型」列，虚拟奖品显示分类中文名
  - 图片上传功能正常（通过 handleUpload → UploadController）
- **依赖任务**：3.1（PrizeTypeConfigForms 组件就绪）
- **运行验证**：
  ```bash
  cd web && npx tsc --noEmit
  cd web && pnpm test
  ```

## 测试策略汇总

| 阶段 | 测试类型 | 测试类/文件 | 覆盖点 |
|------|----------|------------|--------|
| 1 | 迁移回归 | `MigrationV13Test`（待新建） | V13 迁移执行成功、默认值正确、既有数据兼容 |
| 2 | 单元测试 | `PrizeServiceTest` | createPrize 各类型奖品 type_config 校验、持久化正确性 |
| 2 | 集成测试 | `UploadControllerTest`（待新建） | 文件上传返回正确 URL、文件写入磁盘 |
| 3 | 组件测试 | `PrizeTypeConfigForms.test.tsx`（待新建） | 分类型渲染正确性、切换类型时表单联动 |
| 3 | 编译检查 | `npx tsc --noEmit` | TypeScript 类型零错误 |

## 受影响代码路径完整清单

| 路径 | 变更类型 | 涉及任务 |
|------|----------|----------|
| `server/common/src/main/resources/db/migration/V13__add_prize_model_config.sql` | **新增** | 1.1 |
| `server/common/src/main/java/com/cutegoals/common/entity/prize/Prize.java` | 修改 +7 字段 | 1.2 |
| `server/prize/src/main/java/com/cutegoals/prize/service/PrizeService.java` | 修改 createPrize/updatePrize + validateTypeConfig | 2.1 |
| `server/prize/src/main/java/com/cutegoals/prize/controller/PrizeController.java` | 修改 toPrizeMap | 2.2a |
| `server/prize/src/main/java/com/cutegoals/prize/controller/UploadController.java` | **新增** | 2.2b |
| `server/web/src/main/java/com/cutegoals/web/config/WebMvcConfig.java` | 修改 addResourceHandlers | 2.2c |
| `web/src/parent/components/PrizeTypeConfigForms.tsx` | **新增** | 3.1 |
| `web/src/parent/pages/index.tsx` | 修改 Prize 接口 + ParentPrizesPage | 3.2 |

## 实施顺序建议

```
Phase 1: 1.1 (V13 迁移) → 1.2 (Prize Entity)
    ↓
Phase 2: 2.2b (UploadController) + 2.2c (WebMvcConfig) 可先做
    ↓        ↘
   2.1 (PrizeService 改造) → 2.2a (toPrizeMap)
    ↓
Phase 3: 3.1 (PrizeTypeConfigForms 组件) → 3.2 (ParentPrizesPage 集成)
```

每一阶段完成后建议立即运行对应的验证命令。上传模块（2.2b + 2.2c）与 Service 改造（2.1）无依赖，可并行实施。
