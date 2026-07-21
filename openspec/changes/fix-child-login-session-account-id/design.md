# Design: 修复孩子端 PIN 登录 Internal Server Error

## 修复方案

### 方案（唯一，无需多方案对比）

三步修复，按依赖顺序执行：

1. **数据库迁移** → 2. **业务代码修改** → 3. **测试更新**

### 1. 数据库迁移

将 `migration-pending/V13__add_child_session_support.sql` 移到 `migration/` 目录并重命名为 `V15__add_child_session_support.sql`（V13 已被 `V13__add_prize_model_config.sql` 占用）：

```sql
ALTER TABLE session ADD COLUMN IF NOT EXISTS child_id BIGINT DEFAULT NULL;
ALTER TABLE session ALTER COLUMN account_id DROP NOT NULL;
CREATE INDEX IF NOT EXISTS idx_session_child ON session (child_id);
```

- 兼容 H2（测试）和 PostgreSQL（生产）
- 使用 `IF NOT EXISTS` / `IF NOT EXISTS` 保证幂等
- 不添加 FK 约束到 `child_profile(id)`，避免迁移时权限问题

### 2. 业务代码修改

`DeviceBindingService.java:213-217` — 从临时 workaround 切换到已实现的正确方法：

```java
// 删除（lines 213-216）:
// TODO: bug-011 follow-up - switch to createChildSession(childId, deviceId)
// once DBA has applied V13 schema ...
// Currently using legacy -childId path ...

// 修改（line 217）:
- String sessionId = sessionService.createSession(-childId, deviceId);
+ String sessionId = sessionService.createChildSession(childId, deviceId);
```

`SessionService.createChildSession()` 已正确实现（line 62-85）：设置 `accountId = null`，`childId = childId`，不再违反外键约束。

### 3. 测试更新

`DeviceBindingServiceTest.java` — 将 mock 从 `createSession(-childId, ...)` 改为 `createChildSession(childId, ...)`：

- Line 214: `when(sessionService.createSession(-childId, deviceId))` → `when(sessionService.createChildSession(childId, deviceId))`
- Line 221: `verify(sessionService).createSession(-childId, deviceId)` → `verify(sessionService).createChildSession(childId, deviceId)`
- Line 405: `when(sessionService.createSession(-childId, deviceId))` → `when(sessionService.createChildSession(childId, deviceId))`
- Line 409: `verify(sessionService).createSession(-childId, deviceId)` → `verify(sessionService).createChildSession(childId, deviceId)`

### 改动文件

1. `server/common/src/main/resources/db/migration-pending/V13__add_child_session_support.sql` → 移动+重命名
2. `server/family/src/main/java/com/cutegoals/family/service/DeviceBindingService.java`
3. `server/family/src/test/java/com/cutegoals/family/service/DeviceBindingServiceTest.java`

共 3 个文件（含迁移文件的移动），代码改动集中在 1 个 Java 文件。
