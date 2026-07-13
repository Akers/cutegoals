## Context

CuteGoals 后端是多模块 Maven 工程（`server/pom.xml` 父 + 9 个子模块），Spring Boot 3.2.5 / Spring Framework 6 / Java 21。父 pom 没有继承 `spring-boot-starter-parent`（项目按需自行管理依赖版本），因此不会自动获得 parent pom 中 `<maven.compiler.parameters>true</maven.compiler.parameters>` 这一默认设置。

Spring MVC 6 的 `AbstractNamedValueMethodArgumentResolver`（解析 `@RequestParam`、`@PathVariable`、`@CookieValue` 等）在以下情况需要从反射读取参数名：

1. 注解未显式指定 `value`（即 `@RequestParam int page` 而非 `@RequestParam("page") int page`）；
2. 没有调试信息（`-g`）保留参数名时，会回退到 `-parameters` 标志写入字节码的 `MethodParameters` attribute。

Java 8+ 默认编译行为是丢弃参数名；只有显式开启 `-parameters` 才会保留。Spring Boot 3.x 官方推荐开启，且 starter-parent 默认开启，但本项目因不继承 parent 而漏配。

**当前 pom 配置（节选 L153-168）**：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.13.0</version>
    <configuration>
        <source>${java.version}</source>
        <target>${java.version}</target>
        <annotationProcessorPaths>
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>${lombok.version}</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

`<configuration>` 缺少 `<parameters>true</parameters>`。

`fix-admin-401` 修复 401 之后，鉴权链路对 `/api/admin/**` 放行，但 controller 入口立即因参数解析失败抛 500，前端 `useApi` 渲染 `<ErrorState title="加载失败" />`——即用户报告的现象。

## Goals / Non-Goals

**Goals:**

- 让所有 controller 中不显式写 `value` 的 `@RequestParam`/`@PathVariable`/`@RequestBody` 参数能被 Spring MVC 正确解析，消除 admin 页面（及其余同根因页面）的 500。
- 通过父 pom 一次性配置，让全部 9 个子模块继承，避免逐模块修改。
- 保持编译产物与现有依赖、字节码兼容性，不引入回归。

**Non-Goals:**

- 不重构 controller 用 `@RequestParam("page")` 显式指定 `value`——成本高、易遗漏、不可读，且无法消除 `@PathVariable Long id` 这类参数的同源问题。
- 不引入 `spring-boot-starter-parent` 作为 parent——会牵动整个依赖管理策略，远超 hotfix 范围。
- 不修改 `WebSecurityConfig` 或任何鉴权链路（`fix-admin-401` 已完成）。
- 不调整 API 契约、不调整 controller 方法签名。

## Decisions

### 决策 1：在父 pom 的 `pluginManagement` 中给 `maven-compiler-plugin` 加 `<parameters>true</parameters>`

**选择**：单点修改 `server/pom.xml` 的 maven-compiler-plugin 配置。

**实现要点**（在 `<configuration>` 块开头新增一行）：

```xml
<configuration>
    <parameters>true</parameters>          <!-- 新增 -->
    <source>${java.version}</source>
    <target>${java.version}</target>
    <annotationProcessorPaths>
        ...
    </annotationProcessorPaths>
</configuration>
```

`<parameters>true</parameters>` 等价于命令行 `-parameters`，会触发 `javac` 把 `MethodParameters` attribute 写入所有类文件，包含参数名。

**为什么放在 `pluginManagement` 而不是 `<build><plugins>`**：当前 pom 在两处都声明了 maven-compiler-plugin（L153-168 在 `pluginManagement`，L171-177 在 `plugins`），但 `plugins` 下的声明没有 `<configuration>`，按 Maven 语义它会合并（inherit）`pluginManagement` 中的配置。因此修改 `pluginManagement` 中的配置即可生效，同时通过 `pluginManagement` 的传递性，子模块在引用同版本插件时也会继承该配置——这是最一致、影响面最可控的修改点。

**备选方案与拒绝理由**：

- *A. 在 `<properties>` 中加 `<maven.compiler.parameters>true</maven.compiler.parameters>`*：依赖 Maven 隐式把 `maven.compiler.*` property 映射到 compiler 插件参数。确实可行，但与现有 `<source>`/`<target>`/`<release>` 已通过显式 `<configuration>` 设置的风格不一致，可读性差，且部分插件版本对该 property 的支持存在历史 bug。显式 `<parameters>` 更清晰。
- *B. 给所有 controller 参数加 `value`，如 `@RequestParam("page") int page`*：65 处 `@RequestParam(` + 3 处裸用 + 多处 `@PathVariable`，改动面大、易遗漏、不可读；且未来新增 controller 仍会再踩坑。
- *C. 引入 `spring-boot-starter-parent` 作为 `<parent>`*：会重写依赖管理，与项目当前的 `<dependencyManagement>` + 显式版本号策略冲突，远超 hotfix 范围。
- *D. 升级 Java / 切换 AspectJ 等*：与根因无关。

### 决策 2：不动 `fix-admin-401` 中已修改的 `WebSecurityConfig`

两个 hotfix 是独立的：401 是「鉴权链路写 SecurityContext」，500 是「编译期参数名缺失」。修复 500 后，鉴权链路依然按 `fix-admin-401` 设计运行，两者无耦合。

### 决策 3：无需 delta spec

`admin-access-control` capability 的验收场景是「INSTANCE_ADMIN 角色可访问、其他角色 403、未认证 401」。500 错误本身不在 capability 验收语义之内（capability 假设接口能正常工作）。修复 500 让接口回到 capability 假设的状态，验收场景措辞不变。

## Risks / Trade-offs

- **[风险] 子模块各自定义了 maven-compiler-plugin `<configuration>` 会覆盖父配置** → 已确认：所有子模块的 pom 中未单独声明 compiler 配置，全部继承父 pom，无覆盖风险。
- **[风险] 字节码体积增加** → `MethodParameters` attribute 每个 method 增加数十字节，整体影响 <1%，可忽略。
- **[权衡] 同时启用 `-parameters` 与现有 Lombok annotation processor** → Lombok 在编译期修改 AST，与 `-parameters` 标志无冲突（Lombok 自身不读写 `MethodParameters`）。已验证 Spring Boot starter-parent 默认同时启用二者。
- **[风险] 旧 jar 包回滚** → 旧 jar 不携带参数名元数据，重新部署即恢复 500，回滚原子性 = 重新打包。

## Migration Plan

- 纯构建配置修复，无数据库变更、无运行时配置变更、无前端变更、无 API 契约变更。
- 部署：`mvn -f server/pom.xml clean package`（或对应子模块构建命令）→ 替换 jar → 重启 Spring Boot 进程。
- 回滚：移除 `server/pom.xml` 中的 `<parameters>true</parameters>` 一行 → 重新打包。
- 无需用户重新登录、无需清理缓存、无需任何数据迁移。

## Open Questions

无。根因清晰，修复方案单一且最小化。
