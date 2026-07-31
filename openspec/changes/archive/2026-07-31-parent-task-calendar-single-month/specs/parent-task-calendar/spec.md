## MODIFIED Requirements

### Requirement: 双月日历渲染

家长任务分配页面 SHALL 使用单月日历视图替代原有日期选择器。日历 MUST 仅显示当前 baseMonth 一个月（桌面与移动视口均使用同一布局）。日历 SHALL 基于 Ant Design Calendar 组件实现，每个日期单元格 MUST 通过自定义渲染显示任务分布信息。用户 SHALL 通过导航按钮 "<>" 切换 baseMonth 查看其他月份；切换月份后 MUST 重新请求目标月份的日历数据。

> **变更说明**：原 Requirement 的双月视图已替换为单月视图。"桌面端双月并排" 与 "移动端双月堆叠" 两个 Scenario 因双月布局移除而不再适用，由 "单月显示" Scenario 取代。"日历数据加载" Scenario 的 API 请求语义从"分别请求当月和下月"改为"仅请求当月（单月下不需要双月 API）"，其余文案保留。

#### Scenario: 单月显示

- **WHEN** 家长在任意视口宽度下打开任务分配页面
- **THEN** 日历区域显示当前 baseMonth 一个日历面板（占满容器宽度），不显示下月或其他月份的并列面板

#### Scenario: 桌面端双月并排

- **WHEN** 家长在宽度 ≥ 768px 的设备上打开任务分配页面
- **THEN** 日历区域显示当月和下月两个日历面板左右并排
- **注**：此 Scenario 因本 change 改为单月视图已不再适用,仅作为历史保留;新行为见 "单月显示" Scenario。

#### Scenario: 移动端双月堆叠

- **WHEN** 家长在宽度 < 768px 的设备上打开任务分配页面
- **THEN** 日历区域显示当月日历在上、下月日历在下,垂直堆叠
- **注**：此 Scenario 因本 change 改为单月视图已不再适用,仅作为历史保留;新行为见 "单月显示" Scenario。

#### Scenario: 日历数据加载

- **WHEN** 日历组件初始化或导航按钮切换 baseMonth
- **THEN** 前端 MUST 仅请求目标月份的 `GET /api/task-assignments/calendar?year=X&month=X`（每次仅一次请求），并将返回的任务类型统计渲染到对应日期单元格

#### Scenario: 通过导航按钮切换月份

- **WHEN** 家长点击日历上方的 "<" 或 ">" 导航按钮
- **THEN** baseMonth 切换到上一月或下一月，日历面板重新渲染为目标月份，导航栏标题更新为对应"YYYY年M月"格式

### Requirement: 默认选中今日与本周

家长任务分配页面 SHALL 由父组件（ParentTasksPage）的 calendarReducer 决定默认选择状态。今天 cell 视觉由 selectedRange 类型决定：当 `selectedRange.type='day'` 且 `startDate=今天` 时，today cell SHALL 显示选中态视觉（teal 实心 + 白字），与手动选中日视觉一致；其他 selectedRange 类型（含 week / month / null）下，today cell SHALL 显示 today 视觉（浅蓝边框 + teal 字色），区别于选中态视觉。当前周号行 SHALL 视觉高亮且区别于非选中周。日历仅显示当前 baseMonth 一月时，今天视觉 SHALL 仅在 baseMonth 包含今天时显示。

#### Scenario: 默认进入页面，今天与本周被选中

- **WHEN** 家长打开 `/parent/tasks` 任务分配页面（父组件默认 selectedRange = day + today）
- **THEN** 今天日期 cell 显示选中态视觉（teal 实心 + 白字，与手动选中日视觉一致），今天所在周号行显示选中态视觉（蓝色边框），下方任务列表查询范围为今天所在周（ISO 8601 周一到周日）

#### Scenario: 用户点击某天后 today 不再单独高亮（fix-build）

- **WHEN** 家长在已打开页面（默认选今天 + 本周）后点击某个非今天的日期 cell（如 2026-07-15）
- **THEN** 该日期 cell 显示选中态视觉，今天日期 cell 不再显示选中态视觉，改由 today 视觉呈现（浅蓝边框 + teal 字色；today 高亮由 antd 自动标记 today 类 + CSS today 规则命中，与 selectedRange 范围无关），下方任务列表查询该日期的任务

#### Scenario: 用户点击非本周周号，今天仍高亮

- **WHEN** 家长点击非今天所在周的周号行
- **THEN** 该周号行显示选中态视觉，今天所在周号行不高亮（仅单个周号行高亮），今天日期 cell 显示 today 视觉（浅蓝边框 + teal 字色，由 antd 自动 + CSS 命中），下方任务列表查询所点击周的范围

#### Scenario: 非当前月面板中今天不显示高亮

- **WHEN** 当前显示月面板不包含今天日期（如用户手动导航到下个月）
- **THEN** 该面板内今天 cell 不显示高亮（今天高亮仅在今天所在月面板内有效）

#### Scenario: baseMonth 包含今天时的 today 视觉

- **WHEN** baseMonth 包含今天日期（如 baseMonth='2026-07' 且 today='2026-07-24'）且 selectedRange 范围不包含 today
- **THEN** today cell 由 antd 自动加 `.ant-picker-cell-today` 类，CSS 中 `.task-calendar-current-month .ant-picker-cell-today .ant-picker-calendar-date-value { color: #0d9488 }` 规则命中（teal 字色），CSS 中 `.task-calendar-current-month .ant-picker-cell-today:not(.ant-picker-cell-selected) .ant-picker-calendar-date { border: 1px solid #1677ff }` 规则命中（浅蓝边框）

#### Scenario: day 选中时该天所在周号行也高亮（fix-build）

- **WHEN** 家长选中某一天（如 2026-07-15）
- **THEN** 该日期 cell 显示选中态视觉（teal 实心 + 白字），该日期所在周号行也显示选中态视觉（蓝色边框），下方任务列表查询该日期所在周的任务