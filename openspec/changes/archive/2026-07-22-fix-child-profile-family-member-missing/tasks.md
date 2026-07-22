# 修复任务清单

## 后端修改

### 1. PointsController — resolveChildIdFromSession

- [x] **Java**: 优先检查 `ATTR_CHILD_ID`，存在时直接用 `selectById` 验证孩子档案

**文件**: `server/points/src/main/java/com/cutegoals/points/controller/PointsController.java`

### 2. ExchangeController — resolveChildIdFromSession

- [x] **Java**: 同 PointsController 模式修改

**文件**: `server/exchange/src/main/java/com/cutegoals/exchange/controller/ExchangeController.java`

### 3. TaskReviewController — childId 解析（两处）

- [x] **Java**: `queryReviewHistory`（第177-183行）优先检查 `ATTR_CHILD_ID`
- [x] **Java**: `queryChildHistory`（第210-213行）优先检查 `ATTR_CHILD_ID`

**文件**: `server/task-review/src/main/java/com/cutegoals/taskreview/controller/TaskReviewController.java`

## 验证（见 verify 阶段）

- 孩子登录后首页正常加载，不再报 "No child profile found for session"
- 孩子的任务、积分、兑换记录等数据正常展示
- 家长端功能不受影响
