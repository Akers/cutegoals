# Design: fix-calendar-non-current-no-highlight

## 修复方案（单一方案，hotfix 模式）

### 方案概述

只改 `TaskCalendar.tsx` 内 `<Calendar>` 的 props 拆分：

- 新增 `defaultValue={monthDate}` — 显示当前面板所属月份（antd 用它确定渲染哪个月份）。
- `value` 只在当前月面板传 `today`，非当前月面板传 `undefined`。

```tsx
// 修改前（fix-calendar-default-current-date 之后）
<Calendar value={calendarValue} ... />

// 修改后
<Calendar
  defaultValue={monthDate}
  value={isCurrentMonth ? today : undefined}
  ...
/>
```

### 关键决策

#### 决策 1：分拆「显示月份」与「内置高亮」两个语义

候选方案：

- A. 维持现状（非当前月 value=monthDate）— 已被 user 反馈否掉（红框部分不应该高亮 1 号）。
- B. 把 `monthDate` 当 `defaultValue` 传入，让 `value` 仅在当前月=今日,非当前月=undefined — 显式分拆两个语义。
- C. 改为每面板用独立 React state 控制 `displayedMonth`，再独立用 selectedRange 派生高亮 — 引入新 state，过设计，hotfix 不需要。
- D. CSS 覆盖 antd `.ant-picker-cell-selected` 样式强压高亮 — 副作用大，可能误压其他选中态。

**选择**：方案 B。理由：

- 沿用上次 hotfix 已确立的 `dayjs()` / `monthDate` 局部变量体系，新增 1 个 prop、删除 1 个旧值，diff 最小。
- `defaultValue` 是 antd Calendar 的标准「初始显示月份」入口（uncontrolled），与 controlled `value` 正交，无行为冲突。
- 当 `value=undefined` 时，antd 不会在 any cell 上加 `.ant-picker-cell-selected` 类 — 由 antd 5.29.x 源码 `rc-picker/lib/panels/DatePanel/DatePanel.tsx` 的 `selected` state 决定（仅当 value 有值且 value.date() 命中 cell 才置 selected），行为可预测。

#### 决策 2：selectedRange 是否要驱动非当前月面板的内置高亮

候选：

- A. 用户点击 8 月某日后让 antd `value=dayjs(selectedRange.startDate)`，使 8 月面板的内置高亮跟随用户的选中。
- B. 用户点击 8 月某日后非当前月仍维持 `value=undefined`，让 custom boxShadow 单独承担选中反馈（不带 antd 绿底）。

**选择**：方案 B。理由：

- 用户当前反馈是「非当前月不应高亮」，方案 A 在用户首次点击非当前月时又会触发高亮，可能再次违反用户期望。
- custom boxShadow 链路已可工作（`selectedRange` → `isSelected` → boxShadow），点击非当前月 cell 时 boxShadow 仍生效，作为唯一选中反馈。
- 若后续用户要求「点击非当前月也要有 antd 绿底」，可以独立起 change 处理；本 hotfix 不主动跨前。

### 不引入

- 不修改 `calendarReducer` 的 switch 任何分支。
- 不修改 `TaskCalendarProps` 接口。
- 不修改 `WeekNumberColumn` 与 `renderDateCell`。
- 不引入新依赖、新组件、新协议。
- 不引入 CSS 覆盖。

### 回归测试策略

更新 `web/src/parent/components/__tests__/TaskCalendar.test.tsx`：

1. **改造 mock `<Calendar>` 的 `data-value` 暴露**：让它在 `value=undefined` 时不写 `data-value` 属性；新增 `data-default-value` 暴露 `defaultValue?.format('YYYY-MM-DD')`。
2. **更新旧断言**：原「非当前月面板：value=monthDate」断言（在 fix-calendar-default-current-date 中新增）改为：
   - `data-value === undefined`（属性不存在）。
   - `data-default-value === '2026-08-01'`（保证显示月份正确）。
3. **新增断言**：当前月面板的 `data-default-value === '2026-07-01'` 且 `data-value === '2026-07-24'`（双层不变）。

时间控制：与上次 hotfix 相同 — 不使用 `vi.useFakeTimers`，直接依赖测试沙箱 today=2026-07-24。

### 风险

- `defaultValue` 仅在初始挂载生效；后续 `value` 切换不会改变显示月份。本代码 antd Calendar 已被外部代码通过 `onPanelChange` 维护显示月份（如有），但本组件未用 `onPanelChange`，仅 `headerRender={() => null}` 抑制了 antd 自带 header — 不会与 defaultValue 冲突。
- 当前月面板在用户连续点击 `taskTypeFilters` / `View All` 后 `selectedRange` 会变化，但 `value=today` 不变（与 selectedRange 解耦在本次 hotfix 范围内保持不变）。Custom boxShadow 跟随 selectedRange 变化。
- pre-existing TS lint：本次只动 1 个 prop 表达式，期望不引入新 TS 错误。
