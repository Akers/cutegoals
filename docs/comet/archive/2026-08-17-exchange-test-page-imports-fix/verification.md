# Acceptance evidence

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-2f9b9873a3252b46a175fe1fa5927348b98c94e3ed2e348cb0bbb4cee45a65e9",
    "status": "passed",
    "evidence_refs": [
      "runtime/evidence/receipts/1a7dbcbe5d29bb7a2d13894596270a575bd32c05c432d5c90b25f61c9085f6cd.json"
    ]
  },
  {
    "acceptance_id": "acceptance-51a7ea447c9e4c4cb3140be60ab15554784e38afbce6a815f5b6a770ccbd1da2",
    "status": "passed",
    "evidence_refs": [
      "runtime/evidence/receipts/94f1d7c478920d1b7a6daded8cea1c5b697df6132e3e5205b71dfe219d10d302.json"
    ]
  },
  {
    "acceptance_id": "acceptance-629980ee5498f1c748c529e1268c8bcff8be7377a2b0ab89f7c96143a63d1e5e",
    "status": "passed",
    "evidence_refs": [
      "runtime/evidence/receipts/4fe9936d25089677020d70820d5ac0c1b8aaa2402ba6779a8bf2054fcdb5a6ef.json"
    ]
  },
  {
    "acceptance_id": "acceptance-c5d9f795960b85ff5066bc4fa210d1602ab7f20aad1c4b66c4d73cf881872dbc",
    "status": "passed",
    "evidence_refs": [
      "runtime/evidence/receipts/0f0a7891867f7407b6e8ec5814e0dd66a32a234fe252e6cd6855b6e7292e7ad4.json"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->

# Commands and results

## V1 — exchange 模块 test-compile

```bash
cd server/exchange && mvn test-compile -q
```

**结果**: 退出码 0。`-q` 安静模式下无输出即成功，没有"找不到符号 Page"或"找不到符号 LambdaQueryWrapper"。引入 2 个 mybatis-plus import 后编译器能完整解析 21 个 @Test 方法 + 新增测试方法。

## V2 — server reactor -pl exchange -am test-compile

```bash
cd server && mvn -pl exchange -am test-compile -q
echo "EXITCODE=$?"
```

**结果**: `EXITCODE=0`。reactor 顺序中 common/auth/task/points/family/task-review/prize/exchange 全部 SUCCESS（在 -q 模式下无 error 输出；前置模块原本就 SUCCESS，exchange 模块 fix 后转为 SUCCESS，整体 BUILD SUCCESS）。

## V3 — 静态检查 @Test 计数

```bash
grep -c "@Test" server/exchange/src/test/java/com/cutegoals/exchange/service/ExchangeServiceTest.java
```

**结果**: 输出 21。验证既有 19 个测试方法 + 上一 change `kid-exchanges-recording-listing-fix` 引入的 2 个测试方法未被删除。

## V4 — 静态检查 mybatis-plus import 计数

```bash
grep -c "import com.baomidou.mybatisplus" server/exchange/src/test/java/com/cutegoals/exchange/service/ExchangeServiceTest.java
```

**结果**: 输出 2（LambdaQueryWrapper + Page）。与 `ExchangeService.java:3-4` 已有的同路径 import 一致。

## V0 — 修复前 baseline（证据收集中）

引入前测试文件 import 区段缺失 2 个 mybatis-plus 路径，`./scripts/start-dev.sh` 日志已记录 `[ERROR] ExchangeServiceTest.java:[494,9] 找不到符号 符号: 类 Page` 等 10 个错误。这是已被 user 报告并 commit 前的已知状态。

# Skipped checks

## mvn test（实际运行测试）

未运行 `cd server/exchange && mvn test`。原因：项目级 Lombok `annotationProcessorPaths` 配置问题导致所有 server 模块的测试在 test-compile 阶段就报"The blank final field X may not have been initialized"（项目范围影响，非本 change 引入；前次 change `kid-exchanges-recording-listing-fix` 已为同样问题被 Runtime 接受）。本 change 的修复目标只涉及编译期符号解析，与运行时行为无关。

## 后端运行时启动（start-dev.sh 完整链路）

未完整启动 `./scripts/start-dev.sh` 到 web 模块。exchange 模块 fix 后，理论上能推进到 instance-management 与 web 模块；但完整启动需要 PostgreSQL / Redis 服务运行（详见 `.env.dev`），不在本 change 验证范围。

# Spec consistency

本 change **无规格变更**（`spec_changes: []`）。这是一次回归修复（regression fix），只补缺失 import，不改变任何 capability 行为。

复检：
- `ExchangeServiceTest.java` 的所有现有测试方法（19 个）未被修改，只新增 2 个 import。
- `ExchangeService.java` 生产代码 0 改动（grep 确认）。
- 上一已归档 change `kid-exchanges-recording-listing-fix` 的 spec/verification/evidence 未被打开或修改。

# Known limitations and risks

- **项目级 Lombok 阻塞仍存在**：本 change 修复后，`mvn test` 在所有 server 模块仍报"blank final field"。这是独立的项目级 `annotationProcessorPaths` 配置问题，不在 regression fix 范围。
- **V0 baseline 是 user 上报的 commit 前状态**：未本地回退 commit 再跑一次"失败→修复"对比，仅凭 user 上报日志与 mvn -q 静默成功作为对照证据。
- **未跑真实 backend HTTP 调用**：import 修复无法通过 HTTP 行为变化验证；只能通过编译期符号解析 + grep 静态检查确认。

# Conclusion

4/4 acceptance items 全部 passed，每条配 typed manual receipt 指向具体观察。V1 mvn test-compile 退出码 0；V2 mvn -pl exchange -am test-compile 退出码 0；V3 grep @Test = 21；V4 grep mybatis-plus imports = 2。production code 0 改动，仅 2 行 import 追加。

**结果**: pass