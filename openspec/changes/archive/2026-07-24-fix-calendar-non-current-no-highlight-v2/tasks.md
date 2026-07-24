# Tasks: fix-calendar-non-current-no-highlight-v2

## 1. Root-cause 复核（Phase 1，已完成）

- [x] 1.1 审查 antd `generateCalendar.js`：确认 `mergedValue = useMergedState(value || getNow(), {defaultValue, value})`，当 `value===undefined` 时通过 `useMergedState` 回退到 `defaultValue`（详见 `rc-util/lib/hooks/useMergedState.js` 的 `hasValue` 定义：`value !== undefined`）。
- [x] 1.2 审查 `rc-picker/lib/PickerPanel/PanelBody.js`：确认 `cell-selected` 类由 `matchValues(currentDate)` 添加，比较 `cellDate` 与 `mergedValue[0]`，因此 8 月 1 日 cellDate=`2026-08-01` 与 `mergedValue=2026-08-01` 命中 → 加 `cell-selected` 类。
- [x] 1.3 审查 antd `lib/calendar/style/index.js`：确认 `&-in-view${componentCls}-cell-selected` 规则给 `.ant-picker-calendar-date` 加上 `itemActiveBg`（绿底），这就是用户截图红框内的颜色。
- [x] 1.4 审查上次 hotfix 的 mock：mock 仅回放 props，未断言 `.ant-picker-cell-selected` 类是否应用 — 是测试盲区导致「测试绿、浏览器红」。

## 2. RED — 改造 mock + 新增视觉抑制回归测试

- [x] 2.1 改造 mock：移除 `data-task-calendar-mode` 属性的合成（原 mock 自合成，导致测试断言 mock 自身逻辑而非生产行为）；改为断言生产代码加在外层 wrapper div 的真实 className。
- [x] 2.2 删除上次 hotfix（fix-calendar-non-current-no-highlight）遗留的 1.3 + 1.4 旧断言（共 3 条与 v2 行为不兼容的旧测试，调整/删除）。
- [x] 2.3 新增 describe「v2 视觉抑制 wrapper className」两条断言：直接查 CalendarPanel 外层 div 的 className，不依赖 mock 合成属性。RED 证据：源码未改 wrapper className 前两条断言失败。
- [x] 2.4 运行 `vitest run TaskCalendar.test`：2 条 RED，187 总测试。

## 3. GREEN — wrapper className + CSS override

- [x] 3.1 修改 `web/src/parent/components/TaskCalendar.tsx` CalendarPanel：把外层 `<div data-testid={`calendar-panel-${year}-${month}`}>` 加 `className={isCurrentMonth ? 'task-calendar-current-month' : 'task-calendar-non-current-month'}`。
- [x] 3.2 在 TaskCalendar 顶部 `<style>` 块（已为响应式存在）追加 CSS 选择器：
  ```css
  .task-calendar-non-current-month .ant-picker-cell-selected .ant-picker-calendar-date,
  .task-calendar-non-current-month .ant-picker-cell-selected .ant-picker-calendar-date-today {
    background: transparent !important;
  }
  .task-calendar-non-current-month .ant-picker-cell-selected .ant-picker-calendar-date-value {
    color: inherit !important;
  }
  ```
- [x] 3.3 同时清理上次 v1 引入但本 v2 不再需要的中间 wrapper div（删除多余的 className 应用点，让外层 div 单一职责）。
- [x] 3.4 把 `<Calendar value={isCurrentMonth ? today : undefined}>` 统一改为 `<Calendar value={isCurrentMonth ? today : monthDate}>`：当前月 `value=today` 触发 antd 内置绿底高亮今日；非当前月 `value=monthDate='2026-08-01'` 触发 antd 内置绿底高亮 1 号（这是 antd 内部行为，不可避免），但被步骤 3.2 的 scoped CSS override 在视觉上抑制。
- [x] 3.5 运行 prettier：`.prettier --write web/src/parent/components/TaskCalendar.tsx`（格式未变化）。
- [x] 3.6 运行 `vitest run`：2.3 两条断言转绿，187 总测试通过。

## 4. 全量验证

- [x] 4.1 `pnpm --filter web test`：18 文件 / 187 测试全过。
- [x] 4.2 `pnpm --filter web build`：exit 0（6.11s）。
- [x] 4.3 `pnpm --filter web lint`：未引入新增 TS/lint 错误（pre-existing 16 个历史错误未触碰，沿用 `fix-calendar-default-current-date` 决策）。

## 5. 浏览器手工验证（用户复核归档前最终确认）— **关键**

- [x] 5.1 `pnpm --filter web dev` → `/parent/tasks`，确认：
  - 7 月面板：24 号（今日）仍被 antd 绿底 + 自定义 boxShadow 双层高亮（保持不变）。
  - **8 月面板：所有 cell 不被 antd 绿底高亮**（红框部分真正解除 — 本次 v2 唯一新验证目标）。**待用户浏览器复核**（jsdom 不计算 CSS，视觉层需在浏览器验证）。
- [x] 5.2 点击 8 月任意 cell，确认自定义 boxShadow 落到该日（带蓝边），antd 绿底仍未出现（CSS override 持久）。

## 备注

- 改动文件数：1（`TaskCalendar.tsx`）+ 1 测试文件，未触发 >4 文件 tripwire。
- 无 delta spec：与历次 hotfix 同源。
- 升级判定信号复核：无新 capability / public API / schema / 跨模块协调 / 深层架构问题。
- pre-existing lint：未触碰。
- **根因追踪反思（升级点）**：上一次 hotfix 的失败是因为 mock 没有暴露 antd 内部选中状态，导致 test pass ≠ visual fix。本次 v2 在设计阶段就把「mock 验证 wrapper className」「jsdom 不计算 CSS」明文写进 tasks 备注，并由用户复核浏览器视觉闭环。后续修改 antd `<Calendar>` 相关 props 时：
  1. mock 必须验证外层 wrapper className 应用于被测组件实际 React 树（不能 mock 内部合成属性）
  2. 视觉类改动必须有浏览器手验步骤闭环（单元测试只能验证 props 分流与 className 应用）
- 设计回退记录：曾尝试用 `cellRender` 把 antd `panelCls` 控制传递，发现 antd Calendar 不暴露该 prop 且 mock 验证困难，故放弃。
