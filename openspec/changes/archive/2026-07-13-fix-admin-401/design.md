## Context

CuteGoals 2.0 采用 cookie-based JWT 认证：`jwtAuthenticationFilter` 从 `access_token` cookie 读取 JWT，经 `TokenService.parseAccessToken` 解析得到 `claims(accountId, roles, sessionId)`。安全配置 `WebSecurityConfig` 在 `authorizeHttpRequests` 中声明 `/api/admin/**` 端点要求 `hasAuthority("ROLE_INSTANCE_ADMIN")`。

当前实现存在缺陷：过滤器解析 token 后仅调用 `request.setAttribute(ATTR_ACCOUNT_ID/ROLES/SESSION_ID)`，**从未写入 Spring `SecurityContext`**。`authorizeHttpRequests` 的权限判定依赖 `SecurityContextHolder.getContext().getAuthentication()`，因此对 `/api/admin/**` 的请求始终拿不到 Authentication，被 `authenticationEntryPoint` 统一返回 401。

前端 `client.ts` 对任何 401 触发全局 `onUnauthorized` → `handleUnauthorized` 将非公开页用户重定向到对应角色的登录页。于是从 `/admin/accounts`、`/admin/audit-logs`、`/admin/health` 等页面访问时被踢回 `/admin/login`。Overview 页幸免是因为它只调用公开端点 `/api/instance/status`。

`WebSecurityConfig` 顶部已 `import SimpleGrantedAuthority`（第 26 行），说明原设计意图就是构造 Spring Security authorities，只是过滤器实现遗漏了写入步骤。

## Goals / Non-Goals

**Goals:**

- 让持有 `INSTANCE_ADMIN` 角色的已登录账号能正常访问全部 `/api/admin/**` 端点（accounts、audit-logs、health、config）。
- 保持下游依赖 `request.setAttribute(ATTR_*)` 的代码（审计切面、日志、controller 参数解析）行为不变。
- 维持现有的「公开路径跳过 JWT 校验」「token 缺失或非法返回 401」语义。

**Non-Goals:**

- 不引入 `/api/auth/me` 之外的新认证端点。
- 不调整角色模型（仍是 `INSTANCE_ADMIN`/`PARENT`/`CHILD`）、不修改 token claims 结构。
- 不重构 `WebSecurityConfig` 的过滤器链顺序或 CSRF/限流策略。
- 不处理 access token 过期后的自动 refresh（属于独立 change 的前端 client 改造范围）。

## Decisions

### 决策 1：在过滤器内构造 Spring Security Authentication

**选择**：`jwtAuthenticationFilter` 解析 token 成功后，依据 `claims.roles()` 构造 `UsernamePasswordAuthenticationToken` 并写入 `SecurityContextHolder`。

**实现要点**：

```java
if (accessToken != null) {
    var claims = tokenService.parseAccessToken(accessToken);
    request.setAttribute(AuthConstants.ATTR_ACCOUNT_ID, claims.accountId());
    request.setAttribute(AuthConstants.ATTR_ROLES, claims.roles());
    request.setAttribute(AuthConstants.ATTR_SESSION_ID, claims.sessionId());

    // 新增：写入 Spring Security Context，使 hasAuthority 判定生效
    List<SimpleGrantedAuthority> authorities = claims.roles().stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .collect(Collectors.toList());
    var authentication = new UsernamePasswordAuthenticationToken(claims.accountId(), null, authorities);
    SecurityContextHolder.getContext().setAuthentication(authentication);
}
filterChain.doFilter(request, response);
```

**为什么这样映射角色前缀**：`authorizeHttpRequests` 用的是 `hasAuthority("ROLE_INSTANCE_ADMIN")`（精确字符串匹配），而 token claims 里的角色是 `INSTANCE_ADMIN`（无 `ROLE_` 前缀）。按 Spring Security 惯例 `hasRole("X")` 会自动加 `ROLE_` 前缀、`hasAuthority(...)` 不会——既然现有配置用 `hasAuthority`，构造 authority 时就必须显式拼接 `ROLE_` 前缀，与既有安全契约对齐。

**备选方案与拒绝理由**：

- *A. 改 `authorizeHttpRequests` 为 `hasRole("INSTANCE_ADMIN")` 并依赖过滤器原样写入无前缀角色*：同样需要改过滤器加 `setAuthentication`，且 `hasRole`/`hasAuthority` 语义在代码评审时容易混淆，不优于决策 1。
- *B. 改用 `@PreAuthorize` 方法级注解*：需要每个 admin controller 方法加注解，改动面大、易遗漏，且 controller 层当前没有方法级安全；不属 hotfix 范围。
- *C. 新增独立的 `AuthenticationFilter`*：重复造轮子，`jwtAuthenticationFilter` 已是正确的注入点。

### 决策 2：保留 `request.setAttribute` 不删除

下游审计切面（`AuditService`）、日志工具、`AuthContext` 重建会话的 `/api/auth/me` 实现等都读取 `ATTR_ACCOUNT_ID/ROLES/SESSION_ID`。删除会引入回归。两条信息渠道（SecurityContext + request attribute）并存是安全的，无额外开销。

### 信任边界说明

本次修复闭合「JWT claims → Spring Security Authentication」的信任传递缺口。修复后：

- **已认证 + 角色匹配**：请求通过 `authorizeHttpRequests` 进入 controller。
- **已认证 + 角色不匹配**：Spring Security 抛 `AccessDeniedException`，由默认 `accessDeniedHandler` 返回 403（区别于 401，前端不会误跳登录页）。
- **未认证访问 admin 端点**：无 Authentication → `authenticationEntryPoint` 返回 401（行为不变）。

## Risks / Trade-offs

- **[风险] `SecurityContext` 默认线程级，子线程异步任务拿不到 Authentication** → 当前 admin 端点无 `@Async` 调用，且审计/日志仍从 `request.setAttribute` 读取，不依赖 SecurityContext 传播。若未来引入异步需考虑 `DelegatingSecurityContextExecutor`，属独立议题。
- **[风险] 旧版本 access token 无 roles claim** → `TokenService.parseAccessToken` 对缺失 roles 已有兜底（返回空列表），空 authorities 会被 403 拒绝，安全无回退；且初始化流程签发的 token 均含 roles，实际不触发。
- **[权衡] 同时维护 SecurityContext 与 request attribute 两条渠道** → 可读性略降，但避免下游回归，收益大于成本。

## Migration Plan

- 纯代码修复，无数据库变更、无配置变更、无前端变更。
- 部署：重新编译 `server/web` 模块，重启 Spring Boot 进程即可。
- 回滚：还原 `WebSecurityConfig.jwtAuthenticationFilter` 一处 diff。由于改动局限于单个内部类方法，回滚原子性高。
- 无需 token 重签、无需用户重新登录——现有 access/refresh cookie 在修复后立即生效。

## Open Questions

无。根因清晰，修复方案单一且最小化。
