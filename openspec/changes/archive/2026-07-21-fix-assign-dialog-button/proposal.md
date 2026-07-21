# Proposal: 分配任务弹窗——修复按钮和内联按钮多余问题

## 问题描述

分配任务弹窗（单分配和批量分配）中存在以下问题：

1. **多余按钮**：Modal 内容区有一个独立的 `<Button>分配</Button>`，但 Ant Design Modal 默认也会渲染底部的确认/取消按钮。应移除内联按钮，使用 Modal 的 `onOk` 作为提交入口，按钮文本为"分配"
2. **Internal Error**：当前点击分配按钮（无论是内联按钮还是 Modal 默认确定按钮）无法正常保存，显示 Internal error

## 根因分析

1. 两个 `<Modal>` 组件均未设置 `onOk` 和 `okText`，导致 Modal 默认确定按钮无绑定操作
2. 内容区多余的内联 `<Button>` 虽然绑定了 `handleSingleAssign`/`handleAssign`，但 Modal 本身没有 footer 控制造成冲突
3. Internal Error 可能是由于请求数据格式与后端期望不匹配或后端处理异常

## 修复目标

1. 移除两条弹窗内联的 `<Button>分配</Button>`
2. 为两个 Modal 添加 `onOk={handleSingleAssign}` / `onOk={handleAssign}` 和 `okText="分配"`
3. 排查并修复 Internal Error 根因
