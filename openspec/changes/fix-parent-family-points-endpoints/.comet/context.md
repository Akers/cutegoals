# Comet Design Handoff

- Change: fix-parent-family-points-endpoints
- Phase: design
- Mode: compact
- Context hash: ee61c57fd75f43c1077e81b60d9e91d048d6aebb4c9d905b925a7bf73fac6337

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/fix-parent-family-points-endpoints/proposal.md

- Source: openspec/changes/fix-parent-family-points-endpoints/proposal.md
- Lines: 1-28
- SHA256: 6fe7e718b4c86468e30dad00d16cda7f32f69e7b2a72f0f1ed0de2f38a77a93e

```md
## Why

在 fix-parent-pages-contract 归档后，parent 端「家庭」与「积分」两个菜单进入仍报错：

1. **家庭菜单**：后端返回 `RESOURCE_NOT_FOUND`。`FamilyController.getFamily` 从请求属性读取 `ATTR_FAMILY_ID`，但 `WebSecurityConfig.jwtAuthenticationFilter` 只设置了 `ATTR_ACCOUNT_ID`/`ATTR_ROLES`/`ATTR_SESSION_ID`，从未设置 `ATTR_FAMILY_ID`。对未设置家庭 context 的 parent 账号，`FamilyService.getFamily(null)` 抛出 `RESOURCE_NOT_FOUND`。

2. **积分菜单**：前端调用 `useApi<T>('')`（`selectedChild` 为空字符串时），请求落到 `/api`，Spring 报 `No static resource api`（404）。应在未选择孩子时跳过请求。

## What Changes

- 后端 `WebSecurityConfig.jwtAuthenticationFilter`：解析 token 后，使用 `FamilyMemberMapper.findByAccountIdAndRole(accountId, role)` 查询账号所属 family，将 `familyId` 写入请求属性 `ATTR_FAMILY_ID`。
- 前端 `web/src/parent/pages/index.tsx` 的 `ParentPointsPage`：当 `selectedChild` 为空时，给 `useApi` 传入 `skip: true`，避免发起空路径请求。

## Capabilities

无新增 capability、无修改 capability。本次为 parent-pages-contract 修复后的行为补漏。

## Impact

- `server/web/src/main/java/com/cutegoals/web/config/WebSecurityConfig.java`：新增 `FamilyMemberMapper` 注入与 familyId 设置逻辑（约 5-10 行）。
- `web/src/parent/pages/index.tsx`：积分页 `useApi` 增加 `skip` 选项（1 处）。
- 无数据库 schema 变更、无新增依赖、无 API 契约变更。

## Testing

- 后端：`mvn -pl :web -am test` 保持基线通过。
- 前端：`npx tsc -b` 0 errors；`npm test` 无新增失败。
- dev server：重启后家庭/积分菜单不再报错。

```

## openspec/changes/fix-parent-family-points-endpoints/design.md

- Source: openspec/changes/fix-parent-family-points-endpoints/design.md
- Lines: 1-51
- SHA256: f371ee52f30e455150a4e1e742f48d3f5c6edeeae2b425cb97ee5d690fd4f181

```md
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

```

## openspec/changes/fix-parent-family-points-endpoints/tasks.md

- Source: openspec/changes/fix-parent-family-points-endpoints/tasks.md
- Lines: 1-9
- SHA256: d0e040631f99e0e92d03fd6f3d831e96137c1504e3da07d90cbb01359036c82f

```md
- [ ] 1.1 在 `WebSecurityConfig` 中注入 `FamilyMemberMapper`，导入 `com.cutegoals.auth.mapper.FamilyMemberMapper`。
- [ ] 1.2 在 `jwtAuthenticationFilter` 解析 token 后，使用 `claims.roles().get(0)` 获取角色，调用 `familyMemberMapper.findByAccountIdAndRole(accountId, role)` 查询 family member；若存在，设置 `request.setAttribute(AuthConstants.ATTR_FAMILY_ID, member.getFamilyId())`。
- [ ] 1.3 验证后端编译通过：`mvn -pl :web -am compile` exit 0。
- [ ] 2.1 在 `web/src/parent/pages/index.tsx` 的 `ParentPointsPage` 中，将 `useApi<...>(selectedChild ? ... : '')` 改为 `useApi<...>(selectedChild ? ... : '', { skip: !selectedChild })`。
- [ ] 2.2 验证前端类型检查：`cd web && npx tsc -b` 0 errors。
- [ ] 2.3 验证前端测试：`cd web && npm test` 与 baseline 一致（14 failed / 65 passed），0 新增失败。
- [ ] 3.1 后端测试：`mvn -pl :web -am test` 全绿（66 tests）。
- [ ] 3.2 git commit：`fix(parent): set familyId in JWT filter and skip empty points ledger request`。
- [ ] 3.3 运行 `node .opencode/skills/comet/scripts/comet-guard.mjs fix-parent-family-points-endpoints build --apply` 推进到 verify。

```
