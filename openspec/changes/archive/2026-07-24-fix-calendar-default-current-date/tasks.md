# Tasks: fix-calendar-default-current-date

## 1. RED — 新增失败回归测试

- [x] 1.1 在 `web/src/parent/components/__tests__/TaskCalendar.test.tsx` mock `<Calendar>` 中增加 `data-value` 暴露：当 mock 渲染时把传入的 `value.format('YYYY-MM-DD')` 写入 host div 的 `data-value` 属性。
- [x] 1.2 在 `web/src/parent/components/__tests__/TaskCalendar.test.tsx` 末尾新增 describe 块「默认选中今日（回归）」：断言当 `selectedRange={ type: 'day', startDate: '2026-07-24', endDate: '2026-07-24' }`、`baseMonth='2026-07'`、测试沙箱真实 today=2026-07-24 时：
  - 七月面板 mock Calendar 的 `data-value === '2026-07-24'`（RED 证据：修复前 `monthDate='2026-07-01'`，断言失败）。
  - 八月面板 mock Calendar 的 `data-value === '2026-08-01'`（非当前月回退到 monthDate，保持显示月不变）。
  - 七月面板 `date-cell-24` 的 `data-selected === 'true'`。
- [x] 1.3 在 `web/src/parent/pages/__tests__/ParentTasksPage.test.tsx` 末尾新增 describe 块「ParentTasksPage 默认初始化 selectedRange 为今日」：渲染 `<ParentTasksPage />`，断言 mock TaskCalendar 的 `data-selected === '2026-07-24_2026-07-24'`（RED 证据：修复前 init `selectedRange: null`，`data-selected === ''`，断言失败）。
- [x] 1.4 运行 `pnpm --filter web test`：1.2、1.3 各失败 1 条 / 失败信息符合 RED 假设。

## 2. 修复 `web/src/parent/pages/index.tsx` — 初始化 selectedRange 为今日

- [x] 2.1 修改 `ParentTasksPage` 的 `useReducer` 初值：把 `selectedRange: null` 改成 `selectedRange: { type: 'day', startDate: now.format('YYYY-MM-DD'), endDate: now.format('YYYY-MM-DD') }`。`baseMonth`/`taskTypeFilters`/`viewAllMode` 不动。
- [x] 2.2 不运行整文件 prettier（避免无关 import 重排导致 diff 膨胀，仅以 targeted change 提交）；新增注释与现有风格一致。
- [x] 2.3 运行 `pnpm --filter web test`：1.3 新增测试转绿，33 个旧测试仍通过。

## 3. 修复 `web/src/parent/components/TaskCalendar.tsx` — 当前月面板 value 改为今日

- [x] 3.1 在 `CalendarPanel` 内 `monthDate` 计算后追加 `today`、`isCurrentMonth`、`calendarValue`。最终采用：
  ```tsx
  const today = dayjs();
  const isCurrentMonth = today.year() === year && (today.month() + 1) === month;
  const calendarValue = isCurrentMonth ? today : monthDate;
  ```
  （回退到 `monthDate` 即可；之前尝试的 `selectedRange ? ... : monthDate` 会导致非当前月面板跳月显示，故放弃。）
- [x] 3.2 把 `<Calendar value={monthDate}>` 改为 `<Calendar value={calendarValue}>`。
- [x] 3.3 运行 prettier：`web/node_modules/.bin/prettier --write web/src/parent/components/TaskCalendar.tsx`，全文件 prettier 通过。
- [x] 3.4 运行 `pnpm --filter web test`：1.2 / 1.3 新增测试转绿，全部 40 + 34 = 74 个 TaskCalendar/ParentTasksPage 测试仍通过，全量 186 个测试通过。

## 4. 全量验证

- [x] 4.1 `pnpm --filter web test`：18 files / 186 tests 全部通过。
- [x] 4.2 `pnpm --filter web build`：构建 exit 0（5.89s，bundle size 警告属历史问题）。
- [x] 4.3 `pnpm --filter web lint`：未引入新增 TS 错误（pre-existing 16 个历史错误未触碰，沿用上次 hotfix 决策）。

## 5. 验证证据（单元测试已覆盖；浏览器手工验证见 `verify` 阶段归档前最终确认环节）

- [x] 5.1 今日（2026-07-24）被 antd 内置样式 + 自定义 boxShadow 双层高亮：在当前月面板。证据：TaskCalendar.test.tsx 1.2 第二条断言 (`date-cell-24` 的 `data-selected === 'true'` = boxShadow 命中) + 第一条断言 (`data-value === '2026-07-24'` = antd value 锁定今日)。**待用户在 verify → archive 归档前最终确认** `pnpm --filter web dev` → `/parent/tasks` 视觉复核。
- [x] 5.2 点击 7 月任意非今日 cell 时 boxShadow 与 antd 内置高亮同步移动：当前 reducer 行为未改（仍由 `SELECT_DATE` 派发，task 1.2 已通过），SELECT_DATE action 由既有测试覆盖（ParentTasksPage.test.tsx `SELECT_DATE: 设置 selectedRange 为单日`）。**待用户浏览器复核**。

## 备注

- 改动文件数：2（`TaskCalendar.tsx` + `pages/index.tsx`）+ 2 测试文件，未触发 >4 文件 tripwire。
- 无 delta spec：本次不改 capability spec 验收场景。
- 升级判定信号复核：无新 capability / public API / schema / 跨模块协调 / 深层架构问题。
- pre-existing lint：未触碰，沿用上次 hotfix 决策。
- 设计过程中遇到过一次回退：3.1 的 calendarValue 计算先采用 `selectedRange ? dayjs(selectedRange.startDate) : monthDate` 回退，会让八月面板的 antd `<Calendar>` 跳月显示七月（因为 value 控制显示月）；改回纯 `monthDate` 后八月面板正常显示八月，符合预期。
- 已知未处理场景（不在本次 hotfix 范围内）：跨月导航后（如 baseMonth=2026-06）6 月面板的 antd 内置高亮与 selectedRange.boxShadow 会分布在不同月（antd 高亮 6 月 1 号，boxShadow 仍在 7 月 24 号）。本次 hotfix 仅修复『初次进入页面默认高亮 1 号』单一症状；如未来需要统一，需另起一个 change 处理 `selectedRange` 与 `baseMonth` 跨月协同。
