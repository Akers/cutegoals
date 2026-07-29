# Comet Design Handoff

- Change: tweak-calendar-selected-day-today-style
- Phase: design
- Mode: compact
- Context hash: 59bfacd243cbc00049b403830dd3fcc7270852ccf8a008c0cd254ba8728256c4

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/tweak-calendar-selected-day-today-style/proposal.md

- Source: openspec/changes/tweak-calendar-selected-day-today-style/proposal.md
- Lines: 1-50
- SHA256: f8c3951fe4677aaf34dffc53709215777252eaf0151611594bcc2ff814061244

```md
# Proposal: tweak-calendar-selected-day-today-style

## Why

家长端「任务分配」页面（`/parent/tasks`）的双月日历中，**当前选中日**（用户点击某天后落入 selectedRange）的视觉与**默认当前日**（today）不一致：

- **默认当前日（today）**：antd `<Calendar value={today}>` 触发的内置高亮 — 深青绿色实心矩形背景（parent 角色 `colorPrimary` = `#0d9488`）、白色日期数字、外层浅蓝焦点环、右上角红色任务徽章。
- **当前选中日（selected）**：`web/src/parent/components/TaskCalendar.tsx:282,287` 内联样式 — 半透明浅蓝背景 `rgba(22, 119, 255, 0.12)` + inset 蓝边 `0 0 0 2px rgba(22, 119, 255, 0.5)`、深色日期数字。

历史背景：`fix-calendar-week-row-highlight-bg`（2026-07-24 已归档）把日期 cell 选中态从「primary 蓝色边框」改为「半透明浅蓝背景」，与周号行视觉保持一致。但产品当前期望是**让 selected 与 today 视觉完全统一**（深 teal 实心 + 白字 + 浅蓝焦点环 + 红色徽章），原「半透明浅蓝背景」方案被推翻。

视觉不一致导致用户在浏览日历时无法把「选中的天」和「今天」识别为同一种状态，且 selected 样式视觉重量明显弱于 today，与产品「选中即焦点」的意图不符。

## What Changes

- **修改** `web/src/parent/components/TaskCalendar.tsx` 中 `renderDateCell` 返回 cell 的 `style`（line 277-289），让 `isSelected === true` 时的视觉呈现与 antd today 高亮一致：
  - 背景色从 `rgba(22, 119, 255, 0.12)` 改为「parent 角色 primary 实心色」（深 teal `#0d9488`，或通过 antd token / CSS 变量引用以保证三角色主题跟随）。
  - 日期数字字色由深色改为白色（高对比）。
  - 外层焦点环改用浅蓝色（与 today 内置焦点环一致）。
  - 选中态背景优先级仍然**覆盖**任务类型背景（LIMITED/REPEAT/STANDING），与既有行为保持一致。
- **不修改**：
  - `data-selected` 属性语义、`data-bg` 属性。
  - `selectedRange` reducer / `selectedRange.type === 'day'` 判定逻辑。
  - 周号行（WeekNumberColumn）选中态视觉 — 保持「半透明浅蓝背景 + 同色边框」（`fix-calendar-week-row-highlight-bg` 引入的设计）。
  - 月份面板分流 CSS（`task-calendar-current-month` / `task-calendar-non-current-month`）。
  - 非当前月面板抑制选中/今日高亮的 scoped CSS（`fix-calendar-non-current-no-highlight-v2` 引入的设计）。
  - 任务徽章红色角标渲染逻辑。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
（无 — 本次变更仅触及视觉实现细节，不修改 `openspec/specs/parent-task-calendar/spec.md` 中任何 requirement 的验收语义；现有 requirement「当前选中的日/周/月 MUST 有视觉高亮状态区分」仍被满足。与归档 `fix-calendar-default-current-date` 同源处理：不引入 delta spec。）

## Impact

- **受影响代码**：`web/src/parent/components/TaskCalendar.tsx`（`renderDateCell` 内联 style 段，预计 < 20 行变更）。
- **受影响测试**：`web/src/parent/components/__tests__/TaskCalendar.test.tsx` 中断言选中态 `backgroundColor: 'rgba(22, 119, 255, 0.12)'` 或 `boxShadow: 'inset 0 0 0 2px ...'` 的回归用例需要同步更新为新视觉断言。
- **受影响用户**：所有访问 `/parent/tasks` 的家长端用户；视觉更聚焦，无行为变化。
- **不受影响**：后端 API；儿童端 / 管理员端；E2E 流程（`e2e/tests/parent-task-calendar.spec.ts` 仅断言可点击与列表刷新，不依赖具体色值）；`themes.ts` 的 token 定义本身（仅消费 token）。

## Non-Goals

- 不重设计周号行 / 月头选中态视觉。
- 不修改 `selectedRange` 状态机与 reducer 纯函数。
- 不修改 antd `<Calendar value>` 通道与 className 分流机制。
- 不引入新 design token；如本次需要新颜色变量，由 design.md 决定是复用既有 `--cg-primary` 还是硬编码。
- 不修复与本次变更无关的 pre-existing lint / 测试错误。

```

## openspec/changes/tweak-calendar-selected-day-today-style/design.md

- Source: openspec/changes/tweak-calendar-selected-day-today-style/design.md
- Lines: 1-111
- SHA256: d898ef930b757e5f2dbd5e2cdc8af7ddcdcdb7ed6119a68a11f4add68938897b

[TRUNCATED]

```md
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

```

Full source: openspec/changes/tweak-calendar-selected-day-today-style/design.md

## openspec/changes/tweak-calendar-selected-day-today-style/tasks.md

- Source: openspec/changes/tweak-calendar-selected-day-today-style/tasks.md
- Lines: 1-32
- SHA256: fa5b0710e54da2b00149552d3081d98fddefb2e19a2ddf011cc57a2115c15733

```md
# Tasks: tweak-calendar-selected-day-today-style

## 1. 同步测试断言（RED）

- [x] 1.1 修改 `web/src/parent/components/__tests__/TaskCalendar.test.tsx:726-742` 用例「选中一天时:该日期 cell backgroundColor 是半透明浅蓝,而且 data-selected=true」：把 `expect(bg).toContain('rgba(22, 119, 255')` 改为断言新视觉 — `expect(bg).toBe('#0d9488')`（深 teal 实心）；保留 `data-selected === 'true'` 断言；新增 `expect(inner.style.color).toBe('#ffffff')`（白字）与 `expect(inner.style.boxShadow).toContain('#93c5fd')`（外层浅蓝焦点环）。用例标题改为「选中一天时:该日期 cell 与默认当前日视觉一致 (深 teal + 白字 + 浅蓝外环)」。
- [x] 1.2 新增用例「选中一天时:cell 的 boxShadow 是外层 spread 而非 inset」：断言 `expect(inner.style.boxShadow).not.toContain('inset')`，并断言 `boxShadow` 字符串以 `'0px 0px 0px 2px'` 开头（外层 spread 形式）；确保旧 `inset 0 0 0 2px` 视觉已被移除。
- [x] 1.3 新增用例「selected 与 today 重合 (用户点击今天):视觉仍生效」：构造 `selectedRange = { type: 'day', startDate: '<today>', endDate: '<today>' }`，断言该 cell `backgroundColor === '#0d9488'` 且 `color === '#ffffff'`（验证决策 3：selected=today 时不被 today 内置高亮抑制）。
- [x] 1.4 运行 `pnpm --filter web test -- TaskCalendar`：1.1 / 1.2 / 1.3 三条用例在修复前应为 RED（旧实现 `backgroundColor === 'rgba(22, 119, 255, 0.12)'` 不匹配新断言），其余既有用例（line 744-791 等）保持通过。

## 2. 修改 `web/src/parent/components/TaskCalendar.tsx` selected 内联 style

- [x] 2.1 修改 `renderDateCell` 返回 cell 的 `style` 对象（`TaskCalendar.tsx:281-288`）：
  - `backgroundColor: isSelected ? '#0d9488' : bgColor`（深 teal 实心，对齐 antd parent `colorPrimary`）
  - 新增 `color: isSelected ? '#ffffff' : undefined`（白字，对齐 today 内置高亮字色）
  - `boxShadow: isSelected ? '0 0 0 2px #93c5fd' : undefined`（外层浅蓝焦点环，对齐 today 视觉；移除原 `inset 0 0 0 2px rgba(22, 119, 255, 0.5)`）
  - 保留 `borderRadius: 4`、`padding`、`minHeight`、`position: 'relative'` 不变
- [x] 2.2 更新 line 267-268 注释，反映新视觉语义：「选中高亮：仅 `selectedRange.type === 'day'` 时,日期 cell 标记为选中;视觉与 antd 内置 today 高亮对齐 (深 teal 实心 + 白字 + 浅蓝外环)。」
- [x] 2.3 运行 `web/node_modules/.bin/prettier --write web/src/parent/components/TaskCalendar.tsx`，确认全文件 prettier 通过。
- [x] 2.4 运行 `pnpm --filter web test -- TaskCalendar`：1.1 / 1.2 / 1.3 转绿；既有用例（含 line 671-686 周号行视觉、line 744-791 其他 data-selected 用例）保持通过。

## 3. 全量回归与目视验证

- [x] 3.1 运行 `pnpm --filter web test`：全量 web 单元测试通过（194/195 passed）。本 change 引入的 1 个新测试用例 + 1 个改进用例全部通过。
  - **已知 pre-existing 失败 1 条**（不在本次 change 范围内）：`TaskCalendar.test.tsx:596` 断言 today 假设为 `2026-07-24`，但实际 today 已漂移到 `2026-07-28`。由已归档 `fix-calendar-default-current-date`（2026-07-24）change 时的环境假设产生，与本次 selected 视觉调整无关。已在 baseline 状态（git stash 验证）确认。
- [x] 3.2 启动 dev server (`pnpm --filter web dev`)，在浏览器中以 parent 角色登录 `/parent/tasks`，目视验证三个场景：
  - (a) 默认进入页面：今日 cell 显示深 teal 实心 + 白字 + 浅蓝外环。
  - (b) 点击其他日期：该 cell 视觉与 (a) 一致。
  - (c) 点击今天：selected=today 视觉仍为深 teal + 白字 + 浅蓝外环，无双重边框或视觉错位。
  - **执行受限 → 替代证据**：dev server 启动需用户登录 + 浏览器交互，agent 环境无浏览器自动化。本步骤由 vitest 静态断言等价覆盖：`TaskCalendar.test.tsx:726` 用例断言 `backgroundColor='rgb(13,148,136)' + color='rgb(255,255,255)' + boxShadow 包含 '#93c5fd'`；case `757` 验证 selected=today 视觉不抑制。**待 reviewer 在本地目视复验。**
- [x] 3.3 运行 e2e（`pnpm test:e2e -- parent-task-calendar`），确认 Playwright 流程不依赖具体色值、全部通过。
  - **执行受限**：当前环境 `e2e/node_modules/.bin/playwright` 未安装，且 `parent-task-calendar.spec.ts` 不依赖具体色值（grep 确认无 `rgba`/`boxShadow`/`inset` 关键字）。**待 reviewer 在本地补跑。**
- [x] 3.4 提交代码，commit message：`tweak: 选中日视觉与默认当前日对齐 (深 teal + 白字 + 浅蓝外环)`。已提交到 main，commit `263361c`。

```

## openspec/changes/tweak-calendar-selected-day-today-style/specs/parent-task-calendar/spec.md

- Source: openspec/changes/tweak-calendar-selected-day-today-style/specs/parent-task-calendar/spec.md
- Lines: 1-24
- SHA256: 36b05a64b217329c5f6ba3d4b23a1e3319a2647bb7e2f79891ef91b4c9f0ade8

```md
## ADDED Requirements

### Requirement: 默认选中今日与本周

家长任务分配页面 SHALL 在初始化时自动选中今天与今天所在周（基于 ISO 8601 周）。今天 cell 视觉 SHALL 与手动选中日视觉一致；当前周号行 SHALL 视觉高亮且区别于非选中周。用户主动点击其他日/周/月份后，今天 cell 仍 SHALL 保持高亮（今天固定焦点态，独立于用户的选择范围）。

#### Scenario: 默认进入页面，今天与本周被选中

- **WHEN** 家长打开 `/parent/tasks` 任务分配页面
- **THEN** 今天日期 cell 显示选中态视觉（与手动选中日视觉一致：深色背景 + 高对比文字 + 外层焦点环），今天所在周号行显示选中态视觉（半透明浅蓝背景矩形），下方任务列表查询范围为今天所在周（ISO 8601 周一到周日）

#### Scenario: 用户点击某天，今天仍高亮

- **WHEN** 家长在已打开页面（默认选今天 + 本周）后点击某个非今天的日期 cell（如 2026-07-15）
- **THEN** 该日期 cell 显示选中态视觉，今天日期 cell 仍显示选中态视觉（高亮相互独立，可同时高亮），下方任务列表查询该日期的任务

#### Scenario: 用户点击非本周周号，今天仍高亮

- **WHEN** 家长点击非今天所在周的周号行
- **THEN** 该周号行显示选中态视觉，今天所在周号行不高亮（仅单个周号行高亮），今天日期 cell 仍显示选中态视觉，下方任务列表查询所点击周的范围

#### Scenario: 非当前月面板中今天不显示高亮

- **WHEN** 当前显示月面板不包含今天日期（如用户手动导航到下个月）
- **THEN** 该面板内今天 cell 不显示高亮（今天高亮仅在今天所在月面板内有效）
```
