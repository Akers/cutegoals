# 修复方案

TaskChildMapper 新增 `findActiveById(Long childId)` → 直接 SQL 查询激活的孩子档案。3 个控制器替换 `selectById` 为 `findActiveById`。

## 修改文件

- TaskChildMapper.java — 新增方法
- PointsController.java — 替换
- ExchangeController.java — 替换  
- TaskReviewController.java — 替换（2处）
