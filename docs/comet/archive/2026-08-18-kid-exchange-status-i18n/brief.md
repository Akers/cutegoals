# Outcome

孩子端兑换历史页面 (`/child/exchanges`) 中每条兑换记录的状态字段，原样显示英文值 `fulfilled`、`pending_fulfillment`、`cancelled`。改为显示中文：`已核销`、`待核销`、`已取消`。

# Scope

- 修改 `web/src/child/pages/index.tsx`：
  - 引入家长端同款的 `EXCHANGE_STATUS_META` 表（`{ PENDING_FULFILLMENT: '待核销', FULFILLED: '已核销', CANCELLED: '已取消' }` + Tag 颜色）。
  - `ChildExchangesPage` 中渲染状态的行（line 756）改用新的 `exchangeStatusLabel` 与 `exchangeStatusColor`，不再走任务通用的 `statusLabel`。

# Non-goals

- 不修改后端枚举值、契约或响应字段。
- 不修改家长端 `/parent/exchanges` 的展示。
- 不修改任务 (`Task`)、奖品 (`Prize`) 等其他模块的状态渲染逻辑。
- 不引入新的依赖或 i18n 框架；保持与家长端完全一致的内联映射表模式。
- 不修改其它子端页面（任务/奖品等）的 statusLabel 行为；仅替换兑换记录那一处渲染。

# Acceptance examples

- 当后端返回 `status: "FULFILLED"` 时，孩子端兑换历史对应记录渲染"已核销"。
- 当后端返回 `status: "PENDING_FULFILLMENT"` 时，渲染"待核销"。
- 当后端返回 `status: "CANCELLED"` 时，渲染"已取消"。
- 渲染的颜色与家长端保持一致（green / orange / default）。
- 任务列表 (`ChildTasksPage`) 的状态渲染不受影响。
- 不再出现英文 `fulfilled` / `pending_fulfillment` / `cancelled` 文本。

# Constraints and invariants

- 与家长端 `web/src/parent/pages/index.tsx:260-265` 的 `EXCHANGE_STATUS_META` 表保持完全一致（label + color），避免家长端/孩子端文案漂移。
- 后端枚举值不变：`PENDING_FULFILLMENT`、`FULFILLED`、`CANCELLED`（与 `ExchangeStatus` 枚举一致）。
- 最小化修改：仅 `ChildExchangesPage` 状态渲染处使用新映射，不重构 `statusLabel`。
- 保留 `ChildTasksPage` 等其他位置对 `statusLabel` 的既有调用。

# Decisions

- 复用家长端的 `EXCHANGE_STATUS_META` 表结构（不在两个文件间建立跨文件 import — 这会引入 web 端的模块耦合；保留两份独立的常量定义以维持现有模块边界）。
- 新增 `exchangeStatusLabel(status)` 与 `exchangeStatusColor(status)` 两个 helper 函数（与家长端函数命名约定一致），未在文件中匹配到状态时回退到原状态字符串（保持与家长端相同的防御性回退行为）。
- 不修改 `statusLabel` 函数本身；只在该处渲染兑换状态时改用新 helper。
- [CONFIRMED 2026-08-18 by user]: 用户确认按上述方案（复用家长端表结构 / 新增 exchangeStatusLabel + exchangeStatusColor / 不改 statusLabel）实施。

# Open questions

无。

# Verification expectations

- V1: TypeScript 编译通过（`cd web && npx tsc --noEmit`），无新错误。
- V2: 单元/集成测试无需新增（映射是纯函数、已由家长端同样形态覆盖；视觉对比可在浏览器手验）。
- V3: 通过 `git diff` 验证改动范围仅限 `web/src/child/pages/index.tsx`，且未触及后端、其他端代码。
- V4: 通过字符串 grep 验证 `EXCHANGE_STATUS_META`、`exchangeStatusLabel`、`exchangeStatusColor` 正确出现于子端，且 line 756 不再调用 `statusLabel(ex.status.toLowerCase())`。
- V5: dev server 实跑后视觉确认 `/child/exchanges` 页面状态为"待核销/已核销/已取消"中文标签，且任务页面不受影响（人工验证步骤，记录于 verification.md）。