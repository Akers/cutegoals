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
- **THEN** MUST 执行一次完整测试计划的重放（最终回归）
- **AND** 最终回归 MUST 零缺陷才能进入归档阶段
- **AND** 最终回归结果 MUST 写入 `reports/final.md` 作为归档证据

### Requirement: 测试报告必须可追溯与可审计

所有测试活动 MUST 产出可追溯的报告产物，任何后续读者能从单一入口了解发现了什么、修复了什么、验证了什么。

#### Scenario: 报告产物齐全

- **WHEN** 测试与修复流程推进
- **THEN** `reports/` 目录 MUST 至少包含：`test-plan.md`、`round-1.md`、`bug-NNN-<slug>.md`（每 bug 一份）、`round-2.md`（如有）、`final.md`
- **AND** 每份报告 MUST 标注生成时间、测试范围、执行者、环境版本
- **AND** 任何状态变更（open → fixed → verified）MUST 在对应文件中可追溯
