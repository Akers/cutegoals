# Proposal: tweak-calendar-selected-day-today-style

## Why

家长端「任务分配」页面（`/parent/tasks`）的双月日历中，**当前选中日**（用户点击某天后落入 selectedRange）的视觉与**默认当前日**（today）不一致：

- **默认当前日（today）**：antd `<Calendar value={today}>` 触发的内置高亮 — 深青绿色实心矩形背景（parent 角色 `colorPrimary` = `#0d9488`）、白色日期数字、外层浅蓝焦点环、右上角红色任务徽章。
- **当前选中日（selected）**：`web/src/parent/components/TaskCalendar.tsx:282,287` 内联样式 — 半透明浅蓝背景 `rgba(22, 119, 255, 0.12)` + inset 蓝边 `0 0 0 2px rgba(22, 119, 255, 0.5)`、深色日期数字。

历史背景：`fix-calendar-week-row-highlight-bg`（2026-07-24 已归档）把日期 cell 选中态从「primary 蓝色边框」改为「半透明浅蓝背景」，与周号行视觉保持一致。但产品当前期望是**让 selected 与 today 视觉完全统一**（深 teal 实心 + 白字 + 浅蓝焦点环 + 红色徽章），原「半透明浅蓝背景」方案被推翻。

视觉不一致导致用户在浏览日历时无法把「选中的天」和「今天」识别为同一种状态，且 selected 样式视觉重量明显弱于 today，与产品「选中即焦点」的意图不符。

## What Changes

- **修改** `web/src/parent/components/TaskCalendar.tsx` 中 `renderDateCell` 返回 cell 的 `style`（line 277-289），让 `isSelected === true` 时的视觉呈现与 antd today 高亮一致：
  - 背景色从 `rgba(22, 119, 255, 0.12)` 改为「parent 角色 primary 实心色」（深 teal `#0d9488`，或通过 antd token / CSS 变量引用以保证三角色主题跟随）。
  - 日期数字字色由深色改为白色（高对比）。
  - 外层焦点环改用浅蓝色（与 today 内置焦点环一致）。
  - 选中态背景优先级仍然**覆盖**任务类型背景（LIMITED/REPEAT/STANDING），与既有行为保持一致。
- **不修改**：
  - `data-selected` 属性语义、`data-bg` 属性。
  - `selectedRange` reducer / `selectedRange.type === 'day'` 判定逻辑。
  - 周号行（WeekNumberColumn）选中态视觉 — 保持「半透明浅蓝背景 + 同色边框」（`fix-calendar-week-row-highlight-bg` 引入的设计）。
  - 月份面板分流 CSS（`task-calendar-current-month` / `task-calendar-non-current-month`）。
  - 非当前月面板抑制选中/今日高亮的 scoped CSS（`fix-calendar-non-current-no-highlight-v2` 引入的设计）。
  - 任务徽章红色角标渲染逻辑。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
（无 — 本次变更仅触及视觉实现细节，不修改 `openspec/specs/parent-task-calendar/spec.md` 中任何 requirement 的验收语义；现有 requirement「当前选中的日/周/月 MUST 有视觉高亮状态区分」仍被满足。与归档 `fix-calendar-default-current-date` 同源处理：不引入 delta spec。）

## Impact

- **受影响代码**：`web/src/parent/components/TaskCalendar.tsx`（`renderDateCell` 内联 style 段，预计 < 20 行变更）。
- **受影响测试**：`web/src/parent/components/__tests__/TaskCalendar.test.tsx` 中断言选中态 `backgroundColor: 'rgba(22, 119, 255, 0.12)'` 或 `boxShadow: 'inset 0 0 0 2px ...'` 的回归用例需要同步更新为新视觉断言。
- **受影响用户**：所有访问 `/parent/tasks` 的家长端用户；视觉更聚焦，无行为变化。
- **不受影响**：后端 API；儿童端 / 管理员端；E2E 流程（`e2e/tests/parent-task-calendar.spec.ts` 仅断言可点击与列表刷新，不依赖具体色值）；`themes.ts` 的 token 定义本身（仅消费 token）。

## Non-Goals

- 不重设计周号行 / 月头选中态视觉。
- 不修改 `selectedRange` 状态机与 reducer 纯函数。
- 不修改 antd `<Calendar value>` 通道与 className 分流机制。
- 不引入新 design token；如本次需要新颜色变量，由 design.md 决定是复用既有 `--cg-primary` 还是硬编码。
- 不修复与本次变更无关的 pre-existing lint / 测试错误。
