# Verify 报告：fix-calendar-non-current-no-highlight-v2

- 日期：2026-07-24
- Change：fix-calendar-non-current-no-highlight-v2
- Workflow：hotfix（第三次进入该症状链：`fix-calendar-default-current-date` → `fix-calendar-non-current-no-highlight`（用户报告失败） → 本次 v2）
- Verify Mode：light（自动 scale 评估返回 full，因 tasks.md 总任务数 19 超过阈值 3；agent 基于「2 改动文件 + 0 delta spec + 紧接前两个姊妹 hotfix」覆盖为 light）
- Review Mode：off（自动跳过代码审查；review_mode 在 .comet.yaml 中预设 off）
- Branch：main

## 修复概述（v2 相对 v1 的关键差异）

| 维度 | v1（用户报告失败） | v2（本次） |
|------|-------------------|----------|
| 数据通路 | `value={isCurrentMonth ? today : undefined}` + `defaultValue={monthDate}` | `value={isCurrentMonth ? today : monthDate}`，不再传 defaultValue |
| 抑制手段 | 期望 antd 内部 `mergedValue=undefined`（实际回退 defaultValue，行为与未修复等价） | wrapper div 加 `task-calendar-non-current-month` className，`<style>` 块 scoped CSS override `.ant-picker-cell-selected .ant-picker-calendar-date { background: transparent !important }` |
| 测试断言 | mock 仅回放 props，未验证 antd 实际选中行为（mock-vs-reality 脱节） | mock 内不再合成 `data-task-calendar-mode`；测试断言 CalendarPanel 外层 div 真实 className |
| 视觉验证 | 无（mock 通过≠视觉修复） | 浏览器手验 5.1 + 5.2 由用户在 archive 阶段确认 |

### 根因复盘（已在 Phase 1 完成）

**antd 5.29.3 `useMergedState({defaultValue, value})` 行为**：`hasValue(value)` 仅 `value !== undefined` 为真，所以 `value=undefined` 时回退 `defaultValue`，导致 `mergedValue = monthDate = '2026-08-01'`，cell 1 被加 `.ant-picker-cell-selected`，CSS `&-in-view&-cell-selected` 给到绿底。

## 6 项轻量验证检查

| # | 检查项 | 结果 | 证据 |
|---|--------|------|------|
| 1 | tasks.md 全部任务已完成 `[x]` | PASS | 19 tasks `[x]` / 0 tasks `[ ]` |
| 2 | 改动文件与 tasks.md 描述一致 | PASS | 2 文件（`TaskCalendar.tsx`、`TaskCalendar.test.tsx`），与 tasks 3.x 一致。base_ref → HEAD diff：`+53/-39` |
| 3a | web 构建通过 | PASS | `./node_modules/.bin/umi build` exit 0（6.37s） |
| 3b | server 构建通过 | PASS | `cd server && mvn compile -q` exit 0 |
| 4 | 相关测试通过 | PASS | `pnpm test`：`18 files / 187 tests passed`，exit 0 |
| 5 | 无明显安全问题 | PASS | CSS 选择器限定在 `.task-calendar-non-current-month` 作用域；`!important` 局部使用；无凭证/密钥/PII/新增 IO；不影响跨组件。修改文件仅 1 源 + 1 测试 |
| 6 | 代码审查策略 | 跳过 | review_mode=off；skip 原因记录在本文件顶部 |

## RED → GREEN 循环证据

### RED（修复前，task 2.4）

- v2 新增 describe「v2 视觉抑制 wrapper className」两条断言失败：实际 `className=""` 不含 `task-calendar-current-month` / `task-calendar-non-current-month`，错误信息 `expected '' to contain 'task-calendar-non-current-month'`。

### GREEN（修复后，task 3.6）

- 上述 2 条断言全 PASS。
- 全部 187 个总测试通过（v1 188 → v2 187：删除 3 条 v1 残留断言 + 新增 2 条 v2 断言 = -1 delta，与 41→41 的 TaskCalendar.test 一致）。

## 根因消除核查

| 根因 | 修复 | 证据 |
|------|------|------|
| antd `useMergedState` 在 `value=undefined` 时回退 defaultValue，导致 cell 仍被 `.ant-picker-cell-selected` 命中 | v2 不再依赖 props 通路抑制，改为 scoped CSS override `.task-calendar-non-current-month .ant-picker-cell-selected .ant-picker-calendar-date { background: transparent !important }` | 1) TaskCalendar.test.tsx describe「v2 视觉抑制 wrapper className」两条断言通过（wrapper className 正确分流）；2) 用户浏览器手验 5.1（jsdom 不计算 CSS，浏览器为最终验证） |

## 浏览器手工验证（用户复核归档前最终确认环节）

⚠️ **本次 v2 是 CSS 修复，jsdom 不计算 CSS，视觉验证必须由用户在浏览器完成**。

- `pnpm --filter web dev` → `/parent/tasks`，确认：
  - 7 月面板：24 号（今日）仍被 antd 绿底 + 自定义 boxShadow 双层高亮（保持不变）。
  - **8 月面板：所有 cell 不被 antd 绿底高亮（红框部分真正解除 — 本次 v2 唯一新验证目标）**。
- 点击 8 月任意 cell，确认自定义 boxShadow 落到该日（带蓝边），antd 绿底仍未出现（CSS override 持久）。

## 验证结论

**PASS**：6 项检查全部通过，无 CRITICAL/IMPORTANT 问题；fix 已提交（commit `8c21492`），可进入 archive 阶段。

**前置 v1（`fix-calendar-non-current-no-highlight`，用户报告失败）反思已落入 tasks.md 备注**，供后续 hotfix 修改 antd `<Calendar>` 相关 props 时参考：mock 必须验证生产 React 树上外层 wrapper className，视觉类改动必须有浏览器手验步骤闭环。
