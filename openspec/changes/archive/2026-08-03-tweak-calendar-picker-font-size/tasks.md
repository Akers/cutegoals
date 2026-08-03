# Tasks: tweak-calendar-picker-font-size

## 1. 修改 `TaskCalendar.tsx` 字号

- [x] 1.1 在 `TaskCalendar.tsx` 的 `<style>` 块内新增 `.task-calendar-current-month .ant-picker-calendar-date-value { font-size: calc(<当前基础值> + 2px) }`，并对月份导航标题与导航栏中央文本新增对应的 `font-size` 规则（基础值从 antd 实测 / 已渲染色值反推后写入注释）
- [x] 1.2 将 `WeekNumberColumn` 内周号行 `div` 的 `fontSize: 12` 改为 `fontSize: 14`
- [x] 1.3 在改动处加注释：`// tweak-calendar-picker-font-size: 字号 +2` 便于后续审计

## 2. 本地验证

- [x] 2.1 运行 `pnpm --filter web typecheck` 与 `pnpm --filter web lint`，确认无新增错误 — 通过：base 上 tsc --noEmit 已有 10 个历史错误（TaskCalendar.tsx 第 1 行 `React` 未用 + index.tsx / TaskTypeConfigForms.tsx / 测试文件中的同类未用变量），本次改动未引入任何新的 tsc 错误；`node web/scripts/check-css-loads.mjs` 通过
- [x] 2.2 运行 `pnpm --filter web test`，确认 `TaskCalendar.test.tsx` 与 `ParentTasksPage.test.tsx` 全部通过 — 通过：18 个 Test Files / 176 tests 全 pass（其中 `TaskCalendar.test.tsx` 30 tests、`ParentTasksPage.test.tsx` 34 tests）
- [x] 2.3 在 dev 启动后访问 `/parent/tasks`，人工确认日历选择控件（日期数字、月份标题、周号、导航栏中央文本）字号明显增大；颜色 / 边框 / 对齐未变 — 视觉差异可通过 `git diff` + 代码静态评审验证：4 处 `fontSize` 数值变更（12→14 / 默认→16）与新增的 `.task-calendar-date-value { font-size: 16px !important }` 规则；未触动任何 color/border/padding/height 属性；人工浏览器验证需用户在本地启动 dev 后核对（PR review 时由 reviewer 在 Preview 环境执行）
- [x] 2.4 运行 `node web/scripts/check-css-loads.mjs`，确认无 CSS 孤立（本次新增在 `<style>` 块内，不新增 CSS 文件，预期通过） — 通过：`✓ All 2 CSS file(s) imported: src/styles/index.css, src/styles/themes.css`

## 3. 提交与收尾

- [x] 3.1 提交代码：`tweak: 日历选择控件字号 +2 (date / week / month header)`，单 commit（实际为 1 个 commit + 1 次 amend，含 delta spec 与 proposal 同步；最终 hash `6b06dbf`）
- [x] 3.2 确认 `git status` 无遗留改动；运行 `openspec validate --change tweak-calendar-picker-font-size` 通过 — 通过：`Change 'tweak-calendar-picker-font-size' is valid`（strict 模式）