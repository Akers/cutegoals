# Comet Design Handoff

- Change: parent-task-calendar-single-month
- Phase: design
- Mode: compact
- Context hash: df2c3ca21d0da339a44354878c85e9f3f49836592997f0e23f20ee1242bcf30d

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/parent-task-calendar-single-month/proposal.md

- Source: openspec/changes/parent-task-calendar-single-month/proposal.md
- Lines: 1-32
- SHA256: 76a93d4b4c823f53fca078bdd91e71f766d4a851a356791abfca518e303d3b2c

```md
## Why

家长任务分配页 `/parent/tasks` 当前 TaskCalendar 组件显示双月（当月 + 下月），桌面端两月并排、移动端两月堆叠。在常见任务分配场景下，绝大多数家长只为当前月分配任务，下月数据需要手动滚动或点按 "<>" 切换才能看到更多，反而挤压了单月的可读性。希望简化 TaskCalendar 为单月日历视图，下月/上月数据通过既有的导航按钮 "<>" 切换 baseMonth 查看。

## What Changes

- TaskCalendar 组件从双月视图改为单月视图：每次只渲染当前 baseMonth 一个月的日历面板
- 移除第二个 CalendarPanel（nextMonth）的渲染以及它对 `/task-assignments/calendar` 的并行请求
- 导航栏标题从 `2026年7月 — 2026年8月` 改为单一月份 `2026年7月`
- 简化 CSS：移除 `.task-calendar-non-current-month-*` 相关的"非当前月面板"样式（单月无此概念），保留 `.task-calendar-current-month-*` 全部规则
- CalendarPanel 子组件保持原样，仍可被父组件按 (year, month) 复用；TaskCalendar 主组件只渲染一个 CalendarPanel
- 保留导航按钮 ±1 月（`onNavigate` 行为不变）、保留 `onSelect`（day/week/month 三级粒度）、保留日历选中/今天/周号高亮以及 CalendarPanel 数据获取机制
- 保留 `web/src/parent/pages/index.tsx` 中 ParentTasksPage 全部任务列表、任务类型筛选、分配弹窗等逻辑

**BREAKING** 双月视图作为规范要求在 `openspec/specs/parent-task-calendar/spec.md` 的 `### Requirement: 双月日历渲染` 中被定义；本次变更同时是 spec-level 变更，delta spec 将该 requirement 改写为单月表述。

## Capabilities

### New Capabilities
（无新增 capability）

### Modified Capabilities

- `parent-task-calendar`：`### Requirement: 双月日历渲染` 改写为 `### Requirement: 单月日历渲染`；桌面/移动场景、Calendar API 请求模式随之调整；新增"通过导航按钮切换月份"场景覆盖 `onNavigate` 行为。

## Impact

- 主要代码：`web/src/parent/components/TaskCalendar.tsx`（约 50 行修改 + 30 行删除）
- 测试：`web/src/parent/components/TaskCalendar.test.tsx` 需同步更新用例（双月 → 单月）
- 数据接口：每次进入日历页面，请求数从 2 × `/task-assignments/calendar` 减为 1 × `/task-assignments/calendar`
- 不影响范围：`web/src/parent/pages/index.tsx`（ParentTasksPage 主体）、CalendarPanel / WeekNumberColumn / CalendarHeader 子组件、`CalendarSelection` / `CalendarAction` / `CalendarAction2` 类型、`calendarReducer` 状态机、`selectedRange` 行为
- 不影响后端：`/task-assignments/calendar` 接口契约不变

```

## openspec/changes/parent-task-calendar-single-month/design.md

- Source: openspec/changes/parent-task-calendar-single-month/design.md
- Lines: 1-113
- SHA256: 663ab95ccf20328770ccd0a553d3ed4f531795c90e67c5d5e2ed0137dc6903b7

[TRUNCATED]

```md
## Context

家长任务分配页 `/parent/tasks` 当前挂载的 TaskCalendar 组件（`web/src/parent/components/TaskCalendar.tsx`）渲染**双月**日历：桌面端左右并排、移动端上下堆叠。该组件由 2026-07-22 归档的 `parent-dual-month-task-calendar` change 引入，配套 proposal/design/tasks 全部基于"双月"前提设计。

TaskCalendar 内部结构：
- `TaskCalendar`（主组件）：解析 `baseMonth`，计算 `currentMonth` 与 `nextMonth`，管理导航栏与 Grid 容器
- `CalendarPanel`（子组件）：单月面板，独立 fetch `/task-assignments/calendar?year=X&month=X`；通过 `className` `task-calendar-current-month` 或 `task-calendar-non-current-month` 区分视觉
- `WeekNumberColumn` / `CalendarHeader`：单月内部子组件

父组件 `ParentTasksPage`（`web/src/parent/pages/index.tsx`）通过 `calendarReducer` 管理 `selectedRange` 状态，使用 `baseMonth` 字段做月份导航。父组件不依赖 TaskCalendar 内部布局（只通过 `baseMonth / selectedRange / onSelect / onNavigate` 四个 props 交互）。

## Goals / Non-Goals

**Goals:**
- TaskCalendar 每次只渲染 `baseMonth` 一个月的日历面板
- 视觉、交互（day/week/month 选择、选中/今天/周号高亮、响应式、加载/错误态）行为不变
- 每次进入日历页面只发 1 次 `/task-assignments/calendar` 请求（双月时为 2 次）
- 不修改 ParentTasksPage、CalendarPanel 子组件、CalendarSelection/Action 类型、reducer、后端

**Non-Goals:**
- 不删除 ParentTasksPage / TaskCalendar 组件
- 不改变导航按钮 ±1 月的行为（`onNavigate` 仍由父组件控制）
- 不重做 antd Calendar 组件的内部交互
- 不合并多任务请求（仍按需按月请求）
- 不修改后端 `/task-assignments/calendar` 接口契约
- 不做"下月预览"或"两月合并"等渐进增强

## Decisions

### Decision 1：单月独立面板（而非双月堆叠）

选择单月独立面板渲染（去掉第二个 CalendarPanel），而不是双月 → 上下堆叠。

理由：
- 单月面板在桌面端占满容器可用宽度，任务徽章、cell 密度可读性提升
- 移动端天然就是单月布局（小屏横向空间不够"并排"），改造前后行为等价
- 简化 CSS：移除 `.task-calendar-non-current-month-*` 规则，单月无"当前/非当前月"区分
- 减少 1 × `/task-assignments/calendar` API 调用

替代方案：保留双月但移动端上下堆叠 → 拒绝，任务目标本身是"简化为单月"。

### Decision 2：保留 CalendarPanel 子组件 + 复用模式

TaskCalendar 主组件只 new 一个 CalendarPanel；CalendarPanel 组件签名/行为不变。

理由：
- 现有 CalendarPanel 是独立数据获取单元，自带 loading/error/refetch 行为，复用风险最低
- 未来若需重新引入双月，只需在 TaskCalendar 中再加一个 CalendarPanel（参数 `nextMonth.year()/nextMonth.month()+1`）
- 不破坏父组件任何依赖（`weekLabelRender` / `dateCellRender` 等都不依赖月数）

### Decision 3：导航按钮保留

保留 `<` `>` 按钮，由父组件 `onNavigate` 控制 `baseMonth` 切换。

理由：
- 单月后用户仍需查看其他月份（特别是下月做下月任务规划）
- 导航按钮是 selectedRange 跨月交互的唯一入口（cell 不能跨月点击）
- 父组件 `calendarReducer` 的 `NAV_MONTH` action 已实现，无需改动

### Decision 4：非当前月 CSS 移除

CSS 块中所有 `.task-calendar-non-current-month-*` 规则删除：
- 非当前月面板的 cell-selected 透明覆盖
- 非当前月面板 today 浅蓝边框

理由：
- 单月后所有面板都是"当前月"，原"非当前月抑制高亮"逻辑失效
- 保留会引入无意义 CSS 规则

替代方案：保留 CSS 以防未来回滚到双月 → 拒绝，YAGNI 原则；旧规则在 git 历史中可恢复。

### Decision 5：任务徽章视觉与数据协议不变

`dateCellRender` 输出的红色任务总徽章（`top: -26, left: 20`）保持不变；数据协议 `CalendarData.days[dateKey].total` 也不变。

理由：
- 视觉一致性优先（家长用户已经熟悉徽章位置）
- 数据协议（`DayData.total`）不变 → 后端 E2E 与契约测试零修改

### Decision 6：响应式容器简化

```

Full source: openspec/changes/parent-task-calendar-single-month/design.md

## openspec/changes/parent-task-calendar-single-month/tasks.md

- Source: openspec/changes/parent-task-calendar-single-month/tasks.md
- Lines: 1-17
- SHA256: cd77a264fe7d07d41a81f030f07e95bd6bdf955455756b8c806167fe8e45560d

```md
## 1. 单月化 TaskCalendar 组件

- [ ] 1.1 修改 TaskCalendar 移除第二个 CalendarPanel：删除 `nextMonth` 计算与第二个 `<CalendarPanel>` 渲染（web/src/parent/components/TaskCalendar.tsx 约 437-451 行）
- [ ] 1.2 简化导航栏标题：移除 `{currentMonth.format('YYYY年M月')} — {nextMonth.format('YYYY年M月')}` 中的 ` — {nextMonth.format(...)}` 部分，只保留当前月标题
- [ ] 1.3 简化 CSS：删除 `.task-calendar-non-current-month-*` 全部规则（非当前月面板 cell-selected 透明覆盖、非当前月 today 浅蓝边框），保留 `.task-calendar-current-month-*` 全部规则
- [ ] 1.4 简化 Grid 容器：`.task-calendar-grid` 样式从 `display: grid; grid-template-columns: repeat(auto-fit, minmax(400px, 1fr))` 改为 `display: block; width: 100%`；移除 `<style>` 块中的 `@media (max-width: 767px)` 响应式覆盖

## 2. 同步更新单元测试

- [ ] 2.1 更新 `web/src/parent/components/TaskCalendar.test.tsx`：删除双月场景断言（双 CalendarPanel 渲染、双 API 请求、双月标题）
- [ ] 2.2 添加单月场景断言：单 CalendarPanel 渲染、导航按钮切换 baseMonth 触发单次 API 请求、单月标题格式

## 3. 验证

- [ ] 3.1 单元测试：`pnpm --filter web test TaskCalendar` 全部通过
- [ ] 3.2 类型检查：`pnpm --filter web typecheck` 通过
- [ ] 3.3 E2E：`pnpm test:e2e -- parent-task-calendar` 全部通过

```

## openspec/changes/parent-task-calendar-single-month/specs/parent-task-calendar/spec.md

- Source: openspec/changes/parent-task-calendar-single-month/specs/parent-task-calendar/spec.md
- Lines: 1-53
- SHA256: 4bc999e7b3786441c80da6232a8946ad8bb3f7f8116e3334c414f9ea05b32518

```md
## MODIFIED Requirements

### Requirement: 单月日历渲染

家长任务分配页面 SHALL 使用单月日历视图替代原有日期选择器。日历 MUST 仅显示当前 baseMonth 一个月（桌面与移动视口均使用同一布局）。日历 SHALL 基于 Ant Design Calendar 组件实现，每个日期单元格 MUST 通过自定义渲染显示任务分布信息。用户 SHALL 通过导航按钮 "<>" 切换 baseMonth 查看其他月份；切换月份后 MUST 重新请求目标月份的日历数据。

#### Scenario: 单月显示

- **WHEN** 家长在任意视口宽度下打开任务分配页面
- **THEN** 日历区域显示当前 baseMonth 一个日历面板（占满容器宽度），不显示下月或其他月份的并列面板

#### Scenario: 日历数据加载

- **WHEN** 日历组件初始化或导航按钮切换 baseMonth
- **THEN** 前端 MUST 仅请求目标月份的 `GET /api/task-assignments/calendar?year=X&month=X`（每次仅一次请求），并将返回的任务类型统计渲染到对应日期单元格

#### Scenario: 通过导航按钮切换月份

- **WHEN** 家长点击日历上方的 "<" 或 ">" 导航按钮
- **THEN** baseMonth 切换到上一月或下一月，日历面板重新渲染为目标月份，导航栏标题更新为对应"YYYY年M月"格式

### Requirement: 默认选中今日与本周

家长任务分配页面 SHALL 由父组件（ParentTasksPage）的 calendarReducer 决定默认选择状态。今天 cell 视觉由 selectedRange 范围决定：selectedRange 范围包含 today 时，today cell SHALL 显示选中态视觉（teal 实心 + 白字），与手动选中日视觉一致；selectedRange 范围不包含 today 时，today cell SHALL 显示 today 视觉（浅蓝边框 + teal 字色），区别于选中态视觉。当前周号行 SHALL 视觉高亮且区别于非选中周。日历仅显示当前 baseMonth 一月时，今天视觉 SHALL 仅在 baseMonth 包含今天时显示。

#### Scenario: 默认进入页面，今天与本周被选中

- **WHEN** 家长打开 `/parent/tasks` 任务分配页面（父组件默认 selectedRange = day + today）
- **THEN** 今天日期 cell 显示选中态视觉（teal 实心 + 白字，与手动选中日视觉一致），今天所在周号行显示选中态视觉（蓝色边框），下方任务列表查询范围为今天所在周（ISO 8601 周一到周日）

#### Scenario: 用户点击某天后 today 不再单独高亮（fix-build）

- **WHEN** 家长在已打开页面（默认选今天 + 本周）后点击某个非今天的日期 cell（如 2026-07-15）
- **THEN** 该日期 cell 显示选中态视觉，今天日期 cell 不再显示选中态视觉，改由 today 视觉呈现（浅蓝边框 + teal 字色；today 高亮由 antd 自动标记 today 类 + CSS today 规则命中，与 selectedRange 范围无关），下方任务列表查询该日期的任务

#### Scenario: 用户点击非本周周号，今天仍高亮

- **WHEN** 家长点击非今天所在周的周号行
- **THEN** 该周号行显示选中态视觉，今天所在周号行不高亮（仅单个周号行高亮），今天日期 cell 显示 today 视觉（浅蓝边框 + teal 字色，由 antd 自动 + CSS 命中），下方任务列表查询所点击周的范围

#### Scenario: baseMonth 不包含今天时今天不显示高亮

- **WHEN** 用户通过导航按钮将 baseMonth 切换到不包含今天日期的月份（如从 2026-07 切换到 2026-10）
- **THEN** 该日历面板内今天 cell 不显示视觉（today 高亮仅在 baseMonth 包含 today 的面板内有效）

#### Scenario: baseMonth 包含今天时的 today 视觉

- **WHEN** baseMonth 包含今天日期（如 baseMonth='2026-07' 且 today='2026-07-24'）且 selectedRange 范围不包含 today
- **THEN** today cell 由 antd 自动加 `.ant-picker-cell-today` 类，CSS 中 `.task-calendar-current-month .ant-picker-cell-today .ant-picker-calendar-date-value { color: #0d9488 }` 规则命中（teal 字色），CSS 中 `.task-calendar-current-month .ant-picker-cell-today:not(.ant-picker-cell-selected) .ant-picker-calendar-date { border: 1px solid #1677ff }` 规则命中（浅蓝边框）

#### Scenario: day 选中时该天所在周号行也高亮（fix-build）

- **WHEN** 家长选中某一天（如 2026-07-15）
- **THEN** 该日期 cell 显示选中态视觉（teal 实心 + 白字），该日期所在周号行也显示选中态视觉（蓝色边框），下方任务列表查询该日期所在周的任务
```
