# Task 1.2 Report: queryAssignments 新增 taskType 筛选参数

## 实现概述

### Controller 变更
`TaskAssignmentController.java`:
- `queryAssignments` 方法签名新增 `@RequestParam(required = false) String taskType` 参数，插入在 `endDate` 和 `cancelled` 之间
- params map 构建中添加 `if (taskType != null) params.put("taskType", taskType);`

### Service 变更
`TaskAssignmentService.java`:
- `queryAssignments` 方法中，在 cancelled 筛选之后、overdue 筛选之前新增 taskType 筛选逻辑
- 当 params 中有 `taskType` 时：
  - 检查非空和非空白 (`!taskType.isBlank()`)
  - `trim()` 去除前后空白
  - 使用 `\\s*,\\s*` 正则分割，容忍逗号周围空格
  - 在 `snapshotTemplateTaskType` 字段上执行 `in` 查询

### 测试覆盖
`TaskAssignmentServiceTest.java`:
- 新增 `shouldFilterByTaskType()` 测试方法，覆盖 4 个场景：
  1. 单值筛选 (`taskType=LIMITED`) → 仅返回 LIMITED
  2. 多值含空格筛选 (`taskType=LIMITED, REPEAT`) → 返回两种类型（验证空格容忍）
  3. 不传 `taskType` → 返回全部（向后兼容）
  4. 传空字符串 `taskType=""` → 返回全部

## 影响范围
- **代码**: Controller + Service + Test
- **API**: `GET /api/task-assignments?taskType=LIMITED,REPEAT` 新增可选参数
- **数据库**: 无变更，使用已有 `snapshot_template_task_type` 字段

## 验证结果
- `TaskAssignmentServiceTest`: 29 tests passed, 0 failed ✅
- 4 个 `TaskTemplateServiceTest` 失败为既存问题，与本次变更无关
