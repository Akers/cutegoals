## Context

fix-parent-pages-contract 已合并并归档，解决了 parent 端大部分页面契约错配问题。但 dev server 验证发现：

- 进入「家庭」菜单，后端 `FamilyController.getFamily` 返回 `RESOURCE_NOT_FOUND`。
- 进入「积分」菜单，后端 Spring 报 `No static resource api`（404）。

根因分析：
1. `WebSecurityConfig.jwtAuthenticationFilter` 解析 JWT 后只写入 `ATTR_ACCOUNT_ID`、`ATTR_ROLES`、`ATTR_SESSION_ID`，没有写入 `ATTR_FAMILY_ID`。`FamilyController` 等控制器读取 `ATTR_FAMILY_ID` 时得到 `null`，传给 service 层后触发 `RESOURCE_NOT_FOUND`。
2. `ParentPointsPage` 在 `selectedChild` 为空字符串时调用 `useApi('', ...)`，实际发起 `GET /api` 请求，Spring 当成静态资源查找并报 404。

## Goals

- 认证通过后，所有需要 family context 的端点都能从请求属性拿到正确的 `familyId`。
- 积分页未选择孩子时不发起请求。
- 不改 API 契约、不新增 capability、不动数据库 schema。

## Decisions

### 决策 1：在 JWT filter 中注入 familyId

**选择**：在 `WebSecurityConfig.jwtAuthenticationFilter` 解析 access token 后，注入 `FamilyMemberMapper`，查询 `family_member` 表，把 `familyId` 写入 `ATTR_FAMILY_ID`。

**实现要点**：
- 注入 `FamilyMemberMapper`（位于 `auth` 模块，web 已依赖 auth）。
- 取 token 中的第一个 role（通常是 `PARENT`），用 `findByAccountIdAndRole(accountId, role)` 查询 family member。
- 如查询到记录，设置 `ATTR_FAMILY_ID = member.getFamilyId()`；否则不设置（保持 `null`），让 controller 自己处理无家庭场景。

**备选（已拒绝）**：
- A. 在每个 controller 中自行查询 familyId：重复代码，违背 DRY。
- B. 在 `FamilyService.getFamily` 中根据 accountId 反查 family：会改变方法签名，影响测试和现有调用方。

### 决策 2：积分页未选孩子时跳过请求

**选择**：`useApi<...>(selectedChild ? '/points/ledger/...' : '', { skip: !selectedChild })`。

**备选（已拒绝）**：
- A. 在 `useApi` hook 内部自动跳过空路径：会改变 hook 公共行为，影响其他调用方。
- B. 在 points 页面把空字符串当特殊路径：仍需要 skip 语义，不如显式 `skip`。

## Risks

- 风险：JWT filter 中新增 DB 查询可能轻微增加每个请求延迟。缓解：family_member 表通常按 account_id 有索引，查询单条记录。
- 风险：角色为 `CHILD` 的账号访问 family 相关端点时，`findByAccountIdAndRole(accountId, role)` 查不到记录。但 child 端不依赖这些端点；如依赖，可后续扩展。

## Testing

- `mvn -pl :web -am test`：保持基线通过。
- `cd web && npx tsc -b`：0 errors。
- `cd web && npm test`：与 baseline 一致，0 新增失败。
- dev server：重启后家庭/积分菜单正常进入。
