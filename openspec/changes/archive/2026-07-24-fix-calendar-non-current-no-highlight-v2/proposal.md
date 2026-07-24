# Proposal: fix-calendar-non-current-no-highlight-v2

## Why

紧接两次 hotfix：
- `fix-calendar-default-current-date`（archived）— 修复当前月高亮 1 号为高亮今日。✓
- `fix-calendar-non-current-no-highlight`（archived）— 修复非当前月高亮 1 号。**用户反馈：未修复。**

**为什么上次 fix 失败：**

经 antd 5.29.3 源码审查，`useMergedState({defaultValue, value})` 在 `value===undefined` 时**回退到 `defaultValue`**：

```js
// rc-util useMergedState
useState(() => {
  if (hasValue(value)) return value;          // false (value=undefined)
  if (hasValue(defaultValue)) return defaultValue;  // true → monthDate='2026-08-01'
  return defaultStateValue;
});
const mergedValue = value !== undefined ? value : innerValue;
// mergedValue = monthDate = '2026-08-01'
```

后续链路：antd 将 `mergedValue` 传给 `<PickerPanel>` → `<PanelBody>` 中 `matchValues(currentDate)` 检查 `cellDate === mergedValue` → 给 8 月 1 日的 td 加 `.ant-picker-cell-selected` → CSS 规则 `&-in-view&-cell-selected .ant-picker-calendar-date { background: itemActiveBg }` 给 8 月 1 日加上绿底。

之前 hotfix 的 mock 只暴露 `data-value` 属性，没有断言 antd 内部 `cell-selected` 类是否被添加 — 测试绿，但浏览器红（mock-vs-reality 脱节，详见 tasks.md 备注「Root-cause-tracing」）。

**用户期望**：8 月面板（红框部分）不被 antd 绿底高亮；与上次热修复目标一致，仅实现路径需要重做。

## 根因分析

**双根因**：

1. **源码层：`useMergedState` 回退语义**。`value=undefined` 时 antd 不会「不选中任何 cell」，而是「改用 defaultValue 作为选中值」。这是 `rc-util` `hasValue` 定义的语义（仅把 `undefined` 视为 empty）。
2. **测试层：mock 行为盲区**。`TaskCalendar.test.tsx` 的 mock `<Calendar>` 只读 props 回放，不渲染 antd 内部 className/CSS。`not.toHaveAttribute('data-value')` 通过，但 `.ant-picker-cell-selected` 该来还是来。

## What Changes

`web/src/parent/components/TaskCalendar.tsx`：

1. 在 antd `<Calendar>` 外面包一层 wrapper `<div className={isCurrentMonth ? 'task-calendar-current-month' : 'task-calendar-non-current-month'}>`
2. 在 TaskCalendar 顶部的 `<style>` 块（已存在）中追加 CSS 规则：
   ```css
   .task-calendar-non-current-month .ant-picker-cell-selected .ant-picker-calendar-date,
   .task-calendar-non-current-month .ant-picker-cell-selected .ant-picker-calendar-date-today {
     background: transparent !important;
   }
   .task-calendar-non-current-month .ant-picker-cell-selected .ant-picker-calendar-date-value {
     color: inherit !important;
   }
   ```
3. 移除上次 hotfix 的 `defaultValue={monthDate}` 与 `value={isCurrentMonth ? today : undefined}` 控制逻辑，改回 `value={monthDate}`（恢复「monthDate 驱动显示月份」）。**展示月份**仍由 `value=monthDate` 负责，**视觉高亮**由 CSS 在非当前月 wrapper 内抑制。两层语义重新分离。

约束：不动 `calendarReducer`、`renderDateCell`（boxShadow 与 date 命中逻辑）、`WeekNumberColumn`、日历几何对齐、API。

## Impact

- 受影响用户：所有访问 `/parent/tasks` 的家长端用户。
- 受影响组件：`TaskCalendar` 的 `CalendarPanel` 渲染（wrapper className + `<style>` 规则扩展）。
- 不影响：reducer、自定义 boxShadow、WeekNumberColumn、dateCellRender、CalendarHeader、selectedRange 协议、当前月面板行为、后端。

## Non-Goals

- 不修改日历选中态语义（仍由 `selectedRange` 派生 boxShadow）。
- 不修跨月导航的 boxShadow 跨月分布（继承自上次 hotfix 的「已知未处理」）。
- 不动 antd 内部状态、props、API。
- 不引入 CSS-in-JS 或外部 CSS 文件：继续使用 TaskCalendar 现有 `<style>` 注入块（项目内一致）。
- 不引入 delta spec（与历次 hotfix 同源，无 capability 层验收场景变化）。
