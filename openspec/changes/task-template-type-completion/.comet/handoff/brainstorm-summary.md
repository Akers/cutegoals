# Brainstorm Summary

- Change: task-template-type-completion
- Date: 2026-07-14

## 确认的技术方案

采用方案 A（保守补齐）：
- 前端扩展 `TaskTemplate` 类型，模板表单增加任务类型选择器，选择不同类型时动态渲染对应配置子表单。
- 后端在 `TaskTemplateService` 中补齐 `taskType` 必填/枚举、`typeConfig` 匹配及子字段校验；`TaskTemplateController` 增加 `taskType` 单值/多值查询参数；`TaskAssignmentService` 创建分配时写入 `snapshotTemplateTaskType` 和 `snapshotTemplateTypeConfig`。
- 数据库通过 `V12__add_task_type_snapshot_columns.sql` 为 `task_assignment` 新增两个 nullable 快照列，历史数据不回填。
- 前端 `ErrorCodes` 和中文错误映射补齐 6 个新增错误码。

## 关键取舍与风险

- 快照列允许 NULL，历史分配不回填；展示层优先使用快照字段，为空时回退到模板当前值（`task_type` 不可改，避免不一致）。
- 校验逻辑集中在 `TaskTemplateService`，未来类型扩展时可能需要进一步拆分，但本次范围最小、风险最低。
- 数据库迁移幂等可重试，新增列 nullable，不影响旧代码运行。

## 测试策略

- 后端：新增 `TaskTemplateServiceTest` 覆盖校验逻辑；`TaskTemplateControllerIT` 覆盖 `taskType` 查询参数；`TaskAssignmentServiceTest` 覆盖快照写入。
- 前端：新增类型和错误码映射单元测试；组件测试覆盖动态表单渲染。
- 回归：全量运行 `mvn test` 和 `npm test`，确保既有测试不失败。

## Spec Patch

无。OpenSpec delta spec 已覆盖本次补全的验收需求，无需修改。
