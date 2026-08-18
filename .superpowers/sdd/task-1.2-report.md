# Task 1.2 Report: 删除第二个 CalendarPanel + 简化导航栏标题 + 删除 nextMonth

## 实现摘要

实现 Task 1.2 plan §1.2 三项原子改动 (`web/src/parent/components/TaskCalendar.tsx`):

| # | 改动 | 旧行号 | 新行号 | 行数变化 |
|---|---|---|---|---|
| 1 | 删除 `nextMonth = currentMonth.add(1, 'month')` | 343 | (n/a) | -1 |
| 2 | 简化导航栏标题 → 单月 | 407-409 | 407 | -2 / +1 |
| 3 | 删除第二个 `<CalendarPanel ... />` 块(含注释) | 421-437 | 421-426 | -10 / +0 |

净变更: 3 insertions / 14 deletions = **-11 行** (plan 估算 -16,实际 -11,因为注释更紧凑)。

### 最终 TaskCalendar 主组件关键代码片段

```tsx
  // 计算当前月
  const currentMonth = dayjs(baseMonth + '-01');
```

```tsx
        <Button onClick={() => onNavigate(-1)}>{'<'}</Button>
        <span>{currentMonth.format('YYYY年M月')}</span>
        <Button onClick={() => onNavigate(1)}>{'>'}</Button>
```

```tsx
        {/* 单月面板 */}
        <CalendarPanel
          year={year}
          month={month}
          selectedRange={selectedRange}
          onSelect={onSelect}
        />
```

> 未触碰范围:CSS `<style>` 块、CalendarPanel 子组件、测试文件、ParentTasksPage,
> `<` `>` 按钮的 `onNavigate(-1/1)` 调用、`task-calendar-grid` CSS 类名。

## 验证证据

### 1. RED 证据 (plan §Task 1.2 行 56-58 plan-accepted 中间态)

11 个 RED it 全部为 plan-accepted — 均为"依赖双月/双 panel/双月标题"的既存断言,
**最终 GREEN 由 Phase 3 Task 3.1 (测试全面重写) 完成**。

#### Task 1.2 本任务引入的 9 个新 RED

| RED 测试名 | 失败原因 |
|---|---|
| 渲染导航栏及月份标题 | `Unable to find element with text: '2026年7月 — 2026年8月'` (标题已简化) |
| 渲染两个日历面板（双月） | `mock-calendar-2026-08` 不存在 (只渲染 1 个 panel) |
| 月份标题可点击触发 SELECT_MONTH | `Found multiple elements with text: '2026年7月'` (导航标题 + CalendarHeader 重复) |
| 跨年时显示正确的月份标题 | `Unable to find element with text: '2026年12月 — 2027年1月'` |
| 第二个面板的 CalendarHeader 显示下月信息 | 找不到 `'2026年8月'` (第二个 panel 已删除) |
| 三级点击交互 (2.4) > 月份标题触发 SELECT_MONTH | 同上 — multiple `'2026年7月'` |
| useApi 数据获取 (2.5) > 单面板错误不影响另一面板 | `calendar-panel-2026-8` 不存在 |
| useApi 数据获取 (2.5) > 正常数据渲染日历 | `mock-calendar-2026-08` 不存在 |
| today 高亮由 selectedRange > 非当前月面板中 selectedRange=day | 找不到 `mock-calendar-2026-09` |

#### Task 1.1 残留的 2 个 RED (本任务未引入,既已 RED)

| RED 测试名 | 失败原因 |
|---|---|
| 默认选中今日 > 当前月面板:antd <Calendar> value 等于今日 | Task 1.1 删除 ternary 后 `value=monthDate='2026-07-01'` 而非 today='2026-07-24' |
| v2 视觉抑制 wrapper className > 非当前月面板 | Task 1.1 硬编码 className='task-calendar-current-month',不再有 'task-calendar-non-current-month' |

### 2. GREEN 证据 (单月相关 + data-testid 测试仍 GREEN)

**完整 vitest 输出（仅 TaskCalendar.test.tsx）**:

```
 Test Files  1 failed (1)
      Tests  11 failed | 38 passed (49)
```

**GREEN 数据统计**:
- TaskCalendar.test.tsx 共 **49** 个 it
- **38 个 GREEN** (含 `<` `>` 按钮点击、WeekNumberColumn 6 行 + 几何对齐、computeWeekNumbers、任务徽章、loading/error/refetch、selectedRange 三级点击交互、selected/today 数据属性等所有 data-testid 断言)
- **11 个 RED** (如上分类:9 个本任务新引入 + 2 个 Task 1.1 残留)

**关键 GREEN 测试样本（抽样证明无回归）**:

| 测试名 | 状态 | 验证什么 |
|---|---|---|
| 点击 < 按钮触发 onNavigate(-1) | ✓ GREEN | `<` 按钮触发 `onNavigate(-1)` |
| 点击 > 按钮触发 onNavigate(1) | ✓ GREEN | `>` 按钮触发 `onNavigate(1)` |
| 渲染 6 行周号 | ✓ GREEN | data-testid `week-column-${year}-${month}` 仍渲染 6 行 |
| computeWeekNumbers 返回正确结构 | ✓ GREEN | 纯函数结果不变 |
| spacer 高度精确匹配 antd Calendar 表头几何 | ✓ GREEN | geometric alignment 不变 |
| 周号列底部预留 8 px | ✓ GREEN | 不变 |
| total > 0 时显示任务数角标 | ✓ GREEN | data-testid `task-badge-*` 仍渲染 |
| 选中周时:周号行有蓝色边框 | ✓ GREEN | 不变 |
| 选中一天时:该天所在周号行高亮 | ✓ GREEN | 不变 |
| loading 状态显示 Spin | ✓ GREEN | CalendarPanel 子组件不变 |
| error 状态显示 Alert 和重试按钮 | ✓ GREEN | CalendarPanel 子组件不变 |
| 点击重试触发 refetch | ✓ GREEN | CalendarPanel 子组件不变 |
| 当前月面板:CalendarPanel wrapper 包含 task-calendar-current-month | ✓ GREEN | wrapper className 不变 |
| 当前月面板中 type=week 时周号行有蓝色边框 (baseMonth=2026-08) | ✓ GREEN | baseMonth=8 时仍渲染 1 个 calendar-panel-2026-8 |
| selectedRange=week(本周) 时当前周号行 (week-row-30) 有蓝色边框 | ✓ GREEN | 单月下本周高亮仍工作 |

### 3. lint 命令

```bash
$ pnpm --filter web lint
```

**输出**:
```
src/parent/components/TaskCalendar.tsx(1,1): error TS6133: 'React' is declared but its value is never read.
src/parent/components/TaskTypeConfigForms.tsx(241,29): error TS6198: All destructured elements are unused.
src/parent/components/__tests__/TaskCalendar.test.tsx(4,1): error TS6133: 'dayjs' is declared but its value is never read.
src/parent/components/__tests__/TaskCalendar.test.tsx(40,41): error TS6133: 'rest' is declared but its value is never read.
src/parent/components/__tests__/TaskCalendar.test.tsx(535,5): error TS2304: Cannot find name 'afterEach'.
src/parent/components/__tests__/TaskCalendar.test.tsx(764,5): error TS2304: Cannot find name 'afterEach'.
src/parent/pages/__tests__/ParentTasksPage.test.tsx(3,1): error TS6133: 'dayjs' is declared but its value is never read.
src/parent/pages/__tests__/ParentTasksPage.test.tsx(38,46): error TS6133: 'onSelect' is declared but its value is never read.
src/parent/pages/__tests__/ParentTasksPage.test.tsx(38,56): error TS6133: 'onNavigate' is declared but its value is never read.
src/parent/pages/__tests__/ParentTasksPage.test.tsx(60,8): error TS6133: 'CalendarAction2' is declared but its value is never read.
src/parent/pages/index.tsx(1,60): error TS6133: 'useCallback' is declared but its value is never read.
src/parent/pages/index.tsx(33,34): error TS6196: 'CalendarAction' is declared but its value is never used.
src/parent/pages/index.tsx(1510,24): error TS2783: 'type' is specified more than once, so this usage will be overwritten.
src/parent/pages/index.tsx(1547,27): error TS2551: Property 'snapshotTemplateTypeConfig' does not exist on type 'TaskAssignment'. Did you mean 'snapshotTemplateTaskType'?
```

**与 baseline 对比验证（lint baseline = git stash + lint,再 git stash pop）**: 14 个错误**完全一致** —— Task 1.2 **未引入任何新 lint 错误**。
Task 1.1 报告已确认 `React` line 1 是预存错误,与本任务无关。

### 4. Files changed (git diff --stat)

```
$ git diff --stat web/src/parent/components/TaskCalendar.tsx
 web/src/parent/components/TaskCalendar.tsx | 17 +++--------------
 1 file changed, 3 insertions(+), 14 deletions(-)
```

单文件变更 ≤ 200 行 (实际 11 行净减) ✓
未触碰 CSS `<style>` 块、CalendarPanel 子组件、测试文件、ParentTasksPage ✓

### 5. Commit 信息

```
$ git log --oneline -3
b724c47 feat(parent-calendar): 单月化 TaskCalendar 主组件 (删除 2nd panel + 单月标题)
b58c8c1 chore(sdd): checkoff Task 1.1 (CalendarPanel simplify)
a6c1b43 refactor(parent-calendar): 简化 CalendarPanel 内部 — 删除 isCurrentMonth + 硬编码 className
```

**Task 1.2 commit short SHA: `b724c47`**

## Self-review findings

### Completeness ✓
- `nextMonth` 完全删除 (`grep nextMonth web/src/parent/components/TaskCalendar.tsx` 返回 No files found)
- 标题文本仅显示单月 (`{currentMonth.format('YYYY年M月')}`,无 ` — YYYY年M月`)
- TaskCalendar 仅渲染 1 个 `<CalendarPanel>` 调用 (line 421)
- `<` `>` 按钮仍存在并触发 `onNavigate(-1) / onNavigate(1)` (line 406, 408)

### Quality ✓
- 命名清晰 (`currentMonth` 取代 `nextMonth` 后剩余命名合理)
- 代码干净 (无 YAGNI、未引入新依赖、未引入新函数)

### Discipline ✓
- 未触碰 CalendarPanel 内部 (Task 1.1 范围)
- 未触碰 `<style>` CSS 块 (Task 2.1 / 2.2 范围)
- 未触碰测试文件 (Task 3.1 范围)
- 未触碰 ParentTasksPage (no changes)
- 跟随现有代码风格 (中文注释、JSX 缩进、import 顺序)

### Testing ✓
- 本任务不写新测试 (strict plan scope: 测试文件全留给 Task 3.1)
- RED/GREEN 证据完整 (11 RED + 38 GREEN, 与 baseline 2 RED + 47 GREEN 对比,新增 9 RED)
- 所有 GREEN 测试断言基于 data-testid / DOM attribute, 不依赖 panel 数量

## Concerns

### 1. 已知 9 个测试 RED 是 plan-accepted 中间态

Plan §Task 1.2 行 56-58 显式声明这是 plan-mandated 现象:
"双月相关 it (如 '渲染两个日历面板') 变 RED;其它 GREEN"

最终 GREEN 由 Phase 3 Task 3.1 (全面重写测试) 完成。

### 2. "Found multiple elements with text: '2026年7月'" 的副作用 (新增 RED)

删除第二个 panel 后,`'2026年7月'` 文本同时出现在:
- 导航栏 `<span>{currentMonth.format('YYYY年M月')}</span>` (本次简化产物)
- CalendarHeader `<div>{year}年{month}月</div>` (line 75,既有)

`getByText('2026年7月')` 现返回 2 个元素 → `'月份标题可点击触发 SELECT_MONTH'` 测试 RED。
这表明 CalendarHeader 与导航标题的单击语义容易混淆 —— 后续 Task 3.1 (测试重写) 时,
需要明确选择哪个 selector (可能是 `getByRole('button')` 或某个 data-testid)。

### 3. lint 命令产出 14 个预存错误 (与本任务无关)

`pnpm --filter web lint` 在 baseline (git stash 后) 也产生**完全相同**的 14 个错误,
本任务未引入新错误。Task 1.1 报告已确认 `TaskCalendar.tsx(1,1) 'React' 未使用` 是预存错误。

### 4. CSS Grid 容器尚未简化 (Task 2.2 范围,已留 TODO)

本任务保留 `display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(400px, 1fr))'` (line 414-417)
和 `@media (max-width: 767px)` 媒体查询 (line 349-351),因为这是 Task 2.2 的范围。
Phase 2 任务会进一步简化。

### 5. `.task-calendar-non-current-month-*` CSS 规则尚未清理 (Task 2.1 范围)

本任务保留所有 5 条 `non-current-month` CSS 规则,虽然现在没有任何元素匹配该选择器
(Task 1.1 已硬编码 className 为 `task-calendar-current-month`)。
Phase 2 Task 2.1 会清理。

## 风险信号自报

| 风险信号 | 命中? | 说明 |
|---|---|---|
| 跨模块/跨子系统协调改动 | ✗ | 单文件 (`TaskCalendar.tsx`) 3 处局部修改,无跨模块调用 |
| 安全敏感面 | ✗ | 纯 UI 渲染层,与认证、授权、加密、SQL、外部输入、凭证无关 |
| 并发、锁、共享可变状态 | ✗ | 无状态变更,仅删除 DOM 子树和局部变量 |
| 数据或 schema 迁移 | ✗ | 无 API 契约变更,无数据库 schema 变更 |
| 公共 API 契约或对外接口变更 | ✗ | TaskCalendar props (`baseMonth`/`selectedRange`/`onSelect`/`onNavigate`) 不变 |
| 单任务 diff 超过 200 行 | ✗ | 实际 11 行净减,远 ≤ 200 |
| implementer 自报 DONE_WITH_CONCERNS | ✗ | 实现符合 plan 与 brief,RED 是 plan-accepted 中间态,非实现错误 |

**总结**:无风险信号命中。本次任务在 plan 严格定义的边界内完成,所有 9 个 RED 均为 plan 预期的中间态。

## 验收标准对照

| plan 验收标准 | 状态 |
|---|---|
| `TaskCalendar` 渲染时仅包含 1 个 `<CalendarPanel>` 调用 | ✓ (line 421,单 panel) |
| 导航栏标题文本为单月格式 | ✓ (`{currentMonth.format('YYYY年M月')}`) |
| `<` `>` 按钮仍存在并触发 `onNavigate(-1) / onNavigate(1)` | ✓ (line 406, 408) |
| 无 lint 新错误 | ✓ (与 baseline 完全一致) |
| RED 状态符合 plan-accepted | ✓ (9 新 RED + 2 残留 RED,共 11) |
| GREEN (data-testid) 仍 GREEN | ✓ (38 GREEN) |
| 单文件变更 ≤ 200 行 | ✓ (-11 净行) |
| 未触碰范围外代码 | ✓ (CalendarPanel/CSS/test/ParentTasksPage 全部未动) |
