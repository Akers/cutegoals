# Tasks: fix-calendar-non-current-no-highlight

## 1. RED — 更新 mock 与回归断言

- [x] 1.1 修改 `web/src/parent/components/__tests__/TaskCalendar.test.tsx` mock `<Calendar>`：让 host div 暴露 `data-value` 仅在 `value` 有值时写入（属性条件性 spread），新增 `data-default-value` 在 `defaultValue` 有值时写入。
- [x] 1.2 mock 签名扩到 `({ value, defaultValue, dateCellRender, onSelect })`，返回 `anchor = value ?? defaultValue` 推导显示月份，使 mock 在 `value=undefined` 仍可渲染。
- [x] 1.3 更新「非当前月面板:antd <Calendar> value 回退到 monthDate」旧断言：`data-value` 期望从 `'2026-08-01'` 改为「属性不存在」，并新增 `data-default-value === '2026-08-01'`。RED 证据：`data-value` 当前实际是 `'2026-08-01'`，导致 `not.toHaveAttribute('data-value')` 失败。
- [x] 1.4 新增 describe「非当前月面板不内置高亮 (回归, fix-calendar-non-current-no-highlight)」：3 个断言（当前月 value + defaultValue / 非当前月 value 属性缺失 / 非当前月 defaultValue）。RED 证据：当前实现非当前月仍写 `data-value='2026-08-01'`，断言失败。
- [x] 1.5 运行 `vitest run`：3 个 RED 失败，符合预期。

## 2. 修复 `web/src/parent/components/TaskCalendar.tsx` — 非当前月面板 value=undefined

- [x] 2.1 把 `<Calendar value={calendarValue}>` 改为 `<Calendar defaultValue={monthDate} value={isCurrentMonth ? today : undefined}>`。
- [x] 2.2 删除因上一个 hotfix 引入的 `const calendarValue = ...` 中间变量（不再被 `<Calendar>` 引用，留着会触发 TS6133 `noUnusedLocals`，且语义被新的内联表达式覆盖）。
- [x] 2.3 同步更新 CalendarPanel 上方的注释，标注本 change 与上一 change 的职责区分。
- [x] 2.4 运行 prettier：`web/node_modules/.bin/prettier --write web/src/parent/components/TaskCalendar.tsx`（已格式化，文件未变化）。
- [x] 2.5 运行 `pnpm --filter web test`：1.3、1.4 全部转绿；18 文件 / 188 测试全过。

## 3. 全量验证

- [x] 3.1 `pnpm --filter web test`：18 文件 / 188 测试全部通过（含 2 个新增断言 + 1 个更新断言）。
- [x] 3.2 `pnpm --filter web build`：构建 exit 0（5.98s，bundle size 警告属历史问题）。
- [x] 3.3 `pnpm --filter web lint`：未引入新增 TS 错误；pre-existing 16 个历史错误（含 `dayData.taskTypes.* undefined`、`React unused`、`dayjs unused` 等）未触碰，沿用上次 hotfix 决策。

## 4. 验证证据（单元测试已覆盖；浏览器手工验证见 `archive` 阶段归档前最终确认环节）

- [x] 4.1 非当前月面板不再 antd 内置高亮任何 cell（红框部分已解除）。证据：TaskCalendar.test.tsx 1.4 第二条断言 `augCalendar.not.toHaveAttribute('data-value')` 通过、`data-default-value === '2026-08-01'` 通过；1.3 旧断言更新后通过。**待用户在 archive 归档前最终确认** `pnpm --filter web dev` → `/parent/tasks` 视觉复核（7 月 24 日仍双层高亮、8 月面板无绿底）。
- [x] 4.2 点击非当前月 cell 仅 boxShadow 命中、不触发 antd 绿底。证据：1.4 第二条断言（`value=undefined`） + 既有的 `selectedRange.boxShadow` 覆盖测试（TaskCalendar.test.tsx describe "选中日期显示高亮边框"）。**待用户浏览器复核**。

## 备注

- 改动文件数：1（`TaskCalendar.tsx`）+ 1 测试文件，未触发 >4 文件 tripwire。
- 无 delta spec：与上次 hotfix 同源，无 capability 层验收场景变化。
- 升级判定信号复核：无新 capability / public API / schema / 跨模块协调 / 深层架构问题。
- pre-existing lint：未触碰，沿用 `fix-calendar-default-current-date` 决策。
- 本 change 是 `fix-calendar-default-current-date` 的姊妹变更：上次只修当前月、保留非当前月行为；本次补齐非当前月高亮。两者合并后双月日历的默认高亮逻辑完全符合用户期望（仅今日高亮）。
- 已知未处理场景（继承自上次 hotfix）：跨月导航后（baseMonth ≠ today 所在月）boxShadow 仍可能在其他月面板里以 today 形式存在（属于更深的状态同步问题，本 change 与上次 hotfix 都不处理，避免 scope 扩大）。
