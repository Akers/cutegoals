# Tasks: fix-calendar-week-row-highlight-bg

- [x] 1. RED: 在 `web/src/parent/components/__tests__/TaskCalendar.test.tsx` 新增回归测试
  - 周选中时：该周内所有日期 cell 的 `data-selected` 均为 `false`
  - 周选中时：周号行 `backgroundColor` 包含 `rgba(22, 119, 255` 半透明浅蓝字符串
  - 日选中时：选中日期 cell `backgroundColor` 包含 `rgba(22, 119, 255` 字符串
  - 默认无 selection 时：所有日期 cell 与周号行均无高亮 backgroundColor
  - 验证证据：`npx vitest run src/parent/components/__tests__/TaskCalendar.test.tsx -t "fix-calendar-week-row-highlight-bg"` 输出 4 failing 用例

- [x] 2. GREEN: 修改 `web/src/parent/components/TaskCalendar.tsx`
  - `WeekNumberColumn`: 选中态由 `boxShadow` 改为 `backgroundColor: rgba(22, 119, 255, 0.18)` + 1px 半透明边框
  - `dateCellRender`: `isSelected` 增加 `selectedRange.type === 'day'` 判定
  - `dateCellRender`: 选中态由 `boxShadow` 改为 `backgroundColor: rgba(22, 119, 255, 0.12)`（叠加任务类型背景）
  - 验证证据：`TaskCalendar.test.tsx` 48/48 全绿

- [x] 3. 全量回归
  - `cd web && npm test` 194/194 全绿（原 187 → +7 新增）
  - `cd web && npm run lint` 不增新错（pre-existing 错误不阻塞）
  - `cd web && npm run build` exit 0
  - 验证证据：测试输出 + lint 输出 + build 输出

- [x] 4. 提交
  - `git add web/src/parent/components/TaskCalendar.tsx web/src/parent/components/__tests__/TaskCalendar.test.tsx`
  - `git commit -m "fix: 日历周/日高亮改用半透明浅蓝背景矩形 + 周选中不联动日期"`
  - 验证证据：`git log -1 --stat` 提交 b73e47f 包含 TaskCalendar.tsx + .test.tsx + tasks.md
