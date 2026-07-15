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
