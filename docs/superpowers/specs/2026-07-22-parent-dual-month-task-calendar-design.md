---
comet_change: parent-dual-month-task-calendar
role: technical-design
canonical_spec: openspec
archived-with: 2026-07-22-parent-dual-month-task-calendar
status: final
---

# 家长端双月任务日历 — 深度技术设计

## 1. 架构概览

本 change 将家长端任务分配页面的日历模块从简单 `<input type="date">` 重构为基于 antd Calendar 的双月日历视图，新增任务类型颜色标记、日/周/月三级点击交互和任务类型筛选。

核心数据流：

```
ParentTasksPage (useReducer 集中状态管理)
  │
  ├─ TaskCalendar (双月日历)
  │   ├─ useApi('/calendar?year=Y&month=M') × 2 → 日历颜色数据
  │   ├─ CSS Grid: [周号列 48px] [Calendar Month1/Month2]
  │   └─ 点击 → dispatch({type: 'SELECT_DATE|WEEK|MONTH', payload})
  │
  ├─ TaskTypeFilter (复用现有复选框组件)
  │   └─ 变更 → dispatch({type: 'SET_FILTERS', payload})
  │
  ├─ "查看全部" Button
  │   └─ 点击 → dispatch({type: 'VIEW_ALL'})
  │
  └─ TaskList
      └─ useApi('/task-assignments?startDate=X&endDate=X&taskType=...&page=P')
```

## 2. 组件设计

### 2.1 TaskCalendar 组件

**Props 接口**:

```typescript
interface TaskCalendarProps {
  baseMonth: string;              // 'YYYY-MM' 基准月
  selectedRange: CalendarSelection | null;
  onSelect: (action: CalendarAction) => void;
  onNavigate: (direction: -1 | 1) => void;
}

interface CalendarSelection {
  type: 'day' | 'week' | 'month';
  startDate: string;             // 'YYYY-MM-DD'
  endDate: string;
}

type CalendarAction =
  | { type: 'SELECT_DATE'; date: string }
  | { type: 'SELECT_WEEK'; startDate: string }
  | { type: 'SELECT_MONTH'; year: number; month: number };
```

**内部状态**:

```typescript
// 每个面板独立管理自己的 API 状态
const month1 = useApi<CalendarData>(`/task-assignments/calendar?year=${y}&month=${m}`);
const month2 = useApi<CalendarData>(`/task-assignments/calendar?year=${y}&month=${m+1}`);
```

**双月布局（TSX 结构）**:

```tsx
<div className="task-calendar" 
     style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(400px, 1fr))', gap: 16 }}>
  {/* Month 1 Panel */}
  <div className="calendar-panel" style={{ display: 'grid', gridTemplateColumns: '48px 1fr' }}>
    <WeekNumberColumn 
      dates={month1Dates} 
      calendarData={month1.data?.days}
      selectedRange={selectedRange}
      onWeekClick={(startDate) => onSelect({ type: 'SELECT_WEEK', startDate })} 
    />
    <Calendar
      value={dayjs(`${baseMonth}-01`)}
      fullscreen={false}
      headerRender={...}
      dateCellRender={dateCellRender(month1.data?.days, selectedRange)}
    />
  </div>

  {/* Month 2 Panel (same structure) */}
  <div className="calendar-panel" ...>...</div>
</div>
```

### 2.2 WeekNumberColumn 组件

计算每月的 6 行周号，与 Calendar 行高对齐：

```typescript
function computeWeekNumbers(year: number, month: number): WeekRow[] {
  const firstDay = dayjs(`${year}-${month}-01`).startOf('month');
  const lastDay = firstDay.endOf('month');
  let current = firstDay.startOf('week'); // Sunday
  const rows: WeekRow[] = [];
  
  for (let i = 0; i < 6; i++) { // Calendar 固定 6 行
    const weekNum = current.isoWeek();
    rows.push({ weekNum, startDate: current, endDate: current.add(6, 'day') });
    current = current.add(1, 'week');
  }
  return rows;
}
```

行高对齐：使用 `antd` Calendar 的 CSS token `--ant-calendar-cell-height` 变量，或通过 CSS 选择器 `.ant-picker-cell` 获取计算高度。偏移 > 2px 时使用 `ResizeObserver` 动态同步。

### 2.3 颜色标记逻辑 (dateCellRender)

```typescript
function dateCellRender(calendarData: CalendarData['days'], selectedRange: CalendarSelection | null) {
  return (date: Dayjs) => {
    const dateStr = date.format('YYYY-MM-DD');
    const dayData = calendarData?.[dateStr];
    if (!dayData || dayData.total === 0) return <div className="cell-empty">{date.date()}</div>;

    const { taskTypes, total } = dayData;
    let bgColor: string;
    if (taskTypes.LIMITED > 0) bgColor = 'var(--ant-color-error-bg)';
    else if (taskTypes.REPEAT > 0) bgColor = 'var(--ant-color-info-bg)';
    else bgColor = 'var(--ant-color-success-bg)';

    const isSelected = selectedRange && dateStr >= selectedRange.startDate && dateStr <= selectedRange.endDate;

    return (
      <div className={`cell-task ${isSelected ? 'cell-selected' : ''}`} style={{ background: bgColor }}>
        <Badge count={total} size="small" offset={[-2, 2]}>
          {date.date()}
        </Badge>
      </div>
    );
  };
}
```

选中高亮 CSS：
```css
.cell-selected { box-shadow: inset 0 0 0 2px var(--ant-color-primary); }
```

### 2.4 TaskTypeFilter 集成

复用 `web/src/parent/components/TaskTypeFilter.tsx`。放在日历下方，props 接口不变：

```typescript
<TaskTypeFilter 
  selected={state.taskTypeFilters} 
  onChange={(types) => dispatch({ type: 'SET_FILTERS', payload: types })} 
/>
```

筛选变更时 300ms debounce 触发任务列表查询，避免连续点击导致冗余请求。

## 3. 状态管理（useReducer）

### 3.1 State 定义

```typescript
interface CalendarPageState {
  baseMonth: string;                          // 'YYYY-MM'
  selectedRange: CalendarSelection | null;    // null = 无选中
  taskTypeFilters: TaskTypeValue[];           // 默认 ['LIMITED', 'REPEAT', 'STANDING']
  viewAllMode: boolean;
}
```

### 3.2 Action 与转换

| Action | 转换逻辑 |
|--------|---------|
| `SELECT_DATE(date)` | `{ selectedRange: {type:'day', start:date, end:date}, viewAllMode: false }` |
| `SELECT_WEEK(startDate)` | `{ selectedRange: {type:'week', start:startDate, end:startDate+6d}, viewAllMode: false }` |
| `SELECT_MONTH(y, m)` | `{ selectedRange: {type:'month', start:y-m-01, end:月末}, viewAllMode: false }` |
| `SET_FILTERS(types)` | `{ taskTypeFilters: types }`（仅改筛选，不影响选中范围或 viewAllMode） |
| `VIEW_ALL` | `{ viewAllMode: true, selectedRange: null }` |
| `NAV_MONTH(dir)` | `{ baseMonth: baseMonth ± 1 Month }` |

### 3.3 查询参数派生

```typescript
function buildQueryParams(state: CalendarPageState, page: number) {
  const params: Record<string, string> = { 
    page: String(page), 
    pageSize: '20' 
  };

  if (state.taskTypeFilters.length > 0 && state.taskTypeFilters.length < 3) {
    params.taskType = state.taskTypeFilters.join(',');
  }

  if (!state.viewAllMode && state.selectedRange) {
    params.startDate = state.selectedRange.startDate;
    params.endDate = state.selectedRange.endDate;
  }

  return params;
}
```

> 注意：`taskTypeFilters` 全选（3 个全勾选）时不上传 `taskType` 参数，因为与不传等价。空选（0 个勾选）时也不传，列表自然为空或全返回。

修正：全选时传所有类型 = 不传参数效果一致，避免额外参数；空选时传空字符串，后端按空处理返回全部或无结果（由后端行为决定）。根据 spec：全选 3 个时不传 `taskType`；空选时列表应返回空。

## 4. 边界条件处理

| 场景 | 策略 |
|------|------|
| 月初/月末 ISO 周跨月 | 周号在两月面板均显示，点击时各自独立触发完整周查询 |
| 2 月仅 5 行 | Calendar 始终渲染 6 行，最后一行含下月日期；周号列也渲染 6 行 |
| 查看全部模式下无结果 | `<Empty description="没有符合条件的任务分配" />` |
| 日历 API 单面板失败 | 失败面板显示 `<Alert type="error" message="加载失败" action="重试" />`；另一面板正常 |
| 所有日期无任务 | 日历正常白色渲染，无 Badge |
| 类型筛选 debounce | 使用 `useMemo` + `setTimeout`，300ms 延迟触发 API 查询 |
| 同时选中日/周/月冲突 | 最新点击覆盖之前选中，互斥 |

## 5. 后端实现要点

### 5.1 getCalendar 扩展

在 `TaskAssignmentService.getCalendar` 中遍历每日分配时，额外按 `snapshotTemplateTaskType` 分组计数：

```java
// 在 getCalendar 循环中增加
String taskType = assignment.getSnapshotTemplateTaskType();
if (taskType != null) {
    @SuppressWarnings("unchecked")
    Map<String, Integer> typeCounts = (Map<String, Integer>) dayData
        .computeIfAbsent("taskTypes", k -> new LinkedHashMap<>());
    typeCounts.merge(taskType, 1, Integer::sum);
}
// 确保三种类型都有默认 0
Map<String, Integer> taskTypes = (Map<String, Integer>) dayData.get("taskTypes");
if (taskTypes == null) {
    taskTypes = new LinkedHashMap<>();
    taskTypes.put("LIMITED", 0);
    taskTypes.put("REPEAT", 0);
    taskTypes.put("STANDING", 0);
    dayData.put("taskTypes", taskTypes);
}
```

### 5.2 queryAssignments 扩展

Controller 新增 `@RequestParam(required = false) String taskType`，Service 中当非空时：

```java
if (taskType != null && !taskType.isBlank()) {
    String[] types = taskType.split(",");
    wrapper.in(TaskAssignment::getSnapshotTemplateTaskType, Arrays.asList(types));
}
```

日期范围参数改为完全可选：

```java
// 原逻辑：startDate 和 endDate 必传
// 新逻辑：仅在两者都非空时添加日期过滤
if (startDate != null && !startDate.isBlank() && endDate != null && !endDate.isBlank()) {
    wrapper.ge(TaskAssignment::getDeadline, startDateTime);
    wrapper.le(TaskAssignment::getDeadline, endDateTime);
}
```

## 6. 测试策略

- **后端集成测试**：2 条
  - `getCalendar` 返回 `taskTypes` 字段且值正确
  - `queryAssignments` 带 `taskType` 参数正确过滤
- **前端组件测试**：3 条
  - TaskCalendar 双月渲染（快照测试 + 颜色标记验证）
  - `dateCellRender` 颜色优先级（模拟不同 taskTypes 组合）
  - useReducer 状态转换逻辑正确性
- **端到端**：1 条完整用户流程
  - 加载页面 → 日历显示 → 点击有色日期 → 任务列表正确 → 切换筛选 → 列表刷新 → 查看全部 → 全部结果 → 点击日历恢复日期模式
- **手动验证**：响应式布局 + 移动端交互

## 7. Spec Patch

在 delta spec `parent-task-calendar/spec.md` 的「日历点击交互」Requirement 下补充跨月周场景：

```markdown
#### Scenario: 周号跨月边界查询完整周

- **WHEN** 家长点击第 31 周的周号，该周的一部分日期属于 7 月、另一部分属于 8 月
- **THEN** 下方任务列表 MUST 查询该周周一至周日的完整日期范围（含跨月部分）
```
