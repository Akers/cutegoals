# Proposal: fix-parent-save-dialog-button

## 问题描述

家长端（parent）所有"保存修改"对话框的保存功能绑定在对话框 body 内部的"保存"按钮上，而 antd `Modal` 默认底部"确定"按钮未绑定任何处理逻辑——用户点击底部"确定"不会触发任何保存动作，必须点击对话框正文里的"保存"按钮才能提交，交互不符合常规对话框习惯。

涉及 4 个对话框（均在 `web/src/parent/pages/index.tsx`）：

1. **添加孩子**（`ParentFamilyPage`，约 L454-472）：body 内 `<Button>保存</Button>` → `handleSaveChild`；错误通过页面级 `actionError` Alert 展示在对话框**外**。
2. **编辑/新增档案**（`ParentChildrenPage`，约 L585-609）：body 内 `<Button>保存</Button>` → `handleSave`；`handleSave` **未检查 `res.error`**，保存失败也会关闭对话框且无任何提示。
3. **编辑/新建模板**（`ParentTemplatesPage`，约 L762-797）：body 内 `<Button>保存</Button>` → `handleSave`；失败时弹出新的 `Modal.error` 弹窗，而不是在当前对话框内展示错误。
4. **编辑/新增奖品**（`ParentPrizesPage`，约 L1333-1359）：body 内 `<Button>保存</Button>` → `handleSave`；同样**未检查 `res.error`**，失败静默关闭。

## 根因分析

- 4 个 `Modal` 均未设置 `onOk` / `okText` / `confirmLoading`，antd 默认渲染的底部"确定"按钮点击后无任何效果（受控 `open` 属性下仅触发空 handler）。
- 保存入口被放在对话框正文的自定义按钮上，属于错误的交互模式。
- `ParentChildrenPage.handleSave` 与 `ParentPrizesPage.handleSave` 缺少 `res.error` 分支（API client 约定返回 `{ data } | { error }`，不抛异常），导致失败被静默吞掉。
- 错误反馈位置不统一：页面级 Alert（对话框外）、`Modal.error` 新弹窗，均不符合"在对话框内显示错误"的要求。

## 修复目标

对上述 4 个保存对话框统一行为：

1. 对话框底部"确认"按钮改为"保存"按钮（`okText="保存"`），点击触发表单提交保存。
2. 删除对话框 body 内的"保存"按钮。
3. 保存成功：关闭对话框并 `message.success('保存成功')`。
4. 保存失败：不关闭对话框，在对话框内以 Alert 显示错误信息。

不在范围：非"保存"语义的对话框（邀请家长、批量分配、驳回原因、确认兑现、孩子端提交/兑换/盲盒等确认类对话框）不改动。
