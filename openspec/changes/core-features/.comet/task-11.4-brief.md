# Task 11.4: REPEAT Dual Triggers (Scheduler + Submission Hook)

## 问题

REPEAT 类型任务的两种驱动机制完全缺失：
1. 时间触发器：每日扫描推进 OPEN→EXPIRED、PENDING_OPEN→OPEN
2. 提交触发器：审核通过时自动创建下一期

## 依赖

- 依赖 11.2（`TaskTemplateFrequencyService.nextTriggerDate()` — 计算下一触发日）
- 依赖 11.3（STANDING/LIMITED 逻辑提供完整示例）

## 要求

### 1. `@EnableScheduling` 配置

在 `CuteGoalsApplication.java` 上添加 `@EnableScheduling` 注解（`server/web/src/main/java/com/cutegoals/web/CuteGoalsApplication.java`）

### 2. `RepeatTaskScheduler`

在 `server/task/src/main/java/com/cutegoals/task/scheduler/RepeatTaskScheduler.java` 创建：

```java
@Component
@RequiredArgsConstructor
public class RepeatTaskScheduler {
    // 每日 Asia/Shanghai 00:05 运行
    // 1. 扫描所有表里 status=OPEN 且 deadline < now 的 assignment → 设为 EXPIRED
    // 2. 扫描所有表里 status=PENDING_OPEN 且已到触发日的 assignment → 设为 OPEN
}
```

**实现要点**：
- 使用 `@Scheduled(cron = "0 5 0 * * *")` 与 Asia/Shanghai 时区
- 使用 `zone = "Asia/Shanghai"` 参数
- 每模板独立事务：单个模板失败不影响其他
- 审计日志写入每次 batch 运行结果
- 幂等：同日多次运行不重复推进
- 使用 `TaskTemplateMapper` 查找所有已启用 REPEAT 模板
- 使用 `TaskTemplateFrequencyService.nextTriggerDate()` 判断 PENDING_OPEN 是否到触发日

### 3. REPEAT 提交钩子

在 `TaskReviewService.approveAttempt()` 中，审核通过后：
- 若 assignment 是 REPEAT 类型：
  1. 将当前 assignment 状态设为 COMPLETED
  2. 使用 `TaskTemplateFrequencyService.nextTriggerDate(typeConfig, fromDate)` 计算下一触发日
  3. 若有下一触发日：创建新 assignment（status=PENDING_OPEN, deadline=触发日 23:59:59, occurrence_key=新键）
  4. 若无下一触发日（`Optional.empty()`）：不做额外操作

**注意**：需与现有 `approveAttempt` 的事务边界保持一致（和 EARN 流水同事务）

### 4. 状态支持扩展

当前 `status` 字段含义（`task_assignment` 表）：
- REPEAT 新增状态：`PENDING_OPEN`、`OPEN`、`COMPLETED`（用于达上限场景）、`EXPIRED`
- 这些状态在 V4 建表脚本的 `status` 注释中已有定义（PENDING/SUBMITTED/APPROVED/REJECTED/COMPLETED 等），`PENDING_OPEN`/`OPEN`/`EXPIRED` 可能需要补充

**检查**：V4 migration 的 `status` COMMENT 已列出 `PENDING/SUBMITTED/APPROVED/REJECTED/COMPLETED`，
需要确保 `PENDING_OPEN`, `OPEN`, `EXPIRED` 在运行时可作为合法 status 值被接受。
如果存在 `@NotNull` 或 `@Pattern` 校验，需要更新。

### 5. 测试

- `RepeatTaskSchedulerTest`：推进逻辑、幂等、审计
- `TaskReviewServiceTest`：REPEAT 审核通过后创建下一期
- `TaskAssignmentServiceTest`：REPEAT 分配创建时首期 PENDING_OPEN

## 文件范围

- `server/web/src/main/java/com/cutegoals/web/CuteGoalsApplication.java` — 添加 `@EnableScheduling`
- `server/task/src/main/java/com/cutegoals/task/scheduler/RepeatTaskScheduler.java` — 新文件
- `server/task-review/src/main/java/com/cutegoals/taskreview/service/TaskReviewService.java` — 提交钩子
- `server/task/src/main/java/com/cutegoals/task/service/TaskAssignmentService.java` — 必要时补充 REPEAT 创建逻辑
- 测试文件：对应的新增测试
