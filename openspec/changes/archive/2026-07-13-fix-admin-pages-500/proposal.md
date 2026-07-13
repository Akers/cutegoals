## Why

继上次 hotfix（`fix-admin-401`）修复 admin 端点的 401 认证问题之后，持有 `INSTANCE_ADMIN` 角色的账号点击「账号管理」「审计日志」菜单仍无法使用——前端 `useApi` 收到 HTTP 500，UI 渲染 `<ErrorState title="加载失败" />`，标题即用户报告的「加载失败 Internal server error」。

`logs/cutegoals-dev.log` 多个 requestId（`9d0367832afb49ae`、`3cb0c724a13240c9`、`33e93f5942db4117` 等）都抛同一异常：

```
java.lang.IllegalArgumentException: Name for argument of type [int] not specified,
  and parameter name information not available via reflection.
  Ensure that the compiler uses the '-parameters' flag.
  at AbstractNamedValueMethodArgumentResolver.updateNamedValueInfo(...:187)
```

根因是项目父 `server/pom.xml` 的 `maven-compiler-plugin` 未配置 `<parameters>true</parameters>`，且本项目不继承 `spring-boot-starter-parent`（该 parent 默认设置 `<maven.compiler.parameters>true</maven.compiler.parameters>`，会自动打开 `-parameters`）。结果 Java 编译器按默认行为丢弃参数名，Spring MVC 6 在解析 `@RequestParam(defaultValue = "1") int page` 这类不显式写 `value` 的参数时无法通过反射拿到名字，全部 admin 接口（以及其余模块下用法相同的 controller）一律 500。

`server/` 下共 10 个 controller 使用 `@RequestParam`：DeviceController / PointsController / TaskAssignmentController / TaskTemplateController / BlindBoxController / PrizeController / AccountManagementController / AuditLogController / TaskReviewController / ExchangeController——`grep -c '@RequestParam(' = 65`、不带括号裸用 = 3，也就是说修复编译开关即可一次性消除所有同类 500，无需改动任何业务代码。

## What Changes

- 在父 `server/pom.xml` 的 `pluginManagement > plugins > maven-compiler-plugin > configuration` 中新增一行 `<parameters>true</parameters>`，让 Maven 调用 `javac -parameters`，保留所有方法参数名到字节码。
- 不修改任何 controller、service、前端代码，不调整 API 契约，不动数据库 schema，不引入新依赖。
- 修复传递性：父 pom 的 `pluginManagement` 配置会被所有子模块（common/auth/family/task/task-review/points/prize/exchange/instance-management/web）继承，编译后所有 `@RequestParam`/`@PathVariable`/`@RequestBody`（不显式写 `value` 时）均能被 Spring MVC 正确解析。

## Capabilities

### New Capabilities

无。本次仅修复编译期配置缺陷，未引入新能力。

### Modified Capabilities

无。`admin-access-control` capability 在 `fix-admin-401` 中已确立，本次 hotfix 只是把已被该 capability 验收场景覆盖的 `/api/admin/accounts`、`/api/admin/audit-logs` 等端点从「鉴权通过但 500」恢复为「正常返回数据」，验收语义不变，无需写 delta spec。

## Impact

- 受影响代码：`server/pom.xml`（`pluginManagement` 内 `maven-compiler-plugin` 的 `<configuration>` 块，新增 1 行）。
- 受影响接口（修复后可正常工作）：
  - `/api/admin/accounts`（分页：`page`、`pageSize`、`enabled`、`keyword`）
  - `/api/admin/audit-logs`（多条件查询：`actorId`、`action`、`resourceType`、`resourceId`、`startTime`、`endTime`、`page`、`pageSize`）
  - `/api/admin/audit-logs/export`、`/api/admin/accounts/{id}/enable|disable` 等
  - 同源问题但同根因一并修好的：`/api/device/**`、`/api/points/**`、`/api/tasks/**`、`/api/task-templates/**`、`/api/blind-box/**`、`/api/prizes/**`、`/api/task-reviews/**`、`/api/exchange/**` 下所有依赖参数名的端点。
- 无数据库 schema 变更，无依赖版本变更，无前端变更，无 breaking change。
- 风险：极低。
  - `-parameters` 仅在字节码中追加方法参数名元数据（`MethodParameters` attribute），对运行时行为零影响，对未读取参数名的代码零影响。
  - 这是 Spring Boot 官方 parent pom 的默认配置，等价于把缺失的默认补回，不存在语义偏差。
  - 上线后旧 jar 仍可正常运行（无二进制不兼容）。
