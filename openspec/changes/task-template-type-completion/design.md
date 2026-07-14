## Context

`core-features` 已归档并同步到 main spec，`openspec/specs/task-template/spec.md` 已完整定义任务类型 `task_type`（LIMITED/REPEAT/STANDING）与 `type_config` 的语义、校验、状态机和错误码。当前后端已落地数据库字段（`V10__add_task_type_and_type_config.sql`）、基础实体、`TaskTemplateService` 的部分逻辑和 `RepeatTaskScheduler`，但前端模型、错误码映射、API 查询参数、后端校验和分配快照仍不完整。本次 change 在这些已知遗漏点做补全，不引入新的业务语义。

## Goals / Non-Goals

**Goals:**
- 家长端模板管理 UI 支持选择任务类型并配置对应参数。
- `GET /api/task-templates` 支持按 `taskType` 单值/多值筛选。
- 后端对 `taskType` 和 `typeConfig` 做完整校验并返回稳定错误码。
- 任务分配创建时快照 `taskType` 和 `typeConfig`，保证既有分配不受模板后续修改影响。
- 6 个新增错误码同步到前端错误码表与中文提示映射。
- 通过单元与集成测试覆盖所有遗漏点。

**Non-Goals:**
- 不修改任务类型业务语义（LIMITED/REPEAT/STANDING 的行为规则保持不变）。
- 不调整积分、奖品、兑换、家庭或认证模块。
- 不重新设计数据库表结构，仅补充快照列。
- 不修改 main spec 或 archive 的 delta spec 中的需求定义。

## Decisions

**决策 1：直接扩展现有表，不回填历史快照**
- 新增 `task_assignment.snapshot_template_task_type` 和 `snapshot_template_type_config` 列，默认 NULL。
- 现有分配快照保持 NULL，表示创建时未记录任务类型；后续修改模板时这些字段仍为空，不会错误覆盖。
- 新分配创建时从模板读取并写入快照。
- 理由：避免对已有生产数据进行高风险回填；NULL 在展示层可优雅降级为模板当前值（模板不会被修改 task_type）。

**决策 2：前端使用 camelCase 字段名与后端保持一致**
- 后端请求/响应体已使用 `taskType` 和 `typeConfig`（JSON 字符串），前端类型沿用此命名。
- 数据库列使用下划线命名，实体字段已用 camelCase（MyBatis-Plus 映射）。
- 理由：与现有 API 命名一致，减少变更范围。

**决策 3：新增错误码通过常量表 + 映射表双同步**
- 后端 `ErrorCode` 枚举已包含新增错误码，前端 `web/src/shared/api/types.ts` 补齐 `ErrorCodes` 常量，前端 `web/src/shared/api/errors.ts` 补齐中文提示。
- 理由：保持前后端错误码一致性，避免 UI 展示未知错误码。

**决策 4：任务类型选择表单按类型动态渲染子配置**
- 选择 LIMITED 时显示开始日期和结束日期；选择 REPEAT 时显示 frequency 和 triggerDay；选择 STANDING 时显示 maxSubmissions（含无限开关）。
- 提交前前端做基础结构校验，最终校验由后端完成。
- 理由：减少不必要的前端复杂度，以服务端校验为事实源。

## Risks / Trade-offs

| 风险 | 影响 | 缓解 |
|---|---|---|
| 新增快照列后，既有分配在模板修改后展示不一致 | 中 | 展示层优先使用快照字段，快照为空时回退到模板当前值（task_type 不可改，因此不会不一致） |
| 前端任务类型表单结构错误导致后端校验失败 | 低 | 前端提供默认结构，后端返回明确字段错误 |
| taskType 筛选参数解析不一致 | 低 | 后端统一解析逗号分隔字符串或重复 query param，拒绝未知值返回 `TASK_TEMPLATE_INVALID_QUERY` |
| 数据库迁移在已有数据上执行失败 | 低 | 新增列允许 NULL，默认值不影响现有行；迁移脚本幂等可重试 |

## Migration Plan

1. 执行 Flyway 迁移 `V12__add_task_type_snapshot_columns.sql`，为 `task_assignment` 添加 `snapshot_template_task_type` 和 `snapshot_template_type_config`。
2. 部署后端更新（模板校验、API 查询、快照写入）。
3. 部署前端更新（模板管理 UI、错误码映射）。
4. 验证：运行 `TaskTemplateServiceTest`、`TaskAssignmentServiceTest` 和新增补全测试；启动应用后手动验证创建三种任务类型模板、筛选、分配和错误码提示。
5. 回滚：若出现问题，回滚应用版本；数据库列保持新增，不影响旧代码运行（nullable）。

## Open Questions

- 是否需要在前端对历史模板（无 taskType）做默认值转换？当前后端 `V10` 迁移默认值为 LIMITED，因此历史模板均有 taskType，无需特殊处理。
- `typeConfig` 在前端是否以 JSON 字符串还是结构化对象存储？本次保持与后端一致，使用 JSON 字符串，表单编辑时临时转换为对象。
