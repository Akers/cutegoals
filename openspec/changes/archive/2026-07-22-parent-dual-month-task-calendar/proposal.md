# 家长端双月任务日历

## Why

当前家长端任务分配页面的"日历"模块是一个简单的 HTML `<input type="date">` 日期选择器，仅能按单天筛选任务列表。家长需要逐一翻日历才能了解任务分布在哪些日期、哪些周和哪些月份。此外，任务列表中缺乏按任务类型（限时/重复/常驻）筛选的能力。改造为目标驱动型双月日历视图可让家长一目了然掌握任务分布，并通过日/周/月三级粒度快速定位分配记录。

## What Changes

- **移除** 当前的"选择日期" `<input type="date">` 控件
- **新增** 双月日历视图（当月 + 下月），基于 Ant Design Calendar 组件扩展，桌面端左右并排、移动端上下堆叠
- **新增** 日历颜色标记：日期格子按任务类型色彩编码（限时任务 / 重复任务 / 常驻任务各一色），无任务的日期保持默认样式
- **新增** 周号区域指示器（当周有任务时显示标记）
- **新增** 日 / 周 / 月三级点击交互：点击日期 → 当天任务；点击周号 → 当周任务；点击月头 → 当月任务，均在日历下方展示对应任务列表
- **新增** 日历下方集成任务类型筛选器（复用现有 `TaskTypeFilter` 复选框组件）和"查看全部"按钮
- **新增** "查看全部"按钮行为：应用当前任务类型筛选但移除日期范围约束，查询全部匹配分配记录
- **扩展** `GET /api/task-assignments` 端点：新增 `taskType` 查询参数（基于 `snapshotTemplateTaskType`），支持多值过滤（逗号分隔）
- **扩展** `GET /api/task-assignments/calendar` 端点：返回数据中每日统计增加 `taskTypes` 字段（`{LIMITED: n, REPEAT: n, STANDING: n}`）

## Capabilities

### New Capabilities

- `parent-task-calendar`: 双月日历 UI 组件，包含按任务类型颜色标记、周号指示器、日/周/月三级点击交互，以及日历下方的任务列表渲染和任务类型筛选集成

### Modified Capabilities

- `parent-pages-contract`: 更新"Task 按日期列表查询契约"需求，使其同时支持日历交互（前端可通过日历选择日/周/月触发日期范围查询）和"查看全部"模式（无日期约束查询）

- `task-assignment`: 新增按任务类型筛选的查询能力（`taskType` 参数），后端 `queryAssignments` 方法基于 `snapshotTemplateTaskType` 字段过滤

## Impact

- **前端**：`web/src/parent/pages/index.tsx` — 日历卡片区域重构
- **前端**：`web/src/parent/components/TaskTypeFilter.tsx` — 已存在，集成到任务分配页，可能需要样式调整
- **前端**：可能需要新增 `web/src/parent/components/TaskCalendar.tsx` 日历组件
- **后端**：`server/task/.../TaskAssignmentController.java` — `/calendar` 和列表端点参数扩展
- **后端**：`server/task/.../TaskAssignmentService.java` — `getCalendar` 返回加 `taskTypes` 维度，`queryAssignments` 加 `taskType` 过滤
- **后端**：`server/task/.../mapper/TaskAssignmentMapper.java` — 可能需要扩展查询方法支持 `taskType` 过滤
- **API 契约**：`GET /api/task-assignments` 新增可选参数 `taskType`；`GET /api/task-assignments/calendar` 响应新增 `taskTypes` 字段
