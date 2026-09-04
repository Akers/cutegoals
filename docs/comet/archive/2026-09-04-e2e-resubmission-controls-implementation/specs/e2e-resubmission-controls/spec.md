# e2e-resubmission-controls 功能需求

## ADDED Requirements

### Requirement: 家长端模板重复提交配置用例

家长创建带重复提交配置的模板流程应有真实浏览器级 e2e 覆盖。

#### Scenario: 家长创建模板并配置重复提交控制

- **WHEN** 家长以有效凭据登录 console，进入模板管理页并创建/编辑模板
- **THEN** 可将「允许重复提交」打开，并设置「最大提交次数」（如 3），保存成功后模板配置生效（列表或详情可见 allow_resubmit=true、max_submissions=3）

#### Scenario: 模板表单字段联动

- **WHEN** 家长打开模板编辑表单
- **THEN** 「允许重复提交」开关默认未勾选，且不显示「最大提交次数」「积分上限」输入框；勾选后两个字段出现并可输入

### Requirement: 孩子端重复提交上限用例

孩子重复提交达到上限被拒的流程应有完整栈 e2e 覆盖（BASE_URL 门控）。

#### Scenario: 重复提交达上限被拒绝

- **WHEN** 孩子端任务（allow_resubmit=true、max_submissions=3）已提交并审核通过 3 次
- **THEN** 孩子任务列表该任务 `canSubmit=false`（原因 MAX_REACHED，UI 显示达上限提示），再次提交请求被后端拒绝（422 TASK_SUBMISSION_MAX_REACHED）

#### Scenario: 积分上限达到后被拒绝

- **WHEN** 孩子任务（配置了积分上限）已获得的积分达到上限
- **THEN** 孩子任务列表该任务 `canSubmit=false`（原因 POINTS_CAP_REACHED，UI 显示达上限提示），再次提交请求被后端拒绝（422 TASK_SUBMISSION_POINTS_CAP_REACHED）

### Requirement: 套件整体通过

- **WHEN** 在未设置 BASE_URL 的本地环境运行完整 e2e 套件（console dev + 后端）
- **THEN** 家长端新增 2 条用例真实执行并通过，孩子端 2 条与既有 `/child` 用例自动 skip，套件 0 失败
