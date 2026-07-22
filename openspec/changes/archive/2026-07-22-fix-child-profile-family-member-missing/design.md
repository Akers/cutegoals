# 修复方案：孩子会话 childId 解析

## 方案概述

修改 3 个控制器（Points、Exchange、TaskReview）中的 `resolveChildIdFromSession` / childId 解析逻辑，使其在检测到孩子会话时直接验证 `childId`，而非通过 `family_member` 表 JOIN 查找。

## 详细方案

### 核心思路

`ATTR_CHILD_ID` 由 JWT filter 在检测到孩子会话时设置。当此 attribute 存在时，说明当前登录者是孩子本人，其 `childId` 即为 `ChildProfile.id`。此时应直接通过 `taskChildMapper.selectById(childId)` 验证孩子档案存在且状态为 ACTIVE，而非使用 `findByAccountId()` 进行跨表 JOIN。

### 修改位置

#### 1. PointsController.resolveChildIdFromSession()

**文件**: `server/points/src/main/java/com/cutegoals/points/controller/PointsController.java` (第170-175行)

```java
private Long resolveChildIdFromSession(HttpServletRequest httpRequest) {
    Long childId = (Long) httpRequest.getAttribute(AuthConstants.ATTR_CHILD_ID);
    if (childId != null) {
        // Child session: childId IS the profile ID, validate directly
        ChildProfile profile = taskChildMapper.selectById(childId);
        if (profile == null || !"ACTIVE".equals(profile.getStatus())) {
            throw new BusinessException(ErrorCode.POINTS_FORBIDDEN, "Child profile not found or inactive");
        }
        return childId;
    }
    // Parent session: resolve via family_member
    Long accountId = getAccountId(httpRequest);
    return taskChildMapper.findByAccountId(accountId)
            .map(ChildProfile::getId)
            .orElseThrow(() -> new BusinessException(ErrorCode.POINTS_FORBIDDEN, "No child profile found for session"));
}
```

#### 2. ExchangeController.resolveChildIdFromSession()

**文件**: `server/exchange/src/main/java/com/cutegoals/exchange/controller/ExchangeController.java` (第229-234行)

同上模式修改。

#### 3. TaskReviewController (两处)

**文件**: `server/task-review/src/main/java/com/cutegoals/taskreview/controller/TaskReviewController.java`

- 第177-183行（queryReviewHistory 中的孩子身份验证）
- 第210-213行（queryChildHistory 中的 session childId 解析）

两处均使用 `taskChildMapper.findByAccountId(accountId)` 解析 childId，需改为优先检查 `ATTR_CHILD_ID`。

## 不修改的内容

- `TaskChildMapper.findByAccountId` — 家长端仍需要使用
- `ChildProfileService.createChildProfile` — 不引入 account 创建（架构变更过大）
- JWT filter / TokenService / AuthController — 上次修复逻辑正确，无需改动
