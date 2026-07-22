# 修复孩子登录后首页报错 "No child profile found for session"

## 问题描述

上次修复（孩子登录 JWT 会话持久化）后，使用 cici 孩子登录，首页显示错误：`加载失败 No child profile found for session`。

## 复现步骤

1. 孩子端选择 cici 档案，输入 PIN 登录
2. 登录成功后进入 `/child` 首页
3. 首页加载任务和数据时报错：`No child profile found for session`

## 根因分析

### 上游原因

上次修复使 JWT filter 将 `childId` 设置为 `ATTR_ACCOUNT_ID`：
```java
request.setAttribute(AuthConstants.ATTR_ACCOUNT_ID, claims.childId());
```

### 下游影响

PointsController、ExchangeController、TaskReviewController 均使用 `resolveChildIdFromSession()` 方法，其内部调用 `taskChildMapper.findByAccountId(accountId)`：

```sql
SELECT cp.* FROM child_profile cp 
JOIN family_member fm ON fm.family_id = cp.family_id 
WHERE fm.account_id = #{accountId} AND fm.role = 'CHILD' ...
```

此 SQL 要求存在一条 `family_member` 记录，其 `account_id` 等于传入值且 `role='CHILD'`。但 `ChildProfileService.createChildProfile()` 创建孩子档案时**没有创建 `family_member` 记录**（孩子没有独立的 `account` 记录），因此 JOIN 查询返回空结果。

### 触发链

```
孩子 JWT 登录 → ATTR_ACCOUNT_ID = childId (ChildProfile.id)
→ PointsController.resolveChildIdFromSession()
→ taskChildMapper.findByAccountId(childId)
→ SQL JOIN family_member WHERE fm.account_id = childId
→ 无匹配记录（family_member 表中无该 childId 的 CHILD 角色记录）
→ "No child profile found for session"
```

## 修复目标

修改 `resolveChildIdFromSession` 方法，使其支持孩子会话场景：当 `ATTR_CHILD_ID` 存在时直接通过 `childId` 查找并验证孩子档案，而非通过 `family_member` 表 JOIN。
