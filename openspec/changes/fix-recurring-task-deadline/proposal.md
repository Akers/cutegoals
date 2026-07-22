# Proposal: 修复重复任务截止日期未按频率模式动态滚动

## 问题描述

在家长端给儿童分配 REPEAT（重复）类任务模板时，系统始终将截止日期设为当天 23:59:59，完全忽略模板配置的重复频率模式（DAILY/WEEKLY/MONTHLY/YEARLY）。

例如：一个"每周一练琴"（WEEKLY，周一触发）的 REPEAT 模板，家长周三分配后，截止日被设为周三 23:59:59——而正确的截止日应为下一个周一 23:59:59。

## 根因分析

**代码位置**：`server/task/.../service/TaskAssignmentService.java:88-104`

```java
// deadline 计算：
if (deadlineStr == null) {
    if ("REPEAT".equals(template.getTaskType())) {
        deadline = LocalDate.now().atTime(23, 59, 59);  // ← 总是"今天 EOD"
    }
}
```

- `TaskTemplateFrequencyService.nextTriggerDate()` 已实现根据 typeConfig 中的频率配置（DAILY/WEEKLY/MONTHLY/YEARLY）计算下一个触发日期
- 但 `createAssignment` 方法未调用该服务，直接硬编码为当天
- `TaskAssignmentService` 未注入 `TaskTemplateFrequencyService` 依赖

## 修复目标

在 `createAssignment` 中，对 REPEAT 任务，使用 `TaskTemplateFrequencyService.nextTriggerDate()` 计算首次触发日期，以此作为截止日期。

## 影响范围

- **修改文件**：仅 1 个 Java 文件
- **新增依赖**：`TaskAssignmentService` 增加 1 个字段注入
- **不影响**：批量分配（`createBatchAssignments`）、重复生成（`generateRecurringAssignments`）、审核流程中的下一周期生成（`createRepeatAssignment`）
- **回归风险**：低——仅修改 REPEAT 任务的首次截止日期计算逻辑
