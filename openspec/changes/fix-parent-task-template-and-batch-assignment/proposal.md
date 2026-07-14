# 修复：家长端任务模板状态显示与批量分配请求

## 问题描述

1. 家长端任务模板管理页面中，模板列表缺少“是否启用”的显式状态显示。虽然已有“停用/启用”按钮，但按钮本身不能清晰反映当前模板的启用/停用状态，家长无法一眼判断模板状态。

2. 家长端任务分配页面中，点击“批量分配”并选择任务模板和孩子后，调用 `POST /api/task-assignments/batch` 失败，返回：

```json
{
    "code": "VALIDATION_FAILED",
    "message": "At least one childId is required",
    "data": null,
    "request_id": "05b826861faf47c5"
}
```

## 根因分析

### 根因 1：模板列表缺少状态标签

`web/src/parent/pages/index.tsx` 中 `ParentTaskTemplatesPage` 的模板列表只展示了名称、描述、分类、积分和任务类型，以及“停用/启用”按钮。按钮文案会根据 `t.enabled` 变化，但列表中没有一个明确的状态标识（如“已启用”/“已停用”），导致状态不可见。

### 根因 2：批量分配前端请求体与后端接口契约不匹配

后端 `TaskAssignmentService.createBatchAssignments` 期望的批量分配请求体为：

```json
{
  "templateId": 2,
  "difficultyId": 3,
  "startDate": "2026-07-15",
  "endDate": "2026-07-21",
  "childIds": [1],
  "idempotencyKey": "..."
}
```

其中 `startDate` / `endDate` 表示包含两端的本地日期范围，系统会按日期范围批量创建分配，且 `childIds` 是数组。

但前端 `ParentTasksPage.handleAssign` 当前发送的是旧结构：

```json
{
  "assignments": [
    {
      "templateId": 2,
      "childId": 1,
      "deadline": ""
    }
  ]
}
```

后端读取 `request.get("childIds")` 得到 `null`，因此校验抛出 `At least one childId is required`。前端 UI 当前只提供了“模板”“孩子”“截止时间”三个字段，也没有选择难度和日期范围，无法直接调用后端接口。

## 修复目标

1. 在家长端任务模板列表中增加“是否启用”状态显示，保持现有按钮功能不变。
2. 调整家长端批量分配弹窗，使其字段与后端契约一致：
   - 选择模板后，自动填充并允许选择难度（默认取第一个启用难度）；
   - 提供开始日期和结束日期；
   - 支持选择一个或多个孩子（复用现有多选）；
   - 生成幂等键并发送正确结构。
3. 不修改后端接口契约或数据库 schema，仅修复前端调用与展示。
