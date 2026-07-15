# Comet Design Handoff

- Change: migrate-parent-to-antd-pro
- Phase: design
- Mode: compact
- Context hash: 8075e03b162e80f2375f0f49afe846b4e3f69e410cd0c574139b4ec1eede5f65

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/migrate-parent-to-antd-pro/proposal.md

- Source: openspec/changes/migrate-parent-to-antd-pro/proposal.md
- Lines: 1-24
- SHA256: 00990293a632fc2acb52b0ad3e81558243bfb27c0acf1a6f362e81e95df349be

```md
## Why

CuteGoals 家长端当前 10 个页面（家庭概览/家庭管理/孩子档案/任务模板/任务分配/任务审核/积分/奖品/盲盒/兑换履约）均基于自建组件构建。在基础设施已迁移至 Ant Design Pro（Change 1）后，需将家长端页面重建为 antd/pro-components 实现，以消除自建组件边界场景的 bug 和不一致问题。

## What Changes

- 将 parent 端 10 个页面全部使用 antd/pro-components 重建 UI 层
- 业务逻辑、API 调用、状态管理保持不变
- 页面组件从自建 React 组件迁移至 ProTable/ProForm/Card/Descriptions 等 antd 企业级组件
- 保持 parent 主题色（通过 ConfigProvider）和 ProLayout 导航

## Capabilities

### New Capabilities
- `parent-page-migration`: Parent 端家庭概览、家庭管理、孩子档案、任务模板、任务分配、任务审核、积分、奖品、盲盒、兑换履约 10 个页面的 antd 重建

### Modified Capabilities
- `web-app`: Parent 端页面实现方式从自建组件变为 antd，业务需求不变

## Impact

- **代码**：`web/src/parent/pages/` 下 10 个页面组件全部重写 UI 层
- **依赖**：无新增依赖（依赖 Change 1 引入的 antd 体系）
- **测试**：页面级测试用例需按 antd 组件行为更新

```

## openspec/changes/migrate-parent-to-antd-pro/design.md

- Source: openspec/changes/migrate-parent-to-antd-pro/design.md
- Lines: 1-35
- SHA256: bbabcc670d6d0aa7f3d6a15ed07c3a8d7cf6c04e81c95506699c230326a89203

```md
## Context

本变更为 Ant Design Pro 迁移拆分的第三阶段——Parent 端页面重建。依赖 Change 1（`migrate-to-antd-pro-infra`）提供 UmiJS 构建、ProLayout 布局、ConfigProvider parent 主题和共享组件映射。

## Goals / Non-Goals

**Goals:**
- 将 parent 端 10 个页面 UI 层从自建组件替换为 antd/pro-components
- 保持全部业务逻辑不变（API 调用、状态管理、权限控制）
- 保持 parent 角色视觉主题

**Non-Goals:**
- 不修改页面功能或业务流程
- 不新增或删除页面
- 不修改后端 API
- 不修改 ParentLayout（在 Change 1 中实现）

## Decisions

### D1: 使用 ProTable 重构列表类页面
家庭管理、孩子档案、任务模板、任务分配、积分、奖品、盲盒、兑换履约使用 ProTable 实现，利用内置搜索栏、分页、列排序、行操作。

### D2: 使用 ProForm 重构表单类页面
任务审核使用 ProForm + Form.List 实现批量审核表单，奖品/盲盒创建使用 ProForm 实现。

### D3: 家庭概览使用多种 antd 组件组合
家庭概览类似仪表板，使用 antd `Card`、`Statistic`、`Table` 组合实现，替代自建组件。

## Risks / Trade-offs

- **[风险] 家长端 10 页为最大子模块，逐页迁移工作量大** → 缓解：按页面顺序迁移，每页独立可验证
- **[风险] 任务审核页面交互复杂（批量审批+滑动）** → 缓解：使用 antd Table rowSelection + Button 组合替代滑动操作，符合 antd 设计范式

## Open Questions
无。此变更为 Change 1 的延续，技术方案由 Change 1 的 design.md 定义。

```

## openspec/changes/migrate-parent-to-antd-pro/tasks.md

- Source: openspec/changes/migrate-parent-to-antd-pro/tasks.md
- Lines: 1-58
- SHA256: bf20be4b9e89f68f6e06804d092a7377ce6f6e866aa4381d5a3448540cf522ea

```md
## 1. 家庭概览页面

- [ ] 1.1 使用 Card + Statistic + Table 重建家庭概览仪表板
- [ ] 1.2 保留统计数据和快捷操作 API 调用
- [ ] 1.3 验证：家庭成员数、任务完成率、积分概况正确展示

## 2. 家庭管理页面

- [ ] 2.1 使用 ProTable 重建家庭成员列表，支持编辑操作
- [ ] 2.2 验证：成员列表加载、编辑功能正常

## 3. 孩子档案页面

- [ ] 3.1 使用 ProTable 重建孩子列表，支持搜索、分页
- [ ] 3.2 使用 ProForm + Modal 实现新增/编辑孩子档案
- [ ] 3.3 验证：孩子档案增删改查功能正常

## 4. 任务模板页面

- [ ] 4.1 使用 ProTable 重建模板列表，支持分类筛选
- [ ] 4.2 使用 ProForm + Modal 实现创建/编辑任务模板
- [ ] 4.3 验证：模板分类筛选、增删改查功能正常

## 5. 任务分配页面

- [ ] 5.1 使用 ProTable 重建已分配任务列表
- [ ] 5.2 使用 ProForm + Select 实现从模板批量分配
- [ ] 5.3 验证：模板选择、分配操作、列表刷新功能正常

## 6. 任务审核页面

- [ ] 6.1 使用 Table + rowSelection 重建审核列表，支持多选
- [ ] 6.2 实现批量通过/拒绝和单独审核操作
- [ ] 6.3 验证：批量审核和单独审核功能正常，审核后列表刷新

## 7. 积分页面

- [ ] 7.1 使用 ProTable 重建积分流水列表
- [ ] 7.2 保留手动积分调整功能
- [ ] 7.3 验证：积分流水展示、调整功能正常

## 8. 奖品页面

- [ ] 8.1 使用 ProTable 重建奖品列表
- [ ] 8.2 使用 ProForm + Modal 实现新增/编辑奖品
- [ ] 8.3 验证：奖品增删改查功能正常

## 9. 盲盒页面

- [ ] 9.1 使用 ProTable 重建盲盒列表
- [ ] 9.2 使用 ProForm + Modal 实现新增/编辑盲盒（含内容概率）
- [ ] 9.3 验证：盲盒增删改查功能正常

## 10. 兑换履约页面

- [ ] 10.1 使用 ProTable 重建兑换记录列表
- [ ] 10.2 实现履约状态变更操作
- [ ] 10.3 验证：兑换列表展示、履约操作功能正常

```

## openspec/changes/migrate-parent-to-antd-pro/specs/parent-page-migration/spec.md

- Source: openspec/changes/migrate-parent-to-antd-pro/specs/parent-page-migration/spec.md
- Lines: 1-81
- SHA256: d4a11a00fab2edd585e94efd3df3a1807a30941305af9fd6855cbb86070f2cc3

[TRUNCATED]

```md
# parent-page-migration Specification

## ADDED Requirements

### Requirement: 家庭概览页面迁移
Parent 端家庭概览页面 SHALL 使用 antd Card + Statistic + Table 组件重建。MUST 展示家庭统计信息、近期活动摘要、快捷操作入口。

#### Scenario: 家庭概览展示
- **WHEN** 家长进入家庭概览页面
- **THEN** 页面使用 Card 和 Statistic 展示家庭成员数、任务完成率、积分概况等统计

### Requirement: 家庭管理页面迁移
Parent 端家庭管理页面 SHALL 使用 antd ProTable + ProForm 重建。MUST 支持家庭成员查看和编辑。

#### Scenario: 成员列表
- **WHEN** 家长进入家庭管理页面
- **THEN** ProTable 展示家庭成员列表，支持编辑操作

### Requirement: 孩子档案页面迁移
Parent 端孩子档案页面 SHALL 使用 antd ProTable + ProForm 重建。MUST 支持孩子档案的增删改查。

#### Scenario: 孩子列表与编辑
- **WHEN** 家长进入孩子档案页面
- **THEN** ProTable 展示孩子列表，点击编辑弹出 ProForm 表单

### Requirement: 任务模板页面迁移
Parent 端任务模板页面 SHALL 使用 antd ProTable + ProForm 重建。MUST 支持模板的增删改查、分类管理。

#### Scenario: 模板列表
- **WHEN** 家长进入任务模板页面
- **THEN** ProTable 展示模板列表，支持分类筛选和搜索

#### Scenario: 创建模板
- **WHEN** 家长点击"创建模板"
- **THEN** 弹出 ProForm 表单，包含任务名称、分类、默认积分等字段

### Requirement: 任务分配页面迁移
Parent 端任务分配页面 SHALL 使用 antd ProTable + ProForm 重建。MUST 支持从模板批量分配任务给指定孩子。

#### Scenario: 任务分配
- **WHEN** 家长选择模板和目标孩子
- **THEN** 系统批量创建任务分配，ProTable 展示已分配列表

### Requirement: 任务审核页面迁移
Parent 端任务审核页面 SHALL 使用 antd Table + rowSelection 重建。MUST 支持批量审核（通过/拒绝）和单独审核。

#### Scenario: 审核列表
- **WHEN** 家长进入任务审核页面
- **THEN** Table 展示待审核任务列表，支持多选

#### Scenario: 批量通过
- **WHEN** 家长勾选多个任务并点击"批量通过"
- **THEN** 系统批量处理审核，Table 刷新

### Requirement: 积分页面迁移
Parent 端积分页面 SHALL 使用 antd ProTable 重建。MUST 支持积分流水查看和手动积分调整。

#### Scenario: 积分流水
- **WHEN** 家长进入积分页面
- **THEN** ProTable 展示积分流水，包含来源、数量、时间

### Requirement: 奖品页面迁移
Parent 端奖品页面 SHALL 使用 antd ProTable + ProForm 重建。MUST 支持奖品的增删改查。

#### Scenario: 奖品列表与新增
- **WHEN** 家长进入奖品页面
- **THEN** ProTable 展示奖品列表，点击新增弹出 ProForm

### Requirement: 盲盒页面迁移
Parent 端盲盒页面 SHALL 使用 antd ProTable + ProForm 重建。MUST 支持盲盒的增删改查。

#### Scenario: 盲盒管理
- **WHEN** 家长进入盲盒页面
- **THEN** ProTable 展示盲盒列表，支持编辑盲盒内容和概率

### Requirement: 兑换履约页面迁移
Parent 端兑换履约页面 SHALL 使用 antd ProTable + Modal 重建。MUST 支持兑换记录的查看和履约状态变更。

#### Scenario: 兑换列表与履约
- **WHEN** 家长进入兑换履约页面

```

Full source: openspec/changes/migrate-parent-to-antd-pro/specs/parent-page-migration/spec.md
