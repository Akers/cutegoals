# Proposal: 家长端任务分配 - 重复任务不要求截止日期

## 问题描述

家长端的单任务分配弹窗（"分配任务"）中，选任何模板都**强制要求填写截止日期**。但对于 DAILY / WEEKLY / MONTHLY 重复任务模板，正确行为应该是：

- **重复任务**（`taskType === 'REPEAT'`）：不需要设截止日期，分配后任务根据模板的重复规则进入对应时间窗口
- **非重复任务**（`taskType === 'LIMITED'`）：可设开始日期（≥当天）和截止日期

当前单分配弹窗没有根据 `taskType` 做区分，导致：
1. 重复任务也被要求设截止日期——业务上不合理
2. 分配重复任务时应走 `/task-assignments/generate-recurring` 生成多次任务，而不是 `/task-assignments` 创建单条

批量分配（batch assignment）已有正确的 REPEAT 区分逻辑，但**单分配流程缺失**。

## 根因分析

`web/src/parent/pages/index.tsx` 中 `handleSingleAssign` / 单分配弹窗：

- 第 975-976 行：`singleDeadline` 用固定 `useState`，无 `taskType` 感知
- 第 1001-1004 行：强制校验 `if (!singleDeadline)` 
- 第 1006-1011 行：固定调用 `POST /task-assignments`（单条创建），未路由到 `POST /task-assignments/generate-recurring`
- 第 1281-1288 行：截止日期 `DatePicker` 始终展示，没有根据模板类型隐藏

批量分配弹窗（1171-1232 行）已有正确的条件渲染（1222-1227 行隐藏 REPEAT 的结束日期），可作为实现参考。

## 修复目标

1. 单分配弹窗根据所选模板 `taskType` 动态切换 UI：
   - **REPEAT** → 隐藏截止日期，显示开始日期/结束日期，调用 `generate-recurring` 端点
   - **LIMITED** → 保持现有截止日期选择器行为
2. 非重复任务的开始日期校验 ≥ 当天
