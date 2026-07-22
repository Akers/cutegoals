# Design: 修复重复任务截止日期计算

## 修复方案

### 1. 新增依赖注入

在 `TaskAssignmentService` 中添加 `TaskTemplateFrequencyService` 依赖（同在 `server/task` 模块，无需跨模块引用）：

```java
private final TaskTemplateFrequencyService taskTemplateFrequencyService;
```

### 2. 修改截止日期计算逻辑

在 `createAssignment()` 的 REPEAT 分支（约第 91-93 行），替换硬编码的"当天 EOD"为基于频率模式的计算：

```java
if ("REPEAT".equals(template.getTaskType())) {
    // 基于频率模式计算首次触发日期
    // fromDate = yesterday → nextTriggerDate 返回 >= 今天的首个触发日期
    Optional<LocalDate> firstTrigger = taskTemplateFrequencyService.nextTriggerDate(
            template.getTypeConfig(), LocalDate.now().minusDays(1));
    if (firstTrigger.isPresent()) {
        deadline = firstTrigger.get().atTime(23, 59, 59);
    } else {
        // 回退：typeConfig 无效或无频率配置时，默认今天 EOD
        log.warn("REPEAT template {} has no valid frequency config, defaulting to today EOD",
                template.getId());
        deadline = LocalDate.now().atTime(23, 59, 59);
    }
}
```

### 3. 各频率模式对应的行为

| 频率 | 今天 | 计算的截止日期 |
|------|------|---------------|
| DAILY | 任意 | 今天 23:59:59（每天都是触发日） |
| WEEKLY（周一） | 周一 | 今天（下一个周一） 23:59:59 |
| WEEKLY（周一） | 周三 | 下周一 23:59:59 |
| WEEKLY（周一） | 周日 | 明天（周一） 23:59:59 |
| MONTHLY（1号） | 1号 | 今天 23:59:59 |
| MONTHLY（1号） | 15号 | 下月1号 23:59:59 |
| YEARLY（1月1日） | 6月15日 | 下一年1月1日 23:59:59 |

### 4. 状态保持

初始分配的 REPEAT 任务状态保持为 `PENDING`（与现有行为一致），不改动调度器状态机。`RepeatTaskScheduler` 未处理的 `PENDING` 状态不影响孩子的提交流程——`submitTask` 仅拦截 `SUBMITTED` 和 `APPROVED` 状态。

审核通过后，`TaskReviewService.approveAttempt()` 中已有的 `createRepeatAssignment` 逻辑会正确创建下一周期的 `PENDING_OPEN` 记录。

### 5. 不改动的部分

- `createBatchAssignments`：批量分配使用 startDate/endDate 范围，逻辑独立
- `generateRecurringAssignments`：已有基于日期遍历的正确逻辑
- `createRepeatAssignment`（TaskReviewService）：已有正确的 `nextTriggerDate` 调用
- 前端：RepEAT 任务在前端不显示截止日期选择器，仅需后端传入正确值即可
