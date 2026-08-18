# Capability: child-exchanges-listing

孩子端 `/child/exchanges` 兑换历史列表的消费契约与展示形态。

## 范围

- 显示当前登录孩子账户在当前家庭下的全部兑换记录（直接兑换 + 盲盒兑换）。
- 消费后端 `GET /api/exchanges?childId={X}&page=1&pageSize=20` 的统一分页响应，结构为 `{content, page, pageSize, totalElements, totalPages}`。
- 每条记录展示：标题（真实奖品/盲盒名称）、类型标签（奖品 / 盲盒）、消耗积分、状态、创建时间。

## 行为

### 列表消费

- 消费前端 `usePaginatedData<Exchange>(path)` 包装（`web/src/child/pages/index.tsx:49-62`），从 `data.content` 读取记录；不使用 `useApi` 直接读取 `items` 字段。
- `usePaginatedData` 必须正确处理传入路径已包含查询字符串的情况：当 `path` 中已存在 `?` 时（如 `/exchanges?childId=2`），分页参数必须以 `&` 追加（`/exchanges?childId=2&page=1&pageSize=20`），不得再以 `?` 起始（否则后端 `Long` 类型转换失败）。
- `usePaginatedData` 在 `path` 不含 `?` 时仍以 `?` 起始分页参数（保留现有 `/prizes/available` 等调用方行为）。
- 缺 childId 时不发请求（`useChildId` 返回 undefined，path 传空字符串触发 `useApi` 跳过）。

### 渲染契约

- 标题：直接展示 `Exchange.targetName`。
  - 来源：后端 `ExchangeService.queryExchanges` 在响应中根据 `type` 取 `snapshot.prizeName`（DIRECT）或 `snapshot.poolName`（BLIND_BOX）。
- 类型标签：根据 `Exchange.type` 渲染：
  - `BLIND_BOX` → "盲盒"
  - `DIRECT`（含历史 `PRIZE`）→ "奖品"
- 积分：展示 `Exchange.costPoints`。
- 状态：使用 `statusLabel(ex.status.toLowerCase())` 映射中文。
- 创建时间：展示 `Exchange.createdAt`（ISO 字符串）。
- 缺快照时 backend 降级：`targetName === "奖品 #{prizeId}"` / `"盲盒 #{poolId}"`。

### 后端契约

- `GET /api/exchanges` 列表响应在原有 Page 形状上**新增** `content[].targetName: string` 字段。
- 现有字段（`id, childId, familyId, type, status, costPoints, prizeId, poolId, resultPrizeId, idempotencyKey, finalizedAt, finalizedBy, cancelledAt, cancelledBy, createdAt, updatedAt`）保持不变。
- 多条记录使用单次 `WHERE exchange_id IN (...)` 批量取快照，禁止循环 N+1。
- 缺快照时降级为 `奖品 #{prizeId}` / `盲盒 #{poolId}`，绝不返回 null 或空字符串。

### 权限

- 角色 `CHILD` 只能查自己所属 childId 的记录（由 controller `getExchange` 既有 `sessionChildId` 校验保证，继续沿用）。
- 角色 `PARENT` / `INSTANCE_ADMIN` 继续查询家庭全部记录（行为不变）。

### TypeScript 接口

`Exchange` 接口字段必须与后端 JSON 字段 1:1 对齐：

```typescript
interface Exchange {
  id: number;
  childId: number;
  familyId: number;
  type: 'DIRECT' | 'BLIND_BOX';
  status: 'PENDING_FULFILLMENT' | 'FULFILLED' | 'CANCELLED' | string;
  costPoints: number;
  idempotencyKey: string;
  prizeId: number | null;
  poolId: number | null;
  resultPrizeId: number | null;
  finalizedAt: string | null;
  finalizedBy: number | null;
  cancelledAt: string | null;
  cancelledBy: number | null;
  createdAt: string;
  updatedAt: string;
  targetName: string;
}
```

`status` 保留 `string` 兜底（兼容任意后端值）。

### 空状态

- 当 `content.length === 0` 时显示 `<Empty description="还没有兑换记录" />`。

### 错误状态

- 复用 `StateHandler` 组件：loading 显 Spin，error 显 `<Result status="error" />` 并提供 `refetch` 重试按钮。

## 不变性

- 后端 Page 形状契约不被破坏（`{content, page, pageSize, totalElements, totalPages}`）。
- 不修改 `Exchange` 实体字段、迁移或数据库结构。
- 不影响 `GET /api/exchanges/{id}` 详情接口与 `GET /api/exchanges` 家长端消费（家长端当前用 `奖品 #{prizeId}` 渲染，不传 `targetName` 字段也兼容）。

## 非目标

- 家长端页面不同步升级（保留 `奖品 #{prizeId}` 风格）。
- 不增加分页控件（孩子端一屏看完）。
- 不增加缓存层。
- 不重命名数据库字段、实体字段或迁移。
- 不重构 `usePaginatedData` 为通用 hook；保留单参数签名。