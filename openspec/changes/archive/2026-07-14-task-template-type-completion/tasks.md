## 1. 数据库迁移

- [x] 1.1 [task-template] 创建 `V12__add_task_type_snapshot_columns.sql`，为 `task_assignment` 表添加 `snapshot_template_task_type` 和 `snapshot_template_type_config` 列（均允许 NULL）；验证：Flyway 在 H2 和 MySQL 上可重复迁移。
- [x] 1.2 [task-template] 更新 `TaskAssignment` 实体，添加 `snapshotTemplateTaskType` 和 `snapshotTemplateTypeConfig` 字段及 MyBatis-Plus 映射；验证：实体字段与数据库列映射测试通过。

## 2. 后端校验与接口补全

- [x] 2.1 [task-template] 在 `TaskTemplateService` 中补齐 `taskType` 必填、枚举值、`typeConfig` 必填及与 `taskType` 匹配校验，返回 `TASK_TEMPLATE_VALIDATION_FAILED` 并附带字段级错误；验证：缺失 taskType、未知枚举、typeConfig 不匹配单元测试通过。
- [x] 2.2 [task-template] 在 `TaskTemplateService` 中实现 `typeConfig` 子字段校验（LIMITED 的 start_date/end_date、REPEAT 的 frequency/trigger_day、STANDING 的 max_submissions）；验证：四种类型合法/非法输入测试通过。
- [x] 2.3 [task-template] 在 `TaskTemplateController.queryTemplates` 中增加 `taskType` 单值/多值查询参数，并在 `TaskTemplateService` 中实现数据库层筛选；验证：单值、多值、未知值返回 `TASK_TEMPLATE_INVALID_QUERY` 测试通过。
- [x] 2.4 [task-template] 在 `TaskTemplateService.updateTemplate` 中实现 `taskType` 不可改性：请求中 `taskType` 与既有值不一致时返回 `TASK_TEMPLATE_TYPE_IMMUTABLE` 且不增加版本；验证：修改与不变两种情况测试通过。
- [x] 2.5 [task-assignment] 在 `TaskAssignmentService.createAssignmentEntity` 中创建分配时写入 `snapshotTemplateTaskType` 和 `snapshotTemplateTypeConfig`；验证：新分配快照字段非空，模板后续修改不影响既有快照。

## 3. 前端类型与错误码

- [x] 3.1 [web-app] 在 `web/src/shared/api/types.ts` 的 `ErrorCodes` 中补充 6 个新增错误码：`TASK_TEMPLATE_TYPE_IMMUTABLE`、`TASK_TEMPLATE_TYPE_CONFIG_MISMATCH`、`TASK_LIMITED_NOT_STARTED`、`TASK_LIMITED_EXPIRED`、`TASK_REPEAT_NOT_TRIGGER_DAY`、`TASK_STANDING_LIMIT_REACHED`；验证：TypeScript 编译无缺失常量。
- [x] 3.2 [web-app] 在 `web/src/shared/api/errors.ts` 中为上述 6 个错误码补充中文提示；验证：UI 错误映射测试覆盖。
- [x] 3.3 [web-app] 在 `web/src/parent/pages/index.tsx` 中扩展 `TaskTemplate` 类型，增加 `taskType` 和 `typeConfig`；验证：类型检查通过。

## 4. 前端模板管理 UI

- [x] 4.1 [web-app] 在模板创建/编辑表单中增加任务类型选择器（LIMITED/REPEAT/STANDING）；验证：选择不同类型时动态渲染对应配置表单的 Playwright/组件测试通过。
- [x] 4.2 [web-app] 实现 LIMITED 配置表单：开始日期（可空）、结束日期；验证：提交结构符合后端 JSON 契约。
- [x] 4.3 [web-app] 实现 REPEAT 配置表单：频率选择（DAILY/WEEKLY/MONTHLY/YEARLY）和触发日配置（weekday / mode / month+day）；验证：四种频率提交结构正确。
- [x] 4.4 [web-app] 实现 STANDING 配置表单：最大提交次数输入和“无限”开关；验证：`max_submissions` 为 null 或正整数时提交结构正确。
- [x] 4.5 [web-app] 在模板列表页面增加任务类型筛选器（单选或多选），并调用 `GET /api/task-templates?taskType=...`；验证：筛选结果与后端响应一致。

## 5. 测试与回归

- [x] 5.1 [task-template] 为补齐的校验逻辑新增单元测试，覆盖 taskType 必填、枚举、typeConfig 匹配、子字段校验；验证：测试全部通过。
- [x] 5.2 [task-template] 为 `taskType` 列表筛选新增集成测试（Testcontainers）；验证：单值/多值/未知值场景通过。
- [x] 5.3 [task-assignment] 为分配快照新增集成测试；验证：创建分配后快照字段正确，模板修改不追溯。
- [x] 5.4 [web-app] 为前端任务类型表单和错误码映射新增单元/组件测试；验证：npm test 通过。
- [x] 5.5 [task-template] 全量回归后端 task-template / task-assignment / task-review 测试；验证：无既有测试失败。
- [x] 5.6 [task-template] 运行 `mvn test` 和 `npm run test`（如适用），确认 change 实施后的代码仓库整体健康；验证：构建与测试通过。
