# parent-page-migration Specification

## Purpose
TBD - created by archiving change migrate-parent-to-antd-pro. Update Purpose after archive.
## Requirements
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
- **THEN** ProTable 展示兑换记录，点击履约按钮更新状态

