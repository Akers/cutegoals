# Proposal: fix-calendar-non-current-no-highlight

## Why

紧接 `fix-calendar-default-current-date`（archived）后，家长端「任务分配」页面（`/parent/tasks`）的双月日历仍残留一个视觉问题：

- **非当前月份面板**（截图红框所示，如 2026-08 面板）当前依然高亮其「1 号」（绿色背景）。
- 上次 hotfix 只解决了「当前月高亮 1 号」的 bug，但明确把「非当前月高亮 1 号」保留作为 out-of-scope（`fix-calendar-default-current-date/tasks.md 备注`）。
- 用户在新反馈中明确要求：「对于非当前月份的日历，不需要高亮，... 红框部分不需要高亮」。

用户期望：进入页面后，只有今日（位于当前月面板）被双层高亮（antd 内置 + 自定义 boxShadow），非当前月面板没有任何 cell 被 antd 内置样式高亮。

## 根因分析

来源：`web/src/parent/components/TaskCalendar.tsx`。

```tsx
// fix-calendar-default-current-date 之后
const calendarValue = isCurrentMonth ? today : monthDate;
// ...
<Calendar value={calendarValue} ... />
```

- 当前月面板：`value=today` → antd 内置高亮今日 — 正确。
- 非当前月面板：`value=monthDate='2026-08-01'` → antd 内置高亮 `value.date()` 即 8 月 1 号 — **错误，用户不要这个高亮**。

`monthDate` 既是「当前月不命中时的 fallback」也是「非当前月时 antd 自动高亮的源头」。上次 hotfix 选 `monthDate` 是为了「不破坏 antd 自动显示月份」，但它同时承担了「触发 1 号高亮」的副作用。本 change 显式分拆这两个语义：用 `defaultValue={monthDate}` 承担「显示月份」、用 `value=undefined`（即不传）承担「无内置高亮」。

## What Changes

修改 `web/src/parent/components/TaskCalendar.tsx` 的 CalendarPanel `<Calendar>` 调用：

- 新增 `defaultValue={monthDate}` 显式告知 antd 显示哪个月份（替代之前仅靠 `value` 隐式推断）。
- 把 `<Calendar value={calendarValue}>` 改为 `<Calendar value={isCurrentMonth ? today : undefined}>`：当前月仍 antd 高亮今日；非当前月 `value=undefined` → antd 不内置高亮任何 cell。

约束：不修改 `calendarReducer`、不修改 `renderDateCell` 的颜色 / badge / boxShadow 计算（selectedRange 派生的高亮逻辑保持不变），不动日历几何对齐、不动 WeekNumberColumn、不动 dateCellRender 渲染协议。

## Impact

- 受影响用户：所有访问 `/parent/tasks` 的家长端用户。
- 受影响组件：`TaskCalendar` 的 `CalendarPanel` 渲染（仅 `<Calendar>` props 一次接入点）。
- 不影响：reducer 逻辑、自定义 boxShadow 高亮、WeekNumberColumn、dateCellRender 几何/对齐、CalendarHeader、buildQuery、selectedRange 协议、后端 API。

## Non-Goals

- 不修改日历选中态视觉、不引入新视觉语言。
- 不修跨月导航场景（baseMonth ≠ today 所在月）的 boxShadow 跨月分布问题（超出本次用户反馈范围；如未来需要统一，另起 change）。
- 不重构 `calendarReducer`、`TaskCalendarProps` 接口。
- 不修改 `TaskCalendar.test.tsx` 中已有的「非当前月面板：value 回退 monthDate」断言（改为「非当前月面板：value=undefined，defaultValue=monthDate」）。
- 不引入 delta spec（与 `fix-calendar-default-current-date` 同源，无 capability 层验收场景变化）。
