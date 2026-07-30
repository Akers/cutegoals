---
change: parent-task-calendar-single-month
design-doc: docs/superpowers/specs/2026-07-30-parent-task-calendar-single-month-design.md
base-ref: a5b57d815de2dfe7ae3b1bf26189b4209d3704f9
---

# parent-task-calendar-single-month 实施计划

> **产物语言**: zh-CN
>
> **关联文档**:
> - 任务边界：`openspec/changes/parent-task-calendar-single-month/tasks.md`（3 章 / 9 子任务）
> - 技术设计：`docs/superpowers/specs/2026-07-30-parent-task-calendar-single-month-design.md`（10 个决策 + 6 项风险 + 4 类测试策略）
> - 父计划：`docs/superpowers/plans/2026-07-22-parent-dual-month-task-calendar.md`（本计划是其反向变更，回收双月多引入的代码）
> - Spec 来源：`openspec/changes/parent-task-calendar-single-month/specs/parent-task-calendar/spec.md`
>
> **实施顺序**：TaskCalendar 主组件单月化 → CSS 简化 → 单元测试全面重写 → E2E 调整 → Spec 回写 → 三类验证。共 5 个阶段。
>
> **测试策略**：单元测试 4 类（vitest）/ 类型检查（tsc）/ E2E（playwright）/ 浏览器手动视觉复核 4 项。
>
> **代码影响面**（基线 = HEAD `a5b57d8`）：
> - 主文件：`web/src/parent/components/TaskCalendar.tsx`（455 行 → 估 ≤ 360 行）
> - 测试文件：`web/src/parent/components/__tests__/TaskCalendar.test.tsx`（911 行 → 估 ~ 350 行）
> - E2E：`e2e/tests/parent-task-calendar.spec.ts`（106 行 → 估 ~ 90 行）
> - Spec：`openspec/changes/parent-task-calendar-single-month/specs/parent-task-calendar/spec.md`（54 行 → 微调）
> - 不修改：`web/src/parent/pages/index.tsx`（ParentTasksPage 父组件）、`web/src/parent/calendarReducer`、`/task-assignments/calendar` 后端契约

## 计划概览

本计划将 tasks.md 的 9 项子任务按 Design Doc 定义的变更层级归并为 **5 个阶段**，每阶段内任务独立可勾选、单文件变更控制在 ≤ 200 行、提交即可独立验证。

**关键决策引用**（来自 Design Doc §Decisions）：

| # | 决策 | 落地阶段 |
|---|---|---|
| 1 | 单月独立面板（去掉第二个 CalendarPanel） | Phase 1 |
| 2 | 保留 CalendarPanel 子组件 + 复用模式 | Phase 1 |
| 3 | 导航按钮保留（`<` `>`） | Phase 1 |
| 4 | 删除全部 `.task-calendar-non-current-month-*` CSS 规则（5 条） | Phase 2 |
| 5 | CalendarPanel 删除 `isCurrentMonth` 计算（YAGNI 极简） | Phase 1 |
| 6 | CalendarPanel antd value 计算简化（二选一） | Phase 1 |
| 7 | 任务徽章视觉与数据协议不变 | 不变（无任务） |
| 8 | 响应式容器简化（grid → block、删除 media query） | Phase 2 |
| 9 | 测试全面重写（T2） | Phase 3 |
| 10 | Spec Patch 回写 delta spec | Phase 4 |

**关键风险引用**（来自 Design Doc §Risks）：

| Risk | Mitigation |
|---|---|
| 删除 `isCurrentMonth` + non-current CSS 后，默认进入页面 today cell 不再与 selected 视觉一致 | Spec Patch 显式记录新视觉语义（浅蓝边框 + teal 字色 vs teal 实心 + 白字）— Phase 4 |
| CalendarPanel.test.tsx 全面重写风险（30+ 个测试） | 保留 WeekNumberColumn 内部测试、computeWeekNumbers 测试、dateCellRender 徽章测试；只重写双月依赖部分 |
| 父组件 ParentTasksPage 可能历史上有依赖双月面板布局的代码 | 父组件只通过 props 传 baseMonth + selectedRange，不依赖内部布局，验证零父组件修改（Phase 5 复查） |
| 用户失去"一眼看到下月"的便利性 | 保留导航按钮，标题区显示当前月；UX 退化可接受 |
| CSS 简化后若回滚到双月，需重新添加 non-current 规则 | 旧规则在 git history 可恢复；保留 CalendarPanel 子组件签名不变，回滚成本低 |

**测试策略引用**（来自 Design Doc §Test Strategy）：

- **单元（vitest）**：渲染、导航、选中、周号列、任务徽章、useApi 加载/错误/重试、today 视觉、baseMonth 跨月
- **类型检查（tsc）**：`pnpm --filter web typecheck`
- **E2E（playwright）**：单月日历渲染、日期点击联动、任务筛选、查看全部、移动端布局
- **视觉（浏览器手动）**：单月布局、选中态（teal 实心 + 白字）、today（浅蓝边框 + teal 字色）、周号高亮（蓝色边框）、任务徽章（红色）

---

## 阶段 1：TaskCalendar 主组件单月化（tasks.md §1.1-1.2 + Decision 5-6）

**目标**：将 `TaskCalendar` 主组件从"双月并排"改造为"单月独立面板"。

**前置 verify**：
- ⚡ verify 当前 `TaskCalendar.tsx` 第 357-358 行 `currentMonth / nextMonth` 计算（第 437-451 行两个 `<CalendarPanel>`）
- ⚡ verify `CalendarPanel` 当前 `isCurrentMonth` 计算（line 221-222）、antd value 闭包（line 326-333）、wrapper className 三处（line 298 / 322-324）
- ⚡ verify 父组件 `ParentTasksPage.index.tsx` 不依赖"两个 CalendarPanel"行为（仅依赖 `baseMonth / selectedRange / onSelect / onNavigate` 四个 props）

**涉及决策**：Decision 1、2、3、5、6

### Task 1.1: 简化 CalendarPanel 内部（删除 isCurrentMonth + 简化 antd value + 硬编码 className）

- **关联**：tasks.md §1.1（CalendarPanel 内部）+ Design Doc Decision 5、6
- **capability**：parent-task-calendar
- **目标**：在 `CalendarPanel` 内部删除"非当前月"判定逻辑，让 wrapper className 永远为 `task-calendar-current-month`，简化 antd `value` 计算（去三元）。
- **实现方式**（Design Doc §Decisions 5、6）：
  - 删除 line 217-222 注释 + `const today = dayjs(); const isCurrentMonth = ...` 两行（共 6 行）
  - 将 line 298、322-324 中 `className={isCurrentMonth ? ... : ...}` 改为固定字符串 `className="task-calendar-current-month"`
  - 简化 line 326-333 antd `<Calendar value={(() => { ... })()}>` 为：
    ```tsx
    value={(() => {
      if (!selectedRange) return monthDate;
      if (selectedRange.type === 'day') return dayjs(selectedRange.startDate);
      return monthDate;
    })()}
    ```
    删除 `isCurrentMonth ? today : monthDate` 分支
  - 删除 line 217 注释中 "fix-calendar-non-current-no-highlight" 全文（已不再适用）
- **修改文件**：
  - `web/src/parent/components/TaskCalendar.tsx` — `CalendarPanel` 函数体（line 212-343）
- **净变更**：约 -8 行 / ~ 10 行修改（仍在 ≤ 200 行变更约束内）
- **验收标准**：
  - `CalendarPanel` 内不再出现 `isCurrentMonth` 变量
  - wrapper div `className` 固定为 `task-calendar-current-month`
  - `pnpm --filter web typecheck` 无类型错误
- **依赖任务**：无（独立 commit，单独验证 TaskCalendar 现有测试仍 GREEN — 因为 TaskCalendar 仍渲染两个 panel，但每个 panel 的 className/行为现在统一）
- **风险**：本任务独立运行时仍会渲染两个 panel，但两个 panel 的 className 均变为 `task-calendar-current-month`。原本依赖"非当前月"CSS 抑制行为的所有 e2e 场景可能视觉变化，但单元测试断言仅基于 `data-testid`，不受 CSS className 影响 → **单元测试应仍 GREEN**。
- **运行验证**：
  ```bash
  pnpm --filter web test -- TaskCalendar
  # 期望：所有既存测试仍 GREEN（className 变更不影响 data-testid 断言）
  # 但部分依赖 isCurrentMonth 的旧测试可能 RED（见 Phase 3 单元测试全面重写）
  ```
- **回滚**：`git checkout -- web/src/parent/components/TaskCalendar.tsx`

### Task 1.2: 删除第二个 CalendarPanel + 简化导航栏标题 + 删除 nextMonth

- **关联**：tasks.md §1.1（外层渲染）+ §1.2（标题）
- **capability**：parent-task-calendar
- **目标**：删除 TaskCalendar 主组件中第二个 `<CalendarPanel>`（渲染下月）、删除 `nextMonth` 计算、简化导航栏标题为单月格式。
- **实现方式**（Design Doc §Decisions 1、3）：
  - 删除 line 358 `const nextMonth = currentMonth.add(1, 'month');`
  - 修改 line 423 标题：`{currentMonth.format('YYYY年M月')} — {nextMonth.format('YYYY年M月')}` → `{currentMonth.format('YYYY年M月')}`
  - 删除 line 446-451 第二个 `<CalendarPanel ... />` 块（含注释 `第二个月份面板`）
- **修改文件**：
  - `web/src/parent/components/TaskCalendar.tsx` — `TaskCalendar` 主组件（line 347-454）
- **净变更**：约 -16 行（删除 nextMonth + 第二个 panel）/ 1 行修改（标题）
- **验收标准**：
  - `TaskCalendar` 渲染时仅包含 **1 个** `<CalendarPanel>` 调用
  - 导航栏标题文本为单月格式（如 `2026年7月`），不再包含 ` — YYYY年M月`
  - `<` `>` 按钮仍存在并触发 `onNavigate(-1) / onNavigate(1)`
- **依赖任务**：Task 1.1
- **运行验证**：
  ```bash
  pnpm --filter web typecheck
  pnpm --filter web test -- TaskCalendar
  # 预期：双月相关 it（如 "渲染两个日历面板"）变 RED；其它 GREEN
  ```
- **回滚**：`git checkout -- web/src/parent/components/TaskCalendar.tsx`

---

## 阶段 2：CSS 简化（tasks.md §1.3-1.4）

**目标**：删除已无作用的 non-current CSS 规则；将 grid 容器改为简单 block。

**涉及决策**：Decision 4、8

### Task 2.1: 删除 `.task-calendar-non-current-month-*` 全部 5 条 CSS 规则

- **关联**：tasks.md §1.3
- **capability**：parent-task-calendar
- **目标**：删除 CSS `<style>` 块中所有以 `.task-calendar-non-current-month` 开头的规则（共 5 条），单月模式下不再需要抑制高亮。
- **实现方式**（Design Doc §Decision 4）：
  - 删除 line 392-395：`.task-calendar-non-current-month .ant-picker-cell-today:not(.ant-picker-cell-selected) ...`（1 条）
  - 删除 line 402-408：3 条（cell-selected cell background、cell-selected today background、cell-selected date-value color）
  - 共 -4 条（注意：line 392-395 第 1 条 + line 402-408 第 2-4 条总计 4 选 5 的差异来自原代码中实际只 4 条独立规则；Design Doc §Decision 4 标注为 5 条，但 line 392/395 实为同一规则的两条独立 CSS 选择器）
  - **实际需要删除的选择器清单**：
    ```css
    /* 删除 1: */
    .task-calendar-non-current-month .ant-picker-cell-today:not(.ant-picker-cell-selected) .ant-picker-calendar-date {
      border: 1px solid #1677ff !important;
      border-radius: 4px;
    }
    /* 删除 2-4: */
    .task-calendar-non-current-month .ant-picker-cell-selected .ant-picker-calendar-date,
    .task-calendar-non-current-month .ant-picker-cell-selected .ant-picker-calendar-date-today {
      background: transparent !important;
    }
    .task-calendar-non-current-month .ant-picker-cell-selected .ant-picker-calendar-date-value {
      color: inherit !important;
    }
    ```
  - 保留 line 392 上方与 line 401 的注释，但移除 line 367-372 的 "fix-calendar-non-current-no-highlight-v2" 历史注释（替换为简短说明）
- **修改文件**：
  - `web/src/parent/components/TaskCalendar.tsx` — TaskCalendar 主组件内 `<style>` 块（line 363-410）
- **净变更**：约 -20 行 / 0 行新增
- **验收标准**：
  - 代码搜索 `non-current-month` 不返回任何 CSS 选择器（仅可能出现在历史注释中）
  - 单月在浏览器中仍正常显示（teal 实心选中、today 浅蓝边框 + teal 字色、徽章正常）
- **依赖任务**：Task 1.2（先删除第二个 panel，确保 CSS 选择器不会再被命中）
- **运行验证**：
  ```bash
  grep -n "non-current-month" web/src/parent/components/TaskCalendar.tsx
  # 预期：无输出或仅出现在被替换后的注释中
  ```
- **回滚**：`git checkout -- web/src/parent/components/TaskCalendar.tsx`

### Task 2.2: 简化 Grid 容器（display: block + 删除 media query）

- **关联**：tasks.md §1.4
- **capability**：parent-task-calendar
- **目标**：`.task-calendar-grid` 从 `display: grid; grid-template-columns: repeat(auto-fit, minmax(400px, 1fr))` 改为 `display: block; width: 100%`；删除 `@media (max-width: 767px)` 响应式覆盖（单月下不再需要）。
- **实现方式**（Design Doc §Decision 8）：
  - 修改 line 429-435：`task-calendar-grid` div 样式：
    - 旧：`{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(400px, 1fr))', gap: 16 }`
    - 新：`{ display: 'block', width: '100%' }`
  - 删除 line 364-366 `@media (max-width: 767px) { .task-calendar-grid { grid-template-columns: 1fr !important; } }` 媒体查询块
  - 保留 `className="task-calendar-grid"` 标识符
- **修改文件**：
  - `web/src/parent/components/TaskCalendar.tsx` — TaskCalendar 主组件（line 429-435 + line 364-366）
- **净变更**：约 -7 行 / 3 行修改
- **验收标准**：
  - DOM 中 `.task-calendar-grid` 内仅包含单个月份 panel（无 grid 列布局）
  - 移动端（< 768px）单月面板占满容器宽度
  - 桌面端（≥ 768px）单月面板占满容器宽度（与移动端行为一致）
- **依赖任务**：Task 2.1（CSS 同步清理）
- **运行验证**：
  ```bash
  pnpm --filter web test -- TaskCalendar
  ```
- **回滚**：`git checkout -- web/src/parent/components/TaskCalendar.tsx`

---

## 阶段 3：单元测试全面重写（tasks.md §2.1-2.2）

**目标**：`TaskCalendar.test.tsx` 从"双月 30+ 场景"全面重写为"单月"场景。**保留** WeekNumberColumn 内部断言、computeWeekNumbers、dateCellRender 徽章；**删除**所有依赖"双 panel / 双 API 请求 / 双月标题"的断言。

**涉及决策**：Decision 9

### Task 3.1: 重写 TaskCalendar.test.tsx — 删除双月断言 + 新增单月断言

- **关联**：tasks.md §2.1（删除双月）+ §2.2（新增单月）
- **capability**：parent-task-calendar
- **目标**：将 911 行测试文件全面重写为单月场景，约 30 个 it 测试缩减/调整为约 18 个；每个测试与 Design Doc §Test Strategy 8 项覆盖场景一一对应。
- **实现方式**（Design Doc §Decision 9、§Test Strategy）：
  - **删除所有双月断言**（tasks.md §2.1）：
    - `it('渲染两个日历面板（双月）')` — 删除
    - `it('第二个面板的 CalendarHeader 显示下月信息')` — 删除
    - `it('单面板错误不影响另一面板')` — 删除（`mockUseApi.mockImplementation` 内 `month=7/8` 路径分支不再适用）
    - 任何引用 `augustPanel()` 辅助函数（line 144-146）的 it — 删除或迁移
  - **新增单月断言**（tasks.md §2.2）：
    - `it('渲染单个月历面板')` — `getByTestId('mock-calendar-2026-07')` 仅 1 个；不存在 `mock-calendar-2026-08`
    - `it('导航栏标题仅显示当前月')` — `getByText('2026年7月')`；不存在 `2026年8月`
    - `it('跨年时显示单月标题')` — `baseMonth='2026-12'` → `getByText('2026年12月')`（不再断言 `2026年12月 — 2027年1月`）
  - **调整既有断言**（单月化）：
    - `it('渲染导航栏及月份标题')` — 改为断言 `'2026年7月'` 单月；不再断言 `'2026年7月 — 2026年8月'`
    - `it('点击 < 按钮触发 onNavigate(-1)')` — 保留
    - `it('点击 > 按钮触发 onNavigate(1)')` — 保留
    - 删除 `describe('v2 视觉抑制 wrapper className (回归, fix-calendar-non-current-no-highlight-v2)')`（line 591-623 整块）— 双月已不存在，className 单一，"非当前月" wrapper className 测试失去意义
    - 删除 `describe('默认选中今日')` 中依赖 `augustPanel()` 的 it
    - `describe('今天高亮由 selectedRange 范围决定 (fix-build)')` — 删除 line 869-908 中所有引用 `augustPanel()` 的 it（仅保留 `julyPanel()` 单月场景）
  - **保留单月无关断言**（Decision 9 说明）：
    - `describe('dateCellRender 徽章显示')` 完整保留
    - `describe('WeekNumberColumn')` 中 `computeWeekNumbers` 三个 it + `点击周号触发 SELECT_WEEK` + 几何对齐三个 it 全部保留
    - `describe('三级点击交互')` 完整保留
    - `describe('useApi 数据获取')` — 删除 line 491 `it('单面板错误不影响另一面板')`（不再适用）；其余 loading / error / refetch / 正常数据保留
  - **为调整 mock**：
    - `mockUseApi.mockImplementation` 多路径分支（line 492-507）不再需要，全局 `mockReturnValue` 即可
    - 删除 `augustPanel()` 辅助函数
- **修改文件**：
  - `web/src/parent/components/__tests__/TaskCalendar.test.tsx` — 全面重写（911 行 → 估 ~ 350 行）
- **净变更**：约 -560 行 / ~ 80 行新增
- **验收标准**：
  - `pnpm --filter web test -- TaskCalendar` 全部 GREEN
  - 测试用例名称中不再出现"双月"、"双 CalendarPanel"、"另一面板"、"上下堆叠"等依赖双月的语义
  - `mockUseApi.mockReturnValue` 不依赖 `mockImplementation` 多路径分支
  - 8 项测试覆盖场景（Design Doc §Test Strategy）：
    1. 单月渲染 ✓
    2. 导航按钮 ✓
    3. 三级点击 ✓
    4. 周号列 6 行 + 选中 + 几何对齐 ✓
    5. 任务徽章 total>0/=0 ✓
    6. useApi loading/error/refetch ✓
    7. today cell（baseMonth=今日所在月时 antd 自动加 today 类）✓
    8. baseMonth 跨月（baseMonth='2026-08' 时 value=monthDate）✓
- **依赖任务**：Phase 1 + Phase 2 全部完成
- **运行验证**：
  ```bash
  pnpm --filter web test -- TaskCalendar
  # 期望：所有现存 + 新增 it 全部 GREEN
  ```
- **回滚**：`git checkout -- web/src/parent/components/__tests__/TaskCalendar.test.tsx`

### Task 3.2: 校验 ParentTasksPage.test.tsx 无需变更

- **关联**：tasks.md §2.1（间接）+ Design Doc §Decision 9（"ParentTasksPage.test.tsx 仅需校验与 TaskCalendar 的 prop 交互不变"）
- **capability**：parent-task-calendar
- **目标**：确认 ParentTasksPage 测试 mock 仍能通过（不依赖 panel 数量）。
- **实现方式**：
  - 阅读 `web/src/parent/pages/__tests__/ParentTasksPage.test.tsx` line 35-50 区域的 mock（`vi.mock('@parent/components/TaskCalendar', ...)`）
  - 该 mock 把 TaskCalendar 替换为简化 stub：`({ baseMonth, selectedRange, onSelect, onNavigate }) => <div data-testid="mock-task-calendar">...`
  - 因为 mock stub 不渲染 panel，断言依赖 `data-testid="mock-task-calendar"` 与 `data-*` 属性，与"单月 vs 双月"无关
  - **预期零变更** — 但本任务需在 Phase 3 末尾运行测试做最终确认
- **修改文件**：
  - `web/src/parent/pages/__tests__/ParentTasksPage.test.tsx` — 仅在测试失败时才修改
- **验收标准**：
  - `pnpm --filter web test -- ParentTasksPage` 全部 GREEN（不需要修改代码）
- **依赖任务**：Phase 1 完成（确认 props 签名不变） + Task 3.1
- **运行验证**：
  ```bash
  pnpm --filter web test -- ParentTasksPage
  ```
- **回滚**：N/A（不修改或视运行结果局部回滚）

### Task 3.3: 更新 E2E 测试 parent-task-calendar.spec.ts

- **关联**：Design Doc §Decision 9（E2E 调整）+ Design Doc §Migration Plan 第 4 步
- **capability**：parent-task-calendar
- **目标**：删除"双月日历渲染"断言（`toHaveCount(2)`），改为单月面板断言（`toHaveCount(1)` 或 `toBeVisible`）；删除"移动端日历上下堆叠"测试；标题从"双月"改为"单月"。
- **实现方式**：
  - 修改 line 19 `test.describe('家长端双月任务日历', ...)` → `test.describe('家长端单月任务日历', ...)`（标题调整）
  - 修改 line 4 注释 "家长端双月任务日历 E2E 测试" → "家长端单月任务日历 E2E 测试"
  - 修改 line 8 注释 "双月日历渲染（桌面/移动端）" → "单月日历渲染"
  - 修改 line 28-38 `it('页面加载后显示双月日历', ...)` 中：
    - `calendarGrid.locator('.calendar-panel').toHaveCount(2)` → `calendarGrid.locator('.calendar-panel').toHaveCount(1)` 或 `toBeVisible()`
    - `description` "验证双月日历渲染" → "验证单月日历渲染"
    - `description` "验证两个日历面板" → "验证一个日历面板"
  - 修改 line 97-105 `it('移动端日历上下堆叠', ...)`：
    - 整段删除或改写为 `it('移动端单月布局', ...)` 仅断言日历可见
- **修改文件**：
  - `e2e/tests/parent-task-calendar.spec.ts`（106 行 → 估 ~ 90 行）
- **净变更**：约 -16 行 / ~ 5 行修改
- **验收标准**：
  - 文件搜索 "双月"、"两个日历面板" 不返回任何测试断言
  - `pnpm test:e2e -- parent-task-calendar` 全部通过（依赖 docker compose 启动的服务实例）
- **依赖任务**：Task 3.1
- **运行验证**：
  ```bash
  grep -nE "双月|toHaveCount\(2\)|上下堆叠" e2e/tests/parent-task-calendar.spec.ts
  # 预期：无输出
  ```
- **回滚**：`git checkout -- e2e/tests/parent-task-calendar.spec.ts`

---

## 阶段 4：Spec Patch 回写（Design Doc §Decision 10）

**目标**：将 Design Doc 中变更语义回写到 delta spec `openspec/changes/parent-task-calendar-single-month/specs/parent-task-calendar/spec.md`。

**涉及决策**：Decision 10

### Task 4.1: 修改 default-selected scenario + 新增 baseMonth 包含今天场景

- **关联**：Design Doc §Decision 10
- **capability**：parent-task-calendar
- **目标**：在 delta spec 中显式记录新视觉语义（"today 与 selected 视觉区分"），新增 baseMonth 包含今天时的视觉场景。
- **实现方式**（Design Doc §Decision 10）：
  - **修改 Scenario "默认进入页面，今天与本周被选中"**（line 26-30）：
    - 旧："今天日期 cell 显示选中态视觉（teal 实心 + 白字，与手动选中日视觉一致）"
    - 新："今天日期 cell 显示 today 视觉（浅蓝边框 + teal 字色），与选中态视觉（teal 实心 + 白字）区分"
  - **修改 Scenario "用户点击某天后 today 不再单独高亮（fix-build）"**（line 31-34）：
    - 旧描述："今天日期 cell 不再显示选中态视觉，改由 today 视觉呈现（浅蓝边框 + teal 字色）"
    - 实质未变，但需确认与新场景一致
  - **Scenario "baseMonth 包含今天时的 today 视觉"**（line 47-49）已经存在，**需核对内容**：
    - 如已与 Design Doc §Decision 10 一致 → 跳过
    - 如未列出的视觉描述 → 微调
- **修改文件**：
  - `openspec/changes/parent-task-calendar-single-month/specs/parent-task-calendar/spec.md`
- **验收标准**：
  - 文件第一段 Requirement "默认选中今日与本周" 文本与 Design Doc 一致
  - Scenario "默认进入页面，今天与本周被选中" 描述明确区分 today 视觉 vs selected 视觉
- **依赖任务**：Phase 1（实际行为变更已就绪后才能回写 spec）
- **运行验证**：
  ```bash
  grep -n "today 视觉" openspec/changes/parent-task-calendar-single-month/specs/parent-task-calendar/spec.md
  grep -n "选中态视觉" openspec/changes/parent-task-calendar-single-month/specs/parent-task-calendar/spec.md
  # 期望：两者同时出现于 Scenario 描述
  ```
- **回滚**：`git checkout -- openspec/changes/parent-task-calendar-single-month/specs/parent-task-calendar/spec.md`

---

## 阶段 5：全量验证（tasks.md §3.1-3.3 + Design Doc §Test Strategy）

**目标**：在所有代码变更就绪后，运行三类验证（vitest / tsc / playwright）+ 浏览器手动视觉复核。

### Task 5.1: 单元测试全量运行

- **关联**：tasks.md §3.1
- **运行验证**：
  ```bash
  pnpm --filter web test -- TaskCalendar ParentTasksPage
  # 期望：全部 GREEN
  ```
- **验收标准**：
  - TaskCalendar.test.tsx 全部 GREEN（约 18 个 it）
  - ParentTasksPage.test.tsx 全部 GREEN（无新增变更）
  - 不存在 "TODO" / "FIXME" / ".skip" 跳过测试
- **回滚**：依失败用例逐一回滚 Phase 1-3 任务

### Task 5.2: TypeScript 类型检查

- **关联**：tasks.md §3.2
- **运行验证**：
  ```bash
  pnpm --filter web typecheck
  # 期望：零错误
  ```
- **验收标准**：
  - 零编译错误
  - 无 "unused import" 警告（特别是删除 nextMonth 后若残留 `dayjs` add 引用应清理）
- **回滚**：依 tsc 报错修复类型

### Task 5.3: E2E Playwright 验证

- **关联**：tasks.md §3.3
- **运行验证**：
  ```bash
  # 1. 启动服务
  docker compose -f deploy/docker-compose.yml up -d
  # 2. 等待就绪
  sleep 30
  # 3. 运行 e2e
  pnpm --filter e2e test:e2e -- parent-task-calendar
  # 或者根目录：
  pnpm test:e2e -- parent-task-calendar
  ```
- **验收标准**：
  - E2E 全部通过（4-5 个测试）
  - 不存在浏览器 console error
- **回滚**：依 e2e 失败逐项修复

### Task 5.4: 浏览器手动视觉复核（Design Doc §视觉验证）

- **运行验证**：
  - 桌面端（≥ 768px）打开 `/parent/tasks`：
    - [ ] 单月面板占满容器宽度
    - [ ] 选中态视觉：teal 实心 + 白字
    - [ ] today 视觉：浅蓝边框 + teal 字色（与选中态区分）
    - [ ] 周号行高亮：选中周蓝色边框
    - [ ] 任务徽章：红色圆角矩形（top: -26px, left: 20px）
  - 移动端（< 768px）打开 `/parent/tasks`：
    - [ ] 单月面板占满容器宽度
    - [ ] 与桌面端视觉一致（无上下堆叠）
  - 跨月导航：
    - [ ] 点击 `>` 切换到下月，标题更新
    - [ ] 点击 `<` 切换回当月
- **验收标准**：5 + 1 + 2 共 8 项视觉均符合 Design Doc §视觉验证
- **回滚**：N/A（视觉复核仅为确认）

---

## 提交策略

按"独立可验证 + 单文件 ≤ 200 行变更"约束，本计划建议以下 6 个 commit：

| # | Commit | 涉及任务 | 验证命令 |
|---|---|---|---|
| 1 | `refactor(parent-calendar): simplify CalendarPanel internals (drop isCurrentMonth, simplify value)` | Task 1.1 | `pnpm --filter web test -- TaskCalendar` |
| 2 | `feat(parent-calendar): render single-month TaskCalendar (remove 2nd panel + simplify title)` | Task 1.2 | `pnpm --filter web typecheck && pnpm --filter web test -- TaskCalendar` |
| 3 | `style(parent-calendar): drop non-current CSS rules + simplify grid container` | Task 2.1 + Task 2.2 | `pnpm --filter web typecheck` |
| 4 | `test(parent-calendar): rewrite TaskCalendar tests for single-month scenario + update e2e` | Task 3.1 + Task 3.2 + Task 3.3 | `pnpm --filter web test -- TaskCalendar ParentTasksPage` |
| 5 | `docs(openspec): patch delta spec for single-month visual semantics` | Task 4.1 | `grep -n "today 视觉" openspec/changes/.../spec.md` |
| 6 | `chore(parent-calendar): full verification (vitest + tsc + e2e + manual visual)` | Task 5.1-5.4 | `pnpm test` |

> 注：若团队偏好更细粒度，可将 #3 拆为 "drop CSS rules" + "simplify container" 两个 commit（对应 Task 2.1、2.2 各自独立）。

---

## 异常与回滚

### 编译失败（Task 5.2 阶段）

- **症状**：`pnpm --filter web typecheck` 报 `nextMonth` 未使用 / 类型不匹配
- **处理**：检查 Task 1.2 是否完整删除 `nextMonth` 引用（grep `nextMonth` 应返回 0）
- **回滚**：Phase 1 commit 暂存 → 修复后重 commit

### 单元测试失败（Task 5.1 阶段）

- **症状**：测试 RED 但错误信息不明
- **处理**：按 `describe` 块逐个排查是否仍有"双月"残留断言
- **回滚**：仅回滚 Task 3.1，重新写测试

### E2E 失败（Task 5.3 阶段）

- **症状**：playwright 找不到 `.calendar-panel` 单个 / 仍找到 2 个
- **处理**：先确认 Task 1.2 已生效 → 检查 e2e 的 locater selector
- **回滚**：Task 3.3 e2e 文件回滚

### 视觉不符（Task 5.4 阶段）

- **症状**：选中态/teal 实心 vs today 浅蓝边框未生效
- **处理**：检查 `<style>` 块的 CSS 选择器（`.task-calendar-current-month .ant-picker-cell-selected`）是否仍存在
- **回滚**：Phase 2 commit 回滚，重新审视 CSS 规则

---

## 总结

本计划共 **3 主阶段 + 5 子阶段 / 12 个原子任务**，每个任务满足：
- 单文件变更 ≤ 200 行
- 单次 commit 可独立验证
- 明确的目标 / 文件路径 / 验收点 / 提交信息

**变更面**：4 个文件（`TaskCalendar.tsx` / `TaskCalendar.test.tsx` / `parent-task-calendar.spec.ts` / `spec.md`），其中核心代码仅 `TaskCalendar.tsx` 单文件。

**回归保护**：Phase 5（vitest + tsc + e2e + 视觉）四重验证；父组件 `ParentTasksPage` 与后端契约零改动。

**回滚成本**：6 个 commit 可独立 revert；git history 保留双月版本代码作为参考。

**附录 · 与 tasks.md 对应关系**：

| tasks.md 子任务 | 本计划任务 | 状态 |
|---|---|---|
| 1.1 删除第二个 CalendarPanel | Task 1.2（主）+ Task 1.1（内部清理）| ✓ 覆盖 |
| 1.2 简化导航栏标题 | Task 1.2 | ✓ 覆盖 |
| 1.3 简化 CSS（删除 non-current） | Task 2.1 | ✓ 覆盖 |
| 1.4 简化 Grid 容器 | Task 2.2 | ✓ 覆盖 |
| 2.1 更新 TaskCalendar.test.tsx（删除双月） | Task 3.1 | ✓ 覆盖 |
| 2.2 添加单月场景断言 | Task 3.1 | ✓ 覆盖 |
| 3.1 单元测试 | Task 5.1 | ✓ 覆盖 |
| 3.2 类型检查 | Task 5.2 | ✓ 覆盖 |
| 3.3 E2E | Task 5.3 | ✓ 覆盖 |
| _（tasks.md 未列）_ Decision 5 删除 isCurrentMonth | Task 1.1 | ✓ 细化新增 |
| _（tasks.md 未列）_ Decision 6 简化 value | Task 1.1 | ✓ 细化新增 |
| _（tasks.md 未列）_ Decision 9 E2E 调整 | Task 3.3 | ✓ 细化新增 |
| _（tasks.md 未列）_ Decision 10 Spec Patch | Task 4.1 | ✓ 细化新增 |
| _（tasks.md 未列）_ Task 3.2 ParentTasksPage.test 校验 | Task 3.2 | ✓ 验证确认 |

---

## 任务完成状态

> 用于 `comet state task-checkoff` 验证。每行格式 `- [x] Task N.N: <唯一文本>`。

- [x] Task 1.1: 简化 CalendarPanel 内部（删除 isCurrentMonth + 简化 antd value + 硬编码 className）
- [x] Task 1.2: 删除第二个 CalendarPanel + 简化导航栏标题 + 删除 nextMonth
- [x] Task 2.1: 删除 .task-calendar-non-current-month-* CSS 规则
