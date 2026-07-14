# Design: fix-parent-family-modal-bugs

## 方案

### Bug 1：Modal 输入框失焦
在 `Modal` 组件中使用 `useRef` 保存最新的 `onClose` 回调，`useEffect` 的依赖数组仅保留 `isOpen`，避免父组件渲染导致 `onClose` 引用变化时重复触发 focus 逻辑。

```tsx
const onCloseRef = useRef(onClose);
onCloseRef.current = onClose;

useEffect(() => {
  if (!isOpen) return;
  const previouslyFocused = document.activeElement as HTMLElement | null;
  dialogRef.current?.focus();
  const onKeyDown = (event: KeyboardEvent) => {
    if (event.key === 'Escape') onCloseRef.current();
  };
  document.addEventListener('keydown', onKeyDown);
  return () => {
    document.removeEventListener('keydown', onKeyDown);
    previouslyFocused?.focus();
  };
}, [isOpen]);
```

### Bug 2：孩子列表刷新
确认 `handleSaveChild` 在成功后同时刷新 `refetchChildren()` 与家庭概览 `refetch()`。若仍存在显示延迟，考虑在 `refetchChildren` 完成后再关闭弹窗（调整 await 顺序）。

## 边界
- 保持现有 `Modal` 的 Escape 关闭、背景点击关闭行为不变。
- 保持 `handleSaveChild` 的错误处理与 loading 状态。
