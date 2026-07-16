# 修复任务清单

- [x] 1. 在 `web/src/parent/pages/index.tsx` 的模板列表中增加“已启用 / 已停用”状态标签。
- [x] 2. 在 `web/src/parent/pages/index.tsx` 的批量分配弹窗中新增难度选择字段。
- [x] 3. 将批量分配弹窗的孩子选择改为多选，并收集为数组。
- [x] 4. 将批量分配弹窗的“截止时间”改为“开始日期”和“结束日期”。
- [x] 5. 更新 `handleAssign` 请求体为后端批量分配契约格式（包含 `templateId`、`difficultyId`、`startDate`、`endDate`、`childIds`、`idempotencyKey`）。
- [x] 6. 增加提交前校验和错误提示。
- [x] 7. 运行前端格式化与类型检查（`npm run format` / `npm run lint` / `tsc --noEmit`）。
- [x] 8. 运行后端相关测试（`TaskAssignmentServiceTest`）和构建，确保无回归。
- [x] 9. 提交代码，并更新本任务清单。
- [x] 10. 修复 `setTemplateEnabled` 未持久化 `enabled` 字段到数据库的问题。
- [x] 11. 批量分配弹窗根据任务类型自动填入开始/结束日期，REPEAT 任务不显示结束日期。
- [x] 12. 诊断并修复批量分配 INTERNAL_ERROR（已在前端补齐日期校验、后端补齐 snapshot/typeConfig 空值保护 + JsonTypeHandler 修复，代码侧完成；需用户重新构建后复测验证）。
