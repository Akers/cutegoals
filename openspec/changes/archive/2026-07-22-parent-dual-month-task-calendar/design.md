# 家长端双月任务日历 — 技术设计

## Context

### 当前状态
- 家长任务分配页 (`ParentTasksPage`) 使用 `<input type="date">` 实现简单日期选择
- 前端通过 `GET /api/task-assignments?startDate=X&endDate=X` 按单天范围查询任务列表
- 后端 `GET /api/task-assignments/calendar` 端点已存在，返回按月聚合的任务计数（按状态分类），但前端未使用
- `TaskTypeFilter` 组件已存在（复选框多选：限时/重复/常驻），但未集成到任务分配页
- 项目已引入 Ant Design、dayjs，UI 基于 React 18 + TypeScript + Vite

### 约束
- 每家一个实例，无多家庭边界问题
- 前端不使用第三方日历库（基于 antd Calendar）
- 后端数据库使用 MyBatis-Plus LambdaQueryWrapper
- `TaskAssignment.snapshotTemplateTaskType` 存储任务类型，值域 `LIMITED | REPEAT | STANDING`

### 利益相关者
- 家长用户：需要高效查看和筛选任务分配
- 前端开发：统一组件风格（antd）

## Goals / Non-Goals

**Goals:**
- 以双月日历替代日期选择器，直观展示任务分布
- 按任务类型颜色标记日历日期
- 日/周/月三级点击粒度，联动下方任务列表
- 任务类型筛选与"查看全部"模式

**Non-Goals:**
- 不改变 task-template 和 task-assignment 的创建/编辑/删除逻辑
- 不修改亲子端功能
- 不引入新的第三方日历库（如 FullCalendar）
- 不处理跨年/跨月的"查看全部"分页策略（使用现有分页机制）

## Decisions

### 1. 日历组件：antd Calendar 自渲染 dateCell + 包装双月布局

**选择**：基于 antd `Calendar` 组件的 `dateCellRender` 自定义渲染日期单元格，外部用 CSS Grid/Flex 包装两个月面板形成双月布局。

**备选方案**：
- FullCalendar（React）：功能丰富但引入新依赖、样式难以统一 → 不选
- dayjs 手写日历：灵活但工作量大、无内置无障碍支持 → 不选
- React Calendar 库：与 antd 风格不一致 → 不选

**理由**：项目已使用 antd，组件风格一致，`dateCellRender` 可完全自定义内容（颜色标记、任务计数），外层用 Grid 响应式布局实现桌面并排/移动端堆叠。

### 2. 双月布局：单组件 + 两面板 + CSS 媒体查询

**选择**：一个 `TaskCalendar` 组件内渲染两个 `Calendar` 面板（当月和下月），使用 CSS Grid `grid-template-columns: repeat(auto-fit, minmax(400px, 1fr))` 自适应。

**备选方案**：
- 两个独立 Calendar 组件分开放置：代码冗余，状态不统一 → 不选
- CSS `@container` queries：兼容性不足 → 不选

**理由**：媒体查询在本项目目标浏览器中兼容性好，400px 断点确保日历面板有足够宽度显示完整周行。

### 3. 颜色标记方案：单元格背景色 + 类型多色 + 透明度区分

**选择**：三种任务类型各一个基础色（如 `#FFE0E0` 红 / `#E0E8FF` 蓝 / `#E0FFE0` 绿 或使用 antd token），该日期有多个类型时采用优先级色（LIMITED > REPEAT > STANDING）并显示计数徽章。

**备选方案**：
- 单一圆点标记：无法区分类型 → 不选
- 彩色圆点叠加：单元格内空间拥挤 → 不选
- 渐变色/条纹：过度复杂，视觉负担大 → 不选

**理由**：单色优先级方案简洁直观，配合单元格右上角的计数徽章（如「3」表示3个任务），家长能快速识别"有任务"和"哪个类型占主导"。

### 4. 周号指示器：Calendar 外层自定义列

**选择**：在 Calendar 左侧添加一列显示 ISO 周号，通过 week 行高亮（该周有任务时周号单元格着色）。

**备选方案**：
- antd Calendar `fullscreen` 模式自身不支持直接添加列 → 需用自定义逻辑
- 使用 CSS `::before` 伪元素：不够灵活，无法点击 → 不选

**理由**：在 Calendar 外层用 Grid 布局包装，左列放周号（基于 dayjs 计算），与 Calendar 的周行对齐。

**实现细节**：
- `dayjs(date).isoWeek()` 获取 ISO 8601 周号
- 该周内任一天有任务 → 周号单元格着色并可点击

### 5. 月头点击：Calendar 的 `headerRender` 自定义

**选择**：使用 antd Calendar 的 `headerRender` prop 自定义月头内容，添加点击事件触发当月任务列表查询。

**理由**：antd Calendar 原生支持 `headerRender`，无需 hack DOM。

### 6. 任务类型筛选 API：新增 `taskType` 查询参数

**选择**：`GET /api/task-assignments` 新增可选参数 `taskType`（逗号分隔，如 `LIMITED,REPEAT`），后端基于 `snapshotTemplateTaskType` 字段用 `in` 查询过滤。`GET /api/task-assignments/calendar` 响应新增 `taskTypes` 字段，按日聚合各类型计数。

**备选方案**：
- 新增独立端点：API 膨胀 → 不选
- 前端本地过滤：数据量大时不可行 → 不选

**理由**：复用现有端点，参数扩展最小化。`taskType` 作为可选参数不影响现有调用方。

### 7. "查看全部"按钮：前端移除日期参数

**选择**：点击"查看全部"时，前端发起不携带 `startDate`/`endDate` 的 `GET /api/task-assignments` 请求（仍带 `taskType` 和分页参数），后端在无日期参数时不加日期过滤条件。

**理由**：当前 `queryAssignments` 已经按条件动态构建 wrapper，添加 `taskType` 后天然支持此行为——传参则过滤，不传则不过滤。

### 8. 数据流

```
┌──────────────────────────────────────────┐
│           TaskCalendar 组件               │
│                                          │
│  useApi('/task-assignments/calendar',    │
│    { year, month }) × 2                  │
│         │                                │
│         ▼                                │
│  calendarData = { days: {                │
│    "2026-07-01": {                       │
│      total: 5,                          │
│      taskTypes: { LIMITED: 3,           │
│        REPEAT: 1, STANDING: 1 }         │
│    }, ... }}                             │
│         │                                │
│         ▼                                │
│  dateCellRender: 颜色 + 计数徽章         │
│  weekNumber: 周号 + 颜色指示器            │
│  headerRender: 月头 + 点击事件            │
│         │                                │
│  点击日/周/月 ──→ setDateRange()         │
│         │                                │
│         ▼                                │
│  下方 TaskList 组件                       │
│  useApi('/task-assignments', {           │
│    startDate, endDate, taskType,         │
│    page, pageSize })                     │
└──────────────────────────────────────────┘
```

## Risks / Trade-offs

| 风险 | 缓解措施 |
|------|---------|
| antd Calendar 双月布局在窄屏下可能显示不全 | 设置 400px min-width 断点，移动端堆叠 |
| 周号对齐：Calendar 某些月份第1周可能只有1-2天，导致行高不一致 | 统一使用 7 行固定高度布局 |
| 日历月数据量大（如该月有几百个分配）时查询慢 | 后端按月聚合已有索引支持，前端分页显示任务列表 |
| `snapshotTemplateTaskType` 无独立索引 | 全表扫描风险低（单家庭、按月过滤后数据有限），必要时用 `EXPLAIN` 验证 |

## Migration Plan

1. **Deploy**：前后端同时部署（非 BREAKING：日历端点扩展字段向后兼容，列表端点新增可选参数）
2. **Rollback**：直接回滚到上一版本；现有 `<input type="date">` 被移除，但用户可通过浏览器后退
3. **Data**：无数据迁移，仅 API 响应结构扩展

## Open Questions

- 是否需要在日历上同时显示所有孩子的任务（当前 `/calendar` 端点支持 `childId` 可选参数）？默认显示全部家庭孩子
- 颜色方案是否需要与 UI 设计师协商确定具体色值？当前使用建议色值，可在实现阶段调整
- 周号是否需要 ISO 8601 标准（跨年周）？目前使用 `dayjs().isoWeek()`，支持 ISO 标准
