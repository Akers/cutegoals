# Proposal: fix-family-member-lookup

## 问题
家长端进入「家庭」页面时，前端显示「加载失败」，后端返回 。

## 根因
上一个 hotfix 在  的 JWT filter 中通过  获取 familyId。当账号拥有多个角色（如 ）且第一个角色不是家庭角色时，查询返回空，导致  仍为 null， 查询  表  返回 0 条记录，抛出 。

## 修复目标
让 JWT filter 根据账号 ID 直接查找其家庭成员记录，不再依赖 token 中角色的顺序。

## 范围
- 修改 ：新增  方法。
- 修改 ：使用  设置 。

## 非目标
- 不改变权限模型或接口契约。
- 不新增 capability。
