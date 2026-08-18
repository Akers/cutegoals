# Outcome

恢复 `server/exchange` 模块 test-compile 通过。前次 change `kid-exchanges-recording-listing-fix`（已归档）在 `ExchangeServiceTest.java` 新增了 2 个使用 `Page<Exchange>` 与 `LambdaQueryWrapper` 的测试方法，但未对应 import，导致 `./scripts/start-dev.sh` 在 exchange 模块 `test-compile` 阶段报"找不到符号"（10 errors），整整个启动流程阻塞在 exchange 模块。

修复后：`mvn -pl server/exchange test-compile` 退出码 0；`start-dev.sh` 能继续推进到 instance-management / web 模块。

# Scope

**唯一改动文件**：`server/exchange/src/test/java/com/cutegoals/exchange/service/ExchangeServiceTest.java`

仅 import 区段追加 2 行（与 `ExchangeService.java:3-4` 同路径）：

```java
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
```

# Non-goals

- 不改生产代码（`ExchangeService.java`、Mapper、Entity）。
- 不改既有 19 个测试方法（仅新增 2 个测试方法的 import 链路）。
- 不修项目级 Lombok `annotationProcessorPaths` 配置问题（与本 change 无关，影响所有 server 模块）。
- 不动上一已归档 change `kid-exchanges-recording-listing-fix` 的任何产物（spec、verification、evidence、archive 目录）。
- 不动启动脚本 `scripts/start-dev.sh`。

# Acceptance examples

- V1 — `cd server/exchange && mvn test-compile` → 退出码 0；输出"找不到符号 Page"或"找不到符号 LambdaQueryWrapper"均不出现。
- V2 — `cd server && mvn -pl exchange -am test-compile` → 退出码 0；reactor 顺序中 common/auth/task/points/family/task-review/prize/exchange 全部 SUCCESS。
- V3 — `grep -c "@Test" server/exchange/src/test/java/com/cutegoals/exchange/service/ExchangeServiceTest.java` → 输出 21（19 既有 + 2 新增），与本 change 引入前的测试方法数 +2 一致。
- V4 — `grep -c "import com.baomidou.mybatisplus" server/exchange/.../ExchangeServiceTest.java` → 输出 2（LambdaQueryWrapper + Page）。

# Decisions

- D1：使用与 `ExchangeService.java:3-4` 完全相同的 import 路径，不引入新依赖、不重写测试（最小修复原则）。
- D2：不创建新 capability spec，因本 change 是回归修复（regression fix）而非新增能力 — 只改 import，不改变任何行为。Runtime 在 advance 时通过 `spec_changes: []` 表达"无规格变更"。
- D3：V1/V2 即使仍报告 Lombok `The blank final field X may not have been initialized`，也接受为项目级问题，验证只判定"找不到符号"是否消除。

# Open questions

无。所有问题已与用户确认。

# Constraints and invariants

- C1：start-dev.sh 启动顺序：common → auth → task → points → family → task-review → prize → **exchange**（修复点） → instance-management → web。exchange 失败会跳过 instance-management/web。
- C2：mybatis-plus 版本与 `ExchangeService.java` 已使用一致（service 已能编译，说明版本可用）。
- C3：测试文件已有 19 个测试，新加 2 个测试不改方法数（仅补 import），V3 验证 @Test 计数仍为 21。

# Verification expectations

按 acceptance examples 中的 V1/V2/V3/V4 验证：
- V1 + V2 — mvn 命令退出码 0，无"找不到符号 Page/LambdaQueryWrapper"。
- V3 + V4 — 文件静态 grep 验证。
- 不重跑 mvn test（Lombok 项目级阻塞仍存在，与本 change 无关）。
- 不跑 ESLint（前端未受影响）。
- 不跑后端集成测试。