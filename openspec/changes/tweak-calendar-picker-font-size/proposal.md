# Proposal: tweak-calendar-picker-font-size

## Why

家长在 `/parent/tasks` 任务分配页通过日历控件筛选日/周/月任务时，现有 `TaskCalendar.tsx` 中日期单元格、月份导航头部、周号列等日历选择控件的默认字号偏小，长时间操作下可读性较差，影响家长端操作效率。本次 tweak 将日历选择相关控件的字号在现有基础上统一增大 2 号（+2px），在不改变颜色、边框、间距等其他视觉属性的前提下提升可读性。

## What Changes

- 修改 `web/src/parent/components/TaskCalendar.tsx`：
  - `CalendarHeader`（月份导航头部：`YYYY年M月`）：`fontSize` 在当前值基础上 +2px
  - `WeekNumberColumn`（周号列单元格）：`fontSize` 在当前值基础上 +2px
  - 日期单元格文本（通过 `dateCellRender` 或 antd `Cell` 渲染的内容）：日期数字 `fontSize` 在当前值基础上 +2px
  - 任务类型徽章与状态文本：保持原样（不属于「日历选择控件」）
- 不修改 `CalendarSelection` / `CalendarAction` / `TaskCalendarProps` 等类型与状态机
- 不修改 `parent/pages/index.tsx` 中 calendarReducer 与查询逻辑
- 不修改后端 API、数据库、build / deploy 配置
- 不引入新依赖，不修改 `antd` / `dayjs` / 其他库版本

## Capabilities

### New Capabilities
（无；本 change 不引入新能力）

### Modified Capabilities
- `parent-task-calendar`：新增 Requirement「日历选择控件字号」（仅 ADDED Requirements，不改动现有 REQUIREMENTS）。理由：本次为可读性增强，定义家长端日历选择控件的最小字号基线；现有 requirement 描述的是颜色 / 边框 / 选中态视觉，不涉及字号，单独新增一条 requirement 表达基线，避免侵入现有 visual token 描述。

## Impact

- 代码文件：`web/src/parent/components/TaskCalendar.tsx`（单文件改动；如需同步调整，可附带 `web/src/parent/components/TaskCalendar.module.css` 或同目录样式文件）
- 测试：`web/src/parent/components/__tests__/TaskCalendar.test.tsx`、`web/src/parent/pages/__tests__/ParentTasksPage.test.tsx` 不含字号断言，原则上无需修改
- API / DB / 后端：无
- Spec：`parent-task-calendar` 不变；不创建 delta spec
- 视觉回归：仅在 parent 角色 Layout 内可见，不影响 admin / child