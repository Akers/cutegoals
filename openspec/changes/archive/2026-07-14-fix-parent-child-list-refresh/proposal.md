# Proposal: fix-parent-child-list-refresh

## 问题
家庭管理页面（`/parent/family`）添加孩子成功后，「孩子」板块仍显示「暂无孩子」。即使后端已成功创建孩子档案，前端列表不刷新。

## 根因
`ParentFamilyPage` 为孩子列表单独调用了 `usePaginatedData('/family/children')`，与家庭概览 `useApi('/family')` 形成两个独立数据源。后端 `GET /api/family` 实际已返回 `children` 数组，但前端未使用它。两个独立 hook 的状态在创建/删除后未能可靠同步，导致列表不更新。

## 修复目标
改为单一数据源：在 `Family` 接口中加入 `children`，「孩子」板块直接使用 `GET /api/family` 返回的孩子数据。添加/移除孩子后只需 `refetch()` 家庭概览即可同步刷新。

## 范围
- 仅修改前端 `web/src/parent/pages/index.tsx`：
  - `Family` 接口增加 `children` 字段；
  - `ParentFamilyPage` 移除独立的 `usePaginatedData('/family/children')`，改用 `data.children`；
  - `handleSaveChild` / `handleRemoveChild` 仅调用 `refetch()`。

## 非目标
- 不修改后端；
- 不新增 API 或 delta spec。
