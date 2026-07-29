# Design: tweak-calendar-selected-day-today-style

## Context

`web/src/parent/components/TaskCalendar.tsx` 是项目唯一的双月日历视图组件（仅用于 parent 角色，挂载于 `/parent/tasks`）。日期 cell 的视觉分层如下：

| 状态层 | 来源 | 视觉 |
|---|---|---|
| **默认当前日（today）** | antd `<Calendar value={today}>` 触发内置 `.ant-picker-calendar-date-today`（颜色由 antd `ConfigProvider.colorPrimary` 决定） | 深青绿（teal `#0d9488`）实心背景 + 白字 + 浅蓝焦点环 + 右上角红角标 |
| **当前选中日（selected）** | `renderDateCell` 返回 cell 的内联 `style`（`TaskCalendar.tsx:282,287`） | 半透明蓝 `rgba(22, 119, 255, 0.12)` + inset 蓝边 `0 0 0 2px rgba(22, 119, 255, 0.5)` + 深字 |
| 任务类型背景 | `data-bg` 与 `bgColor` 内联值（line 258-265），使用 antd token `var(--ant-color-error-bg/info-bg/success-bg)` | 淡红 / 淡蓝 / 淡绿 |
| 任务徽章 | `data-testid=task-badge-*`（line 293-316） | 红色角标 |

历史脉络：
- `fix-calendar-default-current-date`（已归档）：把 `<Calendar value>` 改为 today，让今日自动获得 antd 内置高亮。
- `fix-calendar-non-current-no-highlight-v2`（已归档）：非当前月面板抑制 antd 内置选中/今日高亮（scoped CSS，line 399-405）。
- `fix-calendar-week-row-highlight-bg`（已归档）：把 selected 改为「半透明浅蓝 + inset 蓝边」视觉。**本次变更推翻该决定。**

样式系统现状（关键 token 映射）：

| Token / 变量 | admin | parent | child |
|---|---|---|---|
| antd `colorPrimary`（`themes.ts`） | `#4f46e5` 靛蓝 | `#0d9488` teal | `#0284c7` 天蓝 |
| CSS `--cg-primary`（`themes.css`） | `#334155` 深灰 | `#92400e` 棕 | `#0284c7` 天蓝 |
| CSS `--cg-accent`（`themes.css`） | `#4f46e5` 靛蓝 | `#0d9488` teal | `#84cc16` 黄绿 |
| CSS `--cg-focus`（`themes.css`） | `#4f46e5` 靛蓝 | `#0d9488` teal | `#0284c7` 天蓝 |

> **关键不对称**：parent 角色下 antd `colorPrimary = #0d9488`（teal）与 CSS `--cg-accent` / `--cg-focus` 同值，但与 `--cg-primary`（棕）不同。TaskCalendar 仅 parent 使用，故本变更只需保证 parent 视觉一致即可。

## Goals / Non-Goals

**Goals**
1. selected day 内联 style 视觉与 antd today 内置高亮对齐：深 teal 实心背景 + 白字 + 浅蓝焦点环。
2. 保留既有数据属性（`data-selected`、`data-bg`）与状态判定逻辑，便于回归测试以最小代价迁移。
3. 在 `selected === today`（用户点击今天）时，视觉无双重绘制（selected style 不与 antd today 内置高亮冲突叠加）。

**Non-Goals**
- 不重设计周号行 / 月头选中态。
- 不修改 `selectedRange` reducer、`SELECT_DATE` action 协议。
- 不改 antd `<Calendar value>` 通道与 `.task-calendar-current/non-current-month` className 分流。
- 不引入新 design token；不为其他角色（admin / child）的 selected 视觉做兼容（TaskCalendar 当前仅 parent 使用）。
- 不修改任何 e2e（`parent-task-calendar.spec.ts` 不依赖具体色值）。

## Decisions

### 决策 1：颜色实现 — 硬编码 `#0d9488`（与 antd `colorPrimary` 同源）

**选择**：`backgroundColor: '#0d9488'`、`color: '#ffffff'`、`boxShadow: '0 0 0 2px #93c5fd'`（外层焦点环）。

**理由**：
- 与 antd 内置 today 高亮（`colorPrimary = #0d9488`）100% 视觉一致 — 这是用户的核心诉求。
- antd 5 默认未开启 cssVar 模式（`themes.ts` 未设 `token.cssVar`），故 `var(--ant-color-primary)` 不可用。
- 用 `var(--cg-accent, #0d9488)` 在 parent 下视觉一致，但在 child 角色下 `--cg-accent = #84cc16`（黄绿）会偏离 antd `colorPrimary = #0284c7`（蓝）—— 本次不为该场景做兼容。
- 与项目现有硬编码先例一致：`renderDateCell` line 282-287 已经硬编码 `rgba(22, 119, 255, ...)`；`WeekNumberColumn` 也硬编码同色。

**备选**：
- (A) `var(--cg-accent, #0d9488)` — 多角色跟随，但 child 角色失配。**否决**：TaskCalendar 仅 parent 用，过度设计。
- (B) `var(--ant-color-primary, #0d9488)` — 走 antd token。**否决**：项目未启用 cssVar 模式，变量不存在。
- (C) 加新 design token `--cg-calendar-selected-bg`。**否决**：单一使用点，不需要新 token；与 tweak 轻量精神冲突。

### 决策 2：`boxShadow` 改为外层焦点环（不再是 inset 边框）

**选择**：`boxShadow: '0 0 0 2px #93c5fd'`（外层 spread，模拟 today 的浅蓝焦点环）。

**理由**：
- 原 `inset 0 0 0 2px rgba(22, 119, 255, 0.5)` 是「画在 cell 内部的边框」，视觉上 cell 边缘没有外凸的环。
- today 的视觉是「外层浅蓝焦点环」，由 antd `.ant-picker-calendar-date-today` 自带 `box-shadow` 实现。
- 改为外层 spread 后，selected 与 today 视觉对齐。

**色值来源**：`#93c5fd`（blue-300）为截图中浅蓝焦点环的估读色。与 today 视觉一致即可，无需精确像素采样。

### 决策 3：`selected === today` 时的视觉处理

**选择**：保持 selected style 在所有选中情况下一致应用；antd today 内置高亮叠加时，外层焦点环视觉上无双重绘制（两层 boxShadow spread 不会产生可见错位）。

**理由**：
- 当用户点击「今天」时，`selectedRange.startDate === today`，renderDateCell 返回的 cell 同时具备：(a) antd 内置 `.ant-picker-calendar-date-today`（来自 `<Calendar value={today}>`）；(b) 我们的内联 selected style。
- antd 内置 today 高亮的背景色与我们的 `#0d9488` 同值，视觉无差异。
- 两层 boxShadow（antd 内置 + 我们的外层环）会叠加，但都是浅蓝色 spread，视觉上只是稍微变粗一点，不影响识别。
- 如果产品后续反馈要求严格单层，可在 design.md 补一个 `data-today-selected` 类做抑制；本次不做。

**备选**：检测 `selectedRange.startDate === today.format('YYYY-MM-DD')` 时跳过 selected style。**否决**：增加分支逻辑、违背 tweak 极简原则；视觉叠加不影响识别。

### 决策 4：保留 `data-bg` 属性，不改任务类型背景逻辑

**选择**：当 `isSelected === true` 时，背景由 `bgColor`（任务类型色）切换为 `#0d9488`；`data-bg` 属性仍记录原 `bgColor` 值以便测试断言。

**理由**：与既有行为一致（line 282 三元表达式优先 selected 背景），不破坏现有测试对 `data-bg` 的断言。

## Risks / Trade-offs

| 风险 | 缓解 |
|---|---|
| `selected === today` 时视觉叠加可能略粗（两层浅蓝环） | 截图验收 + 必要时补 `data-today-selected` 抑制；本次接受 |
| 硬编码色值不跟随未来主题切换 | TaskCalendar 仅 parent 使用；若未来扩展到其他角色，引入新 token 时一并迁移 |
| 截图色值为 observer 估读（`#0d9488` / `#93c5fd`），可能与浏览器实际渲染有偏差 | antd `colorPrimary` 已确认 `#0d9488`；焦点环 `#93c5fd` 是次要细节，肉眼无差异；可在 verify 阶段做像素采样 |
| `TaskCalendar.test.tsx` 现有断言依赖旧 `rgba(22, 119, 255, ...)` 色值 | tasks.md 中显式列出测试同步任务 |
| 视觉变化可能影响色弱用户区分 today 与 selected | 用户需求明示两者视觉一致；today 的右上角红角标（任务徽章）和日期数字本身仍是区分线索 |

## Migration Plan

1. 修改 `TaskCalendar.tsx` 中 `renderDateCell` 内联 style（line 281-288）。
2. 同步更新 `TaskCalendar.test.tsx` 中相关色值断言。
3. 本地启动 dev server，目视验证三种场景：(a) 默认 today 高亮；(b) 点击其他日期后 selected 视觉；(c) 点击今天后 selected=today 视觉。
4. 运行单元测试（`pnpm test`）与 e2e（`pnpm test:e2e -- parent-task-calendar`）确认无回归。

**回滚**：单文件改动 + 测试同步，回滚只需 revert 这一个 commit。

## Open Questions

- 焦点环色值是否需要精确像素采样？默认按估读值 `#93c5fd` 处理；若产品反馈不满意，verify 阶段补一次采样。
