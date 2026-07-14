# Comet Verify Report: `task-template-type-completion`

**Date**: 2026-07-14
**Verify Mode**: `full`
**Schema**: `spec-driven`
**Change Status**: `all_done` (21/21 tasks)

---

## Summary Scorecard

| Dimension | Score | Notes |
|---|---|---|
| **Completeness** | 🟡 85% | 21/21 任务已勾选；数据库、后端、前端均已实现；但**前后端错误码命名不一致**导致 4/6 个新增错误码运行时无法正确展示 |
| **Correctness** | 🔴 60% | 后端错误码字符串与 spec/前端不匹配（CRITICAL）；分配 API 响应缺少新增快照字段（WARNING） |
| **Coherence** | 🟢 95% | 实现与 design.md、Design Doc 高度对齐；仅错误码命名存在分歧 |

---

## CRITICAL Issues

### C1: 前后端错误码字符串命名不一致（4/6 错误码运行时断裂）

**影响**: 当后端返回 `TASK_TEMPLATE_LIMITED_NOT_STARTED` 等错误码时，前端 `getErrorMessage()` 无法匹配对应中文提示，降级为"未知错误"。

**详细对照**:

| Delta Spec / 前端 ErrorCodes | Backend ErrorCode.java 实际值 | 状态 |
|---|---|---|
| `TASK_TEMPLATE_TYPE_IMMUTABLE` | `"TASK_TEMPLATE_TYPE_IMMUTABLE"` | ✅ 一致 |
| `TASK_TEMPLATE_TYPE_CONFIG_MISMATCH` | 不存在于后端枚举 (1) | ⚠️ 仅前端防御 |
| `TASK_LIMITED_NOT_STARTED` | `"TASK_TEMPLATE_LIMITED_NOT_STARTED"` | ❌ **不一致** |
| `TASK_LIMITED_EXPIRED` | `"TASK_TEMPLATE_LIMITED_EXPIRED"` | ❌ **不一致** |
| `TASK_REPEAT_NOT_TRIGGER_DAY` | `"TASK_ASSIGNMENT_REPEAT_NOT_TRIGGER_DAY"` | ❌ **不一致** |
| `TASK_STANDING_LIMIT_REACHED` | `"TASK_ASSIGNMENT_STANDING_LIMIT_REACHED"` | ❌ **不一致** |

(1) `TASK_TEMPLATE_TYPE_CONFIG_MISMATCH` 在 Design Doc §7 中标记为内部码，统一映射为 `TASK_TEMPLATE_VALIDATION_FAILED`，因此后端无独立枚举值属于设计预期。

**证据**:
- 后端 `ErrorCode.java:60-61,78-79`：定义 `TASK_TEMPLATE_LIMITED_NOT_STARTED`、`TASK_TEMPLATE_LIMITED_EXPIRED`、`TASK_ASSIGNMENT_STANDING_LIMIT_REACHED`、`TASK_ASSIGNMENT_REPEAT_NOT_TRIGGER_DAY`
- 后端 `TaskReviewService.java:178,182,191`：实际抛出这些错误码
- 前端 `types.ts:40-47`：定义为 `TASK_LIMITED_NOT_STARTED`、`TASK_LIMITED_EXPIRED`、`TASK_STANDING_LIMIT_REACHED`、`TASK_REPEAT_NOT_TRIGGER_DAY`
- 前端 `errors.ts:45-52`：中文提示以相同 key 映射
- 前端 `getErrorMessage()` (errors.ts:165)：`ERROR_MESSAGES[errorCode]` 查找，key 不匹配时返回 `UNKNOWN_ERROR_MESSAGE`

**根因**: 后端 ErrorCode 枚举（定义于 `core-features` change）使用 `TASK_TEMPLATE_` / `TASK_ASSIGNMENT_` 前缀命名，而 spec（delta 与 main）使用简短命名。本次 change 的前端任务 3.1/3.2 按 spec 命名实现了前端映射，但未与后端实际值对齐。

**修复建议**:
- **方案 A（推荐）**: 修改后端 `ErrorCode.java` 枚举值字符串，使其与 spec 一致（修改 `TASK_TEMPLATE_LIMITED_NOT_STARTED` → `TASK_LIMITED_NOT_STARTED` 等 4 个枚举的 `code` 参数）
- **方案 B**: 修改前端 `types.ts` 和 `errors.ts` 的错误码常量字符串，使其与后端一致（但会导致与 OpenSpec 主 spec 不一致）

---

## WARNING Issues

### W1: `enrichAssignment()` 未暴露新增快照字段

**影响**: 前端查询分配详情时无法获取 `snapshotTemplateTaskType` 和 `snapshotTemplateTypeConfig`，无法满足 spec Scenario "查询既有分配详情"中展示 `snapshot_template_task_type` 的要求。

**证据**:
- `TaskAssignmentService.java:701-711`：`enrichAssignment()` 方法包含 6 个旧快照字段（name、description、category、icon、difficultyName、difficultyReward），但缺少 `snapshotTemplateTaskType` 和 `snapshotTemplateTypeConfig`
- `TaskAssignment` 实体 (TaskAssignment.java:78-82)：正确包含这两个新字段
- `createAssignmentEntity` (TaskAssignmentService.java:338-339)：正确写入这两个新字段

**修复建议**: 在 `enrichAssignment()` 添加：
```java
item.put("snapshotTemplateTaskType", assignment.getSnapshotTemplateTaskType());
item.put("snapshotTemplateTypeConfig", assignment.getSnapshotTemplateTypeConfig());
```

### W2: V12 迁移脚本 `COMMENT ON COLUMN` MySQL 兼容性

**影响**: `V12__add_task_type_snapshot_columns.sql:14-15` 使用 `COMMENT ON COLUMN` 语法，这是 PostgreSQL/Oracle 标准语法。MySQL 8.0 **不支持** `COMMENT ON COLUMN`，若在 MySQL 生产环境运行 Flyway 迁移将失败。

**证据**:
- `V12__add_task_type_snapshot_columns.sql:14-15`：`COMMENT ON COLUMN task_assignment.snapshot_template_task_type IS '快照：...'`
- Design Doc §2 指定使用 MySQL 8.0 作为持久化数据库
- MySQL 8.0 文档明确不支持 `COMMENT ON COLUMN` 语法

**修复建议**: 删除 `COMMENT ON COLUMN` 语句（仅注释用途，不影响功能），或改用 MySQL 兼容的 `ALTER TABLE ... MODIFY COLUMN ... COMMENT '...'` 语法。

### W3: `TASK_TEMPLATE_TYPE_CONFIG_MISMATCH` 仅前端定义

**影响**: 低（符合设计预期）。前端定义了此错误码但后端不返回独立错误码。Design Doc §7 说明此错误码"内部校验异常（外部统一为 `TASK_TEMPLATE_VALIDATION_FAILED`）"。

**证据**:
- 前端 `types.ts:36`：定义 `TASK_TEMPLATE_TYPE_CONFIG_MISMATCH`
- 前端 `errors.ts:41`：中文提示"任务类型配置不匹配"
- 后端 `ErrorCode.java`：无此枚举值
- 后端 `validateTypeConfig()` 方法 (TaskTemplateService.java:577-612) 中所有校验失败统一返回 `TASK_TEMPLATE_VALIDATION_FAILED`

**建议**: 可接受现状。若需要更细粒度的错误提示，应在后端增加该错误码并在相应校验场景使用。

---

## SUGGESTIONS

### S1: 前端错误码测试仅验证"非空"，未验证取值范围

**证据**: `client.test.ts:353-358` 中 `verify()` 函数只验证 `getErrorMessage(code)` 返回非空字符串，无法检测"前端 key 与后端实际值不同"这类运行时缺陷。

**建议**: 增加对关键错误码的**精确消息内容匹配**测试，而非仅验证非空。例如：
```typescript
expect(getErrorMessage('TASK_LIMITED_NOT_STARTED')).toBe('该任务尚未到达开始日期');
```

### S2: 缺失 `TaskTemplate` 查询时 taskType/typeConfig 在 response 中的可见性验证

**证据**: `TaskTemplateService.queryTemplates()` (line 513-514) 正确将 `taskType` 和 `typeConfig` 纳入响应，但没有专门针对此字段的响应结构测试。

**建议**: 增加集成测试验证 `GET /api/task-templates` 返回的每个模板对象包含 `taskType` 和 `typeConfig` 字段，且值正确。

---

## Requirement-by-Requirement Verification

### ADDED Requirements

| Requirement | 场景覆盖 (delta spec) | 实现证据 | 判定 |
|---|---|---|---|
| **前端模板管理支持任务类型选择与配置** | | | |
| → Scenario: 创建限时模板 | ✅ 测试覆盖 | `TaskTypeConfigForms.tsx:371-372` LimitedConfigForm; `client.test.ts:396-398` | ✅ PASS |
| → Scenario: 创建重复模板 | ✅ 测试覆盖 | `TaskTypeConfigForms.tsx:375-376` RepeatConfigForm; `client.test.ts:401-402` | ✅ PASS |
| → Scenario: 创建常驻模板并设置无限次 | ✅ 测试覆盖 | `TaskTypeConfigForms.tsx:379-380` StandingConfigForm; `client.test.ts:405-406` | ✅ PASS |
| **模板列表接口支持按任务类型筛选** | | | |
| → Scenario: 按单值筛选 | ✅ 测试覆盖 | `TaskTemplateController.java:83,100`; `TaskTemplateService.java:468-486`; `TaskTemplateServiceTest.java:582-593` | ✅ PASS |
| → Scenario: 按多值筛选 | ✅ 测试覆盖 | `TaskTemplateServiceTest.java:596-607` | ✅ PASS |
| → Scenario: 传入未知任务类型 | ✅ 测试覆盖 | `TaskTemplateServiceTest.java:610-627` | ✅ PASS |
| **后端完整校验任务类型与类型配置** | | | |
| → Scenario: 缺失 taskType | ✅ 测试覆盖 | `TaskTemplateService.java:578-580`; `TaskTemplateServiceTest.java:648-656` | ✅ PASS |
| → Scenario: typeConfig 与 taskType 不匹配 | ✅ 测试覆盖 | `TaskTemplateService.java:606-612`; `TaskTemplateServiceTest.java:715-724` | ✅ PASS |
| → Scenario: 更新时修改 taskType | ✅ 测试覆盖 | `TaskTemplateService.java:217-223`; `TaskTemplateServiceTest.java:482-494` | ✅ PASS |
| **任务分配创建时快照任务类型与类型配置** | | | |
| → Scenario: 新分配携带快照 | ✅ 测试覆盖 | `TaskAssignmentService.java:338-339`; DB migration V12 | ✅ PASS |
| → Scenario: 模板修改不影响既有分配快照 | ⚠️ 部分覆盖 | `TaskAssignmentService.java:338-339` 写入; V12 迁移存在; 但 `enrichAssignment` 未暴露字段 (见 W1) | ⚠️ WARNING |
| **新增错误码同步到前端** | | | |
| → Scenario: 错误码常量完整 | ✅ 常量已添加 | `types.ts:35-47` 6 个错误码全部定义 | ⚠️ 见 CRITICAL C1 |
| → Scenario: 中文提示完整 | ⚠️ 部分通过 | `errors.ts:40-52` 6 个中文提示全部定义; 但 4 个 key 与后端不匹配 (见 C1) | ⚠️ 见 CRITICAL C1 |

### MODIFIED Requirements (main spec confirmation)

| Requirement | 确认项 | 状态 |
|---|---|---|
| **创建任务模板与字段验证** | `validateTypeConfig()` 在创建时校验 taskType/typeConfig | ✅ 已实现 (TaskTemplateService.java:98,577-612) |
| **分页筛选模板列表** | `taskType` 筛选参数 + 未知值返回 `TASK_TEMPLATE_INVALID_QUERY` | ✅ 已实现 + 测试覆盖 |
| **更新模板仅影响未来分配** | `taskType` 不可改; 快照字段保护 | ✅ 已实现 + 测试覆盖 (TaskTemplateService.java:217-223) |

---

## Design Doc 一致性检查

| Design Doc 项 | 实现匹配 | 状态 |
|---|---|---|
| §3.2 迁移：新增 `snapshot_template_task_type` + `snapshot_template_type_config` (nullable) | V12 migration + TaskAssignment entity | ✅ |
| §4.1 `validateTypeConfig` 集中校验 | `TaskTemplateService.java:577-612` 实现完整 | ✅ |
| §4.2 `@RequestParam String taskType` | `TaskTemplateController.java:83,100` | ✅ |
| §4.3 快照写入 | `TaskAssignmentService.java:338-339` | ✅ |
| §5.1 `TaskTemplate` 类型扩展 | `index.tsx:88-89` | ✅ |
| §5.2 错误码同步 | `types.ts:35-47` + `errors.ts:40-52` | ⚠️ C1 不一致 |
| §5.3 动态表单 | `TaskTypeConfigForms.tsx:363-381` 按 taskType 渲染 | ✅ |
| §5.4 列表筛选 | `TaskTypeFilter.tsx` + `index.tsx:609` | ✅ |
| §7 错误码表 | HTTP 状态映射在 `GlobalExceptionHandler.java:134` | ✅ |
| §8 测试策略 | 单元+集成测试覆盖全部后端校验和前端表单 | ⚠️ 前端测试覆盖浅 (见 S1) |

---

## Task Completion Audit

全部 21 个任务均标记为 `[x] done`。详细检查：

| 任务 | 证据 | 状态 |
|---|---|---|
| 1.1 V12 migration | `V12__add_task_type_snapshot_columns.sql` 存在 | ✅ (见 W2) |
| 1.2 TaskAssignment 实体 | `TaskAssignment.java:78-82` | ✅ |
| 2.1 taskType/typeConfig 校验 | `TaskTemplateService.java:577-612` + 测试 | ✅ |
| 2.2 子字段校验 | `validateLimitedConfig/RepeatConfig/StandingConfig` + 测试 | ✅ |
| 2.3 taskType 列表筛选 | `TaskTemplateController.java:83` + `TaskTemplateService.java:468-486` + 测试 | ✅ |
| 2.4 taskType 不可改 | `TaskTemplateService.java:217-223` + 测试 | ✅ |
| 2.5 分配快照写入 | `TaskAssignmentService.java:338-339` | ✅ |
| 3.1 6 个错误码常量 | `types.ts:35-47` | ✅ |
| 3.2 6 个中文提示 | `errors.ts:40-52` | ✅ |
| 3.3 TaskTemplate 类型扩展 | `index.tsx:88-89` | ✅ |
| 4.1 任务类型选择器 | `TaskTypeConfigForms.tsx:363-368` | ✅ |
| 4.2 LIMITED 配置表单 | `LimitedConfigForm` | ✅ |
| 4.3 REPEAT 配置表单 | `RepeatConfigForm` | ✅ |
| 4.4 STANDING 配置表单 | `StandingConfigForm` | ✅ |
| 4.5 列表筛选器 | `TaskTypeFilter.tsx` | ✅ |
| 5.1 后端校验测试 | `TaskTemplateServiceTest.java:629+` 全面覆盖 | ✅ |
| 5.2 taskType 筛选集成测试 | `TaskTemplateServiceTest.java:568-627` | ✅ |
| 5.3 分配快照集成测试 | `TaskAssignmentServiceTest` | ✅ |
| 5.4 前端测试 | `client.test.ts:383-407` + `TaskTypeConfigForms.test.tsx` | ⚠️ 覆盖率浅 |
| 5.5 全量回归 | 标记 done | ✅ |
| 5.6 mvn test & npm test | 标记 done | ✅ |

---

## Final Assessment

**🔴 NOT Ready for archive**

**原因**: 存在 1 个 CRITICAL 问题（C1：前后端错误码命名不一致），导致 4/6 个新增错误码在运行时无法正确展示中文提示。

**修复路径**:
1. **修复 C1**（CRITICAL）：将后端 `ErrorCode.java` 中 4 个错误码的 `code` 字符串改为与 spec 一致的简短命名（或前端对齐后端，并更新 spec）
2. **修复 W1**（WARNING）：在 `enrichAssignment()` 中添加 `snapshotTemplateTaskType` 和 `snapshotTemplateTypeConfig` 字段
3. **修复 W2**（WARNING）：删除 V12 迁移中不兼容 MySQL 的 `COMMENT ON COLUMN` 语句
4. **增强 S1**（SUGGESTION）：为前端错误码测试增加精确消息内容匹配

**修复后需重新验证项**: C1 + W1 + W2
