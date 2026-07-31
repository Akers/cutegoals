---
comet_change: parent-task-calendar-single-month
role: technical-design
canonical_spec: openspec
archived-with: 2026-07-31-parent-task-calendar-single-month
status: final
---

# Parent Task Calendar — Single Month

## Context

家长任务分配页 `/parent/tasks` 当前 TaskCalendar 组件渲染双月（当月 + 下月），桌面端两月并排、移动端两月堆叠。该组件由 2026-07-22 归档的 `parent-dual-month-task-calendar` change 引入，配套 proposal/design/tasks 全部基于"双月"前提设计。

任务目标（来自 proposal.md）：简化 TaskCalendar 为单月日历视图，下月/上月数据通过既有的导航按钮 "<>" 切换 baseMonth 查看。

TaskCalendar 内部结构（455 行）：
- `TaskCalendar`（主组件）：解析 `baseMonth`，计算 `currentMonth` 与 `nextMonth`，管理导航栏与 Grid 容器
- `CalendarPanel`（子组件）：单月面板，独立 fetch `/task-assignments/calendar?year=X&month=X`；通过 `className` `task-calendar-current-month` 或 `task-calendar-non-current-month` 区分视觉
- `WeekNumberColumn` / `CalendarHeader`：单月内部子组件
- `<style>` 块：12 条 CSS 规则，区分 current/non-current，selected/today 视觉由 CSS 覆盖 antd 内置样式实现

父组件 `ParentTasksPage`（`web/src/parent/pages/index.tsx`，2816 行）通过 `calendarReducer` 管理 `selectedRange` 状态，使用 `baseMonth` 字段做月份导航。父组件不依赖 TaskCalendar 内部布局（只通过 `baseMonth / selectedRange / onSelect / onNavigate` 四个 props 交互）。

测试现状：
- `web/src/parent/components/__tests__/TaskCalendar.test.tsx`（911 行，约 30 个 it 测试，12 个依赖双月渲染）
- `web/src/parent/pages/__tests__/ParentTasksPage.test.tsx`（391 行）
- `e2e/tests/parent-task-calendar.spec.ts`（106 行，含双月面板 `toHaveCount(2)` 断言）

## Goals / Non-Goals

**Goals:**
- TaskCalendar 每次只渲染 `baseMonth` 一个月的日历面板
- 视觉行为：teal 实心选中态、today 浅蓝边框 + teal 字色、任务徽章保留
- 每次进入日历页面只发 1 次 `/task-assignments/calendar` 请求
- 不修改 ParentTasksPage、CalendarPanel 子组件、CalendarSelection/Action 类型、reducer、后端
- TaskCalendar.test.tsx 全面重写为单月场景

**Non-Goals:**
- 不删除 ParentTasksPage / TaskCalendar 组件
- 不改变导航按钮 ±1 月的行为（`onNavigate` 仍由父组件控制）
- 不重做 antd Calendar 组件的内部交互
- 不修改后端 `/task-assignments/calendar` 接口契约
- 不修复 CalendarPanel 中 antd value 与传入 (year, month) 不一致时的渲染月份错误（已有 bug，本变更范围外）

## Decisions

### Decision 1：单月独立面板（而非双月堆叠）

选择单月独立面板渲染（去掉第二个 CalendarPanel），而不是双月 → 上下堆叠。

理由：
- 单月面板在桌面端占满容器可用宽度，任务徽章、cell 密度可读性提升
- 移动端天然就是单月布局（小屏横向空间不够"并排"），改造前后行为等价
- 减少 1 × `/task-assignments/calendar` API 调用

替代方案：保留双月但移动端上下堆叠 → 拒绝，任务目标本身是"简化为单月"。

### Decision 2：保留 CalendarPanel 子组件 + 复用模式

TaskCalendar 主组件只 new 一个 CalendarPanel；CalendarPanel 组件签名/行为不变。

理由：
- 现有 CalendarPanel 是独立数据获取单元，自带 loading/error/refetch 行为，复用风险最低
- 未来若需重新引入双月，只需在 TaskCalendar 中再加一个 CalendarPanel
- 不破坏父组件任何依赖（`weekLabelRender` / `dateCellRender` 等都不依赖月数）

### Decision 3：导航按钮保留

保留 `<` `>` 按钮，由父组件 `onNavigate` 控制 `baseMonth` 切换。

理由：
- 单月后用户仍需查看其他月份（特别是下月做下月任务规划）
- 导航按钮是 selectedRange 跨月交互的唯一入口（cell 不能跨月点击）
- 父组件 `calendarReducer` 的 `NAV_MONTH` action 已实现，无需改动

### Decision 4：删除所有 `.task-calendar-non-current-month-*` CSS 规则

CSS 块中 5 条 non-current 规则全部删除：

```css
/* 删除：非当前月面板抑制 antd 内置 cell-selected 绿底/字色 */
.task-calendar-non-current-month .ant-picker-cell-selected .ant-picker-calendar-date,
.task-calendar-non-current-month .ant-picker-cell-selected .ant-picker-calendar-date-today {
  background: transparent !important;
}
.task-calendar-non-current-month .ant-picker-cell-selected .ant-picker-calendar-date-value {
  color: inherit !important;
}
.task-calendar-non-current-month .ant-picker-cell-today:not(.ant-picker-cell-selected) .ant-picker-calendar-date {
  border: 1px solid #1677ff !important;
  border-radius: 4px;
}
```

理由：
- 单月后所有面板都是"当前月"，原"非当前月抑制高亮"逻辑失效
- 保留会引入无意义 CSS 规则

替代方案：保留 CSS 以防未来回滚到双月 → 拒绝，YAGNI 原则；旧规则在 git 历史中可恢复。

### Decision 5：CalendarPanel 删除 `isCurrentMonth` 计算（YAGNI 极简）

CalendarPanel 中删除 `isCurrentMonth = today.year() === year && today.month() + 1 === month` 计算。className 固定为 `task-calendar-current-month`。

理由：
- 双月模式下"非当前月"= 下月面板；单月模式下仅当 baseMonth ≠ today 所在月时出现"非当前月"场景
- 单月模式下用户主动导航到非今日所在月时仍希望看到 selected 视觉（teal 实心），不能抑制
- 删除 isCurrentMonth 后 className 单一、CSS 规则单一、逻辑链最短

### Decision 6：CalendarPanel antd value 计算简化

antd `value` 计算改为：

```tsx
<Calendar
  value={(() => {
    if (!selectedRange) return monthDate;
    if (selectedRange.type === 'day') return dayjs(selectedRange.startDate);
    return monthDate;
  })()}
  fullscreen={false}
  headerRender={() => null}
  dateCellRender={renderDateCell}
  onSelect={(date) => onSelect({ type: 'SELECT_DATE', date: date.format('YYYY-MM-DD') })}
/>
```

理由：
- 简化：value 始终从 monthDate 或 selectedRange.startDate 二选一
- today cell 视觉由 antd 内部自动加 `.ant-picker-cell-today` 类 + CSS 规则命中实现（浅蓝边框 + teal 字色）
- 默认进入页面（baseMonth=今日所在月，selectedRange=null）→ value=monthDate → antd 渲染该月并标 1 号为 selected → today cell 由 antd 自动标记 today 类

**视觉权衡**（用户已确认 X2）：
- 默认进入页面 today cell 显示 today 视觉（浅蓝边框 + teal 字色），与 selected 视觉（teal 实心 + 白字）区分
- 这是从原"今天与 selected 视觉一致"到"今天与 selected 视觉区分"的行为变化，由 spec patch 显式记录新语义

### Decision 7：任务徽章视觉与数据协议不变

`dateCellRender` 输出的红色任务总徽章（`top: -26, left: 20`）保持不变；数据协议 `CalendarData.days[dateKey].total` 也不变。

理由：
- 视觉一致性优先（家长用户已经熟悉徽章位置）
- 数据协议（`DayData.total`）不变 → 后端 E2E 与契约测试零修改

### Decision 8：响应式容器简化

`.task-calendar-grid` 当前使用 `grid-template-columns: repeat(auto-fit, minmax(400px, 1fr))` 双列布局。改为单月后：
- 容器改为简单 block（`display: block`）+ `width: 100%`
- 删除 `@media (max-width: 767px)` 的响应式覆盖
- 保留 `className="task-calendar-grid"` 标识符

理由：
- 单月后不需要 grid 多列布局
- 保留 className 标识符避免父组件 data-testid 大规模改动

### Decision 9：测试全面重写（T2）

TaskCalendar.test.tsx 全面重写为单月场景。覆盖：
- 单月渲染（一个 mock-calendar）
- baseMonth 默认值（今日所在月）
- baseMonth 跨年（2026-12 / 2027-01）
- 导航按钮触发 onNavigate(-1) / onNavigate(1)
- 单月标题格式（"2026年7月"）
- today 视觉（浅蓝边框 + teal 字色，由 antd 自动 + CSS 命中）
- 选中态视觉（teal 实心 + 白字，jsdom 验证结构，CSS 由浏览器验证）
- 周号列高亮（border 边框）
- loading/error 态（Spin / Alert）
- dateCellRender 徽章显示（total > 0 时显示，total = 0 时隐藏）
- 三级点击交互（SELECT_DATE / SELECT_WEEK / SELECT_MONTH）
- useApi 数据获取（单 API 请求，单月数据）

ParentTasksPage.test.tsx 仅需校验与 TaskCalendar 的 prop 交互不变（baseMonth / selectedRange / onSelect / onNavigate）。

e2e parent-task-calendar.spec.ts：
- 删除"双月日历渲染"断言（`toHaveCount(2)`）
- 改为单月面板断言（`toHaveCount(1)` 或 `toBeVisible`）
- 标题从"双月"改为"单月"
- 删除"移动端日历上下堆叠"测试（单月模式下不再适用）

### Decision 10：Spec Patch 回写 delta spec

delta spec `openspec/changes/parent-task-calendar-single-month/specs/parent-task-calendar/spec.md` 修改：

- **Requirement: 默认选中今日与本周** 场景 "默认进入页面，今天与本周被选中" 改为：
  - 旧："今天日期 cell 显示选中态视觉（与手动选中日视觉一致：深色背景 + 高对比文字 + 外层焦点环）"
  - 新："今天日期 cell 显示 today 视觉（浅蓝边框 + teal 字色），与选中态视觉（teal 实心 + 白字）区分"

- 新增 Scenario: baseMonth 包含今天时的视觉
  - WHEN baseMonth 包含今天日期（如 baseMonth='2026-07' 且 today='2026-07-24'）
  - THEN today cell 由 antd 自动加 `.ant-picker-cell-today` 类，CSS 中 `.task-calendar-current-month .ant-picker-cell-today .ant-picker-calendar-date-value { color: #0d9488 }` 规则命中（teal 字色）+ `.task-calendar-current-month .ant-picker-cell-today:not(.ant-picker-cell-selected) .ant-picker-calendar-date { border: 1px solid #1677ff }` 规则命中（浅蓝边框）

## Risks / Trade-offs

- [Risk] 删除 `isCurrentMonth` + non-current CSS 后，默认进入页面 today cell 不再与 selected 视觉一致 → **Mitigation**：spec patch 显式记录新视觉语义（浅蓝边框 + teal 字色 vs teal 实心 + 白字）
- [Risk] CalendarPanel.test.tsx 全面重写风险（30+ 个测试）→ **Mitigation**：保留 WeekNumberColumn 内部测试逻辑、ComputeWeekNumbers 测试、dateCellRender 徽章测试不变；只重写双月依赖部分
- [Risk] 父组件 ParentTasksPage 可能历史上有依赖双月面板布局的代码 → **Mitigation**：父组件只通过 props 传 baseMonth + selectedRange，不依赖内部布局，验证零父组件修改
- [Risk] 用户失去"一眼看到下月"的便利性 → **Mitigation**：保留导航按钮，标题区显示当前月；UX 退化可接受
- [Risk] CSS 简化后如果某天回滚到双月，需要重新添加 `.task-calendar-non-current-month-*` 规则 + isCurrentMonth 计算 → **Mitigation**：旧规则在 git history / GitHub blame 中可恢复；保留 CalendarPanel 子组件签名不变，回滚成本低
- [Risk] CalendarPanel 中 antd value 与传入 (year, month) 不一致时的渲染月份错误（已在 Open Questions）→ **Mitigation**：本期不修复；未来如要修复，可考虑 value 计算时检查 startDate 所在月是否等于传入 month

## Migration Plan

部署步骤：
1. 修改 `web/src/parent/components/TaskCalendar.tsx`（移除第二个 CalendarPanel + 简化 CSS + 删除 isCurrentMonth + 简化 antd value）
2. 全面重写 `web/src/parent/components/__tests__/TaskCalendar.test.tsx` 为单月场景
3. 更新 `web/src/parent/pages/__tests__/ParentTasksPage.test.tsx`（最小变更，校验 prop 交互不变）
4. 更新 `e2e/tests/parent-task-calendar.spec.ts`（删除双月断言，改为单月）
5. 回写 delta spec（修改场景描述 + 新增 baseMonth 包含今天的视觉场景）
6. 运行单元测试 `pnpm --filter web test TaskCalendar`
7. 运行类型检查 `pnpm --filter web typecheck`
8. 运行 E2E 测试 `pnpm test:e2e -- parent-task-calendar`

回滚策略：
- 本次变更只影响 `web/` 子项目前端代码，回滚 = `git revert` 即可
- TaskCalendar 组件四个 props（`baseMonth` / `selectedRange` / `onSelect` / `onNavigate`）签名不变，父组件 ParentTasksPage 不需要适配
- 后端 `/task-assignments/calendar` 无变更，无需考虑服务降级

## Test Strategy

### 单元测试

`pnpm --filter web test TaskCalendar`（vitest）

覆盖场景：
1. 渲染：单月面板、单月标题（"2026年7月"）、单 mock-calendar（`mock-calendar-2026-07`）、跨年（"2026年12月"）
2. 导航：点击 `<` 触发 onNavigate(-1)、点击 `>` 触发 onNavigate(1)
3. 选中：点击日期触发 SELECT_DATE、点击周号触发 SELECT_WEEK、点击月份标题触发 SELECT_MONTH
4. 周号列：6 行周号、有任务周背景、选中周边框、几何对齐（27 px spacer、8 px bottom-padding）
5. 任务徽章：total > 0 时显示、total = 0 时不显示、绝对定位（top: -26px, left: 20px）
6. useApi：loading 态显示 Spin、error 态显示 Alert + 重试、正常数据渲染
7. today 视觉：baseMonth=今日所在月时 today cell 由 antd 自动加 `.ant-picker-cell-today` 类
8. baseMonth 跨月：baseMonth='2026-08'（today=2026-07-24）时 CalendarPanel value=monthDate（2026-08-01）

### 类型检查

`pnpm --filter web typecheck`

### E2E

`pnpm test:e2e -- parent-task-calendar`（playwright）

覆盖：
1. 单月日历渲染（一个日历面板）
2. 日期点击 → 任务列表联动
3. 任务类型筛选
4. 查看全部模式
5. 移动端单月布局

### 视觉验证（浏览器手动核查）

- 单月布局：桌面/移动端均单月
- 选中态视觉：teal 实心 + 白字
- today 视觉：浅蓝边框 + teal 字色
- 周号行高亮：蓝色边框
- 任务徽章：红色圆角矩形（top: -26, left: 20）

## Open Questions

- CalendarPanel 中 antd value 与传入 (year, month) 不一致时的渲染月份错误（用户在 7 月选 day=15 后导航到 8 月 → CalendarPanel value=15 日 → antd 渲染 7 月而非 8 月）：已有 bug，不在本次变更范围；未来如要修复，可考虑 value 计算时检查 startDate 所在月是否等于传入 month，若不等则用 monthDate
