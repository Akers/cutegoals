# Design: 重复任务单分配——完全无需录入日期

## 改动范围

| 文件 | 改动 |
|------|------|
| `web/src/parent/pages/index.tsx` | 单分配弹窗 REPEAT 时隐藏所有日期字段，提交时不带 deadline |
| `server/task/src/main/java/com/cutegoals/task/service/TaskAssignmentService.java` | `createAssignment` 中 REPEAT 模板允许 deadline 为空，自动设为当天 |

## 前端

- 将原来 REPEAT 分支的 `singleStartDate`/`singleEndDate` 完全移除，不显示任何日期字段
- `handleSingleAssign` 中 REPEAT 分支直接调用 `POST /task-assignments`（不带 deadline）
- `resetSingleAssignForm` 重置时不需要重置日期

## 后端

在 `TaskAssignmentService.createAssignment` 中：
- 如果 `deadlineStr == null` 且模板 `taskType == "REPEAT"`：跳过 deadline 校验，设 `deadline = LocalDate.now().atTime(23, 59, 59)`
- 如果 `deadlineStr == null` 且模板不是 REPEAT：维持现有报错
