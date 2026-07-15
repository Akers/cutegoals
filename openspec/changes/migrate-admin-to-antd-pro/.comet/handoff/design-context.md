# Comet Design Handoff

- Change: migrate-admin-to-antd-pro
- Phase: design
- Mode: compact
- Context hash: 285168afb964242c099f3fb2e900dd51b67eabc047a090f843eef4ee8392da94

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/migrate-admin-to-antd-pro/proposal.md

- Source: openspec/changes/migrate-admin-to-antd-pro/proposal.md
- Lines: 1-24
- SHA256: 533d3439196e5de5f186cbcf0763fa504fdbb9699e428e35deaa2a1b08ef9776

```md
## Why

CuteGoals 管理端当前 5 个页面（实例概览/系统配置/账号管理/审计日志/健康面板）均基于自建组件构建。在基础设施已迁移至 Ant Design Pro（Change 1）后，需将管理端页面重建为 antd/pro-components 实现，以消除自建组件边界场景的 bug 和不一致问题。

## What Changes

- 将 admin 端 5 个页面全部使用 antd/pro-components 重建 UI 层
- 业务逻辑、API 调用、状态管理保持不变
- 页面组件从自建 React 组件迁移至 ProTable/ProForm/Card/Descriptions 等 antd 企业级组件
- 保持 admin 主题色（通过 ConfigProvider）和 ProLayout 导航

## Capabilities

### New Capabilities
- `admin-page-migration`: Admin 端实例概览、系统配置、账号管理、审计日志、健康面板 5 个页面的 antd 重建

### Modified Capabilities
- `web-app`: Admin 端页面实现方式从自建组件变为 antd，业务需求不变

## Impact

- **代码**：`web/src/admin/pages/` 下 5 个页面组件全部重写 UI 层
- **依赖**：无新增依赖（依赖 Change 1 引入的 antd 体系）
- **测试**：页面级测试用例需按 antd 组件行为更新

```

## openspec/changes/migrate-admin-to-antd-pro/design.md

- Source: openspec/changes/migrate-admin-to-antd-pro/design.md
- Lines: 1-34
- SHA256: 67f65d0a6565d6f29176dab45203047f431225f8d152a4d9c29100cc00063eae

```md
## Context

本变更为 Ant Design Pro 迁移拆分的第二阶段——Admin 端页面重建。依赖 Change 1（`migrate-to-antd-pro-infra`）提供 UmiJS 构建、ProLayout 布局、ConfigProvider admin 主题和共享组件映射。

## Goals / Non-Goals

**Goals:**
- 将 admin 端 5 个页面 UI 层从自建组件替换为 antd/pro-components
- 保持全部业务逻辑不变（API 调用、状态管理、权限控制）
- 保持 admin 角色视觉主题

**Non-Goals:**
- 不修改页面功能或业务流程
- 不新增或删除页面
- 不修改后端 API
- 不修改 AdminLayout（在 Change 1 中实现）

## Decisions

### D1: 使用 ProTable 重构列表/表格页面
实例概览、账号管理、审计日志使用 ProTable 实现，利用内置搜索栏、分页、列排序、行操作能力。数据请求使用自建 `useApi` hook，通过 ProTable `request` 属性对接。

### D2: 使用 ProForm 重构配置类页面
系统配置、实例初始化使用 ProForm 实现，利用内置布局（`layout="vertical"`）、校验反馈和提交 loading。

### D3: 使用 Descriptions/Card/Statistic 重构仪表板
健康面板使用 antd `Descriptions`、`Card`、`Statistic` 组件实现状态展示，替代自建 CardSection 和自建布局。

## Risks / Trade-offs

- **[风险] ProTable 列定义语法与自建表格差异大** → 缓解：逐列映射，保持相同的列顺序和字段名

## Open Questions
无。此变更为 Change 1 的延续，技术方案由 Change 1 的 design.md 定义。

```

## openspec/changes/migrate-admin-to-antd-pro/tasks.md

- Source: openspec/changes/migrate-admin-to-antd-pro/tasks.md
- Lines: 1-30
- SHA256: 0dc48a20a1d7a8a1e26a81f58c275d2ea4f98a18de4ccdd610df546af308e3bd

```md
## 1. 实例概览页面

- [ ] 1.1 使用 ProTable 重建实例列表，包含搜索栏、分页、状态列（Tag 展示）
- [ ] 1.2 保留原有 API 调用和状态管理逻辑，通过 ProTable `request` 对接
- [ ] 1.3 验证：实例列表加载、搜索、分页功能正常

## 2. 系统配置页面

- [ ] 2.1 使用 ProForm 重建配置列表，支持内联编辑
- [ ] 2.2 保留配置项增删改查业务逻辑
- [ ] 2.3 验证：配置新增、编辑、删除功能正常，表单校验反馈正确

## 3. 账号管理页面

- [ ] 3.1 使用 ProTable 重建账号列表，含搜索、分页
- [ ] 3.2 使用 ProForm + Modal 实现新增/编辑账号表单
- [ ] 3.3 保留角色分配、删除确认逻辑
- [ ] 3.4 验证：账号增删改查、角色分配功能正常

## 4. 审计日志页面

- [ ] 4.1 使用 ProTable 重建日志列表，支持时间范围和操作类型筛选
- [ ] 4.2 实现日志详情查看（Modal 或 Drawer）
- [ ] 4.3 验证：日志列表加载、筛选、详情查看功能正常

## 5. 健康面板页面

- [ ] 5.1 使用 Card + Descriptions + Statistic 重建健康面板
- [ ] 5.2 保留服务状态检查 API 调用逻辑
- [ ] 5.3 验证：各服务健康状态正确展示，指标数值正确

```

## openspec/changes/migrate-admin-to-antd-pro/specs/admin-page-migration/spec.md

- Source: openspec/changes/migrate-admin-to-antd-pro/specs/admin-page-migration/spec.md
- Lines: 1-46
- SHA256: 8c4134e317bb50373c43c95d88904ca67556b2024a06e6435bbba593da3cac38

```md
# admin-page-migration Specification

## ADDED Requirements

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

```
