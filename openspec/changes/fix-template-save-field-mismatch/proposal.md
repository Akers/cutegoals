# Proposal: fix-template-save-field-mismatch

## 问题
家长端 `/parent/templates` 新建/编辑模板时 POST `/api/task-templates` 返回 `TASK_TEMPLATE_VALIDATION_FAILED: name is required`，模板无法保存。

## 根因
前端 `ParentTemplatesPage` 发送字段 `title`/`basePoints`，后端 `TaskTemplateService.createTemplate` 要求 `name`（必填）和 `difficulties` 数组（至少 1 个）。字段名不匹配，`name` 永远为空导致校验失败。

同时前端 `TaskTemplate`/`Difficulty` 接口字段（`title`、`basePoints`、`points`）与后端返回（`name`、`difficulties[].rewardPoints`）不匹配，列表渲染也会显示 undefined。

## 修复目标
1. `TaskTemplate` 接口：`title` → `name`，移除 `basePoints`。
2. `Difficulty` 接口：`points` → `rewardPoints`，加 `displayOrder`。
3. `handleSave`：发送 `name` + 构造 `difficulties` 数组（单个默认难度）。
4. 列表/编辑渲染：用 `t.name` 和 `difficulties[0]?.rewardPoints`。
5. 检查 `response.error`，失败时展示错误不关闭弹窗。

## 范围
- 仅 `web/src/parent/pages/index.tsx`。

## 非目标
- 不修改后端；
- 不新增 API 或 delta spec。
