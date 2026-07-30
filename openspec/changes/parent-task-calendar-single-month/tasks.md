## 1. 单月化 TaskCalendar 组件

- [x] 1.1 修改 TaskCalendar 移除第二个 CalendarPanel：删除 `nextMonth` 计算与第二个 `<CalendarPanel>` 渲染（web/src/parent/components/TaskCalendar.tsx 约 437-451 行）
- [x] 1.2 简化导航栏标题：移除 `{currentMonth.format('YYYY年M月')} — {nextMonth.format('YYYY年M月')}` 中的 ` — {nextMonth.format(...)}` 部分，只保留当前月标题
- [x] 1.3 简化 CSS：删除 `.task-calendar-non-current-month-*` 全部规则（非当前月面板 cell-selected 透明覆盖、非当前月 today 浅蓝边框），保留 `.task-calendar-current-month-*` 全部规则
- [ ] 1.4 简化 Grid 容器：`.task-calendar-grid` 样式从 `display: grid; grid-template-columns: repeat(auto-fit, minmax(400px, 1fr))` 改为 `display: block; width: 100%`；移除 `<style>` 块中的 `@media (max-width: 767px)` 响应式覆盖

## 2. 同步更新单元测试

- [ ] 2.1 更新 `web/src/parent/components/TaskCalendar.test.tsx`：删除双月场景断言（双 CalendarPanel 渲染、双 API 请求、双月标题）
- [ ] 2.2 添加单月场景断言：单 CalendarPanel 渲染、导航按钮切换 baseMonth 触发单次 API 请求、单月标题格式

## 3. 验证

- [ ] 3.1 单元测试：`pnpm --filter web test TaskCalendar` 全部通过
- [ ] 3.2 类型检查：`pnpm --filter web typecheck` 通过
- [ ] 3.3 E2E：`pnpm test:e2e -- parent-task-calendar` 全部通过
