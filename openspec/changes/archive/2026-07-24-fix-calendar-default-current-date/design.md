# Design: fix-calendar-default-current-date

## 修复方案（单一方案，hotfix 模式）

只改两处接入点：

### 方案概述

1. **`pages/index.tsx` 选择器初值改为今日 day selection**：把 `useReducer` 第二参数的 `selectedRange` 从 `null` 初始化为 `{ type: 'day', startDate: now.format('YYYY-MM-DD'), endDate: now.format('YYYY-MM-DD') }`。`baseMonth` 不动（已是 `now.format('YYYY-MM')`）。这一步让自定义 `boxShadow` 高亮落在今天。

2. **`TaskCalendar.tsx CalendarPanel` 计算 `calendarValue` 后再传给 `<Calendar>`**：
   ```tsx
   const today = dayjs();
   const isCurrentMonth = today.year() === year && (today.month() + 1) === month;
   const calendarValue = isCurrentMonth
     ? today
     : (selectedRange ? dayjs(selectedRange.startDate) : monthDate);
   ```
   - 当前月面板：antd `<Calendar value={calendarValue=today}>` 自动高亮今天。
   - 非当前月面板：保留 `selectedRange.startDate`（如果有）或 `monthDate`（当月 1 号）作为回退，保持现状。

### 关键决策

#### 决策 1：`<Calendar value>` 二义性的拆解

antd `<Calendar>` 的 `value` 既控制显示月又控制自动高亮日。要么改用 `defaultValue`，要么继续用 `value` 但只在当前月用 today。

候选方案：

- A. `value = dayjs()` 永远——破坏 `<Calendar>` 显示月份的边界（非当前月面板无法维持 prev/next 月）。week-column 对齐逻辑链也可能受影响。
- B. 当前月用 today、非当前月用 monthDate 或 selectedRange.startDate——当前月正确高亮 today，其它面板保留回退。
- C. 改用 `defaultValue` + 不传 `value`——失去「selected」受控，但与现有 `selectedRange` 解耦，破坏后续交互（如 SELECT_WEEK / SELECT_MONTH 后再次显示月份）。

**选择**：方案 B。

#### 决策 2：是否引入 useState 镜像 today

候选方案：

- A. `const today = dayjs()` 在 CalendarPanel 内直接计算——简单，但用户长时间挂页面不刷新会过时；与「默认行为」语义一致（默认=初次进入，不是实时）。
- B. `const [today] = useState(() => dayjs())`——比 A 仅多一次冻结，实际差异可忽略。
- C. 监听定时器刷新——超出 hotfix 范围。

**选择**：方案 A（hotfix 内最小改动）。理由：默认行为只在初次挂载时决定；后续用户的点击、导航、月度切换产生的 selectedRange 已经覆盖所有交互路径。

#### 决策 3：reducer 是否新增 action

不要新增。现有 `SELECT_DATE` 已能完整表达「设置 today」的选择，复用现有路径：
- 挂载后通过 `selectedRange` 初值直接传入选中态，无需派发 action。
- 不需要 `INIT_TODAY` 之类的特殊 action。

### 不引入

- 不修改 `calendarReducer` 的 switch 任何分支。
- 不修改 `TaskCalendarProps` 接口。
- 不修改 `WeekNumberColumn` 与 `renderDateCell`。
- 不引入新依赖。

### 回归测试策略

#### `web/src/parent/components/__tests__/TaskCalendar.test.tsx`

新增 1 个 describe 块「默认选中今日（回归）」：

- `selectedRange` 初值为今日 day-selection 时，渲染 `TaskCalendar` 并断言：
  - 在「今日所在月」面板中，传递给 mock `<Calendar>` 的 `value` 等于今日 dayjs（通过 mock Calendar 的 props 暴露 testid 或数据属性验证）。
  - 今日 cell 的 `[data-selected]` 应为 `'true'`（自定义 boxShadow 命中）。

辅助 mock 调整：现有 mock `<Calendar>`（`__tests__/TaskCalendar.test.tsx:62-87`）已经把 `value` 当 dayjs 解析；不需要结构改动，只让 mock 暴露 `data-testid` 或 `data-value` 便于断言 value。

#### `web/src/parent/pages/__tests__/ParentTasksPage.test.tsx`

新增一个 describe 块「组件默认选择今日」：

- 渲染 `<ParentTasksPage />`（mocked TaskCalendar 暴露 `data-selected`），断言 `mock-task-calendar` 的 `data-selected` 等于 `${today}_${today}`。
- 断言日历 query params 包含 `startDate=${today}&endDate=${today}`（说明 buildQuery 链路也用 today）。

时间控制：用 `vi.useFakeTimers({ now: new Date('2026-07-24T12:00:00Z') })` 或在测试内 `vi.setSystemTime`，固定今日为 2026-07-24，便于断言。

### 风险

- 长期挂页不刷新：today 不会更新为「下一个真实今日」。可接受：本 bug 修复语义是「默认行为」，不是「实时跟踪」。
- `vi.useFakeTimers` 与 `dayjs` 在 jsdom 中已多次验证兼容（前次 hotfix 已用）。
- pre-existing TS lint：未触碰，沿用历史问题。
