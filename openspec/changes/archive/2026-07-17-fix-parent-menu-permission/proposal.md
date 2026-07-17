# fix-parent-menu-permission

## Why

家长端登录成功后，访问 `/parent` 下所有菜单均显示「无权访问此页面」（`Result status="403"`），家长端整体不可用。

根因：实例初始化创建的管理员账号被设计为同时持有 `INSTANCE_ADMIN` 和 `PARENT` 双角色（`server/.../InitializationService.java:67,73`），登录接口如实返回 `["INSTANCE_ADMIN","PARENT"]`。而前端 `web/src/app.tsx:12-18` 的 `deriveRole` 把多角色折叠为单一角色且 `admin` 优先；路由守卫 `web/src/wrappers/AuthGuard.tsx:50-52` 用该单一角色与路径期望角色做**相等比较**，于是该账号访问 `/parent` 时 `'admin' !== 'parent'` → 所有家长端菜单 403。根路由 `/` 重定向到 `/parent`，使该问题必然被首先撞上。

这与已归档的 `2026-07-17-fix-admin-auth-redirect` 同源：当时修复了 `deriveRole` 的归一化 bug，但「单一角色相等比较」这一缺陷在双角色账号访问家长端时仍然暴露。

## What Changes

- `web/src/shared/role.ts`：新增 `normalizeRoles()` 纯函数，把后端角色字符串（如 `INSTANCE_ADMIN`、`ROLE_PARENT`）归一化为前端 `Role[]`。
- `web/src/wrappers/AuthGuard.tsx`：角色校验从「单一角色相等」改为「角色集合成员检查」——账号持有路径期望角色即放行。
- `web/src/app.tsx`：`deriveRole` 复用 `normalizeRoles()`，主角色优先级（admin > parent > child）与主题行为保持不变。
- 新增回归测试 `web/src/wrappers/__tests__/AuthGuard.test.tsx`：双角色账号访问 `/parent` 不再 403；无对应角色的账号访问仍 403。

非目标：不改动后端角色授予模型（初始化账号双角色是既有设计）；不改动 `RoleContext`/`RoleProvider` 对外签名；不改动登出、登录跳转逻辑。

## Capabilities

- **New Capabilities**: 无
- **Modified Capabilities**: 无

说明：现有 spec `web-app` 的「路由权限守卫与安全会话」要求语义即「已认证且持有该角色的用户可访问对应入口；角色不匹配才 403」。持有 `PARENT` 角色的账号访问 `/parent` 本就不是「角色不匹配」，本次修复是恢复 spec 预期行为，不修改任何验收场景，故无需 delta spec。

## Impact

- 代码：`web/src/shared/role.ts`、`web/src/wrappers/AuthGuard.tsx`、`web/src/app.tsx`、新增 `web/src/wrappers/__tests__/AuthGuard.test.tsx`（共 4 个文件）。
- API/接口：无变更。
- 依赖：无新增。
- 影响面：仅前端路由守卫的角色判定；单一角色账号行为完全不变。
