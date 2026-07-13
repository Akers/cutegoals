# Proposal: fix-parent-home-family-link

## 问题
家长登录后默认进入首页 `/parent`，该页面仅展示家庭成员列表，没有跳转到家庭管理页面 `/parent/family` 的入口。用户无法在首页找到「添加孩子、移除家庭成员」等管理功能。

## 根因
`ParentHomePage` 没有提供进入 `ParentFamilyPage` 的导航链接；家庭管理功能集中在 `/parent/family`，但用户从首页无法发现该页面。

## 修复目标
在 `/parent` 首页增加「管理家庭」入口，点击后跳转到 `/parent/family`。

## 范围
- 仅修改前端 `web/src/parent/pages/index.tsx` 中 `ParentHomePage` 的 actions。
- 不复用或移动 `ParentFamilyPage` 的逻辑，只增加导航入口。

## 非目标
- 不修改 `ParentFamilyPage`；
- 不新增 API 或后端逻辑；
- 不新增 capability 或 delta spec。
