# Comet Design Handoff

- Change: e2e-system-test-and-fix
- Phase: design
- Mode: compact
- Context hash: 87e48728e83e7fa6cec67b181ee6edd151366d7b8202fd55a3b701fe02622138

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/e2e-system-test-and-fix/proposal.md

- Source: openspec/changes/e2e-system-test-and-fix/proposal.md
- Lines: 1-32
- SHA256: d02d8c025e44bf86b07b4555d43920ccee5383c698e9e05d0f54d25aa8d13da4

```md
## Why

CuteGoals 2.0 经过多次功能迭代后，系统缺少一次完整的端到端（E2E）质量验证。各模块（admin/parent/child 三端 + 9 个后端领域）虽然在单元测试和部分组件测试层面有覆盖，但跨模块、跨角色的真实业务流从未通过模拟真实用户操作的方式被系统性地遍历过。为了在投入更大规模用户之前发现并修复所有阻塞性和细节缺陷，需要对系统做一次「真实启动 + agent-browser 实际操作 + 修复 + 回归」的完整闭环，覆盖管理员初始化、家长管理家庭/任务/审核/积分/奖品/兑换、孩子完成/提交/兑换/盲盒等全部主流程与边界场景。

## What Changes

- 制定覆盖三端（admin / parent / child）所有 UI 可达功能的端到端测试计划，按模块组织测试用例清单。
- 在本地 dev 环境实际启动 PostgreSQL（已运行于 :35432）、Redis、Spring Boot 后端（:8080）和前端 dev server（:5173），并对数据库执行 `scripts/reset-dev-db.sh` 重置为干净初始状态。
- 使用 `agent-browser` 以真实用户身份（管理员/家长 13600049114/117315Akers；孩子 cici/PIN 180614）逐项执行测试用例，覆盖正常路径、关键边界、不变量校验和安全基线。
- 形成结构化测试报告，按严重度（Critical / High / Medium / Low / Cosmetic）分级记录所有发现的缺陷，附复现步骤、证据截图与影响范围。
- 针对每个缺陷制定修复方案，在当前 change 内一次性修复全部级别缺陷（Critical / High / Medium / Low / Cosmetic 全部纳入范围）。
- 修复完成后进行回归测试，验证所有缺陷已消除且未引入新问题，循环直至测试报告为零缺陷。

## Capabilities

### New Capabilities

- `system-e2e-test-coverage`: 端到端系统测试能力，定义对 admin/parent/child 三端所有功能的真实启动 + agent-browser 操作式覆盖要求、缺陷分级与回归闭环规则。

### Modified Capabilities

<!-- 本 change 是新增测试能力并修复测试中发现的缺陷；任何对现有功能行为的实质性修改（如审核幂等、积分流水不变量等）都将以 bug 修复形式落地，并在 specs 中以 delta 形式记录。测试启动后根据实际发现的缺陷再补充 Modified Capabilities。 -->

## Impact

- **后端**：`server/` 下 9 个领域模块（auth、family、task、task-review、points、prize、exchange、instance-management、web）均可能在测试中发现缺陷并需要修复。
- **前端**：`web/src/admin|parent|child` 三端页面、共享组件、API 封装均需接受端到端验证；当前前端实际基于 UmiJS（`web/src/.umi` 存在），与 README 描述的「Vite」存在偏差，需在测试中确认实际运行时栈。
- **数据**：测试开始前执行 `scripts/reset-dev-db.sh` 重置 PostgreSQL `cutegoals` schema，所有历史数据被清除；测试过程中产生的家庭/任务/积分/奖品/兑换数据保留在 dev 库中用于回归。
- **核心不变量**：测试必须显式验证「积分余额和库存不得为负、积分流水/审核历史/兑换快照不可变、审核/兑换/退款幂等且事务原子」等核心不变量不被破坏。
- **安全基线**：测试必须显式验证「明文密码、PIN、令牌、完整手机号不被记录到日志或响应中」。
- **依赖服务**：测试依赖本机 PostgreSQL:35432 与 Redis:6379 可用，且 8080/5173 端口空闲。
- **范围外**：不跑 Playwright（`e2e/tests/`）；不做性能/压力测试；不做安全渗透测试；不做多实例/多家庭边界（MVP 单实例单家庭）。

```

## openspec/changes/e2e-system-test-and-fix/design.md

- Source: openspec/changes/e2e-system-test-and-fix/design.md
- Lines: 1-101
- SHA256: c08c32de165f8f0ba37cfaf7c94497728ece1f2266cd1b199b9a1386cb408b1e

[TRUNCATED]

```md
## Context

CuteGoals 2.0 是面向家庭的私有化部署任务与奖励协作平台，由 9 个后端 Spring Boot 模块（auth/family/task/task-review/points/prize/exchange/instance-management/web）和 React/UmiJS 前端（admin/parent/child 三端）构成。系统已有单元测试和组件测试，但缺少一次以真实用户身份串起全链路的端到端验证；多次迭代后各模块之间可能存在跨模块回归、状态不一致、UI 与后端契约漂移、安全日志泄露、核心不变量被破坏等问题，这些只能通过模拟真实用户操作才能发现。

本设计文档定义「端到端测试 → 缺陷修复 → 回归测试」闭环的执行架构，不规定具体缺陷的修复实现（具体修复方案在测试报告产出后按 bug 单独制定）。

## Goals / Non-Goals

**Goals:**

- 在本地 dev 环境用真实启动的 PostgreSQL + Redis + Spring Boot 后端 + UmiJS 前端运行整套系统。
- 以真实账号（家长/管理员 13600049114、孩子 cici）通过 `agent-browser` 逐项操作三端全部 UI 可达功能。
- 在测试过程中显式验证核心不变量（积分/库存非负、流水/审核/兑换快照不可变、审核/兑换/退款幂等且事务原子）与安全基线（不记录明文敏感字段）。
- 产出结构化测试报告，缺陷按 Critical / High / Medium / Low / Cosmetic 五级分级，附复现步骤与影响范围。
- 在本 change 内修复全部级别缺陷，回归通过后归档。

**Non-Goals:**

- 不做性能、压力、容量测试。
- 不做安全渗透、漏洞扫描、依赖项 CVE 全量审计。
- 不跑 `e2e/tests/` 下的 Playwright 自动化套件（用 agent-browser 替代以获得探索式覆盖）。
- 不验证多实例/多家庭场景（MVP 单实例单家庭）。
- 不重写既有功能架构；任何重构以「最小修复」为原则。

## Decisions

### D1：测试执行方式 = `agent-browser` 操作式探索

选择用 `agent-browser` 而非扩展 Playwright 脚本：

- **理由**：Playwright 脚本需要预先知道选择器和预期流，本任务目标是「发现未知缺陷」；agent-browser 能根据 UI 实时反馈做探索式决策，对 UI 改动具有鲁棒性，能在不预先写选择器的情况下发现意外状态、错误提示、跳转异常。
- **备选**：扩展 `e2e/tests/` Playwright 套件 → 被否，因为回归脚本只验证「已知路径」，无法发现新缺陷。
- **代价**：执行成本与上下文消耗高于脚本，但收益（缺陷发现率）更高，且产出报告天然带步骤证据。

### D2：测试环境 = 本地 dev（PG:35432 + Redis + 后端:8080 + 前端:5173）

- PostgreSQL :35432 已运行；通过 `deploy/docker-compose.yml` 启动 Redis；通过 `scripts/start-dev.sh` 启动后端；前端在 `web/` 下用 `npm run dev` 启动（实际为 UmiJS dev server）。
- API 代理：前端 `/api` 经 Vite/Umi 代理到 `:8080`，保持单一来源。

### D3：数据库初始化策略 = 测试前一次性重置

- 测试启动前运行 `scripts/reset-dev-db.sh` 清空并重建 schema。
- 测试过程中产生的数据**保留**，作为回归测试的基础数据集；回归测试在同一数据集上重放以验证修复未破坏状态。
- **备选**：每个用例独立重置 → 被否，过度的 DB 重置会掩盖跨用例状态污染类缺陷，且启动成本高。

### D4：缺陷分级 = 五级（Critical / High / Medium / Low / Cosmetic）

| 级别 | 判定标准 | 示例 |
|------|---------|------|
| Critical | 系统无法启动、核心流程完全阻塞、数据损坏、安全敏感字段泄露 | 后端启动失败、登录失败、积分变负、明文密码写入日志 |
| High | 核心功能不可用或有严重错误，但有 workaround | 审核流程错误但可重试、兑换库存错算 |
| Medium | 非核心功能异常、边界场景错误、UX 严重缺陷 | 校验提示缺失、列表分页错误、错误码不准确 |
| Low | 轻微功能缺陷、文案错误、非阻塞 | 默认值不合理、按钮状态不刷新、提示文案歧义 |
| Cosmetic | UI 视觉、布局、对齐、响应式 | 间距不一致、移动端布局错乱、颜色对比度低 |

**全部级别都必须修复**（用户已确认）。

### D5：缺陷修复策略 = 单 change 内全修

- 所有缺陷在本 change 内修复，不再拆分为多个 changes。
- **风险**：若缺陷数量大，change 范围会膨胀；**缓解**：每个修复作为 tasks.md 中独立任务项，按模块归类；若执行阶段发现某模块修复需要重构或架构调整，再触发 build 阶段「范围扩张需重新设计」决策点，拆分为新 change。

### D6：回归测试策略 = 分层回归

- **针对单 bug 的回归**：修复后只重放该 bug 的复现用例 + 直接关联用例。
- **阶段性回归**：每完成一个模块（或一批同类）修复后，重放该模块全部用例。
- **最终回归**：所有 bug 修复完成后，重放整套测试计划，作为归档前的最终证据。

### D7：测试报告位置与结构

- 报告根目录：`openspec/changes/e2e-system-test-and-fix/reports/`
- 结构：
  - `test-plan.md` — 测试计划（模块 × 用例清单）
  - `round-1.md` — 第一轮测试结果（发现的 bug 清单）
  - `bug-<NNN>-<slug>.md` — 每个 bug 一份详情（复现步骤、证据、根因、修复方案、回归结果）
  - `round-2.md`, `round-3.md` … — 回归测试结果
  - `final.md` — 最终归档报告（零缺陷证据）

### D8：agent-browser 会话与账号隔离


```

Full source: openspec/changes/e2e-system-test-and-fix/design.md

## openspec/changes/e2e-system-test-and-fix/tasks.md

- Source: openspec/changes/e2e-system-test-and-fix/tasks.md
- Lines: 1-62
- SHA256: 9ca120bd5d058a277d6dd11951a340182614904a6f021af915e345bba366a61e

```md
## 1. 环境准备与启动

- [ ] 1.1 启动 Redis 容器（`deploy/docker-compose.yml` 的 `mit-modelide-core-redis` 服务），确认 :6379 监听
- [ ] 1.2 确认 PostgreSQL :35432 可连接（用户 cutegoals / 库 cutegoals / schema cutegoals）
- [ ] 1.3 运行 `scripts/reset-dev-db.sh` 重置数据库到干净初始状态，记录重置前后表清单作为证据
- [ ] 1.4 启动后端：`cd server && mvn clean -pl web -am spring-boot:run -DskipTests`，确认 :8080 监听且 `/api/health` 返回 UP
- [ ] 1.5 启动前端：`cd web && npm install && npm run dev`，确认 :5173 监听；记录实际是 Vite 还是 UmiJS（从 `web/package.json` 与启动日志判定）
- [ ] 1.6 验证前端 `/api` 代理到 :8080 正常工作（任意未登录请求转发即可）

## 2. 测试计划制定

- [ ] 2.1 在 `openspec/changes/e2e-system-test-and-fix/reports/test-plan.md` 写入完整测试计划，覆盖 admin / parent / child 三端所有 UI 可达功能
- [ ] 2.2 计划中显式列入核心不变量验证用例：积分/库存非负、积分流水/审核历史/兑换快照不可变、审核/兑换/退款幂等与事务原子
- [ ] 2.3 计划中显式列入安全基线用例：明文密码/PIN/令牌/完整手机号不出现在 API 响应、浏览器存储、后端日志
- [ ] 2.4 计划按模块（auth/family/task/task-review/points/prize/exchange/instance-management/admin）分组，每模块至少含一条正常路径 + 一条边界用例
- [ ] 2.5 计划中标注每个用例的预期结果与判定证据（截图 / API 响应 / DB 状态）

## 3. 第一轮：agent-browser 全量测试

- [ ] 3.1 准备 agent-browser：加载 skill、确认 CLI 可用、规划三套独立 browser context（admin / parent / child）
- [ ] 3.2 admin 端测试：使用 13600049114/117315Akers 登录 `/admin`，覆盖概览、账号管理、审计日志、健康检查四个功能
- [ ] 3.3 parent 端测试 - 家庭：登录 `/parent`，覆盖家庭信息查看/编辑、添加孩子档案（cici）、添加家长账号、设备授权流程
- [ ] 3.4 parent 端测试 - 任务：发布任务模板、查看任务列表、查看孩子任务完成情况
- [ ] 3.5 parent 端测试 - 审核：对孩子提交的任务执行通过/驳回、验证幂等（重复审核不产生多次积分）
- [ ] 3.6 parent 端测试 - 积分：查看积分流水、调整积分、验证余额非负与流水不可变
- [ ] 3.7 parent 端测试 - 奖品：创建奖品、创建盲盒、库存管理
- [ ] 3.8 parent 端测试 - 兑换：查看兑换申请、核销、验证核销幂等与库存扣减原子
- [ ] 3.9 child 端测试：用 cici/PIN 180614 登录 `/child`，覆盖今日任务查看/提交、奖品兑换、盲盒抽取、历史记录
- [ ] 3.10 跨模块端到端：孩子提交任务 → 家长审核 → 积分入账 → 孩子兑换奖品 → 家长核销，验证全链路状态一致
- [ ] 3.11 不变量验证用例：尝试构造余额为负、重复审核、并发兑换等场景，验证系统拒绝或正确处理
- [ ] 3.12 安全基线用例：登录、敏感操作期间观察浏览器存储与后端日志，验证不出现明文敏感字段
- [ ] 3.13 整理第一轮发现的全部缺陷到 `reports/round-1.md`，按 Critical/High/Medium/Low/Cosmetic 分级，每条含编号、模块、复现步骤、预期/实际、证据
- [ ] 3.14 为每个 Critical/High 缺陷单独创建 `reports/bug-NNN-<slug>.md`，记录根因初判

## 4. 缺陷修复

- [ ] 4.1 根据 round-1 缺陷清单更新本 tasks.md，为每个 bug 新增独立修复任务（按模块分组：auth/family/task/task-review/points/prize/exchange/admin/web-frontend）
- [ ] 4.2 修复 auth 模块缺陷（含安全基线类问题）
- [ ] 4.3 修复 family 模块缺陷
- [ ] 4.4 修复 task 模块缺陷
- [ ] 4.5 修复 task-review 模块缺陷（重点：幂等与事务原子）
- [ ] 4.6 修复 points 模块缺陷（重点：余额非负、流水不可变）
- [ ] 4.7 修复 prize 模块缺陷
- [ ] 4.8 修复 exchange 模块缺陷（重点：核销幂等、库存扣减原子）
- [ ] 4.9 修复 instance-management / admin 端缺陷
- [ ] 4.10 修复前端 UmiJS 三端 UI 缺陷（含 Cosmetic 级视觉问题）
- [ ] 4.11 每个修复完成后立即执行单 bug 回归（重放该 bug 复现用例），结果写入对应 `bug-NNN-*.md`
- [ ] 4.12 任何修复涉及跨模块重构、schema 变更或新增 capability 时，暂停并触发「范围扩张」决策点交用户选择

## 5. 阶段性与最终回归测试

- [ ] 5.1 每完成一个模块的批量修复后，执行该模块全部用例的阶段回归，结果写入 `reports/round-N.md`
- [ ] 5.2 所有缺陷标记 fixed 后，执行最终全量回归（重放整个 test-plan.md），结果写入 `reports/final.md`
- [ ] 5.3 最终回归必须零缺陷；若发现新缺陷，回到第 4 节修复并补充新一轮回归报告
- [ ] 5.4 整理所有 bug 文件，确认状态全部为 verified

## 6. 归档准备

- [ ] 6.1 校验 `reports/` 目录产物齐全（test-plan / round-* / bug-* / final）
- [ ] 6.2 在 design.md 末尾追加「测试结果摘要」（缺陷数量、修复数量、回归轮次）
- [ ] 6.3 运行 `comet guard e2e-system-test-and-fix verify --apply` 进入 verify 阶段
- [ ] 6.4 按 comet-verify 流程完成验证报告，准备归档

```

## openspec/changes/e2e-system-test-and-fix/specs/system-e2e-test-coverage/spec.md

- Source: openspec/changes/e2e-system-test-and-fix/specs/system-e2e-test-coverage/spec.md
- Lines: 1-94
- SHA256: f54ed57bb73eb90947ebeadb31d4dd7d69180dbbf7f8b0edf88daa8254ecc6a1

[TRUNCATED]

```md
## ADDED Requirements

### Requirement: 测试计划必须覆盖三端全部 UI 可达功能

系统 MUST 提供一份结构化测试计划，覆盖 admin/parent/child 三端所有 UI 可达功能入口，按模块（auth/family/task/task-review/points/prize/exchange/instance-management）组织，每个功能至少包含一个正常路径用例和必要的边界用例。

#### Scenario: 测试计划覆盖三端核心功能

- **WHEN** 测试执行者打开测试计划 `reports/test-plan.md`
- **THEN** 计划 MUST 列出 admin 端（概览/账号/审计/健康）、parent 端（家庭/任务/审核/积分/奖品/兑换）、child 端（今日/奖品/盲盒/历史）的全部主要功能
- **AND** 每个功能项 MUST 标注至少一条可执行的操作步骤序列
- **AND** 计划 MUST 显式包含核心不变量验证用例（积分/库存非负、流水/审核/兑换快照不可变、审核/兑换/退款幂等）
- **AND** 计划 MUST 显式包含安全基线验证用例（敏感字段不进日志、明文密码/PIN/令牌/完整手机号不出现在响应或日志）

### Requirement: 测试前必须重置数据库到干净初始状态

测试启动前 MUST 执行 `scripts/reset-dev-db.sh` 重置 PostgreSQL `cutegoals` schema，确保所有测试从已知初始状态开始，避免历史数据干扰缺陷判定。

#### Scenario: 测试启动前数据库被重置

- **WHEN** 测试执行者准备启动第一轮测试
- **THEN** MUST 先运行 `scripts/reset-dev-db.sh`（或等效 SQL）清空并重建 schema
- **AND** 重置后 MUST 通过健康检查（后端 `/api/health` 或等效端点）确认 schema 可用
- **AND** 重置产生的数据库状态 MUST 在本轮测试全程保留，不中途重置

### Requirement: 必须使用真实账号通过 agent-browser 执行测试

所有功能用例 MUST 通过 `agent-browser` 以真实用户身份执行，覆盖真实登录、真实 UI 交互、真实 API 调用；不使用 mock、不直接调 API 绕过 UI。

#### Scenario: 三端账号分别登录并保持独立会话

- **WHEN** 测试执行者开始一轮测试
- **THEN** admin/parent 端 MUST 使用账号 `13600049114`（密码 `117315Akers`）通过 `/admin` 与 `/parent` 登录
- **AND** child 端 MUST 通过设备授权流程让孩子档案 `cici`（PIN `180614`）在 `/child` 登录
- **AND** 三端 MUST 使用相互隔离的 browser context，cookie/localStorage 不串扰

### Requirement: 缺陷必须按五级严重度分级记录

测试过程中发现的每一个缺陷 MUST 按 Critical / High / Medium / Low / Cosmetic 五级分级记录，并附复现步骤、预期/实际结果、影响模块、证据（截图或日志片段）。

#### Scenario: 缺陷记录包含必要字段

- **WHEN** 测试执行者在任何用例中发现预期与实际不符
- **THEN** MUST 在 `reports/round-N.md` 中登记一条缺陷
- **AND** 缺陷 MUST 有唯一编号 `bug-NNN`
- **AND** MUST 记录：标题、严重度、模块、复现步骤、预期、实际、证据、状态（open/fixed/verified/wontfix）
- **AND** Critical/High 级缺陷 MUST 额外附根因初判

### Requirement: 所有级别缺陷都必须在本 change 内修复

Critical / High / Medium / Low / Cosmetic 全部级别 MUST 在本 change 内修复，不得降级为 wontfix 或推迟到后续 change（除非触发范围扩张决策点）。

#### Scenario: 缺陷全量修复

- **WHEN** 一轮测试完成并形成缺陷清单
- **THEN** 每个缺陷 MUST 有对应的修复任务（落到 tasks.md）
- **AND** 每个修复 MUST 记录修复方案（根因、改动点、影响面）到 `reports/bug-NNN-<slug>.md`
- **AND** Cosmetic 级缺陷也 MUST 被修复，不得跳过

#### Scenario: 范围扩张触发拆分

- **WHEN** 某个缺陷的修复需要跨模块重构、数据库 schema 变更或新增 capability
- **THEN** MUST 暂停并按 `comet/reference/decision-point.md` 交用户决策
- **AND** 用户可选择继续在本 change 内修（接受范围扩张）或拆分为新 change
- **AND** 无论哪种选择都 MUST 在报告中记录决策依据

### Requirement: 修复后必须通过分层回归测试

每个修复 MUST 通过分层回归验证：单 bug 回归（修复后立即重放该 bug 用例）、阶段性回归（按模块批量回归）、最终全量回归（所有 bug 修复完成后重放整套测试计划）。

#### Scenario: 单 bug 回归

- **WHEN** 缺陷 `bug-NNN` 修复完成
- **THEN** MUST 立即重放 `bug-NNN` 的复现用例
- **AND** 必须确认原缺陷不再出现
- **AND** 同时 MUST 重放与该 bug 直接关联的用例（同模块相关路径）

#### Scenario: 最终全量回归作为归档门槛

- **WHEN** 所有缺陷都被标记为 fixed

```

Full source: openspec/changes/e2e-system-test-and-fix/specs/system-e2e-test-coverage/spec.md
