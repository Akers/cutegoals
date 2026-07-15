## Why

家长端批量分配任务时，后端 `POST /api/task-assignments/batch` 因 `childIds` 中的 JSON 数字被反序列化为 `java.lang.Integer`，而代码直接以 `List<Long>` 迭代，导致 `ClassCastException: Integer cannot be cast to Long`，批量分配 500 失败。需要修复该类型转换错误，使批量分配可用。

## What Changes

- 修复 `TaskAssignmentService.createBatchAssignments` 对 `childIds` 的类型处理：从 `Map<String, Object>` 读取时统一将元素按 `Number.longValue()` 转换为 `Long`，不再直接强转泛型。
- 保持 `templateId`、`difficultyId` 已有的 `Number.longValue()` 处理逻辑不变。
- 修复后 `POST /api/task-assignments/batch` 在 `childIds` 为 `[1]` 等整数数组时不再抛出 500，并正常返回分配结果。

## Capabilities

### New Capabilities

（无新 capability）

### Modified Capabilities

- `task-assignment`: 在「原子批量分配」要求中补充「批量请求中的 childIds 为 JSON 整数数组」场景，明确后端必须兼容 Integer 类型的反序列化结果。

## Impact

- 后端：`server/task/src/main/java/com/cutegoals/task/service/TaskAssignmentService.java`
- 可能涉及的测试：`TaskAssignmentServiceTest` 中批量分配相关用例
- 前端无需改动
