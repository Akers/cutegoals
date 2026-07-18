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

- 每个「角色」使用独立 browser context：admin/parent/child 三套 cookie/localStorage 互不污染。
- 登录态在单轮测试内保持；新一轮测试开始前清空会话重登。

## Risks / Trade-offs

- **[Risk] 测试过程中发现缺陷数量过大导致 change 失控** → Mitigation: D5 的分模块归类 + 「范围扩张」决策点触发拆分。
- **[Risk] 重置 DB 会清除现有 dev 数据** → Mitigation: 测试前明确告知用户；如用户后续需要可从备份恢复（不在本 change 范围）。
- **[Risk] agent-browser 在动态 UI 上不稳定，可能误报** → Mitigation: 每个缺陷必须人工复核复现步骤；误报不计入 bug 清单，但要记录「误报原因」。
- **[Risk] 修复引入新缺陷** → Mitigation: D6 分层回归策略，特别是最终全量回归。
- **[Risk] OpenSpec context 写 MySQL 但实际是 PostgreSQL，可能存在其他配置漂移** → Mitigation: 以 `.env.dev` 和 `deploy/docker-compose.yml` 为事实源，发现不一致立即记录到报告。
- **[Trade-off] 不跑 Playwright 牺牲了自动化回归复用** → 但 agent-browser 的探索式覆盖更广，且本任务目标是一次性全量验证而非建立长期回归套件。

## Migration Plan

- 本 change 为 QA 工作流，无生产迁移。
- dev 环境迁移：`scripts/reset-dev-db.sh` 重置；测试期间 dev 数据库状态会被持续修改，测试结束后用户可手动决定是否再次重置。

## Open Questions

- 首轮测试启动前是否需要专门准备「初始家庭 + 孩子档案」的种子数据，还是完全依赖现有初始化流程（家长首次登录后创建）？倾向后者，以便同时测试初始化流程本身。
- 前端实际是 UmiJS（`web/src/.umi`）还是 Vite？需在启动前端 dev server 时确认（`web/package.json` 的 scripts）。

## Stage E/F 执行结果摘要（2026-07-18）

### 修复范围
- 14 个 bug 全部进入修复阶段
- 代码层修复：14/14 完成（含 bug-011 的 schema migration V13）
- 验证 PASS：13/14
- BLOCKED：1/14（bug-011 schema 改动需 DBA 应用 V13，session 表 owner=pmp 限制）

### 关键工程产物
- `reports/round-1.md` + `reports/round-2.md`：完整 Round-1 / Round-2 测试报告
- `reports/bug-001..014-*.md`：14 个 bug 详情文件（含根因/修复方向）
- `reports/evidence/round-2/`：Stage F 截图证据
- `reports/helpers/`：db_checks.py / security_checks.py / partial_reset.py
- `server/common/src/main/resources/db/migration/V13__add_child_session_support.sql`：schema migration
- `server/common/src/main/java/com/cutegoals/common/config/MybatisPlusConfig.java`：分页拦截器（修 bug-009）

### 待 DBA 完成的 Follow-up
应用 V13 schema 改动（session 表加 child_id 列、account_id 改 NULL、加 FK 和 CHECK），然后重启后端并回归 C-002~C-007、P-013/P-014、X-001。
