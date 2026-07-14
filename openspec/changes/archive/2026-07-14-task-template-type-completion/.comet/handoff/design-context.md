# Comet Design Handoff

- Change: task-template-type-completion
- Phase: design
- Mode: compact
- Context hash: e9cd3a8121c5accf7ee1c89006276a18621c660d88b1fe1e94e93e10ddfb43bb

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/task-template-type-completion/proposal.md

- Source: openspec/changes/task-template-type-completion/proposal.md
- Lines: 1-30
- SHA256: 29a28e2393f5beed95b441181052d0998e1dc4161bd3a60fe5ed0d0bc3b81d88

```md
## Why

已归档的 `core-features` change 将任务模板 `task_type`（LIMITED/REPEAT/STANDING）及其配置 `type_config` 写入了 main spec，并在后端完成了基础字段存储。但当前代码中仍遗留若干属性的前端模型、接口、校验和错误码同步未实现，导致家长端无法完整配置任务类型，部分后端行为也与 main spec 不一致。本次 change 补齐这些实现遗漏，使产品行为与 `openspec/specs/task-template/spec.md` 保持一致。

## What Changes

- **前端模型与 UI**：家长端 `TaskTemplate` 类型增加 `taskType` 和 `typeConfig`，模板管理表单支持选择三类任务类型并配置对应字段（限时起止日期、重复周期与触发日、常驻最大提交次数）。
- **后端查询接口**：`GET /api/task-templates` 增加 `taskType` 单值/多值筛选参数，支持按任务类型过滤模板列表。
- **后端校验**：`TaskTemplateService` 创建/更新模板时，强制校验 `taskType` 必填、取值合法、`typeConfig` 与 `taskType` 匹配、子字段合法，并返回统一错误码。
- **分配快照**：`task_assignment` 表及实体增加 `snapshot_template_task_type` 和 `snapshot_template_type_config` 字段；创建分配时同步快照，确保模板后续修改不追溯既有分配。
- **错误码同步**：将 6 个新增错误码（`TASK_TEMPLATE_TYPE_IMMUTABLE`、`TASK_TEMPLATE_TYPE_CONFIG_MISMATCH`、`TASK_LIMITED_NOT_STARTED`、`TASK_LIMITED_EXPIRED`、`TASK_REPEAT_NOT_TRIGGER_DAY`、`TASK_STANDING_LIMIT_REACHED`）补充到前端 `ErrorCodes` 与中文错误映射。
- **回归验证**：补齐上述实现后，现有 task-template / task-assignment / task-review 测试继续通过，新增针对遗漏属性的单元与集成测试。

## Capabilities

### New Capabilities

<!-- 无新增能力；本次为已有能力实现补齐。 -->

### Modified Capabilities

<!-- main spec 中需求已完整，本次不修改需求定义。 -->
- `task-template`：本次 change 仅补齐其实现，不修改需求规格。

## Impact

- **后端**：`server/task`（模板服务、控制器）、`server/task-review`（错误码引用）、`server/common`（实体、数据库迁移）。
- **前端**：`web/src/shared/api/types.ts`、`web/src/shared/api/errors.ts`、`web/src/parent/pages/index.tsx`（模板管理 UI）。
- **数据库**：新增 `task_assignment.snapshot_template_task_type` 和 `snapshot_template_type_config` 列，并回填历史数据（nullable，不回填不影响既有分配展示）。
- **API**：`GET /api/task-templates` 新增可选查询参数 `taskType`，请求/响应体中的 `taskType`/`typeConfig` 字段完整生效。

```

## openspec/changes/task-template-type-completion/design.md

- Source: openspec/changes/task-template-type-completion/design.md
- Lines: 1-63
- SHA256: c4f93bfe5639c61f0c3d6e0330eedc001b5f6e07321d8fafb92007d4fe70a7a0

```md
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

```

## openspec/changes/task-template-type-completion/tasks.md

- Source: openspec/changes/task-template-type-completion/tasks.md
- Lines: 1-35
- SHA256: 7c02673f37bcfa0f1a69b116a51f9c8bb434073e19c9cd3c625bf6dd3afd39f3

```md
## 1. 数据库迁移

- [ ] 1.1 [task-template] 创建 `V12__add_task_type_snapshot_columns.sql`，为 `task_assignment` 表添加 `snapshot_template_task_type` 和 `snapshot_template_type_config` 列（均允许 NULL）；验证：Flyway 在 H2 和 MySQL 上可重复迁移。
- [ ] 1.2 [task-template] 更新 `TaskAssignment` 实体，添加 `snapshotTemplateTaskType` 和 `snapshotTemplateTypeConfig` 字段及 MyBatis-Plus 映射；验证：实体字段与数据库列映射测试通过。

## 2. 后端校验与接口补全

- [ ] 2.1 [task-template] 在 `TaskTemplateService` 中补齐 `taskType` 必填、枚举值、`typeConfig` 必填及与 `taskType` 匹配校验，返回 `TASK_TEMPLATE_VALIDATION_FAILED` 并附带字段级错误；验证：缺失 taskType、未知枚举、typeConfig 不匹配单元测试通过。
- [ ] 2.2 [task-template] 在 `TaskTemplateService` 中实现 `typeConfig` 子字段校验（LIMITED 的 start_date/end_date、REPEAT 的 frequency/trigger_day、STANDING 的 max_submissions）；验证：四种类型合法/非法输入测试通过。
- [ ] 2.3 [task-template] 在 `TaskTemplateController.queryTemplates` 中增加 `taskType` 单值/多值查询参数，并在 `TaskTemplateService` 中实现数据库层筛选；验证：单值、多值、未知值返回 `TASK_TEMPLATE_INVALID_QUERY` 测试通过。
- [ ] 2.4 [task-template] 在 `TaskTemplateService.updateTemplate` 中实现 `taskType` 不可改性：请求中 `taskType` 与既有值不一致时返回 `TASK_TEMPLATE_TYPE_IMMUTABLE` 且不增加版本；验证：修改与不变两种情况测试通过。
- [ ] 2.5 [task-assignment] 在 `TaskAssignmentService.createAssignmentEntity` 中创建分配时写入 `snapshotTemplateTaskType` 和 `snapshotTemplateTypeConfig`；验证：新分配快照字段非空，模板后续修改不影响既有快照。

## 3. 前端类型与错误码

- [ ] 3.1 [web-app] 在 `web/src/shared/api/types.ts` 的 `ErrorCodes` 中补充 6 个新增错误码：`TASK_TEMPLATE_TYPE_IMMUTABLE`、`TASK_TEMPLATE_TYPE_CONFIG_MISMATCH`、`TASK_LIMITED_NOT_STARTED`、`TASK_LIMITED_EXPIRED`、`TASK_REPEAT_NOT_TRIGGER_DAY`、`TASK_STANDING_LIMIT_REACHED`；验证：TypeScript 编译无缺失常量。
- [ ] 3.2 [web-app] 在 `web/src/shared/api/errors.ts` 中为上述 6 个错误码补充中文提示；验证：UI 错误映射测试覆盖。
- [ ] 3.3 [web-app] 在 `web/src/parent/pages/index.tsx` 中扩展 `TaskTemplate` 类型，增加 `taskType` 和 `typeConfig`；验证：类型检查通过。

## 4. 前端模板管理 UI

- [ ] 4.1 [web-app] 在模板创建/编辑表单中增加任务类型选择器（LIMITED/REPEAT/STANDING）；验证：选择不同类型时动态渲染对应配置表单的 Playwright/组件测试通过。
- [ ] 4.2 [web-app] 实现 LIMITED 配置表单：开始日期（可空）、结束日期；验证：提交结构符合后端 JSON 契约。
- [ ] 4.3 [web-app] 实现 REPEAT 配置表单：频率选择（DAILY/WEEKLY/MONTHLY/YEARLY）和触发日配置（weekday / mode / month+day）；验证：四种频率提交结构正确。
- [ ] 4.4 [web-app] 实现 STANDING 配置表单：最大提交次数输入和“无限”开关；验证：`max_submissions` 为 null 或正整数时提交结构正确。
- [ ] 4.5 [web-app] 在模板列表页面增加任务类型筛选器（单选或多选），并调用 `GET /api/task-templates?taskType=...`；验证：筛选结果与后端响应一致。

## 5. 测试与回归

- [ ] 5.1 [task-template] 为补齐的校验逻辑新增单元测试，覆盖 taskType 必填、枚举、typeConfig 匹配、子字段校验；验证：测试全部通过。
- [ ] 5.2 [task-template] 为 `taskType` 列表筛选新增集成测试（Testcontainers）；验证：单值/多值/未知值场景通过。
- [ ] 5.3 [task-assignment] 为分配快照新增集成测试；验证：创建分配后快照字段正确，模板修改不追溯。
- [ ] 5.4 [web-app] 为前端任务类型表单和错误码映射新增单元/组件测试；验证：npm test 通过。
- [ ] 5.5 [task-template] 全量回归后端 task-template / task-assignment / task-review 测试；验证：无既有测试失败。
- [ ] 5.6 [task-template] 运行 `mvn test` 和 `npm run test`（如适用），确认 change 实施后的代码仓库整体健康；验证：构建与测试通过。

```

## openspec/changes/task-template-type-completion/specs/task-template/spec.md

- Source: openspec/changes/task-template-type-completion/specs/task-template/spec.md
- Lines: 1-115
- SHA256: dc250cec4a9afbd085d3207d7292469d89e861eb79555f29b5cc5e9b1f983c11

[TRUNCATED]

```md
## ADDED Requirements

### Requirement: 前端模板管理支持任务类型选择与配置
家长端任务模板管理 UI MUST 在创建和编辑模板时展示任务类型选择器，支持 `LIMITED`、`REPEAT`、`STANDING` 三种选项。选择不同任务类型时，表单 MUST 动态显示对应的配置字段：`LIMITED` 显示开始日期（可空）和结束日期；`REPEAT` 显示重复周期（DAILY/WEEKLY/MONTHLY/YEARLY）和触发日配置；`STANDING` 显示最大提交次数（可设为无限或正整数）。前端提交前 MUST 按后端期望的 JSON 结构组装 `typeConfig`，不得以错误结构调用 API。

#### Scenario: 创建限时模板
- **WHEN** 家长在模板表单中选择“限时任务”并填写结束日期
- **THEN** 前端提交 `taskType=LIMITED` 与 `typeConfig={end_date:"2026-08-20"}` 到后端

#### Scenario: 创建重复模板
- **WHEN** 家长选择“重复任务”，设置频率为“每周”，并选择周三
- **THEN** 前端提交 `taskType=REPEAT` 与 `typeConfig={frequency:"WEEKLY",trigger_day:{weekday:3}}`

#### Scenario: 创建常驻模板并设置无限次
- **WHEN** 家长选择“常驻任务”并勾选“无限次提交”
- **THEN** 前端提交 `taskType=STANDING` 与 `typeConfig={max_submissions:null}`

### Requirement: 模板列表接口支持按任务类型筛选
后端 `GET /api/task-templates` 接口 MUST 支持 `taskType` 查询参数，允许传入单值或逗号分隔的多值。系统 MUST 返回当前家庭中 `task_type` 与查询值匹配的模板列表；传入未知任务类型值 MUST 返回错误码 `TASK_TEMPLATE_INVALID_QUERY`。

#### Scenario: 按单值筛选
- **WHEN** 家长请求 `GET /api/task-templates?taskType=REPEAT`
- **THEN** 系统仅返回任务类型为 REPEAT 的模板

#### Scenario: 按多值筛选
- **WHEN** 家长请求 `GET /api/task-templates?taskType=LIMITED,STANDING`
- **THEN** 系统返回 LIMITED 或 STANDING 类型的模板

#### Scenario: 传入未知任务类型
- **WHEN** 家长请求 `GET /api/task-templates?taskType=UNKNOWN`
- **THEN** 系统返回错误码 `TASK_TEMPLATE_INVALID_QUERY`

### Requirement: 后端完整校验任务类型与类型配置
后端在创建和更新模板时 MUST 校验 `taskType` 必填且取值必须为 `LIMITED`、`REPEAT`、`STANDING` 之一；`typeConfig` 必填且结构必须与 `taskType` 匹配。校验失败 MUST 返回错误码 `TASK_TEMPLATE_VALIDATION_FAILED` 并附带字段级错误。`taskType` 字段 MUST NOT 在更新时修改；尝试修改 MUST 返回 `TASK_TEMPLATE_TYPE_IMMUTABLE` 且不增加模板版本。

#### Scenario: 缺失 taskType
- **WHEN** 家长提交创建模板请求但未提供 `taskType`
- **THEN** 系统返回错误码 `TASK_TEMPLATE_VALIDATION_FAILED` 并提示 taskType 缺失

#### Scenario: typeConfig 与 taskType 不匹配
- **WHEN** 家长提交 `taskType=REPEAT` 但 `typeConfig` 缺少 `frequency`
- **THEN** 系统返回错误码 `TASK_TEMPLATE_VALIDATION_FAILED` 并提示 typeConfig 不合法

#### Scenario: 更新时修改 taskType
- **WHEN** 家长更新模板时将 `taskType` 从 LIMITED 改为 STANDING
- **THEN** 系统返回错误码 `TASK_TEMPLATE_TYPE_IMMUTABLE` 且版本不增加

### Requirement: 任务分配创建时快照任务类型与类型配置
创建任务分配时，系统 MUST 将当前模板的 `task_type` 和 `type_config` 写入 `task_assignment.snapshot_template_task_type` 和 `snapshot_template_type_config` 字段。模板后续修改 MUST NOT 影响既有分配中的快照字段。数据库表 MUST 支持这两个字段，并允许现有数据为 NULL（历史数据不回填）。

#### Scenario: 新分配携带快照
- **WHEN** 家长基于 `taskType=LIMITED` 的模板创建分配
- **THEN** 系统创建的 `task_assignment` 记录包含 `snapshot_template_task_type=LIMITED` 和对应 `snapshot_template_type_config`

#### Scenario: 模板修改不影响既有分配快照
- **WHEN** 家长修改模板名称后，查询既有分配详情
- **THEN** 系统展示的快照名称不变，且 `snapshot_template_task_type` 保持原值

### Requirement: 新增错误码同步到前端
后端已定义的 6 个错误码（`TASK_TEMPLATE_TYPE_IMMUTABLE`、`TASK_TEMPLATE_TYPE_CONFIG_MISMATCH`、`TASK_LIMITED_NOT_STARTED`、`TASK_LIMITED_EXPIRED`、`TASK_REPEAT_NOT_TRIGGER_DAY`、`TASK_STANDING_LIMIT_REACHED`）MUST 在前端 `ErrorCodes` 常量表和中文错误映射中同时存在。当后端返回这些错误码时，前端 MUST 展示对应中文提示，不得以“未知错误”兜底。

#### Scenario: 错误码常量完整
- **WHEN** 前端工程编译时
- **THEN** `ErrorCodes` 包含全部 6 个新增错误码

#### Scenario: 中文提示完整
- **WHEN** 后端返回 `TASK_STANDING_LIMIT_REACHED`
- **THEN** 前端展示中文提示“已达最大提交次数，不能再提交”

## MODIFIED Requirements

### Requirement: 创建任务模板与字段验证
家长 SHALL 能够创建任务模板。模板 MUST 包含去除首尾空白后长度为 1 至 100 个字符的名称、长度为 1 至 50 个字符的分类、至少一个启用的难度,以及取值为 `LIMITED`、`REPEAT` 或 `STANDING` 之一的任务类型 `task_type`。说明和图标 MUST 为可选字段,说明最长 2000 个字符,图标标识最长 500 个字符。任务类型配置 `type_config` MUST 与 `task_type` 匹配:`LIMITED` 必须包含 `end_date`,可选 `start_date`;`REPEAT` 必须包含 `frequency` 取值 `DAILY`、`WEEKLY`、`MONTHLY` 或 `YEARLY`,并按 `frequency` 携带合法的 `trigger_day`;`STANDING` 必须包含为 null 或 1 至 10000 正整数的 `max_submissions`。系统 MUST 拒绝空白名称、空白分类、超长字段、未知字段类型、不合法难度、未知任务类型、缺失 `type_config`、`type_config` 与 `task_type` 不匹配或子字段不合法,并以稳定错误码 `TASK_TEMPLATE_VALIDATION_FAILED` 返回逐字段错误;验证失败 MUST NOT 创建部分模板或部分难度。

#### Scenario: 使用完整字段创建限时模板
- **WHEN** 家长提交合法的名称、分类、说明、图标、`task_type=LIMITED` 与 `{start_date: "2026-07-20", end_date: "2026-08-20"}`,以及两个合法难度
- **THEN** 系统在一个原子操作中创建模板及其难度,返回模板标识和当前版本

#### Scenario: 使用最小字段创建常驻模板
- **WHEN** 家长提交合法名称、分类、一个合法难度、`task_type=STANDING` 与 `{max_submissions: null}`,且未提交说明或图标

```

Full source: openspec/changes/task-template-type-completion/specs/task-template/spec.md
