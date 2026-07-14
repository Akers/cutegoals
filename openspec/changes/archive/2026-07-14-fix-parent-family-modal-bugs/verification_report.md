# Verification Report: fix-parent-family-modal-bugs

## Summary

| Dimension    | Status                                       |
|--------------|----------------------------------------------|
| Completeness | 6/6 tasks done                               |
| Correctness  | 2/2 bugs fixed                               |
| Coherence    | Follows design decisions                       |

## 1. 完整性（Completeness）

- `tasks.md` 6 项任务全部勾选完成。
- 实现覆盖：Modal 焦点修复、添加孩子刷新顺序调整。

## 2. 正确性（Correctness）

### Bug 1：添加孩子弹窗输入框失焦

- **根因**：`Modal` 组件的 `useEffect` 依赖 `[isOpen, onClose]`，父组件每次渲染都会创建新的 `onClose` 内联函数引用，导致 `useEffect` 在每次输入后重复执行 `dialogRef.current?.focus()`，抢走输入框焦点。
- **修复位置**：`web/src/shared/components/Modal.tsx`
- **修复内容**：使用 `useRef` 保存最新的 `onClose`，`useEffect` 依赖数组改为仅 `[isOpen]`，Escape 按键通过 `onCloseRef.current()` 调用。
- **验证结果**：`npx tsc -b` 0 errors；逻辑分析确认输入不再触发重复 focus。

### Bug 2：添加孩子后列表未刷新

- **修复位置**：`web/src/parent/pages/index.tsx` 中 `handleSaveChild`
- **修复内容**：将 `await refetchChildren()` 与 `await refetch()` 移到 `setShowChildModal(false)` 之前，确保数据刷新完成后再关闭弹窗，避免关闭动画或状态竞争导致刷新被中断。
- **验证结果**：类型检查通过；刷新逻辑顺序正确。

## 3. 一致性（Coherence）

- `Modal` 修改保持原有 Escape 关闭、背景点击关闭、焦点恢复行为。
- `handleSaveChild` 错误处理与 loading 状态保持不变。

## 4. 发现的问题

### WARNING
- 前端 `npm test` 仍有 **14 个预存失败 / 65 通过**，与 baseline 一致，无新增失败。

## 5. 测试证据

- **前端类型检查**：`npx tsc -b` 通过，0 errors。
- **前端单元测试**：`npm test -- --run`：14 failed / 65 passed（与 baseline 一致，0 新增失败）。

## 6. 最终评估

两个 bug 的根因均已消除。Modal 输入框失焦问题通过稳定 `useEffect` 依赖解决；孩子列表刷新通过调整异步操作顺序确保数据及时更新。

**Ready for archive.**
