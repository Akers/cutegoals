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
