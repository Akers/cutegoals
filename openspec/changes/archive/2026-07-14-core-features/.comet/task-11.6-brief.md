# Task 11.6: Wire taskType/typeConfig through Service Layer

## 问题

`TaskTemplateService.createTemplate()` 和 `updateTemplate()` 不从请求中提取 `taskType`/`typeConfig` 字段设置到实体。`getTemplateDetail()`/`queryTemplates()` 的返回数据也不包含这些字段。

## 要求

### 1. TaskTemplateService.createTemplate()
在 `TaskTemplateService.createTemplate()` 方法中，从 `request` Map 提取 `taskType` (String) 和 `typeConfig` (String)：
```java
String taskType = (String) request.get("taskType");
if (taskType != null) {
    template.setTaskType(taskType);
}
String typeConfig = (String) request.get("typeConfig");
if (typeConfig != null) {
    template.setTypeConfig(typeConfig);
}
```
注意：模板创建时 `taskType` 不要求必填（兼容旧 API），如果为空则使用默认值 "LIMITED"。

### 2. TaskTemplateService.updateTemplate()
更新时同样提取这两个字段。如果请求中包含 `taskType` 且与现有 `taskType` 不同，返回 `TASK_TEMPLATE_TYPE_IMMUTABLE`（错误码已由 11.1 添加）。如果只有 `typeConfig` 且 `taskType` 未变，允许更新 typeConfig。

### 3. 响应包含新字段
`getTemplateDetail()` 和 `queryTemplates()` 返回的 Map 应包含 `taskType` 和 `typeConfig` 字段。检查当前实现，如果使用 Map.of/MapBuilder 构建响应，追加这两个字段。

### 4. 测试
验证：`mvn -f server/pom.xml test -pl web -am` 全部通过（92 项 web 测试）。

## 文件范围
- `server/task/src/main/java/com/cutegoals/task/service/TaskTemplateService.java`
- `server/task/src/main/java/com/cutegoals/task/controller/TaskTemplateController.java`（如需）

## 依赖
- 依赖 11.1-11.5 全部完成
- `TaskTemplate` entity 已有 `taskType` 和 `typeConfig` 字段
