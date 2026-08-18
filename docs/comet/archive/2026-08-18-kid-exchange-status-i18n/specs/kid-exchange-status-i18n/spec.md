# Capability: kid-exchange-status-i18n

子端兑换历史页面的状态字段应显示中文标签，与家长端 `EXCHANGE_STATUS_META` 文案 + 颜色保持一致。本规格描述该能力的完整行为（归档后视图）。

## 范围

仅作用于 `web/src/child/pages/index.tsx` 内 `ChildExchangesPage` 组件的兑换记录状态渲染。后端枚举与响应字段不变；家长端、其他子端页面、其他模块的状态渲染均不受影响。

## 状态映射表

| 后端状态值 | 中文标签 | Tag 颜色 |
|---|---|---|
| `PENDING_FULFILLMENT` | 待核销 | orange |
| `FULFILLED` | 已核销 | green |
| `CANCELLED` | 已取消 | default |

定义位置：`web/src/child/pages/index.tsx` 顶部常量 `EXCHANGE_STATUS_META: Record<string, { label: string; color: string }>`。

## Helper 函数

- `exchangeStatusLabel(status: string): string`
  - 命中表时返回 `EXCHANGE_STATUS_META[status].label`。
  - 未命中时回退到原 `status` 字符串（与家长端 `exchangeStatusLabel` 一致的防御性行为）。

- `exchangeStatusColor(status: string): string | undefined`
  - 命中表时返回 `EXCHANGE_STATUS_META[status].color`。
  - 未命中时返回 `undefined`（由 `<Tag color={...}>` antd 默认处理）。

## 渲染契约

`ChildExchangesPage` 中兑换记录的状态列必须使用：

```tsx
<Tag color={exchangeStatusColor(ex.status)}>
  {exchangeStatusLabel(ex.status)}
</Tag>
```

禁止使用 `statusLabel(ex.status.toLowerCase())` 渲染兑换状态（该 helper 不识别兑换枚举）。

## 不变量

- 家长端 `web/src/parent/pages/index.tsx:260-265` 的 `EXCHANGE_STATUS_META` 表与本规格中的子端表保持**完全一致**（label 与 color）。后续修改必须同步双端。
- 后端 `ExchangeStatus` 枚举不变：`PENDING_FULFILLMENT` / `FULFILLED` / `CANCELLED`。
- `ChildTasksPage` 等其他子端页面的 `statusLabel` 调用方不受影响；保持现有行为。
- `usePaginatedData` / `useApi` / `useChildId` 等 helper 不变。
- 后端 `/api/exchanges` 响应结构不变（含 `content[].status` 字段）。

## 验收示例

- 后端返回 `status: "FULFILLED"` → 渲染"已核销"+ green Tag。
- 后端返回 `status: "PENDING_FULFILLMENT"` → 渲染"待核销"+ orange Tag。
- 后端返回 `status: "CANCELLED"` → 渲染"已取消"+ default Tag。
- 后端返回未知状态 `status: "FOO"` → 渲染 "FOO"（无 Tag 颜色匹配，回退原始字符串）。
- 任务列表 (`ChildTasksPage`) 仍使用任务通用 `statusLabel`，行为不变。
- `ChildPrizesPage` 等其他子端页面不受影响。
- 家长端 `/parent/exchanges` 渲染不变（独立常量子端/父端两份）。

## 决策

- 不引入共享 web 模块（`web/src/shared`）抽取 `EXCHANGE_STATUS_META`；当前项目 web 端无此模块边界，新增会扩大变更范围。子端与父端各自维护独立但等价的常量。
- 不修改子端通用的 `statusLabel` 函数本体；保留其对任务等其他模块的映射能力。
- 不修改后端枚举或契约；纯前端渲染层修复。

## 非目标

- 后端契约/枚举改造。
- 家长端 `/parent/exchanges` 渲染改造。
- 其他子端页面（任务、奖品、积分）状态渲染改造。
- 引入 i18n 框架（如 react-i18next）。
- 抽取共享 web 模块。

## 已知限制

- 项目级 Lombok `annotationProcessorPaths` 配置问题导致 `mvn test` 失败（与本 change 无关）。
- 本能力为纯前端渲染层修复，无服务端回归测试覆盖；视觉验证依赖 dev server 实跑 + 浏览器手验。