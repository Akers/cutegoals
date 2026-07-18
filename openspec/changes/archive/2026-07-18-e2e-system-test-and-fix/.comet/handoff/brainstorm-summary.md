# Brainstorm Summary

- Change: e2e-system-test-and-fix
- Date: 2026-07-18

## 确认的技术方案

E2E 系统测试与修复采用「3 阶段 × 多轮循环」执行架构：

- **Round-1（一整轮全量测试）**：6 个 Stage（启动环境 → 制定计划 → 分模块测试 admin/parent/child/cross/invariants/security → 汇总 round-1.md）。用户已确认执行节奏 = 「测完一整轮再统一修」。
- **修复阶段（按模块批量 commit）**：模块依赖顺序 auth → family → task → task-review → points → prize → exchange → admin → web-frontend。每模块内：bug-NNN 调研→改代码→单 bug 回归→模块阶段回归→commit。用户已确认 commit 策略 = 「按模块批量 commit」。
- **Round-2（最终全量回归）**：重放整个 test-plan.md，写 final.md（零缺陷证据）；如发现新 bug 回到修复阶段。

**用户决策**（深度设计层面）：
1. 执行节奏 = 测完一整轮再统一修（不是边测边修）
2. 不变量验证手段 = agent-browser + psql 直查 DB（不是纯 UI 推断）
3. 中断恢复策略 = 精细 checkpoint（每用例写 test-progress.md）
4. commit 策略 = 按模块批量 commit（commit 消息含 bug-NNN 列表）

## 关键取舍与风险

**取舍**：
- 一整轮再修：bug 相关性识别更强 vs 测试阶段上下文消耗大（用精细 checkpoint 缓解）
- psql 直查 DB：最权威的不变量验证 vs 需要预知表结构（Stage B 调研）
- 按模块批量 commit：粒度适中 vs 单 bug 难以单独 revert（接受）

**风险**：
- agent-browser 上下文消耗（缓解：每模块后强制压缩）
- 修复可能引入新 bug（缓解：分层回归 + 最多 3 轮后升级 oracle 评审）
- 测试数据污染（缓解：模块阶段回归前允许 partial reset）
- 范围扩张触发（缓解：暂停 + 用户决策点）
- 后端/前端启动失败（缓解：异常处理矩阵 + Critical bug 记录 + 跳过依赖用例）

## 测试策略

**分层回归**：
- 单 bug 回归：修复后立即重放该 bug 复现用例 + 直接关联用例
- 阶段回归：模块全部 bug 修完后重放该模块全部用例
- 最终回归：所有 bug fixed 后重放整个 test-plan.md

**不变量验证**：
- 每个不变量用例统一结构：操作前 DB 快照（hash）→ agent-browser 操作 → 操作后快照 → 精确验证 SQL
- helper 脚本 `reports/helpers/db-checks.sh` 封装常用 SQL
- 6 条核心不变量用例（I-001~I-006）

**安全基线验证**：三层观测
- Layer 1：agent-browser 捕获 API 响应，grep 敏感字符串
- Layer 2：浏览器存储扫描（localStorage/sessionStorage/cookie）
- Layer 3：后端日志（tail/grep 敏感字符串）
- 6 条安全用例（S-001~S-006）

**异常处理**：8 类异常的处理矩阵（启动失败 / DB 重置失败 / agent-browser 不可用 / 登录失败 / 数据污染 / 修复引入新 bug / 范围扩张 / 会话超时）

**上下文压缩安全点**：8 个强制安全点（每模块 round 报告落盘后 + 每模块修复 commit 后）

## Spec Patch

无。OpenSpec specs/system-e2e-test-coverage/spec.md 的 7 条 ADDED Requirements 已经覆盖了深度设计中需要验证的全部能力；本 Design Doc 是对实现细节的细化，不引入新需求，也不修改现有 requirement 的语义。
