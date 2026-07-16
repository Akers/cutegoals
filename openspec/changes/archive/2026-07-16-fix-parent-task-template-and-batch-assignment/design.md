# 修复方案：家长端任务模板状态显示与批量分配请求

## 1. 任务模板列表增加启用状态显示

**文件**：`web/src/parent/pages/index.tsx`

在 `ParentTaskTemplatesPage` 的模板卡片中，除了“停用/启用”按钮外，增加一个状态标签：

- 当 `t.enabled === true` 时，显示“已启用”标签，使用绿色/成功色调；
- 当 `t.enabled === false` 时，显示“已停用”标签，使用灰色/禁用色调；
- 保持原有的 `toggleEnabled` 按钮和文案逻辑不变。

该标签应放在模板名称右侧或卡片信息区域，确保状态一目了然。

## 2. 批量分配弹窗对齐后端契约

**文件**：`web/src/parent/pages/index.tsx`

调整 `ParentTasksPage` 的批量分配弹窗与提交逻辑：

### 字段调整

- **模板**：保留单选下拉框，选择后联动填充难度下拉框；
- **难度**：新增下拉框，列出当前模板中 `enabled === true` 的难度，默认选中第一个启用难度；
- **孩子**：将现有单选 `<Select>` 改为多选 `<select multiple>`，已选孩子维护为数组；
- **日期范围**：将“截止时间”改为“开始日期”和“结束日期”两个日期输入；
- **幂等键**：前端生成 UUID，避免重复提交时创建重复分配。

### 提交请求体

将原来发送的 `assignments` 数组结构改为后端期望的批量结构：

```json
{
  "templateId": 2,
  "difficultyId": 3,
  "startDate": "2026-07-15",
  "endDate": "2026-07-21",
  "childIds": [1, 2],
  "idempotencyKey": "..."
}
```

其中 `startDate` 和 `endDate` 使用 `YYYY-MM-DD` 格式（`type="date"`）。

### 校验与错误处理

- 提交前校验：模板、难度、至少一个孩子、开始日期和结束日期不能为空；
- 开始日期不得晚于结束日期；
- 接口调用失败时弹出错误提示，并保留弹窗状态；
- 成功提交后刷新任务列表并关闭弹窗。

## 不修改范围

- 不修改后端 `TaskAssignmentService`、`TaskAssignmentController` 或数据库 schema；
- 不修改现有 API 契约；
- 不新增 capability 或 public API。
