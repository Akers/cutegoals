# Tasks: 修复孩子端 PIN 登录 Internal Server Error

## 任务清单

- [x] 1. 将 V13 schema 迁移从 `migration-pending/` 移到 `migration/` 并重命名为 V15
  - 源: `server/common/src/main/resources/db/migration-pending/V13__add_child_session_support.sql`
  - 目标: `server/common/src/main/resources/db/migration/V15__add_child_session_support.sql`
  - 原因: V13 已被 `V13__add_prize_model_config.sql` 占用

- [x] 2. 修改 `DeviceBindingService.java:217` - 从 `createSession(-childId, deviceId)` 改为 `createChildSession(childId, deviceId)`
  - 同时删除 lines 213-216 的 TODO 注释

- [x] 3. 更新 `DeviceBindingServiceTest.java` - mock/verify 从 `createSession(-childId, ...)` 改为 `createChildSession(childId, ...)`
  - Line 214, 221 (`shouldLoginChildWithCorrectPin`)
  - Line 405, 409 (`shouldClearLockoutAfterSuccessfulLogin`)

- [x] 4. 运行相关测试确认修复通过
  - `server/family` 模块测试
  - `server/auth` 模块测试（SessionService 相关）
