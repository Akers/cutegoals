---
comet_change: add-task-template-resubmission-controls
role: technical-design
canonical_spec: openspec
---

# Design Doc: 任务模板重复提交控制端到端落地

> 本文件是 OpenSpec change `add-task-template-resubmission-controls` 的深度技术 Design Doc。OpenSpec delta spec（`openspec/changes/add-task-template-resubmission-controls/specs/*/spec.md`）是能力规格的事实源；本文件在其之上展开实现层细节、技术风险与测试策略。

## 1. Context

CuteGoals 2.0 当前任务模板（LIMITED / REPEAT / STANDING）只有 STANDING 通过 `type_config.max_submissions` 表达「单 assignment 内最大提交次数」，存在两个产品空白：

1. **跨周期不可控**：REPEAT / LIMITED 完全没有上限保护，孩子通过同一模板的多期 assignment 可累计无限积分。
2. **维度单一**：现有 `max_submissions` 只在「同一 assignment 内」生效（STANDING 单 assignment 多次重投），无法表达「积分维度上限」。

任务审核与积分流水已有可观察基础：每条 APPROVED 审核会在同一事务内创建 `points_ledger` 流水，业务引用 `business_ref = "ATTEMPT_<attemptId>"`；通过 `task_attempt.assignment_id → task_assignment.template_id` 可反查聚合。本变更把三个新属性提升为 task-template 一等字段并在 task-assignment 上固化快照，让校验逻辑跨周期、跨 assignment 强制执行。

受影响核心不变量：

- 积分流水不可变 → 通过 JOIN 流水聚合统计，不破坏现有写入路径
- 审核幂等 → 新校验仅作为 submit 前置，不影响审核决定路径
- 任务分配快照固化 → 新增三个 snapshot 字段与现有 `snapshot_template_*` 同源同生命周期

## 2. Goals / Non-Goals

**Goals**：

- 把 `allow_resubmit` / `max_submissions` / `points_cap` 提升为 `task_template` 一等字段，在数据库、DTO、Service、前端表单上端到端落地
- 在 `task_assignment` 上固化三个对应快照字段，保证审核期校验与模板后续修改解耦
- 在 `POST /api/task-review/submissions` 接口前置两道校验，分别覆盖「已通过次数」与「累计积分」维度，返回稳定错误码
- 在 `GET /api/task-assignments` 响应中暴露 `canSubmit` / `submissionBlockReason`，让孩子端 UI 可以表达「已达上限」状态
- 通过 Flyway V14 一次性迁移所有 STANDING 模板的旧 `type_config.max_submissions` 到新字段，零手工操作
- 解决跨 assignment 聚合的性能与并发问题（批量查询 + FOR UPDATE 锁）

**Non-Goals**：

- 不修改任务审核决定路径（approve / reject 内部逻辑保持不变）
- 不修改积分账户余额计算逻辑（仅新增只读聚合查询）
- 不修改任务模板的分类、难度、重复规则、任务类型本身
- 不引入新的审计表（沿用 task_template audit log 与 task_review attempt history）
- 不为其他类型（PRIZE / BLIND_BOX / EXCHANGE）添加积分上限保护
- 不为「积分上限已达」提供家长手动重置入口（家长通过编辑模板 `points_cap` 字段即可调整）
- 不实现「跨家庭」语义（MVP 每实例一个家庭不变）
- 不实现运营级「重置上限」功能（未来需求出现时再加 capability）

## 3. 高层架构决策（D1–D9，源自 open 阶段 design.md）

### D1：三个新字段直接落到 task_template 表

不引入子表，三列 `allow_resubmit BOOLEAN`、`max_submissions INT`、`points_cap INT` 直接落 `task_template` 主表。

### D2：task_assignment 同步固化三个 snapshot 字段

`snapshot_template_allow_resubmit`、`snapshot_template_max_submissions`、`snapshot_template_points_cap`，与现有 `snapshot_template_*` 同生命周期。

### D3：跨 child + template 维度聚合统计

- **已通过次数** = `task_review JOIN task_attempt ON review.attempt_id = attempt.id JOIN task_assignment ON attempt.assignment_id = assignment.id` 中 `assignment.template_id = ? AND assignment.child_id = ? AND review.decision = 'APPROVED'` 的行数
- **累计获得积分** = `points_ledger JOIN task_attempt ON ledger.business_ref = CONCAT('ATTEMPT_', attempt.id) JOIN task_assignment ON attempt.assignment_id = assignment.id` 中 `assignment.template_id = ? AND assignment.child_id = ? AND ledger.type = 'EARN'` 的 `SUM(amount)`

### D4：两道前置校验在 submit 入口

在 `POST /api/task-review/submissions` 既有「状态合法 / 迟交策略 / 幂等」校验**之前**插入两道前置校验。

### D5：稳定错误码

`TASK_SUBMISSION_MAX_REACHED` / `TASK_SUBMISSION_POINTS_CAP_REACHED`，统一 `TASK_SUBMISSION_*` 前缀族。

### D6：Flyway V14 单脚本

含 `task_template` + `task_assignment` 字段新增、`idx_assignment_child_template` 索引、STANDING 数据回填（不删 `type_config.max_submissions` 兼容旧客户端）。

### D7：家长端 UI 改造

顶层 Checkbox「允许重复提交」（默认不勾选）+ 条件显示 max/cap InputNumber；STANDING 子表单移除「最大提交次数」字段。

### D8：列表响应暴露 canSubmit

`GET /api/task-assignments` 返回 `canSubmit: boolean` + `submissionBlockReason`，与 submit 校验完全相同聚合口径。

### D9：NULL snapshot 字段审核期回退

V14 之前老 assignment 的 snapshot 字段为 NULL，审核期回退读 template 当前值；V14 之后新 assignment 一律固化 snapshot。

## 4. 实现层决策（design 阶段补充）

### ID1：canSubmit 性能策略 = 批量 IN 查询 + 内存组装

**问题**：孩子端任务列表 `GET /api/task-assignments?childId=...` 一次加载 N 个 assignment（典型 10-50），每个都要做 2 条跨表 JOIN 聚合。朴素实现是 N+1 查询（2N 条 SQL），性能塌方。

**方案**：

```
查询路径：
1. SELECT * FROM task_assignment WHERE child_id = ? [LIMIT/OFFSET]   → 拿到 N 条 assignment
2. 提取 distinct template_ids（同模板自动去重，<= N 个）
3. SELECT template_id, COUNT(*) AS approved_count
     FROM task_review JOIN task_attempt ... JOIN task_assignment ...
    WHERE child_id = ? AND template_id IN (?, ?, ...)
    GROUP BY template_id                                                    → 1 条
4. SELECT template_id, SUM(amount) AS earned_points
     FROM points_ledger JOIN task_attempt ... JOIN task_assignment ...
    WHERE child_id = ? AND template_id IN (?, ?, ...) AND type = 'EARN'
    GROUP BY template_id                                                    → 1 条
5. Service 层按 template_id 映射回每个 assignment，组装 canSubmit
```

**结果**：N+1 → 4 条 SQL（1 主查询 + 2 聚合 + 1 模板读取），单页查询毫秒级。

### ID2：并发兜底 = 应用层 SELECT...FOR UPDATE 锁 (child_id, template_id)

**问题**：同一孩子在两台设备同时提交同一模板的两个 attempt 时，两个事务可能都看到「已通过 2 < max 3」同时通过校验，APPROVED 后变成 4 次超出。

**方案**：

在 submit 前置校验事务内，先显式锁定 (child_id, template_id) 维度：

```sql
SELECT id FROM task_assignment
 WHERE child_id = ? AND template_id = ?
 ORDER BY template_id
 LIMIT 1
 FOR UPDATE;
```

- **锁粒度**：行级锁，仅锁该 (child, template) 组合下一行 assignment；不影响其他孩子或其他模板
- **死锁防护**：所有 submit 路径按 `template_id ASC` 顺序获取行锁（即使一次只锁一个也保持习惯）
- **索引依赖**：V14 同时新增 `idx_assignment_child_template (child_id, template_id, id)` 复合索引，保证 FOR UPDATE 走索引而非全表锁
- **事务边界**：submit 接口的 `@Transactional` 包住「FOR UPDATE → 聚合查询 → 创建 attempt」全流程，事务结束自动释放锁
- **方言兼容**：H2 PostgreSQL 模式 / MySQL InnoDB / PostgreSQL 均原生支持 `SELECT ... FOR UPDATE`

**不在 approve 路径加锁**：审核 APPROVED 是异步后台任务，加锁会拖慢审核吞吐；依赖 submit 锁 + 流水不可变保证「同一 attempt 不会被批准两次」。

### ID3：聚合 SQL 实现 = XML mapper 手写 JOIN SQL

**方案**：

在 `TaskReviewMapper.xml` 新增：

```xml
<select id="countApprovedByTemplateAndChild" resultType="map">
  SELECT a.template_id AS templateId, COUNT(r.id) AS approvedCount
    FROM task_review r
    JOIN task_attempt t ON r.attempt_id = t.id
    JOIN task_assignment a ON t.assignment_id = a.id
   WHERE a.child_id = #{childId}
     AND r.decision = 'APPROVED'
     AND a.template_id IN
    <foreach collection="templateIds" item="id" open="(" close=")" separator=",">
      #{id}
    </foreach>
   GROUP BY a.template_id
</select>

<select id="lockAssignmentByChildTemplate" resultType="long">
  SELECT id FROM task_assignment
   WHERE child_id = #{childId} AND template_id = #{templateId}
   ORDER BY template_id
   LIMIT 1
   FOR UPDATE
</select>
```

在 `PointsLedgerMapper.xml` 新增：

```xml
<select id="sumEarnByTemplateAndChild" resultType="map">
  SELECT a.template_id AS templateId, SUM(l.amount) AS earnedPoints
    FROM points_ledger l
    JOIN task_attempt t ON l.business_ref = CONCAT('ATTEMPT_', t.id)
    JOIN task_assignment a ON t.assignment_id = a.id
   WHERE a.child_id = #{childId}
     AND l.type = 'EARN'
     AND a.template_id IN
    <foreach collection="templateIds" item="id" open="(" close=")" separator=",">
      #{id}
    </foreach>
   GROUP BY a.template_id
</select>
```

**跨方言约束**：

- 仅使用 SQL 标准函数：`CONCAT` / `COALESCE` / `CASE WHEN` / `SUM` / `COUNT`
- 不使用 MySQL 专属 `JSON_EXTRACT`（仅 V14 数据回填脚本使用，那里按 `databaseId` 分方言）
- 不使用 PostgreSQL 专属 `->>` 运算符
- `IN (?...)` 列表上限：单次 50 个 template_id（孩子端单页 assignment 数上限），如未来膨胀需要分批

### ID4：错误响应 schema = 复用项目通用 ErrorCode 结构

**HTTP 状态码**：`422 Unprocessable Entity`（与项目同模式 `TASK_TEMPLATE_VALIDATION_FAILED` 一致，区别于 400 参数错误与 409 状态冲突）

**响应体**（复用 `ApiErrorResponse` 通用 schema）：

```json
{
  "code": "TASK_SUBMISSION_MAX_REACHED",
  "message": "已达到最大提交次数",
  "timestamp": "2026-07-20T10:30:00Z",
  "path": "/api/task-review/submissions",
  "fieldErrors": null,
  "details": null
}
```

**MUST NOT 包含**：当前已通过次数、当前累计积分、配置上限值（spec 强制约束，避免暴露内部状态）

### ID5：V14 迁移跨方言实现

```sql
-- 字段新增（标准 SQL，三方言通用）
ALTER TABLE task_template
  ADD COLUMN allow_resubmit BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN max_submissions INT NOT NULL DEFAULT 0,
  ADD COLUMN points_cap INT NOT NULL DEFAULT 0;

ALTER TABLE task_assignment
  ADD COLUMN snapshot_template_allow_resubmit BOOLEAN DEFAULT NULL,
  ADD COLUMN snapshot_template_max_submissions INT DEFAULT NULL,
  ADD COLUMN snapshot_template_points_cap INT DEFAULT NULL;

-- 复合索引（MySQL / PostgreSQL 通用）
CREATE INDEX idx_assignment_child_template
    ON task_assignment (child_id, template_id, id);

-- STANDING 数据回填（按 databaseId 分方言）
-- MySQL:
UPDATE task_template
   SET allow_resubmit = TRUE,
       max_submissions = COALESCE(JSON_EXTRACT(type_config, '$.max_submissions'), 0)
 WHERE task_type = 'STANDING'
   AND JSON_EXTRACT(type_config, '$.max_submissions') IS NOT NULL;

-- PostgreSQL / H2 PostgreSQL 模式:
UPDATE task_template
   SET allow_resubmit = TRUE,
       max_submissions = COALESCE((type_config::jsonb ->> 'max_submissions')::int, 0)
 WHERE task_type = 'STANDING'
   AND type_config::jsonb ->> 'max_submissions' IS NOT NULL;
```

**回填约束**：

- `allow_resubmit = (max_submissions IS NOT NULL)`：原 STANDING 配置了 max_submissions 的迁移为启用
- `max_submissions = COALESCE(原值, 0)`：原值为 NULL 时取 0（不限制）
- `points_cap = 0`：原 STANDING 无积分上限概念，迁移后默认不限制
- 既有 `task_assignment.snapshot_template_type_config.max_submissions` **保留不动**（旧客户端读 type_config 仍可工作）
- 老 assignment 新增的三 snapshot 字段保持 NULL（D9 回退路径处理）

### ID6：审核期 NULL snapshot 回退路径

```java
// ResubmissionPolicyEvaluator.java
public ResubmissionDecision evaluate(TaskAssignment assignment) {
    Boolean allowResubmit = assignment.getSnapshotTemplateAllowResubmit();
    Integer maxSubmissions = assignment.getSnapshotTemplateMaxSubmissions();
    Integer pointsCap = assignment.getSnapshotTemplatePointsCap();

    // D9 回退：V14 之前老 assignment snapshot 为 NULL，读 template 当前值
    if (allowResubmit == null) {
        TaskTemplate template = templateService.getById(assignment.getTemplateId());
        allowResubmit = template.getAllowResubmit();
        maxSubmissions = template.getMaxSubmissions();
        pointsCap = template.getPointsCap();
    }

    if (Boolean.FALSE.equals(allowResubmit)) {
        return ResubmissionDecision.allowed();
    }

    // ID1/ID2 锁与聚合在 caller 完成
    long approvedCount = stats.getApprovedCount(assignment.getTemplateId());
    long earnedPoints = stats.getEarnedPoints(assignment.getTemplateId());

    if (maxSubmissions > 0 && approvedCount >= maxSubmissions) {
        return ResubmissionDecision.blocked("TASK_SUBMISSION_MAX_REACHED");
    }
    if (pointsCap > 0
        && earnedPoints + assignment.getRewardPoints() > pointsCap) {
        return ResubmissionDecision.blocked("TASK_SUBMISSION_POINTS_CAP_REACHED");
    }
    return ResubmissionDecision.allowed();
}
```

### ID7：ResubmissionPolicyEvaluator 共享组件

新增 `server/task-review/src/main/java/com/cutegoals/taskreview/service/ResubmissionPolicyEvaluator.java`，被两个调用方共享：

- `TaskReviewService.submitAttempt` — submit 校验路径
- `TaskAssignmentService.listByChild` / `TaskAssignmentListAssembler` — canSubmit 列表计算路径

保证 spec D8「列表与 submit 完全相同聚合口径」要求。

## 5. 受影响代码路径

### 后端

**实体**：

- `server/common/src/main/java/com/cutegoals/common/entity/task/TaskTemplate.java`：新增三字段 + `@TableField` 注解
- `server/common/src/main/java/com/cutegoals/common/entity/task/TaskAssignment.java`：新增三 snapshot 字段

**Mapper**：

- `server/task-review/src/main/resources/mapper/TaskReviewMapper.xml`：
  - `countApprovedByTemplateAndChild`（单 template + 批量 templates 两版本）
  - `lockAssignmentByChildTemplate`（FOR UPDATE）
- `server/points/src/main/resources/mapper/PointsLedgerMapper.xml`：
  - `sumEarnByTemplateAndChild`（单 template + 批量 templates 两版本）

**Service**：

- `server/task/src/main/java/com/cutegoals/task/service/TaskTemplateService.java`：
  - 创建 / 更新路径接受 + 校验三字段（min/max 范围）
  - 删除约 line 740 附近 `parseMaxSubmissions` 读取 `type_config.max_submissions` 的逻辑
- `server/task/src/main/java/com/cutegoals/task/service/TaskAssignmentService.java`：
  - 单次 / 批量 / 周期生成三条路径固化三 snapshot 字段
- `server/task-review/src/main/java/com/cutegoals/taskreview/service/TaskReviewService.java`：
  - submit 入口先调用 `lockAssignmentByChildTemplate` 取行锁
  - 再调用聚合 mapper
  - 通过 `ResubmissionPolicyEvaluator` 决策
  - 删除约 line 189 / 465 附近 STANDING 专属 `parseMaxSubmissions`
- `server/task-review/src/main/java/com/cutegoals/taskreview/service/ResubmissionPolicyEvaluator.java`（新文件）

**Controller / DTO**：

- `TaskTemplateCreateRequest` / `TaskTemplateUpdateRequest` / `TaskTemplateResponse`：新增三字段
- `TaskAssignmentResponse`：新增三 snapshot 字段 + `canSubmit` + `submissionBlockReason`

**数据库迁移**：

- `server/common/src/main/resources/db/migration/V14__add_resubmission_controls.sql`

### 前端

**家长端**：

- `web/src/parent/pages/ParentTemplatesPage.tsx`：模板编辑表单顶层 Checkbox + 条件 InputNumber
- `web/src/parent/components/TaskTypeConfigForms.tsx`：StandingConfigForm 移除「无限提交」Checkbox 与「最大提交次数」InputNumber（line 240-303 附近）
- `web/src/parent/api.ts`（或对应类型定义）：`TaskTemplate` / `TaskTemplatePayload` 类型新增三字段

**孩子端**：

- `web/src/child/pages/index.tsx`：
  - `ChildTasksPage`（line 190+）：读取 `canSubmit`，false 时禁用「提交」按钮 + tooltip
  - `ChildHomePage`（line 104+）：同步处理 canSubmit
- `web/src/child/api.ts`：`ChildAssignment` 类型新增 `canSubmit` / `submissionBlockReason` + 三 snapshot 字段

### 受影响 API

| API | 变更 |
|---|---|
| `POST /api/task-templates` | 请求体 +3 字段 |
| `PUT /api/task-templates/{id}` | 请求体 +3 字段；忽略 `type_config.max_submissions` |
| `GET /api/task-templates` / `GET /api/task-templates/{id}` | 响应体 +3 字段 |
| `GET /api/task-assignments` | 每个 assignment +3 snapshot 字段 + `canSubmit` + `submissionBlockReason` |
| `POST /api/task-review/submissions` | 新增 2 个错误码 + 422 状态 |

## 6. 数据流

### 6.1 家长配置上限 → 孩子提交达上限被拒

```
[家长端]
  勾选「允许重复提交」
    → InputNumber max_submissions=3
    → InputNumber points_cap=100
  保存模板
    → POST /api/task-templates (allow_resubmit=true, max_submissions=3, points_cap=100)

[后端 TaskTemplateService]
  字段校验 → 持久化 task_template + 审计

[家长创建分配]
  POST /api/task-assignments
    → TaskAssignmentService 固化 snapshot_template_allow_resubmit=true,
      snapshot_template_max_submissions=3,
      snapshot_template_points_cap=100

[孩子端 GET /api/task-assignments?childId=...]
  TaskAssignmentService.listByChild
    → 批量 IN 聚合 (countApprovedByTemplateAndChild + sumEarnByTemplateAndChild)
    → ResubmissionPolicyEvaluator.evaluateBatch
    → 返回每个 assignment 的 canSubmit + submissionBlockReason

[孩子端 POST /api/task-review/submissions]
  TaskReviewService.submitAttempt
    1. lockAssignmentByChildTemplate (FOR UPDATE)
    2. countApprovedByTemplateAndChild + sumEarnByTemplateAndChild (单 template)
    3. ResubmissionPolicyEvaluator.evaluate
    4a. allow → 既有审核流程
    4b. block → 422 TASK_SUBMISSION_MAX_REACHED / POINTS_CAP_REACHED
```

### 6.2 老 assignment（V14 之前）兼容路径

```
[审核期]
  ResubmissionPolicyEvaluator.evaluate
    snapshot_template_allow_resubmit == null
      → 读 task_template 当前值
      → apply max/cap 校验
```

## 7. 测试策略

### 7.1 单元测试

| 测试类 | 覆盖点 |
|---|---|
| `TaskTemplateServiceTest` | 三字段默认值 / 范围校验（max 0-10000, cap 0-100000000）/ STANDING 不再读 type_config / 旧客户端兼容（PUT 提交 type_config.max_submissions 被忽略） |
| `TaskAssignmentServiceTest` | snapshot 写入单次 / 批量 / 周期生成三条路径 |
| `ResubmissionPolicyEvaluatorTest` | max/cap 双校验 / NULL snapshot 回退 / allow_resubmit=false 不校验 / max=0 不限制 / cap=0 不限制 / cap 边界（95+5≤100 vs 95+10>100）|
| `TaskReviewMapperTest` (H2 PG 模式) | GROUP BY 聚合结果正确 / REJECTED 不计数 / REFUND 不计入 / 跨 assignment 聚合 |
| `PointsLedgerMapperTest` | business_ref JOIN 正确 / type=EARN 过滤正确 |

### 7.2 集成测试

| 测试类 | 覆盖点 |
|---|---|
| `TaskReviewResubmissionControlIT` | 端到端 6 场景：默认不启用 / max=0 不限制 / cap=0 不限制 / 达 max 拒绝 / 达 cap 拒绝 / 跨 assignment 聚合 |
| `TaskAssignmentListCanSubmitIT` | 列表批量计算与 submit 单条校验结果一致 |
| `ConcurrentSubmitResubmissionIT` | 两线程同时提交同一 (child, template)，断言仅一个 submit 成功（FOR UPDATE 兜底） |

### 7.3 迁移回归

| 测试类 | 覆盖点 |
|---|---|
| `MigrationV14Test` | 准备 V13 STANDING fixture → 跑 V14 → 验证 task_template 三字段映射正确 / task_assignment.snapshot_template_* 为 NULL / idx_assignment_child_template 存在 / 新 assignment 正确固化 snapshot |

### 7.4 前端测试

| 测试类 | 覆盖点 |
|---|---|
| `TaskTypeConfigForms.test.tsx` | STANDING 子表单不再有「最大提交次数」字段 |
| `parent-save-dialogs.test.tsx` | 勾选「允许重复提交」后 max/cap InputNumber 出现 / 默认值 0 / 字段提交到后端 |
| `pages.test.tsx` (孩子端) | canSubmit=false 时按钮 disabled + tooltip 文案正确（MAX_REACHED / POINTS_CAP_REACHED） |

### 7.5 E2E

`web/e2e/` 新增链路场景：家长配置上限 → 孩子重投达上限 → 列表禁用按钮 → 强行提交得到错误码。

## 8. 风险与缓解

| 风险 | 等级 | 缓解 |
|---|---|---|
| 跨 assignment 聚合性能 | 中 | ID1 批量 IN 查询，N+1 → 4 SQL；单页 50 assignment 上限 |
| 并发提交超出上限 | 中 | ID2 FOR UPDATE 行锁 (child_id, template_id)；按 template_id 升序避免死锁 |
| Flyway V14 跨方言失败 | 中 | ID5 仅用 SQL 标准 DDL；JSON 字段提取按 databaseId 分方言（MySQL `JSON_EXTRACT` / PG&H2 `::jsonb ->>`）；CI 跑三种方言迁移 |
| STANDING BREAKING 旧客户端不兼容 | 中 | D6 保留 `type_config.max_submissions` 只读；旧客户端 PUT 提交被静默忽略（不报错）；升级说明显式标注 |
| FOR UPDATE 锁竞争拖慢 submit | 低 | 单页 50ms 内完成事务，并发冲突概率低；可在监控告警后回退到乐观锁（版本号 + 重试） |
| canSubmit 响应体积增大 | 低 | 单 assignment 新增 5 字段（3 snapshot + canSubmit + submissionBlockReason），约 100 字节；50 assignment 5KB，可接受 |
| 审核失败错误信息用户友好度 | 低 | ID4 复用通用 ApiErrorResponse，message 用「已达到最大提交次数」/「已达到积分上限」，不暴露数值 |

## 9. Open Questions 最终答案

源自 open 阶段 design.md 的三个 Open Questions，本 design 阶段最终明确：

| 问题 | 答案 | 理由 |
|---|---|---|
| 错误响应是否携带数值上下文（当前累计值/上限值）？ | **否** | spec 强制要求；避免暴露内部状态被反推策略；前端通过 canSubmit 列表已知状态 |
| 家长调低 cap 后旧 assignment 行为？ | **snapshot 固化优先** | D2 核心不变量；旧 assignment 按创建时 snapshot_points_cap 校验，不受家长后续调整影响；如家长需要立即生效需删除旧 assignment 重建 |
| 未来「重置上限」运营功能？ | **本次不做** | Non-Goals；家长通过编辑模板字段即可调整 cap/max；运营场景出现时再加 capability（如「批量重置已通过次数」需要独立 capability） |

## 10. 实施顺序建议

按依赖关系建议（具体由 build 阶段 plan 决定）：

1. V14 迁移脚本（含字段 + 索引 + STANDING 回填） + `MigrationV14Test`
2. 实体字段 + DTO 字段（TaskTemplate / TaskAssignment）
3. Mapper XML（4 个查询：单 template count + 批量 count + 单 template sum + 批量 sum + FOR UPDATE 锁）
4. `ResubmissionPolicyEvaluator` 组件
5. `TaskTemplateService` 字段校验 + 删除旧 `parseMaxSubmissions`
6. `TaskAssignmentService` snapshot 写入三条路径
7. `TaskReviewService.submitAttempt` 集成前置校验
8. `TaskAssignmentResponse` canSubmit 批量计算
9. 前端家长端表单
10. 前端孩子端 canSubmit + tooltip
11. 集成测试 / E2E

## 11. Spec Patch

**无**。open 阶段 delta spec（`task-template` / `task-assignment` / `task-review` 三份）已完整覆盖验收场景；本 design 阶段确认的实现决策（批量聚合 / FOR UPDATE / XML mapper）属实现层细节，不需要回写 delta spec。
