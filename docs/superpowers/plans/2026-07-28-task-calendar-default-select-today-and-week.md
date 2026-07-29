---
change: tweak-calendar-selected-day-today-style
design-doc: docs/superpowers/specs/2026-07-28-task-calendar-default-select-today-and-week-design.md
base-ref: 263361cd61a6d6d182730c8c0ba5713da34344f7
---

# Implementation Plan: 任务日历默认选中今日与本周

## Summary

在 commit `263361c`（tweak 阶段已完成 selected 视觉：深 teal 实心 + 白字 + 浅蓝外环）基础上，本 build 阶段扩展两项能力：

1. **selectedRange 初值从 type=day+today 改为 type=week+本周**（决策 1）
2. **today cell 固定高亮**：dateCellRender.isSelected 增加 today 独立判定，与 selectedRange.type 无关（决策 2）

TDD 风格：每个实现任务前先有 RED 测试任务。任务粒度确保单次实施会话可完成。

---

## Pre-flight Checklist

- [x] `git status` clean（仅 plan 文件未跟踪）
- [x] 当前 HEAD = `263361cd61a6d6d182730c8c0ba5713da34344f7`（注：build 完成后 HEAD 已推进到 504286e/eec8d76，base-ref 保留创建 plan 时的快照）
- [x] `pnpm --filter web test -- TaskCalendar ParentTasksPage` 当前全绿（除 1 条 pre-existing today 日期漂移）
- [x] 阅读 Design Doc 全部 6 个决策 + Risks/Trade-offs

---

## Phase 1: RED Tests — 断言先行

### Task 1.1: 修改 ParentTasksPage.test.tsx selectedRange 初值断言

**目标**：将 `data-selected` 断言从 `2026-07-24_2026-07-24`（type=day 单点）更新为 `2026-07-20_2026-07-26`（type=week 本周范围），使测试在实现前 RED。

**范围**：

- 文件：`web/src/parent/pages/__tests__/ParentTasksPage.test.tsx`
- 行号：`378–381`

**依赖**：无

**操作**：

```
// 旧（line 381）：
expect(mockCalendar.getAttribute('data-selected')).toBe('2026-07-24_2026-07-24');

// 新：
// systemTime = 2026-07-24（周五），startOf('week') = 2026-07-20（周一，dayjs 默认周日为周首 → 需确认 locale）
// 实际：使用 dayjs weekday 插件，startOf('week') 默认 Sunday。2026-07-24 周五 → 当周 7/19(日)~7/25(六)
// 或 Chinese locale 下周一为周首 → 7/20(一)~7/26(日)
// 取决于 dayjs locale 设置。先按 locale 默认 Sunday 周首写入：
expect(mockCalendar.getAttribute('data-selected')).toBe('2026-07-19_2026-07-25');
```

> **注意**：需要先确认项目中 dayjs locale 具体设置。若未显式设置 locale，`startOf('week')` 默认 Sunday。`shared/dayjs.ts` 未调用 `dayjs.locale('zh-cn')`，故周首为 Sunday。
>
> `2026-07-24` 周五 → Sunday 周首 → startOf('week') = `2026-07-19` → endOf('week') = `2026-07-25`。

**验证**：

- `pnpm --filter web test -- ParentTasksPage` → **RED**（type=day + todayStr=`2026-07-24` 与 `2026-07-19_2026-07-25` 不匹配）

**回滚**：`git checkout -- web/src/parent/pages/__tests__/ParentTasksPage.test.tsx`

---

### Task 1.2: 新增 TaskCalendar.test.tsx 用例 — 默认本周 today 固定高亮

**目标**：新增 2 条测试用例，在实现前 RED：

- (a) 默认进入（selectedRange=本周 + today 在当周）→ today cell `data-selected='true'` + 深 teal 视觉
- (b) 用户点选非今天日期后 → today cell 仍高亮 + 被点日期也高亮

**范围**：

- 文件：`web/src/parent/components/__tests__/TaskCalendar.test.tsx`
- 插入位置：`describe('周/日高亮改用半透明浅蓝背景矩形')` 末尾（line 825 `});` 之前）新增子 `describe`

**依赖**：Task 1.1 已完成（确保 selectedRange 初值变更不会遗漏回归）

**操作**：新增 `describe('默认选中本周: today 固定高亮 (tweak-build)')` 块，含以下 it：

```typescript
describe('默认选中本周: today 固定高亮 (tweak-build)', () => {
  // (a) 默认进入页面: selectedRange=本周, today 应高亮
  it('selectedRange=week(本周) 时 today cell 仍高亮 (深 teal + 白字 + 浅蓝外环)', () => {
    render(
      <TaskCalendar
        {...defaultProps}
        baseMonth="2026-07"
        selectedRange={{
          type: 'week',
          startDate: '2026-07-19',  // Sunday 周首
          endDate: '2026-07-25',    // Saturday 周末
        }}
      />,
    );
    // today = 2026-07-24 (systemTime 设置的周五)
    const todayCell = within(julyPanel()).getByTestId('date-cell-24');
    const inner = todayCell.querySelector('[data-bg]') as HTMLElement;
    expect(inner.getAttribute('data-selected')).toBe('true');
    expect(inner.style.backgroundColor).toBe('rgb(13, 148, 136)');
    expect(inner.style.color).toBe('rgb(255, 255, 255)');
    expect(inner.style.boxShadow).toContain('#93c5fd');
  });

  // (b) 用户点选非今天日期: today 仍高亮 + 被选日期高亮
  it('type=week 下点击某非 today 日期后 today 仍高亮', () => {
    render(
      <TaskCalendar
        {...defaultProps}
        baseMonth="2026-07"
        selectedRange={{
          type: 'day',
          startDate: '2026-07-15',  // 用户点的非 today 日期
          endDate: '2026-07-15',
        }}
      />,
    );
    // 2026-07-15 应选中
    const clickedCell = within(julyPanel()).getByTestId('date-cell-15');
    const clickedInner = clickedCell.querySelector('[data-bg]') as HTMLElement;
    expect(clickedInner.getAttribute('data-selected')).toBe('true');

    // today (2026-07-24) 也应高亮
    const todayCell = within(julyPanel()).getByTestId('date-cell-24');
    const todayInner = todayCell.querySelector('[data-bg]') as HTMLElement;
    expect(todayInner.getAttribute('data-selected')).toBe('true');
    expect(todayInner.style.backgroundColor).toBe('rgb(13, 148, 136)');
    expect(todayInner.style.color).toBe('rgb(255, 255, 255)');
  });
});
```

> **注意**：测试需使用 `vi.useFakeTimers()` + `vi.setSystemTime(new Date('2026-07-24T12:00:00'))` 固定 today。检查文件是否已有全局 beforeEach 设置 systemTime。

**验证**：

- `pnpm --filter web test -- TaskCalendar` → **RED**（current isSelected 仅检查 type=day，不识别 today 独立高亮；case (b) 单击非今天日期后 today 不高亮）

**回滚**：删除新增的 describe 块

---

### Task 1.3: 新增 TaskCalendar.test.tsx 用例 — 当前周号行默认高亮

**目标**：验证 selectedRange 初值为 week 时，当前周号行 visual 高亮（rgba 浅蓝背景）。

**范围**：同上文件，与 Task 1.2 同一 describe 块内追加

**依赖**：Task 1.2

**操作**：追加 it：

```typescript
it('selectedRange=week(本周=第30周) 时周号行高亮 (半透明浅蓝背景)', () => {
  render(
    <TaskCalendar
      {...defaultProps}
      baseMonth="2026-07"
      selectedRange={{
        type: 'week',
        startDate: '2026-07-19',
        endDate: '2026-07-25',
      }}
    />,
  );
  // 2026-07-19~07-25 对应第 30 周（周日周首）
  const weekRow = within(julyPanel()).getByTestId('week-row-30') as HTMLElement;
  expect(weekRow.getAttribute('data-selected')).toBe('true');
  expect(weekRow.style.backgroundColor).toBe('rgba(22, 119, 255, 0.18)');
});
```

**验证**：

- `pnpm --filter web test -- TaskCalendar` → **RED** 或 **GREEN**（取决于 2026-07-19~07-25 的周编号是否精确匹配到 `week-row-30`。实际需根据 dayjs weekOfYear 插件验证；此 case 主要验证 selectedRange=week 时 week-row 高亮行为不变，其视觉已在 fix-calendar-week-row-highlight-bg 中覆盖）

> **注**：若 weekNum 不确定（dayjs 周编号与 antd Calendar 周编号可能不一致），此条可降级为：保持现有 week-row 测试通过即可；新增 today 高亮测试覆盖主要新行为。

**回滚**：删除新增的 it

---

### Task 1.4: RED 确认

**目标**：运行所有新增/修改测试，确认 RED 失败符合预期。

**范围**：

- `pnpm --filter web test -- TaskCalendar ParentTasksPage`

**依赖**：Task 1.1, 1.2, 1.3

**验证**：

- Task 1.1 → RED（selectedRange 初值不匹配）
- Task 1.2 → RED（today 不高亮）
- Task 1.3 → 待定（depends on weekNum 匹配）
- 所有既存用例保持通过（除 pre-existing today 日期漂移）

**回滚**：N/A（确认阶段）

---

## Phase 2: GREEN Implementation — 代码修改

### Task 2.1: 修改 selectedRange 初值为 week 类型

**目标**：`ParentTasksPage` useReducer 初值 `selectedRange` 从 `{ type: 'day', today, today }` 改为 `{ type: 'week', weekStart, weekEnd }`。

**范围**：

- 文件：`web/src/parent/pages/index.tsx`
- 行号：`1041–1051`（ParentTasksPage 函数体顶部）

**依赖**：Task 1.4（RED 确认完成）

**操作**：

```typescript
// 旧（lines 1043-1051）：
const now = dayjs();
const todayStr = now.format("YYYY-MM-DD");
const [calendarState, dispatch] = useReducer(calendarReducer, {
  baseMonth: now.format("YYYY-MM"),
  selectedRange: { type: "day", startDate: todayStr, endDate: todayStr },
  taskTypeFilters: ["LIMITED", "REPEAT", "STANDING"],
  viewAllMode: false,
});

// 新：
const now = dayjs();
const weekStart = now.startOf("week").format("YYYY-MM-DD");
const weekEnd = now.endOf("week").format("YYYY-MM-DD");
const [calendarState, dispatch] = useReducer(calendarReducer, {
  baseMonth: now.format("YYYY-MM"),
  selectedRange: { type: "week", startDate: weekStart, endDate: weekEnd },
  taskTypeFilters: ["LIMITED", "REPEAT", "STANDING"],
  viewAllMode: false,
});
```

> **dayjs 周首确认**：`shared/dayjs.ts` 未调用 `dayjs.locale()`，故默认 `startOf('week')` = Sunday。`endOf('week')` = Saturday。

**验证**：

- `pnpm --filter web test -- ParentTasksPage` → Task 1.1 **GREEN**（data-selected 断言匹配 `2026-07-19_2026-07-25`）
- Visual：`baseMonth` 仍是 `now.format('YYYY-MM')` 不变

**回滚**：revert 回 `{ type: 'day', startDate: todayStr, endDate: todayStr }`

---

### Task 2.2: 修改 dateCellRender.isSelected 增加 today 独立判定

**目标**：`isSelected` 从仅 type=day 判定扩展为：`(type=day 单点) || (isCurrentMonth && date==today)`。

**范围**：

- 文件：`web/src/parent/components/TaskCalendar.tsx`
- 行号：`267–277`（`// 选中高亮` 注释块 + `isSelected` 计算）

**依赖**：Task 2.1

**操作**：

```typescript
// 旧（lines 267-277）：
// 选中高亮：仅 selectedRange.type === 'day' 时,日期 cell 标记为选中;
// 视觉与 antd 内置 today 高亮对齐 (深 teal 实心 + 白字 + 浅蓝外环)。
// 周选中时,周内日期 cell 不跟随高亮(产品要求:周选中只高亮周号行,
// 日期 cell 全部 data-selected='false')。
const dateStr = date.format("YYYY-MM-DD");
const isSelected = !!(
  selectedRange &&
  selectedRange.type === "day" &&
  dateStr === selectedRange.startDate &&
  dateStr === selectedRange.endDate
);

// 新：
// 选中高亮判定（tweak-calendar-selected-day-today-style build 阶段）：
//   1. selectedRange.type === 'day' 且为单点匹配 → 用户手动选中某一天
//   2. 当天日期（today）在当前月面板内 → 固定焦点高亮，与 selectedRange.type 无关
// 视觉与 antd 内置 today 高亮对齐 (深 teal 实心 + 白字 + 浅蓝外环)。
// 周选中时,周内日期 cell 不跟随高亮(产品要求:周选中只高亮周号行),
// 但 today cell 在周选中仍高亮（today 固定焦点）。
const dateStr = date.format("YYYY-MM-DD");
const today = dayjs();
const todayStr = today.format("YYYY-MM-DD");
const isCurrentMonthForDate =
  today.year() === year && today.month() + 1 === month;
const isSelected =
  !!(
    selectedRange &&
    selectedRange.type === "day" &&
    dateStr === selectedRange.startDate &&
    dateStr === selectedRange.endDate
  ) ||
  (isCurrentMonthForDate && dateStr === todayStr);
```

> **Perf 注意**：`isCurrentMonthForDate` + `todayStr` 在每次 `dateCellRender` 调用时计算。「不引入 useMemo —— 该回调每次渲染被调用 42 次（双月 x 2），计算量极小；过早优化违背 YAGNI。」

**验证**：

- `pnpm --filter web test -- TaskCalendar` → Task 1.2 **GREEN**（today 固定高亮 + selectedRange=week 时 today 仍高亮）
- 所有既存 selected 视觉测试保持 GREEN（深 teal + 白字 + 浅蓝外环）
- Week-row 高亮测试保持 GREEN

**回滚**：revert 回纯 type=day 判定

---

## Phase 3: Full Verification — 全量确认

### Task 3.1: TaskCalendar 测试全量通过

**目标**：运行 TaskCalendar 全部测试，确认无回归。

**操作**：

```bash
pnpm --filter web test -- TaskCalendar
```

**验证**：

- 新增用例（Task 1.2, 1.3）GREEN
- 修改用例（Task 1.1 不在此文件）N/A
- 既有用例（lines 671-825）GREEN
- 已知 pre-existing 失败 1 条（today 日期漂移）仍失败（列入 known-issues）

**Fail-safe**：若发现意外回归，git diff 排查 → 回滚单文件

---

### Task 3.2: ParentTasksPage 测试全量通过

**目标**：运行 ParentTasksPage 全部测试，确认 selectedRange 初值变更无回归。

**操作**：

```bash
pnpm --filter web test -- ParentTasksPage
```

**验证**：

- `日历 mock 在初次渲染时 data-selected 等于本周` → GREEN（weekStart_weekEnd）
- `日历 mock 在初次渲染时 data-base-month 等于本月` → GREEN（不变）
- calendarReducer 纯函数测试全 16 条 → GREEN（reducer 逻辑不变）
- 组件渲染测试全 10+ 条 → GREEN

---

### Task 3.3: 全量 web 测试

**目标**：运行 web 模块全量测试，确认无跨模块回归。

**操作**：

```bash
pnpm --filter web test
```

**验证**：

- 全量通过，仅已知 pre-existing 失败 1 条
- 无新增失败

---

### Task 3.4: E2E 测试

**目标**：运行 parent-task-calendar E2E 测试，确认 Playwright 流程不依赖具体色值。

**操作**：

```bash
pnpm test:e2e -- parent-task-calendar
```

**验证**：

- 全量通过（本 change 不改变 DOM 结构，仅变更 selectedRange 初值 + isSelected 判定；E2E 不依赖色值）

**Fail-safe**：若 E2E 依赖了 `data-selected` 属性初值（type=day 预期），需同步更新 E2E 断言

---

## Phase 4: Polish & Commit

### Task 4.1: Prettier 格式化

**目标**：确保所有修改文件通过 prettier。

**操作**：

```bash
npx prettier --write web/src/parent/pages/index.tsx web/src/parent/components/TaskCalendar.tsx \
  web/src/parent/pages/__tests__/ParentTasksPage.test.tsx \
  web/src/parent/components/__tests__/TaskCalendar.test.tsx
```

**验证**：prettier 无错误输出

---

### Task 4.2: 提交代码

**目标**：提交所有修改，commit message 反映 build 阶段内容。

**操作**：

```bash
git add web/src/parent/pages/index.tsx \
        web/src/parent/components/TaskCalendar.tsx \
        web/src/parent/pages/__tests__/ParentTasksPage.test.tsx \
        web/src/parent/components/__tests__/TaskCalendar.test.tsx

git commit -m "feat(build): 默认选中本周 + today 固定高亮

- selectedRange 初值: type=week + 本周范围 (Decision 1)
- dateCellRender.isSelected: today 独立判定,与 selectedRange.type 无关 (Decision 2)
- today 仅当前月面板高亮 (Decision 4)
- 更新 ParentTasksPage.test.tsx selectedRange 初值断言
- 新增 TaskCalendar.test.tsx today 固定高亮用例"
```

**验证**：

- `git log --oneline -1` 显示新 commit
- `git diff HEAD~1 --stat` 确认 4 个文件

---

### Task 4.3: 更新 tasks.md

**目标**：在 `openspec/changes/tweak-calendar-selected-day-today-style/tasks.md` 追加 build 阶段完成条目。

**操作**：在 tasks.md 末尾追加：

```markdown
## 4. build 阶段: 默认选中本周 + today 固定高亮

- [x] 4.1 修改 ParentTasksPage.test.tsx selectedRange 初值断言 (RED)
- [x] 4.2 新增 TaskCalendar.test.tsx today 固定高亮用例 (RED)
- [x] 4.3 修改 pages/index.tsx selectedRange 初值为 type=week
- [x] 4.4 修改 TaskCalendar.tsx isSelected 增加 today 独立判定
- [x] 4.5 运行全量 web 测试确认 GREEN
- [x] 4.6 提交代码 + prettier
```

---

## Phase 5: fix-build 回归修复

### Task 5.1: 修复 today 独立判定 bug

**目标**：删除 dateCellRender.isSelected 中的 today 独立判定分支，today 高亮完全由 selectedRange 范围决定。

**范围**：

- 文件：`web/src/parent/components/TaskCalendar.tsx`
- 行号：`271–291`（`isSelected` 计算）

**操作**：

```typescript
// 旧（有 today 独立判定分支）：
const isSelected = !!(
  (selectedRange && selectedRange.type === 'day' && ...) ||
  (isCurrentMonthForDate && dateStr === todayStr)
);

// 新（fix-build：无 today 独立分支）：
const isSelected = !!(
  selectedRange &&
  isCurrentMonthForDate &&
  ((selectedRange.type === 'day' && dateStr === selectedRange.startDate && dateStr === selectedRange.endDate) ||
    ((selectedRange.type === 'week' || selectedRange.type === 'month') &&
      dateStr === todayStr &&
      dateStr >= selectedRange.startDate &&
      dateStr <= selectedRange.endDate))
);
```

**验证**：`pnpm --filter web test -- TaskCalendar` → 53 GREEN

---

### Task 5.2: 修复 antd 内置 selected 背景叠加 bug（当前月也需抑制）

**目标**：CSS 抑制从仅非当前月扩展到当前月，避免 teal 双层叠加。

**范围**：

- 文件：`web/src/parent/components/TaskCalendar.tsx`
- 行号：`419–429`（`<style>` 块）

**操作**：添加 `.task-calendar-current-month` 选择器覆盖。

**验证**：`pnpm --filter web test -- TaskCalendar` → 53 GREEN

---

### Task 5.3: 修复 WeekNumberColumn.isSelected 支持 day 类型

**目标**：day 选中时该天所在周号行也高亮。

**范围**：

- 文件：`web/src/parent/components/TaskCalendar.tsx`
- 行号：`154–162`（`isSelected` 计算）

**操作**：

```typescript
// 旧：
const isSelected = selectedRange && selectedRange.type === 'week' && ...;

// 新：
const isSelected = !!(
  selectedRange &&
  ((selectedRange.type === 'week' && range overlap) ||
    (selectedRange.type === 'day' && 该天属于此周))
);
```

**验证**：`pnpm --filter web test -- TaskCalendar` → 53 GREEN

---

### Task 5.4: 更新测试

**目标**：删除/修改不再适用的 today 独立判定用例，新增 fix-build 对应用例。

**范围**：

- 文件：`web/src/parent/components/__tests__/TaskCalendar.test.tsx`

**操作**：

- 删除「selected 与 today 重合」测试
- 更新 week-row 高亮测试：day 选中时该天所在周高亮
- 新增「day 选中时 today 不高亮（非 today）」测试
- 新增「非当前月面板 selected cell 不高亮」测试

**验证**：`pnpm --filter web test -- TaskCalendar` → 53 GREEN

---

### Task 5.5: 更新 Design Doc + Delta Spec + Plan

**目标**：文档同步更新 fix-build 决策变更。

**操作**：

- Design Doc Decision 2 改为「selectedRange 范围判定（无 today 独立分支）」
- Design Doc Decision 4 改为「范围匹配走 isCurrentMonthForDate 守卫」
- Delta Spec Scenario 2 改为「today 不再单独高亮」
- Delta Spec 新增 Scenario 5「day 选中时周号行也高亮」
- Plan 追加 Phase 5

---

### Task 5.6: Prettier + Commit

**操作**：

```bash
prettier --write web/src/parent/components/TaskCalendar.tsx web/src/parent/components/__tests__/TaskCalendar.test.tsx
git add web/src/parent/components/TaskCalendar.tsx web/src/parent/components/__tests__/TaskCalendar.test.tsx docs/superpowers/specs/*.md openspec/changes/tweak-calendar-selected-day-today-style/specs/parent-task-calendar/spec.md docs/superpowers/plans/*.md
git commit -m "fix(build): 修复回归 bug (today 取消独立判定 / 抑制 antd 内置背景 / 严格仅当前月 / day 选中周高亮)"
```

**验证**：`git log --oneline -1` 显示新 commit

---

## File Manifest

| 文件                                                        | 操作 | 变更内容                                 |
| ----------------------------------------------------------- | ---- | ---------------------------------------- |
| `web/src/parent/pages/index.tsx`                            | 修改 | L1043-1051: selectedRange 初值 type=week |
| `web/src/parent/components/TaskCalendar.tsx`                | 修改 | L267-277: isSelected 增加 today 判定     |
| `web/src/parent/pages/__tests__/ParentTasksPage.test.tsx`   | 修改 | L381: data-selected 断言更新             |
| `web/src/parent/components/__tests__/TaskCalendar.test.tsx` | 修改 | L825 前插入 today 固定高亮 describe      |
| `openspec/changes/.../tasks.md`                             | 修改 | 追加 build 阶段完成清单                  |

---

## Rollback Plan

单 commit revert 即可，影响范围 4 文件 + tasks.md：

```bash
git revert HEAD
```

若已推送：`git revert HEAD && git push`

---

## Known Issues

- **TaskCalendar.test.tsx:596** — today 日期漂移（hardcoded `2026-07-24`）。pre-existing，不在此 change 范围内。
- **dayjs week start** — 项目未显式设置 locale，`startOf('week')` 默认 Sunday。若未来设置 `dayjs.locale('zh-cn')`，周首将变为 Monday，需同步更新测试断言。

---

## References

- Design Doc: `docs/superpowers/specs/2026-07-28-task-calendar-default-select-today-and-week-design.md`
- Delta Spec: `openspec/changes/tweak-calendar-selected-day-today-style/specs/parent-task-calendar/spec.md`
- Current tasks.md: `openspec/changes/tweak-calendar-selected-day-today-style/tasks.md`
- Base commit: `263361cd61a6d6d182730c8c0ba5713da34344f7` (tweak: 选中日视觉与默认当前日对齐)
