## Why

实例初始化并登录后，管理员点击「账号管理」「审计日志」「系统健康」「实例配置」任意菜单都会被踢回 `/admin/login`，导致全部后台管理功能不可用。根因是后端 `WebSecurityConfig.jwtAuthenticationFilter` 解析 access token 后只把账号信息写进 `request.setAttribute`，从未调用 `SecurityContextHolder.setAuthentication(...)`，因此 Spring Security 对 `/api/admin/**` 的 `hasAuthority("ROLE_INSTANCE_ADMIN")` 判定永远拿不到 Authentication，所有 admin 端点统一被 `authenticationEntryPoint` 以 401 拒绝；前端 client 任何 401 都触发全局 `onUnauthorized` 跳转，于是从 `/admin/accounts` 等非公开页跳到 `/admin/login`。Overview 页正常是因为它只调用公开的 `/api/instance/status`。

## What Changes

- 修复 `WebSecurityConfig.jwtAuthenticationFilter`：解析 access token 成功后，依据 token claims 中的 `roles` 构造 `UsernamePasswordAuthenticationToken(accountId, null, authorities)`，其中 `authorities = roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList()`，并调用 `SecurityContextHolder.getContext().setAuthentication(auth)`。
- 过滤器原有的 `request.setAttribute(ATTR_ACCOUNT_ID/ROLES/SESSION_ID)` 保留，确保依赖 request attribute 的下游代码（审计、日志、controller 参数解析）不受影响。
- 不修改任何公共 API 契约、不新增端点、不调整角色模型、不改变 token 格式。

## Capabilities

### New Capabilities

- `admin-access-control`: 首次记录 admin 端点（`/api/admin/**`）的访问授权契约——持有 `INSTANCE_ADMIN` 角色的已认证账号方可访问，其它角色与未认证请求分别以 403/401 拒绝。本次 hotfix 通过修复 JWT 过滤器恢复该契约。

### Modified Capabilities

无。openspec/specs 此前为空，无既有 spec 被改动。

## Impact

- 受影响代码：`server/web/src/main/java/com/cutegoals/web/config/WebSecurityConfig.java`（仅 `jwtAuthenticationFilter` 内部，新增约 6 行）。
- 受影响接口：`/api/admin/accounts`、`/api/admin/audit-logs`、`/api/admin/health`、`/api/admin/config` 等 `/api/admin/**` 端点——修复后持有 INSTANCE_ADMIN 角色的账号可正常访问。
- 无数据库 schema 变更，无依赖变更，无前端变更，无 breaking change。
- 风险：极低。改动局限于安全过滤器的 Authentication 写入，且 `WebSecurityConfig` 已 `import SimpleGrantedAuthority`（原拟用未用），修复闭合既有缺口。
