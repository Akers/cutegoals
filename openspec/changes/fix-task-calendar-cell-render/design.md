# Design: fix-task-calendar-cell-render

## 修复方案（单一方案，hotfix 模式）

### 方案概述

不动 `computeWeekNumbers` 算法（已正确返回 6 行），只改 `TaskCalendar.tsx` 的渲染层与 `WeekNumberColumn` 的布局对齐：

1. **消除重复数字**：从 `renderDateCell` 中删除 `<div>{date.date()}</div>`，让 antd `<Calendar>` 自己渲染唯一的天数数字。自定义渲染只保留任务背景层与 Badge。
2. **对齐周号列**：在 `WeekNumberColumn` 顶部增加两个占位行，分别与 `CalendarHeader`（月份标题）和 antd `<Calendar>` 内部星期表头等高；同时把六个周号行的行高改成与 antd Calendar 内部日期行同源（通过测量或共享基准），保证逐行对齐。

### 关键决策

#### 决策 1：保留 `dateCellRender` 还是迁移到 antd 5 `cellRender`

antd 5 `<Calendar>` 提供 `cellRender(date, info)` 新 API（`info.type` 区分 `'date'`/`'month'`），语义同样是「向 cell 追加内容」。两个 API 都不会替换 antd 默认的 cell 数字。

**选择**：保留 `dateCellRender`，仅删除 `<div>{date.date()}</div>`。
**理由**：最小改动，避免引入 API 迁移风险；`dateCellRender` 在 antd 5 仍受支持（deprecation 警告非阻塞）。

#### 决策 2：周号列对齐策略

候选方案：

- A. 用 `ResizeObserver` 测量真实 Calendar 行高，动态同步周号列——复杂、易碎、性能开销。
- B. 把周号列与日历改为共享 CSS Grid 布局，让 antd Calendar 内部行结构被外层 grid 控制——需要深入 antd 内部 className，脆弱。
- C. 在周号列顶部增加与 `CalendarHeader` + 星期表头等高的占位 div，并把六个周号行的 `minHeight` 调整到与 antd 月面板默认行高接近（约 36px → 与 antd 默认 100/6 ≈ 不到 36，需测试）。

**选择**：方案 C。
**理由**：

- 不依赖 antd 内部 CSS 选择器，长期稳定。
- 占位高度可通过固定的 `CalendarHeader` 渲染输出（已知 `fontSize`、`padding`）和 antd 星期表头默认 token（`lineHeight`、`paddingY`）推算，或用一个常量 + 容差 + `alignItems: 'stretch'` 兜底。
- 若 antd Calendar 行高随容器宽度等比缩放导致轻微误差，仍可通过让周号列与日历共享 `height: 100%`、六个 row 用 `flex: 1` 等分来兜底，避免精确像素匹配的脆弱性。

具体实现细节（build 阶段确定）：

- `WeekNumberColumn` 顶部加 `paddingTop` 或独立的占位 div，高度等于 `CalendarHeader` 实际渲染高度。
- `WeekNumberColumn` 第二个占位（或 `margin-top`）等于 antd Calendar 星期表头高度（约 36~40px，可通过 `<Calendar>` 容器 `:first-child` 推算）。
- 六个周号行改用 `flex: 1` 等分剩余高度，配合外层容器 `height` 与日历同步。

### 不引入

- 不引入 `dayjs` 新插件（与之前 hotfix 解耦）。
- 不引入新的 antd 组件。
- 不修改 `selectedRange` / `onSelect` 协议。
- 不动 `computeWeekNumbers` 算法。

### 回归测试策略

`TaskCalendar.test.tsx` 当前用 `vi.mock('antd', ...)` 完全绕过真实组件。本次修复：

- 调整 mock：让 mock Calendar 把 `dateCellRender` 的返回值作为 cell 内容的「附加部分」，并在 cell 中保留 antd 默认数字 `<span>{date.date()}</span>`，模拟真实行为。
- 新增断言：「cell 内自定义渲染函数不应再包含独立的天数数字元素」（避免回归）。
- 不引入浏览器级视觉测试（jsdom 无法测对齐像素）。

### 风险

- antd 5.29.x Calendar 内部行高与 cell padding 可能在不同视口下动态变化，方案 C 的固定占位可能在极端宽度下误差 1~2px。容差范围内可接受。
- 删除 `<div>{date.date()}</div>` 后，cell 的可点击区域不变（antd 默认 cell value 仍可点击触发 `onSelect`）。
