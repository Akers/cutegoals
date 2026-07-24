# Verify 报告：fix-calendar-week-row-highlight-bg

- 日期：2026-07-24
- Change：fix-calendar-week-row-highlight-bg
- Workflow：hotfix
- Verify Mode：light
  - 自动 scale 评估：`Tasks=4 > 3` 与 `Changed files=13 > 8` 触发 full
  - 手动覆盖 light 原因：实际代码改动仅 2 文件（`TaskCalendar.tsx` + `TaskCalendar.test.tsx`），其余 11 个是 `.comet/` 状态文件 + 3 个 OpenSpec 产物（proposal/design/tasks），与本仓库历次 hotfix 的轻量验证惯例一致
- Review Mode：off（自动跳过代码审查，原因：build 阶段已逐任务验证根因与修复 + 4 条新 RED 用例覆盖周/日高亮单选语义）
- Branch：main

## 修复概述

`/parent/tasks` 双月日历周/日高亮样式与单选语义不符合产品要求：

1. **样式错误**：原使用 `boxShadow: inset 0 0 0 2px var(--ant-color-primary)`（primary 边框），产品期望「半透明的浅蓝背景的矩形框选当前周的整行」。
2. **联动错误**：选中某一周时，周号行被高亮，但周内所有日期 cell 也被同时高亮（`dateCellRender` 用日期范围包含判断）。
3. **单选语义缺失**：选中一天时，其他日期 cell 的 `data-selected` 正确为 `false`，但所有周号行在该周内仍被范围重叠检查命中。

## 修复内容

1. `web/src/parent/components/TaskCalendar.tsx` WeekNumberColumn：
   - 选中态 `boxShadow` 替换为 `backgroundColor: rgba(22, 119, 255, 0.18)` + `border: 1px solid rgba(22, 119, 255, 0.5)`
   - `isSelected` 增加 `selectedRange.type === 'week'` 判定，避免 day 选中时该周也被标记
2. `web/src/parent/components/TaskCalendar.tsx` dateCellRender：
   - `isSelected` 改为 `selectedRange.type === 'day' && dateStr === startDate === endDate`，周选中时周内日期 cell 全部 `data-selected='false'`
   - 选中态 `backgroundColor` 改为 `rgba(22, 119, 255, 0.12)` 叠加任务类型背景；边框 `boxShadow` 改为半透明蓝 `rgba(22, 119, 255, 0.5)`

## 6 项轻量验证检查

| # | 检查项 | 结果 | 证据 |
|---|--------|------|------|
| 1 | tasks.md 全部任务已完成 `[x]` | PASS | 4 tasks `[x]` / 0 tasks `[ ]` |
| 2 | 改动文件与 tasks.md 描述一致 | PASS | 2 web 源文件 + 3 OpenSpec 产物，与 tasks 1–4 描述一致 |
| 3a | web 构建通过 | PASS | `npm run build` exit 0（5.45s） |
| 3b | server 编译 | 不适用 | 本 hotfix 与后端无关 |
| 4 | 相关测试通过 | PASS | `npm test` → 18 files / 194 tests passed（baseline 187 + 7 新增），exit 0 |
| 5 | 无明显安全问题 | PASS | 改动不涉及：凭证/密钥/PII、新增网络/IO、unsafe 操作、dangerouslySetInnerHTML、跨域渲染。本修复纯本地 UI 视觉与 `isSelected` 判定，无攻击面变化 |
| 6 | 代码审查策略 | 跳过 | review_mode=off；skip 原因记录在本文件顶部 |

## RED → GREEN 循环证据

### RED（修复前）

`npx vitest run src/parent/components/__tests__/TaskCalendar.test.tsx -t "fix-calendar-week-row-highlight-bg"` 输出 4 failing：

1. `选中周时:周号行 backgroundColor 是半透明浅蓝`：期望 `rgba(22, 119, 255, 0.18)` 与 `boxShadow === ''`，实际 `boxShadow` 为 primary 边框
2. `选中周时:该周内所有日期 cell 的 data-selected 均为 false (不联动日期)`：7/1–7/4 实际 `data-selected='true'`（范围重叠命中）
3. `选中一天时:该日期 cell backgroundColor 是半透明浅蓝`：期望 `backgroundColor` 包含 `rgba(22, 119, 255`，实际为空
4. `选中一天时:所有周号行 data-selected 均为 false`：含今日的周号行 `data-selected='true'`（日范围与周范围重叠）

### GREEN（修复后）

- 上述 4 条新增回归断言全部 PASS。
- 全部 190 个原有测试仍 PASS（无回归）。
- 3 条同时新增的语义验证（day 选中时其他日期不高亮、周内日期保留任务类型背景、无 selection 时全无高亮）全部 PASS。

## 根因消除核查

| 根因 | 修复 | 证据 |
|------|------|------|
| 1. `WeekNumberColumn.isSelected` 范围重叠导致 day 选中时命中该周 | `isSelected` 增加 `selectedRange.type === 'week'` 判定 | 新测试 #4「选中一天时:所有周号行 data-selected 均为 false」PASS |
| 2. `dateCellRender.isSelected` 范围包含导致周选中时周内日期 cell 全部命中 | `isSelected` 改为 `type === 'day' && 双等 startDate/endDate` 单点判定 | 新测试 #2「选中周时:该周内所有日期 cell 的 data-selected 均为 false」PASS |
| 3. 选中态视觉用 primary boxShadow 而非半透明浅蓝背景 | 改 `backgroundColor: rgba(22, 119, 255, 0.12|0.18)` + 半透明边框 | 新测试 #1（周选中）、#3（日选中）断言 PASS |

## 已知未处理场景

- 视觉层正确性由 jsdom 限制无法自动断言（`backgroundColor` 字符串写入校验已涵盖；视觉效果须用户在浏览器复核，archive 前最终确认环节执行）。
- 颜色搭配在 antd 深色主题下可视性需后续验证；本次 hotfix 范围仅覆盖 antd 默认浅色主题。

## 浏览器手工验证（待用户在 archive 归档前最终确认环节完成）

- `pnpm --filter web dev` → `/parent/tasks`
- 默认：今日 cell 与 antd 内置样式双层高亮（已由 fix-calendar-default-current-date 覆盖）
- 点击某周号：该周号行显示半透明浅蓝矩形，该周内日期 cell 不显示高亮（仅任务类型背景）
- 点击某日 cell：仅该 cell 显示半透明浅蓝半覆盖 + 半透明边框，其他日期全部不高亮，所有周号行不高亮

## 验证结论

**PASS**：6 项检查全部通过，无 CRITICAL/IMPORTANT 问题；fix 已提交（commit `b73e47f`），可进入 archive 阶段。
