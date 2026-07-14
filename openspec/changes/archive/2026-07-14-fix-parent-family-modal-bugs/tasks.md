# Tasks: fix-parent-family-modal-bugs

- [x] 1.1 修改 `Modal` 组件：用 `useRef` 保存 `onClose`，`useEffect` 仅依赖 `isOpen`，避免重复 focus。
- [x] 1.2 确认/修正 `handleSaveChild` 添加成功后先 `await refetchChildren()` 再关闭弹窗。
- [x] 1.3 验证 `npx tsc -b` 0 errors。
- [x] 1.4 验证 `npm test` 与 baseline 一致（14 failed / 65 passed），0 新增失败。
- [x] 1.5 git commit：`fix(parent): fix modal focus loss and child list refresh`。
- [x] 1.6 运行 build guard 推进到 verify。
