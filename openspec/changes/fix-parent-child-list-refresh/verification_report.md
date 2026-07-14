# Verification Report: fix-parent-child-list-refresh

## Summary

| Dimension    | Status                                       |
|--------------|----------------------------------------------|
| Completeness | 7/7 tasks done                               |
| Correctness  | 1/1 bug fixed (root cause addressed)         |
| Coherence    | Single data source, simpler than before      |

## 1. 完整性（Completeness）

- `tasks.md` 7 项任务全部勾选完成。
- 实现覆盖：Family 接口扩展、移除独立孩子分页请求、改用 data.children、简化刷新逻辑。

## 2. 正确性（Correctness）

### Bug：添加孩子后列表不刷新

- **根因**：`ParentFamilyPage` 使用两个独立数据源（`useApi('/family')` 和 `usePaginatedData('/family/children')`），添加/删除后两者的状态同步不可靠，导致孩子列表不更新。
- **修复位置**：`web/src/parent/pages/index.tsx`
- **修复内容**：
  - `Family` 接口新增 `children: ChildProfile[]`；
  - 移除 `usePaginatedData<ChildProfile>('/family/children')`；
  - 「孩子」板块直接使用 `data.children`（来自 `GET /api/family` 返回）；
  - `handleSaveChild` 和 `handleRemoveChild` 仅调用 `refetch()`，刷新家庭概览即可同步孩子列表。
- **验证结果**：`npx tsc -b` 0 errors；逻辑确认单一数据源消除了状态同步问题。

## 3. 一致性（Coherence）

- 复用 `GET /api/family` 已返回的孩子数据，无需新增后端接口。
- 后端 `getFamily` 返回的 children 对象包含 `id`、`nickname`、`avatar`、`status`、`birthYear`、`ageGroup`，足以渲染列表。
- 移除孩子的 `DELETE /api/family/children/{id}` 调用不变。

## 4. 发现的问题

### WARNING
- 前端 `npm test` 仍有 **14 个预存失败 / 65 通过**，与 baseline 一致，无新增失败。

## 5. 测试证据

- **前端类型检查**：`npx tsc -b` 通过，0 errors。
- **前端单元测试**：`npm test -- --run`：14 failed / 65 passed（与 baseline 一致，0 新增失败）。

## 6. 最终评估

根因已消除：孩子列表改为单一数据源（家庭概览），添加/移除后 `refetch()` 即可同步刷新，不再依赖独立的分页请求状态同步。

**Ready for archive.**
