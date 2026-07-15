# shared-component-migration Specification

## Purpose
定义 CuteGoals 自建 UI 组件到 antd/pro-components 的映射关系和兼容性需求。

## ADDED Requirements

### Requirement: Button 组件迁移
自建 `Button` 组件 SHALL 替换为 antd `Button`。所有变体（primary/secondary/danger/ghost）和尺寸（small/middle/large）MUST 通过 antd Button 的 `type` 和 `size` props 等价实现。loading 状态 MUST 通过 antd `loading` prop 实现，图标 MUST 通过 `icon` prop 实现。

#### Scenario: 主要按钮
- **WHEN** 组件使用 `<Button variant="primary">确认</Button>`
- **THEN** antd Button 渲染为 `type="primary"` 的主色按钮，视觉效果与交互符合 antd 规范

#### Scenario: 危险按钮
- **WHEN** 组件使用 `<Button variant="danger">删除</Button>`
- **THEN** antd Button 渲染为 `danger` 类型的红色按钮

#### Scenario: 加载状态
- **WHEN** 组件使用 `<Button loading>提交中</Button>`
- **THEN** antd Button 显示加载图标并禁用点击

### Requirement: Modal 组件迁移
自建 `Modal` 和 `ConfirmModal` 组件 SHALL 替换为 antd `Modal`。ESC 关闭、遮罩层点击关闭、焦点管理 MUST 由 antd Modal 原生支持。确认弹窗 MUST 使用 `Modal.confirm` 静态方法实现。

#### Scenario: 确认对话框
- **WHEN** 调用 `Modal.confirm({ title: '确认删除', content: '此操作不可撤销', onOk: handleDelete })`
- **THEN** antd 渲染确认对话框，点击"确定"执行 callback，"取消"关闭对话框

#### Scenario: ESC 关闭
- **WHEN** Modal 打开且用户按下 ESC 键
- **THEN** Modal MUST 关闭，且 `onCancel` callback 被触发

### Requirement: Toast/Message 通知迁移
自建 `ToastProvider`/`useToast` SHALL 替换为 antd `App.useApp().message` 和 `App.useApp().notification` API。四种通知类型（info/success/warning/error）MUST 映射到 antd 对应方法。通知 MUST 支持自动消失时间配置，且 Provider MUST 在应用根层级注入。

#### Scenario: 成功通知
- **WHEN** 操作成功后调用 `message.success('保存成功')`
- **THEN** 页面顶部显示绿色成功提示，N 秒后自动消失

#### Scenario: 错误通知
- **WHEN** 操作失败后调用 `message.error('保存失败')`
- **THEN** 页面顶部显示红色错误提示

### Requirement: Form 表单组件迁移
自建 `Input`/`TextArea`/`Select`/`Label`/`FormField` SHALL 替换为 antd `Input`/`Input.TextArea`/`Select`/`Form.Item`。表单校验反馈 MUST 通过 `Form.Item` 的 `rules` 和 `validateStatus` 实现。表单字段状态管理 MUST 继续使用自建 `useFormField` hook，与 antd `Form` 组件无冲突。

#### Scenario: 文本输入
- **WHEN** 页面渲染 `<Input placeholder="请输入名称" />`
- **THEN** antd Input 渲染为带边框的文本输入框，获得焦点时显示蓝色边框

#### Scenario: 下拉选择
- **WHEN** 页面渲染 `<Select options={options} />`
- **THEN** antd Select 渲染带下拉箭头的选择器，点击展开选项列表

#### Scenario: 表单校验错误
- **WHEN** antd `Form.Item` 的 rules 校验不通过
- **THEN** 表单项 MUST 显示红色边框和错误提示文字

### Requirement: Pagination 分页组件迁移
自建 `Pagination` 组件 SHALL 替换为 antd `Pagination`。页码切换、每页条数切换、总数显示 MUST 通过 antd Pagination props 等价实现。

#### Scenario: 分页导航
- **WHEN** 列表数据超过单页容量且用户点击下一页
- **THEN** antd Pagination MUST 触发 `onChange` 回调并传入新页码

### Requirement: 状态组件迁移
自建 `EmptyState`/`ErrorState`/`LoadingState`/`OfflineState` SHALL 替换为 antd 等价组件：`EmptyState` → `Empty`、`ErrorState` → `Result status="error"`、`LoadingState` → `Spin`、`OfflineState` → `Result status="warning"`。各状态组件 MUST 保持原有的说明文案和操作按钮能力。

#### Scenario: 空数据状态
- **WHEN** 数据列表返回空数组
- **THEN** antd `Empty` 组件渲染空数据图示和"暂无数据"文案

#### Scenario: 加载状态
- **WHEN** 数据正在请求中
- **THEN** antd `Spin` 组件渲染加载动画，不得阻塞页面导航

#### Scenario: 错误状态
- **WHEN** 数据请求失败
- **THEN** antd `Result status="error"` 渲染错误图标、错误说明和"重试"按钮

#### Scenario: 离线状态
- **WHEN** 浏览器检测到网络断开
- **THEN** antd `Result status="warning"` 渲染离线提示和"请检查网络连接"文案

### Requirement: 其余组件迁移
自建 `ErrorBoundary`/`GlobalErrorFallback`/`CardSection`/`StatusBadge` SHALL 替换为 antd 等价组件：`ErrorBoundary` → React Error Boundary + antd `Result status="500"`、`CardSection` → antd `Card`、`StatusBadge` → antd `Tag`。PageHeader SHALL 由 ProLayout 的面包屑自动替代。

#### Scenario: 全局错误边界
- **WHEN** 页面渲染过程中抛出未捕获异常
- **THEN** ErrorBoundary MUST 捕获异常并渲染 antd `Result status="500"` 错误页，提供"返回首页"入口

#### Scenario: 状态标签
- **WHEN** 显示任务状态"待审核"
- **THEN** antd `Tag` 渲染对应颜色和文案的状态标签
