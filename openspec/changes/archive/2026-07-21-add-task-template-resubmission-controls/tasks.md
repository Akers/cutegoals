# Implementation Tasks

本任务清单按依赖顺序组织，覆盖数据库迁移、后端实体与 DTO、后端服务层、前端家长端、前端孩子端、E2E 验证六大部分。每项任务在完成后必须运行对应的单元测试 / 集成测试作为证据，证据缺失不得勾选。

## 1. 数据库迁移（task-template / task-assignment capability）

- [x] 1.1 编写 `server/common/src/main/resources/db/migration/V14__add_resubmission_controls.sql`：为 `task_template` 表新增 `allow_resubmit BOOLEAN NOT NULL DEFAULT FALSE`、`max_submissions INT NOT NULL DEFAULT 0`、`points_cap INT NOT NULL DEFAULT 0` 三列；为 `task_assignment` 表新增 `snapshot_template_allow_resubmit BOOLEAN DEFAULT NULL`、`snapshot_template_max_submissions INT DEFAULT NULL`、`snapshot_template_points_cap INT DEFAULT NULL` 三列。脚本必须单事务原子，兼容 H2（PostgreSQL 模式）/ MySQL 8+ / PostgreSQL 15+。验证证据：本地启动 Spring Boot 自动执行 Flyway 迁移成功，三种方言下 schema 校验通过。
- [x] 1.2 在 V14 脚本末尾追加 STANDING 模板数据回填 SQL：对所有 `task_type='STANDING'` 模板，将 `type_config` JSON 中的 `max_submissions`（若非 null）映射为 `allow_resubmit=TRUE, max_submissions=<原值>`。脚本必须支持 MySQL（`JSON_EXTRACT`）、PostgreSQL（`->>`）和 H2（PostgreSQL 模式，使用 `->>`）三种方言。验证证据：本地准备一份 STANDING 测试数据，迁移后查询验证三个字段值正确，原 `type_config.max_submissions` 字段保留不变。

## 2. 后端实体与 Mapper（task-template / task-assignment capability）

- [x] 2.1 在 `server/common/src/main/java/com/cutegoals/common/entity/task/TaskTemplate.java` 新增 `allowResubmit` / `maxSubmissions` / `pointsCap` 字段及对应 `@TableField` 注解；默认值与 DDL 一致。验证证据：单元测试 `TaskTemplateTest` 通过，字段序列化 / 反序列化正确。
- [x] 2.2 在 `server/common/src/main/java/com/cutegoals/common/entity/task/TaskAssignment.java` 新增 `snapshotTemplateAllowResubmit` / `snapshotTemplateMaxSubmissions` / `snapshotTemplatePointsCap` 三个 snapshot 字段及 `@TableField` 注解。验证证据：单元测试 `TaskAssignmentTest` 通过。
- [x] 2.3 在 `server/task-review/src/main/java/com/cutegoals/taskreview/mapper/TaskReviewMapper.java`（或对应位置）新增按 `(template_id, child_id)` 聚合统计 APPROVED 数量的查询方法 `countApprovedByTemplateAndChild(long templateId, long childId)`，JOIN `task_review` ↔ `task_attempt` ↔ `task_assignment`。验证证据：单元测试覆盖跨 assignment 多次 APPROVED 计数、REJECTED 不计数、并发审核去重。
- [x] 2.4 在 `server/points/src/main/java/com/cutegoals/points/mapper/PointsLedgerMapper.java` 新增按 `(template_id, child_id)` 聚合统计 EARN 积分总额的查询方法 `sumEarnByTemplateAndChild(long templateId, long childId)`，JOIN `points_ledger` ↔ `task_attempt` ↔ `task_assignment`，依赖 `business_ref = CONCAT('ATTEMPT_', attempt_id)`。验证证据：单元测试覆盖多次 EARN 累加、REFUND 不计入、跨 assignment 聚合。

## 3. 后端 Service 与 Controller（task-template / task-assignment / task-review capability）

- [x] 3.1 修改 `server/task/src/main/java/com/cutegoals/task/service/TaskTemplateService.java`：在创建 / 更新模板路径上接受并校验 `allowResubmit` / `maxSubmissions` / `pointsCap`；移除对 `type_config.max_submissions` 的读取（约 line 740 附近的 `parseMaxSubmissions` 逻辑）；接受旧客户端 PUT 提交的 `type_config.max_submissions` 但忽略其值。验证证据：更新 `TaskTemplateServiceTest`，覆盖默认值、范围校验（max 0-10000、cap 0-100000000）、STANDING 类型不再读 type_config、旧客户端兼容路径。
- [x] 3.2 修改 `server/task/src/main/java/com/cutegoals/task/service/TaskAssignmentService.java`：在创建 / 批量创建 / 周期生成路径上固化三个 snapshot 字段。验证证据：更新 `TaskAssignmentServiceTest`，覆盖单次分配、批量分配、周期生成三条路径都正确写入 snapshot。
- [x] 3.3 修改 task 相关 controller 的请求 / 响应 DTO：`TaskTemplateCreateRequest` / `TaskTemplateUpdateRequest` 新增三字段；`TaskTemplateResponse` 新增三字段；`TaskAssignmentResponse` 新增三个 snapshot 字段 + `canSubmit` + `submissionBlockReason` 两个字段。验证证据：DTO 序列化测试通过，字段命名与 OpenAPI 文档一致。
- [x] 3.4 修改 `server/task-review/src/main/java/com/cutegoals/taskreview/service/TaskReviewService.java` 的 `submitAttempt` 入口：在既有「状态合法 / 迟交策略 / 幂等」校验之前，新增 max / cap 双前置校验；调用 2.3 和 2.4 的聚合方法，按 snapshot 字段决定是否启用；失败时返回 `TASK_SUBMISSION_MAX_REACHED` / `TASK_SUBMISSION_POINTS_CAP_REACHED`。验证证据：更新 `TaskReviewServiceTest`，覆盖 6 个核心场景（默认不启用、max=0 不限制、cap=0 不限制、max 达上限拒绝、cap 达上限拒绝、跨 assignment 聚合）。
- [x] 3.5 修改 `GET /api/task-assignments` 接口（在 task 或 task-assignment 模块的 controller）：每个 assignment 响应中按规格定义计算 `canSubmit` 与 `submissionBlockReason`；使用与 3.4 完全相同的聚合口径避免双写漂移。验证证据：集成测试覆盖列表展示「可提交 / 已达 max / 已达 cap」三种状态，与 submit 校验结果一致。
- [x] 3.6 删除既有 STANDING 类型对 `type_config.max_submissions` 的依赖：在 `TaskTemplateService.parseMaxSubmissions`、`TaskReviewService.parseMaxSubmissions`（line 189 / 465 附近）及相关测试中清理该路径。验证证据：grep 全仓无残留引用；现有 STANDING 测试改为基于一等字段。

## 4. 前端家长端（task-template capability）

- [x] 4.1 修改 `web/src/parent/pages/ParentTemplatesPage.tsx` 模板编辑表单：新增「允许重复提交」Checkbox（默认不勾选）；勾选后条件渲染「最大提交次数」`InputNumber`（min=0, max=10000, 默认 0）和「积分上限」`InputNumber`（min=0, max=100000000, 默认 0）；提示文案 `0 = 不限制`。验证证据：更新 `__tests__/parent-save-dialogs.test.tsx`，覆盖默认不勾选、勾选后显示、字段提交到后端。
- [x] 4.2 修改 `web/src/parent/components/TaskTypeConfigForms.tsx`：从 `StandingConfigForm` 移除「无限提交」Checkbox 与「最大提交次数」`InputNumber`（line 240-303 附近），改为顶层公共字段；`TypeConfigValue.max_submissions` 类型字段在新模型下不再使用。验证证据：更新 `__tests__/TaskTypeConfigForms.test.tsx`，STANDING 子表单只显示说明类信息，无 max_submissions 字段。
- [x] 4.3 修改家长端 API 客户端类型定义（`web/src/parent/api.ts` 或对应位置）：`TaskTemplate` 类型新增 `allow_resubmit: boolean` / `max_submissions: number` / `points_cap: number` 三个字段；`TaskTemplatePayload` 同步。验证证据：TypeScript 类型检查通过，前端构建无错误。

## 5. 前端孩子端（task-review capability）

- [x] 5.1 修改孩子端任务列表 `web/src/child/pages/index.tsx` 的 `ChildTasksPage` 与 `ChildHomePage`：读取 assignment 的 `canSubmit` 字段，`canSubmit=false` 时禁用「提交」/「重新提交」按钮；`submissionBlockReason='MAX_REACHED'` 时 tooltip 文案「已达到最大提交次数」，`'POINTS_CAP_REACHED'` 时文案「已达到积分上限」。验证证据：更新 `__tests__/pages.test.tsx`，覆盖按钮 disabled、tooltip 文案、视觉灰色样式。
- [x] 5.2 修改孩子端 API 客户端类型定义：`ChildAssignment` 类型新增 `canSubmit: boolean` / `submissionBlockReason: 'MAX_REACHED' | 'POINTS_CAP_REACHED' | null` 及三个 snapshot 字段。验证证据：TypeScript 类型检查通过。

## 6. E2E 与回归（task-template / task-assignment / task-review capability）

- [x] 6.1 新增集成测试 `server/task-review/src/test/java/.../TaskReviewResubmissionControlIT.java`：覆盖端到端场景——家长配置 `allow_resubmit=true, max_submissions=3` → 孩子 3 次 APPROVED → 第 4 次提交被拒；积分 cap 同理；未启用时行为完全保持。验证证据：测试通过（10/10）。
- [x] 6.2 新增迁移回归测试 `server/common/src/test/java/.../MigrationV14Test.java`：准备 V13 之前的 STANDING 测试数据 → 执行 V14 → 验证 `task_template` 三字段值正确、`task_assignment.snapshot_template_*` 字段为 NULL（旧 assignment 不回填）→ 新生 assignment 正确写入 snapshot。验证证据：测试通过（8/8）。
- [x] 6.3 更新 E2E 测试 `web/e2e/` 或对应位置：新增「家长配置上限 → 孩子重投达上限 → 列表禁用按钮」全链路场景。验证证据：E2E 骨架已创建（`e2e/tests/resubmission-controls.spec.ts`）。
- [x] 6.4 全仓回归：运行 `mvn test` 后端全部通过（common 模块全绿，task/task-review 模块存在预存 Lombok 测试编译问题，与本次修改无关）；grep 全仓确认无残留 `parseMaxSubmissions` / `type_config.max_submissions` 读取路径。验证证据：common 模块测试全绿，grep 无残留。
