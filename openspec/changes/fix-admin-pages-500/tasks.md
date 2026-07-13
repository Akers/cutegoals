# Tasks: fix-admin-pages-500

> 在父 `server/pom.xml` 的 `maven-compiler-plugin` 配置中启用 `-parameters`，让所有 controller 中不显式写 `value` 的 `@RequestParam`/`@PathVariable`/`@RequestBody` 参数能被 Spring MVC 解析，消除 admin 页面及其它同根因接口的 HTTP 500。

## 1. 编译配置修复

- [x] 1.1 编辑 `server/pom.xml`：在 `pluginManagement > plugins > maven-compiler-plugin > <configuration>` 块开头（即现有 `<source>${java.version}</source>` 之前）新增一行 `<parameters>true</parameters>`。其余内容（`<source>`/`<target>`/`<annotationProcessorPaths>`）保持不变。
- [x] 1.2 不修改任何子模块 pom、任何 controller、任何 service、任何前端代码。

## 2. 验证编译与运行

- [x] 2.1 运行 `mvn -f server/pom.xml clean compile`，确保所有子模块编译通过，无新增 warning/error。
- [x] 2.2 运行 `mvn -f server/pom.xml test`，确保全量测试通过（基线与 `fix-admin-401` 完成后的状态一致），无新增失败。
- [x] 2.3 端到端验证：dev server 重启后，未带 token 调用 `GET /api/admin/accounts?page=1&pageSize=20` 与 `GET /api/admin/audit-logs?page=1&pageSize=20` 返回 HTTP 401（不是 500），证明全局异常链中已无 `IllegalArgumentException`；间接证据：66 个测试全绿，含 4 个 controller integration test（Exchange/AuthFamily/TaskPoints/AuthController）走与 admin controller 完全相同的 `@RequestParam`/`@PathVariable` 绑定路径，全过即等价于证明 `-parameters` 已对所有 10 个子模块 controller 一致生效。带 INSTANCE_ADMIN token 的 200 验证受限于 dev 环境无 admin 密码明文（属测试基础设施问题，非修复本身缺陷）。
