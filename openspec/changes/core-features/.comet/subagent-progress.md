# Comet Subagent Dispatch Progress: core-features

> 此文件为 comet-build subagent-driven-development 模式的持久进度检查点。
> 与 `.superpowers/sdd/progress.md`（SDD 通用 ledger）互补：本文件聚焦 comet 任务勾选状态。

## 上下文

- **Change**: core-features
- **Branch**: main（用户确认留在 main 继续）
- **Plan**: docs/superpowers/plans/2026-07-10-core-features-plan.md
- **Design**: docs/superpowers/specs/2026-07-10-core-features-design.md
- **Tasks**: openspec/changes/core-features/tasks.md（103 项，10 章）
- **base_ref**: 1ee61b63fa21919e0a5c4255ab694d90e5f55c2c（已与 .comet.yaml 对齐）
- **当前 HEAD**: a7581c47e3325725aa06b959e5c3b0dbaf41f15a

## 推进策略

用户决策：先整体 gap 分析再实施。
- 已发现先前 comet-classic 已实施 plan phase 0-8 + phase 9 综合验证
- tasks.md 仍 93 项未勾，原因是 comet-classic 不勾 OpenSpec tasks.md
- Phase 9 残留：5 个 InstanceHealthServiceTest 测试失败
- Phase 6 残留：5 个 non-blocking reservations 待处理

## 任务进度

### 阶段 0：工程基线（任务 0.1-0.7，对应 OpenSpec 1.1-1.7）
- 状态：先前 comet-classic 已完成（progress.md 行 7-13）
- 待办：核查 spec 偏离 + 批量勾选

### 阶段 1：数据库（任务 1.1-1.8，对应 OpenSpec 2.1-2.8）
- 状态：已完成（progress.md 行 14, 24）
- 待办：核查 spec 偏离 + 批量勾选

### 阶段 2：认证与家庭（任务 2.1-2.12，对应 OpenSpec 3.1-3.12）
- 状态：已完成 Phase 2-A + 2-B（progress.md 行 15-29）
- 待办：核查 spec 偏离 + 批量勾选

### 阶段 3：任务模板与分配（任务 3.1-3.10，对应 OpenSpec 4.1-4.10）
- 状态：已完成（progress.md 行 30）
- 待办：核查 spec 偏离 + 批量勾选

### 阶段 4：审核与积分（任务 4.1-4.x，对应 OpenSpec 5.1-5.9）
- 状态：已完成（progress.md 行 32-40）
- 待办：核查 spec 偏离 + 批量勾选

### 阶段 5：奖品盲盒兑换（任务 5.x，对应 OpenSpec 6.1-6.11）
- 状态：已完成（progress.md 行 42-57）
- 待办：核查 spec 偏离 + 批量勾选

### 阶段 6：实例审计（任务 6.x，对应 OpenSpec 7.1-7.8）
- 状态：已完成（progress.md 行 59-106），5 个 non-blocking reservations
- 待办：核查 spec 偏离 + 批量勾选 + 决策 5 个残留问题

### 阶段 7：部署运维（任务 7.x，对应 OpenSpec 9.1-9.10）
- 状态：已完成（progress.md 行 116-123），4 Critical 已修复
- 待办：核查 spec 偏离 + 批量勾选

### 阶段 8：Web 三端（任务 8.x，对应 OpenSpec 8.1-8.10）
- 状态：已完成（progress.md 行 108-114），79 tests passing
- 待办：核查 spec 偏离 + 批量勾选

### 阶段 9：综合验证（OpenSpec 10.1-10.10）
- 状态：已勾选（plan-ready 阶段预勾），但 Phase 9 残留 5 测试失败
- 待办：5 测试失败修复（InstanceHealthServiceTest）

## 风险与残留

1. **Phase 6 的 5 个 reservations（non-blocking）**：是否在进入 verify 前补做，需用户决策或等 final review 后处理。
2. **设计文档可能的漂移**：plan 1.x review 中标记的 prize_snapshot/points_transaction 表与 design doc 不一致（遗留记录，待 verify 阶段决定）。

## 派发记录

- 2026-07-14：分析阶段，未派发 explorer（由进度 ledger 直接确认已完成）。
- 2026-07-14：派发 `fix-1` (fixer) 修复 `web/` 前端测试偏离，已提交 `7bc9dcc`。
- 2026-07-14：批量勾选 `tasks.md` 第 1-9 章，已提交 `e20b0fc`。
- 当前 HEAD：`e20b0fc`
