# Outcome

修复上一个变更（kid-task-filter-active-highlight）引入的视觉回归：孩子端「我的任务」页的五分类筛选器（antd Segmented）在真实浏览器中不可见，导致用户无法切换筛选状态，列表长期停留在默认「进行中」分类（若该分类无数据即显示空态）。修复后筛选器在该页面正常可见，选中项以孩子主题色背景 + 白字突出显示，行为与原意一致。

# Scope

- web/src/styles/themes.ts：回滚 `childTheme.components.Segmented`（恢复 canonical 状态）。
- web/src/child/pages/index.tsx：为 ChildTasksPage 的 Segmented 添加作用域受限的 className 与内联 `<style>`，仅对本实例的 `.ant-segmented-item-selected` 应用主题色背景 + 白字高亮。

# Non-goals

- 不改变五分类归类逻辑、列表数据契约、提交受限展示、默认选中项。
- 不为各筛选分类上不同颜色（统一选中高亮）。
- 不改家长端、管理端或孩子端其他页面。
- 不涉及后端。
- 不修复 TaskTemplateServiceTest 的 4 个预存失败或 parent 组件 TS6198。

# Acceptance examples

- Given 孩子进入「我的任务」页面，When 页面加载完成，Then 可见五分类筛选器（含默认选中的「进行中」），选中项以主题色背景 + 白字突出显示。
- Given 孩子点击「已逾期」筛选项，When 切换完成，Then 「已逾期」以主题色背景 + 白字突出显示，列表同步切换为对应分类。
- Given 修复部署完成，Then 真实浏览器中筛选器可见且可交互，vitest 23/23 保持通过。

# Constraints and invariants

- 修复 MUST 不影响 Segmented 的可访问性语义（role/aria 选中态保留）。
- 高亮色继续使用孩子端主题色（colorPrimary #0284c7）与白字，WCAG AA 对比度达标。
- 修复 MUST 不改变 childTheme 的 token 字段（其他下游组件的样式不受影响）。
- 修复 MUST 仅作用于 ChildTasksPage 的 Segmented，不影响应用其他位置（如有）的 Segmented。

# Decisions

- D1：放弃全局 `components.Segmented` token 方式（作用域过广，与具体渲染上下文耦合存在视觉回归风险），改用作用域受限的局部样式（className + style 标签）仅作用于目标 Segmented。
- D2：行为契约不变（默认「进行中」、切换高亮、五分类互归类、列表契约），仅修复实现方式。
- D3：用户于 2026-08-13 确认修复方案（回滚全局 Segmented token，改用仅作用于 ChildTasksPage Segmented 的局部样式）。

# Open questions

（无；用户已确认修复方案）

# Verification expectations

- vitest src/child 23/23 通过（既有五分类测试 + 高亮可交互）。
- npm run lint（tsc --noEmit）不引入新错误。
- 真实浏览器渲染（编排者目检 + 用户确认）：筛选器可见、可点击切换、选中态主题色背景 + 白字。