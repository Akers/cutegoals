# Proposal: fix-calendar-default-current-date

## Why

家长端「任务分配」页面（`/parent/tasks`）的双月日历存在默认高亮 bug：

- 进入页面或刷新时，日历对**当前所在月份**面板总是高亮「1号」，而不是当前日期（截图所示：今天 2026-07-24，但 7 月 1 日被高亮）。
- 下个月份面板也固定高亮其 1 号（截图 8 月 1 号被高亮），这是 antd `<Calendar>` 默认行为的副产品，与本 bug 同源但不是用户当前关心点。
- 自定义 `boxShadow` 高亮（selectedRange 派生）从未初始化生效，因为 `selectedRange` 初值为 `null`，所以即便 antd 错误高亮被纠正，「选中」也不会落到今天。

用户期望：进入任务分配页面，今日所在面板应默认选中并高亮今天（与 antd 内置高亮 + 自定义 `boxShadow` 两层一致），而其它非今日面板保持当前视觉行为不变。

## 根因分析

来源：`web/src/parent/components/TaskCalendar.tsx` 与 `web/src/parent/pages/index.tsx`。

### 根因 1：antd `<Calendar value={monthDate}>` 强制每日历自动高亮 `value.date()`（即每月 1 号）

```tsx
// TaskCalendar.tsx:240
const monthDate = dayjs(`${year}-${String(month).padStart(2, '0')}-01`);
// ...
<Calendar value={monthDate} ... />
```

antd `<Calendar>` 的 `value` 同时承担两个语义：

1. 决定显示哪个月份（year/month 维度）。
2. 决定哪个 day 因 antd 内置样式被自动高亮（即 `value.date()`）。

`monthDate` 永远是 `01` 号，所以两个面板的「自动高亮日」都被钉死在各自月份 1 号。

### 根因 2：`ParentTasksPage` 初始化 `selectedRange: null`，自定义 `boxShadow` 高亮永不落在今天

```tsx
// pages/index.tsx:1046
selectedRange: null,
```

`CalendarPanel.renderDateCell` 用 `selectedRange` 推导 `isSelected`：

```tsx
const isSelected =
  selectedRange && dateStr >= selectedRange.startDate && dateStr <= selectedRange.endDate;
```

初值 `null` → `isSelected === false` 全月 → 没有 boxShadow 高亮。两层高亮都不指向今天。

## What Changes

修改 `web/src/parent/pages/index.tsx` 与 `web/src/parent/components/TaskCalendar.tsx`：

1. **`pages/index.tsx`**：`useReducer` 初值 `selectedRange` 从 `null` 改为今天日期的 day-类型 selection。`baseMonth` 保持 `now.format('YYYY-MM')`（与今日同月，保证 today 在可见月）。
2. **`TaskCalendar.tsx CalendarPanel`**：在传给 `<Calendar>` 的 `value` 时，**先判断 today 是否在本面板**：
   - 是本面板（year/month 与 today 一致）→ `value = dayjs()`，让 antd 自动高亮今天；
   - 不是本面板 → `value = selectedRange ? dayjs(selectedRange.startDate) : monthDate`，保留既有回退。

约束：本周仅触碰 `selectedRange` 初值与 `<Calendar value>` 两个接入点，不修改 `selectedRange` reducer 逻辑、不修改 `renderDateCell` 高亮判定、不修改布局/几何。

## Impact

- 受影响用户：所有访问 `/parent/tasks` 的家长端用户。
- 受影响组件：`ParentTasksPage`、`TaskCalendar`（含两个 `CalendarPanel`）。
- 不影响：后端、其他家长端页面、儿童端、管理员端；reducer 纯函数行为；WeekNumberColumn；dateCellRender 的颜色/角标/badge 计算；日历几何对齐。

## Non-Goals

- 不重设计日历选中态视觉、不引入新视觉语言。
- 不为非今日面板更改默认高亮策略（保留 monthDate 回退）。
- 不重构 `calendarReducer`、`buildQuery`、`TaskCalendarProps` 协议。
- 不引入 delta spec（与 2026-07-24 archived `fix-task-calendar-cell-render` 同源，无 capability 层验收场景变化）。
- 不修全量 lint 中的 pre-existing 错误。
