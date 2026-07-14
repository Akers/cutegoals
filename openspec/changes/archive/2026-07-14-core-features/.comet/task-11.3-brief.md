# Task 11.3: STANDING & LIMITED Task Type Business Logic

## 问题

任务模板已支持 `task_type`（LIMITED/REPEAT/STANDING）和 `type_config` JSON 字段（由 11.1、11.2 添加），但创建分配和提交/审核流程中未根据 `task_type` 实施不同类型的逻辑约束。

## 依赖

- 依赖 11.1（task_type/type_config 字段存在）
- 与 11.2 无依赖关系

## 要求

### 1. STANDING 类型业务规则

**按孩子独立 `submission_count` 计数**：
- `TaskAssignment` 表已添加 `submission_count INT DEFAULT 0` 列
- 每次该分配的审核**通过**（APPROVED）时，`submission_count` 自增 1
- 驳回（REJECTED）不增加计数

**达上限处理**：
- 从 `TaskTemplate.typeConfig` 中读取 `max_submissions`（JSON 字段，可为 `null` 或正整数）
- `max_submissions=null` → 永远 ACTIVE（无限提交）
- `max_submissions=N` → 当 `submission_count >= N` 时，该 assignment 的状态切换为 COMPLETED
- 达上限后提交尝试返回 `TASK_STANDING_LIMIT_REACHED`（错误码 `TASK_ASSIGNMENT_STANDING_LIMIT_REACHED`）

**修改位置**：
- `TaskReviewService.java` 的审批逻辑中（`approveTask` 方法）：
  - 审核通过后，若 assignment 是 STANDING 类型，`submission_count++`
  - 若达上限，将 assignment 状态设为 COMPLETED
- `TaskAttemptService` 或 `TaskReviewService` 的提交逻辑中：
  - 创建新 attempt 前，检查 assignment 状态是否 COMPLETED（STANDING 达上限时）
  - 若是，返回 `TASK_ASSIGNMENT_STANDING_LIMIT_REACHED`

### 2. LIMITED 类型业务规则

**日期窗口状态机**：
- `TaskTemplate.typeConfig` 中解析 `start_date` 和 `end_date`（ISO 日期字符串）
- 分配创建时，根据当前时间与日期窗口计算派生状态（不改变 DB status）：
  - `start_date` 为 `null` 或 `created_at >= start_date` → 正常 ACTIVE 状态（DB status 保持 PENDING）
  - `created_at < start_date` → 标记为 PENDING（未到开始日期）
  - `expired_at > end_date` → 标记为 EXPIRED
- 创建 attempt 的预检查（`TaskReviewService.submitTask` 或等效方法）：
  - 若当前时间在 `start_date` 之前 → 返回 `TASK_TEMPLATE_LIMITED_NOT_STARTED`
  - 若当前时间在 `end_date` 之后 → 返回 `TASK_TEMPLATE_LIMITED_EXPIRED`

**修改位置**：
- `TaskReviewService.java` 提交逻辑中加入 LIMITED 日期检查
- 在 `enrichAssignment()` 或等效展示层加入 LIMITED 派生状态标记

### 3. 任务类型不可改性

在 `TaskTemplateService.updateTemplate()` 中：
- 若请求中 `task_type` 与现有值不同 → 返回 `TASK_TEMPLATE_TYPE_IMMUTABLE`
- 允许修改 `type_config` 内字段（不影响 task_type）

### 4. 测试

- `TaskTemplateServiceTest`：type 不可改性测试
- `TaskReviewServiceTest`：STANDING approval + 达上限 + 超限提交拒绝
- `TaskReviewServiceTest`：LIMITED 开始前/过期后提交拒绝

## 设计决策

1. STANDING 的 submission_count 逻辑放在 `TaskReviewService` 的审批方法中，因为唯一能改变 count 的是审核通过事件
2. LIMITED 的日期检查只是前置条件检查，不影响 DB status
3. 派生状态（PENDING/SUBMITTABLE/EXPIRED 等）是展示层概念，DB 只存 PENDING/SUBMITTED/APPROVED/COMPLETED/EXPIRED

## 文件范围

- `server/task-review/src/main/java/com/cutegoals/taskreview/service/TaskReviewService.java` — 审批后 submission_count 自增、LIMITED 日期预检查
- `server/task/src/main/java/com/cutegoals/task/service/TaskAssignmentService.java` — `enrichAssignment()` 添加 STANDING count 和 LIMITED 派生状态
- `server/task/src/main/java/com/cutegoals/task/service/TaskTemplateService.java` — type 不可改性
- `server/task-review/src/test/java/com/cutegoals/taskreview/service/TaskReviewServiceTest.java` — 新增测试
- `server/task/src/test/java/com/cutegoals/task/service/TaskTemplateServiceTest.java` — 新增测试
- `server/task/src/test/java/com/cutegoals/task/service/TaskAssignmentServiceTest.java` — 必要时补充
