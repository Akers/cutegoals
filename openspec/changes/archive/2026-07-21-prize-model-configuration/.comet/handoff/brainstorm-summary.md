# Brainstorm Summary

- Change: prize-model-configuration
- Date: 2026-07-21

## 确认的技术方案

### 数据模型
Prize 表新增 7 列：prize_type(VIRTUAL/PHYSICAL), prize_category(TV_TIME/COMPUTER_TIME/PARK_PLAY/GENERAL/TRAVEL), title_image, detail_image, valid_from, valid_to, type_config(JSON)。type_config 按分类存储特有配置，复用 TaskTemplate 的枚举+JSON 混合模式。

### 图片上传
本地存储方案：`POST /api/prizes/upload` MultipartFile → `uploads/prizes/` → Spring Boot 静态映射 `/uploads/**` 返回相对路径。前端 `<input type="file">` + FormData 选中即上传。

### 后端
Prize entity 新增字段；PrizeService 创建/更新适配 type_config 校验；PrizeController toPrizeMap 输出新字段；新增 UploadController。

### 前端
新增 PrizeTypeConfigForms 组件（仿 TaskTypeConfigForms 模式）：类型选择+二级分类+公共配置区+动态特有配置子表单。ParentPrizesPage Modal 重构集成新组件。

## 关键取舍与风险

- JSON 列 vs 独立子表：选 JSON 列，MVP 组合少、扩展灵活
- 本地存储 vs 对象存储：选本地存储，后续平滑迁移
- 图片上传失败不会阻断奖品创建（允许空值）

## 测试策略

- 后端：PrizeService 新建/更新单元测试覆盖各 prizeCategory
- 前端：PrizeTypeConfigForms 渲染正确性测试

## Spec Patch

无
