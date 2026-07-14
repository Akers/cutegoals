# Design: fix-template-save-field-mismatch

## 方案
1. `TaskTemplate` 接口：`title` 改名 `name`；移除 `basePoints`（积分从 difficulties 取）。
2. `Difficulty` 接口：`points` 改名 `rewardPoints`；新增 `displayOrder: number`。
3. `handleSave` payload：
   ```js
   {
     name: title.value,
     description: description.value,
     category: category.value,
     difficulties: [{ name: '标准', displayOrder: 1, rewardPoints: Number(basePoints.value), enabled: true }]
   }
   ```
4. 编辑时 `openEdit`：`title.setValue(t.name)`；`basePoints.setValue(String(t.difficulties?.[0]?.rewardPoints ?? 10))`。
5. 列表卡片：`t.name` 替代 `t.title`；积分显示 `t.difficulties?.[0]?.rewardPoints`。
6. `handleSave` 检查 `res.error`：失败时 `setSaving(false)` 不关闭弹窗（由于该页面当前没有 actionError 状态，用 window.alert 简单提示）。

## 边界
- 只支持单难度（简化表单），后端支持多难度但前端表单只有 basePoints 一个输入。
- 编辑时 version 字段后端必需，从 `t.version` 读取并在 payload 中带上。
