---
comet_change: task-template-type-completion
role: technical-design
canonical_spec: openspec
archived-with: 2026-07-14-task-template-type-completion
status: final
---

# CuteGoals 2.0 任务模板属性补全设计文档

## 1. 设计目标

本文档将 `openspec/changes/task-template-type-completion/` 中的 delta spec 转化为可实施的技术设计。本次 change 的核心目标是补齐已归档 `core-features` 中任务模板 `task_type` 属性的实现遗漏：前端模型/UI、后端校验与查询、分配快照、错误码同步。OpenSpec delta spec 与 `openspec/specs/task-template/spec.md` 是验收事实源；本文档只说明如何实现，不引入新的需求或范围变更。

## 2. 技术栈与架构

- **后端**：Spring Boot 3.2.x + Java 21 + Maven 多模块，MyBatis-Plus 3.5.x，MySQL 8.0
- **前端**：React 18 + TypeScript 5.x + Vite 5.x，Tailwind CSS
- **持久化**：Flyway 版本化迁移，JSON 字段存储 `type_config`
- **现有模块**：`server/task`（模板与分配）、`server/task-review`（提交与审核）、`server/common`（实体与错误码）、`web/src/parent`（家长端 UI）

## 3. 数据模型

### 3.1 `task_template` 表（已有字段）

| 字段 | 类型 | 说明 |
|---|---|---|
| `task_type` | VARCHAR(20) NOT NULL | LIMITED / REPEAT / STANDING |
| `type_config` | JSON | 类型配置，结构由 `taskType` 决定 |

### 3.2 `task_assignment` 表扩展

新增两列（nullable）：

```sql
ALTER TABLE task_assignment
    ADD COLUMN snapshot_template_task_type VARCHAR(20) DEFAULT NULL,
    ADD COLUMN snapshot_template_type_config JSON DEFAULT NULL;
```

对应 `TaskAssignment` 实体新增：

```java
@TableField("snapshot_template_task_type")
private String snapshotTemplateTaskType;

@TableField("snapshot_template_type_config")
private String snapshotTemplateTypeConfig;
```

**设计说明**：
- 历史分配不回填，保持 NULL；新分配创建时从模板读取写入。
- `task_type` 不可修改，因此 NULL 快照在展示时回退到模板当前值也不会产生不一致。
- 未来若模板 `type_config` 可修改，已有分配仍通过快照字段保护。

## 4. 后端实现设计

### 4.1 `TaskTemplateService` 校验增强

在 `createTemplate` 和 `updateTemplate` 中增加以下校验：

1. **taskType 必填**：缺失返回 `TASK_TEMPLATE_VALIDATION_FAILED`。
2. **taskType 枚举校验**：必须为 `LIMITED` / `REPEAT` / `STANDING` 之一，否则返回 `TASK_TEMPLATE_VALIDATION_FAILED`。
3. **typeConfig 必填**：缺失返回 `TASK_TEMPLATE_VALIDATION_FAILED`。
4. **typeConfig 与 taskType 匹配校验**：
   - `LIMITED`：必须包含 `end_date`，可选 `start_date`；`end_date >= start_date`。
   - `REPEAT`：必须包含 `frequency`（DAILY/WEEKLY/MONTHLY/YEARLY），并按 frequency 携带合法 `trigger_day`。
   - `STANDING`：必须包含 `max_submissions`（null 或 1~10000 正整数）。
5. **taskType 不可改**：更新时若请求体 `taskType` 与数据库值不一致，返回 `TASK_TEMPLATE_TYPE_IMMUTABLE`，不增加版本。

建议实现一个私有方法 `validateTypeConfig(String taskType, String typeConfig)` 集中处理，避免散落在 `createTemplate`/`updateTemplate` 中。

### 4.2 `TaskTemplateController` 列表筛选增强

`GET /api/task-templates` 增加可选参数 `taskType`：

```java
@RequestParam(required = false) String taskType
```

支持两种形式：
- 单值：`?taskType=REPEAT`
- 多值：`?taskType=LIMITED,STANDING`

服务层解析后使用 `IN` 条件查询；传入未知值时返回 `TASK_TEMPLATE_INVALID_QUERY`。

### 4.3 `TaskAssignmentService` 快照写入

在 `createAssignmentEntity` 方法中，创建 `TaskAssignment` 时从模板读取并写入：

```java
assignment.setSnapshotTemplateTaskType(template.getTaskType());
assignment.setSnapshotTemplateTypeConfig(template.getTypeConfig());
```

同时保留现有快照字段（模板名称、描述、分类、图标、难度名称、奖励积分）。

## 5. 前端实现设计

### 5.1 类型扩展

`web/src/parent/pages/index.tsx` 中的 `TaskTemplate` 接口扩展为：

```typescript
interface TaskTemplate {
  id: number;
  name: string;
  description: string;
  category: string;
  difficulties: Difficulty[];
  enabled: boolean;
  version: number;
  taskType: 'LIMITED' | 'REPEAT' | 'STANDING';
  typeConfig: string; // JSON string
}
```

### 5.2 错误码同步

`web/src/shared/api/types.ts` 补充：

```typescript
TASK_TEMPLATE_TYPE_IMMUTABLE: 'TASK_TEMPLATE_TYPE_IMMUTABLE',
TASK_TEMPLATE_TYPE_CONFIG_MISMATCH: 'TASK_TEMPLATE_TYPE_CONFIG_MISMATCH',
TASK_LIMITED_NOT_STARTED: 'TASK_LIMITED_NOT_STARTED',
TASK_LIMITED_EXPIRED: 'TASK_LIMITED_EXPIRED',
TASK_REPEAT_NOT_TRIGGER_DAY: 'TASK_REPEAT_NOT_TRIGGER_DAY',
TASK_STANDING_LIMIT_REACHED: 'TASK_STANDING_LIMIT_REACHED',
```

`web/src/shared/api/errors.ts` 补充对应中文提示。

### 5.3 模板表单动态渲染

模板创建/编辑表单增加任务类型下拉选择器。根据选择动态渲染：

- **LIMITED**：开始日期（date，可选）、结束日期（date，必填）
- **REPEAT**：频率选择（DAILY/WEEKLY/MONTHLY/YEARLY）+ 触发日配置
  - WEEKLY：周几选择（1-7）
  - MONTHLY：模式选择（FIRST_DAY/LAST_DAY/MID_MONTH）
  - YEARLY：月份（1-12）和日期（1-31）
- **STANDING**：最大提交次数（number，可空）+ “无限”开关

表单提交时将配置对象序列化为 JSON 字符串作为 `typeConfig`。

### 5.4 模板列表筛选

在模板列表页增加任务类型筛选器（多选），调用 `GET /api/task-templates?taskType=...`。

## 6. API 影响

| 接口 | 变更 |
|---|---|
| `POST /api/task-templates` | 请求体新增 `taskType`（必填）、`typeConfig`（必填，JSON 字符串） |
| `PUT /api/task-templates/{id}` | 允许修改 `typeConfig` 内字段；`taskType` 不可改 |
| `GET /api/task-templates` | 新增 `taskType` 查询参数（单值或逗号分隔多值） |
| `GET /api/task-templates/{id}` | 响应体包含 `taskType` 和 `typeConfig` |
| `POST /api/task-assignments` | 创建分配时写入快照字段 |

## 7. 错误码

| 错误码 | 场景 | HTTP 状态 |
|---|---|---|
| `TASK_TEMPLATE_TYPE_IMMUTABLE` | 更新模板时修改 `taskType` | 409 Conflict |
| `TASK_TEMPLATE_TYPE_CONFIG_MISMATCH` | 内部校验异常（外部统一为 `TASK_TEMPLATE_VALIDATION_FAILED`） | - |
| `TASK_LIMITED_NOT_STARTED` | LIMITED 实例未到开始日期提交 | 409 Conflict |
| `TASK_LIMITED_EXPIRED` | LIMITED 实例已过期提交 | 409 Conflict |
| `TASK_REPEAT_NOT_TRIGGER_DAY` | REPEAT 实例非触发日提交 | 409 Conflict |
| `TASK_STANDING_LIMIT_REACHED` | STANDING 实例达到最大提交次数 | 409 Conflict |

## 8. 测试策略

### 8.1 后端单元测试

- `TaskTemplateServiceTest`：新增 `taskType` 必填、枚举、typeConfig 匹配、子字段校验、不可改等测试用例。
- `TaskTemplateTypeConfigValidatorTest`（若拆出）：覆盖 LIMITED/REPEAT/STANDING 的合法/非法输入。
- `TaskAssignmentServiceTest`：验证快照字段写入。

### 8.2 后端集成测试

- `TaskTemplateControllerIT`：验证 `taskType` 单值/多值筛选、未知值返回 `TASK_TEMPLATE_INVALID_QUERY`。
- 使用 Testcontainers 启动 MySQL，验证数据库迁移与查询。

### 8.3 前端测试

- `types.ts` / `errors.ts`：错误码常量与映射完整。
- 组件测试：任务类型选择器切换后正确渲染子表单。
- 单元测试：`typeConfig` 序列化与反序列化。

### 8.4 回归测试

- 全量运行 `mvn test` 和 `npm test`（如适用），确保既有测试不失败。

## 9. 风险与缓解

| 风险 | 影响 | 缓解 |
|---|---|---|
| 新增快照列导致旧数据展示不一致 | 中 | 快照为空时回退到模板当前值；`task_type` 不可改，不会不一致 |
| 前端表单结构错误导致后端校验失败 | 低 | 前端提供默认结构，后端返回明确字段错误 |
| 多值 `taskType` 参数解析不一致 | 低 | 统一解析逗号分隔字符串，拒绝未知值 |
| 数据库迁移失败 | 低 | 新增列 nullable，迁移脚本幂等可重试 |

## 10. 实施顺序

1. 数据库迁移：`V12__add_task_type_snapshot_columns.sql`
2. 后端实体：`TaskAssignment` 增加快照字段
3. 后端校验：`TaskTemplateService` 补齐 `taskType`/`typeConfig` 校验
4. 后端筛选：`TaskTemplateController` 增加 `taskType` 查询参数
5. 后端快照：`TaskAssignmentService` 创建分配时写入快照
6. 前端错误码：`types.ts` / `errors.ts` 补充
7. 前端类型/UI：`TaskTemplate` 类型 + 动态表单 + 列表筛选
8. 测试与回归

## 11. 与 OpenSpec 的关系

- `openspec/changes/task-template-type-completion/specs/task-template/spec.md` 是验收事实源。
- 本 Design Doc 中所有技术决策均服务于该 delta spec 和 `openspec/specs/task-template/spec.md`，不引入新需求。
- 若实施中发现 spec 仍存歧义，必须回写 spec 并重新运行 `openspec validate task-template-type-completion --strict`。

