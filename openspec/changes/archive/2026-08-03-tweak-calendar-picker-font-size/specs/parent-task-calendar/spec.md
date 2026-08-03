# Delta Spec: parent-task-calendar

> 本 change 仅 `## ADDED Requirements`，未触动 `parent-task-calendar` 任何现有 REQUIREMENTS；现有 Spec（main）的颜色 / 边框 / 选中态 / 周号 / 任务类型筛选等 requirement 保持不变。

## ADDED Requirements

### Requirement: 日历选择控件字号

家长任务分配页面 `/parent/tasks` 的日历选择控件 SHALL 满足下列最小字号基线，以满足家长端长时间操作的可读性需求：

- 日期单元格文本（antd `Calendar` 内部 `.ant-picker-calendar-date-value`）：实际渲染 `font-size` SHALL ≥ 16px。
- 月份导航标题（`CalendarHeader` 渲染的 `YYYY年M月`）：实际渲染 `font-size` SHALL ≥ 16px。
- 月份导航栏中央文本（`<' '>'` 中间的 `YYYY年M月`）：实际渲染 `font-size` SHALL ≥ 16px。
- 周号列单元格文本（`第N周`）：实际渲染 `font-size` SHALL ≥ 14px。

本 requirement 是 CSS 实现基线，不约束颜色 / 边框 / 间距 / 选中态视觉等其他属性（其他视觉属性由现有 requirement 描述）。antd `Calendar` token 或主题切换时，本 requirement 保证实际渲染字号不低于上述基线。

#### Scenario: 日期单元格字号

- **WHEN** 家长在 `/parent/tasks` 打开任务分配页面并查看日历
- **THEN** 实际渲染时 `.task-calendar-current-month .ant-picker-calendar-date-value` 的 `font-size` SHALL ≥ 16px

#### Scenario: 周号列字号

- **WHEN** 家长在 `/parent/tasks` 查看日历左侧周号列
- **THEN** 实际渲染时每个周号行（`第N周`）的 `font-size` SHALL ≥ 14px

#### Scenario: 月份标题与导航栏中央文本字号

- **WHEN** 家长在 `/parent/tasks` 查看日历月份标题（`CalendarHeader`）与月份导航栏中央文本（`<' '>'` 中间）
- **THEN** 这两处实际渲染的 `font-size` SHALL ≥ 16px