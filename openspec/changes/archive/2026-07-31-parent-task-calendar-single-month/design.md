## Context

家长任务分配页 `/parent/tasks` 当前挂载的 TaskCalendar 组件（`web/src/parent/components/TaskCalendar.tsx`）渲染**双月**日历：桌面端左右并排、移动端上下堆叠。该组件由 2026-07-22 归档的 `parent-dual-month-task-calendar` change 引入，配套 proposal/design/tasks 全部基于"双月"前提设计。

TaskCalendar 内部结构：
- `TaskCalendar`（主组件）：解析 `baseMonth`，计算 `currentMonth` 与 `nextMonth`，管理导航栏与 Grid 容器
- `CalendarPanel`（子组件）：单月面板，独立 fetch `/task-assignments/calendar?year=X&month=X`；通过 `className` `task-calendar-current-month` 或 `task-calendar-non-current-month` 区分视觉
- `WeekNumberColumn` / `CalendarHeader`：单月内部子组件

父组件 `ParentTasksPage`（`web/src/parent/pages/index.tsx`）通过 `calendarReducer` 管理 `selectedRange` 状态，使用 `baseMonth` 字段做月份导航。父组件不依赖 TaskCalendar 内部布局（只通过 `baseMonth / selectedRange / onSelect / onNavigate` 四个 props 交互）。

## Goals / Non-Goals

**Goals:**
- TaskCalendar 每次只渲染 `baseMonth` 一个月的日历面板
- 视觉、交互（day/week/month 选择、选中/今天/周号高亮、响应式、加载/错误态）行为不变
- 每次进入日历页面只发 1 次 `/task-assignments/calendar` 请求（双月时为 2 次）
- 不修改 ParentTasksPage、CalendarPanel 子组件、CalendarSelection/Action 类型、reducer、后端

**Non-Goals:**
- 不删除 ParentTasksPage / TaskCalendar 组件
- 不改变导航按钮 ±1 月的行为（`onNavigate` 仍由父组件控制）
- 不重做 antd Calendar 组件的内部交互
- 不合并多任务请求（仍按需按月请求）
- 不修改后端 `/task-assignments/calendar` 接口契约
- 不做"下月预览"或"两月合并"等渐进增强

## Decisions

### Decision 1：单月独立面板（而非双月堆叠）

选择单月独立面板渲染（去掉第二个 CalendarPanel），而不是双月 → 上下堆叠。

理由：
- 单月面板在桌面端占满容器可用宽度，任务徽章、cell 密度可读性提升
- 移动端天然就是单月布局（小屏横向空间不够"并排"），改造前后行为等价
- 简化 CSS：移除 `.task-calendar-non-current-month-*` 规则，单月无"当前/非当前月"区分
- 减少 1 × `/task-assignments/calendar` API 调用

替代方案：保留双月但移动端上下堆叠 → 拒绝，任务目标本身是"简化为单月"。

### Decision 2：保留 CalendarPanel 子组件 + 复用模式

TaskCalendar 主组件只 new 一个 CalendarPanel；CalendarPanel 组件签名/行为不变。

理由：
- 现有 CalendarPanel 是独立数据获取单元，自带 loading/error/refetch 行为，复用风险最低
- 未来若需重新引入双月，只需在 TaskCalendar 中再加一个 CalendarPanel（参数 `nextMonth.year()/nextMonth.month()+1`）
- 不破坏父组件任何依赖（`weekLabelRender` / `dateCellRender` 等都不依赖月数）

### Decision 3：导航按钮保留

保留 `<` `>` 按钮，由父组件 `onNavigate` 控制 `baseMonth` 切换。

理由：
- 单月后用户仍需查看其他月份（特别是下月做下月任务规划）
- 导航按钮是 selectedRange 跨月交互的唯一入口（cell 不能跨月点击）
- 父组件 `calendarReducer` 的 `NAV_MONTH` action 已实现，无需改动

### Decision 4：非当前月 CSS 移除

CSS 块中所有 `.task-calendar-non-current-month-*` 规则删除：
- 非当前月面板的 cell-selected 透明覆盖
- 非当前月面板 today 浅蓝边框

理由：
- 单月后所有面板都是"当前月"，原"非当前月抑制高亮"逻辑失效
- 保留会引入无意义 CSS 规则

替代方案：保留 CSS 以防未来回滚到双月 → 拒绝，YAGNI 原则；旧规则在 git 历史中可恢复。

### Decision 5：任务徽章视觉与数据协议不变

`dateCellRender` 输出的红色任务总徽章（`top: -26, left: 20`）保持不变；数据协议 `CalendarData.days[dateKey].total` 也不变。

理由：
- 视觉一致性优先（家长用户已经熟悉徽章位置）
- 数据协议（`DayData.total`）不变 → 后端 E2E 与契约测试零修改

### Decision 6：响应式容器简化

`.task-calendar-grid` 当前使用 `grid-template-columns: repeat(auto-fit, minmax(400px, 1fr))` 双列布局。改为单月后：
- 移除 `.task-calendar-grid` 类的栅格布局样式
- 容器改为简单 block（`display: block`）+ `width: 100%`
- 移除 `@media (max-width: 767px)` 的响应式覆盖（不再需要）
- 保留 `className="task-calendar-grid"` 标识符（避免测试 `data-testid`/`getByTestId` 大规模改动）

## Risks / Trade-offs

- [Risk] 移除"非当前月"面板后，原 spec 中"非当前月面板中今天不显示高亮"场景需重新措辞 → **Mitigation**：delta spec 改写为"baseMonth 不包含今天时今天不显示高亮"，语义保持一致
- [Risk] TaskCalendar.test.tsx 现有用例假设双月渲染（双 CalendarPanel 断言）→ **Mitigation**：同步更新测试用例（删除双月断言、保留单月交互断言）
- [Risk] 父组件 ParentTasksPage 可能历史上有依赖双月面板布局的代码 → **Mitigation**：父组件只通过 props 传 baseMonth + selectedRange，不依赖内部布局，验证零父组件修改
- [Risk] 用户失去"一眼看到下月"的便利性 → **Mitigation**：保留导航按钮，标题区显示当前月；UX 退化可接受（两步变两步）
- [Risk] CSS 简化后如果某天回滚到双月，需要重新添加 `.task-calendar-non-current-month-*` 规则 → **Mitigation**：旧规则在 git history / GitHub blame 中可恢复；保留 CalendarPanel 子组件签名不变，回滚成本低
- [Risk] 单月后若产品未来想提供"下月任务预览"功能，会需要回到双月或改造组件 → **Mitigation**：本期不实现该功能；如未来需要，CalendarPanel 子组件签名不变，可平滑加回

## Migration Plan

部署步骤：
1. 修改 `web/src/parent/components/TaskCalendar.tsx`（移除第二个 CalendarPanel + 简化 CSS）
2. 更新 `web/src/parent/components/TaskCalendar.test.tsx` 用例（双月 → 单月）
3. 运行单元测试 `pnpm --filter web test TaskCalendar`
4. 运行类型检查 `pnpm --filter web typecheck`
5. 运行 E2E 测试 `pnpm test:e2e -- parent-task-calendar`

回滚策略：
- 本次变更只影响 `web/` 子项目前端代码，回滚 = `git revert` 即可
- TaskCalendar 组件四个 props（`baseMonth` / `selectedRange` / `onSelect` / `onNavigate`）签名不变，父组件 ParentTasksPage 不需要适配
- 后端 `/task-assignments/calendar` 无变更，无需考虑服务降级

## Open Questions

（无；本变更范围明确，集中在 TaskCalendar 内部渲染层，无需设计期决策）
