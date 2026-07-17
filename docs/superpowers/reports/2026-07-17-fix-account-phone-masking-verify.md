# 验证报告：fix-account-phone-masking

- **change**: `fix-account-phone-masking`
- **workflow**: hotfix（preset → open → build → verify → archive）
- **verify_mode**: light（手动覆盖）
- **review_mode**: off（hotfix 默认）
- **date**: 2026-07-17
- **commit**: `7e44db3`

## 规模评估

`comet-state scale` 自动评估结果为 `full`（tasks=10 > 3、changed_files=15 > 8），但其中：

- 8 个 `.comet/` 文件是 Comet workflow 元数据（状态、快照、事件日志）
- 3 个 `openspec/changes/.../` 文件是 OpenSpec 必需产物（proposal/design/tasks.md）
- 实际实现改动只有 **4 个源文件**：
  - `server/common/.../MaskUtil.java`（新增 `maskPhonePartial` 方法，+42 行）
  - `server/common/.../MaskUtilTest.java`（新增 7 个测试用例）
  - `server/instance-management/.../AccountManagementService.java`（1 行：`maskPhone` → `maskPhonePartial`）
  - `web/src/admin/pages/index.tsx`（删除 `mask` 闭包，phone 列去掉 `render`）
- 0 个 delta spec、0 个新 capability

依据 `comet-verify` 的覆盖机制手动设为 `light`，符合 hotfix 范围（10 个任务对应 1 个聚焦修复点，无 capability 增量）。

## 6 项轻量验证

| # | 检查项 | 结果 | 证据 |
|---|---|---|---|
| 1 | tasks.md 全部任务已完成 | PASS | 0 个 `- [ ]`，10/10 `- [x]` |
| 2 | 改动文件与 tasks.md 一致 | PASS | `git show --name-only HEAD`：4 个代码文件与 tasks 1.1/1.2/2.1/3.1 一一对应；其余 11 个为 Comet 元数据与 OpenSpec 产物 |
| 3 | 编译通过 | PASS | `mvn -pl instance-management -am test`：common/auth/instance-management 三模块全部 BUILD SUCCESS（编译嵌入于 test 阶段）；`cd web && npx tsc -b` exit 0；`cd web && npm run build` ✓ built in 5.73s |
| 4 | 相关测试通过 | PASS | `mvn -pl common test -Dtest=MaskUtilTest`：26/26 pass（含 7 个新增 `maskPhonePartial` 用例）；`mvn -pl instance-management -am test`：common 49 + auth 9 + instance-management 35 全部 pass，含 `AccountManagementServiceTest` 11/11 pass |
| 5 | 无明显安全问题 | PASS | `git show HEAD -- <4 个代码文件> \| grep -iE "password\|secret\|api[-_]?key\|token=\|System\.exit\|exec\(\|Runtime\."` 无新增命中；`maskPhonePartial` 仍属脱敏函数，短号 < 7 回退全掩码避免 PIN 泄漏；前端仅删除闭包，无新增网络/unsafe 操作 |
| 6 | 代码审查策略 | SKIP | `.comet.yaml` 中 `review_mode: off`（hotfix 默认），按 `comet-verify` Step 2a 第 6 项跳过自动审查；改动为机械修复（新增独立方法 + 1 行替换 + 删除组件内闭包），正确性已由 build/test 与下方根因消除 grep 验证覆盖 |

## 根因消除验证

```bash
# 前端不再有 mask 闭包或 slice(0,3)+'****'+slice(7) 模式
grep -nE "slice\(0, 3\)|const mask = " web/src/admin/pages/index.tsx
# (no output — 根因代码已消除)

# 后端账号列表改用 maskPhonePartial，maskPhone 仅由 InvitationService 用于日志/API
grep -n "maskPhone" server/instance-management/src/main/java/com/cutegoals/instancemanagement/service/AccountManagementService.java
# (no output)
grep -n "maskPhonePartial" server/instance-management/src/main/java/com/cutegoals/instancemanagement/service/AccountManagementService.java
# 53:            item.put("phone", MaskUtil.maskPhonePartial(account.getPhone()));
```

`MaskUtil.maskPhone`（全掩码）保持不变，`InvitationService.maskPhone`（私有方法，用于邀请日志与 `targetPhone` API 字段）零改动——本次 hotfix 不影响邀请流程与日志深度防御语义。

## 手工演算

| 输入 | 后端 `maskPhonePartial` 输出 | 前端渲染 | 修复前（双重脱敏） |
|---|---|---|---|
| `13612341249`（11 位） | `136*****249` | `136*****249` ✓ | `*******ED***` |
| `1234567`（7 位边界） | `123*567` | `123*567` ✓ | N/A（后端原返回 `***MASKED***`） |
| `123456`（6 位短号） | `***MASKED***` | `***MASKED***` ✓ | N/A |
| `null` / `""` | `null` / `""` | `—` ✓ | N/A |

## 结论

全部 6 项检查通过（5 PASS + 1 按策略 SKIP），根因消除验证通过，无 CRITICAL 或 IMPORTANT 问题。验证通过。

## 分支处理

- 当前分支：`main`（与先前 hotfix `fix-admin-auth-redirect`、`fix-admin-pages-500` 一致的项目惯例，直接在 main 提交）
- commit：`7e44db3 fix: 修复 admin 账号管理页手机号双重脱敏显示为 *******ED***`
- `branch_status: handled`（在 main 上 commit 完成，无独立 feature 分支需要合并/PR；若需推送远程，由用户自行 `git push origin main`）

## 手动浏览器验证（建议，归档后由用户执行）

1. 启动 dev server（后端 8981 + 前端 umi dev）后访问 `/admin/accounts`
2. 表格「手机号」列显示 `136*****249` 格式（前 3 位 + 5 个星号 + 后 3 位），不再显示 `*******ED***`
3. 不同手机号显示不同的脱敏值，管理员可区分账号
4. 短号（< 7 位）账号（如有）显示 `***MASKED***`

## 影响范围

- 后端：2 个文件（`MaskUtil.java` 新增方法，`AccountManagementService.java` 1 行替换）
- 前端：1 个文件（`admin/pages/index.tsx` 删除 mask 闭包与 phone 列 render）
- 测试：1 个文件（`MaskUtilTest.java` 新增 7 个用例）
- spec：无改动（无 delta spec）—— `instance-management` capability 「当前实例账号启停」Requirement 验收场景要求「脱敏手机号」，部分掩码仍属脱敏
- 日志/审计调用方：零影响（`MaskUtil.maskPhone` 全掩码语义不变）

## 运行时验证附录（archive-reopen 后补充）

首次 archive-reopen 触发原因是用户报告"显示还是 `***MASKED***`"。经 `comet` 入口路由进入 verify 阶段后，按 systematic-debugging 进行根因调查：

| 层 | 状态 | 证据 |
|---|---|---|
| 源码 `MaskUtil.java` | ✅ 含 `maskPhonePartial` | commit `7e44db3`，源文件 mtime 2026-07-17 12:33:55 |
| 编译产物 `MaskUtil.class` | ✅ 字节码含 `maskPhonePartial` | `javap -p MaskUtil.class \| grep maskPhone` 同时命中 `maskPhone` 与 `maskPhonePartial`；class mtime 12:40:45 |
| `AccountManagementService.class` | ✅ 调用 `maskPhonePartial` | class mtime 12:37:00 |
| 后端 dev server (PID 505303) | ❌ 启动于 09:12:21 早于修复 | `ps -o lstart -p 505303`；classpath 指向 target/classes，但 JVM 已加载旧版本类 |
| spring-boot-devtools 自动重载 | ❌ 未引入 | `grep -r spring-boot-devtools server/ --include=pom.xml` 无命中 |

**根因**：`mvn spring-boot:run` 启动的 JVM 不会监听 class 文件变化，进程一直运行 09:12 时的旧 `MaskUtil`（只有 `maskPhone` 全掩码）。前端 dev server 有 HMR 自动重载，所以前端代码改动已生效——这正是用户从 `*******ED***`（前端二次脱敏旧逻辑）变为 `***MASKED***`（前端不脱敏但后端仍全掩码）的原因，反向印证前端层修复已生效。

**结论**：代码层修复正确且必要，验证报告原 6 项检查结论保持成立。运行时症状属 dev server 未重启，非代码缺陷。用户确认按原计划归档；归档后由用户重启后端 dev server（在 `pts/2` 终端 Ctrl+C 停止 PID 505303，再 `mvn -pl web -am spring-boot:run -DskipTests -Dspring-boot.run.jvmArguments=-Dfile.encoding=UTF-8`），即可看到 `/admin/accounts` 表格手机号列显示 `136*****407` 格式。
