# Bug bug-011: child_login 用 `-childId` 作为 account_id 导致 session FK 违反，孩子端完全无法登录

| Field | Value |
|-------|-------|
| ID | bug-011 |
| Severity | **Critical** |
| Module | auth/session + child-login（DeviceBindingService） |
| Status | open |
| Discovered | 2026-07-18 |
| Blocking Cases | C-002~C-007, P-013, P-014, P-019, X-001 |
| Evidence | reports/backend.log:1811（完整 stack trace） |

## 复现步骤

1. 完成 C-001 设备绑定（孩子端绑定设备成功）
2. 尝试孩子 PIN 登录（C-002）：在 `/child/login` 输入 PIN `180614`
3. 观察 API 响应和后台日志

## 期望行为

- 孩子输入正确 PIN 后应成功创建 session，进入孩子首页
- `session` 表应正确存储孩子的 session 记录

## 实际行为

- 孩子 PIN 登录失败，API 返回 500 错误
- 后端日志完整 stack trace 打印 FK 违反错误

## 根因分析

### 精确代码定位
- 文件：`server/family/src/main/java/com/cutegoals/family/service/DeviceBindingService.java:213`
- 代码行：
  ```java
  String sessionId = sessionService.createSession(-childId, deviceId);
  ```
  其中 `childId` 为孩子的 ID（如 2），因此传入 `-2` 作为 `account_id`

### 错误链
1. `DeviceBindingService.childLogin()` 调用 `SessionService.createSession(-childId, deviceId)`
2. `SessionService.createSession(accountId=-2, deviceFingerprint="...")` 执行 INSERT
3. SQL：`INSERT INTO session (session_id, account_id, expires_at, device_fingerprint, revoked, created_at) VALUES (?, -2, ?, ?, ?, ?)`
4. session 表存在 FK `fk_session_account`，引用 `account(id)` 表
5. account 表无 id=-2 的记录（childId=2 的孩子在 child_profile 表，不在 account 表）
6. **违反外键约束**：`ERROR: insert or update on table "session" violates foreign key constraint "fk_session_account". Key (account_id)=(-2) is not present in table "account"`

### 后端日志确认（backend.log:1811）
```
org.springframework.dao.DataIntegrityViolationException:
### Error updating database.  Cause: org.postgresql.util.PSQLException:
ERROR: insert or update on table "session" violates foreign key constraint "fk_session_account"
  详细：Key (account_id)=(-2) is not present in table "account".
```

### 影响
- childId=2 → 传参 -2 → session 表 account_id=-2 → FK 违反 → 500 错误
- **孩子端完全无法登录**：所有孩子端用例（C-002~C-007）和依赖孩子登录的家长端用例（P-013, P-014, P-019）全部阻塞
- 阻塞全链路 X-001、积分审核流程、兑换核销流程

## 修复方向

### 核心问题
session 表当前 schema 假定所有 session 都绑定 account（用户），但 child session 应当绑定 child（孩子）。`account_id` 为负值是一种「hack」尝试区分 child session，但破坏了 FK 约束。

### 推荐方案：schema 改造 + SessionService 扩展

**步骤 1：Migration 加 child_id 列**
```sql
ALTER TABLE session ADD COLUMN child_id BIGINT;
ALTER TABLE session ADD CONSTRAINT fk_session_child
    FOREIGN KEY (child_id) REFERENCES child_profile(id);
ALTER TABLE session ALTER COLUMN account_id DROP NOT NULL;
-- 或保持 NOT NULL 但移除 child login 对 account_id 的依赖
```

**步骤 2：SessionService 改造**
```java
// 新增重载方法
public String createSession(Long accountId, String deviceFingerprint) {
    // 已有逻辑：account-based session
}

public String createChildSession(Long childId, String deviceFingerprint) {
    // 新方法：child-based session，account_id = null，child_id = childId
    Session session = new Session();
    session.setSessionId(generateSessionId());
    session.setChildId(childId);  // 新字段
    session.setDeviceFingerprint(deviceFingerprint);
    session.setExpiresAt(LocalDateTime.now().plusDays(7));
    sessionMapper.insert(session);
    return session.getSessionId();
}
```

**步骤 3：DeviceBindingService.childLogin 改造**
```java
// 修复前（错误）
String sessionId = sessionService.createSession(-childId, deviceId);

// 修复后（正确）
String sessionId = sessionService.createChildSession(childId, deviceId);
```

### 替代方案（不推荐）
- 不改 schema，在 account 表为孩子创建「影子记录」（复杂度高，入侵大）
- 完全移除 session FK 约束（不安全，数据完整性无保障）

### 关键约束
- 必须保证不破坏现有 account-based session（家长/管理员登录）
- `SessionService.createSession(accountId, deviceFingerprint)` 的现有调用者不受影响
- 孩子 session 不需要关联 account 表，只需要 `child_id` 指向 `child_profile`

## 影响范围

- **阻塞用例**：
  - C-002~C-007（孩子端全部 6 个功能用例）
  - P-013（审核通过 — 依赖孩子提交任务）
  - P-014（审核驳回 — 同上）
  - P-019（核销幂等 — 依赖孩子兑换）
  - X-001（完整工作流 — 依赖孩子登录）
- **关联模块**：
  - `server/family/.../service/DeviceBindingService.java:213` — 直接错误位置
  - `server/auth/.../service/SessionService.java:45` — FK 违反处
  - `server/auth/.../mapper/SessionMapper.java` — SQL INSERT
  - `db/migration/V*__*.sql` — 需要添加 child_id 列 migration
  - `server/auth/.../entity/Session.java` — 加 childId 字段

## 回归测试要点

1. **单 bug 回归**：孩子 PIN 登录成功（C-002 PASS），session 表有 child_id 值
2. **回归验证**：家长登录仍正常（account-based session 不受影响）
3. **完整流程回归**：C-003 提交任务 → P-013 审核 → C-006 兑换 → P-018 核销（X-001）
