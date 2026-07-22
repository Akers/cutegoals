# Comet Design Handoff

- Change: parent-dual-month-task-calendar
- Phase: design
- Mode: compact
- Context hash: 472c80a6596aecb9d4ae7c5e6680f30f07af93c161d7e1b9c71147981e871198

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/parent-dual-month-task-calendar/proposal.md

- Source: openspec/changes/parent-dual-month-task-calendar/proposal.md
- Lines: 1-39
- SHA256: 6bf22e1553ce7adef078a77bc59399ca35019904d943b08b68bd5b61c541d512

```md
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

```

## openspec/changes/parent-dual-month-task-calendar/design.md

- Source: openspec/changes/parent-dual-month-task-calendar/design.md
- Lines: 1-157
- SHA256: e03fcaf98c92f8b861008c033b78c659cee0c2f23037c91ad960973c57b7c913

[TRUNCATED]

```md
# 家长端双月任务日历 — 技术设计

## Context

### 当前状态
- 家长任务分配页 (`ParentTasksPage`) 使用 `<input type="date">` 实现简单日期选择
- 前端通过 `GET /api/task-assignments?startDate=X&endDate=X` 按单天范围查询任务列表
- 后端 `GET /api/task-assignments/calendar` 端点已存在，返回按月聚合的任务计数（按状态分类），但前端未使用
- `TaskTypeFilter` 组件已存在（复选框多选：限时/重复/常驻），但未集成到任务分配页
- 项目已引入 Ant Design、dayjs，UI 基于 React 18 + TypeScript + Vite

### 约束
- 每家一个实例，无多家庭边界问题
- 前端不使用第三方日历库（基于 antd Calendar）
- 后端数据库使用 MyBatis-Plus LambdaQueryWrapper
- `TaskAssignment.snapshotTemplateTaskType` 存储任务类型，值域 `LIMITED | REPEAT | STANDING`

### 利益相关者
- 家长用户：需要高效查看和筛选任务分配
- 前端开发：统一组件风格（antd）

## Goals / Non-Goals

**Goals:**
- 以双月日历替代日期选择器，直观展示任务分布
- 按任务类型颜色标记日历日期
- 日/周/月三级点击粒度，联动下方任务列表
- 任务类型筛选与"查看全部"模式

**Non-Goals:**
- 不改变 task-template 和 task-assignment 的创建/编辑/删除逻辑
- 不修改亲子端功能
- 不引入新的第三方日历库（如 FullCalendar）
- 不处理跨年/跨月的"查看全部"分页策略（使用现有分页机制）

## Decisions

### 1. 日历组件：antd Calendar 自渲染 dateCell + 包装双月布局

**选择**：基于 antd `Calendar` 组件的 `dateCellRender` 自定义渲染日期单元格，外部用 CSS Grid/Flex 包装两个月面板形成双月布局。

**备选方案**：
- FullCalendar（React）：功能丰富但引入新依赖、样式难以统一 → 不选
- dayjs 手写日历：灵活但工作量大、无内置无障碍支持 → 不选
- React Calendar 库：与 antd 风格不一致 → 不选

**理由**：项目已使用 antd，组件风格一致，`dateCellRender` 可完全自定义内容（颜色标记、任务计数），外层用 Grid 响应式布局实现桌面并排/移动端堆叠。

### 2. 双月布局：单组件 + 两面板 + CSS 媒体查询

**选择**：一个 `TaskCalendar` 组件内渲染两个 `Calendar` 面板（当月和下月），使用 CSS Grid `grid-template-columns: repeat(auto-fit, minmax(400px, 1fr))` 自适应。

**备选方案**：
- 两个独立 Calendar 组件分开放置：代码冗余，状态不统一 → 不选
- CSS `@container` queries：兼容性不足 → 不选

**理由**：媒体查询在本项目目标浏览器中兼容性好，400px 断点确保日历面板有足够宽度显示完整周行。

### 3. 颜色标记方案：单元格背景色 + 类型多色 + 透明度区分

**选择**：三种任务类型各一个基础色（如 `#FFE0E0` 红 / `#E0E8FF` 蓝 / `#E0FFE0` 绿 或使用 antd token），该日期有多个类型时采用优先级色（LIMITED > REPEAT > STANDING）并显示计数徽章。

**备选方案**：
- 单一圆点标记：无法区分类型 → 不选
- 彩色圆点叠加：单元格内空间拥挤 → 不选
- 渐变色/条纹：过度复杂，视觉负担大 → 不选

**理由**：单色优先级方案简洁直观，配合单元格右上角的计数徽章（如「3」表示3个任务），家长能快速识别"有任务"和"哪个类型占主导"。

### 4. 周号指示器：Calendar 外层自定义列

**选择**：在 Calendar 左侧添加一列显示 ISO 周号，通过 week 行高亮（该周有任务时周号单元格着色）。

**备选方案**：
- antd Calendar `fullscreen` 模式自身不支持直接添加列 → 需用自定义逻辑
- 使用 CSS `::before` 伪元素：不够灵活，无法点击 → 不选

**理由**：在 Calendar 外层用 Grid 布局包装，左列放周号（基于 dayjs 计算），与 Calendar 的周行对齐。

**实现细节**：

```

Full source: openspec/changes/parent-dual-month-task-calendar/design.md

## openspec/changes/parent-dual-month-task-calendar/tasks.md

- Source: openspec/changes/parent-dual-month-task-calendar/tasks.md
- Lines: 1-70
- SHA256: c1ff7e2e12b5211b4777cf9a2af43821236621692d1a8cea1a6afbb9f7df84ac

```md
# 家长端双月任务日历 — 任务清单

## 1. 后端：日历 API 扩展任务类型统计

- [ ] 1.1 修改 `TaskAssignmentService.getCalendar` 方法，在每日聚合数据中新增 `taskTypes` 字段（`{LIMITED: n, REPEAT: n, STANDING: n}`），遍历当天分配时按 `snapshotTemplateTaskType` 分组计数
  - **能力**: `task-assignment`
  - **验证**: 调用 `GET /api/task-assignments/calendar?year=2026&month=7`，检查每日返回数据包含 `taskTypes` 对象且值与该日分配的任务类型一致

## 2. 后端：任务列表 API 新增 taskType 筛选

- [ ] 2.1 修改 `TaskAssignmentController.queryAssignments` 方法签名，新增可选参数 `taskType`（String，逗号分隔多值）
- [ ] 2.2 修改 `TaskAssignmentService.queryAssignments` 方法，当 `taskType` 参数非空时，按逗号分割后在 `snapshotTemplateTaskType` 字段上使用 `in` 条件过滤
  - **能力**: `task-assignment`
  - **验证**: 调用 `GET /api/task-assignments?taskType=LIMITED&page=1&pageSize=20`，确认返回仅 `snapshotTemplateTaskType=LIMITED` 的分配；调用 `GET /api/task-assignments?taskType=LIMITED,REPEAT`，确认返回两种类型

## 3. 后端：确保可选日期范围查询

- [ ] 3.1 修改 `TaskAssignmentService.queryAssignments`，当 `startDate` 或 `endDate` 参数未传入时，不添加日期过滤条件，使"查看全部"模式正常工作
  - **能力**: `task-assignment`, `parent-pages-contract`
  - **验证**: 调用 `GET /api/task-assignments?taskType=STANDING&page=1&pageSize=20`（不传 startDate/endDate），确认返回所有 STANDING 分配

## 4. 前端：创建 TaskCalendar 双月日历组件

- [ ] 4.1 新建 `web/src/parent/components/TaskCalendar.tsx`，基于 antd `Calendar` 组件实现双月布局：当月和下月日历面板，使用 CSS Grid/Flex 自适应（≥768px 左右并排，<768px 上下堆叠）
- [ ] 4.2 实现月头 `headerRender` 自定义渲染，添加点击事件
- [ ] 4.3 实现 `dateCellRender` 自定义日期单元格渲染：根据 `taskTypes` 数据按优先级（LIMITED > REPEAT > STANDING）设置背景色，右上角显示任务总数徽章
  - **能力**: `parent-task-calendar`
  - **验证**: 页面加载后显示双月日历，有任务的日期格子显示对应颜色和计数

## 5. 前端：周号指示器与日/周/月三级点击交互

- [ ] 5.1 在日历面板左侧添加 ISO 8601 周号列，当周有任务时周号单元格着色
- [ ] 5.2 实现日期单元格点击：传递 `{startDate, endDate}` 到父组件
- [ ] 5.3 实现周号点击：计算该周周一至周日的日期范围
- [ ] 5.4 实现月头点击：计算该月 1 日至月末的日期范围
- [ ] 5.5 当前选中的日/周/月有视觉高亮状态（选中态 vs 非选中态）
  - **能力**: `parent-task-calendar`
  - **验证**: 点击日期 → 下方任务列表刷新为当天任务；点击周号 → 当周任务；点击月头 → 当月任务

## 6. 前端：日历数据获取与状态管理

- [ ] 6.1 TaskCalendar 组件使用 `useApi` 分别请求当月和下月的 `/api/task-assignments/calendar` 端点，获取含 `taskTypes` 的聚合数据
- [ ] 6.2 将日历数据传递给 `dateCellRender` 渲染颜色标记
  - **能力**: `parent-task-calendar`
  - **验证**: 日历正确显示两个月的任务分布，颜色标记与后端数据一致

## 7. 前端：集成任务类型筛选与查看全部

- [ ] 7.1 在 `ParentTasksPage` 中引入现有 `TaskTypeFilter` 组件，放置在日历下方
- [ ] 7.2 添加「查看全部」按钮，点击时清除日期范围，保留 `taskType` 筛选，重新查询任务列表
- [ ] 7.3 任务类型筛选变更时，保持当前日期范围，附加 `taskType` 参数重新查询
- [ ] 7.4 「查看全部」模式下清除日历选中高亮
  - **能力**: `parent-task-calendar`, `parent-pages-contract`
  - **验证**: 选择「限时任务」+ 点击「查看全部」→ 返回所有限时任务；筛选类型变更 → 仅刷新任务列表

## 8. 前端：替换旧日历控件并整合数据流

- [ ] 8.1 在 `ParentTasksPage` 中移除旧的 `<input type="date">` 控件和相关 `date` 状态
- [ ] 8.2 替换为 `TaskCalendar` 组件，接收 `onDateRangeChange` 和 `selectedRange` props
- [ ] 8.3 修改任务列表的 `useApi` 查询逻辑：支持 `startDate`、`endDate`、`taskType` 动态组合
- [ ] 8.4 日历颜色标记始终显示所有类型，不受下方类型筛选器影响
  - **能力**: `parent-task-calendar`, `parent-pages-contract`
  - **验证**: 页面正常渲染双月日历、筛选器和任务列表，交互逻辑完整

## 9. 验收测试

- [ ] 9.1 端到端验收：桌面端双月并排 → 点击有色日期 → 任务列表正确 → 切换筛选类型 → 列表刷新 → 点击查看全部 → 全部匹配结果 → 点击日历恢复日期模式
- [ ] 9.2 移动端验收：缩小视口至 <768px → 日历上下堆叠 → 交互正常
  - **能力**: `parent-task-calendar`
  - **验证**: 全部验收场景通过，无控制台错误

```

## openspec/changes/parent-dual-month-task-calendar/specs/parent-pages-contract/spec.md

- Source: openspec/changes/parent-dual-month-task-calendar/specs/parent-pages-contract/spec.md
- Lines: 1-34
- SHA256: 7abc002d2c0ae6e53df2e65572d2612bae9999a4d497b38d1d5a1a87d8c76f2d

```md
# parent-pages-contract Specification (Delta)

## MODIFIED Requirements

### Requirement: Task 按日期列表查询契约

parent 前端任务分配页 MUST 使用 `GET /api/task-assignments` 分页查询端点，支持以下查询模式：

1. **日历模式**：当用户在双月日历中点击日期/周/月时，前端 MUST 传入 `startDate` + `endDate` 过滤对应时间范围内任务列表
2. **查看全部模式**：当用户点击「查看全部」按钮时，前端 MUST 不传 `startDate` 和 `endDate` 参数，保留 `taskType` 筛选条件，查询符合类型的所有分配记录

前端调用 `GET /api/task-assignments` MUST 支持 `page`、`pageSize`、`startDate`、`endDate`、`taskType`（可选，逗号分隔多值）、`childId`（可选）查询参数。

前端 MUST NOT 调用 `/api/task-assignments/calendar` 端点获取任务列表（该端点为日历聚合视图专用，返回 `{year, month, days: {...聚合统计}}` 形状，非任务列表）。获取日历颜色标记数据时，前端 SHALL 调用 `/api/task-assignments/calendar` 端点。

#### Scenario: 前端按日历点击查询当天任务列表

- **WHEN** parent 前端任务分配页 `useApi<PageResult<TaskAssignment>>('/task-assignments?page=1&pageSize=50&startDate=2026-07-13&endDate=2026-07-13')` 调用 `GET /api/task-assignments`
- **THEN** 系统 MUST 返回 HTTP 200，响应体 `data` 为 `PageResult<TaskAssignment>`（`{content: TaskAssignment[], page, pageSize, totalElements, totalPages}`），`content` 字段仅包含 `deadline` 在 `[startDate 00:00, endDate 23:59:59]` 区间内的任务分配

#### Scenario: 前端查看全部模式不传日期参数

- **WHEN** parent 前端任务分配页 `useApi<PageResult<TaskAssignment>>('/task-assignments?page=1&pageSize=20&taskType=LIMITED')` 调用 `GET /api/task-assignments`
- **THEN** 系统 MUST 返回 HTTP 200，`content` 字段包含所有 `snapshotTemplateTaskType` 为 `LIMITED` 的分配记录（分页），不受日期范围限制

#### Scenario: 后端 task-assignments 端点支持日期过滤

- **WHEN** 任意请求调用 `GET /api/task-assignments?page=1&pageSize=50&startDate=2026-07-13&endDate=2026-07-13` 且已认证为 PARENT 角色
- **THEN** 系统 MUST 返回 HTTP 200，`PageResult.content` 中的 TaskAssignment 其 `deadline` MUST 落在 `startDate` 至 `endDate` 区间内（端到端验证前端按日期过滤正确）

#### Scenario: 后端 task-assignments 端点支持任务类型过滤

- **WHEN** 请求调用 `GET /api/task-assignments?page=1&pageSize=50&taskType=LIMITED,REPEAT` 且已认证为 PARENT 角色
- **THEN** 系统 MUST 返回 HTTP 200，`content` 仅包含 `snapshotTemplateTaskType` 为 `LIMITED` 或 `REPEAT` 的分配

```

## openspec/changes/parent-dual-month-task-calendar/specs/parent-task-calendar/spec.md

- Source: openspec/changes/parent-dual-month-task-calendar/specs/parent-task-calendar/spec.md
- Lines: 1-111
- SHA256: ce0ff54145d87a15c7f77bb9446dea7ac57285d4dd03fa612694a0ada00e3397

[TRUNCATED]

```md
# parent-task-calendar Specification

## Purpose

定义家长端任务分配页面双月日历 UI 组件的行为规范，包括颜色标记、交互筛选和任务列表联动。

## ADDED Requirements

### Requirement: 双月日历渲染

家长任务分配页面 SHALL 使用双月日历视图替代原有日期选择器。日历 MUST 同时显示当月和下月。桌面视口（宽度 ≥ 768px）下两月日历 MUST 左右并排显示，移动视口（宽度 < 768px）下 MUST 上下堆叠显示。日历 SHALL 基于 Ant Design Calendar 组件实现，每个日期单元格 MUST 通过自定义渲染显示任务分布信息。

#### Scenario: 桌面端双月并排

- **WHEN** 家长在宽度 ≥ 768px 的设备上打开任务分配页面
- **THEN** 日历区域显示当月和下月两个日历面板左右并排

#### Scenario: 移动端双月堆叠

- **WHEN** 家长在宽度 < 768px 的设备上打开任务分配页面
- **THEN** 日历区域显示当月日历在上、下月日历在下，垂直堆叠

#### Scenario: 日历数据加载

- **WHEN** 日历组件初始化
- **THEN** 前端 MUST 分别请求当月和下月的 `GET /api/task-assignments/calendar?year=X&month=X`，并将返回的任务类型统计渲染到对应日期单元格

### Requirement: 任务类型颜色标记

日历日期单元格 SHALL 根据该日是否存在任务分配及其任务类型进行颜色标记。无任务的日期 MUST 保持默认样式。有任务的日期 MUST 按以下优先级色显示：当日有 LIMITED 类型任务时显示红色系；否则有 REPEAT 类型时显示蓝色系；否则有 STANDING 类型时显示绿色系。当日有多个任务时，单元格右上角 MUST 显示任务总数徽章。

#### Scenario: 某日有多种类型任务

- **WHEN** 2026-07-15 有 2 个 LIMITED 任务和 1 个 REPEAT 任务
- **THEN** 该日期单元格显示红色系背景（LIMITED 优先级最高），右上角显示徽章「3」

#### Scenario: 某日仅有 STANDING 任务

- **WHEN** 2026-07-20 仅有 1 个 STANDING 任务
- **THEN** 该日期单元格显示绿色系背景，右上角显示徽章「1」

#### Scenario: 某日无任务

- **WHEN** 2026-07-25 没有任何任务分配
- **THEN** 该日期单元格显示默认样式，无徽章

### Requirement: 周号指示器

日历左侧 SHALL 显示 ISO 8601 周号。当周内任一天有任务分配时，该周号单元格 MUST 显示颜色标记（与优先级最高的任务类型同色），无任务的周 MUST 保持默认样式。周号 MUST 可点击。

#### Scenario: 当周有任务

- **WHEN** 2026 年第 30 周内至少有一天存在任务分配
- **THEN** 该周号单元格显示颜色标记

#### Scenario: 当周无任务

- **WHEN** 2026 年第 31 周内没有任何任务分配
- **THEN** 该周号单元格显示默认样式

### Requirement: 日历点击交互

用户 SHALL 能够通过点击日历的日、周、月三级粒度来筛选下方任务列表。点击日期单元格 MUST 触发查询该日期的任务列表；点击周号 MUST 触发查询该周（周一至周日）的任务列表；点击月面板头部 MUST 触发查询该月的任务列表。当前选中的日/周/月 MUST 有视觉高亮状态区分。

#### Scenario: 点击日期查看当天任务

- **WHEN** 家长点击 2026-07-15 的日期单元格
- **THEN** 下方任务列表刷新为 `startDate=2026-07-15&endDate=2026-07-15` 的查询结果，且 7 月 15 日单元格显示选中高亮

#### Scenario: 点击周号查看当周任务

- **WHEN** 家长点击第 30 周的周号
- **THEN** 下方任务列表刷新为该周周一至周日的日期范围查询结果，且该周号显示选中高亮

#### Scenario: 周号跨月边界查询完整周

- **WHEN** 家长点击第 31 周的周号，该周的一部分日期属于 7 月、另一部分属于 8 月
- **THEN** 下方任务列表 MUST 查询该周周一至周日的完整日期范围（含跨月部分）

#### Scenario: 点击月头查看当月任务

```

Full source: openspec/changes/parent-dual-month-task-calendar/specs/parent-task-calendar/spec.md

## openspec/changes/parent-dual-month-task-calendar/specs/task-assignment/spec.md

- Source: openspec/changes/parent-dual-month-task-calendar/specs/task-assignment/spec.md
- Lines: 1-47
- SHA256: 6242a1c972beb93b3aaaea05d68a5e092e1e2de7e281022f4d7809048a06c2db

```md
# task-assignment Specification (Delta)

## MODIFIED Requirements

### Requirement: 分页列表与日历查询

家长 SHALL 能够查询家庭内分配，孩子 SHALL 仅能查询自己的分配。列表 SHALL 支持按孩子、模板、任务类型（基于 `snapshotTemplateTaskType` 字段，逗号分隔多值）、审核状态、取消标记、逾期标记以及本地截止日期范围筛选；默认页大小 MUST 为 20，页大小 MUST 为 1 至 100，结果 MUST 按截止时间升序、分配标识升序稳定排序。日期范围 MUST 使用实例时区且最长为 366 个本地日；日期范围参数为可选，不传入时 MUST 不应用日期过滤。月历查询 SHALL 接受合法的本地年月，并按本地自然日返回每天的总数、各任务类型（`taskTypes`：`{LIMITED: n, REPEAT: n, STANDING: n}`）计数、各审核状态、取消和逾期计数；月历和列表 MUST 使用同一可见性规则和派生逻辑。无效查询 MUST 返回 `TASK_ASSIGNMENT_INVALID_QUERY`。

#### Scenario: 家长按孩子和日期筛选

- **WHEN** 家长查询某孩子在 2026-04-01 至 2026-04-30 的分配且页大小为 20
- **THEN** 系统仅返回当前家庭内符合条件的分配、稳定分页信息及其创建时快照

#### Scenario: 孩子查看自己的日历

- **WHEN** 孩子请求 2026-04 月历
- **THEN** 系统按 `Asia/Shanghai` 的本地自然日聚合且仅返回分配给该孩子的状态、取消和逾期计数，以及按任务类型统计

#### Scenario: 日期筛选边界

- **WHEN** 查询本地日期范围为 2026-04-08 至 2026-04-08
- **THEN** 系统包含截止本地日期恰好为 2026-04-08 的全部可见分配，不包含相邻日期

#### Scenario: 查询参数不合法

- **WHEN** 请求提交页大小 101、非法年月、未知状态或超过 366 日的日期范围
- **THEN** 系统返回错误码 `TASK_ASSIGNMENT_INVALID_QUERY`，且不返回部分结果

#### Scenario: 按任务类型筛选

- **WHEN** 家长查询 `taskType=LIMITED` 的分配列表
- **THEN** 系统仅返回 `snapshotTemplateTaskType` 为 `LIMITED` 的分配

#### Scenario: 按多个任务类型筛选

- **WHEN** 家长查询 `taskType=LIMITED,REPEAT` 的分配列表
- **THEN** 系统返回 `snapshotTemplateTaskType` 为 `LIMITED` 或 `REPEAT` 的分配

#### Scenario: 月历查询包含任务类型统计

- **WHEN** 家长请求 2026-07 月历
- **THEN** 每日返回数据包含 `taskTypes` 字段，如 `{"LIMITED": 2, "REPEAT": 1, "STANDING": 0}`，与 `total`、`pending` 等状态计数并列

#### Scenario: 无日期范围查询全部

- **WHEN** 家长查询 `taskType=STANDING` 但不传入 `startDate` 和 `endDate`
- **THEN** 系统返回所有 `snapshotTemplateTaskType` 为 `STANDING` 的分配（分页），不受日期限制

```
