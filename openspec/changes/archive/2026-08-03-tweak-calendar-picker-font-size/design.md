# Design: tweak-calendar-picker-font-size

## Context

`web/src/parent/components/TaskCalendar.tsx` 实现 `/parent/tasks` 任务分配页的日历组件，包含三类与「日历选择」直接相关的控件：

| 控件 | 当前实现位置 | 当前字号 |
|------|------|------|
| 月份导航标题 `YYYY年M月` | `CalendarHeader`（line 65-77） | 无显式 `fontSize`（继承 antd 默认，约 14px） |
| 周号列单元格 `第N周` | `WeekNumberColumn` 渲染的 `div`（line 165-189） | `fontSize: 12`（行内 style 显式声明） |
| 月份导航栏中央文本（`<' '>'` 中间） | `TaskCalendar` 主组件导航栏 `span`（line 385） | 无显式 `fontSize`（继承 antd `Button` 上下文默认） |
| 日期单元格文本 `1-31` | antd `<Calendar>` 内部 `.ant-picker-calendar-date-value`（line 312-324） | antd token `fontSize`（约 14px） |

任务徽章 `task-badge-*`（line 256-280，`fontSize: 7`）属于「任务分布信息」，不属于「日历选择控件」，本 change 不动。

家长端反馈上述控件偏小、长时操作可读性差，需要在不改变颜色、边框、间距等其它视觉属性的前提下，将字号统一增大 2 号（+2px）。

## Goals / Non-Goals

**Goals**
- 上述 4 类日历选择控件字号在现有基础上统一 +2px
- 改动聚焦在 `TaskCalendar.tsx` 单文件，与现有 `<style>` 块组织方式一致
- 不影响其它视觉属性、不影响日历状态机与 API 请求、不引入新依赖
- 现有 `TaskCalendar.test.tsx` / `ParentTasksPage.test.tsx` 不含字号断言，无需修改测试

**Non-Goals**
- 不修改 antd `ConfigProvider` 主题 token（避免影响全局）
- 不修改 `task-calendar-current-month` 已有的颜色 / 边框 CSS 规则
- 不修改任务徽章、加载 / 错误态、占位行等非选择控件
- 不调整 cell 高度或周号列对齐（已在 fix-build 第三次迭代用 `height: 27` 占位行严格对齐 antd 实测值，本次不加额外 padding）

## Decisions

### 决策 1：在 `<style>` 块内通过 antd className 选择器统一覆盖

- **选择**：日期单元格文本通过 `.task-calendar-current-month .ant-picker-cell .ant-picker-calendar-date-value { font-size: calc(<base> + 2px) }` 在 `<style>` 块内覆盖；周号列单元格沿用现有行内 `fontSize: 12` → `fontSize: 14`；月份导航标题与导航栏中央文本通过 `<style>` 块新增 `font-size: calc(<base> + 2px)` 规则
- **理由**：日期单元格由 antd 渲染，外部行内 style 无法挂到 antd 内部元素；现有 `<style>` 块已经按此模式管理 today / selected 视觉，本次延续同模式
- **备选 A（行内 style 注入到 antd `Calendar` props）**：antd 5.x 不暴露 calendar `dateCellRender` 容器之外的 cell DOM 行内 style 钩子，放弃
- **备选 B（新建 `TaskCalendar.module.css`）**：会增加文件 + 增加 import 验证负担（`scripts/check-css-loads.mjs`），与现有单文件 `<style>` 块组织不符，放弃

### 决策 2：基础字号不写死，使用相对值 +2

- **选择**：周号列从 `12` → `14`；日期单元格在现有 `.ant-picker-calendar-date-value` 上叠加 `font-size` 覆盖到基础值 +2；月份标题与导航栏文本同样叠加 +2
- **理由**：用户原话「增大 2 号」语义为 +2px；不引入 CSS variable（避免与 antd token 系统耦合）
- **备选**：使用 `calc(var(--ant-font-size) + 2px)`，但 antd 5 token 不一定下放到所有内部元素，验证成本高，放弃

### 决策 3：不调整 cell 高度与占位行

- **选择**：保持现有 `week-column-weekday-spacer-{year}-{month}` `height: 27` 与 `week-column-bottom-padding-*` `height: 8` 不变
- **理由**：font-size +2 不会触发 cell 整体高度变化（antd cell 高度由 `controlHeightSM * 0.75 = 24 * 0.75 = 18px` 之外的 `DatePicker body padding` 等决定，本次不动布局），且不改动对齐的几何基础
- **风险**：若实测发现 cell 文本被截断，再回退决策 3

## Risks / Trade-offs

- [字号+2 后 cell 文本可能贴边] → 仍由 antd padding 控制；若实测有截断，把 `.task-calendar-current-month .ant-picker-calendar-date-value` 的 `line-height` 同步 +2 即可，本次不动
- [月份导航标题字号+2 与 antd `<Button>` 视觉不再对齐] → 月份标题是纯 `<div>`，按钮仍保留 antd 默认；视觉差异属于用户主动选择「日历选择控件更大」的可接受结果
- [E2E 视觉快照若存在 `text/font-size` 断言会失败] → 按既有 E2E fixture 检查（`e2e/`）；若失败，按 `tweak:` commit 同步更新 fixture（snapshot 类断言）

## Migration Plan

无需迁移。本 change 不改 API、不改 DB、不改 theme token、不改依赖。部署即生效：

1. 合并 PR → 触发 CI（含 `web/scripts/check-css-loads.mjs`、`pnpm typecheck`、`pnpm test web`）
2. 部署后家长访问 `/parent/tasks` 即看到新字号
3. 回滚：revert 提交即可

## Open Questions

- 「+2 号」是否含 antd `Calendar` 日期单元格的徽章字号？—— 按本次决定**不动**徽章（徽章属于「任务分布信息」而非「日历选择控件」）。如家长端后续反馈徽章也需要变大，由后续 tweak 单独处理。