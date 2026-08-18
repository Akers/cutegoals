# Outcome

孩子端 `/child/exchanges` 兑换历史页面能够正确查询并展示当前孩子名下的兑换记录（直接兑换 + 盲盒兑换），卡片标题使用真实奖品/盲盒名称（而不是 ID），并显示消耗积分、状态和创建时间。

# Scope

## 前端
- 修改 `web/src/child/pages/index.tsx` 中的 `ChildExchangesPage`（line 717），使其消费后端分页契约 `{content, page, pageSize, totalElements, totalPages}`，与家长端保持一致。
- 同步修正 `Exchange` 接口字段（`type: 'DIRECT'|'BLIND_BOX'`，`costPoints`、`targetName`、`prizeId`、`poolId`），使其与后端真实返回一致。
- 渲染卡片标题使用 `targetName`（奖品名/盲盒池名），不显示 ID。

## 后端
- 修改 `server/exchange/src/main/java/com/cutegoals/exchange/service/ExchangeService.java` 的 `queryExchanges` 方法（line 447），在分页结果基础上批量关联 `exchange_snapshot` 表中的名称，输出 `targetName` 字段：
  - `type='DIRECT'` → `targetName = snapshot.prizeName`
  - `type='BLIND_BOX'` → `targetName = snapshot.poolName`
  - 缺快照时回退至 `奖品 #{prizeId}` / `盲盒 #{poolId}`，避免显示空字符串。
- 新增 `ExchangeSnapshotMapper` 方法 `findByExchangeIds(List<Long> ids)`，按 `exchange_id IN (...)` 一次性批量返回快照，避免 N+1 查询。
- 列表响应继续以 Page 形状返回，**只新增 `targetName` 字段**（不影响 `id/childId/type/costPoints/status/...` 现有字段）。

## 测试
- `ExchangeServiceTest` 当前对 `queryExchanges` 无覆盖（grep 0 命中）。新增 2 个单元测试：
  - `shouldQueryExchangesWithTargetNameFromSnapshot` — 验证 DIRECT 类型返回 `prizeName`、BLIND_BOX 返回 `poolName`。
  - `shouldFallbackToIdLabelWhenSnapshotMissing` — 验证缺快照时回退到 `奖品 #{prizeId}` 格式。

# Non-goals

- 不修改 `Exchange` 实体（Prize/Pool 字段不冗余到 exchange 表）。
- 不动 `ExchangeController` 既有 GET /parent/exchanges 行为（家长端目前显示 `奖品 #{prizeId}` 即可，本次不强制同步家长端；如果后续想统一，也可另开 change）。
- 不修改 `ExchangeSnapshot` 实体或迁移。
- 不动其他孩子端页面或家长端页面（家长端是否同步升级**留待后续**）。
- 不添加分页控件（孩子端暂保持"一屏看完"）。
- 不增加缓存层（低 QPS 场景，关联成本可控）。

# Acceptance examples

- AC1: 后端向后端真实调用：登录孩子账号 → 直接兑换 1 个奖品 → 调 `GET /api/exchanges?childId=X` → 列表第 1 条 `content[0].targetName === "<该奖品名>"`，`type==='DIRECT'`，`costPoints>=0`。
- AC2: 后端盲盒兑换同上，列表 `content[0].targetName === "<该盲盒池名>"`，`type==='BLIND_BOX'`，`costPoints>=0`。
- AC3: 后端缺快照场景（构造/等待异常数据）：`targetName` 不为空，回退为 `奖品 #{prizeId}` 或 `盲盒 #{poolId}`。
- AC4: 后端返回结构顶层仍为 `{content, page, pageSize, totalElements, totalPages}`（契约不变）。
- AC5: 前端 `/child/exchanges` 看到种子数据集：1 条直接兑换 + 1 条盲盒兑换，渲染 2 张卡片，标题分别为真实奖品名 / 盲盒池名，副标题显示 `奖品 · N 积分 · 时间` 或 `盲盒 · N 积分 · 时间`，状态 tag 显示中文。
- AC6: 前端空列表渲染 `<Empty description="还没有兑换记录" />`。
- AC7: `web/src/child/pages/index.tsx` 通过 `tsc --noEmit` 无新增错误；与本 change 相关的 `Exchange` 接口字段全部存在。
- AC8: `ExchangeServiceTest` 集成新测试，2 个新 case 全部通过；已有测试无回归。

# Constraints and invariants

- 复用同文件已有的 `usePaginatedData<T>` 包装（line 49-62），不重复实现分页 helper。
- 后端批量查询用 `WHERE exchange_id IN (...)` 单次查询，禁止 N+1 循环 `findByExchangeId`。
- 后端缺失快照必须降级展示，绝不返回 `targetName=null` 或空字符串（家长端 `ParentExchangesPage` 也有此保证）。
- 卡片保留现有 antd 视觉风格（`Card size="small"`，`Tag` 状态标签，最小改动）。
- 不引入新依赖；不修改 `useApi`、`useChildId` 等共享 hook。

# Decisions

- D1: 复用 `usePaginatedData<Exchange>` 而不是新写 wrapper（与家长端完全一致）。
- D2: 渲染标识用 `targetName` 友好名称（来自快照），不再展示 ID。理由：用户选 B。
- D3: 后端在 `queryExchanges` 一次性批量取快照再组装，避免 N+1。理由：1 个孩子通常只有几条兑换记录，但保持代码干净不依赖数量。
- D4: 缺快照时降级为 `奖品 #{prizeId}` / `盲盒 #{poolId}`，不报错。理由：理论上不可达（每次 create 必写快照），但防御性代码避免空字符串。
- D5: 只新增 `targetName` 字段，不引入 `pointsCost` 字段别名。理由：避免字段重复；前端用 `costPoints` 即可。
- D6: 家长端 `ParentExchangesPage` 不同步升级，依然显示 `奖品 #{prizeId}`。理由：本次用户只反馈孩子端，最小范围；家长端是否同步升级另开 change 评估。
- D7: 新增 2 个单元测试覆盖 `queryExchanges` 的新增行为。理由：补齐空缺，明确契约。

# Open questions

（无。用户已确认 B。）

# Verification expectations

- V1: `cd web && npx tsc --noEmit` 无新增错误（仅允许既有的 `src/parent/components/TaskTypeConfigForms.tsx(241,29)` 历史错误）。
- V2: `cd web && npx eslint src/child/pages/index.tsx` 无错误。
- V3: `cd server/exchange && mvn test -Dtest=ExchangeServiceTest` 全部通过（含 2 个新 case）。
- V4: 手动 e2e：构造种子数据（直接兑换 + 盲盒各 1 条），孩子账号登录，访问 `/child/exchanges`，期望看到 2 张卡片，标题为真实名称，积分与状态正确。
- V5: 手动 curl 验证后端响应：`GET /api/exchanges?childId=X` 返回 `content[i].targetName` 与快照名称匹配。
