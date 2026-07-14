# Proposal: fix-parent-family-modal-bugs

## 问题
家长端家庭管理页面（`/parent/family`）存在两个 bug：
1. 「添加孩子」弹窗的输入框输入一个字符后立即失去焦点，无法连续输入。
2. 成功添加孩子后，家庭页面「孩子」一栏未显示最新添加的孩子。

## 根因
1. **输入框失焦**：`Modal` 组件的 `useEffect` 依赖 `[isOpen, onClose]`，而 `onClose` 在父组件每次渲染时都是新创建的内联箭头函数引用。用户每输入一个字符触发 `setValue` → 父组件重渲染 → `onClose` 新引用 → `useEffect` 重跑 → `dialogRef.current?.focus()` 把焦点抢回 dialog 容器，导致输入框失焦。
2. **孩子列表未刷新**：`useApi` 的 `refetch` 通过 `setData` 更新状态，但 `handleSaveChild` 调用 `refetchChildren()` 时机和状态传播需确认；同时 `Family.children`（来自 `/family`）与独立分页的 `/family/children` 可能存在数据源不一致。经核查，`ParentFamilyPage` 的「孩子」板块读取的是 `usePaginatedData('/family/children')` 的 `children`，添加成功后已 `await refetchChildren()`，但若后端创建事务与查询存在短暂延迟或前端缓存，需确保强制刷新。

## 修复目标
1. 修复 `Modal` 组件，使 `useEffect` 不再因 `onClose` 引用变化而重复执行，输入框保持焦点。
2. 确保「添加孩子」成功后家庭页面孩子列表立即反映最新数据。

## 范围
- 修改前端 `web/src/shared/components/Modal.tsx`：用 ref 存储最新 `onClose`，`useEffect` 仅依赖 `isOpen`。
- 检查/修正 `web/src/parent/pages/index.tsx` 中 `handleSaveChild` 的刷新逻辑。

## 非目标
- 不修改后端逻辑；
- 不新增 API 或 delta spec。
