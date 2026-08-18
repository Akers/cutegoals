# kid-prize-shop — 孩子端积分商城奖品清单

## 目的

孩子端「积分商城」(`/child/prizes`) 能完整、准确地展示当前家庭下所有可兑换的奖品；当无奖品时显示空列表占位；当某个奖品因禁用、删除或库存为 0 而不再可兑换时，孩子端不展示该条目。

## 接口契约

- **请求：** `GET /api/prizes/available?page={int}&pageSize={int}`，必须携带 `ROLE_CHILD` 会话；非该角色返回 `403 FORBIDDEN`。
- **响应（与 `GET /api/prizes` 家长端一致的分页结构）：**

  ```json
  {
    "content": [
      {
        "id": 1,
        "familyId": 1,
        "name": "雪糕",
        "description": "奖励雪糕一支",
        "image": null,
        "pointsCost": 50,
        "stock": 10,
        "enabled": true,
        "deleted": false,
        "createdAt": "2026-08-01T10:00:00",
        "updatedAt": "2026-08-01T10:00:00",
        "prizeType": null,
        "prizeCategory": null,
        "titleImage": null,
        "detailImage": null,
        "validFrom": null,
        "validTo": null,
        "typeConfig": null
      }
    ],
    "page": 1,
    "pageSize": 20,
    "totalElements": 1,
    "totalPages": 1
  }
  ```

- **过滤规则（服务端 `PrizeService.queryAvailablePrizes`，不变）：**
  - `familyId` = 当前家庭 ID
  - `enabled = true`
  - `deleted = false`
  - `stock > 0`（严格大于 0；库存为 0 不展示）
- **排序规则（不变）：** `createdAt DESC, id DESC`。
- **错误响应：**
  - `401 UNAUTHORIZED` — 未登录。
  - `403 FORBIDDEN` — 当前角色不是 `ROLE_CHILD`。
  - `400 VALIDATION_FAILED` — `pageSize` 越界（不在 1..`MAX_PAGE_SIZE` 之间）。

## 数据消费契约（孩子端）

- 孩子端组件 `ChildPrizesPage`（位于 `web/src/child/pages/index.tsx`）必须使用 `usePaginatedData<Prize>('/prizes/available')` 消费分页结果，从 `data.content` 中读取奖品数组（不使用裸 `useApi<{ items: Prize[] }>`）。
- `Prize` 接口字段以服务端 `PrizeController.toPrizeMap` 输出为准：
  - 必含：`id, name, description, pointsCost, stock, enabled`。
  - 可选：`image, familyId, deleted, createdAt, updatedAt, prizeType, prizeCategory, titleImage, detailImage, validFrom, validTo, typeConfig`。
  - **不包含 `availableStock`**：孩子端若需展示库存，必须读取 `prize.stock`。

## 渲染行为

- 每个奖品卡片显示：
  - 名称（`prize.name`，粗体）。
  - 描述（`prize.description`，次级字号 12）。
  - 积分价与库存：「`{pointsCost}` 积分 · 库存 `{stock}`」。
- 兑换按钮：
  - 当 `childPointsBalance < pointsCost` 或 `stock <= 0` 时禁用。
  - 点击打开「确认兑换」Modal，文案为「确定要用 `{pointsCost}` 积分兑换「`{name}`」吗？」，二次确认后调用 `POST /exchanges/direct`。
- 兑换成功后：
  - 调用 `refetchBalance()` 刷新积分余额。
  - 调用 `refetchPrizes()` 刷新奖品列表（库存扣减后该奖品仍可继续展示直至库存为 0）。
  - 跳转 `/child/exchanges`。
- 列表为空时显示 `Empty` 占位，文案「商城暂无奖品」。
- 加载、错误、空状态统一通过 `StateHandler` 组件处理。

## 权限与隔离

- 孩子端「积分商城」仅对 `ROLE_CHILD` 开放。
- 服务端 `getSingleFamilyId()` 沿用现有实现（`familyMapper.selectList(null).get(0)`），依赖单家庭单孩子演示场景；不调整。
- 任何对孩子端奖品的写操作（如调整库存、修改）均在家长端 `/prizes` 完成，本规格不涉及。

## 错误处理

- `usePaginatedData` 返回的 `error` 通过 `StateHandler` 渲染「加载失败」并提供重试按钮。
- 离线状态由 `useOnline()` 检测，渲染「当前处于离线状态」并阻止后续请求。

## 回归测试要求

- `server/prize/src/test/java/com/cutegoals/prize/service/PrizeServiceTest.java` 新增 `shouldQueryAvailablePrizes` 用例，覆盖：
  - 过滤 `enabled=true, deleted=false, stock > 0`。
  - 分页参数（`page=1, pageSize=20`）正确传递给 `PrizeMapper.selectPage`。
  - 响应 Map 包含 `content, page, pageSize, totalElements, totalPages` 五个字段。
  - 排序参数为 `createdAt DESC, id DESC`。
- 现有 13 条 `PrizeServiceTest` 用例全部保留并通过。

## 不在本规格范围

- 奖品图片、分类、有效期等扩展字段在孩子端的样式化呈现（数据已下发，UI 暂不展示）。
- 库存为 0 时显示「已抢光」独立样式（当前沿用「兑换按钮禁用」行为）。
- 奖品按积分价升降序、分类筛选等孩子端排序 / 筛选交互。
- 孩子端奖品详情页（本规格只覆盖列表渲染）。