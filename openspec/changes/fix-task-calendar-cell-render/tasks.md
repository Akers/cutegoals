# Tasks: fix-task-calendar-cell-render

## 1. RED — 新增失败回归测试

- [x] 1.1 调整 `web/src/parent/components/__tests__/TaskCalendar.test.tsx` 的 antd mock：让 mock `<Calendar>` 在每个 cell 中先渲染 antd 默认数字 `<span data-testid="antd-default-date">{date.date()}</span>`，再附加 `dateCellRender(date)` 的返回值，模拟真实 antd 行为。
- [x] 1.2 新增回归测试「cell 内不应出现重复的天数数字」：断言每个 date cell 内自定义渲染函数返回的 JSX 不包含 `<div>{digit}</div>` 形式的独立天数文本节点。
- [x] 1.3 运行 `pnpm --filter web test` 确认新测试当前 FAIL（RED 证据），原测试不受影响。RED 证据：`expected [ <div></div> ] to have a length of +0 but got 1`，对应 `<div>{date.date()}</div>` 重复渲染。

## 2. 修复 `TaskCalendar.tsx` — 消除重复数字

- [x] 2.1 编辑 `web/src/parent/components/TaskCalendar.tsx:248` 的 `renderDateCell`：删除 `<div>{date.date()}</div>`，保留任务背景层与 Badge。
- [x] 2.2 运行 prettier：`web/node_modules/.bin/prettier --write web/src/parent/components/TaskCalendar.tsx`。
- [x] 2.3 运行 `pnpm --filter web test`，确认 1.x 新增的回归测试转绿，全部 177 个测试通过。

## 3. 修复 `TaskCalendar.tsx` — 周号列对齐

- [x] 3.1 重构 `CalendarPanel`（`TaskCalendar.tsx:254-276`）：把 `CalendarHeader` 移到面板顶部跨全宽，下方 `display:flex; alignItems:stretch` 让 `WeekNumberColumn` 与 antd `<Calendar>` 同高；`WeekNumberColumn` 顶部增加与 antd 星期表头等高的占位（40px），六个周号行改用 `flex: 1` 均分剩余高度（外层 stretch 同高），逐行对齐日历日期行。
- [x] 3.2 在 jsdom 中无法测像素对齐，记录手工验证步骤：本地 `pnpm --filter web dev`，浏览器打开 `/parent/tasks`，截屏比对周号 1~6 与 antd Calendar 第一至第六周日期行视觉对齐。**待用户在浏览器中手工验证。**
- [x] 3.3 运行 `pnpm --filter web test`，确认现有 33 个 TaskCalendar 测试（含新加的回归断言）全部通过。

## 4. 全量验证

- [x] 4.1 `pnpm --filter web test`：18 files / 177 tests 全部通过（含 175 个原有测试 + 本次新增 2 个回归断言）。
- [x] 4.2 `pnpm --filter web build`：构建成功（7.54s exit 0；bundle size 警告属历史问题）。
- [x] 4.3 `pnpm --filter web lint`：FAIL，但 7 个 TS 错误全在 `pages/index.tsx` 与 `pages/__tests__/ParentTasksPage.test.tsx`，均属 pre-existing 历史 TS 错误（clean main 已存在 12 个，本次重写测试反而修了 5 个），不在本次 hotfix 范围内。verify 阶段 record-check 用 test + build 两条 exit 0 作为可审计证据。

## 5. verify-fail 迭代：像素级 spacer 校准

verify 阶段用户在浏览器验证后发现周号对齐仍不准（整体偏低 + 行高不一致 + 1-10px + 所有月份一致），verify_failures=1 自动回到 build。派 explorer 在真实 Chromium 中测量 antd 5.29.3 `<Calendar fullscreen={false} headerRender={() => null} />` 取证：

- `.ant-picker-calendar` = 273 px
- `.ant-picker-panel` border-top = 1 px
- `.ant-picker-body` padding = 8 0
- `.ant-picker-content` table height = 256 px（token `miniContentHeight`）
- thead = 18 px（token `weekHeight = controlHeightSM * 0.75 = 24 * 0.75`）
- tbody 6 行总高 = 238 px，单行 ≈ 39.67 px（`table-layout: fixed` 强制均分）
- `headerRender={() => null}` 零副作用（不渲染任何 wrapper / spacing）

根因：原 `spacer=40` 与 `minHeight=36` 是错误估算（误以为 antd 表头约 40 px），实际 antd 表头几何固定为 `1+8+18=27 px`。

- [x] 5.1 RED：在 `TaskCalendar.test.tsx` WeekNumberColumn describe 块末尾追加 3 个像素级结构断言：spacer height = 27px、周号行无 minHeight + flex=1、周号列底部 8px 占位。确认 3 failed | 177 passed (180)。
- [x] 5.2 修复 `TaskCalendar.tsx`：spacer height 40 → 27（含 explorer 取证来源注释）；删除 minHeight=36（避免兜底扭曲 flex:1 均分）；周号列底部新增 8px 占位 div（对齐 antd body padding-bottom）。让 6 个周号行严格均分 (273-27-8)/6 = 39.67 px，与 antd tbody 单行 39.67 px 一致（累积亚像素误差 ≤ 0.05 px，肉眼不可见）。
- [x] 5.3 跑 `pnpm --filter web test` 转绿：18 files / 180 tests 全部通过。
- [x] 5.4 跑 `pnpm --filter web build`：成功 7.46s exit 0。
- [x] 5.5 prettier：`TaskCalendar.tsx` 格式化（TaskCalendar.test.tsx unchanged），重跑测试确认 180/180 仍绿。
- [x] 5.6 待用户在浏览器中再次手工验证：本地 `pnpm --filter web dev`，打开 `/parent/tasks`，截屏比对周号 1~6 与 antd `<Calendar>` 第一至第六周日期行的视觉对齐。

## 备注

- 文件数 tripwire：本次修改 2 个文件（`TaskCalendar.tsx` + `TaskCalendar.test.tsx`），未触发 >4 文件提示。
- 无 delta spec：本次不改 capability spec 验收场景。
- 升级判定信号复核：无新 capability / public API / schema / 跨模块协调 / 深层架构问题。
- pre-existing lint：未触碰，留待后续独立清理。
