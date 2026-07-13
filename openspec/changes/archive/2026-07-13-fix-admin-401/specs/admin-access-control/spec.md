## ADDED Requirements

### Requirement: Admin 端点访问授权

系统 SHALL 对所有 `/api/admin/**` 路径的请求执行基于角色的访问控制：仅持有 `INSTANCE_ADMIN` 角色的已认证账号被授权访问；其它已认证账号被拒绝访问；未认证请求被要求认证。

#### Scenario: INSTANCE_ADMIN 角色账号访问 admin 端点

- **WHEN** 一个已认证且持有 `INSTANCE_ADMIN` 角色的账号发起 `GET /api/admin/accounts`（或任意 `/api/admin/**`）请求
- **THEN** 系统 MUST 返回 HTTP 200，并在响应体中返回对应 admin 功能的数据

#### Scenario: 非 INSTANCE_ADMIN 角色账号访问 admin 端点

- **WHEN** 一个已认证但仅持有 `PARENT` 或 `CHILD` 角色的账号发起 `/api/admin/**` 请求
- **THEN** 系统 MUST 返回 HTTP 403，且响应体错误码为 `FORBIDDEN`

#### Scenario: 未认证请求访问 admin 端点

- **WHEN** 一个未携带有效 access token 的请求访问 `/api/admin/**`
- **THEN** 系统 MUST 返回 HTTP 401，且响应体错误码为 `UNAUTHORIZED`

### Requirement: 角色到权限标识的映射

系统 SHALL 将 access token claims 中的每个角色字符串映射为形如 `ROLE_<角色名>` 的权限标识，用于权限判定。该映射 MUST 对所有携带有效 access token 的请求一致生效，且 MUST NOT 影响依赖账号标识（accountId、roles、sessionId）的下游组件（审计、日志、会话恢复等）继续获取原始信息。

#### Scenario: 单角色 token 映射为带 ROLE_ 前缀的权限

- **WHEN** access token 的 `roles` claim 为 `["INSTANCE_ADMIN"]`
- **THEN** 权限判定层 MUST 看到包含 `ROLE_INSTANCE_ADMIN` 的权限集合

#### Scenario: 多角色 token 映射为多个权限

- **WHEN** access token 的 `roles` claim 为 `["INSTANCE_ADMIN", "PARENT"]`
- **THEN** 权限判定层 MUST 看到同时包含 `ROLE_INSTANCE_ADMIN` 与 `ROLE_PARENT` 的权限集合

#### Scenario: 映射不影响下游 request attribute

- **WHEN** 一个持有有效 access token 的请求经过认证过滤器
- **THEN** request attribute 中的 `accountId`、`roles`、`sessionId` MUST 与 token claims 保持一致，供审计与日志组件使用
