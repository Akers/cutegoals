# Proposal: 修复孩子端 PIN 登录 Internal Server Error

## 问题描述

孩子端输入 PIN 后报错 `Internal server error`（HTTP 500），后端日志：

```
ERROR: insert or update on table "session" violates foreign key constraint "fk_session_account"
Key (account_id)=(-2) is not present in table "account".
```

## 根因分析

调用链：`DeviceController.childLogin()` → `DeviceBindingService.childLogin()` → `SessionService.createSession(-childId, deviceId)`

`DeviceBindingService.java:217` 使用了 `sessionService.createSession(-childId, deviceId)`，将 `-childId`（本例为 `-2`）作为 `account_id` 传入。`session` 表的外键 `fk_session_account` 要求 `account_id` 必须存在于 `account` 表中，负数 ID 自然不存在，导致 `DataIntegrityViolationException`。

代码中第 213-216 行已有 TODO 注释说明：这是 `bug-011` 的临时 workaround，等待 V13 schema 迁移（`migration-pending/V13__add_child_session_support.sql`）为 `session` 表添加 `child_id` 列后才能切换到 `createChildSession()`。

## 修复方案

1. **应用 V13 schema 迁移**：将 `migration-pending/V13__add_child_session_support.sql` 移到活跃迁移目录（重命名为 V15，因为 V13 已被 `V13__add_prize_model_config.sql` 占用），添加 `child_id` 列并使 `account_id` 可空
2. **切换会话创建方法**：`DeviceBindingService.java:217` 从 `createSession(-childId, deviceId)` 改为 `createChildSession(childId, deviceId)`（该方法已存在于 `SessionService` 中，正确设置 `accountId=null` + `childId=childId`）
3. **更新测试**：`DeviceBindingServiceTest` 中验证 `createSession(-childId, ...)` 的 mock 改为 `createChildSession(childId, ...)`

## 影响范围

- `server/common/src/main/resources/db/migration-pending/V13__add_child_session_support.sql` → 移到 `migration/` 并重命名为 V15
- `server/family/src/main/java/com/cutegoals/family/service/DeviceBindingService.java:213-217`
- `server/family/src/test/java/com/cutegoals/family/service/DeviceBindingServiceTest.java:214,405`

## 不涉及

- 无新增 capability
- 无 API 接口变更
- 无架构调整
- `SessionService.createChildSession()` 和 `Session.childId` 字段均已存在
