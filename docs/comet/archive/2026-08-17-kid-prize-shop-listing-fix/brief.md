# Outcome

孩子端「积分商城」(`/child/prizes`) 能正确列出当前家庭下所有已配置、已启用、未删除、且库存大于 0 的奖品，每个奖品显示名称、描述、积分价、库存，并提供可用的兑换入口；空列表时显示「商城暂无奖品」。

# Scope

- 修复 `server/prize` 服务端奖品查询 / 孩子端渲染之间的契约不匹配，使 `ChildPrizesPage` 能正确接收并渲染奖品清单。
- 服务端 `GET /api/prizes/available` 响应字段与 `Prize` 单对象字段名沿用现有契约（`content / page / pageSize / totalElements / totalPages`、`stock`），不调整与家长端共享的分页结构。
- 孩子端 `web/src/child/pages/index.tsx` 中的 `ChildPrizesPage` 改用与家长端一致的 `usePaginatedData` 数据消费方式，修正字段映射。
- 涉及文件（暂定，最终以 Build 阶段为准）：
  - `/home/akers/projects/cutegoals/web/src/child/pages/index.tsx`
  - `/home/akers/projects/cutegoals/server/prize/src/main/java/com/cutegoals/prize/service/PrizeService.java`（仅在缺失测试补齐时小改）
  - `/home/akers/projects/cutegoals/server/prize/src/test/java/com/cutegoals/prize/service/PrizeServiceTest.java`（新增 `queryAvailablePrizes` 单测覆盖分页/过滤/库存筛选）

# Non-goals

- 不修改 `toPrizeMap` 的字段集合（保持现有 `stock / titleImage / detailImage / prizeType / prizeCategory / typeConfig / validFrom / validTo`）。
- 不重做 `queryAvailablePrizes` 的过滤条件（`enabled=true, deleted=false, stock > 0` 视为正确业务规则）。
- 不调整家长端 `/api/prizes` 列表契约，避免双向影响。
- 不引入新的依赖、不调整权限模型（仍要求 `ROLE_CHILD`）。
- 不重做视觉样式，仅保证渲染路径可用；前端交互（兑换 Modal、库存展示）原样保留。

# Acceptance examples

- **A1：** 家庭下存在 1 个启用、未删除、`stock=10`、积分价 50 的奖品 → 孩子端 `/child/prizes` 请求 `GET /api/prizes/available?page=1&pageSize=20` 返回 `content: [{ id, name, description, pointsCost: 50, stock: 10, ... }]`，页面正确渲染该奖品卡片，「库存 10」，「50 积分 · 库存 10」文本无误，兑换按钮可点击。
- **A2：** 家庭下存在多个满足过滤条件的奖品（≥3） → `content` 数组按 `createdAt DESC, id DESC` 排序，页面全部渲染，每条卡片字段显示正确。
- **A3：** 家庭下无任何满足过滤条件的奖品（如奖品被禁用、或 `deleted=true`、或 `stock=0`） → 接口返回 `content: []`，页面显示 `Empty` 「商城暂无奖品」。
- **A4：** 奖品 `stock=0` → 不出现在孩子端列表（服务端过滤生效），且家长端仍可看到该奖品（不在本 change 范围，但需确保家长端列表契约未变）。
- **A5：** 当 `childId` 为空（未登录或家长身份）调用 `GET /api/prizes/available` → 后端返回 `403 FORBIDDEN`（`ROLE_CHILD` 强制）；孩子端组件按现有行为不发起请求（`usePaginatedData` 通过空 childId 短路）。

# Constraints and invariants

- 后端 `PrizeService.queryAvailablePrizes` 的过滤规则（`familyId=eq, enabled=true, deleted=false, stock > 0`）和排序（`createdAt DESC, id DESC`）保持不变。
- 服务端分页响应结构 `{content, page, pageSize, totalElements, totalPages}` 保持不变（与 `/api/prizes` 家长端一致）。
- `Prize` 单对象通过 `PrizeController.toPrizeMap` 输出，字段集合保持不变（`availableStock` 不在服务端字段中，孩子端需按 `stock` 读取）。
- 权限要求保持 `ROLE_CHILD` 独占，不允许家长或匿名访问。
- 孩子端组件必须沿用现有 `useApi / usePaginatedData / getClient` 调用栈，不引入新的网络层。
- 现有 `Empty` / `StateHandler` 错误处理、空列表文案「商城暂无奖品」、Modal「确认兑换」交互保留。

# Decisions

- **D1 字段命名：** 孩子端 `Prize` 接口将 `availableStock` 重命名为 `stock`，与服务端 `toPrizeMap` 一致。理由：服务端无 `availableStock` 字段；命名差异是开发期错配，未体现业务语义差异（当前 `Prize` 表只有 `stock` 一列，无独立的"预留/可用"概念）。
- **D2 消费契约：** 孩子端改用家长端同款 `usePaginatedData<Prize>('/prizes/available')`，自动处理 `content` 分页字段；不再使用裸 `useApi<{ items: Prize[] }>`。理由：保持与 `/api/prizes` 家长端一致的契约消费方式，且 `usePaginatedData` 已封装分页状态。
- **D3 后端形态：** 不修改 `queryAvailablePrizes` 的响应形状（保留 `content`），不调整 `toPrizeMap`。理由：避免与家长端 `/api/prizes` 出现两套响应契约；问题完全出在前端消费契约上。
- **D4 回归测试：** 新增 `PrizeServiceTest.shouldQueryAvailablePrizes`（覆盖 `stock > 0` 过滤、`enabled/deleted` 过滤、分页参数、排序），防止回归。当前测试集未覆盖此方法。
- **D5 不改路由：** `GET /api/prizes/available` 路径不变，孩子端请求路径保持 `/prizes/available`（由前端 `api/client` 拼接 `BASE_URL`）。

# Open questions

（已澄清，无阻塞项；用户确认后即可推进。）

# Verification expectations

- **V1 命令：**
  - `cd /home/akers/projects/cutegoals/server/prize && mvn -q -Dtest=PrizeServiceTest test` → 新增的 `shouldQueryAvailablePrizes` 用例通过；既有 13 条用例全部通过。
- **V2 命令：**
  - `cd /home/akers/projects/cutegoals/server && mvn -q -pl prize -am test` → prize 模块全量测试通过。
- **V3 命令：**
  - `cd /home/akers/projects/cutegoals/web && npx tsc --noEmit -p tsconfig.json` → TypeScript 编译通过，无新增类型错误。
- **V4 命令：**
  - `cd /home/akers/projects/cutegoals/web && npx eslint src/child/pages/index.tsx` → 无新增 ESLint 错误。
- **V5 手工验收：** 启动家长端新增奖品 → 用孩子端访问 `/child/prizes` 看到奖品；将奖品库存改 0 → 孩子端列表立即隐藏；将奖品 disabled → 孩子端列表立即隐藏；将奖品删除 → 孩子端列表立即隐藏。