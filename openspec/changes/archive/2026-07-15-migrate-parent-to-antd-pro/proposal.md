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
