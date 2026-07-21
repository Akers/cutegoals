# Proposal: 重复任务单分配——完全无需录入日期

## 问题描述

在单任务分配弹窗中，如果选择了重复任务模板（`taskType === 'REPEAT'`），当前仍然需要填写日期区间（开始日期/结束日期）。用户期望对重复任务完全不需要录入任何日期，选择模板和难度后直接分配即可。

## 根因分析

1. **前端** (`web/src/parent/pages/index.tsx`): 上一轮修复对 REPEAT 模板改用了 `singleStartDate`/`singleEndDate` 字段路由到 `generate-recurring`，但仍然需要用户填写日期。
2. **后端** (`TaskAssignmentService.java:834-837`): `parseDeadline` 对 null deadlineStr 抛出异常，所有模板都强制要求 deadline。

## 修复目标

1. **前端**: 单分配弹窗选中 REPEAT 模板时，不显示任何日期字段
2. **后端**: 当模板为 REPEAT 且 deadline 为空时，自动设为当天 23:59:59 作为默认触发日期，不抛错
