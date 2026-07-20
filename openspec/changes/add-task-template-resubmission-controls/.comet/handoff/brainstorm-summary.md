# Brainstorm Summary

- Change: add-task-template-resubmission-controls
- Date: 2026-07-20

## 确认的技术方案

open 阶段 design.md 已确定 D1-D9 高层框架（字段直接落 task_template / task_assignment 同步 snapshot / 跨 assignment 聚合 / submit 入口前置校验 / 错误码 TASK_SUBMISSION_* / Flyway V14 含 STANDING 回填 / 顶层 Checkbox UI / GET task-assignments 暴露 canSubmit / NULL snapshot 回退读 template）。design 阶段补强三项实现层决策：

1. **canSubmit 列表性能策略 = 批量 IN 查询 + 内存组装**
   - `GET /api/task-assignments?childId=...` 一次拉取 N 个 assignment 后，按 (child_id, template_id_set) 一次性 GROUP BY 聚合，Service 层按 template_id 映射回每个 assignment
   - N+1 → 2 条 SQL（已通过次数 + 累计积分各一条）；与 submit 校验共享同一 Mapper 方法

2. **并发兜底 = 应用层 SELECT...FOR UPDATE 锁 (child_id, template_id)**
   - 在 submit 前置校验事务内，先显式锁定 (child_id, template_id) 维度对应行
   - 锁对象：从 task_assignment 表取该 child+template 组合下任一行做 `FOR UPDATE`（行级锁，粒度细），借助 `(idx_assignment_child_template)` 复合索引定位
   - 不依赖审核 APPROVED 阶段二次校验，不在 approve 路径加额外锁
   - H2 PostgreSQL 模式 / MySQL InnoDB / PostgreSQL 均原生支持 `SELECT ... FOR UPDATE`

3. **聚合 SQL 实现 = XML mapper 手写 JOIN SQL**
   - 在 `TaskReviewMapper.xml` 与 `PointsLedgerMapper.xml` 新增按 (child_id, template_id) 聚合的 SQL
   - 跨方言只使用 SQL 标准函数（CONCAT、COALESCE、CASE WHEN）；不使用 MySQL 专属 JSON_EXTRACT 等运行时聚合（V14 数据回填脚本除外，那是一次性 DDL）
   - canSubmit 列表批量方法签名形如 `Map<TemplateId, Long> countApprovedBatchByTemplateAndChild(long childId, Collection<Long> templateIds)`，Mapper 返回 `List<Map>` 由 Service 组装

## 关键取舍与风险

- **行锁粒度选择**：锁 (child_id, template_id) 维度比锁 task_template 整行（影响所有孩子）更细，但需要确保 task_assignment 表上有 (child_id, template_id) 复合索引；V14 脚本需要补建 `idx_assignment_child_template` 索引（不存在时）
- **批量聚合上限**：单次列表查询 50 个 assignment 内属于同一 template 的会去重到 < 50 个 template_id，IN 查询性能可控；如未来模板数膨胀需要考虑分批
- **FOR UPDATE 死锁风险**：按 template_id 升序获取行锁，避免不同事务交叉锁顺序导致死锁
- **NULL snapshot 回退**：D9 设计对 V14 之前的老 assignment 仍生效（snapshot 为 NULL 时审核期回退读 template 当前值）；新创建的 assignment 一律固化 snapshot
- **STANDING BREAKING**：spec 已明确，迁移后服务端不再读 type_config.max_submissions；旧客户端 PUT 提交该字段会被忽略（不报错）。需要文档化 BREAKING 升级说明
- **聚合精度**：「已通过次数」按 task_review.decision=APPROVED 去重计数；REJECTED 后重投再次 APPROVED 计入新次数；REFUND 流水不影响累计积分（type=EARN 才统计）

## 测试策略

- **单元测试**：
  - `TaskTemplateServiceTest`：三字段默认值 / 范围校验 / STANDING 不再读 type_config / 旧客户端兼容
  - `TaskAssignmentServiceTest`：snapshot 三字段固化（单次 / 批量 / 周期生成三条路径）
  - `ResubmissionPolicyEvaluatorTest`（新组件）：max/cap 双校验 / 跨 assignment 聚合 / REFUND 不影响 / NULL snapshot 回退 / 默认 allow_resubmit=false 不校验
  - Mapper 测试：使用 H2 PostgreSQL 模式 fixture，验证 GROUP BY 聚合结果与 Java 内存 JOIN 等价
- **集成测试**：
  - `TaskReviewResubmissionControlIT`：端到端 6 个核心场景（默认不启用 / max=0 不限制 / cap=0 不限制 / 达 max 拒绝 / 达 cap 拒绝 / 跨 assignment 聚合）
  - `TaskAssignmentListCanSubmitIT`：列表 canSubmit 批量聚合与单条 submit 校验结果一致性
  - 并发测试：模拟两线程同时提交同一 child+template，断言只允许一个成功
- **迁移回归**：
  - `MigrationV14Test`：准备 V13 STANDING fixture → 跑 V14 → 验证 task_template 三字段映射正确、task_assignment.snapshot_template_* 为 NULL（老 assignment）、idx_assignment_child_template 索引存在、新 assignment 正确固化 snapshot
- **前端测试**：
  - `TaskTypeConfigForms.test.tsx`：STANDING 子表单不再有「最大提交次数」字段
  - `parent-save-dialogs.test.tsx`：勾选「允许重复提交」后 max/cap InputNumber 出现 + 默认值 0
  - `pages.test.tsx`（孩子端）：canSubmit=false 时按钮 disabled + tooltip 文案正确
- **E2E**：`web/e2e/` 新增家长配置上限 → 孩子重投达上限 → 列表禁用按钮 → 提交按钮报错链路

## Spec Patch

无。design.md / spec.md 三份 delta spec 在 open 阶段已完整覆盖场景。本阶段确认的实现决策（批量聚合 / FOR UPDATE 锁 / XML mapper）属实现层，不需要回写 delta spec。

## 检查点说明

本文件是 brainstorming 过程的恢复检查点；Design Doc 尚未落盘。恢复时需重新加载：

- `openspec/changes/add-task-template-resubmission-controls/.comet/handoff/design-context.md`
- 本文件 + 用户已确认的三项实现决策
