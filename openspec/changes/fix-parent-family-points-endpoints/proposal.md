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
