# Design: fix-parent-family-member-crud

## 方案
在 `ParentFamilyPage` 中补充孩子管理：
1. 在「家庭成员」之后新增「孩子」板块，列出当前家庭的孩子（调用 `GET /api/family/children`）。
2. 每个孩子在右侧展示「移除」按钮，点击后弹出 `ConfirmModal` 确认，确认后调用 `DELETE /api/family/children/{id}` 并刷新数据。
3. 在页面顶部操作区添加「添加孩子」按钮，点击后打开 `Modal` 表单，输入昵称、PIN、生日，调用 `POST /api/family/children` 创建孩子并刷新。
4. 保持现有的「邀请家长」按钮和待处理邀请列表不变。

## 边界
- 添加孩子时复用与 `ParentChildrenPage` 相同的字段和校验（昵称必填）。
- 移除孩子前需二次确认，避免误删。
- 操作失败时展示后端返回的错误信息。
