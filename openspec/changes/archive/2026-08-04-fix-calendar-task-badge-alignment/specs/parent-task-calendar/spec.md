# Delta Spec: parent-task-calendar

> 本 change 仅 `## MODIFIED Requirements`，未触动 `parent-task-calendar` 现有 requirement 主文；只新增 1 个 Scenario 明确「角标位置基线」，对齐当前实现层的修复。

## MODIFIED Requirements

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
