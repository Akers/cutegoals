## 修复方案

### 根因

`web/src/app.tsx:9-14` 的 `deriveRole`：

```ts
function deriveRole(roles?: string[]): Role {
  if (!roles || roles.length === 0) return 'child';
  if (roles.includes('admin')) return 'admin';
  if (roles.includes('parent')) return 'parent';
  return 'child';
}
```

后端 `AuthConstants`：

```java
public static final String ROLE_INSTANCE_ADMIN = "INSTANCE_ADMIN";
public static final String ROLE_PARENT = "PARENT";
public static final String ROLE_CHILD = "CHILD";
```

`AuthenticationService.login()` 和 `InitializationService.initialize()` 返回的 `roles` 数组里直接放入大写角色字符串（例如 `["INSTANCE_ADMIN", "PARENT"]`）。`deriveRole` 用区分大小写的 `includes('admin')` / `includes('parent')` 比对，永远不命中，最终 fallback 到 `'child'`。

下游链路：

- `web/src/app.tsx:22` 调用 `deriveRole(account?.roles)` 后 `<RoleProvider role={role}>`。
- `web/src/wrappers/AuthGuard.tsx:50-52` 在 `expectedRole === 'admin'`、`role !== 'admin'` 时渲染 `<Result status="403" subTitle="无权访问此页面" />`。
- 因此 admin 端所有挂了 `wrappers: ['@/wrappers/AuthGuard']` 且期望 `admin` 角色的路由（`web/config/routes.ts`）都返回 403。

### 修改点

1. **`web/src/app.tsx`（核心）** —— `deriveRole` 改为对每项 role 字符串归一化后比较：

   ```ts
   function deriveRole(roles?: string[]): Role {
     if (!roles || roles.length === 0) return 'child';
     const normalized = roles.map((r) => r.toUpperCase().replace(/^ROLE_/, ''));
     if (normalized.includes('INSTANCE_ADMIN')) return 'admin';
     if (normalized.includes('PARENT')) return 'parent';
     return 'child';
   }
   ```

   - 大小写无关：`INSTANCE_ADMIN`、`instance_admin`、`Instance_Admin` 都能命中。
   - 兼容可能的 `ROLE_` 前缀：`ROLE_INSTANCE_ADMIN`、`ROLE_PARENT` 也能正确识别（后端 `SimpleGrantedAuthority("ROLE_" + r)` 的写法未来若被错误地塞回 `account.roles` 也能容错，不改变后端行为，只让前端更稳健）。
   - 多角色情况下 `INSTANCE_ADMIN` 优先级高于 `PARENT`，与初始化时同时授予两个角色的现状一致（管理员同时也是该家庭的家长）。

2. **`web/src/admin/pages/AdminInitPage.tsx`** —— 第 68 行 `history.replace('/admin')` 改为 `history.replace('/admin/login')`。`login()` 仍然调用，保留登录态与 token；用户到达 `/admin/login` 后，由于 `deriveRole` 已修复，再次登录或刷新即可进入 `/admin`，与用户报告的期望一致。

### 边界

- 不改后端任何代码、API、契约。
- 不改路由表 `routes.ts`、不改 `wrappers/AuthGuard.tsx` 的 403 渲染逻辑、不改 `shared/auth/AuthGuard.tsx`（仅被 App.tsx inner 使用）。
- 不改 `AdminLoginPage.tsx` 的 `history.push('/admin')` —— 登录成功仍跳 `/admin`，修复 `deriveRole` 后该跳转自然可用。
- 不改 parent / child 端任何文件（它们使用同一 `deriveRole`，会顺带受益于大小写无关判定，但行为不会回归）。
- `AdminInitPage.tsx` 的 `useEffect` 中 `history.replace('/admin/login')`（实例已初始化时）保持不变，与本次目标一致。

### 风险

- 归一化比较本身不引入回归：以前区分大小写时所有大写角色都会 fallback 到 `'child'`，现在大写角色被正确识别，`parent`/`child` 用户的角色判定不变（仍是 `PARENT` 命中、否则 `child`）。
- `AdminInitPage` 改跳 `/admin/login` 不会破坏登录态：`login()` 已经写入 token 与账号信息，浏览器到达 `/admin/login` 时若已登录，行为与「手动访问 `/admin/login` 已登录」一致；用户期望明确为「跳到登录页」，该改动直接满足。

## 测试

- 类型检查：`cd web && npm run lint`（`tsc --noEmit`）确认无类型错误。
- 单元测试：`cd web && npm run test` 确认现有 vitest 用例不回归。
- 生产构建：`cd web && npm run build`（`umi build`）确认无构建错误。
- 手动验证（verify 阶段执行）：
  1. 实例未初始化时访问 `/admin/init`，填入合法 token、手机号、密码，提交后跳到 `/admin/login`（而非 `/admin`）。
  2. 在 `/admin/login` 用初始化时的手机号/密码登录，跳转到 `/admin`，菜单不再显示 `无权访问此页面`，能正常进入「账号管理」「审计日志」「系统健康」「实例配置」等子页面。
  3. 刷新 `/admin` 或重新打开标签页访问 `/admin`，仍能正常进入（角色判定来自持久化的 token / `/auth/me`，`deriveRole` 已支持大写）。
