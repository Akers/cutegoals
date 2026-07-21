---
change: add-task-template-resubmission-controls
design-doc: docs/superpowers/specs/2026-07-20-task-template-resubmission-controls-design.md
base-ref: 2d8d2e0da005cb298238d9e01d51da3603b28bb5
archived-with: 2026-07-21-add-task-template-resubmission-controls
---

# add-task-template-resubmission-controls 实施计划

> **产物语言**: zh-CN
> **关联文档**:
> - 任务边界：`openspec/changes/add-task-template-resubmission-controls/tasks.md`（6 组 / 19 子任务）
> - 技术设计：`docs/superpowers/specs/2026-07-20-task-template-resubmission-controls-design.md`（11 步实施顺序 + 9 个高层决策 D1-D9 + 7 个实现层决策 ID1-ID7）
> - 验收事实源：`openspec/changes/add-task-template-resubmission-controls/specs/task-template/spec.md`、`specs/task-assignment/spec.md`、`specs/task-review/spec.md`
> - 提案文档：`openspec/changes/add-task-template-resubmission-controls/proposal.md`
> **实施顺序**：11 步（Design Doc §10 定义），按 数据库 → 后端实体+Mapper → 后端 Service+Controller → 前端家长端 → 前端孩子端 → E2E+回归 推进
> **测试策略**：Design Doc §7（单元测试 5 类 → 集成测试 3 类 → 迁移回归 1 类 → 前端测试 3 类 → E2E 1 类）

## 计划概览

本计划将 19 项子任务按 Design Doc §10 定义的 11 步实施顺序归并为 **6 个阶段**，每阶段对应 tasks.md 的一个章节或逻辑子集，阶段间存在严格的前置依赖。每个阶段末尾标注可运行的验证命令。

**基线约束**：
- 后端基线 = HEAD `2d8d2e0` 的 `mvn test` = 全部通过
- 前端基线 = `pnpm test` = 全部通过
- tsc 编译必须零错误后方可进入 UI 阶段

**整体依赖图**：
```
Phase 1 (DB 迁移) ──→ Phase 2 (实体+Mapper) ──→ Phase 3 (Service+Controller) ──→ Phase 6 (E2E+回归)
                                                            │
                                                            ├──→ Phase 4 (前端家长端)
                                                            └──→ Phase 5 (前端孩子端)
```

**高层决策引用**（来自 Design Doc §3，本文不重写）：
- **D1**：三个新字段直接落到 `task_template` 表
- **D2**：`task_assignment` 同步固化三个 snapshot 字段
- **D3**：跨 child + template 维度聚合统计（APPROVED 计数 + EARN 积分求和）
- **D4**：两道前置校验在 submit 入口，紧接既有校验之前
- **D5**：稳定错误码 `TASK_SUBMISSION_MAX_REACHED` / `TASK_SUBMISSION_POINTS_CAP_REACHED`
- **D6**：Flyway V14 单脚本（字段 + 索引 + STANDING 回填，不删 `type_config.max_submissions`）
- **D7**：家长端 UI 顶层 Checkbox + 条件 InputNumber
- **D8**：`GET /api/task-assignments` 返回 `canSubmit` + `submissionBlockReason`
- **D9**：NULL snapshot 字段审核期回退读 template 当前值

**实现层决策引用**（来自 Design Doc §4，本文不重写）：
- **ID1**：canSubmit 性能策略 = 批量 IN 查询 + 内存组装（N+1 → 4 SQL）
- **ID2**：并发兜底 = 应用层 `SELECT...FOR UPDATE` 锁 `(child_id, template_id)`
- **ID3**：聚合 SQL 实现 = XML mapper 手写 JOIN SQL（`TaskReviewMapper.xml` + `PointsLedgerMapper.xml`）
- **ID4**：错误响应 schema = 复用项目通用 `ApiErrorResponse`，HTTP 422，MUST NOT 暴露累计值/上限值
- **ID5**：V14 迁移跨方言实现（MySQL `JSON_EXTRACT` / PG&H2 `::jsonb ->>`）
- **ID6**：审核期 NULL snapshot 回退路径（`ResubmissionPolicyEvaluator.evaluate()`）
- **ID7**：`ResubmissionPolicyEvaluator` 共享组件（submit 校验 + canSubmit 列表计算共用）

## 阶段 1：数据库迁移（2 子任务 → tasks.md §1）

**目标**：创建 Flyway 迁移脚本 `V14__add_resubmission_controls.sql`，为 `task_template` 和 `task_assignment` 表加字段，创建复合索引，回填 STANDING 数据。

**前置 verify**：
- ⚡ verify `server/common/src/main/resources/db/migration/` 目录中 `V13` 的编号范围，确认 V14 未使用
- ⚡ verify `task_template` 表现有 `type_config` 列的 JSON 结构（`V10__add_task_type_and_type_config.sql`）
- ⚡ verify `task_assignment` 表既有 snapshot 列命名风格（参考 `V12__add_task_type_snapshot_columns.sql`）

**涉及决策**：D1, D2, D6, ID5

### 任务 1.1：创建 V14 迁移脚本 — 字段新增 + 索引

- **原任务编号**：1.1（tasks.md）
- **capability**：task-template / task-assignment
- **目标**：在 `server/common/src/main/resources/db/migration/` 下创建 `V14__add_resubmission_controls.sql`
- **实现方式**（Design Doc ID5）：
  ```sql
  -- 标准 SQL DDL，三方言通用
  ALTER TABLE task_template
    ADD COLUMN allow_resubmit BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN max_submissions INT NOT NULL DEFAULT 0,
    ADD COLUMN points_cap INT NOT NULL DEFAULT 0;

  ALTER TABLE task_assignment
    ADD COLUMN snapshot_template_allow_resubmit BOOLEAN DEFAULT NULL,
    ADD COLUMN snapshot_template_max_submissions INT DEFAULT NULL,
    ADD COLUMN snapshot_template_points_cap INT DEFAULT NULL;

  CREATE INDEX idx_assignment_child_template
      ON task_assignment (child_id, template_id, id);
  ```
- **输入**：Design Doc ID5 字段定义 + 索引定义
- **输出**：`V14__add_resubmission_controls.sql` 文件（前半段）
- **验收标准**：
  - Flyway 在 H2 PostgreSQL 模式 / MySQL 8+ / PostgreSQL 15+ 上均可成功迁移
  - 迁移幂等可重试（Flyway checksum + 首次迁移即幂等）
  - `idx_assignment_child_template` 复合索引存在
  - 既有 `type_config.max_submissions` 数据不受影响
  - 既有 `task_assignment` 数据不受影响（新 snapshot 列为 NULL）
- **依赖任务**：无
- **运行验证**：
  ```bash
  mvn flyway:migrate -pl :common -am           # MySQL profile
  mvn -pl :common -am test -Dtest=*Migration*  # H2 PG 模式自动迁移
  ```

### 任务 1.2：V14 迁移脚本 — STANDING 数据回填

- **原任务编号**：1.2（tasks.md）
- **capability**：task-template
- **目标**：在 V14 脚本末尾追加 STANDING 模板数据回填 SQL
- **实现方式**（Design Doc ID5 回填约束）：
  - MySQL 方言分支：
    ```sql
    UPDATE task_template
       SET allow_resubmit = TRUE,
           max_submissions = COALESCE(JSON_EXTRACT(type_config, '$.max_submissions'), 0)
     WHERE task_type = 'STANDING'
       AND JSON_EXTRACT(type_config, '$.max_submissions') IS NOT NULL;
    ```
  - PostgreSQL / H2 PostgreSQL 模式方言分支：
    ```sql
    UPDATE task_template
       SET allow_resubmit = TRUE,
           max_submissions = COALESCE((type_config::jsonb ->> 'max_submissions')::int, 0)
     WHERE task_type = 'STANDING'
       AND type_config::jsonb ->> 'max_submissions' IS NOT NULL;
    ```
  - 回填语义（Design Doc ID5 约束）：
    - `allow_resubmit = TRUE`（原 STANDING 配置了 max_submissions 的迁移为启用）
    - `max_submissions = COALESCE(原值, 0)`
    - `points_cap = 0`（原无积分上限概念）
    - 既有 `type_config.max_submissions` **保留不动**
    - 老 assignment 的三 snapshot 字段保持 NULL（D9 回退路径）
- **输入**：Design Doc ID5 方言 SQL + 回填约束
- **输出**：`V14__add_resubmission_controls.sql` 文件（完整内容）
- **验收标准**：
  - 所有 `task_type='STANDING'` 且 `type_config` 有 `max_submissions` 的模板正确回填
  - `type_config.max_submissions` 原值保留不变（兼容旧客户端）
  - 非 STANDING 模板不受影响（默认 allow_resubmit=FALSE, max_submissions=0, points_cap=0）
  - `points_cap` 一律为 0（不限制）
- **依赖任务**：1.1（同一文件，先加字段再回填）
- **运行验证**：
  ```bash
  mvn -pl :common -am test -Dtest=MigrationV14Test
  ```

## 阶段 2：后端实体与 Mapper（4 子任务 → tasks.md §2）

**目标**：在实体类新增字段映射，在 Mapper 层新增 4 个聚合查询方法 + FOR UPDATE 锁查询。

**前置 verify**：
- ⚡ verify V14 脚本已在本地 H2 PostgreSQL 模式下执行成功
- ⚡ verify `TaskTemplate.java` 和 `TaskAssignment.java` 的现有字段命名风格（Lombok `@Data`, `@TableField`）

**涉及决策**：D3, ID1, ID2, ID3

### 任务 2.1：TaskTemplate 实体新增字段

- **原任务编号**：2.1（tasks.md）
- **capability**：task-template
- **目标文件**：`server/common/src/main/java/com/cutegoals/common/entity/task/TaskTemplate.java`
- **实现内容**：
  - 新增 `private Boolean allowResubmit;`
  - 新增 `private Integer maxSubmissions;`
  - 新增 `private Integer pointsCap;`
  - 添加 `@TableField` 注解（如有既有风格）
  - 默认值逻辑：`allowResubmit=false, maxSubmissions=0, pointsCap=0`（与 DDL DEFAULT 一致）
- **输入**：Design Doc D1 + 表字段定义
- **输出**：`TaskTemplate.java` 编译通过
- **验收标准**：
  - 字段序列化/反序列化正确（Jackson JSON）
  - MyBatis-Plus 映射到 V14 新增列
  - 无遗留 `@TableField(exist = false)` 等错误注解
  - 现有单元测试 `TaskTemplateServiceTest` 不因字段新增而失败
- **依赖任务**：1.1（V14 必须先执行）
- **运行验证**：
  ```bash
  mvn -pl :common -am compile
  mvn -pl :common -am test -Dtest=TaskTemplateServiceTest
  ```

### 任务 2.2：TaskAssignment 实体新增 snapshot 字段

- **原任务编号**：2.2（tasks.md）
- **capability**：task-assignment
- **目标文件**：`server/common/src/main/java/com/cutegoals/common/entity/task/TaskAssignment.java`
- **实现内容**：
  - 新增 `private Boolean snapshotTemplateAllowResubmit;`
  - 新增 `private Integer snapshotTemplateMaxSubmissions;`
  - 新增 `private Integer snapshotTemplatePointsCap;`
  - 添加 `@TableField` 注解
  - 与既有 `snapshot_template_*` 字段同命名规范
- **验收标准**：
  - 字段序列化/反序列化正确
  - MyBatis-Plus 映射到 V14 新增 snapshot 列
  - snapshot 字段全部为 `DEFAULT NULL`，不影响既有 assignment 数据
- **依赖任务**：1.1
- **运行验证**：
  ```bash
  mvn -pl :common -am compile
  ```

### 任务 2.3：TaskReviewMapper 新增聚合查询 + FOR UPDATE

- **原任务编号**：2.3（tasks.md）
- **capability**：task-review
- **目标文件**：
  - `server/task-review/src/main/resources/mapper/TaskReviewMapper.xml`
  - `server/task-review/src/main/java/com/cutegoals/taskreview/mapper/TaskReviewMapper.java`（接口方法）
- **实现内容**（Design Doc ID3）：
  - **方法 1**：`countApprovedByTemplateAndChild(long templateId, long childId)` → 单 template 聚合
    ```sql
    SELECT COUNT(r.id)
      FROM task_review r
      JOIN task_attempt t ON r.attempt_id = t.id
      JOIN task_assignment a ON t.assignment_id = a.id
     WHERE a.child_id = #{childId}
       AND a.template_id = #{templateId}
       AND r.decision = 'APPROVED'
    ```
  - **方法 2**：`countApprovedBatch(List<Long> templateIds, long childId)` → 批量聚合（ID1 批量优化）
    ```sql
    SELECT a.template_id AS templateId, COUNT(r.id) AS approvedCount
      FROM task_review r
      JOIN task_attempt t ON r.attempt_id = t.id
      JOIN task_assignment a ON t.assignment_id = a.id
     WHERE a.child_id = #{childId}
       AND r.decision = 'APPROVED'
       AND a.template_id IN <foreach>
     GROUP BY a.template_id
    ```
  - **方法 3**：`lockAssignmentByChildTemplate(long childId, long templateId)` → FOR UPDATE 行锁（ID2）
    ```sql
    SELECT id FROM task_assignment
     WHERE child_id = #{childId} AND template_id = #{templateId}
     ORDER BY template_id
     LIMIT 1
     FOR UPDATE
    ```
  - Mapper 接口方法签名对应上述 XML 的 `id`
  - 返回值类型：`long`（单 template）/ `List<Map<String, Object>>`（批量）/ `Long`（锁）
- **输入**：Design Doc ID3 SQL + ID1 批量优化
- **输出**：`TaskReviewMapper.java` + `TaskReviewMapper.xml` 编译通过
- **验收标准**：
  - `countApprovedByTemplateAndChild` 正确聚合 APPROVED 计数
  - REJECTED 不计数，REFUND 不计入
  - `lockAssignmentByChildTemplate` 在 H2 PG 模式下正确执行 FOR UPDATE
  - 批量查询在 `IN (...)` 40+ 模版 ID 时正常工作
  - `IN` 列表上限 50（ID1 约束）
- **依赖任务**：2.2（实体字段确定命名后写 SQL JOIN）
- **运行验证**：
  ```bash
  mvn -pl :task-review -am test -Dtest=TaskReviewMapperTest
  ```

### 任务 2.4：PointsLedgerMapper 新增积分聚合查询

- **原任务编号**：2.4（tasks.md）
- **capability**：task-review（points 模块）
- **目标文件**：
  - `server/points/src/main/resources/mapper/PointsLedgerMapper.xml`
  - `server/points/src/main/java/com/cutegoals/points/mapper/PointsLedgerMapper.java`（接口方法）
- **实现内容**（Design Doc ID3）：
  - **方法 1**：`sumEarnByTemplateAndChild(long templateId, long childId)` → 单 template
    ```sql
    SELECT COALESCE(SUM(l.amount), 0)
      FROM points_ledger l
      JOIN task_attempt t ON l.business_ref = CONCAT('ATTEMPT_', t.id)
      JOIN task_assignment a ON t.assignment_id = a.id
     WHERE a.child_id = #{childId}
       AND a.template_id = #{templateId}
       AND l.type = 'EARN'
    ```
  - **方法 2**：`sumEarnBatch(List<Long> templateIds, long childId)` → 批量（ID1）
    ```sql
    SELECT a.template_id AS templateId, COALESCE(SUM(l.amount), 0) AS earnedPoints
      FROM points_ledger l
      JOIN task_attempt t ON l.business_ref = CONCAT('ATTEMPT_', t.id)
      JOIN task_assignment a ON t.assignment_id = a.id
     WHERE a.child_id = #{childId}
       AND l.type = 'EARN'
       AND a.template_id IN <foreach>
     GROUP BY a.template_id
    ```
  - 跨方言约束（ID3）：仅用 `CONCAT` / `COALESCE` / `CASE WHEN` / `SUM` / `COUNT`，不用专属 JSON 操作符
- **输入**：Design Doc ID3 SQL + 积分流水结构
- **输出**：`PointsLedgerMapper.java` + `PointsLedgerMapper.xml` 编译通过
- **验收标准**：
  - `business_ref = CONCAT('ATTEMPT_', attempt_id)` JOIN 链路正确
  - `type='EARN'` 过滤正确，REFUND 不扣减
  - 跨 assignment 累加正确
  - 无 assignment 记录时返回 0（`COALESCE` 兜底）
- **依赖任务**：2.2
- **运行验证**：
  ```bash
  mvn -pl :points -am test -Dtest=PointsLedgerMapperTest
  ```

## 阶段 3：后端 Service 与 Controller（6 子任务 → tasks.md §3）

**目标**：实现 `ResubmissionPolicyEvaluator` 组件，修改 Service 层校验 + 快照写入 + canSubmit 计算，清理旧 `parseMaxSubmissions` 逻辑。

**前置 verify**：
- ⚡ verify `TaskTemplateService.java` line 731-755 附近 `parseMaxSubmissions` 方法的精确位置
- ⚡ verify `TaskReviewService.java` line 189 / 465 附近 `parseMaxSubmissions` 调用位置 (已确认存在)
- ⚡ verify TaskAssignmentService 中创建/批量/周期生成三条路径（grep `createAssignment`, `batchCreate`, `generateRecurring`）

**涉及决策**：D4, D5, D8, D9, ID4, ID6, ID7

### 任务 3.1：TaskTemplateService 字段校验 + 移除旧逻辑

- **原任务编号**：3.1（tasks.md）
- **capability**：task-template
- **目标文件**：`server/task/src/main/java/com/cutegoals/task/service/TaskTemplateService.java`
- **实现内容**：
  - 在创建模板的请求解析路径上，从 `Map<String, Object>` 中读取 `allow_resubmit` / `max_submissions` / `points_cap`
  - 字段校验规则（Design Doc D1 + spec 约束）：
    - `allow_resubmit` 缺省时取 `FALSE`
    - `max_submissions` 缺省或 `0` 时取 `0`；非负整数且 `<= 10000`
    - `points_cap` 缺省或 `0` 时取 `0`；非负整数且 `<= 100000000`
    - 越界时返回 `TASK_TEMPLATE_VALIDATION_FAILED` + 字段错误
  - **移除**：`parseMaxSubmissions` 整段逻辑（line 731-755 附近），不再从 `type_config` 读取 `max_submissions`
  - 更新模板路径：接受 `type_config.max_submissions` 但忽略其值（旧客户端兼容，spec Scenario 旧客户端兼容）
- **输入**：Design Doc D1 + spec task-template（默认值、边界值、旧客户端兼容）
- **输出**：`TaskTemplateService.java` 修改完毕
- **验收标准**：
  - 新模板创建时三字段正确持久化
  - 越界值返回 `TASK_TEMPLATE_VALIDATION_FAILED`
  - STANDING 类型不再报错 `"STANDING typeConfig requires max_submissions"`
  - 旧客户端 PUT 提交 `type_config.max_submissions` 不影响一等字段
- **依赖任务**：2.1（实体字段就绪）
- **运行验证**：
  ```bash
  mvn -pl :task -am test -Dtest=TaskTemplateServiceTest
  ```

### 任务 3.2：TaskAssignmentService snapshot 写入

- **原任务编号**：3.2（tasks.md）
- **capability**：task-assignment
- **目标文件**：`server/task/src/main/java/com/cutegoals/task/service/TaskAssignmentService.java`
- **实现内容**：
  - 在单次创建、批量创建、周期生成三条路径上，从 `task_template` 读取 `allow_resubmit` / `max_submissions` / `points_cap`，写入 `TaskAssignment` 的对应 snapshot 字段
  - 与既有 `snapshot_template_*` 快照字段同生命周期（分配创建时固化，后续不动）
- **输入**：Design Doc D2
- **输出**：`TaskAssignmentService.java` 修改完毕
- **验收标准**：
  - 单次分配正确写入三个 snapshot 字段
  - 批量分配正确写入
  - 周期生成正确写入
  - 模板后续修改不影响已生成分配的 snapshot
  - 新快照字段与既有 snapshot 字段在同一事务写入
- **依赖任务**：2.1（实体字段就绪）
- **运行验证**：
  ```bash
  mvn -pl :task -am test -Dtest=TaskAssignmentServiceTest
  ```

### 任务 3.3：Controller DTO 更新

- **原任务编号**：3.3（tasks.md）
- **capability**：task-template / task-assignment
- **目标文件**：
  - `server/task/src/main/java/com/cutegoals/task/controller/TaskTemplateController.java`（请求体通过 `Map<String, Object>` 处理，需在 Service 层解析新字段）
  - `server/task/src/main/java/com/cutegoals/task/service/TaskTemplateService.java`（`getTemplateDetail` 返回 Map 需包含三字段）
  - `server/task/src/main/java/com/cutegoals/task/controller/TaskAssignmentController.java`（返回 Map 需包含 `canSubmit` + `submissionBlockReason` + 三 snapshot 字段）
  - 注意：本项目使用 `Map<String, Object>` 而非 typed DTO（已验证 `TaskTemplateController.java`），所以 DTO 变更为在 Service 层组装 Map 时追加字段
- **实现内容**：
  - **TaskTemplate 响应**：`getTemplateDetail` 返回的 Map 中追加 `allowResubmit` / `maxSubmissions` / `pointsCap`
  - **TaskAssignment 响应**：assignment 返回的 Map 中追加 `snapshotTemplateAllowResubmit` / `snapshotTemplateMaxSubmissions` / `snapshotTemplatePointsCap` / `canSubmit` / `submissionBlockReason`
- **输入**：Design Doc §5 「受影响 API」表 + D8
- **输出**：Controller + Service 组装逻辑修改完毕
- **验收标准**：
  - `GET /api/task-templates` 响应包含三个新字段
  - `GET /api/task-assignments` 每个 assignment 包含三 snapshot + `canSubmit` + `submissionBlockReason`
  - 字段命名使用 camelCase（与项目既有风格一致）
- **依赖任务**：3.1, 3.2
- **运行验证**：
  ```bash
  mvn -pl :task -am test -Dtest=*Controller*
  ```

### 任务 3.4：TaskReviewService submit 入口前置校验

- **原任务编号**：3.4（tasks.md）
- **capability**：task-review
- **目标文件**：`server/task-review/src/main/java/com/cutegoals/taskreview/service/TaskReviewService.java`
- **实现内容**：
  - 在 `submitAttempt` 方法的「状态合法 / 迟交策略 / 幂等」校验**之前**插入前置校验块（D4）
  - 前置校验流程：
    1. 调用 `lockAssignmentByChildTemplate(childId, templateId)` 获取行锁（ID2）
    2. 检查 `snapshot_template_allow_resubmit`：若为 `false` 或 `null` 则跳过（D9 回退路径由 `ResubmissionPolicyEvaluator` 处理）
    3. 若 `true`，调用 `countApprovedByTemplateAndChild` + `sumEarnByTemplateAndChild` 聚合统计
    4. 调用 `ResubmissionPolicyEvaluator.evaluate()` 做决策
    5. 若 blocked 返回 422 + 对应错误码（ID4）
  - 错误响应复用 `ApiErrorResponse`（ID4）：
    ```json
    { "code": "TASK_SUBMISSION_MAX_REACHED", "message": "已达到最大提交次数", ... }
    ```
  - 错误响应 MUST NOT 包含当前累计值/上限值（ID4 约束）
- **输入**：Design Doc D4, D5, ID2, ID4, ID6, ID7
- **输出**：`TaskReviewService.java` 修改完毕
- **验收标准**：
  - 默认 `allow_resubmit=false` 时不应用校验（既有行为保持）
  - `max_submissions=0` 时不限制（ID6 规则）
  - `points_cap=0` 时不限制
  - 达 max 上限返回 `TASK_SUBMISSION_MAX_REACHED`
  - 达 cap 上限返回 `TASK_SUBMISSION_POINTS_CAP_REACHED`
  - 跨 assignment 聚合（REPEAT 多期 + LIMITED 多 assignment）
  - REJECTED 不计数，REFUND 不减 cap
  - FOR UPDATE 行锁在事务内生效
- **依赖任务**：2.3, 2.4, 3.5（ResubmissionPolicyEvaluator 组件）
- **运行验证**：
  ```bash
  mvn -pl :task-review -am test -Dtest=TaskReviewServiceTest
  ```

### 任务 3.5：ResubmissionPolicyEvaluator 共享组件（新文件）

- **原任务编号**：3.4 的组件提取（Design Doc ID7 要求共享）
- **capability**：task-review
- **目标文件**（新文件）：`server/task-review/src/main/java/com/cutegoals/taskreview/service/ResubmissionPolicyEvaluator.java`
- **实现内容**（Design Doc ID6, ID7）：
  ```java
  public class ResubmissionPolicyEvaluator {

      public ResubmissionDecision evaluate(TaskAssignment assignment,
                                            long approvedCount, long earnedPoints) {
          Boolean allowResubmit = assignment.getSnapshotTemplateAllowResubmit();
          Integer maxSubmissions = assignment.getSnapshotTemplateMaxSubmissions();
          Integer pointsCap = assignment.getSnapshotTemplatePointsCap();

          // D9 回退：V14 之前老 assignment snapshot 为 NULL，读 template 当前值
          if (allowResubmit == null) {
              // caller 负责准备 template 当前值
              return evaluateWithTemplateValues(...);
          }

          if (Boolean.FALSE.equals(allowResubmit)) {
              return ResubmissionDecision.allowed();
          }

          // max 校验
          if (maxSubmissions != null && maxSubmissions > 0
              && approvedCount >= maxSubmissions) {
              return ResubmissionDecision.blocked("TASK_SUBMISSION_MAX_REACHED",
                  "已达到最大提交次数");
          }

          // cap 校验需在 caller 侧传入 earnedPoints + rewardPoints
          // 在 evaluate 内完成 "earnedPoints + rewardPoints > pointsCap" 比较

          return ResubmissionDecision.allowed();
      }
  }
  ```
  - 包含 `ResubmissionDecision` 内部类：`allowed()` / `blocked(code, message)`
  - 被 `TaskReviewService.submitAttempt` 和 `TaskAssignmentService.listByChild` 共享调用（ID7）
- **输入**：Design Doc ID6, ID7
- **输出**：`ResubmissionPolicyEvaluator.java` 编译通过
- **验收标准**：
  - 单元测试覆盖 max/cap 双校验、NULL snapshot 回退、allow_resubmit=false 不校验
  - max=0 不限制、cap=0 不限制
  - cap 边界值（95+5≤100 通过 vs 95+10>100 拒绝）
- **依赖任务**：2.1, 2.2（实体就绪）
- **运行验证**：
  ```bash
  mvn -pl :task-review -am test -Dtest=ResubmissionPolicyEvaluatorTest
  ```

### 任务 3.6：清理 `parseMaxSubmissions` 遗留路径

- **原任务编号**：3.6（tasks.md）
- **capability**：task-template / task-review
- **目标文件**：
  - `server/task-review/src/main/java/com/cutegoals/taskreview/service/TaskReviewService.java` line 189 和 465 处调用
  - `server/task-review/src/main/java/com/cutegoals/taskreview/service/TaskReviewService.java` line 893 处 `parseMaxSubmissions` 方法定义
  - `server/task/src/main/java/com/cutegoals/task/service/TaskTemplateService.java` line 731-755 附近（已在 3.1 中移除）
- **实现内容**：
  - 删除 `TaskReviewService.parseMaxSubmissions()` 方法
  - 删除 `TaskReviewService.submitAttempt` 中对 `parseMaxSubmissions` 的调用（2 处）
  - 替换为从 `ResubmissionPolicyEvaluator` 读取 snapshot 或 template 当前值
  - 删除相关测试辅助方法
- **输入**：Design Doc §5（受影响代码路径）
- **输出**：`TaskReviewService.java` 修改完毕
- **验收标准**：
  - `grep "parseMaxSubmissions" server/` 返回空
  - `grep "type_config.*max_submissions" server/` 返回空（不含 migration 脚本中的回填 SQL）
  - 既有 STANDING 测试改为基于一等字段
- **依赖任务**：3.4, 3.5
- **运行验证**：
  ```bash
  rg -r "" "parseMaxSubmissions" server/  # 确认无残留
  mvn -pl :task-review :task -am test        # 回归
  ```

## 阶段 4：前端家长端（3 子任务 → tasks.md §4）

**目标**：在家长端模板编辑表单新增三个顶层字段，STANDING 子表单移除旧字段，API 类型同步。

**前置 verify**：
- ⚡ verify `ParentTemplatesPage.tsx` 表单中模板编辑部分的渲染逻辑
- ⚡ verify `StandingConfigForm` 在 `TaskTypeConfigForms.tsx` 中的精确行范围（line 240-303）
- ⚡ verify 家长端 API 类型定义位置（`web/src/parent/api.ts` 或 `web/src/shared/api.ts`）

**涉及决策**：D7

### 任务 4.1：ParentTemplatesPage 表单新增顶层字段

- **原任务编号**：4.1（tasks.md）
- **capability**：task-template
- **目标文件**：`web/src/parent/pages/ParentTemplatesPage.tsx`
- **实现内容**：
  - 在模板编辑表单**顶层**（独立于具体 task type 子表单）新增：
    - Checkbox：`允许重复提交`（`allow_resubmit`，默认 `false`）
    - 条件渲染（勾选后显示）：
      - InputNumber：`最大提交次数`（`max_submissions`，min=0, max=10000, 默认 0，提示 `0 = 不限制`）
      - InputNumber：`积分上限`（`points_cap`，min=0, max=100000000, 默认 0，提示 `0 = 不限制`）
  - 字段值绑定到提交 payload 中的 `allow_resubmit` / `max_submissions` / `points_cap`
- **输入**：Design Doc D7 + spec task-template（默认值、范围）
- **输出**：`ParentTemplatesPage.tsx` 修改完毕
- **验收标准**：
  - 默认不勾选「允许重复提交」，不显示 max/cap 输入框
  - 勾选后 max/cap InputNumber 出现，默认值 0
  - 字段提交到后端对应接口
  - 保存后数据回显正确
- **依赖任务**：3.1（后端字段校验就绪）
- **运行验证**：
  ```bash
  cd web && pnpm test -- --testPathPattern="parent-save-dialogs"
  ```

### 任务 4.2：TaskTypeConfigForms STANDING 子表单移除旧字段

- **原任务编号**：4.2（tasks.md）
- **capability**：task-template
- **目标文件**：`web/src/parent/components/TaskTypeConfigForms.tsx`
- **实现内容**：
  - 从 `StandingConfigForm` 组件移除「无限提交」Checkbox 与「最大提交次数」InputNumber（line 240-303 附近）
  - 保留 STANDING 类型子表单的其他说明类信息（如频率配置等）
  - `TypeConfigValue.max_submissions` 在新模型下不再使用（保留 `type_config` 中字段不动但前端不再编辑）
- **输入**：Design Doc D7（STANDING 子表单移除逻辑）
- **输出**：`TaskTypeConfigForms.tsx` 修改完毕
- **验收标准**：
  - STANDING 子表单不再显示 max_submissions 相关字段
  - 非 STANDING 子表单不受影响
  - 现有 STANDING 模板在编辑器中加载正常（旧 type_config.max_submissions 数据保持只读）
- **依赖任务**：4.1
- **运行验证**：
  ```bash
  cd web && pnpm test -- --testPathPattern="TaskTypeConfigForms"
  ```

### 任务 4.3：家长端 API 类型定义更新

- **原任务编号**：4.3（tasks.md）
- **capability**：task-template
- **目标文件**：`web/src/parent/api.ts`（或项目中对应类型定义文件，需确认具体路径）
- **实现内容**：
  - `TaskTemplate` 接口/类型新增：
    - `allow_resubmit: boolean`
    - `max_submissions: number`
    - `points_cap: number`
  - `TaskTemplatePayload` 接口/类型同步
- **输入**：Design Doc §5（前端 API 类型）
- **输出**：类型定义文件修改完毕
- **验收标准**：
  - TypeScript 类型检查通过（`tsc --noEmit` 无错误）
  - 前端构建无错误
- **依赖任务**：4.1（类型与表单绑定）
- **运行验证**：
  ```bash
  cd web && pnpm type-check  # 或 tsc --noEmit
  ```

## 阶段 5：前端孩子端（2 子任务 → tasks.md §5）

**目标**：孩子端任务列表读取 `canSubmit`，false 时禁用提交按钮并显示 tooltip，API 类型同步。

**前置 verify**：
- ⚡ verify `ChildTasksPage` 和 `ChildHomePage` 在 `index.tsx` 中的渲染逻辑（line 190+ 和 line 104+）
- ⚡ verify 孩子端 API 类型定义位置（`web/src/child/api.ts`）

**涉及决策**：D8, ID4

### 任务 5.1：孩子端页面 canSubmit 处理

- **原任务编号**：5.1（tasks.md）
- **capability**：task-review
- **目标文件**：`web/src/child/pages/index.tsx`
- **实现内容**：
  - `ChildTasksPage`（line 190+）：
    - 读取每个 assignment 的 `canSubmit` 字段
    - `canSubmit=false` 时禁用「提交」/「重新提交」按钮
    - 根据 `submissionBlockReason` 设置 tooltip：
      - `'MAX_REACHED'` → `"已达到最大提交次数"`
      - `'POINTS_CAP_REACHED'` → `"已达到积分上限"`
    - 按钮视觉灰色样式（disabled）
  - `ChildHomePage`（line 104+）：
    - 同步处理 `canSubmit` 逻辑（同上）
  - 当 `canSubmit=true` 或 `submissionBlockReason=null` 时行为保持原样
- **输入**：Design Doc D8 + ID4（错误信息不暴露数值）
- **输出**：`web/src/child/pages/index.tsx` 修改完毕
- **验收标准**：
  - `canSubmit=false, reason=MAX_REACHED` → 按钮 disabled + tooltip「已达到最大提交次数」
  - `canSubmit=false, reason=POINTS_CAP_REACHED` → 按钮 disabled + tooltip「已达到积分上限」
  - `canSubmit=true` → 按钮正常可用
  - tooltip 不包含数值信息
- **依赖任务**：3.4, 3.5（后端可以 submit + canSubmit 逻辑就绪）
- **运行验证**：
  ```bash
  cd web && pnpm test -- --testPathPattern="pages.test"
  ```

### 任务 5.2：孩子端 API 类型定义更新

- **原任务编号**：5.2（tasks.md）
- **capability**：task-review
- **目标文件**：`web/src/child/api.ts`（或对应类型定义文件）
- **实现内容**：
  - `ChildAssignment` 接口/类型新增：
    - `canSubmit: boolean`
    - `submissionBlockReason: 'MAX_REACHED' | 'POINTS_CAP_REACHED' | null`
    - `snapshot_template_allow_resubmit: boolean | null`
    - `snapshot_template_max_submissions: number | null`
    - `snapshot_template_points_cap: number | null`
- **输入**：Design Doc D8 + §5（前端 API 类型）
- **输出**：类型定义文件修改完毕
- **验收标准**：
  - TypeScript 类型检查通过（`tsc --noEmit` 无错误）
  - `submissionBlockReason` 联合类型正确
  - snapshot 字段可为 `null`（D9 兼容）
- **依赖任务**：5.1
- **运行验证**：
  ```bash
  cd web && pnpm type-check
  ```

## 阶段 6：集成测试、迁移回归与 E2E（4 子任务 → tasks.md §6）

**目标**：通过集成测试验证端到端场景，迁移回归验证 V14 脚本正确性，E2E 验证全链路，全仓回归确无残留。

**前置 verify**：
- ⚡ verify `mvn test` 基线全部通过
- ⚡ verify `pnpm test` 基线全部通过
- ⚡ verify web/e2e/ 目录结构和测试框架

**涉及决策**：D1-D9, ID1-ID7（全面验证）

### 任务 6.1：集成测试 TaskReviewResubmissionControlIT

- **原任务编号**：6.1（tasks.md）
- **capability**：task-review
- **目标文件**（新文件）：`server/task-review/src/test/java/.../TaskReviewResubmissionControlIT.java`
- **实现内容**（Design Doc §7.2 集成测试）：
  - 端到端 6 场景：
    1. **默认不启用**：allow_resubmit=false → 提交正常，无校验
    2. **max=0 不限制**：多提交通过
    3. **cap=0 不限制**：多提交通过
    4. **达 max 拒绝**：3 次 APPROVED → 第 4 次 422 `TASK_SUBMISSION_MAX_REACHED`
    5. **达 cap 拒绝**：累计 95 分 + 10 分 snapshot → 422 `TASK_SUBMISSION_POINTS_CAP_REACHED`
    6. **跨 assignment 聚合**：REPEAT 类型多期 assignment，跨期统计
  - 使用 Spring Boot `@SpringBootTest` + H2 PostgreSQL 模式
  - 准备 fixture 数据 → 执行 submit → 验证结果
- **输入**：Design Doc §7.2 集成测试场景
- **输出**：`TaskReviewResubmissionControlIT.java` 通过
- **验收标准**：
  - 6 个场景全部通过
  - 并发场景由 6.3（ConcurrentSubmitResubmissionIT）专门覆盖
  - `TaskAssignmentListCanSubmitIT` 验证列表与 submit 结果一致
- **依赖任务**：3.4, 3.5
- **运行验证**：
  ```bash
  mvn -pl :task-review -am test -Dtest=TaskReviewResubmissionControlIT
  ```

### 任务 6.2：迁移回归测试 MigrationV14Test

- **原任务编号**：6.2（tasks.md）
- **capability**：task-template / common
- **目标文件**（新文件）：`server/common/src/test/java/.../MigrationV14Test.java`
- **实现内容**（Design Doc §7.3 迁移回归）：
  - 准备 V13 阶段的 STANDING fixture 数据（含 `type_config.max_submissions`）
  - 执行 Flyway V14 迁移
  - 验证：
    - `task_template` 三字段值映射正确
    - `task_assignment.snapshot_template_*` 为 NULL（旧 assignment 不回填）
    - `idx_assignment_child_template` 存在
    - 新创建的 assignment 正确写入 snapshot
    - 原 `type_config.max_submissions` 保留不变
  - 使用 `@FlywayTest` 或手动 Flyway 控制
- **输入**：Design Doc §7.3 迁移回归测试 + ID5
- **输出**：`MigrationV14Test.java` 通过
- **验收标准**：
  - STANDING 回填数据准确性验证
  - 索引存在性验证
  - 旧 assignment snapshot 为 NULL 验证
  - 新 assignment snapshot 正确写入验证
- **依赖任务**：1.1, 1.2
- **运行验证**：
  ```bash
  mvn -pl :common -am test -Dtest=MigrationV14Test
  ```

### 任务 6.3：E2E 测试更新

- **原任务编号**：6.3（tasks.md）
- **capability**：task-template / task-assignment / task-review
- **目标文件**：`web/e2e/` 下的测试文件（需确认具体 E2E 框架和目录结构）
- **实现内容**（Design Doc §7.5 E2E）：
  - 新增全链路场景：
    1. 家长创建模板并配置 `allow_resubmit=true, max_submissions=3`
    2. 家长创建分配
    3. 孩子提交 3 次，每次审核 APPROVED
    4. 列表显示 canSubmit=false, MAX_REACHED
    5. 孩子强行提交 → 422 `TASK_SUBMISSION_MAX_REACHED`
  - 可选的积分上限等价场景
- **输入**：Design Doc §7.5 E2E
- **输出**：E2E 测试文件修改完毕并通过
- **验收标准**：
  - 全链路通过
  - 前端按钮 disabled + tooltip 可见
- **依赖任务**：4.1, 4.2, 5.1
- **运行验证**：
  ```bash
  cd web && pnpm e2e  # 或对应 E2E 命令
  ```

### 任务 6.4：全仓回归

- **原任务编号**：6.4（tasks.md）
- **capability**：全仓
- **实现内容**：
  1. 运行 `mvn test` 后端全部测试通过
  2. 运行 `pnpm test` 前端全部测试通过
  3. grep 全仓确认 `parseMaxSubmissions` 和 `type_config.max_submissions` 读取路径无残留（migration 中回填 SQL 的引用除外）
  4. grep 确认 `web/src/parent/components/TaskTypeConfigForms.tsx` 无残留旧字段
- **输入**：Design Doc §5 受影响代码路径完整清单
- **验收标准**：
  - 后端全部测试通过
  - 前端全部测试通过
  - `rg -r "" "parseMaxSubmissions" .` 返回空
  - `rg "type_config.max_submissions" server/ --include "*.java"` 返回空
  - TypeScript 编译零错误
- **依赖任务**：1-5 全部完成
- **运行验证**：
  ```bash
  mvn test && pnpm test
  rg "parseMaxSubmissions" . --type java
  rg "type_config.*max_submissions" server/ --type java
  ```

## 测试策略汇总

| 阶段 | 测试类型 | 测试类 | 覆盖点 |
|------|----------|--------|--------|
| 1 | 迁移回归 | `MigrationV14Test` | STANDING 回填、索引存在性、旧 assignment NULL snapshot |
| 2 | 单元测试 | `TaskReviewMapperTest` | APPROVED 计数、REJECTED 过滤、批量 IN 查询 |
| 2 | 单元测试 | `PointsLedgerMapperTest` | EARN 求和、business_ref JOIN、REFUND 过滤 |
| 3 | 单元测试 | `TaskTemplateServiceTest` | 字段校验、默认值、范围边界、旧客户端兼容 |
| 3 | 单元测试 | `TaskAssignmentServiceTest` | 三条路径 snapshot 写入、后续修改不回溯 |
| 3 | 单元测试 | `ResubmissionPolicyEvaluatorTest` | max/cap 校验、NULL snapshot 回退、边界值 |
| 3 | 单元测试 | `TaskReviewServiceTest` | 6 核心场景 + submit 前置校验 |
| 3 | 集成测试 | `TaskAssignmentListCanSubmitIT` | 列表 canSubmit 与 submit 结果一致 |
| 6 | 集成测试 | `TaskReviewResubmissionControlIT` | 端到端 6 场景 |
| 6 | 集成测试 | `ConcurrentSubmitResubmissionIT` | FOR UPDATE 并发兜底 |
| 4 | 前端测试 | `parent-save-dialogs.test.tsx` | Checkbox 条件渲染、字段提交 |
| 4 | 前端测试 | `TaskTypeConfigForms.test.tsx` | STANDING 子表单无旧字段 |
| 5 | 前端测试 | `pages.test.tsx`（孩子端） | canSubmit 按钮状态、tooltip 文案 |
| 6 | E2E | `web/e2e/` | 全链路家长→孩子→上限→拒绝 |

## 受影响代码路径完整清单

| 路径 | 变更类型 | 涉及任务 |
|------|----------|----------|
| `server/common/src/main/resources/db/migration/V14__add_resubmission_controls.sql` | **新增** | 1.1, 1.2 |
| `server/common/src/main/java/com/cutegoals/common/entity/task/TaskTemplate.java` | 修改 +3 字段 | 2.1 |
| `server/common/src/main/java/com/cutegoals/common/entity/task/TaskAssignment.java` | 修改 +3 字段 | 2.2 |
| `server/task-review/src/main/resources/mapper/TaskReviewMapper.xml` | 修改 +4 查询 | 2.3 |
| `server/task-review/src/main/java/com/cutegoals/taskreview/mapper/TaskReviewMapper.java` | 修改 +3 方法 | 2.3 |
| `server/points/src/main/resources/mapper/PointsLedgerMapper.xml` | 修改 +2 查询 | 2.4 |
| `server/points/src/main/java/com/cutegoals/points/mapper/PointsLedgerMapper.java` | 修改 +2 方法 | 2.4 |
| `server/task/src/main/java/com/cutegoals/task/service/TaskTemplateService.java` | 修改字段校验 + 删旧逻辑 | 3.1 |
| `server/task/src/main/java/com/cutegoals/task/service/TaskAssignmentService.java` | 修改三条路径写 snapshot | 3.2 |
| `server/task/src/main/java/com/cutegoals/task/controller/TaskTemplateController.java` | 无修改（Map 方式由 Service 层处理） | 3.3 |
| `server/task/src/main/java/com/cutegoals/task/controller/TaskAssignmentController.java` | 修改返回 Map 组装 | 3.3 |
| `server/task-review/src/main/java/com/cutegoals/taskreview/service/ResubmissionPolicyEvaluator.java` | **新增** | 3.5 |
| `server/task-review/src/main/java/com/cutegoals/taskreview/service/TaskReviewService.java` | 修改前置校验 + 删旧逻辑 | 3.4, 3.6 |
| `web/src/parent/pages/ParentTemplatesPage.tsx` | 修改表单 +3 字段 | 4.1 |
| `web/src/parent/components/TaskTypeConfigForms.tsx` | 修改移除旧字段 | 4.2 |
| `web/src/parent/api.ts`（或对应类型文件） | 修改 +3 类型字段 | 4.3 |
| `web/src/child/pages/index.tsx`（ChildTasksPage + ChildHomePage） | 修改 canSubmit 逻辑 | 5.1 |
| `web/src/child/api.ts`（或对应类型文件） | 修改 +5 类型字段 | 5.2 |
| `web/e2e/` | 新增场景 | 6.3 |

## 实施顺序建议

```
Phase 1 (DB 迁移: 1.1 → 1.2)
    ↓
Phase 2 (实体+Mapper: 2.1 → 2.2 → 2.3,2.4 可并行)
    ↓
Phase 3 (Service+Controller: 3.5 → 3.1 → 3.2 → 3.3 → 3.4 → 3.6)
    ↙                  ↘
Phase 4 (前端家长: 4.3 → 4.1 → 4.2)     Phase 5 (前端孩子: 5.2 → 5.1)
    ↙                  ↘
Phase 6 (E2E+回归: 6.1 → 6.2 → 6.3 → 6.4)
```

每一阶段完成后建议立即运行对应的验证命令。阶段之间可并行（Phase 4 和 Phase 5 可同时开始），但每阶段内部子任务按编号顺序执行。
