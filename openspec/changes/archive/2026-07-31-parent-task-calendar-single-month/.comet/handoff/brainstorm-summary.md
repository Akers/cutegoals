# Brainstorm Summary

- Change: parent-task-calendar-single-month
- Date: 2026-07-30

## 确认的技术方案

### 架构与导航

- TaskCalendar 主组件从渲染两个 CalendarPanel 改为只渲染一个 CalendarPanel
- CalendarPanel 子组件签名/行为不变（仍按 (year, month) 接收 prop，独立 fetch 数据，自带 loading/error/refetch）
- 导航栏保留 `<` `>` 按钮，由父组件 `onNavigate` 控制 baseMonth 切换
- 导航栏标题从 `YYYY年M月 — YYYY年M月` 改为 `YYYY年M月`

### CSS 与响应式容器

- 删除 `<style>` 块中所有 `.task-calendar-non-current-month-*` 规则（共 5 条）
- 删除 `@media (max-width: 767px)` 响应式覆盖（不再需要 grid 单列切换）
- 保留 `.task-calendar-current-month-*` 全部规则（teal 实心选中、today 字色、today 浅蓝边框等）
- `.task-calendar-grid` 容器从 `display: grid; grid-template-columns: repeat(auto-fit, minmax(400px, 1fr))` 改为 `display: block; width: 100%`
- 保留 `className="task-calendar-grid"` 标识符（避免父组件 data-testid 大规模改动）

### CalendarPanel 简化

- 删除 `isCurrentMonth` 计算
- className 固定为 `task-calendar-current-month`（不再有 `task-calendar-non-current-month`）
- antd `value` 计算改为：
  - `!selectedRange` → `monthDate`
  - `selectedRange.type === 'day'` → `dayjs(selectedRange.startDate)`
  - 其他类型（`week` / `month`）→ `monthDate`
- 任务徽章视觉（红色 total 徽章，top: -26, left: 20）保持不变
- 数据协议 `CalendarData.days[dateKey].total` 不变

### 测试重写

- TaskCalendar.test.tsx 全面重写为单月场景（T2）
- 覆盖：单月渲染、baseMonth 默认值、baseMonth 跨年、导航按钮触发 onNavigate、baseMonth 变更后单次 API 请求、单月标题格式、today 视觉（浅蓝边框）、选中态视觉（teal 实心）、周号列高亮、loading/error 态
- 删除所有双月相关断言：双月标题、双 CalendarPanel 渲染、双 API 请求、双面板错误隔离、v2 wrapper className 分流、非当前月面板 value 属性
- ParentTasksPage.test.tsx 仅需校验与 TaskCalendar 的 prop 交互不变（baseMonth / selectedRange / onSelect / onNavigate）
- e2e parent-task-calendar.spec.ts：删除"双月日历渲染"断言（`toHaveCount(2)`），改为单月面板断言；标题从"双月"改为"单月"

### Spec Patch（回写 delta spec）

delta spec `openspec/changes/parent-task-calendar-single-month/specs/parent-task-calendar/spec.md` 修改：

- **Requirement: 默认选中今日与本周** 场景 "默认进入页面，今天与本周被选中" 改为：
  - 旧："今天日期 cell 显示选中态视觉（与手动选中日视觉一致：深色背景 + 高对比文字 + 外层焦点环）"
  - 新："今天日期 cell 显示 today 视觉（浅蓝边框 + teal 字色），与选中态视觉（teal 实心 + 白字）区分"
- 新增 Scenario: baseMonth 包含今天时的视觉
  - WHEN baseMonth 包含今天日期（如 baseMonth='2026-07' 且 today='2026-07-24'）
  - THEN today cell 由 antd 自动加 .ant-picker-cell-today 类，CSS 中 `.task-calendar-current-month .ant-picker-cell-today .ant-picker-calendar-date-value { color: #0d9488 }` 规则命中（teal 字色）+ `.task-calendar-current-month .ant-picker-cell-today:not(.ant-picker-cell-selected) .ant-picker-calendar-date { border: 1px solid #1677ff }` 规则命中（浅蓝边框）

## 关键取舍与风险

- **取舍 1**：删除 `isCurrentMonth` + non-current CSS（YAGNI 极简），代价是默认进入页面 today cell 不再与 selected 视觉一致 → 由 spec patch 显式记录 today 视觉新语义
- **风险 1**：用户首次进入页面看到的是「1 号 teal 实心（antd value=monthDate 自动标记）+ 24 号 today 浅蓝边框」二阶视觉，可能感觉不直观 → Mitigation：spec patch 明示新视觉，并通过 UI 微文案/文案确认
- **风险 2**：若未来想恢复双月，需要重新加 isCurrentMonth + non-current CSS → Mitigation：CalendarPanel 子组件签名不变，回滚成本低
- **风险 3**：CalendarPanel 中 antd value 仍由 selectedRange.startDate 或 monthDate 决定，存在跨月不一致场景（如用户在 7 月选 day=15 后导航到 8 月 → CalendarPanel value=15 日 → antd 渲染 7 月而非 8 月）→ 这是已有 bug，不在本次变更范围，记入 Open Questions

## 测试策略

- 单元测试：`pnpm --filter web test TaskCalendar`（vitest）
- 类型检查：`pnpm --filter web typecheck`
- E2E：`pnpm test:e2e -- parent-task-calendar`（playwright）
- 视觉验证：浏览器手动核查 teal 实心选中态、today 浅蓝边框、单月布局
- jsdom 不计算 CSS，所有 CSS 视觉效果由浏览器验证；单元测试仅断言结构（testid、className、value、data-value）

## Open Questions

- CalendarPanel 中 antd value 与传入 (year, month) 不一致时的渲染月份错误（用户在 7 月选 day 后导航到 8 月 → antd 仍渲染 7 月）：已有 bug，不在本次变更范围；未来如要修复，可考虑 value 计算时检查 startDate 所在月是否等于传入 month，若不等则用 monthDate