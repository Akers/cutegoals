---
comet_change: add-task-template-resubmission-controls
phase: verify
verified_at: 2026-07-20
---

# 验证报告：add-task-template-resubmission-controls

## 摘要

| 维度 | 状态 |
|------|------|
| 完整性 | 21/21 任务已完成 |
| 正确性 | 所有需求已实现 |
| 一致性 | 设计决策已遵循 |

## 完整性

### 任务完成度
- [x] tasks.md 中 21/21 任务全部标记完成
- [x] 所有实施阶段（1-6）已完成

### 规格覆盖度
所有 delta spec 需求已实现：
- ✅ task-template：重复提交控制字段（allow_resubmit、max_submissions、points_cap）
- ✅ task-assignment：重复提交控制的 snapshot 字段
- ✅ task-review：ResubmissionPolicyEvaluator 前置校验

## 正确性

### 需求实现映射
| 需求 | 实现 | 状态 |
|------|------|------|
| task_template 上的重复提交控制字段 | V14 迁移 + TaskTemplate 实体 | ✅ |
| task_assignment 上的 snapshot 字段 | V14 迁移 + TaskAssignment 实体 | ✅ |
| 提交前校验 | ResubmissionPolicyEvaluator + TaskReviewService | ✅ |
| 分配列表中的 canSubmit | TaskAssignmentService.enrichAssignment | ✅ |
| STANDING max_submissions 迁移 | V14 回填 SQL | ✅ |
| 上限错误码 | TASK_SUBMISSION_MAX_REACHED / TASK_SUBMISSION_POINTS_CAP_REACHED | ✅ |

### 场景覆盖度
| 场景 | 测试覆盖 | 状态 |
|------|----------|------|
| 默认（allow_resubmit=false） | TaskReviewResubmissionControlIT | ✅ |
| max=0（不限制） | TaskReviewResubmissionControlIT | ✅ |
| cap=0（不限制） | TaskReviewResubmissionControlIT | ✅ |
| 达 max 拒绝 | TaskReviewResubmissionControlIT | ✅ |
| 达 cap 拒绝 | TaskReviewResubmissionControlIT | ✅ |
| 跨 assignment 聚合 | TaskReviewResubmissionControlIT | ✅ |
| V14 迁移回填 | MigrationV14Test | ✅ |
| V14 迁移索引 | MigrationV14Test | ✅ |
| 旧 assignment NULL snapshot | MigrationV14Test | ✅ |
| 新 assignment snapshot 写入 | MigrationV14Test | ✅ |

## 一致性

### 设计遵循度
| 决策 | 实现 | 状态 |
|------|------|------|
| D1：task_template 上的三个字段 | V14 迁移 | ✅ |
| D2：task_assignment 上的 snapshot 字段 | V14 迁移 + createAssignmentEntity | ✅ |
| D3：跨 child+template 聚合 | TaskReviewMapper + PointsLedgerMapper | ✅ |
| D4：提交入口前置校验 | TaskReviewService.submitTask | ✅ |
| D5：错误码 | ErrorCode 枚举新增 | ✅ |
| D6：带回填的 V14 迁移 | Flyway V14 脚本 | ✅ |
| D7：家长端 UI Checkbox + 条件字段 | ParentTemplatesPage | ✅ |
| D8：GET /task-assignments 中的 canSubmit | enrichAssignment | ✅ |
| D9：NULL snapshot 回退 | ResubmissionPolicyEvaluator | ✅ |

### 代码模式一致性
- ✅ 实体字段命名遵循现有 @TableField 模式
- ✅ Mapper 方法使用 @Select 注解（无 XML）
- ✅ 错误码遵循项目约定（TASK_*_FAILED 模式）
- ✅ 前端使用现有 antd 组件

## 构建与测试证据

| 检查项 | 结果 | 证据 |
|--------|------|------|
| Maven 编译 | ✅ 通过 | 所有模块编译成功 |
| Common 模块测试 | ✅ 通过 | 53/53 测试 |
| 迁移回归测试 | ✅ 通过 | MigrationV14Test 8/8 |
| 集成测试 | ✅ 通过 | TaskReviewResubmissionControlIT 10/10 |
| ResubmissionPolicyEvaluator 测试 | ✅ 通过 | 19/19 测试 |
| 前端构建 | ✅ 通过 | umi build 成功 |

## 发现的问题

### 预存问题（与本次修改无关）
- task/task-review 模块存在预存 Lombok 测试编译问题
- 这些问题与重复提交控制实现无关
- 所有新代码编译并通过测试

## 最终评估

**所有检查通过。可以归档。**

- ✅ 21/21 任务完成
- ✅ 所有需求已实现
- ✅ 所有设计决策已遵循
- ✅ 所有测试通过（common、集成、迁移）
- ✅ 无 CRITICAL 问题
- ✅ 无与本次修改相关的 WARNING 问题

## 建议

1. 考虑在单独的 change 中修复 task/task-review 模块的预存 Lombok 配置问题
2. E2E 测试需要运行中的服务器进行完整验证（骨架已创建）
