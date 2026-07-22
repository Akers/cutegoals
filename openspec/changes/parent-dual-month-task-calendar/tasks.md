# 家长端双月任务日历 — 任务清单

## 1. 后端：日历 API 扩展任务类型统计

- [x] 1.1 修改 `TaskAssignmentService.getCalendar` 方法，在每日聚合数据中新增 `taskTypes` 字段（`{LIMITED: n, REPEAT: n, STANDING: n}`），遍历当天分配时按 `snapshotTemplateTaskType` 分组计数
  - **能力**: `task-assignment`
  - **验证**: 调用 `GET /api/task-assignments/calendar?year=2026&month=7`，检查每日返回数据包含 `taskTypes` 对象且值与该日分配的任务类型一致

## 2. 后端：任务列表 API 新增 taskType 筛选

- [x] 2.1 修改 `TaskAssignmentController.queryAssignments` 方法签名，新增可选参数 `taskType`（String，逗号分隔多值）
- [x] 2.2 修改 `TaskAssignmentService.queryAssignments` 方法，当 `taskType` 参数非空时，按逗号分割后在 `snapshotTemplateTaskType` 字段上使用 `in` 条件过滤
  - **能力**: `task-assignment`
  - **验证**: 调用 `GET /api/task-assignments?taskType=LIMITED&page=1&pageSize=20`，确认返回仅 `snapshotTemplateTaskType=LIMITED` 的分配；调用 `GET /api/task-assignments?taskType=LIMITED,REPEAT`，确认返回两种类型

## 3. 后端：确保可选日期范围查询

- [x] 3.1 修改 `TaskAssignmentService.queryAssignments`，当 `startDate` 或 `endDate` 参数未传入时，不添加日期过滤条件，使"查看全部"模式正常工作
  - **能力**: `task-assignment`, `parent-pages-contract`
  - **验证**: 调用 `GET /api/task-assignments?taskType=STANDING&page=1&pageSize=20`（不传 startDate/endDate），确认返回所有 STANDING 分配

## 4. 前端：创建 TaskCalendar 双月日历组件

- [x] 4.1 新建 `web/src/parent/components/TaskCalendar.tsx`，基于 antd `Calendar` 组件实现双月布局：当月和下月日历面板，使用 CSS Grid/Flex 自适应（≥768px 左右并排，<768px 上下堆叠）
- [x] 4.2 实现月头 `headerRender` 自定义渲染，添加点击事件
- [x] 4.3 实现 `dateCellRender` 自定义日期单元格渲染：根据 `taskTypes` 数据按优先级（LIMITED > REPEAT > STANDING）设置背景色，右上角显示任务总数徽章
  - **能力**: `parent-task-calendar`
  - **验证**: 页面加载后显示双月日历，有任务的日期格子显示对应颜色和计数

## 5. 前端：周号指示器与日/周/月三级点击交互

- [x] 5.1 在日历面板左侧添加 ISO 8601 周号列，当周有任务时周号单元格着色
- [x] 5.2 实现日期单元格点击：传递 `{startDate, endDate}` 到父组件
- [x] 5.3 实现周号点击：计算该周周一至周日的日期范围
- [x] 5.4 实现月头点击：计算该月 1 日至月末的日期范围
- [x] 5.5 当前选中的日/周/月有视觉高亮状态（选中态 vs 非选中态）
  - **能力**: `parent-task-calendar`
  - **验证**: 点击日期 → 下方任务列表刷新为当天任务；点击周号 → 当周任务；点击月头 → 当月任务

## 6. 前端：日历数据获取与状态管理

- [x] 6.1 TaskCalendar 组件使用 `useApi` 分别请求当月和下月的 `/api/task-assignments/calendar` 端点，获取含 `taskTypes` 的聚合数据
- [x] 6.2 将日历数据传递给 `dateCellRender` 渲染颜色标记
  - **能力**: `parent-task-calendar`
  - **验证**: 日历正确显示两个月的任务分布，颜色标记与后端数据一致

## 7. 前端：集成任务类型筛选与查看全部

- [x] 7.1 在 `ParentTasksPage` 中引入现有 `TaskTypeFilter` 组件，放置在日历下方
- [x] 7.2 添加「查看全部」按钮，点击时清除日期范围，保留 `taskType` 筛选，重新查询任务列表
- [x] 7.3 任务类型筛选变更时，保持当前日期范围，附加 `taskType` 参数重新查询
- [x] 7.4 「查看全部」模式下清除日历选中高亮
  - **能力**: `parent-task-calendar`, `parent-pages-contract`
  - **验证**: 选择「限时任务」+ 点击「查看全部」→ 返回所有限时任务；筛选类型变更 → 仅刷新任务列表

## 8. 前端：替换旧日历控件并整合数据流

- [x] 8.1 在 `ParentTasksPage` 中移除旧的 `<input type="date">` 控件和相关 `date` 状态
- [x] 8.2 替换为 `TaskCalendar` 组件，接收 `onDateRangeChange` 和 `selectedRange` props
- [x] 8.3 修改任务列表的 `useApi` 查询逻辑：支持 `startDate`、`endDate`、`taskType` 动态组合
- [x] 8.4 日历颜色标记始终显示所有类型，不受下方类型筛选器影响
  - **能力**: `parent-task-calendar`, `parent-pages-contract`
  - **验证**: 页面正常渲染双月日历、筛选器和任务列表，交互逻辑完整

## 9. 验收测试

- [x] 9.1 端到端验收：桌面端双月并排 → 点击有色日期 → 任务列表正确 → 切换筛选类型 → 列表刷新 → 点击查看全部 → 全部匹配结果 → 点击日历恢复日期模式
- [x] 9.2 移动端验收：缩小视口至 <768px → 日历上下堆叠 → 交互正常
  - **能力**: `parent-task-calendar`
  - **验证**: 全部验收场景通过，无控制台错误
