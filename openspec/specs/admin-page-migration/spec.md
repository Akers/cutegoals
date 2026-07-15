# admin-page-migration Specification

## Purpose
TBD - created by archiving change migrate-admin-to-antd-pro. Update Purpose after archive.
## Requirements
### Requirement: 实例概览页面迁移
Admin 端实例概览页面 SHALL 使用 antd ProTable 重建，功能保持不变。MUST 包含实例列表表格（含搜索栏、分页、实例状态展示）和实例操作入口。

#### Scenario: 实例列表加载
- **WHEN** 管理员进入实例概览页面
- **THEN** ProTable 渲染实例列表，包含实例名称、状态、创建时间等列，支持搜索和分页

#### Scenario: 状态展示
- **WHEN** 实例有不同的运行状态（运行中/已停止/异常）
- **THEN** 状态列 MUST 使用 antd Tag 或 Badge 以不同颜色展示

### Requirement: 系统配置页面迁移
Admin 端系统配置页面 SHALL 使用 antd ProForm 重建，功能保持不变。MUST 支持配置项的增删改查，表单校验反馈 MUST 清晰明确。

#### Scenario: 配置列表与编辑
- **WHEN** 管理员进入系统配置页面
- **THEN** ProForm 展示当前配置项列表，支持内联编辑和保存

### Requirement: 账号管理页面迁移
Admin 端账号管理页面 SHALL 使用 antd ProTable 重建。MUST 支持账号列表搜索、新增/编辑/删除账号、角色分配。

#### Scenario: 账号列表
- **WHEN** 管理员进入账号管理页面
- **THEN** ProTable 渲染账号列表，支持搜索和分页

#### Scenario: 新增账号
- **WHEN** 管理员点击"新增账号"
- **THEN** 弹出 Modal 表单，使用 ProForm 收集账号信息

### Requirement: 审计日志页面迁移
Admin 端审计日志页面 SHALL 使用 antd ProTable 重建。MUST 支持日志列表（时间倒序）、操作类型筛选、操作详情查看。

#### Scenario: 日志列表与筛选
- **WHEN** 管理员进入审计日志页面
- **THEN** ProTable 渲染审计日志，支持按时间范围和操作类型筛选

### Requirement: 健康面板页面迁移
Admin 端健康面板 SHALL 使用 antd Card + Descriptions + Statistic 组件重建。MUST 展示系统运行状态、关键指标、服务可用性。

#### Scenario: 健康状态展示
- **WHEN** 管理员进入健康面板
- **THEN** 页面使用 Card 和 Statistic 组件展示各服务健康状态

