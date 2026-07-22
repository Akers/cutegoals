# Task 3.1-3.4: ParentTasksPage 集成改造 — 完成报告

## 概述

在 `web/src/parent/pages/index.tsx` 中完成了四个集成改造任务，将旧日期的 date input 替换为 TaskCalendar + useReducer 状态管理。

## 3.1: useReducer 状态管理

- 在 `ParentTasksPage` 组件上方添加了 `CalendarPageState` 接口、`CalendarAction2` 类型和 `calendarReducer` 纯函数 reducer
- Reducer 支持 6 种 action: `SELECT_DATE`、`SELECT_WEEK`、`SELECT_MONTH`、`SET_FILTERS`、`VIEW_ALL`、`NAV_MONTH`
- 组件内部使用 `useReducer` 初始化日历状态：baseMonth、selectedRange、taskTypeFilters（默认全选三种类型）、viewAllMode

## 3.2: 动态查询 URL + debounce

- 删除了旧的 `const [date, setDate] = useState(...)`
- 添加了 `useRef<setTimeout>` 的 debounce 定时器和 `queryPath` 状态
- 添加了 `buildQuery()` 函数，根据 calendarState 动态构建 API 路径（含 taskType 筛选和日期范围）
- 300ms debounce effect 监听 calendarState 变化
- `useApi` 改为动态 `queryPath`，每次查询 pageSize=20（原为 100）

## 3.3: 替换日历 Card + 添加筛选器

- 旧 `<Card title="日历">` 中 date input 替换为完整 `<TaskCalendar>` 组件
- `onSelect` 映射 `CalendarAction → CalendarAction2`（SELECT_DATE / SELECT_WEEK / SELECT_MONTH）
- `onNavigate` 映射 `-1/1 → NAV_MONTH`
- 新增第二个 `<Card>` 包含：
  - `<TaskTypeFilter>` 组件（任务类型筛选多选框）
  - `查看全部` 按钮（切换 viewAllMode，激活时显示"查看全部（已激活）"）

## 3.4: 保留现有状态

- 保留了 `templates`、`children`、`childNameMap`、`showAssign`、`showSingleAssign`、各种 `useFormField`、弹窗逻辑等所有非日历相关的状态和交互

## TDD 测试

新增测试文件: `web/src/parent/pages/__tests__/ParentTasksPage.test.tsx`

### Reducer 纯函数测试（19 个用例）
- SELECT_DATE: 单日选择
- SELECT_WEEK: 周选择、跨月、周日到周六
- SELECT_MONTH: 整月、闰年2月、平年2月
- SET_FILTERS: 单选、空数组、不修改其他状态
- VIEW_ALL: 清除选择、重复 VIEW_ALL
- NAV_MONTH: 前进、后退、跨年前进、跨年后退
- 复合交互: SELECT_DATE→VIEW_ALL、VIEW_ALL→SELECT_DATE、SET_FILTERS→NAV_MONTH

### 组件渲染测试（13 个用例）
- 页面标题、分配按钮、日历 card、任务列表 card、查看全部按钮
- 任务类型筛选 card、三种类型复选框
- loading 状态显示 Spin
- error 状态显示错误信息
- 空数据提示"当天暂无任务"
- 渲染任务列表项

## 测试结果

- 新增测试: 32 passed ✓
- 全量测试: 170 tests passed across 17 files ✓

## 文件变更

| 文件 | 变更 |
|------|------|
| `web/src/parent/pages/index.tsx` | 添加 imports、reducer、state management、TaskCalendar + TaskTypeFilter Card |
| `web/src/parent/pages/__tests__/ParentTasksPage.test.tsx` | 新建 — 32 个测试用例 |
