# Design: fix-parent-child-list-refresh

## 方案
1. 在 `Family` 接口中增加 `children: ChildProfile[]` 字段（复用 `ChildProfile` 类型，补充 `birthYear` 等可选字段）。
2. 移除 `ParentFamilyPage` 中 `usePaginatedData<ChildProfile>('/family/children')`，直接从 `data.children` 读取孩子列表。
3. `handleSaveChild` 创建成功后仅 `await refetch()`；`handleRemoveChild` 删除成功后仅 `await refetch()`。
4. 孩子板块空状态文案保持不变。

## 边界
- `GET /api/family` 返回的孩子对象包含 `id`、`nickname`、`avatar`、`status`、`birthYear`、`ageGroup`，足以渲染列表与移除操作。
- 移除孩子仍调用 `DELETE /api/family/children/{id}`，不受数据源变更影响。
