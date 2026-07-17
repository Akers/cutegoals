# 修复方案：家长端菜单 403

## 现状（问题代码）

1. `web/src/app.tsx:12-18` — `deriveRole(roles)` 把账号的多角色折叠为**单一**角色，`INSTANCE_ADMIN` 优先：
   ```ts
   if (normalized.includes('INSTANCE_ADMIN')) return 'admin';
   if (normalized.includes('PARENT')) return 'parent';
   return 'child';
   ```
2. `web/src/wrappers/AuthGuard.tsx:50-52` — 用该单一角色与路径期望角色做相等比较，不等即 403。
3. 后端按设计为初始化管理员账号授予 `INSTANCE_ADMIN` + `PARENT` 双角色（`server/auth/.../InitializationService.java:67,73`），登录与 `/api/auth/me` 均如实返回两个角色。

结果：初始化管理员账号（多数私有化部署的唯一家长账号）经 `/parent/login` 登录后，`deriveRole(["INSTANCE_ADMIN","PARENT"]) === 'admin'`，访问任何 `/parent` 页面都被判定角色不匹配 → 全部 403。

## 修复方案（唯一方案）

把路由守卫的角色判定从「单一角色相等」改为「角色集合成员检查」：

1. **`web/src/shared/role.ts`** 新增纯函数：
   ```ts
   export function normalizeRoles(roles?: string[]): Role[]
   ```
   对每个后端角色字符串做大写归一化并剥离 `ROLE_` 前缀，映射 `INSTANCE_ADMIN→admin`、`PARENT→parent`、`CHILD→child`，去重、忽略未知值。

2. **`web/src/wrappers/AuthGuard.tsx`**：从 `useAuth()` 取 `account.roles`，用 `normalizeRoles()` 得到角色集合，`roles.includes(expectedRole)` 为放行条件。未认证跳转与 loading 行为不变；不再依赖 `useRole()`。

3. **`web/src/app.tsx`**：`deriveRole` 改为基于 `normalizeRoles()` 实现，保持原有优先级（admin > parent > child）与返回类型不变——`RoleContext` 的主角色仍用于主题（`setDocumentTheme`）与登录页跳转，行为完全不变。

4. **回归测试** `web/src/wrappers/__tests__/AuthGuard.test.tsx`：
   - 双角色账号（`["INSTANCE_ADMIN","PARENT"]`）访问 `/parent` → 渲染子路由而非 403（修复前必然失败的 RED 用例）；
   - 仅 `["CHILD"]` 角色访问 `/parent` → 仍渲染 403；
   - 未认证访问 `/parent` → 重定向 `/parent/login`。

## 不采纳的替代方案

- 改 `deriveRole` 优先级为 parent 优先：会让同一账号访问 `/admin` 时反过来 403，治标且互相矛盾。
- 按路径动态推导主角色：把路由知识泄漏进全局 `RoleProvider`，主题色会随路径抖动，侵入更大。
- 后端去掉初始化账号的 `PARENT` 角色：破坏「实例管理员即家长」的既有设计，且 `/admin` 之外该账号将无任何可用入口。

## 风险

- `useRole()` 的其他消费方（`RoleGuard`、`shared/auth/AuthGuard`、`NotFound`、主题）仍读取主角色，语义不变，无需修改。
- 守卫改用 `useAuth().account` 后，`loading` 期间仍渲染 Spin，account 就绪后才判定，时序安全。
