# Design: fix-family-member-lookup

## 方案
1. 在  中增加按账号 ID 查询的方法，使用  取一条家庭成员记录。
2. 在  中，token 解析成功后直接调用 ，将结果中的  写入请求属性。

## 边界
- 若账号无家庭，返回 Optional.empty，不设置 ，由 controller 层按既有逻辑处理。
- 使用  是因为一个账号通常只关联一个家庭。
