# Tasks: fix-admin-401

> 修复 `WebSecurityConfig.jwtAuthenticationFilter` 未写入 Spring Security Context 导致 `/api/admin/**` 全部 401 的缺陷，落实 `admin-access-control` capability 的访问授权契约。

## 1. 过滤器认证上下文修复（admin-access-control）

- [x] 1.1 在 `server/web/src/main/java/com/cutegoals/web/config/WebSecurityConfig.java` 顶部补充 import：`org.springframework.security.authentication.UsernamePasswordAuthenticationToken`、`org.springframework.security.core.context.SecurityContextHolder`、`org.springframework.security.core.authority.SimpleGrantedAuthority`、`java.util.stream.Collectors`（已 import 的不重复）。
- [x] 1.2 在 `jwtAuthenticationFilter` 成功解析 token 并执行既有 `request.setAttribute(...)` 之后，新增构造 `UsernamePasswordAuthenticationToken(accountId, null, roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).collect(Collectors.toList()))` 并通过 `SecurityContextHolder.getContext().setAuthentication(auth)` 写入 SecurityContext。保留既有 `request.setAttribute` 不动；token 无效或解析失败的分支 MUST NOT 设置 authentication。

## 2. 验证 admin 访问授权契约

- [x] 2.1 运行 `mvn -f server/pom.xml test`，确保全量测试通过（基线 66 tests 全绿），无新增失败。
- [x] 2.2 端到端验证：以 INSTANCE_ADMIN 账号通过 `POST /api/auth/login` 获取 cookie 后，访问 `GET /api/admin/accounts`（携带 X-XSRF-TOKEN header）。验证权限检查通过：带 cookie 请求不再返回 HTTP 401；未携带 cookie 的相同请求返回 HTTP 401；如可构造 PARENT 角色账号，其访问返回 HTTP 403。实测 `AccountManagementController` 因方法参数名未在编译时保留（缺 `-parameters` 标志）返回 HTTP 500，该问题与访问授权无关，属独立预存缺陷，不在本 change 范围。
