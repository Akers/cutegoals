# Proposal: fix-parent-family-member-crud

## 问题
家长端「家庭」管理页面（`/parent/family`）已能展示家长成员，并支持「邀请家长」和「移除家长/退出家庭」，但缺少对孩子这一家庭成员的管理功能：
- 无法在家庭管理页面直接添加孩子；
- 无法在家庭管理页面移除孩子（删除孩子档案）。

## 根因
`ParentFamilyPage` 只渲染了 `Family.members`（家长列表），孩子数据（`/api/family/children`）和添加/删除孩子功能被放在独立的「孩子档案」页面（`/parent/children`），导致家庭管理功能分散。

## 修复目标
在家庭管理页面补充孩子管理功能：
- 添加「添加孩子」按钮，打开表单创建孩子档案；
- 渲染孩子列表，并为每个孩子提供「移除」按钮（删除孩子档案）。

## 范围
- 仅修改前端 `web/src/parent/pages/index.tsx` 中 `ParentFamilyPage` 的渲染与交互。
- 复用现有后端接口：
  - `POST /api/family/children` — 创建孩子档案
  - `DELETE /api/family/children/{id}` — 删除孩子档案
  - 已存在的 `POST /api/family/invitations` 和 `DELETE /api/family/members/{id}` 保持不变。

## 非目标
- 不修改后端逻辑；
- 不新增 capability 或 delta spec；
- 不改变现有孩子档案页面（`/parent/children`）。
