# Proposal: fix-calendar-task-badge-alignment

## Why

家长在 `/parent/tasks` 任务分配页，有任务的日期单元格**右上角**应显示红色任务数角标（如「1」「3」）。当前实现下，角标实际渲染位置**错位到了上一周日期行**——视觉上像是在上方的灰色相邻月日期右侧。截图证据：2026 年 8 月 3 日、5 日、7 日三个有任务日的角标中心 y≈145，而当前行日期中心 y≈178，角标向上偏移约 33 px，跨入上一行。

根因：`web/src/parent/components/TaskCalendar.tsx` 中 `renderDateCell` 返回的 `<span>` 内联 `top: -26, left: 20`。antd 5.x 的 `dateCellRender` 内容渲染在 `.ant-picker-cell-inner`（24 × 24 px 日期内盒）内，`position: absolute` 以该内盒为定位参考；`top: -26` 相对 24 px 内盒向上越界 ~108%，导致角标跨出 cell 顶部进入上一行。历史 commit `1f94e8c` 修过 dateCellRender 相关错位但未覆盖角标自身定位。

本次 hotfix 将角标放回日期内盒**右上角（贴角）** 处（`top: 0, right: 0`），消除错位，并与 `parent-task-calendar` main spec「单元格右上角 MUST 显示任务总数徽章」的既有描述对齐。

## What Changes

- 修改 `web/src/parent/components/TaskCalendar.tsx` 中 `renderDateCell` 返回的 `<span>` 内联定位：`top: -26 → top: 0`，`left: 20 → right: 0`
- 修正 `web/src/parent/components/__tests__/TaskCalendar.test.tsx` 中既有测试「任务数角标使用绝对定位」的断言：原断言锁定了 buggy 值（`top: -26px / left: 20px`），改为断言 `top: 0px / right: 0px`，作为定位值的回归护栏
- 创建 delta spec（`## MODIFIED Requirements`）：在「任务类型颜色标记」requirement 下新增 Scenario「角标位置基线」，明确角标应落在日期内盒右上角（贴角）（main spec 措辞「右上角」保持不变）
- 不修改 antd `Calendar` 组件 API、不修改角标的颜色 / 字号 / 形状 / 任务数据来源
- 不修改其他日历组件（`CalendarHeader` / `WeekNumberColumn`）

## Capabilities

### New Capabilities
（无；本 change 不引入新能力）

### Modified Capabilities
- `parent-task-calendar`：「任务类型颜色标记」requirement 主文不变（仍为「右上角」），新增 1 个 Scenario「角标位置基线 (fix-calendar-task-badge-alignment)」明确绝对定位数值基线。原 spec 未规定定位数值，导致 buggy 值长期存在（回归测试甚至锁定了错误值），本次补齐验收基线。

## Impact

- 代码文件：`web/src/parent/components/TaskCalendar.tsx`（修复 2 行 + 注释）、`web/src/parent/components/__tests__/TaskCalendar.test.tsx`（修正断言）
- 测试：`TaskCalendar.test.tsx` 30 个测试全部通过；全 web 套件 18 files / 176 tests 通过
- API / DB / 后端：无
- Spec：`parent-task-calendar` 新增角标位置基线 Scenario（归档时合并）
