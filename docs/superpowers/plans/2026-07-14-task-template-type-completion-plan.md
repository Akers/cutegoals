---
change: task-template-type-completion
design-doc: docs/superpowers/specs/2026-07-14-task-template-type-completion-design.md
base-ref: 8d16c5e0b5a37d7af15c4c062ec5dfc1de67a754
---

# task-template-type-completion 实施计划

> **产物语言**: zh-CN
> **关联文档**:
> - 任务边界：`openspec/changes/task-template-type-completion/tasks.md`（5 章 / 19 子任务）
> - 技术设计：`docs/superpowers/specs/2026-07-14-task-template-type-completion-design.md`（10 步实施顺序 + 7 个错误码 + 3 类动态表单）
> - 验收事实源：`openspec/changes/task-template-type-completion/specs/task-template/spec.md`
> - 上游模式参考：`openspec/specs/task-template/spec.md`（既有 task-template 规格基线）
> **实施顺序**：10 步（Design Doc §10 定义），按 数据库 → 后端 → 前端 → 测试推进
> **测试策略**：Design Doc §8（后端单元测试 → 集成测试 → 前端测试 → 回归测试）

---

## 计划概览

本计划将 19 项子任务按 Design Doc §10 定义的 10 步实施顺序归并为 **6 个阶段**，每阶段对应 tasks.md 的一个章或逻辑子集，阶段间存在严格的前置依赖。每个阶段末尾标注可运行的验证命令。

**基线约束**：
- 后端基线 = HEAD `8d16c5e` 的 `mvn test` = 全部通过
- 前端基线 = `npm test` = 与 core-features baseline 一致
- tsc 编译必须零错误后方可进入 UI 阶段

**整体依赖图**：
```
Phase 1 (DB 迁移) ──→ Phase 3 (快照写入)
       │
       └──→ Phase 2 (后端校验/筛选) ──→ Phase 6 (测试与回归)
                                              ↑
Phase 4 (前端错误码/类型) ──→ Phase 5 (前端UI) ──┘
```

---

## 阶段 1：数据库迁移（2 子任务）

**目标**：创建 Flyway 迁移脚本 `V12__add_task_type_snapshot_columns.sql`，为 `task_assignment` 表添加两列快照字段；更新 `TaskAssignment` 实体映射。

**前置 verify（Design Doc §10 第 1 步）**：
- ⚡ verify `db/migration/` 目录中 `V11` 的编号范围，确认 V12 未使用
- ⚡ verify `task_assignment` 表存在 `snapshot_template_name` 等既有快照字段的命名风格，确保新列命名一致

**涉及 Design Decision**：DD 数据模型（§3.2）

### 任务 1.1：创建 V12 迁移脚本

- **原任务编号**：1.1
- **capability**：task-template
- **目标**：在 `server/common/src/main/resources/db/migration/` 下创建 `V12__add_task_type_snapshot_columns.sql`
- **实现方式**（Design Doc §3.2）：
  ```sql
  ALTER TABLE task_assignment
      ADD COLUMN snapshot_template_task_type VARCHAR(20) DEFAULT NULL,
      ADD COLUMN snapshot_template_type_config JSON DEFAULT NULL;
  ```
- **输入**：Design Doc §3.2 表定义
- **输出**：`V12__add_task_type_snapshot_columns.sql` 文件
- **验收标准**：
  - Flyway 在 H2（单元测试）和 MySQL（集成测试/Testcontainers）上均可成功迁移
  - 迁移幂等可重试（使用 Flyway checksum 验证）
  - 新列均为 NULLABLE，不影响既有数据
- **依赖任务**：无（仅需了解 V11 编号范围）
- **运行验证**：
  ```bash
  mvn flyway:migrate -pl :common -am  # MySQL profile
  # 或直接运行测试验证迁移
  mvn -pl :common -am test -Dtest=*FlywayMigrationTest*
  ```

### 任务 1.2：更新 TaskAssignment 实体

- **原任务编号**：1.2
- **capability**：task-template
- **目标**：在 `TaskAssignment` Java 实体中增加两个快照字段
- **实现方式**（Design Doc §3.2）：
  ```java
  @TableField("snapshot_template_task_type")
  private String snapshotTemplateTaskType;

  @TableField("snapshot_template_type_config")
  private String snapshotTemplateTypeConfig;
  ```
- **输入**：Design Doc §3.2；既有 `TaskAssignment.java` 中 `snapshot_template_name` 等既有快照字段的映射模式
- **输出**：编译通过的 `TaskAssignment` 实体
- **验收标准**：
  - 实体字段与数据库列名映射正确（MyBatis-Plus `@TableField` 注解）
  - 字段与既有快照字段使用一致的命名风格（`snapshot*` 前缀 + 驼峰）
  - `mvn compile` 通过，无编译警告
- **依赖任务**：1.1（迁移脚本先存在，但实体可先编译，建议 V12 SQL 和实体同时提交）
- **建议提交粒度**：1 个 commit（与 1.1 合并）
- **运行验证**：
  ```bash
  mvn -pl :task -am compile
  ```

---

## 阶段 2：后端校验与接口补全（4 子任务）

**目标**：补齐 `TaskTemplateService` 中 `taskType`/`typeConfig` 校验逻辑（必填、枚举、子字段匹配、不可改）；增加 `TaskTemplateController` 的 `taskType` 查询参数筛选。

**前置 verify（Design Doc §4.1、§4.2）**：
- ⚡ verify `TaskTemplateService.java` 现有 `createTemplate`/`updateTemplate` 方法签名，确认 `taskType`/`typeConfig` 在当前请求体 DTO 中是否已存在
  - 如 DTO 已有 `taskType`/`typeConfig` 字段但未校验 → 仅补齐 validate 即可
  - 如 DTO 尚无 → 需先扩展 DTO 再补校验
- ⚡ verify `TaskTemplateMapper.xml` 中 `queryTemplates` 的 SQL 片段，确认如何拼接 `WHERE` 条件

**涉及 Design Decision**：DD4.1（校验增强）、DD4.2（列表筛选）

### 任务 2.1：实现 validateTypeConfig 方法

- **原任务编号**：2.1
- **capability**：task-template
- **目标**：在 `TaskTemplateService` 中实现私有方法 `validateTypeConfig(String taskType, String typeConfig)`，集中处理所有校验逻辑
- **实现方式**（Design Doc §4.1）：
  1. `taskType` 必填 → `TASK_TEMPLATE_VALIDATION_FAILED`
  2. `taskType` 枚举校验（LIMITED/REPEAT/STANDING）→ `TASK_TEMPLATE_VALIDATION_FAILED`
  3. `typeConfig` 必填 → `TASK_TEMPLATE_VALIDATION_FAILED`
  4. `typeConfig` 与 `taskType` 字段匹配校验
  5. 建议返回 `List<FieldError>` 结构，支持字段级错误定位
- **输入**：Design Doc §4.1 的 5 类校验规则
- **输出**：`validateTypeConfig` 私有方法，在 `createTemplate`/`updateTemplate` 开头调用
- **验收标准**：
  - 缺失 taskType → 抛出 `TASK_TEMPLATE_VALIDATION_FAILED`
  - 未知枚举值 → 抛出 `TASK_TEMPLATE_VALIDATION_FAILED`
  - 缺失 typeConfig → 抛出 `TASK_TEMPLATE_VALIDATION_FAILED`
  - 合法输入 → 无异常，正常继续
- **依赖任务**：无（仅需既有 `TaskTemplate` 实体已有 `taskType`/`typeConfig` 字段）
- **建议提交粒度**：1 个 commit
- **运行验证**：
  ```bash
  mvn -pl :task -am test -Dtest=*TaskTemplateService*
  ```

### 任务 2.2：实现 typeConfig 子字段校验

- **原任务编号**：2.2
- **capability**：task-template
- **目标**：在 `validateTypeConfig` 中实现三种 `taskType` 的 typeConfig 子字段校验
- **实现方式**（Design Doc §4.1 → 子字段校验）：
  - **LIMITED**：必须包含 `end_date`（非空），可选 `start_date`；`end_date >= start_date`（均非空时）
  - **REPEAT**：必须包含 `frequency`（DAILY/WEEKLY/MONTHLY/YEARLY），并按 `frequency` 校验 `trigger_day`：
    - WEEKLY：`trigger_day` 为 1-7 的整数（周一到周日）
    - MONTHLY：`trigger_day` 为 `FIRST_DAY`/`LAST_DAY`/`MID_MONTH` 之一
    - YEARLY：需包含 `month`（1-12）和 `day`（1-31）
  - **STANDING**：必须包含 `max_submissions`（null 或 1~10000 正整数）
- **输入**：Design Doc §4.1 子字段规则
- **输出**：`validateTypeConfig` 方法完成全部子字段校验逻辑
- **验收标准**：
  - LIMITED 缺 `end_date` → 校验失败
  - REPEAT `${frequency}` + 缺少合法 `trigger_day` → 校验失败
  - STANDING `max_submissions=0` 或负数 → 校验失败
  - 四种类型的合法输入 → 校验通过
- **依赖任务**：2.1（`validateTypeConfig` 方法框架必须先存在）
- **建议提交粒度**：与 2.1 合并为 1 个 commit
- **运行验证**：
  ```bash
  mvn -pl :task -am test -Dtest=*TaskTemplateService*,*TaskTemplateTypeConfigValidator*
  ```

### 任务 2.3：实现 taskType 列表筛选

- **原任务编号**：2.3
- **capability**：task-template
- **目标**：在 `TaskTemplateController.queryTemplates` 增加 `taskType` 可选参数；`TaskTemplateService` 中实现 `IN` 条件筛选
- **实现方式**（Design Doc §4.2、§6）：
  ```java
  @RequestParam(required = false) String taskType
  ```
  - 服务层解析逗号分隔字符串 → `List<String>` 
  - 传入未知值 → 抛出 `TASK_TEMPLATE_INVALID_QUERY`
  - 使用 MyBatis-Plus `IN` 条件：`lambdaQuery().in(TaskTemplate::getTaskType, typeList)`
- **输入**：Design Doc §6 API 影响表
- **输出**：`GET /api/task-templates?taskType=REPEAT` 和 `?taskType=LIMITED,STANDING` 两种形式均有效
- **验收标准**：
  - 无 `taskType` 参数 → 返回全量（向后兼容）
  - 单值 `taskType=REPEAT` → 仅返回 REPEAT 模板
  - 多值 `taskType=LIMITED,STANDING` → 返回两种类型模板的并集
  - 未知值 → 400 + `TASK_TEMPLATE_INVALID_QUERY`
- **依赖任务**：无（可独立于 2.1/2.2 实现）
- **建议提交粒度**：1 个独立 commit
- **运行验证**：
  ```bash
  mvn -pl :task -am test -Dtest=*TaskTemplateController*
  ```

### 任务 2.4：实现 taskType 不可改性

- **原任务编号**：2.4
- **capability**：task-template
- **目标**：在 `updateTemplate` 中校验请求体 `taskType` 与数据库当前值是否一致
- **实现方式**（Design Doc §4.1 第 5 点）：
  ```java
  // 查询数据库当前值
  String currentTaskType = existingTemplate.getTaskType();
  if (!currentTaskType.equals(request.getTaskType())) {
      throw new TaskTemplateException(TASK_TEMPLATE_TYPE_IMMUTABLE);
  }
  ```
  - 校验放在 `validateTypeConfig` 之后，版本递增之前
  - 不满足时不增加版本号
- **输入**：Design Doc §7 错误码表中的 `TASK_TEMPLATE_TYPE_IMMUTABLE`
- **输出**：更新时修改 `taskType` → 409 Conflict + `TASK_TEMPLATE_TYPE_IMMUTABLE`
- **验收标准**：
  - `taskType` 与数据库一致 → 正常更新
  - `taskType` 与数据库不一致 → 409，不增加版本
  - `taskType` 未在请求体中传递（不影响）→ 正常更新（需确保 DTO 中 `taskType` 为 null 时不触发校验）
- **依赖任务**：2.1（`validateTypeConfig` 方法框架为前置，但逻辑上可独立实现）
- **建议提交粒度**：与 2.1/2.2 合并为 1 个 commit
- **运行验证**：
  ```bash
  mvn -pl :task -am test -Dtest=*TaskTemplateService*
  ```

---

## 阶段 3：后端分配快照写入（1 子任务）

**目标**：在 `TaskAssignmentService.createAssignmentEntity` 中，创建 `TaskAssignment` 时从模板读取 `taskType` 和 `typeConfig` 并写入快照字段。

**前置 verify**：
- ⚡ verify `TaskAssignmentService.createAssignmentEntity` 方法中既有快照字段写入的代码位置和模式
- ⚡ verify `template.getTaskType()` 和 `template.getTypeConfig()` 返回值类型（String/String）

**涉及 Design Decision**：DD4.3（快照写入）

### 任务 3.1：分配时写入 taskType 快照

- **原任务编号**：2.5
- **capability**：task-assignment
- **目标**：在 `createAssignmentEntity` 中新增两行快照写入
- **实现方式**（Design Doc §4.3）：
  ```java
  assignment.setSnapshotTemplateTaskType(template.getTaskType());
  assignment.setSnapshotTemplateTypeConfig(template.getTypeConfig());
  ```
  放在既有快照字段（模板名称、描述、分类、图标、难度名称、奖励积分）写入的同位置
- **输入**：Design Doc §4.3
- **输出**：新创建的 `TaskAssignment` 记录包含 `snapshot_template_task_type` 和 `snapshot_template_type_config` 非空值
- **验收标准**：
  - 创建分配后快照字段值 = 模板当前值
  - 模板后续修改 `typeConfig` 不影响已创建的分配快照
  - 既有历史分配（已有的 NULL 记录）不受影响
- **依赖任务**：1.2（实体字段必须先存在）
- **建议提交粒度**：1 个独立 commit
- **运行验证**：
  ```bash
  mvn -pl :task -am test -Dtest=*TaskAssignmentService*
  ```

---

## 阶段 4：前端类型与错误码（3 子任务）

**目标**：在前端项目中补充 6 个新增错误码常量与中文提示；扩展 `TaskTemplate` 接口类型。本阶段与后端阶段 1-3 无代码耦合，可完全并行。

**前置 verify**：
- ⚡ verify `web/src/shared/api/types.ts` 中既有 `ErrorCodes` 对象的格式（对象 vs 联合类型 vs const enum）
- ⚡ verify `web/src/shared/api/errors.ts` 中错误映射函数签名
- ⚡ verify `web/src/parent/pages/index.tsx` 中既有 `TaskTemplate` 接口定义位置

**涉及 Design Decision**：DD5.1（错误码同步）、DD5.2（类型扩展）

### 任务 4.1：补充错误码常量

- **原任务编号**：3.1
- **capability**：web-app
- **目标**：在 `web/src/shared/api/types.ts` 的 `ErrorCodes` 对象中新增 6 个错误码
- **实现方式**（Design Doc §5.2）：
  ```typescript
  TASK_TEMPLATE_TYPE_IMMUTABLE: 'TASK_TEMPLATE_TYPE_IMMUTABLE',
  TASK_TEMPLATE_TYPE_CONFIG_MISMATCH: 'TASK_TEMPLATE_TYPE_CONFIG_MISMATCH',
  TASK_LIMITED_NOT_STARTED: 'TASK_LIMITED_NOT_STARTED',
  TASK_LIMITED_EXPIRED: 'TASK_LIMITED_EXPIRED',
  TASK_REPEAT_NOT_TRIGGER_DAY: 'TASK_REPEAT_NOT_TRIGGER_DAY',
  TASK_STANDING_LIMIT_REACHED: 'TASK_STANDING_LIMIT_REACHED',
  ```
- **输入**：Design Doc §7 错误码表、Design Doc §5.2
- **输出**：`ErrorCodes` 对象包含完整 6 个新常量
- **验收标准**：TypeScript 编译通过，无缺失常量
- **依赖任务**：无
- **建议提交粒度**：与 4.2 合并为 1 个 commit
- **运行验证**：
  ```bash
  cd web && npx tsc -b --noEmit
  ```

### 任务 4.2：补充中文错误提示

- **原任务编号**：3.2
- **capability**：web-app
- **目标**：在 `web/src/shared/api/errors.ts` 中为 6 个新错误码补充映射中文提示
- **实现方式**：
  ```typescript
  TASK_TEMPLATE_TYPE_IMMUTABLE: '任务类型不可修改',
  TASK_TEMPLATE_TYPE_CONFIG_MISMATCH: '任务类型配置不匹配',
  TASK_LIMITED_NOT_STARTED: '该任务尚未到达开始日期',
  TASK_LIMITED_EXPIRED: '该任务已过期',
  TASK_REPEAT_NOT_TRIGGER_DAY: '今天不是该重复任务的触发日',
  TASK_STANDING_LIMIT_REACHED: '该常驻任务已达到最大提交次数',
  ```
- **输入**：Design Doc §5.2
- **输出**：中文提示映射表包含全部新错误码
- **验收标准**：UI 错误映射测试覆盖（错误码 → 中文提示的单元测试）
- **依赖任务**：4.1（常量必须先存在）
- **建议提交粒度**：与 4.1 合并
- **运行验证**：
  ```bash
  cd web && npm test -- --run  # 确认错误映射测试通过
  ```

### 任务 4.3：扩展 TaskTemplate 类型

- **原任务编号**：3.3
- **capability**：web-app
- **目标**：在 `web/src/parent/pages/index.tsx` 中扩展 `TaskTemplate` 接口增加 `taskType` 和 `typeConfig`
- **实现方式**（Design Doc §5.1）：
  ```typescript
  interface TaskTemplate {
    // ... 既有字段不变
    taskType: 'LIMITED' | 'REPEAT' | 'STANDING';
    typeConfig: string; // JSON string
  }
  ```
- **输入**：Design Doc §5.1
- **输出**：类型化的 `TaskTemplate` 接口
- **验收标准**：
  - `tsc` 编译通过，无 any 泄漏
  - 所有使用 `TaskTemplate` 的地方类型检查通过
- **依赖任务**：无（纯类型定义，无运行时依赖）
- **建议提交粒度**：1 个独立 commit
- **运行验证**：
  ```bash
  cd web && npx tsc -b --noEmit
  ```

---

## 阶段 5：前端模板管理 UI（5 子任务）

**目标**：实现模板创建/编辑表单中的任务类型选择器、三类动态配置表单、模板列表筛选器。本阶段依赖阶段 4 的类型扩展。

**前置 verify（Design Doc §5.3、§5.4）**：
- ⚡ verify 模板创建/编辑表单在 `index.tsx` 中的代码位置（是独立组件还是内联表单）
- ⚡ verify 模板列表页面当前筛选器实现方式（如果有）
- ⚡ verify 既有 `<form>` 或表单组件的提交数据序列化方式（JSON.stringify 还是 FormData）

**涉及 Design Decision**：DD5.3（动态表单渲染）、DD5.4（列表筛选）

### 任务 5.1：添加任务类型选择器

- **原任务编号**：4.1
- **capability**：web-app
- **目标**：在模板创建/编辑表单中增加 `<select>` 下拉选择器，值为 `LIMITED` / `REPEAT` / `STANDING`
- **实现方式**（Design Doc §5.3）：
  ```tsx
  <select value={taskType} onChange={e => setTaskType(e.target.value)}>
    <option value="">选择任务类型</option>
    <option value="LIMITED">限时任务</option>
    <option value="REPEAT">重复任务</option>
    <option value="STANDING">常驻任务</option>
  </select>
  ```
  - 选择后触发状态更新，动态渲染对应子表单
- **输入**：Design Doc §5.3
- **输出**：可交互的任务类型下拉选择器
- **验收标准**：选择不同类型时，表单区域动态切换为对应配置项
- **依赖任务**：4.3（`TaskTemplate` 类型扩展）
- **建议提交粒度**：与 5.2-5.4 合并为 1-2 个 commits
- **运行验证**：
  ```bash
  cd web && npm test -- --run  # 组件测试覆盖类型切换
  ```

### 任务 5.2：实现 LIMITED 配置表单

- **原任务编号**：4.2
- **capability**：web-app
- **目标**：实现 LIMITED 类型的配置表单：开始日期（可选 date input）、结束日期（必填 date input）
- **实现方式**（Design Doc §5.3）：
  ```tsx
  {taskType === 'LIMITED' && (
    <div>
      <label>开始日期（可选）<input type="date" value={startDate} onChange={...} /></label>
      <label>结束日期<input type="date" value={endDate} onChange={...} /></label>
    </div>
  )}
  ```
  - 提交时序列化为 JSON：`JSON.stringify({ start_date, end_date })`
- **输入**：Design Doc §5.3 的 LIMITED 配置描述
- **输出**：LIMITED 配置子表单
- **验收标准**：
  - 开始日期可选，结束日期必填
  - 提交结构：`{"start_date":"2026-07-15","end_date":"2026-07-31"}`
- **依赖任务**：5.1（选择器必须先存在）
- **建议提交粒度**：与 5.1 合并
- **运行验证**：
  ```bash
  cd web && npx tsc -b --noEmit && npm test -- --run
  ```

### 任务 5.3：实现 REPEAT 配置表单

- **原任务编号**：4.3
- **capability**：web-app
- **目标**：实现 REPEAT 类型的配置表单：频率选择（DAILY/WEEKLY/MONTHLY/YEARLY）+ 触发日配置
- **实现方式**（Design Doc §5.3）：
  ```tsx
  {taskType === 'REPEAT' && (
    <div>
      <select value={frequency} onChange={...}>
        <option value="DAILY">每天</option>
        <option value="WEEKLY">每周</option>
        <option value="MONTHLY">每月</option>
        <option value="YEARLY">每年</option>
      </select>
      {frequency === 'WEEKLY' && <WeekdayPicker />}
      {frequency === 'MONTHLY' && <MonthlyModePicker />}
      {frequency === 'YEARLY' && <MonthDayPicker />}
    </div>
  )}
  ```
  - WEEKLY：多选周几（1-7 checkbox）
  - MONTHLY：选择模式（FIRST_DAY/LAST_DAY/MID_MONTH）
  - YEARLY：选择月份（1-12）+ 日期（1-31）
- **输入**：Design Doc §5.3 的 REPEAT 配置描述
- **输出**：REPEAT 配置子表单（含四种频率）
- **验收标准**：
  - 四种频率切换后渲染不同触发日配置
  - 提交结构正确：`{"frequency":"DAILY"}` 或 `{"frequency":"WEEKLY","trigger_day":"1,3,5"}`
- **依赖任务**：5.1
- **建议提交粒度**：与 5.1/5.2 合并
- **运行验证**：
  ```bash
  cd web && npx tsc -b --noEmit && npm test -- --run
  ```

### 任务 5.4：实现 STANDING 配置表单

- **原任务编号**：4.4
- **capability**：web-app
- **目标**：实现 STANDING 类型的配置表单：最大提交次数输入 + "无限"开关
- **实现方式**（Design Doc §5.3）：
  ```tsx
  {taskType === 'STANDING' && (
    <div>
      <label>
        <input type="checkbox" checked={unlimited} onChange={...} />
        无限提交
      </label>
      {!unlimited && (
        <label>
          最大提交次数
          <input type="number" min="1" max="10000" value={maxSubmissions} onChange={...} />
        </label>
      )}
    </div>
  )}
  ```
  - 勾选"无限"时 `max_submissions` 设为 null
  - 未勾选时要求 1~10000 正整数
- **输入**：Design Doc §5.3 的 STANDING 配置描述
- **输出**：STANDING 配置子表单
- **验收标准**：
  - "无限"开 → `{"max_submissions": null}`
  - "无限"关 + 输入值 → `{"max_submissions": 5}`
- **依赖任务**：5.1
- **建议提交粒度**：与 5.1-5.3 合并
- **运行验证**：
  ```bash
  cd web && npx tsc -b --noEmit && npm test -- --run
  ```

### 任务 5.5：实现模板列表任务类型筛选器

- **原任务编号**：4.5
- **capability**：web-app
- **目标**：在模板列表页增加任务类型多选筛选器
- **实现方式**（Design Doc §5.4）：
  ```tsx
  <div>
    <label><input type="checkbox" value="LIMITED" checked={...} onChange={...} /> 限时任务</label>
    <label><input type="checkbox" value="REPEAT" checked={...} onChange={...} /> 重复任务</label>
    <label><input type="checkbox" value="STANDING" checked={...} onChange={...} /> 常驻任务</label>
  </div>
  ```
  - 选中状态变化时拼接 `taskType=LIMITED,STANDING` 参数
  - 无选中时（全部）不传 `taskType` 参数
- **输入**：Design Doc §5.4、§6 API 影响
- **输出**：模板列表页的任务类型多选筛选器
- **验收标准**：
  - 筛选结果 URL 参数正确：`?taskType=LIMITED,STANDING`
  - 清空筛选 → 返回全部模板
  - 筛选结果无前后端不一致
- **依赖任务**：2.3（后端筛选端点先就绪）、4.3（类型扩展）
- **建议提交粒度**：1 个独立 commit
- **运行验证**：
  ```bash
  cd web && npx tsc -b --noEmit
  # 手动浏览器验证：模板列表页筛选器正常交互
  ```

---

## 阶段 6：测试与回归（6 子任务）

**目标**：为全部新增逻辑编写单元/集成/组件测试，全量回归确保无既有测试失败。

**前置 verify**：
- ⚡ verify 当前后端测试套件数量：`mvn test | grep "Tests run:"`
- ⚡ verify 当前前端测试套件基线：`npm test -- --run`
- ⚡ verify Testcontainers 是否已在项目中配置（`@Testcontainers` 注解是否可用）

### 任务 6.1：后端校验单元测试

- **原任务编号**：5.1
- **capability**：task-template
- **目标**：为 `TaskTemplateService` 新增校验逻辑编写单元测试
- **测试策略**（Design Doc §8.1）：
  - `createTemplate_missingTaskType_throws`：缺失 taskType → `TASK_TEMPLATE_VALIDATION_FAILED`
  - `createTemplate_invalidTaskType_throws`：未知枚举 → `TASK_TEMPLATE_VALIDATION_FAILED`
  - `createTemplate_missingTypeConfig_throws`：缺失 typeConfig → `TASK_TEMPLATE_VALIDATION_FAILED`
  - `createTemplate_validInput_success`：合法输入 → 正常创建
  - `updateTemplate_changeTaskType_throws`：修改 taskType → `TASK_TEMPLATE_TYPE_IMMUTABLE`
  - `updateTemplate_sameTaskType_success`：相同 taskType → 正常更新
  - 子字段校验覆盖（LIMITED/REPEAT/STANDING 合法/非法输入）
- **输入**：Design Doc §8.1
- **输出**：`TaskTemplateServiceTest.java` 中新增 10+ 测试用例
- **验收标准**：全部新增测试通过
- **依赖任务**：阶段 2（校验逻辑就绪）
- **建议提交粒度**：1 个独立 commit
- **运行验证**：
  ```bash
  mvn -pl :task -am test -Dtest=*TaskTemplateService*
  ```

### 任务 6.2：后端列表筛选集成测试

- **原任务编号**：5.2
- **capability**：task-template
- **目标**：为 `taskType` 列表筛选编写集成测试（Testcontainers）
- **测试策略**（Design Doc §8.2）：
  - `queryTemplates_withSingleTaskType_returnsFiltered`：单值筛选
  - `queryTemplates_withMultiTaskType_returnsUnion`：多值筛选
  - `queryTemplates_withInvalidTaskType_throws`：未知值 → `TASK_TEMPLATE_INVALID_QUERY`
  - `queryTemplates_withoutTaskType_returnsAll`：无参数向后兼容
- **输入**：Design Doc §8.2
- **输出**：`TaskTemplateControllerIT.java` 中新增测试用例
- **验收标准**：Testcontainers 启动 MySQL，全部 4 个场景通过
- **依赖任务**：2.3（筛选逻辑就绪）
- **建议提交粒度**：1 个独立 commit
- **运行验证**：
  ```bash
  mvn -pl :task -am test -Dtest=*TaskTemplateControllerIT*
  ```

### 任务 6.3：后端分配快照集成测试

- **原任务编号**：5.3
- **capability**：task-assignment
- **目标**：为分配快照写入编写集成测试
- **测试策略**（Design Doc §8.2）：
  - `createAssignment_snapshotFieldsPopulated`：新分配快照字段非空且等于模板当前值
  - `createAssignment_templateChangesNotAffectSnapshot`：模板修改后已有分配快照不变
- **输入**：Design Doc §4.3、§8.2
- **输出**：`TaskAssignmentServiceTest.java` 或 `TaskAssignmentControllerIT.java` 中新增测试
- **验收标准**：两个场景全部通过
- **依赖任务**：3.1（快照写入逻辑就绪）
- **建议提交粒度**：与 6.2 合并或独立
- **运行验证**：
  ```bash
  mvn -pl :task -am test -Dtest=*TaskAssignment*
  ```

### 任务 6.4：前端组件测试

- **原任务编号**：5.4
- **capability**：web-app
- **目标**：为任务类型表单和错误码映射编写前端单元/组件测试
- **测试策略**（Design Doc §8.3）：
  - 错误码映射测试：`errors.ts` 中 6 个新错误码返回正确中文
  - 组件测试：任务类型选择器切换后渲染正确子表单
  - `typeConfig` 序列化/反序列化测试（JSON.stringify → parse）
- **输入**：Design Doc §8.3
- **输出**：新增前端测试用例
- **验收标准**：`npm test` 全部新增通过
- **依赖任务**：阶段 4 + 阶段 5（前端代码就绪）
- **建议提交粒度**：1 个独立 commit
- **运行验证**：
  ```bash
  cd web && npm test -- --run
  ```

### 任务 6.5：后端全量回归

- **原任务编号**：5.5
- **capability**：task-template、task-assignment、task-review
- **目标**：全量运行后端 task-template / task-assignment / task-review 测试套件
- **测试策略**（Design Doc §8.4）：
  ```bash
  mvn -pl :task -am test -Dtest="*TaskTemplate*,*TaskAssignment*,*TaskReview*"
  ```
- **验收标准**：所有既有测试 + 新增测试全部通过，0 failures
- **依赖任务**：6.1、6.2、6.3
- **建议提交粒度**：无代码变更
- **运行验证**：
  ```bash
  mvn -pl :task -am test
  ```

### 任务 6.6：全量构建与测试验证

- **原任务编号**：5.6
- **capability**：task-template
- **目标**：运行全量后端和前端构建/测试，确认整体代码仓库健康
- **测试策略**（Design Doc §8.4）：
  ```bash
  mvn test                        # 后端全量
  cd web && npm run test          # 前端测试
  cd web && npm run build         # 前端构建
  ```
- **验收标准**：
  - `mvn test` → BUILD SUCCESS，0 failures
  - `npm run test` → baseline 一致（14 failed / 65 passed），0 新增失败
  - `npm run build` → 构建产物正常
- **依赖任务**：6.4、6.5
- **建议提交粒度**：无代码变更
- **运行验证**：
  ```bash
  mvn test && cd web && npm run test && npm run build
  ```

---

## 附加：Build 阶段 Verify Point 汇总

| # | Verify 点 | 来源 | 影响 | 决策时机 |
|---|---|---|---|---|
| 1 | V12 迁移编号是否可用 | DD 数据模型 / §10 Step 1 | 任务 1.1 SQL 文件命名 | 阶段 1 开始前 |
| 2 | `task_assignment` 既有快照字段命名风格 | DD 数据模型 / §3.2 | 任务 1.2 新字段命名 | 阶段 1 开始前 |
| 3 | DTO 是否已有 `taskType`/`typeConfig` 字段 | DD §4.1 | 任务 2.1 校验实现方式 | 阶段 2 开始前 |
| 4 | `queryTemplates` 的 SQL 拼接方式 | DD §4.2 | 任务 2.3 筛选实现 | 阶段 2 开始前 |
| 5 | `createAssignmentEntity` 既有快照写入模式 | DD §4.3 | 任务 3.1 快照写入位置 | 阶段 3 开始前 |
| 6 | `ErrorCodes` 对象格式 | DD §5.2 | 任务 4.1 错误码格式 | 阶段 4 开始前 |
| 7 | 模板表单是组件还是内联 | DD §5.3 | 任务 5.1-5.4 实现方式 | 阶段 5 开始前 |
| 8 | Testcontainers 是否已配置 | DD §8.2 | 任务 6.2 集成测试 | 阶段 6 开始前 |

---

## 附加：Risks & Mitigations 摘要（来自 Design Doc §9）

| # | 风险 | 缓解措施 | 影响阶段 |
|---|---|---|---|
| 1 | 新增快照列导致旧数据展示不一致 | 快照为空时回退到模板当前值；`task_type` 不可改 | 阶段 3 |
| 2 | 前端表单结构错误导致后端校验失败 | 前端提供默认结构，后端返回明确字段错误 | 阶段 5 |
| 3 | 多值 `taskType` 参数解析不一致 | 统一解析逗号分隔字符串，拒绝未知值 | 阶段 2 |
| 4 | 数据库迁移失败 | 新增列 nullable，迁移脚本幂等可重试 | 阶段 1 |

---

## 附加：Testing Strategy 汇总（来自 Design Doc §8）

| 验证层级 | 命令/方式 | 通过标准 | 阶段 |
|---|---|---|---|
| 后端单元测试 | `mvn -pl :task -am test -Dtest=*TaskTemplateService*` | 10+ 新增测试全绿 | 阶段 6 |
| 后端集成测试 | `mvn -pl :task -am test -Dtest=*TaskTemplateControllerIT*` | 4 场景全通过 | 阶段 6 |
| 后端快照测试 | `mvn -pl :task -am test -Dtest=*TaskAssignment*` | 2 场景全通过 | 阶段 6 |
| 类型检查 | `cd web && npx tsc -b --noEmit` | exit 0, 0 errors | 阶段 4/5 |
| 前端组件测试 | `cd web && npm run test` | baseline 一致，新增测试通过 | 阶段 6 |
| 全量后端测试 | `mvn test` | BUILD SUCCESS, 0 failures | 阶段 6 |
| 全量前端测试 | `cd web && npm run test` | baseline 一致，0 新增失败 | 阶段 6 |
| 全量前端构建 | `cd web && npm run build` | 构建产物正常 | 阶段 6 |

---

## 附录：文件改动清单

| 文件 | 操作 | 估计行数 | 说明 |
|---|---|---|---|
| `server/common/src/main/resources/db/migration/V12__add_task_type_snapshot_columns.sql` | 新建 | +3 行 | 新增两列快照字段 |
| `server/task/src/main/java/.../entity/TaskAssignment.java` | 修改 | +4 行 | 新增两个 `@TableField` 字段 |
| `server/task/src/main/java/.../service/TaskTemplateService.java` | 修改 | +50~80 行 | `validateTypeConfig` 方法 + 子字段校验 + 不可改校验 |
| `server/task/src/main/java/.../controller/TaskTemplateController.java` | 修改 | +2 行 | `queryTemplates` 增加 `@RequestParam(required=false) String taskType` |
| `server/task/src/main/java/.../service/TaskAssignmentService.java` | 修改 | +2 行 | 快照写入 |
| `server/task/src/test/java/.../TaskTemplateServiceTest.java` | 修改 | +~100 行 | 新增 10+ 校验测试用例 |
| `server/task/src/test/java/.../TaskTemplateControllerIT.java` | 修改 | +~80 行 | 新增 4 个筛选集成测试 |
| `server/task/src/test/java/.../TaskAssignmentServiceTest.java` | 修改 | +~40 行 | 新增快照测试 |
| `web/src/shared/api/types.ts` | 修改 | +6 行 | ErrorCodes 新增 6 个常量 |
| `web/src/shared/api/errors.ts` | 修改 | +6 行 | 错误码中文映射 |
| `web/src/parent/pages/index.tsx` | 修改 | +100~150 行 | TaskTemplate 类型扩展 + 动态表单 + 列表筛选器 |
| `web/src/parent/components/__tests__/TaskTypeForm.test.tsx` | 新建 | +~80 行 | 组件测试 |
