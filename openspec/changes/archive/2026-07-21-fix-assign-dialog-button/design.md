# Design: 分配任务弹窗——修复按钮

## 改动范围

只改 `web/src/parent/pages/index.tsx`。

## 具体设计

### 批量分配弹窗（`showAssign` Modal）

- 移除内联 `<Button onClick={handleAssign}>分配</Button>`
- 添加 `onOk={handleAssign}`、`okText="分配"`、`confirmLoading={assigning}` 到 Modal

### 单分配弹窗（`showSingleAssign` Modal）

- 移除内联 `<Button onClick={handleSingleAssign}>分配</Button>`
- 添加 `onOk={handleSingleAssign}`、`okText="分配"`、`confirmLoading={singleAssigning}` 到 Modal

### Internal Error排查

调用 `handleSingleAssign` 和 `handleAssign` 时，`getClient().post()` 失败时会在前端显示错误消息。Internal Error 可能是：
- 重复任务分配不带 deadline 时后端正确处理，但某些情况数据不符预期
- 排查后如果确认是后端处理问题则修改后端
