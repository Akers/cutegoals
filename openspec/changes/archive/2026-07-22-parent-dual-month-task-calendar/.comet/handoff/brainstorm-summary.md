# Brainstorm Summary

- Change: parent-dual-month-task-calendar
- Date: 2026-07-22

## 确认的技术方案

### 1. 周号列实现
CSS Grid 外层包装（grid-template-columns: 48px 1fr），左侧渲染 6 行 ISO 周号，右侧放置 antd Calendar。周号行高与 Calendar 行高同步（优先固定高度，偏移 > 2px 时启用 ResizeObserver 动态同步）。周号按每月 6 行中每行第一个日期的 isoWeek 计算。

### 2. 状态管理
ParentTasksPage 中使用 useReducer 统一管理：selectedRange（日/周/月 + 日期范围）、taskTypeFilters、viewAllMode、baseMonth。所有子组件通过 dispatch(action) 触发状态变更，查询参数集中由 reducer state 推导。

### 3. 日历数据加载
挂载时发起 2 次 `/calendar` 请求（当月+下月）。跨月导航时只请求新增月，复用已有数据。每个 Calendar 面板独立管理 loading/error/data。

### 4. 跨月周处理
点击周号时查询完整周范围（周一至周日），不截断到月边界。

### 5. 颜色渲染
使用 antd token（colorErrorBg/colorInfoBg/colorSuccessBg），优先级 LIMITED > REPEAT > STANDING。有任务时右上角显示 Badge 计数。选中高亮用 box-shadow 内边框或半透明背景。

### 6. 错误处理
轻量降级：单面板失败不影响另一面板，失败面板内显示小型 error 提示+重试。加载中显示 Skeleton。

### 7. 防抖
任务类型筛选变更时 300ms debounce 后触发 API 查询。

## 关键取舍与风险

- antd Calendar 行高变动风险（ResizeObserver 作为 fallback）
- snapshotTemplateTaskType 无独立索引（数据量小，风险可控）
- antd 主版本升级导致 DOM 结构变化（依赖公开 API，风险低）

## 测试策略

- 端到端：1 条完整用户流程
- 集成：2 条（后端 taskType 筛选 + 日历 taskTypes 统计）
- 组件：3 条（双月渲染、颜色标记、reducer 状态转换）
- 手动：响应式布局 + 移动端交互

## Spec Patch

- 在 `parent-task-calendar/spec.md` 的「日历点击交互」Requirement 下补充：ISO 周跨月边界时查询完整周范围的 Scenario
