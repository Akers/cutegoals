## Why

实例初始化完成后访问 `/admin` 系列页面（包括初始化成功跳转、登录成功跳转）始终显示 `无权访问此页面`（`Result status="403"`），导致全部 admin 后台不可用。用户的具体反馈：

1. `/admin/init` 完成初始化后页面 `history.replace('/admin')` 跳转到 `/admin`，但页面显示 `无权访问此页面`；期望初始化完成后跳转到 `/admin/login`。
2. 使用初始化时设置的手机号/密码在 `/admin/login` 手动登录成功后，`history.push('/admin')` 仍然显示 `无权访问此页面`，admin 端所有菜单均不可进入。

### 根因

后端 `AuthConstants.ROLE_INSTANCE_ADMIN = "INSTANCE_ADMIN"`、`ROLE_PARENT = "PARENT"`、`ROLE_CHILD = "CHILD"`（`server/common/.../AuthConstants.java:11-13`），`InitializationService` 与 `AuthenticationService.login` 通过 `roleBindingMapper.findRolesByAccountId()` 返回的 `roles` 数组里都是**大写字符串**，例如 `["INSTANCE_ADMIN", "PARENT"]`。

但前端 `web/src/app.tsx:9-14` 的 `deriveRole` 用**区分大小写**的字符串包含判断：

```ts
function deriveRole(roles?: string[]): Role {
  if (!roles || roles.length === 0) return 'child';
  if (roles.includes('admin')) return 'admin';   // 永远不匹配 'INSTANCE_ADMIN'
  if (roles.includes('parent')) return 'parent'; // 永远不匹配 'PARENT'
  return 'child';
}
```

因此即使是初始化出来的管理员账号，`deriveRole` 也始终返回 `'child'`；`web/src/wrappers/AuthGuard.tsx:50-52` 在 `expectedRole === 'admin'` 与 `role === 'child'` 不匹配时渲染 `<Result status="403" subTitle="无权访问此页面" />`，于是 admin 端所有受保护页面都是 403。

## What Changes

- `web/src/app.tsx`：修复 `deriveRole`，使其按后端真实的大写角色字符串（`INSTANCE_ADMIN`/`PARENT`/`CHILD`）判定，并对未来可能出现的拼写差异（如带 `ROLE_` 前缀、混合大小写）保持稳健——通过把每项角色字符串归一化（去 `ROLE_` 前缀、转大写）后再比较 `INSTANCE_ADMIN`/`PARENT`/`CHILD`。
- `web/src/admin/pages/AdminInitPage.tsx`：初始化成功分支将 `history.replace('/admin')` 改为 `history.replace('/admin/login')`，与用户期望一致；`login()` 仍保留（保留登录态，避免在 `/admin/login` 上重新要求输入凭据，且若用户已登录再访问 `/admin/login` 不会自动跳走——这是与 `/parent/login`、`/parent/init` 等其他端登录页一致的现状，不在本次修复范围）。

## Capabilities

### New Capabilities

（无新 capability）

### Modified Capabilities

（无 spec 文本变更。本次仅修复前端角色推导与初始化跳转目标，不改变 `admin-access-control`、`auth` 等 capability 的验收场景语义。）

## Impact

- 前端：
  - `web/src/app.tsx`（核心修复：`deriveRole` 根因所在）
  - `web/src/admin/pages/AdminInitPage.tsx`（初始化成功跳转目标改为 `/admin/login`）
- 后端：无改动。
- 测试：本次不新增自动化测试（项目 `web` 侧无针对 `deriveRole` 的单元测试基建，`vitest` 仅覆盖部分 hook/逻辑）；以 `npm run lint`（`tsc --noEmit`）与 `npm run build`（`umi build`）作为代码层验证，verify 阶段补充本地浏览器手动验证。
- 已知关联点：`web/src/shared/auth/AuthGuard.tsx:13` 也存在一处 `roles.includes('admin')`，但该组件仅被 `App.tsx` 内的 inner 包装使用，路由 `wrappers` 用的是 `@/wrappers/AuthGuard.tsx`；本次修复点在 `deriveRole`，由 `RoleProvider` 统一向下分发 `role`，因此不会影响 `shared/auth/AuthGuard.tsx` 的现有行为。如后续要统一角色判定逻辑，留作 follow-up change。
