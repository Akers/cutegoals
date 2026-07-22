# parent-task-calendar Specification

## Purpose
TBD - created by archiving change parent-dual-month-task-calendar. Update Purpose after archive.
## Requirements
### Requirement: 双月日历渲染

家长任务分配页面 SHALL 使用双月日历视图替代原有日期选择器。日历 MUST 同时显示当月和下月。桌面视口（宽度 ≥ 768px）下两月日历 MUST 左右并排显示，移动视口（宽度 < 768px）下 MUST 上下堆叠显示。日历 SHALL 基于 Ant Design Calendar 组件实现，每个日期单元格 MUST 通过自定义渲染显示任务分布信息。

#### Scenario: 桌面端双月并排

- **WHEN** 家长在宽度 ≥ 768px 的设备上打开任务分配页面
- **THEN** 日历区域显示当月和下月两个日历面板左右并排

#### Scenario: 移动端双月堆叠

- **WHEN** 家长在宽度 < 768px 的设备上打开任务分配页面
- **THEN** 日历区域显示当月日历在上、下月日历在下，垂直堆叠

#### Scenario: 日历数据加载

- **WHEN** 日历组件初始化
- **THEN** 前端 MUST 分别请求当月和下月的 `GET /api/task-assignments/calendar?year=X&month=X`，并将返回的任务类型统计渲染到对应日期单元格

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

