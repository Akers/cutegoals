# Proposal: fix-parent-family-members-management

## 问题
家长端「家庭」页面已能加载家庭信息，但家庭成员卡片只有昵称和角色展示，缺少管理操作。家长无法移除其他家长或退出家庭。

## 根因
前端 `ParentFamilyPage` 只渲染了成员列表，没有调用后端已有的管理接口。
后端已提供：
- DELETE /api/family/members/{id} — 移除家长成员
- POST /api/family/members/me/leave — 当前家长退出家庭
- PUT /api/family — 修改家庭名称等基础信息

## 修复目标
在家庭页面为成员列表添加管理操作按钮：移除成员、退出家庭。

## 范围
- 仅修改前端 `web/src/parent/pages/index.tsx` 中 `ParentFamilyPage` 的渲染与交互。
- 复用现有后端接口，不新增 API。

## 非目标
- 不修改后端逻辑。
- 不新增 capability 或 delta spec。
