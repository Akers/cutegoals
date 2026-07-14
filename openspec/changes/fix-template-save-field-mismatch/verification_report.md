# Verification Report: fix-template-save-field-mismatch

## Summary
| Dimension    | Status                                              |
|--------------|-----------------------------------------------------|
| Completeness | 7/7 tasks done                                      |
| Correctness  | Root cause (field name mismatch) fixed              |
| Coherence    | Frontend fields aligned with backend contract       |

## 1. 完整性
7 项任务全部完成。

## 2. 正确性
### Bug：模板保存报 TASK_TEMPLATE_VALIDATION_FAILED: name is required
- **根因**：前端发送 `title`/`basePoints`，后端要求 `name`/`difficulties`。
- **修复**：`handleSave` 发送 `name` + 构造 `difficulties` 数组；接口和渲染全部对齐后端返回的 `name`/`difficulties[].rewardPoints`。

## 3. 一致性
- `TaskTemplate` 接口完全匹配后端 `TaskTemplateService.queryTemplates` 返回结构。
- 编辑时携带 `version` 字段（后端乐观锁必需）。

## 4. 测试证据
- `npx tsc -b`：0 errors。
- `npm test -- --run`：14 failed / 65 passed（与 baseline 一致）。

**Ready for archive.**
