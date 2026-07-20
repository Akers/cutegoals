## Why

当前任务模板只能通过 `task_type=STANDING` + `type_config.max_submissions` 限制单个 assignment 内的提交次数，存在两个产品空白：

1. 无法表达「积分上限」语义——家长无法控制「孩子通过这个模板最多累计获得多少积分」（同一模板的多次 REPEAT/LIMITED assignment 累计没有上限保护）。
2. 限制粒度耦合在 STANDING 类型的 `type_config` 内，REPEAT/LIMITED 类型完全无法配置任何重投上限；家长如果希望「每周家务模板累计最多奖励 50 积分」或「阅读打卡模板最多通过 30 次」目前无任何实现路径。

把孩子反复刷模板积分的场景纳入产品可控范围是 CuteGoals 激励机制的必要闭环。本次变更把「允许重复提交」「最大提交次数」「积分上限」提升为 task-template 的一等属性，并在 task-assignment 上固化快照、在提交接口前置校验、在孩子端 UI 上表达已达上限状态，让家长一次性配置、系统跨周期强制执行。

## What Changes

- **新增 task-template 一等属性**（替代 STANDING `type_config.max_submissions`）：
  - `allow_resubmit: BOOLEAN NOT NULL DEFAULT FALSE`：是否允许同一孩子在同一模板下重复提交（默认不勾选）
  - `max_submissions: INT NOT NULL DEFAULT 0`：最大「已通过」提交次数上限；`0` 表示不限制；仅在 `allow_resubmit=true` 时有意义
  - `points_cap: INT NOT NULL DEFAULT 0`：该孩子在该模板上累计可获得的积分上限；`0` 表示不限制；仅在 `allow_resubmit=true` 时有意义
- **BREAKING**：`task_type=STANDING` 不再读取 `type_config.max_submissions`，迁移到一等属性 `max_submissions`。`type_config` 内的 `max_submissions` 字段在迁移后视为只读历史字段；新创建的 STANDING 模板允许 `type_config` 内不再携带 `max_submissions`。`task_template.service.TaskTemplateService` / `TaskReviewService.parseMaxSubmissions` 中读取 `type_config.max_submissions` 的逻辑全部下线。
- **数据迁移 V14**：新增 `task_template` 三个字段 + `task_assignment` 三个 snapshot 字段；把所有 `task_type=STANDING` 模板的 `type_config.max_submissions`（null 或 1-10000）映射为 `allow_resubmit = (max_submissions IS NOT NULL)`、`max_submissions = COALESCE(原值, 0)`；其他类型默认 `allow_resubmit=FALSE`、`max_submissions=0`、`points_cap=0`；既有 `task_assignment.snapshot_template_type_config.max_submissions` 保留不动，新增同语义 snapshot 字段从 `task_template` 同步快照。
- **task-assignment 快照扩展**：分配创建时除了现有 snapshot_template_* 字段外，新增固化 `snapshot_template_allow_resubmit`、`snapshot_template_max_submissions`、`snapshot_template_points_cap`，使审核期校验与模板后续修改解耦。
- **task-review 提交前置校验**：`POST /api/task-review/submissions` 在执行现有「状态合法 / 迟交策略 / 幂等」校验之前，新增两道前置校验（仅当 `snapshot_template_allow_resubmit=true` 时启用）：
  - 「已通过次数」以 child + template 维度统计（跨 assignment 聚合：APPROVED 状态的 task_assignment 数 + REJECTED→重投历史中以 APPROVED 结束的尝试），达到 `snapshot_template_max_submissions` 时拒绝
  - 「累计获得积分」以 child + template 维度统计 PointsLedger 中通过该模板获得的积分总额，达到 `snapshot_template_points_cap` 时拒绝
  - 拒绝时分别返回稳定错误码 `TASK_SUBMISSION_MAX_REACHED` / `TASK_SUBMISSION_POINTS_CAP_REACHED`
- **孩子端任务列表可观察状态**：`GET /api/task-assignments?childId=...` 返回的每个 assignment 新增 `canSubmit: boolean` 和 `submissionBlockReason: 'MAX_REACHED' | 'POINTS_CAP_REACHED' | null` 字段（仅 PENDING / REJECTED 状态有判定意义）；前端按此禁用对应任务的提交按钮并显示 tooltip。
- **家长端模板编辑表单**：`ParentTemplatesPage` 表单新增「允许重复提交」复选框（默认不勾选）；勾选后条件显示「最大提交次数」`InputNumber`（min=0, max=10000, 默认 0）和「积分上限」`InputNumber`（min=0, max=100000000, 默认 0），提示文案 `0 = 不限制`。
- **管理端审计**：模板字段变更与提交拒绝事件复用现有审计通道（task-template audit log + task-review attempt history），不新增独立审计表。

## Capabilities

### New Capabilities

无。本次变更通过扩展现有 task-template、task-assignment、task-review 三个 capability 的 requirement 表达；家长端 / 孩子端 UI 行为作为 task-template 字段编辑契约和 task-review 提交校验的可观察 scenario 描述，不构成独立业务能力。

### Modified Capabilities

- `task-template`：新增「重复提交控制属性」requirement，定义 `allow_resubmit` / `max_submissions` / `points_cap` 三个一等字段的语义、取值范围和默认值；修改「创建任务模板与字段验证」requirement，移除 STANDING 类型对 `type_config.max_submissions` 的硬性要求（迁移到一等属性）；修改「更新模板仅影响未来分配」requirement，明确新字段可更新且仅影响新分配。
- `task-assignment`：扩展「人工单次分配与数据快照」requirement，在固化快照字段清单中追加三个 `snapshot_template_*` 字段；同步覆盖批量分配和周期生成路径。
- `task-review`：新增「重复提交次数与积分上限前置校验」requirement，定义提交接口的两道前置校验、两个稳定错误码、跨 assignment 统计口径，以及孩子端任务列表的 `canSubmit` / `submissionBlockReason` 字段语义。

## Impact

**受影响代码**：

- 后端 `server/common/`：
  - `entity/task/TaskTemplate.java`、`entity/task/TaskAssignment.java` 新增字段 + Lombok 注解
  - `resources/db/migration/V14__add_resubmission_controls.sql` 新增迁移脚本
- 后端 `server/task/`：
  - `service/TaskTemplateService.java`（约 740 行附近的 maxSubmissions 解析逻辑、字段校验、模板创建/更新 DTO）
  - `service/TaskAssignmentService.java`（snapshot 字段写入逻辑）
  - `controller/dto/`：请求/响应 DTO 新增字段
  - 现有 STANDING `parseMaxSubmissions` 相关代码全部下线
- 后端 `server/task-review/`：
  - `service/TaskReviewService.java`（submit 入口前置校验，约 189 行附近的现有 maxSubmissions 校验被替换）
  - `mapper/TaskReviewMapper`（新增按 template_id + child_id 聚合 APPROVED 数量的查询）
- 后端 `server/points/`：
  - `mapper/PointsLedgerMapper`（新增按 template_id + child_id 聚合积分总额的 JOIN 查询，依赖 `business_ref = "ATTEMPT_<id>"` → `task_attempt.assignment_id` → `task_assignment.template_id` 链路）
- 后端 `web/`（前端）：
  - `parent/pages/ParentTemplatesPage.tsx`（表单字段）
  - `parent/components/TaskTypeConfigForms.tsx`（STANDING 子表单移除「最大提交次数」字段，改为顶层公共字段）
  - `child/pages/index.tsx` 的 `ChildTasksPage` 和 `ChildHomePage`（按 `canSubmit` 禁用按钮 + tooltip）
  - 共享类型 `shared/api.ts` 或对应 task-assignment / task-template 类型定义

**受影响 API**：

- `POST /api/task-templates`、`PUT /api/task-templates/{id}`：请求体新增三个字段
- `GET /api/task-templates`、`GET /api/task-templates/{id}`：响应体新增三个字段
- `GET /api/task-assignments`：每个 assignment 响应体新增 `canSubmit` + `submissionBlockReason` + 三个 snapshot 字段
- `POST /api/task-review/submissions`：新增 `TASK_SUBMISSION_MAX_REACHED` / `TASK_SUBMISSION_POINTS_CAP_REACHED` 两个错误码

**数据迁移与兼容**：

- Flyway V14 单脚本，含 `task_template` 三列 + `task_assignment` 三列 + STANDING 数据回填
- H2（PostgreSQL 模式）/ MySQL 8+ / PostgreSQL 15+ 兼容
- 既有 STANDING 类型模板的 `type_config.max_submissions` 迁移到 `max_submissions` 一等字段；保留 `type_config` 内字段不动以避免破坏旧客户端
- 不需要重建既有 assignment；新字段在旧 assignment 上以 NULL 形式存在，审核接口读取时回退到 template 当前值（兼容快照语义）

**测试与验证**：

- `TaskTemplateServiceTest`：新增字段校验、STANDING 兼容、默认值
- `TaskAssignmentServiceTest`：snapshot 写入、跨周期生成一致性
- `TaskReviewServiceTest`：max / cap 双前置校验、跨 assignment 统计、错误码
- `PointsServiceTest`：积分聚合查询正确性
- 现有 STANDING 测试需要更新（不再读取 `type_config.max_submissions`）
- 前端 `TaskTypeConfigForms.test.tsx`、`parent/pages/__tests__`、`child/pages.test.tsx` 更新
- E2E：家长配置上限 → 孩子重投达到上限被拒的全链路

**风险**：

- 跨 assignment 统计性能：每个孩子每次访问任务列表都会触发聚合查询；通过 template_id 索引 + child_id 索引覆盖，单次查询应 <10ms
- 数据迁移幂等性：V14 脚本必须是事务原子的，失败必须整体回滚
- BREAKING 影响：旧客户端调用 PUT 接口时如果继续提交 `type_config.max_submissions`，服务端必须忽略或返回 deprecation 警告而非错误
