---
comet_change: tweak-calendar-selected-day-today-style
role: technical-design
canonical_spec: openspec
---

# Design: 任务日历默认选中今日与本周

## Context

`/parent/tasks` 双月日历（基于 antd `<Calendar fullscreen={false}>`）当前选中行为：

- `ParentTasksPage.useReducer` 初值 `selectedRange`（来自 `fix-calendar-default-current-date` 已归档 change）：`{ type: 'day', startDate: today, endDate: today }`
- `dateCellRender.isSelected`（`TaskCalendar.tsx:269-275`）：仅 `type === 'day' && startDate === endDate` 时该 cell 高亮
- `WeekNumberColumn.isSelected`（`TaskCalendar.tsx:154-158`）：仅 `type === 'week' && week 在范围内` 时周号行高亮

`fix-calendar-week-row-highlight-bg`（已归档）把 selected 视觉改为"半透明浅蓝背景矩形"。本次调整把 selected 视觉改为"深 teal 实心 + 白字 + 浅蓝外环"（与 antd today 内置高亮对齐）。

用户最新需求：

1. 当前日选中样式用青绿色实心块（图片中红框标注的实际目标样式）。
2. 进入页面默认选中今天所在周（7月29日 → 第31周），并同时高亮今天 cell。

历史脉络：

- `fix-calendar-default-current-date`（已归档）：进入页面高亮 today 单点。
- `fix-calendar-non-current-no-highlight-v2`（已归档）：非当前月面板抑制 antd 内置高亮。
- `fix-calendar-week-row-highlight-bg`（已归档）：周选中用半透明浅蓝背景 + 同色边框。

## Goals / Non-Goals

**Goals**

1. `selectedRange` 初值从 `{ type: 'day', today }` 改为 `{ type: 'week', 本周一至周日 }`。
2. 今天 cell 视觉高亮（深 teal 实心 + 白字 + 浅蓝外环），与用户手动选日视觉一致。
3. 当前周号行视觉高亮（保留现有 `rgba(22, 119, 255, 0.18)` 浅蓝背景）。
4. today 视觉与 selectedRange.type 无关：用户点击任何日/周/月，today cell 仍高亮。
5. 现有 `data-selected` / `data-bg` 属性语义不变；测试断言可断言。

**Non-Goals**

1. 不重设计周号行视觉（保留浅蓝背景矩形）。
2. 不修改 antd `<Calendar value>` 通道与 `task-calendar-current-month` / `task-calendar-non-current-month` className 分流。
3. 不修改 `selectedRange` reducer 纯函数。
4. 不引入新 design token；色值硬编码（与 antd parent `colorPrimary` 同源）。
5. 不修全量 lint 中的 pre-existing 错误。

## Decisions

### 决策 1：`selectedRange` 初值改为 week 类型

```ts
// pages/index.tsx ParentTasksPage
const now = dayjs();
const weekStart = now.startOf('week');  // 需 dayjs weekday 插件（已注册，见 web/src/shared/dayjs.ts）
const weekEnd = now.endOf('week');
const initialSelectedRange = {
  type: 'week' as const,
  startDate: weekStart.format('YYYY-MM-DD'),
  endDate: weekEnd.format('YYYY-MM-DD'),
};
useReducer(calendarReducer, ..., { selectedRange: initialSelectedRange, baseMonth: now.format('YYYY-MM'), taskTypeFilters: ..., viewAllMode: false });
```

**理由**：用户明确要求"默认选周"。week 类型触发 WeekNumberColumn 视觉高亮，dateCellRender 内 today 独立判定。

### 决策 2：`dateCellRender.isSelected` 改为 selectedRange 范围判定（fix-build：today 不再独立分支）

```ts
// TaskCalendar.tsx CalendarPanel renderDateCell
const today = dayjs();
const todayStr = today.format("YYYY-MM-DD");
const isCurrentMonthForDate =
  today.year() === year && today.month() + 1 === month;

const isSelected = !!(
  selectedRange &&
  isCurrentMonthForDate &&
  ((selectedRange.type === "day" &&
    dateStr === selectedRange.startDate &&
    dateStr === selectedRange.endDate) ||
    ((selectedRange.type === "week" || selectedRange.type === "month") &&
      dateStr === todayStr &&
      dateStr >= selectedRange.startDate &&
      dateStr <= selectedRange.endDate))
);
```

**理由（fix-build 修正）**：

- 原决策 2 采用 today 独立判定分支，导致用户点击非 today 日期后 today 仍高亮（bug a）
- fix-build 删除 today 独立分支，today 高亮完全由 selectedRange 范围决定
- type=day：单 cell 高亮
- type=week/month：仅 today（在范围内）高亮；周/月末内其他日期 cell 不高亮（产品要求：week/month 选中只高亮周号行）
- isCurrentMonthForDate 守卫确保非当前月面板不高亮（bug c）

### 决策 3：week-row 高亮与 dateCell 高亮分离

`WeekNumberColumn.isSelected` 保持原样（`TaskCalendar.tsx:154-158`）：

```ts
const isSelected = selectedRange && selectedRange.type === 'week' && ...;
```

**理由**：用户已确认保留 week-row 现有视觉（半透明浅蓝背景）。两种高亮视觉不冲突——cell 高亮表示"焦点日"，week-row 高亮表示"范围圈选"。

### 决策 4（fix-build 修正）：范围匹配走 isCurrentMonthForDate 守卫

`isCurrentMonthForDate` 判定确保选中范围匹配仅在今天所在月面板内高亮。baseMonth 默认是当前月，所以今天默认可见；用户手动 `<` `>` 切到非当前月时，匹配 selectedRange 的 cell 不高亮。

**理由（fix-build 修正）**：原决策 4 仅针对 today，但 bug c 要求所有 selectedRange 匹配都不应在非当前月面板高亮。现改为统一守卫：day/week/month 类型凡匹配 selectedRange 范围者，均需过 isCurrentMonthForDate 门槛。

### 决策 5：视觉色值硬编码

继续用 `#0d9488` / `#ffffff` / `#93c5fd` 硬编码（与 commit 263361c 一致）。不复用 `--cg-primary` / `--cg-accent` / `--ant-color-primary`：

- `--cg-primary` 在 parent 下是 `#92400e` 棕色（与 antd `colorPrimary = #0d9488` 不同）
- 项目未启用 antd cssVar 模式，`--ant-color-primary` 不可用
- 单一使用点，硬编码简单可靠

### 决策 6：测试策略 — TDD RED → GREEN

按 `fix-calendar-default-current-date` 已建立的项目惯例走 TDD：

1. 修改 `ParentTasksPage.test.tsx` 断言 `selectedRange.type === 'week' && startDate/endDate 是本周范围`
2. 修改 `TaskCalendar.test.tsx` 新增"默认进入 today 高亮"用例（断言 `backgroundColor === 'rgb(13,148,136)'`）
3. 新增"默认进入当前周号行高亮"用例
4. 新增"用户点选非今天日期后 today 仍高亮"用例
5. 跑 RED，确认失败
6. 改实现，跑 GREEN

## Risks / Trade-offs

| 风险                                                                           | 缓解                                                                                                |
| ------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------- |
| today 固定高亮破坏 `selectedRange` 单值语义（用户可能误以为 today 被"选了"）   | today 高亮在 TaskCalendar 内部判定，ParentTasksPage 的 `selectedRange` 仅描述用户选择范围；语义分离 |
| 当前 selectedRange 初值变更与 `fix-calendar-default-current-date` 既有断言冲突 | 同步更新那个 change 的回归测试断言                                                                  |
| baseMonth 变更后（用户导航到非当前月）today 不可见                             | 是符合 antd today 行为；用户已确认该语义                                                            |
| 视觉色值硬编码不跟随未来主题切换                                               | TaskCalendar 仅 parent 使用；若未来扩展到其他角色再统一 token 化                                    |
| week-row 视觉与 dateCell 视觉不一致                                            | 用户已确认保留差异；两种状态（范围 vs 焦点）区分有意义                                              |

## Migration Plan

1. 修改 `TaskCalendar.tsx` `dateCellRender`：`isSelected` 增加 today 判定分支（替换现有 `line 269-275`）。
2. 修改 `pages/index.tsx` `ParentTasksPage` `useReducer` 初值：selectedRange 从 `type=day+today` 改为 `type=week+本周`。
3. 修改 `ParentTasksPage.test.tsx`：更新 selectedRange 初值断言。
4. 修改 `TaskCalendar.test.tsx`：更新现有 selected 视觉用例 + 新增 today 固定高亮用例 + 新增周号行高亮用例。
5. 运行 `prettier --write` 两个文件。
6. 运行 `pnpm --filter web test -- TaskCalendar ParentTasksPage` 确认 RED → GREEN。
7. 运行全量 `pnpm --filter web test` 确认无回归。
8. 提交代码 + 更新 tasks.md 标记完成。
9. 更新 delta spec `parent-task-calendar/spec.md`（已完成本 design 阶段）。

**回滚**：commit revert 即可，影响 2 文件 + 2 测试文件。

## Open Questions

- 是否需要在 `dateCellRender` 增加 `data-today` 属性供测试断言？今天 cell 已经有 `data-selected='true'`，已经足够断言。
- 周号行的 `data-selected` 已有保留，不需修改。
- `baseMonth` 仍保持当前月（与既有行为一致）。

## Reference

- brainstorm-summary: `openspec/changes/tweak-calendar-selected-day-today-style/.comet/handoff/brainstorm-summary.md`
- delta spec: `openspec/changes/tweak-calendar-selected-day-today-style/specs/parent-task-calendar/spec.md`
- 现有 implementation: `web/src/parent/components/TaskCalendar.tsx:269-296`（commit 263361c 之后）
- 现有 reducer: `web/src/parent/pages/index.tsx` ParentTasksPage useReducer
- 既有归档: `openspec/changes/archive/2026-07-24-fix-calendar-default-current-date/`、`2026-07-24-fix-calendar-week-row-highlight-bg/`
