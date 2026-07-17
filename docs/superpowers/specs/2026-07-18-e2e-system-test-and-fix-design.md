---
comet_change: e2e-system-test-and-fix
role: technical-design
canonical_spec: openspec
---

# Design Doc: E2E 系统测试与修复执行架构

- **Change**: `e2e-system-test-and-fix`
- **Phase**: design
- **Date**: 2026-07-18
- **OpenSpec canonical**: `openspec/changes/e2e-system-test-and-fix/`
- **Handoff hash**: `87e48728e83e7fa6cec67b181ee6edd151366d7b8202fd55a3b701fe02622138`

> 本 Design Doc 是 OpenSpec `design.md` 高层架构的**深度技术细化**（实现方案、技术风险、测试策略、边界条件），不替代或重写 OpenSpec 的 proposal/design/specs/tasks。OpenSpec 产物仍为 canonical 事实源。

## 1. 背景与目标

### 1.1 背景

Open 阶段已确认：

- CuteGoals 2.0 是面向家庭的私有化部署任务与奖励协作平台（9 个后端模块 + admin/parent/child 三端 React/UmiJS 前端）。
- 系统从未做过以真实用户身份串起全链路的端到端验证，存在跨模块回归、UI 与后端契约漂移、安全日志泄露、核心不变量被破坏等隐患。
- 用户决策：用 agent-browser 全测（不跑 Playwright）；测试前重置数据库；单 change 内修全部级别缺陷；测试账号：管理员/家长 `13600049114/117315Akers`、孩子 `cici/PIN 180614`。

### 1.2 设计目标

把 OpenSpec 已定义的 7 条 capability requirement（覆盖三端 / 重置 DB / 真实账号 / 五级分级 / 全部修复 / 分层回归 / 可追溯）**落地为可执行的工程方案**：

- 明确执行节奏、checkpoint 结构、commit 策略、上下文压缩安全点。
- 明确核心不变量（积分/库存/流水/审核/兑换快照）的 DB 直查验证手段。
- 明确安全基线（密码/PIN/Token/手机号不进响应/存储/日志）的三层观测方法。
- 明确 bug 文件结构、生命周期状态机与修复阶段 SOP。
- 明确异常处理矩阵与上下文恢复协议。

### 1.3 非目标

- 不重写 OpenSpec 已确认的需求与决策（D1~D8）。
- 不规定每个具体 bug 的修复实现（修复方案在 round-1.md 产出后按 bug 单独制定）。
- 不引入新的 capability 或新的 requirement（如需要，按 OpenSpec 范围扩张决策点处理）。

## 2. 用户决策（深度设计层面）

| 决策点 | 选择 | 影响 |
|--------|------|------|
| 执行节奏 | **测完一整轮再统一修** | bug 相关性识别强，但测试阶段上下文消耗大 → 用精细 checkpoint 缓解 |
| 不变量验证手段 | **agent-browser + psql 直查 DB** | 最权威的 append-only / 幂等 / 非负验证；需要 Stage B 调研表结构 |
| 中断恢复策略 | **精细 checkpoint（每用例写 progress）** | 每用例完成立即落盘 `test-progress.md`；恢复时跳过已完成用例 |
| Commit 策略 | **按模块批量 commit** | 一个 commit = 一个模块的 bug 修复批次；commit 消息含 bug-NNN 列表 |

## 3. 执行架构（Execution Architecture）

### 3.1 三阶段循环

```
┌─────────────────────────────────────────────────────────────────┐
│                    Round-1: 一整轮全量测试                        │
├─────────────────────────────────────────────────────────────────┤
│ Stage A: 启动环境（DB reset + Redis + 后端 :8080 + 前端 :5173）  │
│ Stage B: 制定 test-plan.md（每用例 ID + 预期 + 判定证据）        │
│ Stage C: 分模块测试                                              │
│   C1: admin      → 写 reports/round-1-admin.md                  │
│   C2: parent     → 写 reports/round-1-parent.md                 │
│   C3: child      → 写 reports/round-1-child.md                  │
│   C4: cross-mod  → 写 reports/round-1-cross.md                  │
│   C5: invariants → 写 reports/round-1-invariants.md             │
│   C6: security   → 写 reports/round-1-security.md               │
│ Stage D: 汇总 reports/round-1.md（全部 bug 清单，分级排序）     │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                修复阶段（按模块批量 commit）                      │
├─────────────────────────────────────────────────────────────────┤
│ 模块顺序：auth → family → task → task-review → points →          │
│           prize → exchange → admin → web-frontend               │
│ 每模块：bug-NNN 调研→改代码→单 bug 回归→模块阶段回归→commit     │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                Round-2: 最终全量回归                              │
├─────────────────────────────────────────────────────────────────┤
│ 重放整个 test-plan.md → 写 reports/final.md（零缺陷证据）       │
│ 如发现新 bug：回到修复阶段，新增 round-3.md                     │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 模块修复批次顺序（按依赖深度）

```
auth (基础，无依赖)
  ↓
family (依赖 auth)
  ↓
task (依赖 family)
  ↓
task-review (依赖 task + points)
  ↓
points (流水不变量核心)
  ↓
prize (依赖 points)
  ↓
exchange (依赖 prize + points；核销幂等核心)
  ↓
admin (独立端，最后修)
  ↓
web-frontend (Cosmetic 类集中修)
```

### 3.3 上下文压缩安全点（强制）

每个安全点到达后，确保相关报告已落盘，然后触发上下文压缩：

1. Stage C1 (admin) 完成 → `round-1-admin.md` 落盘后
2. Stage C2 (parent) 完成 → `round-1-parent.md` 落盘后
3. Stage C3 (child) 完成 → `round-1-child.md` 落盘后
4. Stage C4 (cross-module) 完成 → `round-1-cross.md` 落盘后
5. Stage C5 (invariants) 完成 → `round-1-invariants.md` 落盘后
6. Stage C6 (security) 完成 → `round-1-security.md` 落盘后
7. Stage D (汇总 round-1.md) 完成 → 进入修复阶段前
8. 每个模块修复 commit 后（共 9 次）

**压缩恢复协议**：

- 压缩前确保 `reports/test-progress.md`（测试期）或 `bug-NNN-*.md`（修复期）已更新。
- 恢复时按顺序读取：
  1. `openspec/changes/e2e-system-test-and-fix/.comet/handoff/design-context.md`（任务上下文）
  2. `reports/test-progress.md`（执行进度）
  3. `reports/round-*.md`（已完成模块的发现）
  4. 当前正在执行的模块报告
- 不重新加载已退出的 agent-browser session；新开 session 即可。

## 4. 用例级 progress checkpoint

### 4.1 文件：`reports/test-progress.md`

```markdown
# Test Progress Tracker

- Change: e2e-system-test-and-fix
- Round: 1
- Started: 2026-07-18T...
- Last updated: 2026-07-18T...

## 用例进度表

| Case ID | Module | Status | Bug IDs | DB Hash | Notes |
|---------|--------|--------|---------|---------|-------|
| A-001 | admin.login | pass | - | - | - |
| A-003 | admin.account-mgmt | fail | bug-003 | - | 账号列表分页错误 |
| P-008 | parent.task-review.approve-dup | pass | - | h1a2b3 | 幂等验证通过 |
| C-005 | child.exchange | fail | bug-012, bug-013 | h4c5d6 | 库存扣减错误 |

## Bug 状态

| Bug ID | Severity | Module | Status | Case | Root-cause | Fix-commit |
|--------|----------|--------|--------|------|------------|------------|
| bug-003 | Medium | admin | open | A-003 | - | - |
| bug-012 | High | exchange | open | C-005 | - | - |

## 模块汇总

- [x] A admin (8 用例，1 fail，1 bug)
- [x] P parent (32 用例，3 fail，4 bug)
- [ ] C child (12 用例，进行中)
- [ ] X cross-module
- [ ] I invariants
- [ ] S security
```

### 4.2 Case ID 命名规则

- `A-NNN` admin
- `P-NNN` parent
- `C-NNN` child
- `X-NNN` cross-module（端到端跨模块）
- `I-NNN` invariants（核心不变量）
- `S-NNN` security（安全基线）

### 4.3 DB Hash 字段用途

测试涉及关键不变量表（`points_ledger`、`exchange_snapshot`、`task_review_log` 等）时，记录操作前后的表内容 hash，用于回归时验证「流水未被篡改」：

```bash
psql -h localhost -p 35432 -U cutegoals -d cutegoals -tAc \
  "SELECT md5(string_agg(t::text, ',' ORDER BY id)) FROM points_ledger"
```

### 4.4 恢复协议

1. 会话恢复时先读 `reports/test-progress.md`。
2. 跳过 Status=pass/fail 的用例。
3. 找到第一个未完成用例（Status 为空），从此处续跑。
4. Bug 状态表用于跟踪修复阶段进度。

## 5. 不变量 DB 验证策略

### 5.1 关键表清单（Stage B 制定测试计划时预先调研）

| 模块 | 关键表 | 不变量 |
|------|--------|--------|
| points | `points_account`、`points_ledger`、`points_adjustment` | 余额非负；流水 append-only |
| task-review | `task_review`、`task_review_log` | 审核幂等；审核历史不可变 |
| exchange | `exchange_order`、`exchange_snapshot`、`prize_stock` | 核销幂等；库存非负；快照不可变 |
| auth | `user_credential`、`session_token` | 不存储明文密码/PIN；token hash 化 |
| family | `child_profile`、`device_authorization` | PIN 不明文落库 |

> 实际表名以 `server/` JPA 实体定义为准；Stage B 在制定 test-plan 时实际核对。

### 5.2 不变量用例统一结构

```markdown
### Case I-NNN: <不变量名称> 验证

**操作前快照**：
psql 命令 → `hash_before = h1a2b3...`

**agent-browser 操作**：<步骤>

**操作后快照**：
psql 命令 → `hash_after = h1a2b3...`

**精确验证 SQL**：
- 新增流水数 = N（预期）
- updated_at > created_at 行数 = 0（append-only）
- 余额 = X（非负）
- 重复操作后增量 = 0（幂等）

**判定**：通过 / 失败（关联 bug-NNN）
```

### 5.3 不变量用例分布（Stage C5）

| Case ID | 验证目标 | 验证手段 |
|---------|---------|---------|
| I-001 | 积分流水 append-only | ledger hash + `updated_at > created_at` 检查 |
| I-002 | 积分余额非负（构造负数场景） | `balance` 查询 + 拒绝响应 |
| I-003 | 审核幂等 | audit-log hash + 重复审核后增量 = 0 |
| I-004 | 兑换快照不可变 | snapshot 表 hash + 字段不变 |
| I-005 | 库存非负（超额兑换） | stock 查询 + 拒绝响应 |
| I-006 | 核销幂等 | 重复核销后状态不变 + 无重复扣减 |

### 5.4 Helper 脚本：`reports/helpers/db-checks.sh`

封装常用 SQL 查询为可复用脚本：

```bash
# 用法示例
./db-checks.sh ledger-hash
./db-checks.sh balance <child_id>
./db-checks.sh audit-log-hash <task_id>
./db-checks.sh stock <prize_id>
./db-checks.sh snapshot-hash <order_id>
```

回归测试时直接调脚本，无需重新写 SQL。

## 6. 安全基线验证策略

### 6.1 三层观测矩阵

| 安全字段 | API 响应 | 浏览器存储 | 后端日志 |
|---------|----------|------------|----------|
| 明文密码 | 登录响应不含 password 字段 | 不写入 localStorage/cookie | 不输出 `password=` |
| PIN | 设备授权/child 登录响应不含明文 PIN | 不写入 localStorage | 不输出 `pin=` |
| Token | 不返回完整 token（脱敏或 jwt，不含明文密码） | localStorage 仅可存 hash/jwt | 不输出 `token=` |
| 手机号 | API 响应中手机号必须脱敏（`136****4914`） | 不在客户端 | 不输出完整手机号 |

### 6.2 三层验证手段（每个安全用例覆盖三层）

```markdown
### Case S-NNN: <场景>

**操作**：通过 agent-browser 在 /parent 输入 13600049114 / 117315Akers 登录

**Layer 1 - API 响应**：
- agent-browser 捕获 POST /api/auth/login 的响应 JSON
- 检查：响应体不应包含 "117315Akers"、"117315"、"Akers"、明文 password 字段
- 检查：响应中可含 token，但 token 必须是脱敏后的 hash 或 jwt

**Layer 2 - 浏览器存储**：
- agent-browser 执行 `localStorage`、`sessionStorage`、`document.cookie`
- grep 敏感字符串："117315"、"180614"、"13600049114"、"password"、"pin"
- 只允许 token hash 或脱敏后的标识符存在

**Layer 3 - 后端日志**：
- 测试期间持续 `tail -f reports/backend.log`
- 每个安全用例后 grep 敏感字符串
- 任何命中即 Critical bug
```

### 6.3 日志采集策略（推荐方案 A）

**方案 A（推荐，不动应用配置）**：

- 启动后端时重定向 stdout/stderr：`mvn ... spring-boot:run > reports/backend.log 2>&1 &`
- 每个安全用例后：`grep -E '117315|180614|13600049114|password=|pin=' reports/backend.log`
- 命中即记录 Critical bug

**方案 B（不推荐）**：修改 logback 配置增加 file appender —— 这属于"为测试改动应用配置"，可能引入测试本身的行为偏差。

### 6.4 Helper 脚本：`reports/helpers/security-check.sh`

```bash
# 用法
./security-check.sh response <url-pattern>    # 扫描 agent-browser 抓取的响应
./security-check.sh storage                    # 扫描浏览器存储
./security-check.sh logs                       # 扫描后端日志
./security-check.sh logs-since <timestamp>     # 增量扫描
```

### 6.5 安全用例清单（Stage C6）

| Case ID | 场景 | 观测重点 |
|---------|------|----------|
| S-001 | parent 登录响应 | password / token / 手机号脱敏 |
| S-002 | admin 登录响应 | 同上 |
| S-003 | child 设备授权（PIN 输入） | PIN 不进响应 / 存储 / 日志 |
| S-004 | 任务审核 API 响应 | 不返回敏感字段 |
| S-005 | 兑换 API 响应 | 手机号脱敏、token 脱敏 |
| S-006 | 后端日志全量扫描 | 整个测试期间敏感字符串扫描 |

## 7. Bug 文件结构与生命周期

### 7.1 状态机

```
                    ┌──────────────────┐
                    │   open           │ ← 测试中发现并记录
                    └────────┬─────────┘
                             │ 开始修复
                             ▼
                    ┌──────────────────┐
                    │   investigating  │ ← 根因分析中
                    └────────┬─────────┘
                             │ 根因确定，开始改代码
                             ▼
                    ┌──────────────────┐
                    │   in-progress    │ ← 代码改动中
                    └────────┬─────────┘
                             │ 改完 + 单 bug 回归通过
                             ▼
                    ┌──────────────────┐
                    │   fixed          │ ← 单 bug 回归通过
                    └────────┬─────────┘
                             │ 模块阶段回归通过
                             ▼
                    ┌──────────────────┐
                    │   verified       │ ← 阶段回归通过
                    └────────┬─────────┘
                             │ 最终全量回归通过
                             ▼
                    ┌──────────────────┐
                    │   closed         │ ← 归档
                    └──────────────────┘

  任意阶段：发现需范围扩张 → wontfix-in-this-change（拆分到新 change）
```

### 7.2 Bug 单文件模板：`reports/bug-NNN-<slug>.md`

```markdown
# Bug-NNN: <简短标题>

- Severity: Critical | High | Medium | Low | Cosmetic
- Module: auth | family | task | task-review | points | prize | exchange | admin | web-frontend
- Status: open | investigating | in-progress | fixed | verified | closed | wontfix
- Discovered in: round-1 / case A-003
- Fixed in commit: <sha>（空则未修）
- Regression: round-2 / pass or fail

## 复现步骤

1. ...
2. ...
3. ...

## 预期

<正确行为>

## 实际

<观察到的错误行为>

## 证据

- 截图：`reports/evidence/bug-NNN-<step>.png`
- API 响应：`reports/evidence/bug-NNN-response.json`
- DB 状态：`reports/evidence/bug-NNN-db.json`
- 后端日志片段：`reports/evidence/bug-NNN-backend.log`

## 根因（修复阶段填写）

<代码层面根因，定位到文件:行号>

## 修复方案（修复阶段填写）

- 改动点 1：`server/.../XxxService.java:NN` — <改动说明>
- 改动点 2：`web/src/.../Xxx.tsx:NN` — <改动说明>
- 影响面：<其他受影响功能>

## 单 bug 回归（修复后填写）

- 重放复现用例：pass / fail
- 关联用例：<列出>：pass / fail

## 阶段回归（模块批量回归后填写）

- 重放该模块全部用例：pass / fail（如有 fail 列出）

## 最终回归（final 全量回归后填写）

- 全量重放：pass / fail
```

### 7.3 证据目录结构

```
reports/
├── test-progress.md
├── round-1.md
├── round-1-admin.md
├── round-1-parent.md
├── round-1-child.md
├── round-1-cross.md
├── round-1-invariants.md
├── round-1-security.md
├── round-2-<module>.md（修复阶段，每模块一份阶段回归报告）
├── final.md
├── scope-decisions.md（范围扩张决策记录）
├── bug-001-<slug>.md
├── bug-002-<slug>.md
├── ...
├── evidence/
│   ├── bug-NNN-step-3.png
│   ├── bug-NNN-response.json
│   ├── bug-NNN-db.json
│   ├── bug-NNN-backend.log
│   ├── network/（所有 API 调用记录）
│   └── critical-<bug-id>-dump.sql（不变量违反的完整 DB dump）
└── helpers/
    ├── db-checks.sh
    ├── security-check.sh
    └── partial-reset.sh
```

### 7.4 Bug 编号规则

- 全局递增 `bug-NNN`（001-999），按发现顺序编号。
- 不重新编号（即使 wontfix 也保留编号）。
- 修复阶段新增的"修复中发现的副作用 bug"使用 `bug-NNN-side` 后缀。

### 7.5 状态更新责任

| 阶段 | 谁更新 | 更新哪些字段 |
|------|--------|-------------|
| 测试期 | orchestrator | Status: open；填 severity / module / case / repro / expected / actual / evidence |
| 修复期 | fixer | Status: investigating → in-progress → fixed；填根因 / 修复方案 / 单 bug 回归 |
| 模块回归 | orchestrator 或 fixer | Status: fixed → verified；填阶段回归结果 |
| 最终回归 | orchestrator | Status: verified → closed；填最终回归 |

## 8. 修复阶段工作流

### 8.1 每模块修复 SOP

```
1. 读 round-1.md，过滤出该模块全部 bug
2. 按 severity 排序（Critical → Cosmetic）
3. 逐 bug：
   a. 创建 reports/bug-NNN-<slug>.md，Status=open
   b. 调研代码定位根因（codegraph_explore / grep）
   c. 修改代码（多个 bug 可能在同一文件，合并改动）
   d. Status=in-progress
   e. 执行单 bug 回归（重放 case 的 agent-browser 步骤）
      - 通过：Status=fixed，填回归结果
      - 失败：Status=in-progress，继续改（最多 3 轮后升级 oracle 评审）
   f. bug-NNN 单文件完成
4. 模块全部 bug 修完：
   a. 执行该模块全部用例的阶段回归（重放 module 全部 case）
   b. 所有 bug Status=verified
   c. 写 reports/round-2-<module>.md
5. git commit（按模块批量）
```

### 8.2 Commit 格式

```bash
git commit -m "fix(e2e/round-1/<module>): bug-NNN, bug-NNN, bug-NNN

模块批量修复：

- bug-NNN: <一句话描述> (Critical)
- bug-NNN: <一句话描述> (High)
- bug-NNN: <一句话描述> (Medium)

回归：
- 单 bug 回归全部通过
- 模块阶段回归：reports/round-2-<module>.md"
```

**约束**：

- 一个 commit 只包含同一模块的 bug 修复。
- 跨模块的共享工具类修改（如 `common/`）按"主要受益模块"归类。
- 若 commit 涉及 schema 变更或跨模块重构，**暂停**，触发范围扩张决策点。

### 8.3 范围扩张决策点

**触发条件**：

- 修复需要数据库 schema 变更（migration）。
- 修复需要修改超过 3 个模块的代码。
- 修复引入新的 capability（需要新增 spec）。
- 修复改变了 OpenSpec 已有的 requirement 语义。

**触发后**：

- 暂停当前模块修复。
- 通过 `question` 工具问用户：
  - 选项 1：继续在本 change 内修（接受范围扩张，更新 proposal/design/tasks）
  - 选项 2：拆分为新 change（当前 bug 标记 wontfix-in-this-change，归档时说明）
- 记录决策到 `reports/scope-decisions.md`。

### 8.4 修复阶段 agent-browser 使用约束

**只在以下场景使用 agent-browser**：

- 单 bug 回归（重放该 bug 的复现用例）
- 模块阶段回归（重放该模块全部用例）
- 最终全量回归

**不在调研代码时使用 agent-browser**：调研用 `codegraph_explore` / `grep`。

### 8.5 修复阶段不使用 git worktree

- 当前已是 e2e-system-test-and-fix change 的主分支工作。
- 修复 commit 直接在主分支推进，便于 comet verify 阶段追溯。
- worktree 隔离反而增加跨 worktree 同步 `reports/` 目录的复杂度。

## 9. 异常处理矩阵

| 异常 | 检测手段 | 处理 |
|------|----------|------|
| 后端启动失败 | `curl /api/health` 超时或非 UP | 读 `backend.log` 定位；5 分钟内修不好则记录为 Critical bug-001，跳过后端依赖用例 |
| 前端启动失败 | `curl :5173` 超时 | 同上，bug-002，跳过前端依赖用例 |
| DB 重置失败 | `reset-dev-db.sh` 非零退出 | 立即停止，问用户是否手动恢复 DB |
| agent-browser CLI 不可用 | skill 加载失败 | 立即停止，问用户是否切换到 Playwright 或人工测试 |
| 登录失败 | UI 显示错误或 API 401 | 记录为 Critical bug，跳过依赖该账号的用例 |
| 测试数据污染 | 后续用例受前序 bug 影响 | 记录为 blocker，可能需要 partial DB reset |
| 修复后引入新 bug | 单 bug 回归 fail | 状态回退到 in-progress，继续改；最多 3 轮后升级 oracle 评审 |
| 范围扩张触发 | schema 变更或跨模块重构 | 暂停，触发决策点（见 §8.3） |
| 会话超时 | 上下文耗尽 | 压缩 → 从 `test-progress.md` 恢复 |

### 9.1 测试数据污染的边界处理

- 每个模块测试期间不重置 DB（OpenSpec D3 决策）。
- 修复阶段的"模块阶段回归"前**允许** partial reset：
  - 清空本模块测试产生的数据（如 `DELETE FROM tasks WHERE created_at > $test_start`）
  - 保留基础数据（用户、家庭、孩子档案）
- partial reset 脚本：`reports/helpers/partial-reset.sh <module>`
- 全量最终回归前允许完整 `reset-dev-db.sh` + 完整重放。

### 9.2 agent-browser 操作的鲁棒性策略

- **超时重试**：单步操作超时 30s，重试 1 次后失败。
- **截图证据**：每个关键步骤截图（成功也截，作为对照）。
- **网络响应捕获**：所有 API 调用记录请求/响应到 `reports/evidence/network/`。
- **会话隔离**：admin/parent/child 三个 browser context，互不串扰。
- **登出清理**：每个模块测试结束后显式登出，避免 cookie 残留。

### 9.3 不变量违反的紧急处理

如果测试中发现**核心不变量被破坏**（如积分变负、流水被改）：

- 立即记录为 **Critical bug**。
- 保留现场：完整 DB dump 到 `reports/evidence/critical-<bug-id>-dump.sql`。
- 截图 + 后端日志 + 网络响应全部留证。
- 不在本用例内修复（修复阶段统一处理）。
- 跳过依赖该不变量的后续用例（避免连锁失败）。

## 10. OpenSpec 任务映射

本 Design Doc 的执行架构直接映射到 OpenSpec `tasks.md` 的 6 大任务组：

| OpenSpec 任务组 | Design Doc 对应章节 |
|----------------|---------------------|
| 1. 环境准备与启动 | §3.1 Stage A + §9 异常处理 |
| 2. 测试计划制定 | §3.1 Stage B + §5.1 表清单 + §4 命名规则 |
| 3. 第一轮 agent-browser 全量测试 | §3.1 Stage C/D + §5 不变量 + §6 安全 + §4 checkpoint |
| 4. 缺陷修复 | §3.1 修复阶段 + §8 SOP + §7 bug 文件 |
| 5. 阶段性与最终回归测试 | §8.1 步骤 4 + §3.1 Round-2 |
| 6. 归档准备 | comet-verify 阶段处理 |

OpenSpec `tasks.md` 不需要重写；本 Design Doc 是对其第 3、4、5 任务组的执行细节深化。

## 11. Spec Patch

**无**。OpenSpec `specs/system-e2e-test-coverage/spec.md` 的 7 条 ADDED Requirements 已覆盖本 Design Doc 需要验证的全部能力。本 Design Doc 是对实现细节的细化，不引入新需求，也不修改现有 requirement 的语义。

## 12. 风险与缓解

| 风险 | 严重度 | 缓解策略 |
|------|--------|----------|
| agent-browser 上下文消耗爆炸 | High | §3.3 强制安全点压缩 + §4 精细 checkpoint |
| 修复阶段引入新 bug | Medium | §7 单 bug 回归 + §8.1 阶段回归 + 3 轮后升级 oracle |
| 测试数据污染 | Medium | §9.1 partial reset + §8.4 修复期不调研用 agent-browser |
| 范围扩张频繁触发 | Medium | §8.3 决策点；严重时拆 change |
| 后端/前端启动失败 | Low | §9 异常处理矩阵 + Critical bug 跳过依赖用例 |
| OpenSpec specs 表名与实际不符 | Low | §5.1 Stage B 实际核对，发现偏差作为 Spec Patch |

## 13. Open Questions

无（用户已通过 4 轮 question 工具确认全部关键决策）。

## 14. 下一步

按 `comet-design` Step 2/3：

1. 本 Design Doc 已写入 `docs/superpowers/specs/2026-07-18-e2e-system-test-and-fix-design.md`。
2. 无 Spec Patch，无需重新生成 handoff。
3. 运行 `comet state set e2e-system-test-and-fix design_doc docs/superpowers/specs/2026-07-18-e2e-system-test-and-fix-design.md`。
4. 运行 `comet guard e2e-system-test-and-fix design --apply` 推进到 build 阶段。
5. 进入 `/comet-build` 开始实施。
