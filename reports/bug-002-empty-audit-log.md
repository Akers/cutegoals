# Bug bug-002: audit_log 表始终为空，所有审计操作未落表

| Field | Value |
|-------|-------|
| ID | bug-002 |
| Severity | High |
| Module | server/audit（instance-management） |
| Status | open |
| Discovered | 2026-07-18 |
| Blocking Cases | — |
| Evidence | reports/evidence/A-005/audit-page.png, reports/backend.log:255（SELECT 查询返回 0 行） |

## 复现步骤

1. 完成 A-001 系统初始化（创建管理员账号、初始化实例）
2. 完成 A-002 管理员登录（POST /api/auth/login）
3. 完成 A-003 配置更新（POST /api/admin/config 写入 7 项配置）
4. 完成 A-005 访问审计日志页 `/admin/audit`，页面显示「暂无审计日志」
5. 直查 DB：`SELECT event_type, count(*) FROM audit_log GROUP BY event_type;` 返回空

## 期望行为

- 系统初始化（INITIALIZE）、管理员登录（LOGIN）、配置更新（CONFIG_UPDATE）等关键操作后，audit_log 表应至少有一条对应的审计记录
- 审计日志页面应按时序展示日志条目，包含 event_type、actor_id、result、detail、created_at

## 实际行为

- audit_log 表始终为 0 行
- A-005 审计日志页面始终显示「暂无审计日志」
- 后端日志中仅有 SELECT 查询日志（`Preparing: SELECT ... FROM audit_log WHERE ...`），无 INSERT 日志

## 根因分析

### 日志确认
后端日志第 255 行确认 audti_log SELECT 查询正常执行：
```
Preparing: SELECT id,actor_id,actor_type,event_type,result,object_type,object_id,summary,request_id,metadata,created_at FROM audit_log WHERE (created_at >= ? AND created_at <= ?) ORDER BY created_at DESC
```
但返回 0 行，说明 `audit_log` 表始终未写入。

### 代码层面疑似原因

**1. Lombok 注解失效（LSP 报错）**
- `InstanceConfig.java` 和 `AuditLog.java` 实体类的 getter/setter 在 IDE 中报 undefined（LSP 错误），但 Spring Boot 编译期通过了（Lombok 在编译期生成方法）
- 可能是 `DatabaseAuditService.java` 使用 `AuditLog` 实体时字段映射不正确

**2. DatabaseAuditService 未实际调用 INSERT**
- 文件：`server/instance-management/src/main/java/com/cutegoals/instancemanagement/service/DatabaseAuditService.java`
- 可能存在以下问题：
  - `AuditLogMapper.insert()` 未被调用
  - 业务代码中 `auditService.record()` 未被调用（各 endpoint 未注入 auditService）
  - `@Transactional` 异常被吞没，导致 INSERT 失败但未抛错

**3. auditService 注入失败**
- 检查 `DatabaseAuditService` 是否被 Spring 容器管理（`@Service` 注解？）
- 检查调用方是否成功注入 `AuditService` 接口（而非 `DatabaseAuditService` 实现）

## 修复方向

### 推荐方案
1. **确认 auditService.record() 调用链路**：
   - 搜索 `auditService.record(` 调用点（全局 grep）
   - 确认 `InstanceInitializationService.initialize()` 中调用
   - 确认 `AuthController.login()` 中调用
   - 确认 `InstanceConfigController.updateConfig()` 中调用

2. **修复 DatabaseAuditService**：
   - 确认 `AuditLogMapper.insert(auditLog)` 被调用
   - 确认 `@Service` 注解存在
   - 确认 `AuditLog` 实体字段通过 `@TableField` 正确映射

3. **加日志**：
   - 在 `DatabaseAuditService.record()` 方法开头加 `log.info("Recording audit: eventType={}, actorId={}", ...)`
   - 在 Mapper insert 后加 `log.debug("Audit log inserted, id={}", ...)`
   - 捕获异常时打印 stack trace（不静默吞没）

4. **加集成测试**：
   ```java
   @Test
   void testAuditLogRecordedOnLogin() {
       authService.login(...);
       AuditLog log = auditLogMapper.selectOne(...);
       assertThat(log).isNotNull();
       assertThat(log.getEventType()).isEqualTo("LOGIN");
   }
   ```

### 替代方案
- 在 `WebSecurityConfig` 或 Auth 过滤器中添加审计拦截器（AOP），确保所有关键 endpoint 自动记录 audit
- 使用 Spring 事件机制：发布 `AuditEvent`，由 `AuditEventListener` 处理持久化

## 影响范围

- **阻塞用例**：无（功能正常，仅审计缺失）
- **关联模块**：
  - `server/instance-management/src/main/java/com/cutegoals/instancemanagement/service/DatabaseAuditService.java`
  - `server/instance-management/src/main/java/com/cutegoals/instancemanagement/entity/AuditLog.java`
  - `server/auth/src/main/java/.../AuthController.java`（登录审计）
  - `server/instance-management/.../InstanceConfigController.java`（配置更新审计）
  - `server/instance-management/.../InitializationService.java`（初始化审计）

## 回归测试要点

1. **单 bug 回归**：执行初始化（A-001）→ 验证 audit_log 有 `INITIALIZE` 记录；执行登录（A-002）→ 验证 `LOGIN` 记录
2. **阶段回归**：Admin 端全部用例，确认每个关键操作产生对应审计事件
3. **审计页面**：确认 `/admin/audit` 渲染正确，筛选功能正常
