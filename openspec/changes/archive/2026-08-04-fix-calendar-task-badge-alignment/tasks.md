# Tasks: fix-calendar-task-badge-alignment

## 1. 复现并定位

- [x] 1.1 通过 observer vision 分析截图，确认角标 DOM `data-testid="task-badge-2026-08-03"` 内联 `top: -26px; left: 20px;`；角标视觉位置跨入上一周日期行（observer 报告）
- [x] 1.2 定位源码：`web/src/parent/components/TaskCalendar.tsx:265-266` 中 `renderDateCell` 返回的 `<span>` 内联 `top: -26, left: 20`

## 2. 修复实现

- [x] 2.1 修改 `TaskCalendar.tsx:265-266`：`top: -26 → top: 2`，`left: 20 → left: 2`；并加注释说明本次修复（fix-calendar-task-badge-alignment）
- [x] 2.2 确认其他视觉属性（color / borderRadius / fontSize / padding 等）保持不变

## 3. 回归测试

- [x] 3.1 在 `web/src/parent/components/__tests__/TaskCalendar.test.tsx` 中**修改已有测试**「任务数角标使用绝对定位」断言为 `top: '2px', left: '2px'`；**未新增**测试用例，因为已有同名测试即覆盖此场景，且原断言锁定了 buggy 值（`-26px / 20px`），需要先改正它
- [x] 3.2 TDD red-green 循环：先修改测试断言后跑红（`expected '-26px' to be '2px'`）→ 修改代码 `top/left` → 跑绿（30/30 pass）

## 4. 全量验证

- [x] 4.1 跑 `pnpm --filter @cutegoals/web run test`，确认 18 个 Test Files / 全部 tests 通过 — 通过：18 Test Files / 176 tests / 176 passed（0 failed）
- [x] 4.2 跑 `node web/scripts/check-css-loads.mjs`，确认通过 — 通过：`✓ All 2 CSS file(s) imported: src/styles/index.css, src/styles/themes.css`
- [x] 4.3 跑 `pnpm --filter @cutegoals/web exec tsc --noEmit`，确认本 change 未引入新 tsc 错误 — 通过：10 个错误全部为 base 838e6f8 已存在的历史未用变量错误（与上次 hotfix 同基线），本 change 未引入新错误

## 5. 提交与归档

- [x] 5.1 提交代码：`fix: 日历任务角标定位回归（top -26 → 2, left 20 → 2）`，单 commit — 完成：commit `7275ef7`（含 TaskCalendar.tsx + TaskCalendar.test.tsx 改动；delta spec 在归档前补齐）
- [x] 5.2 跑 `openspec validate fix-calendar-task-badge-alignment --type change --strict`，确认通过 — 通过：`Change 'fix-calendar-task-badge-alignment' is valid`（补齐 delta spec 后）

## 6. delta spec 补齐

- [x] 6.1 创建 `openspec/changes/fix-calendar-task-badge-alignment/specs/parent-task-calendar/spec.md`，含 `## MODIFIED Requirements`：在「任务类型颜色标记」requirement 下新增 Scenario「角标位置基线 (fix-calendar-task-badge-alignment)」明确期望 `top: 2px; right: 2px`（措辞保持 main spec 的「右上角」）
- [x] 6.2 用此 delta spec 解决 OpenSpec schema 「Change must have at least one delta」硬要求

## 7. verify-fail 修正循环（用户澄清：右上角）

- [x] 7.1 用户在归档确认阶段选择「需要调整或重新验证」并澄清：角标应在「天的方框的**右上角**」而非左上角；运行 `comet state transition verify-fail` 回退到 build（verify_failures=1）
- [x] 7.2 TDD red-green：改测试断言 `left:'2px' → right:'2px'` → 红（`expected '' to be '2px'`）→ 改代码 `left: 2 → right: 2` → 绿（`TaskCalendar.test.tsx` 30/30）
- [x] 7.3 delta spec 措辞改回「右上角」，角标位置基线 Scenario 更新为 `top: 2px; right: 2px`；proposal.md / design.md 同步更新
- [x] 7.4 全量验证：`pnpm --filter @cutegoals/web run test` 18 files / 176 tests pass；`openspec validate --strict` 通过
- [x] 7.5 提交修正：commit `cd480c8` `fix: 日历任务角标定位改为右上角 (left:2 → right:2)`

## 8. 第二次 verify-fail 微调（贴角）

- [x] 8.1 用户反馈角标位置仍需往右上角再挪；运行 `comet state transition verify-fail` 回退 build（verify_failures=1）
- [x] 8.2 代码 `top: 2 → 0, right: 2 → 0`（贴日期内盒右上角）；测试断言同步 `top:'0px' / right:'0px'`（30/30 pass）
- [x] 8.3 delta spec 角标位置基线 Scenario 同步 `top: 0px; right: 0px`；design.md / proposal.md 值引用同步
- [x] 8.4 全量验证：18 files / 176 tests pass；check-css-loads 通过；`openspec validate --strict` 通过
- [x] 8.5 提交微调：commit `f831f5f`
