# Comet Design Handoff

- Change: migrate-child-to-antd-pro
- Phase: design
- Mode: compact
- Context hash: fd6fd3aa53d4deb7f1ebb5556acfc0355c154f2e8a96e3df298d06fcf75c532a

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/migrate-child-to-antd-pro/proposal.md

- Source: openspec/changes/migrate-child-to-antd-pro/proposal.md
- Lines: 1-24
- SHA256: 900bb0d7c4e4de8351c52361fde80974d05ad6794be67b73907b939228e5e166

```md
## Why

CuteGoals 儿童端当前 5 个页面（今日任务/全部任务/积分商城/惊喜盲盒/兑换历史）均基于自建组件构建。在基础设施已迁移至 Ant Design Pro（Change 1）后，需将儿童端页面重建为 antd/pro-components 实现，以消除自建组件边界场景的 bug 和不一致问题。

## What Changes

- 将 child 端 5 个页面全部使用 antd/pro-components 重建 UI 层
- 业务逻辑、API 调用、状态管理保持不变
- 页面组件从自建 React 组件迁移至 ProTable/ProForm/Card/Descriptions 等 antd 企业级组件
- 保持 child 主题色（通过 ConfigProvider）和 ProLayout 导航

## Capabilities

### New Capabilities
- `child-page-migration`: Child 端今日任务、全部任务、积分商城、惊喜盲盒、兑换历史 5 个页面的 antd 重建

### Modified Capabilities
- `web-app`: Child 端页面实现方式从自建组件变为 antd，业务需求不变

## Impact

- **代码**：`web/src/child/pages/` 下 5 个页面组件全部重写 UI 层
- **依赖**：无新增依赖（依赖 Change 1 引入的 antd 体系）
- **测试**：页面级测试用例需按 antd 组件行为更新

```

## openspec/changes/migrate-child-to-antd-pro/design.md

- Source: openspec/changes/migrate-child-to-antd-pro/design.md
- Lines: 1-34
- SHA256: 8f208b8903e70b57ec2efdb672a9ab25b50f2453fd89c5bd87b9af90c2625367

```md
## Context

本变更为 Ant Design Pro 迁移拆分的第四阶段——Child 端页面重建。依赖 Change 1（`migrate-to-antd-pro-infra`）提供 UmiJS 构建、ProLayout 布局、ConfigProvider child 主题和共享组件映射。

## Goals / Non-Goals

**Goals:**
- 将 child 端 5 个页面 UI 层从自建组件替换为 antd/pro-components
- 保持全部业务逻辑不变（API 调用、状态管理、权限控制）
- 保持 child 角色视觉主题

**Non-Goals:**
- 不修改页面功能或业务流程
- 不新增或删除页面
- 不修改后端 API
- 不修改 ChildLayout（在 Change 1 中实现）

## Decisions

### D1: 使用 Card + List 重构任务列表页
今日任务、全部任务使用 antd `Card` + `List` + `Checkbox` 组合实现，替代自建列表组件。任务完成操作使用 `Button` + loading 状态。

### D2: 使用 Card + Tag 重构商城/盲盒页
积分商城、惊喜盲盒使用 antd `Card` 网格布局 + `Tag` 标签 + `Button` 操作，替代自建卡片组件。

### D3: 使用 Timeline + Table 重构兑换历史
兑换历史使用 antd `Timeline` + `Table` 组合实现时间线展示，替代自建列表。

## Risks / Trade-offs

- **[风险] 儿童端 UI 需要活泼友好风格** → 缓解：通过 ConfigProvider child 主题 token 确保鲜艳色彩，通过 `Card` 圆角和大尺寸适配儿童交互

## Open Questions
无。此变更为 Change 1 的延续，技术方案由 Change 1 的 design.md 定义。

```

## openspec/changes/migrate-child-to-antd-pro/tasks.md

- Source: openspec/changes/migrate-child-to-antd-pro/tasks.md
- Lines: 1-30
- SHA256: 8fb5e98fb399a4370d34482e3b33c4030ad3899025de52eec8830fd8e33aeef9

```md
## 1. 今日任务页面

- [ ] 1.1 使用 Card + List + Checkbox + Button 重建今日任务列表
- [ ] 1.2 实现任务完成提交逻辑，含 loading 和成功反馈
- [ ] 1.3 验证：任务列表加载、完成提交、状态更新功能正常

## 2. 全部任务页面

- [ ] 2.1 使用 Card + List + Tabs 重建全部任务页面，支持状态筛选
- [ ] 2.2 保留原有 API 调用和状态管理
- [ ] 2.3 验证：任务列表加载、按状态筛选功能正常

## 3. 积分商城页面

- [ ] 3.1 使用 Card 网格 + Tag + Button 重建奖品列表
- [ ] 3.2 使用 Modal 实现兑换确认对话框
- [ ] 3.3 保留积分检查和兑换 API 逻辑
- [ ] 3.4 验证：奖品浏览、兑换确认、积分更新功能正常

## 4. 惊喜盲盒页面

- [ ] 4.1 使用 Card + Modal 重建盲盒展示和抽取
- [ ] 4.2 保留盲盒抽取 API 调用和结果展示
- [ ] 4.3 验证：盲盒抽取、结果展示、积分扣除功能正常

## 5. 兑换历史页面

- [ ] 5.1 使用 Timeline + Table 重建兑换历史
- [ ] 5.2 保留分页和数据请求逻辑
- [ ] 5.3 验证：兑换历史按时间展示、状态标签正确

```

## openspec/changes/migrate-child-to-antd-pro/specs/child-page-migration/spec.md

- Source: openspec/changes/migrate-child-to-antd-pro/specs/child-page-migration/spec.md
- Lines: 1-46
- SHA256: 2e342cb456a63205e94ccae843fae05e1613de5169c7e73353c24b5ae9d4fbc9

```md
# child-page-migration Specification

## ADDED Requirements

### Requirement: 今日任务页面迁移
Child 端今日任务页面 SHALL 使用 antd Card + List + Checkbox 组合重建。MUST 展示今日待完成任务列表，支持任务完成提交。

#### Scenario: 今日任务展示
- **WHEN** 孩子进入今日任务页面
- **THEN** Card 卡片列表展示今日任务，每项包含任务名称、积分奖励和完成按钮

#### Scenario: 任务完成提交
- **WHEN** 孩子点击任务完成按钮
- **THEN** Button 显示 loading 状态，提交成功后任务状态更新并显示积分配变动效（或即时反馈）

### Requirement: 全部任务页面迁移
Child 端全部任务页面 SHALL 使用 antd Card + List 重建。MUST 支持按任务状态（待完成/已提交/已通过）筛选。

#### Scenario: 任务列表与筛选
- **WHEN** 孩子进入全部任务页面
- **THEN** 页面展示全部任务列表，支持按状态 Tab 筛选

### Requirement: 积分商城页面迁移
Child 端积分商城页面 SHALL 使用 antd Card 网格布局 + Tag 重建。MUST 支持奖品浏览和兑换操作。

#### Scenario: 奖品浏览
- **WHEN** 孩子进入积分商城
- **THEN** Card 网格展示可用奖品，显示奖品名称、所需积分、库存 Tag

#### Scenario: 奖品兑换
- **WHEN** 孩子点击奖品兑换按钮
- **THEN** 先展示确认 Modal，确认后提交兑换请求，成功后更新积分余额

### Requirement: 惊喜盲盒页面迁移
Child 端盲盒页面 SHALL 使用 antd Card + Modal 重建。MUST 支持盲盒展示和抽取操作，盲盒动效需保留惊喜感。

#### Scenario: 盲盒抽取
- **WHEN** 孩子点击盲盒抽取按钮
- **THEN** Modal 展示抽取过程和结果，积分扣除实时更新

### Requirement: 兑换历史页面迁移
Child 端兑换历史页面 SHALL 使用 antd Timeline + Table 重建。MUST 按时间倒序展示兑换记录和状态。

#### Scenario: 兑换历史展示
- **WHEN** 孩子进入兑换历史页面
- **THEN** Timeline 或 Table 按时间展示兑换记录，包含奖品名称、积分消耗、状态

```
