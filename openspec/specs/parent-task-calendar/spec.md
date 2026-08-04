# parent-task-calendar Specification

## Purpose
TBD - created by archiving change parent-dual-month-task-calendar. Update Purpose after archive.
## Requirements
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

### Requirement: 任务类型颜色标记

日历日期单元格 SHALL 根据该日是否存在任务分配及其任务类型进行颜色标记。无任务的日期 MUST 保持默认样式。有任务的日期 MUST 按以下优先级色显示：当日有 LIMITED 类型任务时显示红色系；否则有 REPEAT 类型时显示蓝色系；否则有 STANDING 类型时显示绿色系。当日有多个任务时，单元格右上角 MUST 显示任务总数徽章。

#### Scenario: 某日有多种类型任务

- **WHEN** 2026-07-15 有 2 个 LIMITED 任务和 1 个 REPEAT 任务
- **THEN** 该日期单元格显示红色系背景（LIMITED 优先级最高），右上角显示徽章「3」

#### Scenario: 某日仅有 STANDING 任务

- **WHEN** 2026-07-20 仅有 1 个 STANDING 任务
- **THEN** 该日期单元格显示绿色系背景，右上角显示徽章「1」

#### Scenario: 某日无任务

- **WHEN** 2026-07-25 没有任何任务分配
- **THEN** 该日期单元格显示默认样式，无徽章

#### Scenario: 角标位置基线 (fix-calendar-task-badge-alignment)

- **WHEN** 任何有任务的日期 cell（`total > 0`）通过 antd `dateCellRender` 渲染任务数角标（`data-testid="task-badge-<YYYY-MM-DD>"`）
- **THEN** 角标实际渲染位置 SHALL 贴该日期内盒（.ant-picker-cell-inner）右上角（即 inline style `position: absolute; top: 0px; right: 0px`）；角标 SHALL NOT 跨出 cell 顶部进入上一行日期行；不同浏览器 / antd 版本下位置基线不变（由内联 style + vitest 断言兜底）

### Requirement: 周号指示器

日历左侧 SHALL 显示 ISO 8601 周号。当周内任一天有任务分配时，该周号单元格 MUST 显示颜色标记（与优先级最高的任务类型同色），无任务的周 MUST 保持默认样式。周号 MUST 可点击。

#### Scenario: 当周有任务

- **WHEN** 2026 年第 30 周内至少有一天存在任务分配
- **THEN** 该周号单元格显示颜色标记

#### Scenario: 当周无任务

- **WHEN** 2026 年第 31 周内没有任何任务分配
- **THEN** 该周号单元格显示默认样式

### Requirement: 日历点击交互

用户 SHALL 能够通过点击日历的日、周、月三级粒度来筛选下方任务列表。点击日期单元格 MUST 触发查询该日期的任务列表；点击周号 MUST 触发查询该周（周一至周日）的任务列表；点击月面板头部 MUST 触发查询该月的任务列表。当前选中的日/周/月 MUST 有视觉高亮状态区分。

#### Scenario: 点击日期查看当天任务

- **WHEN** 家长点击 2026-07-15 的日期单元格
- **THEN** 下方任务列表刷新为 `startDate=2026-07-15&endDate=2026-07-15` 的查询结果，且 7 月 15 日单元格显示选中高亮

#### Scenario: 点击周号查看当周任务

- **WHEN** 家长点击第 30 周的周号
- **THEN** 下方任务列表刷新为该周周一至周日的日期范围查询结果，且该周号显示选中高亮

#### Scenario: 周号跨月边界查询完整周

- **WHEN** 家长点击第 31 周的周号，该周的一部分日期属于 7 月、另一部分属于 8 月
- **THEN** 下方任务列表 MUST 查询该周周一至周日的完整日期范围（含跨月部分）

#### Scenario: 点击月头查看当月任务

- **WHEN** 家长点击当月日历面板的月头（如「2026年7月」）
- **THEN** 下方任务列表刷新为该月 1 日至月末的日期范围查询结果，且月头显示选中高亮

### Requirement: 任务类型筛选器

日历下方 SHALL 集成任务类型筛选器，使用复选框多选方式（限时任务 / 重复任务 / 常驻任务）。默认所有类型选中。筛选器变更时 MUST 重新查询任务列表（保持当前日期范围，附加类型筛选条件），同时日历颜色标记 MUST 保持不变（日历仍显示所有类型）。

#### Scenario: 仅勾选「限时任务」

- **WHEN** 家长在任务类型筛选器中取消「重复任务」和「常驻任务」，仅保留「限时任务」
- **THEN** 下方任务列表刷新为 `taskType=LIMITED` 加当前日期范围的查询结果

#### Scenario: 取消所有类型

- **WHEN** 家长取消所有任务类型勾选
- **THEN** 下方任务列表显示空结果（无匹配任务）

### Requirement: 查看全部按钮

日历下方 SHALL 提供「查看全部」按钮。点击该按钮时 MUST 移除当前日期范围约束，但保留任务类型筛选条件，向 `GET /api/task-assignments` 发起查询，返回符合类型筛选的全部分配记录（分页）。日历上的选中状态 MUST 清除。

#### Scenario: 查看全部限时任务

- **WHEN** 家长仅勾选「限时任务」并点击「查看全部」按钮
- **THEN** 系统查询 `GET /api/task-assignments?taskType=LIMITED&page=1&pageSize=20`，不传 startDate/endDate，返回所有限时任务分配。日历选中状态清除

#### Scenario: 查看全部后重新选择日期

- **WHEN** 家长在「查看全部」模式下点击日历中的某个日期
- **THEN** 系统退出「查看全部」模式，恢复日期范围查询，日历恢复选中状态

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

