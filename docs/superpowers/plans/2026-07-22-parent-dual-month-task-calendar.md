---
change: parent-dual-month-task-calendar
design-doc: docs/superpowers/specs/2026-07-22-parent-dual-month-task-calendar-design.md
base-ref: 4d7d7510afc62a10ff69196b904c34dad0be56dc
---

# parent-dual-month-task-calendar 实施计划

> **产物语言**: zh-CN
> **关联文档**:
> - 任务边界：`openspec/changes/parent-dual-month-task-calendar/tasks.md`（9 章 / 16 子任务）
> - 技术设计：`docs/superpowers/specs/2026-07-22-parent-dual-month-task-calendar-design.md`（8 个决策 + 6 章实施要点）
> - OpenSpec 提案：`openspec/changes/parent-dual-month-task-calendar/design.md`（3 capability spec 增量）
> - 验收事实源：`openspec/changes/parent-dual-month-task-calendar/specs/`
> **实施顺序**：后端 API 扩展 → 前端核心组件 → 前端页面集成 → 测试，按 4 个阶段推进
> **测试策略**：后端集成测试 2 条 → 前端组件测试 3 条 → E2E 1 条 → 手动验证 2 项（桌面端 + 移动端）

## 计划概览

本计划将 16 项子任务按 Design Doc 定义的变更层级归并为 **4 个阶段**，每阶段内按依赖关系排序。阶段间存在严格的前置依赖：Phase 1（后端 API）完成后方可进入 Phase 2（前端组件），Phase 2 的核心组件就绪后方可进入 Phase 3（页面集成），Phase 4 全程可并行验证。

**基线约束**：
- 后端基线 = HEAD `4d7d751` 的 `mvn test` = 全部通过
- 前端基线 = `pnpm test` = 全部通过
- tsc 编译必须零错误后方可进入 Phase 3

**整体依赖图**：
```
Phase 1 (后端: Service + Controller)
    │
    ├── Phase 2 (前端核心: TaskCalendar + useReducer)
    │       │
    │       └── Phase 3 (前端集成: Page 重构 + 筛选)
    │               │
    │               └── Phase 4 (测试: 后端集成 + 前端组件 + E2E + 手动)
    │
    └── Phase 4 (后端集成测试可提前)
```

**高层决策引用**（来自 Design Doc，本文不重写）：
- **决策 1**：日历组件 = antd `Calendar` + `dateCellRender` + CSS Grid 双月布局
- **决策 2**：双月布局 = 单组件内两个 Calendar 面板 + `auto-fit minmax(400px, 1fr)` 自适应
- **决策 3**：颜色标记 = 单色优先级方案（LIMITED > REPEAT > STANDING）+ 右上角 Badge 计数
- **决策 4**：周号指示器 = Calendar 外层 Grid 左列 + ISO 8601 周号 + 有任务周着色
- **决策 5**：月头点击 = `headerRender` 自定义 + 点击触发当月查询
- **决策 6**：任务类型筛选 = `taskType` 可选查询参数，逗号分隔多值 `in` 查询
- **决策 7**："查看全部" = 前端移除日期参数，后端无日期时不加过滤条件
- **决策 8**：数据流 = TaskCalendar 自管日历 API 状态 + 父组件 useReducer 管页面级状态

## 阶段 1：后端 API 扩展（3 子任务 → tasks.md §1-3）

**目标**：扩展 `getCalendar` 按日聚合任务类型计数、扩展 `queryAssignments` 支持 `taskType` 筛选并使日期参数可选。

**前置 verify**：
- ⚡ verify `TaskAssignmentController.queryAssignments` 当前签名（已有 9 个可选参数）
- ⚡ verify `TaskAssignmentService.queryAssignments` 当前 LambdaQueryWrapper 日期过滤逻辑（line 434-448）
- ⚡ verify `TaskAssignmentService.getCalendar` 当前聚合结构（line 475-545）
- ⚡ verify `snapshotTemplateTaskType` 值域确认为 `LIMITED | REPEAT | STANDING`

**涉及决策**：决策 6、决策 7

### 任务 1.1：getCalendar 新增 taskTypes 聚合字段

- **原任务编号**：1.1（tasks.md）
- **capability**：task-assignment
- **目标**：修改 `TaskAssignmentService.getCalendar`，在每日统计中新增 `taskTypes` 字段，按 `snapshotTemplateTaskType` 分组计数
- **实现方式**（Design Doc §5.1）：
  - 在遍历 `allAssignments` 的循环中，对每个 `TaskAssignment` 获取 `getSnapshotTemplateTaskType()`
  - 为每日 `dayData` 的 `taskTypes`（`Map<String, Integer>`）执行 `merge(type, 1, Integer::sum)`
  - 初始化每日数据时预先填充三种类型默认值 `{"LIMITED": 0, "REPEAT": 0, "STANDING": 0}`
  - 使用 `LinkedHashMap` 保持类型顺序一致
- **修改文件**：
  - `server/task/src/main/java/com/cutegoals/task/service/TaskAssignmentService.java` — `getCalendar` 方法
- **输出**：`/api/task-assignments/calendar?year=Y&month=M` 响应中每日对象新增 `taskTypes: {LIMITED: n, REPEAT: n, STANDING: n}`
- **验收标准**：
  - 调用 `GET /api/task-assignments/calendar?year=2026&month=7`，每日 `days["YYYY-MM-DD"].taskTypes` 存在且三种类型均有值
  - 无任务日期 `taskTypes` 三个值为 0
  - 有任务日期各类型计数与该日 `snapshotTemplateTaskType` 分布一致
  - 既有字段（`total`, `pending`, `submitted` 等）不受影响
- **依赖任务**：无
- **运行验证**：
  ```bash
  # 启动服务后 curl 验证（需要已登录 token）
  curl -H "Authorization: Bearer $TOKEN" \
    "http://localhost:8080/api/task-assignments/calendar?year=2026&month=7" | jq '.data.days'
  ```

### 任务 1.2：queryAssignments 新增 taskType 筛选参数

- **原任务编号**：2.1 + 2.2（tasks.md）
- **capability**：task-assignment
- **目标**：Controller 和 Service 支持 `taskType` 可选参数，按逗号分隔多值在 `snapshotTemplateTaskType` 上 `in` 查询
- **实现方式**（Design Doc §5.2）：
  - **Controller**：在 `queryAssignments` 方法签名中新增 `@RequestParam(required = false) String taskType`（插入到 `endDate` 和 `cancelled` 之间或末尾），添加至 `params` map
  - **Service**：在 `queryAssignments` 方法的 wrapper 构建段中，当 `taskType` 非空时：
    ```java
    if (params.containsKey("taskType")) {
        String taskType = (String) params.get("taskType");
        if (taskType != null && !taskType.isBlank()) {
            String[] types = taskType.split(",");
            wrapper.in(TaskAssignment::getSnapshotTemplateTaskType, Arrays.asList(types));
        }
    }
    ```
  - 注意去除 `types` 数组元素的空白字符（`trim()`）
- **修改文件**：
  - `server/task/src/main/java/com/cutegoals/task/controller/TaskAssignmentController.java` — `queryAssignments` 方法签名 + params 构建
  - `server/task/src/main/java/com/cutegoals/task/service/TaskAssignmentService.java` — `queryAssignments` 方法 wrapper 构建段
- **输出**：`GET /api/task-assignments?taskType=LIMITED,REPEAT&page=1&pageSize=20` 返回仅匹配类型的分配
- **验收标准**：
  - `?taskType=LIMITED` → 仅返回 `snapshotTemplateTaskType=LIMITED` 的分配
  - `?taskType=LIMITED,REPEAT` → 返回两种类型
  - `?taskType=STANDING` → 仅返回 `STANDING` 分配
  - 不传 `taskType` → 返回全部（向后兼容）
- **依赖任务**：无（与 1.1 无依赖）
- **运行验证**：
  ```bash
  curl -H "Authorization: Bearer $TOKEN" \
    "http://localhost:8080/api/task-assignments?taskType=LIMITED&page=1&pageSize=5" | jq '.data.content[].snapshotTemplateTaskType'
  ```

### 任务 1.3：queryAssignments 日期参数改为完全可选

- **原任务编号**：3.1（tasks.md）
- **capability**：task-assignment / parent-pages-contract
- **目标**：当 `startDate` 或 `endDate` 参数未传入时，不添加日期过滤条件，使"查看全部"模式正常工作
- **实现方式**（Design Doc §5.2、决策 7）：
  - 当前代码 `if (params.containsKey("startDate") && params.containsKey("endDate"))` 已满足要求——仅在两者都传入时才加过滤
  - **如果当前逻辑已经是 `&&`**，则此任务无需修改代码，仅需编写验证测试
  - **验证当前逻辑**：检查 `TaskAssignmentService.java` line 434 前后的条件是否为 `&&`
  - 若当前是 `||` 或其他逻辑，改为 `&&` 两者都存在才过滤
- **修改文件**：
  - `server/task/src/main/java/com/cutegoals/task/service/TaskAssignmentService.java` — 日期过滤条件（如需修改）
- **输出**：`GET /api/task-assignments?taskType=STANDING&page=1&pageSize=20`（无日期参数）正常返回全部 STANDING 分配
- **验收标准**：
  - 不传 startDate/endDate → 不添加日期过滤，返回所有匹配分配
  - 仅传 startDate 不传 endDate → 不添加日期过滤
  - 两者都传 → 正常日期范围过滤（既存行为）
  - 日期范围 > 366 天时仍触发 `MAX_DATE_RANGE_DAYS` 校验
- **依赖任务**：1.2（taskType 参数需要先就位以验证组合查询）
- **运行验证**：
  ```bash
  # 不传日期，仅传 taskType
  curl -H "Authorization: Bearer $TOKEN" \
    "http://localhost:8080/api/task-assignments?taskType=STANDING&page=1&pageSize=20" | jq '.data.totalElements'
  ```

## 阶段 2：前端核心组件（5 子任务 → tasks.md §4-6）

**目标**：创建 `TaskCalendar` 双月日历组件及 `WeekNumberColumn` 子组件，实现颜色标记、周号指示器、日/周/月三级点击交互。建立基于 `useReducer` 的页面级状态管理。

**前置 verify**：
- ⚡ verify antd `Calendar` 组件已在项目中可用（`import { Calendar } from 'antd'`）
- ⚡ verify `dayjs` 已在项目依赖中（`package.json`）
- ⚡ verify `useApi` hook 签名与使用模式（`web/src/shared/hooks/useApi.ts`）
- ⚡ verify `TaskTypeFilter` 组件接口（`web/src/parent/components/TaskTypeFilter.tsx`）
- ⚡ verify `Badge` 组件已导入（antd 依赖）

**涉及决策**：决策 1、决策 2、决策 3、决策 4、决策 5、决策 8

### 任务 2.1：创建 TaskCalendar 双月布局结构

- **原任务编号**：4.1（tasks.md）
- **capability**：parent-task-calendar
- **目标**：新建 `web/src/parent/components/TaskCalendar.tsx`，实现双月 Calendar 面板 + CSS Grid 自适应布局
- **实现方式**（Design Doc §2.1、决策 1、决策 2）：
  - 组件 Props：`baseMonth: string`（`YYYY-MM`）、`selectedRange: CalendarSelection | null`、`onSelect: (action: CalendarAction) => void`、`onNavigate: (dir: -1 | 1) => void`
  - 内部用 `useApi<CalendarData>` 请求当月和下月日历数据（两个独立 hook 调用）
  - 外容器：`display: grid; grid-template-columns: repeat(auto-fit, minmax(400px, 1fr)); gap: 16px`
  - 每个日历面板：`display: grid; grid-template-columns: 48px 1fr`（周号列 + Calendar）
  - 左右导航按钮放在组件顶部（`<` 上月 | 当月显示 | 下月 `>`）
- **关键类型定义**：
  ```typescript
  type CalendarData = { year: number; month: number; days: Record<string, DayData> };
  type DayData = { total: number; taskTypes: { LIMITED: number; REPEAT: number; STANDING: number }; /* ...其他状态字段 */ };
  type CalendarSelection = { type: 'day' | 'week' | 'month'; startDate: string; endDate: string };
  type CalendarAction = { type: 'SELECT_DATE'; date: string } | { type: 'SELECT_WEEK'; startDate: string } | { type: 'SELECT_MONTH'; year: number; month: number };
  ```
- **修改/新建文件**：
  - `web/src/parent/components/TaskCalendar.tsx` — 新建
  - `web/src/parent/components/TaskCalendar.module.css` — 新建（可选，视 inline style 决策而定）
- **输出**：双月日历组件，≥768px 并排，<768px 上下堆叠
- **验收标准**：
  - 组件渲染两个 Calendar 面板（当月 + 下月），默认显示当前月
  - 桌面端（≥768px）左右并排，移动端上下堆叠
  - 左右导航按钮切换月份，两个面板同步滚动
- **依赖任务**：1.1（日历 API 需返回 taskTypes 才能显示颜色；但在 taskTypes 就位前可先用骨架渲染验证布局）
- **运行验证**：
  ```bash
  cd web && pnpm dev
  # 浏览器访问家长任务页面，确认双月日历渲染
  ```

### 任务 2.2：实现 dateCellRender 颜色标记

- **原任务编号**：4.3（tasks.md）
- **capability**：parent-task-calendar
- **目标**：实现 `dateCellRender` 函数，按任务类型优先级着色日期单元格，右上角显示任务总数 Badge
- **实现方式**（Design Doc §2.3、决策 3）：
  - 优先级：LIMITED → `var(--ant-color-error-bg)`（红底）、REPEAT → `var(--ant-color-info-bg)`（蓝底）、STANDING → `var(--ant-color-success-bg)`（绿底）
  - 无任务日期只显示日期数字，白色背景
  - 有任务日期右上角使用 antd `<Badge count={total} size="small" offset={[-2, 2]}>` 包裹日期数字
  - 选中状态：`selectedRange` 范围内的日期添加 `cell-selected` CSS class（`box-shadow: inset 0 0 0 2px var(--ant-color-primary)`）
  - 使用 antd ConfigProvider token 而非硬编码颜色，保持主题一致性
- **修改文件**：
  - `web/src/parent/components/TaskCalendar.tsx` — 实现 `dateCellRender` 函数
- **输出**：有任务的日期格子显示对应颜色 + 右上角数字，选中日期有蓝色内边框高亮
- **验收标准**：
  - 同一天同时有 LIMITED + STANDING 任务时显示红色（LIMITED 优先级最高）
  - 同一天只有 REPEAT + STANDING 时显示蓝色
  - 无任务日期白色背景，有任务日期右上角数字正确
  - 点击日期后该格子显示选中态
- **依赖任务**：2.1（组件结构就位后可接入渲染逻辑）
- **运行验证**：
  ```bash
  # 启动后观察日历颜色标记与后端 taskTypes 数据一致
  ```

### 任务 2.3：实现 WeekNumberColumn 周号指示器

- **原任务编号**：5.1（tasks.md）
- **capability**：parent-task-calendar
- **目标**：在日历面板左侧添加 ISO 周号列，与 Calendar 行高对齐，当周有任务时周号着色
- **实现方式**（Design Doc §2.2、决策 4）：
  - `computeWeekNumbers(year, month)`: 从该月第一天开始，对齐到周日（`startOf('week')`），依次计算 6 行周号
  - 使用 `dayjs(date).isoWeek()` 获取 ISO 8601 周号
  - 行高对齐方案：优先使用 CSS `--ant-calendar-cell-height` 变量；偏差 > 2px 时使用 `ResizeObserver` 动态同步
  - 周号着色逻辑：遍历该周 7 天，若任一 `calendarData.days[dateStr].total > 0` 则着色并标记可点击
  - 周号点击 → 调用 `onSelect({ type: 'SELECT_WEEK', startDate })`，其中 `startDate` 为该周周一
- **修改文件**：
  - `web/src/parent/components/TaskCalendar.tsx` — 新增 `WeekNumberColumn` 子组件或内联渲染逻辑
- **输出**：左侧周号列与 Calendar 每行精确对齐，有任务周着色
- **验收标准**：
  - 2 月（只有 5 行实际日期）也渲染 6 行周号，与 Calendar 行数一致
  - 某月第 1 周可能只有 1-2 天，周号仍正确显示
  - 有任务周周号着色，无任务周灰色/白色
  - 点击周号触发 `SELECT_WEEK` action
- **依赖任务**：2.1（组件结构）、2.2（日历数据就位后判断周任务存在性）
- **运行验证**：
  ```bash
  # 主要依赖目视检查；也可在 Jest 中测试 computeWeekNumbers 返回值
  ```

### 任务 2.4：实现月头点击与三级交互分发

- **原任务编号**：4.2 + 5.2 + 5.3 + 5.4 + 5.5（tasks.md）
- **capability**：parent-task-calendar
- **目标**：自定义 Calendar `headerRender`，实现月头点击事件；日期单元格、周号、月头三个入口统一通过 `onSelect` 回调分发选中逻辑
- **实现方式**（Design Doc §2.1、§3.2、决策 5）：
  - **月头 headerRender**：自定义月头显示 `YYYY年M月`，添加 `onClick` 触发 `onSelect({ type: 'SELECT_MONTH', year, month })`
  - **日期单元格点击**：在 `dateCellRender` 返回的元素上绑定 `onClick`，触发 `onSelect({ type: 'SELECT_DATE', date: date.format('YYYY-MM-DD') })`
  - **周号点击**：在 `WeekNumberColumn` 的周号单元格上绑定 `onClick`，触发 `onSelect({ type: 'SELECT_WEEK', startDate })`
  - **选中高亮**：`selectedRange` 范围内所有日期格子添加 `cell-selected` class；月头选中态（如文字加粗/颜色变化）；周号选中态
  - 互斥：最新点击覆盖之前选中（由父组件 useReducer 处理）
- **修改文件**：
  - `web/src/parent/components/TaskCalendar.tsx` — 添加 headerRender、各点击处理器
- **输出**：点击日期 → 选中该日；点击周号 → 选中整周；点击月头 → 选中全月
- **验收标准**：
  - 点击某日 → 该日格子高亮，其他取消高亮
  - 点击周号 → 该周 7 天全部高亮，日/月高亮清除
  - 点击月头 → 该月全部日期高亮，日/周高亮清除
  - 重复点击同一入口 → 保持选中（不 toggle off）
  - 跨月周号点击 → 回调传递的日期范围包含跨月日期（基于周一起始）
- **依赖任务**：2.1、2.2、2.3
- **运行验证**：
  ```bash
  # 浏览器中逐级点击验证交互
  ```

### 任务 2.5：日历数据获取（useApi × 2）

- **原任务编号**：6.1 + 6.2（tasks.md）
- **capability**：parent-task-calendar
- **目标**：TaskCalendar 组件内部使用两个 `useApi` hook 分别请求当月和下月日历数据，将数据传递给渲染函数
- **实现方式**（Design Doc §2.1、§8）：
  - 从 `baseMonth` 解析出 `year` 和 `month`（当月）
  - 计算下月：先生成 `dayjs(baseMonth + '-01')`，再加 1 月
  - 两个 `useApi<CalendarData>` 分别请求：
    - `useApi(\`/task-assignments/calendar?year=${year}&month=${month}\`)`
    - `useApi(\`/task-assignments/calendar?year=${nextYear}&month=${nextMonth}\`)`
  - 失败处理：单面板失败时显示 `<Alert type="error" message="加载失败" action={<Button onClick={refetch}>重试</Button>} />`；另一面板不受影响
  - 加载态：面板内显示 `<Spin />`
  - 空数据：所有日期无任务时日历正常白色渲染，无 Badge
- **修改文件**：
  - `web/src/parent/components/TaskCalendar.tsx` — 添加两个 useApi 调用
- **输出**：双月日历正确加载并显示任务颜色标记
- **验收标准**：
  - 两个面板各自独立加载，失败互不影响
  - 某月无任何任务 → 日历正常显示白色格子
  - 导航切换月份时 `baseMonth` 变化，useApi 自动重新请求
- **依赖任务**：1.1（API 就位）、2.1（组件结构）
- **运行验证**：
  ```bash
  # 启动服务后访问页面，确认两个面板独立加载
  ```

## 阶段 3：前端页面集成（4 子任务 → tasks.md §7-8）

**目标**：在 `ParentTasksPage` 中引入 `useReducer` 集中状态管理，替换旧 `<input type="date">` 控件，集成 `TaskTypeFilter` 和"查看全部"按钮，建立日历到任务列表的完整数据流。

**前置 verify**：
- ⚡ verify `ParentTasksPage` 当前状态：`date` state（line 980）、`useApi` 调用（line 981-982）、`<Input type="date">` 控件（line 1201）
- ⚡ verify `TaskTypeFilter` 组件未在任务页中使用
- ⚡ verify Phase 2 组件已通过 tsc 编译

**涉及决策**：决策 6、决策 7、决策 8

### 任务 3.1：实现 useReducer 页面级状态管理

- **原任务编号**：跨 tasks.md §7-8 的状态重构
- **capability**：parent-task-calendar / parent-pages-contract
- **目标**：在 `ParentTasksPage` 中用 `useReducer` 替换分散的 `useState`，实现 `CalendarPageState` 集中状态
- **实现方式**（Design Doc §3.1、§3.2）：
  - **State 定义**：
    ```typescript
    interface CalendarPageState {
      baseMonth: string;                    // 'YYYY-MM'
      selectedRange: CalendarSelection | null;
      taskTypeFilters: TaskTypeValue[];     // 默认 ['LIMITED', 'REPEAT', 'STANDING']
      viewAllMode: boolean;
    }
    ```
  - **Reducer actions**：`SELECT_DATE`、`SELECT_WEEK`、`SELECT_MONTH`、`SET_FILTERS`、`VIEW_ALL`、`NAV_MONTH`
  - **查询参数派生函数** `buildQueryParams(state, page)`：
    - `taskTypeFilters` 全选（3 个）时不传 `taskType` 参数（等价于不传）
    - `taskTypeFilters` 为空时传空字符串（后端返回空列表，行为明确）
    - `viewAllMode = true` 时不传 `startDate`/`endDate`
  - 保留既有的 `useFormField` 和分配弹窗状态（不在 CalendarPageState 范围内）
- **修改文件**：
  - `web/src/parent/pages/index.tsx` — 新增 reducer + 替换相关 useState
- **输出**：`ParentTasksPage` 的状态由 `useReducer` 统一管理，派生查询参数用于任务列表 API
- **验收标准**：
  - `SELECT_DATE` → selectedRange 变为 day 类型
  - `SELECT_MONTH` → selectedRange 变为 month 类型
  - `VIEW_ALL` → viewAllMode = true, selectedRange = null
  - `NAV_MONTH(-1)` → baseMonth 减 1 月
  - `SET_FILTERS(['LIMITED'])` → taskTypeFilters 更新
- **依赖任务**：Phase 2（TaskCalendar 组件就位后，reducer action 类型与其对接）
- **运行验证**：Jest 单元测试验证 reducer 纯函数转换

### 任务 3.2：替换旧日期控件为 TaskCalendar

- **原任务编号**：8.1 + 8.2（tasks.md）
- **capability**：parent-task-calendar / parent-pages-contract
- **目标**：移除旧 Card 中的 `<Input type="date">` 和 `date` state，替换为 `TaskCalendar` 组件
- **实现方式**（Design Doc §2.4、§8）：
  - 移除 line 980 `const [date, setDate] = useState(...)`
  - 移除 line 1198-1203 的旧 `<Card title="日历">` 区块中的 `<Input type="date">`
  - 替换为 `<TaskCalendar baseMonth={state.baseMonth} selectedRange={state.selectedRange} onSelect={(action) => dispatch(action)} onNavigate={(dir) => dispatch({ type: 'NAV_MONTH', payload: dir })} />`
  - 修改任务列表 `useApi` 的 URL 构建：从 `date` 参数改为基于 `state.selectedRange` 和 `state.viewAllMode` 的动态构建
- **修改文件**：
  - `web/src/parent/pages/index.tsx` — 移除旧控件，接入 TaskCalendar + 修改查询逻辑
- **输出**：页面顶部显示双月日历，选择日期后下方任务列表自动刷新
- **验收标准**：
  - 旧 `<Input type="date">` 控件已移除，页面无相关引用
  - 日历的日期选择正确触发任务列表 API 调用（含正确的 startDate/endDate）
  - 月导航按钮切换月份后日历刷新，任务列表不自动刷新（等待用户新选择）
  - 日历颜色标记始终显示所有任务类型，不受下方筛选器影响
- **依赖任务**：2.1-2.5（TaskCalendar 组件）、3.1（useReducer 就位）
- **运行验证**：
  ```bash
  cd web && pnpm dev
  # 查看任务分配页面，验证日历替换生效
  ```

### 任务 3.3：集成 TaskTypeFilter 与"查看全部"按钮

- **原任务编号**：7.1 + 7.2 + 7.3 + 7.4（tasks.md）
- **capability**：parent-task-calendar / parent-pages-contract
- **目标**：在日历下方放置 `TaskTypeFilter` 组件和"查看全部"按钮，完成筛选交互
- **实现方式**（Design Doc §2.4、§3.2）：
  - 在 `TaskCalendar` 下方（任务列表上方）添加：
    ```tsx
    <Space direction="vertical" style={{ width: '100%' }}>
      <TaskTypeFilter 
        selected={state.taskTypeFilters}
        onChange={(types) => dispatch({ type: 'SET_FILTERS', payload: types })}
      />
      <Button onClick={() => dispatch({ type: 'VIEW_ALL' })}>
        {state.viewAllMode ? '查看全部（已激活）' : '查看全部'}
      </Button>
    </Space>
    ```
  - 筛选变更 → `SET_FILTERS` action → 300ms debounce 触发任务列表 API 查询
  - "查看全部"模式 → 日历选中高亮清除，日期范围参数移除
  - 日历颜色标记始终显示所有任务类型（不受筛选器影响，因为日历 API 请求不传 taskType）
  - debounce 实现：使用 `useMemo` + `setTimeout` 或 `useRef` 管理 timer
- **修改文件**：
  - `web/src/parent/pages/index.tsx` — 添加筛选器 + 按钮 + debounce hook
- **输出**：筛选器改变 → 任务列表刷新（保持当前日期范围）；查看全部 → 显示全部任务（保留类型筛选）
- **验收标准**：
  - 全选 3 种类型 → API 请求不传 `taskType` 参数
  - 仅选「限时任务」+ 当前日期范围 → API 带 `taskType=LIMITED` + 日期参数
  - 点击"查看全部" → API 不传日期参数，保留 taskType
  - "查看全部"模式下日历选中高亮清除
  - 连续快速切换筛选 → 仅最后一次触发 API（300ms debounce）
- **依赖任务**：3.1（useReducer）、3.2（日历就位后布局确认）+ 同时依赖 1.2（taskType API）
- **运行验证**：
  ```bash
  # 浏览器中切换筛选类型、点击查看全部，观察 Network 面板和列表变化
  ```

### 任务 3.4：任务列表 useApi 动态查询参数改造

- **原任务编号**：8.3 + 8.4（tasks.md）
- **capability**：parent-task-calendar / parent-pages-contract
- **目标**：改造任务列表的 `useApi` 调用，使其 URL 基于 `state` 动态构建，支持 `startDate`/`endDate`/`taskType`/`page` 组合
- **实现方式**（Design Doc §3.3）：
  - 使用 `useMemo` 基于 `state.selectedRange`、`state.viewAllMode`、`state.taskTypeFilters` 派生查询参数字符串
  - `buildQueryParams` 逻辑：
    - 始终带 `page=1&pageSize=20`
    - `taskTypeFilters` 非全选时附加 `taskType=X,Y`
    - 非 `viewAllMode` 且有 `selectedRange` 时附加 `startDate=X&endDate=X`
  - `useApi` 的 path 参数使用派生结果
  - 保留 `refetch` 供分配操作后手动刷新
- **修改文件**：
  - `web/src/parent/pages/index.tsx` — 修改任务列表 useApi 调用
- **输出**：任务列表参数完全由 `CalendarPageState` 派生，单一数据源
- **验收标准**：
  - 日历选择某日 → URL 带 `startDate=2026-07-15&endDate=2026-07-15`
  - 选择某周 → URL 带 `startDate=周一&endDate=周日`
  - 选择某月 → URL 带 `startDate=月初&endDate=月末`
  - 查看全部 → URL 不带日期参数
  - 任务分配操作后 `refetch` 使用当前筛选参数重新加载
- **依赖任务**：3.1、3.2、3.3
- **运行验证**：
  ```bash
  # DevTools Network 面板观察 API 请求参数变化
  ```

## 阶段 4：测试与验证（4 子任务 → tasks.md §9 + Design Doc §6）

**目标**：通过后端集成测试、前端组件测试、E2E 测试和手动验证，确保 change 完整正确。

**前置 verify**：
- ⚡ verify 后端现有测试 `TaskAssignmentServiceTest` 中 getCalendar / queryAssignments 的测试模式
- ⚡ verify 前端测试框架配置（Jest + Testing Library）
- ⚡ verify E2E 测试是否已在项目中建立（如 Playwright/Cypress）

**涉及决策**：所有 8 个决策在本阶段得到验证

### 任务 4.1：后端集成测试（2 条）

- **原任务编号**：跨 tasks.md §1-3 验证条目
- **capability**：task-assignment
- **目标**：编写集成测试验证 getCalendar taskTypes 字段和 queryAssignments taskType 筛选
- **实现方式**（Design Doc §6）：
  - **测试 1** (`shouldReturnTaskTypesInCalendar`): 创建含不同 taskType 的 TaskAssignment → 调用 `getCalendar` → 断言 `days[dateStr].taskTypes` 存在且值正确
  - **测试 2** (`shouldFilterByTaskType`): 创建多种类型分配 → 调用 `queryAssignments` 带 `taskType=LIMITED` → 断言仅返回 LIMITED 类型 → 带 `taskType=LIMITED,REPEAT` → 断言返回两种类型
- **修改/新建文件**：
  - `server/task/src/test/java/com/cutegoals/task/service/TaskAssignmentServiceTest.java` — 新增测试方法
- **输出**：2 条集成测试通过
- **验收标准**：
  - `mvn test -pl :task -am -Dtest="TaskAssignmentServiceTest"` 全部通过
  - 新测试覆盖 getCalendar taskTypes 值正确性 + queryAssignments taskType 过滤正确性
- **依赖任务**：1.1、1.2、1.3（被测代码就位）
- **运行验证**：
  ```bash
  cd server && mvn test -pl :task -am -Dtest="TaskAssignmentServiceTest"
  ```

### 任务 4.2：前端组件测试（3 条）

- **原任务编号**：跨 tasks.md §5-6 验证条目
- **capability**：parent-task-calendar
- **目标**：编写组件测试覆盖 TaskCalendar 双月渲染、dateCellRender 颜色逻辑、useReducer 状态转换
- **实现方式**（Design Doc §6）：
  - **测试 1** (`rendersTwoCalendarPanels`): 快照测试 TaskCalendar 渲染两个 Calendar 面板 + 颜色标记验证（mock useApi 返回含 taskTypes 的数据）
  - **测试 2** (`dateCellRenderColorPriority`): 模拟不同 taskTypes 组合（`{LIMITED:1, STANDING:3}`、`{REPEAT:2}`、空数据），验证优先级色正确
  - **测试 3** (`reducerStateTransitions`): 测试 6 个 action 的状态转换正确性
- **修改/新建文件**：
  - `web/src/parent/components/TaskCalendar.test.tsx` — 新建
  - `web/src/parent/pages/ParentTasksPage.test.tsx` 或内联测试 — 新建 reducer 测试
- **输出**：3 条组件测试通过
- **验收标准**：
  - 快照测试可稳定运行（使用 mock 数据，不依赖后端）
  - 颜色优先级测试覆盖 3 种日期的优先级判断
  - reducer 测试覆盖全部 action type
- **依赖任务**：2.1-2.5（被测组件就位）、3.1（reducer 就位）
- **运行验证**：
  ```bash
  cd web && pnpm test -- --testPathPattern="TaskCalendar|ParentTasksPage"
  ```

### 任务 4.3：端到端验收测试（1 条完整流程）

- **原任务编号**：9.1（tasks.md）
- **capability**：parent-task-calendar
- **目标**：编写 1 条 E2E 测试覆盖从日历打开到完整交互的全流程
- **实现方式**（Design Doc §6）：
  - 流程：加载页面 → 双月日历显示 → 点击有色日期 → 确认任务列表正确 → 切换筛选类型 → 列表刷新 → 点击查看全部 → 显示全部匹配结果 → 点击日历日期恢复日期模式
- **输出**：1 条 E2E 测试通过
- **验收标准**：
  - 完整流程无报错，每步断言通过
  - 覆盖跨月周号的边界场景（第 30/31 周跨月点击）
- **依赖任务**：Phase 1-3 全部完成
- **运行验证**：
  ```bash
  cd web && pnpm test:e2e -- --spec="parent-task-calendar"
  ```

### 任务 4.4：手动验证响应式布局与边界场景

- **原任务编号**：9.2（tasks.md）+ Design Doc §4 边界条件
- **capability**：parent-task-calendar
- **目标**：手动验证移动端布局、跨月周号、2 月短月、API 失败等边界场景
- **验证清单**：
  - 移动端 (<768px)：日历上下堆叠，交互正常
  - 桌面端 (≥768px)：双月并排，周号对齐
  - 跨月周号：点击 7 月最后一周（含 8 月初几天）→ 任务列表查询完整周一至周日范围
  - 2 月场景：28 天 + 6 行日历 → 最后一行含 3 月初日期 → 周号列正确渲染 6 行
  - API 失败：断网/后端报错 → 失败面板显示错误提示 + 重试按钮 → 另一面板正常
  - 所有日期无任务：日历白色渲染，无 Badge
  - 3 月为空但 4 月有任务：3 月面板白色，4 月面板有色
  - 图标边界：Badge 数字 > 99 时显示"99+"
- **依赖任务**：Phase 1-3 全部完成
- **运行验证**：浏览器手动操作 + Chrome DevTools 模拟移动端

## 风险与注意事项

| 风险 | 缓解措施 |
|------|---------|
| 周号列行高与 Calendar 不对齐 | 优先使用 CSS `--ant-calendar-cell-height` 变量；偏差 > 2px 时用 `ResizeObserver` 动态同步 |
| `useApi` 的 path 参数变化导致不必要的重复请求 | 使用 `useMemo` 控制 path 引用变化频率，仅在 `state` 关键字段变化时更新 |
| taskType 为空字符串时后端行为不确定 | 前端约定：taskTypeFilters 全选（3 个）时不传 taskType；空选时传空字符串，后端按空处理 |
| antd Calendar 版本不兼容自定义 headerRender | 已在项目中确认 antd 版本支持 `headerRender` prop（5.x+），当前项目使用 antd 5.x |

## 汇总清单

| 阶段 | 任务数 | 新建文件 | 修改文件 | 关键交付物 |
|------|--------|---------|---------|-----------|
| Phase 1: 后端 API | 3 | 0 | 2 | getCalendar 新增 taskTypes、queryAssignments 新增 taskType + 日期可选 |
| Phase 2: 前端核心 | 5 | 1 | 0 | TaskCalendar.tsx（含 WeekNumberColumn、dateCellRender、headerRender、useApi×2） |
| Phase 3: 前端集成 | 4 | 0 | 1 | ParentTasksPage 重构（useReducer + TaskCalendar + TaskTypeFilter + 查看全部） |
| Phase 4: 测试 | 4 | 2 | 1 | 后端集成测试 2 条 + 前端测试 3 条 + E2E 1 条 + 手动验证清单 |
| **合计** | **16** | **3** | **4** | |

## 实施检查清单

- [x] Phase 1: 后端 API 扩展完成，`mvn test` 通过
- [x] Phase 2: TaskCalendar 组件创建完成，`pnpm test` 通过，`tsc` 零错误
- [x] Phase 3: ParentTasksPage 集成完成，旧 date input 已移除，全部交互可工作
- [x] Phase 4: 后端 2 条 + 前端 3 条测试通过
- [x] E2E 测试通过
- [x] 手动验证：桌面端并排 + 移动端堆叠 + 跨月周号 + 2 月短月 + API 失败
- [x] `pnpm build` 生产构建成功
