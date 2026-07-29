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

**Goals (fix-build 第三次迭代修订)**

1. `selectedRange` 初值从 `{ type: 'week', 本周 }` 改为 `{ type: 'day', today }`（取消整周高亮）。
2. 仅 today cell 视觉高亮（深 teal 实心 + 白字 + 浅蓝外环）。
3. 当周周号行蓝色边框高亮（独立于 selectedRange 范围判定）。
4. 用户点击某天：取消其他天的 teal 高亮，并高亮点击的那天 + 其所在周蓝色边框。
5. 用户点击某周：移动蓝色边框到该周，取消其他周的高亮，无 cell 高亮。
6. 现有 `data-selected` / `data-bg` 属性语义不变；测试断言可断言。

**Non-Goals**

1. 不重设计周号行视觉（蓝色边框替代浅蓝背景矩形）。
2. 不修改 antd `<Calendar value>` 通道与 `task-calendar-current-month` / `task-calendar-non-current-month` className 分流。
3. 不修改 `selectedRange` reducer 纯函数。
4. 不引入新 design token；色值硬编码（与 antd parent `colorPrimary` 同源）。
5. 不修全量 lint 中的 pre-existing 错误。

## Decisions

### 决策 1（fix-build 第三次迭代修订）：`selectedRange` 初值改为 day 类型

```ts
// pages/index.tsx ParentTasksPage
const now = dayjs();
const todayStr = now.format('YYYY-MM-DD');
useReducer(calendarReducer, ..., {
  selectedRange: { type: 'day', startDate: todayStr, endDate: todayStr },
  baseMonth: now.format('YYYY-MM'),
  taskTypeFilters: ['LIMITED', 'REPEAT', 'STANDING'],
  viewAllMode: false,
});
```

**理由（fix-build 第三次迭代修订）**：用户明确要求"初始状态仅当天高亮，不需要把当周的所有天都高亮"。day 类型初值让"点击某天"逻辑与"初始 today 高亮"统一为单一选中态。

### 决策 2（fix-build 第三次迭代修订）：`dateCellRender.isSelected` 仅 type=day 单点高亮

```ts
// TaskCalendar.tsx CalendarPanel renderDateCell
const isCurrentMonthForDate =
  today.year() === year && today.month() + 1 === month;

// fix-build 第三次迭代:仅 type=day 单点高亮，取消 type=week/month 整周/整月高亮
const isSelected = !!(
  selectedRange &&
  isCurrentMonthForDate &&
  selectedRange.type === "day" &&
  dateStr === selectedRange.startDate &&
  dateStr === selectedRange.endDate
);
```

**理由（fix-build 第三次迭代修订）**：

- type=day：单 cell 高亮（startDate === endDate === 该日）
- type=week/month：周号行蓝色边框，但日期 cell 不高亮（产品明确要求）
- isCurrentMonthForDate 守卫确保非当前月面板不高亮

### 决策 3（fix-build 第三次迭代修订）：WeekNumberColumn isWeekInRange 蓝色边框独立判定

`WeekNumberColumn.isWeekInRange` 独立判定（`TaskCalendar.tsx`）：

```ts
// fix-build 第三次迭代:isWeekInRange 独立判定（蓝色边框视觉）
const isWeekInRange = !!(
  selectedRange &&
  ((selectedRange.type === 'week' &&
    weekStartDate <= selectedRange.endDate &&
    weekEndDate >= selectedRange.startDate) ||
    (selectedRange.type === 'day' &&
      selectedRange.startDate >= weekStartDate &&
      selectedRange.startDate <= weekEndDate))
);

// 样式：蓝色边框（无背景填充）
border: isWeekInRange ? '2px solid rgba(22, 119, 255, 0.6)' : undefined,
```

**理由（fix-build 第三次迭代修订）**：

- type=week：该周被选中时周号行蓝色边框
- type=day：该天所在周周号行蓝色边框
- 蓝色边框是独立视觉判定，与 dateCellRender.isSelected 互不干扰

### 决策 4（fix-build 修正）：范围匹配走 isCurrentMonthForDate 守卫

`isCurrentMonthForDate` 判定确保选中范围匹配仅在当前月面板内高亮/蓝色边框。baseMonth 默认是当前月，所以今天默认可见；用户手动 `<` `>` 切到非当前月时，匹配 selectedRange 的 cell 不高亮。

**理由（fix-build 修正）**：原决策 4 仅针对 today，但 bug c 要求所有 selectedRange 匹配都不应在非当前月面板高亮。现改为统一守卫：day/week/month 类型凡匹配 selectedRange 范围者，均需过 isCurrentMonthForDate 门槛。

### 决策 5：视觉色值硬编码

继续用 `#0d9488` / `#ffffff` / `#93c5fd` 硬编码（与 commit 263361c 一致）。不复用 `--cg-primary` / `--cg-accent` / `--ant-color-primary`：

- `--cg-primary` 在 parent 下是 `#92400e` 棕色（与 antd `colorPrimary = #0d9488` 不同）
- 项目未启用 antd cssVar 模式，`--ant-color-primary` 不可用
- 单一使用点，硬编码简单可靠

### 决策 6（fix-build 第三次迭代修订）：测试策略

按 `fix-calendar-default-current-date` 已建立的项目惯例走 TDD：

1. 修改 `ParentTasksPage.test.tsx` 断言 `selectedRange.type === 'day' && startDate/endDate 是 today`
2. 修改 `TaskCalendar.test.tsx` 更新"week selected 时整周 cell 高亮"测试 → 改为"week selected 时无 cell 高亮"
3. 修改 `TaskCalendar.test.tsx` 更新周号行背景色断言 → 改为蓝色边框断言
4. 移除依赖"整周高亮"的所有测试断言
5. 新增"初始 type=day+today 时 today cell 高亮 + 当周蓝色边框"用例
6. 新增"点击某天时取消其他天 + 蓝色边框移动到新周"用例
7. 跑 GREEN，确认全部通过

**fix-build 第三次迭代补充**：

- 修改 `TaskCalendar.test.tsx` "默认选中今日" describe 块添加 fake timers（确保 today=2026-07-24）
- 更新 `ParentTasksPage.test.tsx` 中 `data-selected` 断言从 `2026-07-20_2026-07-26` 改为 `2026-07-24_2026-07-24`
- 更新 `TaskCalendar.test.tsx` 中 type=week 测试用例，更新为"week 时无 cell 高亮"语义

## Risks / Trade-offs

| 风险                                                                                | 缓解                                                                                                |
| ----------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| today 固定高亮破坏 `selectedRange` 单值语义（用户可能误以为 today 被"选了"）        | today 高亮在 TaskCalendar 内部判定，ParentTasksPage 的 `selectedRange` 仅描述用户选择范围；语义分离 |
| 当前 selectedRange 初值变更与 `fix-calendar-default-current-date` 既有断言冲突      | 同步更新那个 change 的回归测试断言                                                                  |
| baseMonth 变更后（用户导航到非当前月）today 不可见                                  | 是符合 antd today 行为；用户已确认该语义                                                            |
| 视觉色值硬编码不跟随未来主题切换                                                    | TaskCalendar 仅 parent 使用；若未来扩展到其他角色再统一 token 化                                    |
| week-row 视觉与 dateCell 视觉不一致                                                 | 用户已确认保留差异；两种状态（范围 vs 焦点）区分有意义                                              |
| dayjs 默认 Sunday 周首与 antd Calendar zh_CN Monday 周首错位 1 天（fix-build 发现） | shared/dayjs.ts 显式 `updateLocale('en', { weekStart: 1 })` 统一对齐                                |

## Migration Plan

1. 修改 `pages/index.tsx` `ParentTasksPage` `useReducer` 初值：selectedRange 从 `type=week+本周` 改为 `type=day+today`。
2. 修改 `TaskCalendar.tsx` `WeekNumberColumn`：`isSelected` 重命名为 `isWeekInRange`，移除背景色，蓝色边框独立判定。
3. 修改 `TaskCalendar.tsx` `dateCellRender`：`isSelected` 仅 `type=day` 单点高亮，移除 type=week/month 整周高亮逻辑。
4. 修改 `TaskCalendar.test.tsx`：移除/更新"week 时整周高亮"测试，更新周号行背景色断言为蓝色边框断言，添加 fake timers。
5. 修改 `ParentTasksPage.test.tsx`：更新 `data-selected` 断言为 today 单点。
6. 运行 `prettier --write` 两个实现文件 + 两个测试文件 + design doc。
7. 运行 `vitest run` 确认全部通过。
8. **浏览器实测验证**（Task F.7）：启动 dev server + agent-browser 实际访问 `/parent/tasks`。
9. 提交代码。

**fix-build 第三次迭代实测验证**：

- 启动 dev server + agent-browser 实际访问 `/parent/tasks`
- 场景 1：初始进入页面 → 仅 today cell 高亮（teal 实心 + 白字），第31周号行蓝色边框
- 场景 2：点击 7/15（不同周）→ 7/15 高亮，7/29 不再高亮，第29周号行蓝色边框
- 场景 3：点击第30周号行 → 第30周号行蓝色边框，第29/31周号行无边框，无日期 cell 高亮

**回滚**：commit revert 即可，影响 2 实现文件 + 2 测试文件 + 1 design doc。

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
