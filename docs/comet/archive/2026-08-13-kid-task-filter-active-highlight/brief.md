# Outcome

孩子端「我的任务」页面的状态筛选器（五分类）清晰高亮当前应用的筛选分类：选中项以孩子主题主题色背景 + 白色文字展示，与未选中项形成明确视觉区分，孩子能一眼辨认当前生效的筛选。纯视觉增强，不改变筛选逻辑与列表内容。

# Scope

- web/src/child/pages/index.tsx：ChildTasksPage 中 Segmented 筛选器选中态的高亮样式。
- web/src/child/__tests__/pages.test.tsx：补充选中态高亮的测试断言。

# Non-goals

- 不改变五分类归类逻辑、列表数据契约、提交受限展示及其他任何行为。
- 不改变家长端、管理端或孩子端其他页面。
- 不为各筛选分类使用不同颜色（统一选中高亮，不做分类着色）。
- 不涉及后端。

# Acceptance examples

- Given 孩子进入「我的任务」页面，When 页面加载完成，Then 默认选中的「进行中」筛选项以高亮样式（主题色背景）展示。
- Given 孩子点击「已逾期」筛选项，When 切换完成，Then 「已逾期」以高亮样式展示，其余四项为未选中样式。

# Constraints and invariants

- 使用孩子端 antd 主题 token（colorPrimary #0284c7）派生高亮色，保持儿童端风格（圆角、活泼感）一致。
- 高亮态文字与背景对比度充足（白字 + 主题色背景）。
- 保持 Segmented 的交互方式与可访问性语义（选中项 aria/role 状态不变）。

# Decisions

- D1：高亮形式为选中项主题色背景 + 白色文字，与未选中项明确区分；具体视觉细节（圆角、过渡、hover 态）由设计实现决定。
- D2：纯前端视觉改动，不改变任何行为契约；canonical 规格仅在筛选要求中补充选中态高亮的展示要求。
- D3：用户于 2026-08-12 确认共享理解摘要（选中项主题色背景+白字，默认「进行中」高亮，纯视觉不改行为）。

# Open questions

（无；用户已确认共享理解）

# Verification expectations

- 前端单元测试（vitest）：选中的筛选项呈现选中高亮态；既有五分类筛选测试全部保持通过。
- `npm run lint`（tsc --noEmit）不引入新错误。
