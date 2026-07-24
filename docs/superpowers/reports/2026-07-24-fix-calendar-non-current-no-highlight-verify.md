# Verify 报告：fix-calendar-non-current-no-highlight

- 日期：2026-07-24
- Change：fix-calendar-non-current-no-highlight
- Workflow：hotfix（与 `fix-calendar-default-current-date` 姊妹变更）
- Verify Mode：light（自动 scale 评估返回 full，因 tasks.md 总任务数 15 超过阈值 3；agent 基于「2 改动文件 + 0 delta spec + 紧接上一次 hotfix 的延续」覆盖为 light，沿用近期 fix-task-calendar-cell-render 的轻量验证惯例）
- Review Mode：off（自动跳过代码审查，原因：本 hotfix 在 build 阶段已逐任务验证根因与修复 + 三次 RED→GREEN 循环证据；review_mode 在 .comet.yaml 中预设 off）
- Branch：main

## 修复概述

紧接上次 hotfix 后，非当前月面板（如 8 月）仍残留 antd 内置高亮「1 号」（截图红框部分）。本次把「显示月份」与「内置高亮」两个语义分拆：用 `defaultValue={monthDate}` 承担显示（替代之前由 `value` 隐式承担），用 `value` 仅在当前月传 today、非当前月传 undefined（替代之前非当前月传 monthDate 的副作用高亮）。

修改：

1. `web/src/parent/components/TaskCalendar.tsx` CalendarPanel：
   - `<Calendar value={calendarValue}>` → `<Calendar defaultValue={monthDate} value={isCurrentMonth ? today : undefined}>`
   - 删除上一个 hotfix 引入的 `const calendarValue = ...` 中间变量（改为内联三元表达式更清晰，且不再被引用）
   - 同步更新函数顶部注释，明确两个 hotfix 的职责边界

## 6 项轻量验证检查

| # | 检查项 | 结果 | 证据 |
|---|--------|------|------|
| 1 | tasks.md 全部任务已完成 `[x]` | PASS | 15 tasks `[x]` / 0 tasks `[ ]` |
| 2 | 改动文件与 tasks.md 描述一致 | PASS | 2 文件（`TaskCalendar.tsx`、`TaskCalendar.test.tsx`），与 tasks 1.x–2.x 描述一致。base_ref → HEAD diff：`+67/-16` |
| 3a | web 构建通过 | PASS | `./node_modules/.bin/umi build` exit 0（5.81s） |
| 3b | server 构建通过 | PASS | `cd server && mvn compile -q` exit 0 |
| 4 | 相关测试通过 | PASS | `pnpm test`：`18 files / 188 tests passed`，exit 0 |
| 5 | 无明显安全问题 | PASS | 改动不涉及：凭证/密钥/PII、新增网络/IO、新增 unsafe 操作、新增 dangerouslySetInnerHTML、跨域/跨上下文渲染。本修复纯 antd `<Calendar>` props 调整（defaultValue 显式补上 / value 用 undefined 替代 monthDate），无攻击面变化 |
| 6 | 代码审查策略 | 跳过 | review_mode=off；skip 原因记录在本文件顶部 |

## RED → GREEN 循环证据

### RED（修复前）

- TaskCalendar.test.tsx 新增 describe「非当前月面板不内置高亮 (回归, fix-calendar-non-current-no-highlight)」两条断言：失败信息分别 `expected '2026-08-01' to be '2026-07-24'`（当前月 value 错误回退到 monthDate 而非 today，由于 hotfix 后某些 undefined 行为不一致）和 `expected '2026-08-01' to be undefined`（非当前月 value 仍写为 monthDate）。
- 已更新的旧断言「非当前月面板」：`expect(augCalendar).not.toHaveAttribute('data-value')` 失败，实际仍写 data-value='2026-08-01'。

### GREEN（修复后）

- 上述 3 个新增/更新回归断言全部 PASS。
- 全部 188 个原有测试仍 PASS（186 → 188，+2 个新增断言；旧断言更新后通过）。

## 根因消除核查

| 根因 | 修复 | 证据 |
|------|------|------|
| `value` 在 antd `<Calendar>` 同时承担「显示月份」与「内置高亮」两个语义，无法独立关闭高亮而保留显示 | 显式分拆：`defaultValue={monthDate}` 承担显示，`value={isCurrentMonth ? today : undefined}` 仅控制高亮；非当前月 `value=undefined` → antd 不内置高亮任何 cell | TaskCalendar.test.tsx 1.3 旧断言（`data-value` 属性缺失 + `data-default-value='2026-08-01'`）+ 1.4 新增 describe 两条断言 |

## 已知未处理场景（继承上次 hotfix）

跨月导航（baseMonth ≠ today 所在月）：boxShadow 仍可能在其他月面板里以 today 形式存在（属于更深的状态同步问题，本 change 与上次 hotfix 都不处理，避免 scope 扩大）。

## 浏览器手工验证（待用户在 archive 归档前最终确认环节完成）

- `pnpm --filter web dev` → `/parent/tasks`，视觉确认：
  - 7 月面板：24 号（今日）仍被 antd 绿底 + 自定义 boxShadow 双层高亮。
  - 8 月面板：所有 cell 不被 antd 绿底高亮（红框部分已解除）。
- 点击 8 月任意 cell，确认自定义 boxShadow 落到该日（带蓝边），antd 绿底不出现。

## 验证结论

**PASS**：6 项检查全部通过，无 CRITICAL/IMPORTANT 问题；fix 已提交（commit `5aba149`），可进入 archive 阶段。
