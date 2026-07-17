# Tasks: fix-parent-save-dialog-button

- [x] 1. 新增回归测试 `web/src/__tests__/parent-save-dialogs.test.tsx`（底部"保存"按钮触发提交 / 失败保持打开且框内显错 / 成功关闭并提示），运行确认按预期失败（RED 证据）
- [x] 2. `ParentFamilyPage` 添加孩子对话框：Modal 绑定 `okText="保存"` + `onOk` + `confirmLoading`，删除 body 内保存按钮，错误改用对话框内 Alert（`childSaveError`），成功 `message.success('保存成功')`
- [x] 3. `ParentChildrenPage` 档案对话框：同上改造，并为 `handleSave` 补充 `res.error` 检查
- [x] 4. `ParentTemplatesPage` 模板对话框：同上改造，移除 `Modal.error` 弹窗改为框内 Alert
- [x] 5. `ParentPrizesPage` 奖品对话框：同上改造，并为 `handleSave` 补充 `res.error` 检查
- [x] 6. 运行回归测试转绿、`npm test` 全量通过、`npm run lint`（tsc）通过
