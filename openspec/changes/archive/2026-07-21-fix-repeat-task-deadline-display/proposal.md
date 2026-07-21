# Proposal: 重复任务列表显示频率标签而非截止日期

## 问题

任务分配页面列表中，所有任务都显示 `截止 {deadline}`，但重复任务（REPEAT）不应显示截止日期，应显示"重复任务，每天/每周/每月"等频率标签。

## 根因

列表渲染代码直接展示 `a.deadline`，未根据 `a.snapshotTemplateTaskType` 区分任务类型。

## 修复目标

对 `snapshotTemplateTaskType === 'REPEAT'` 的任务，解析 `snapshotTemplateTypeConfig` 中的 frequency 字段，显示对应的中文频率标签。
