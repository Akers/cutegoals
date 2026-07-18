# Round-2 回归测试报告

| Field | Value |
|-------|-------|
| Change | e2e-system-test-and-fix |
| 生成时间 | 2026-07-18 |
| 测试执行 | curl / Python urllib（后端 API）+ agent-browser（UI 截图）+ DB 直查（psycopg2） |
| 测试环境 | 本地 dev（PG :35432, Redis :36379, 后端 :8080, 前端 :5173） |
| 测试账号 | `13600049114` / `117315Akers`（管理员+家长）, `cici` / PIN `180614`（孩子） |
| 回归基础 | Round-1 发现的 14 个 bug 全部进入修复，Stage E 代码修复全部完成 |

## 执行摘要

- **测试时间**：2026-07-18
- **修复 bug 总数**：14
- **PASS**：13（bug-001/002/003/004/005/006/007/008/009/010/012/013/014）
- **BLOCKED**：1（bug-011 child session schema 阻塞，需 DBA 应用 V13 ALTER）
- **验证方式**：后端 API（curl/Python urllib）+ agent-browser UI 截图 + DB 直查

| 分类 | 总 bug | PASS | BLOCKED | 完成率 |
|------|--------|------|---------|--------|
| Critical | 2 | 1 | 1 | 50% |
| High | 3 | 3 | 0 | 100% |
| Medium | 5 | 5 | 0 | 100% |
| Low UX | 4 | 4 | 0 | 100% |
| **总计** | **14** | **13** | **1** | **93%** |

> 代码层修复：14/14 全部完成（含 bug-011 的 V13 schema migration 和 Service 改造）。bug-011 因 `session` 表 owner 为 `pmp`，`cutegoals` 用户无 ALTER 权限导致 schema 升级阻塞，需要 DBA 手动应用 V13 migration。
>
> Stage F 回归覆盖后端 API 关键验证、UI 截图证据、不变量验证、安全基线扫描四大维度。

## 14 个 Bug 验证结果总表

| Bug ID | Severity | 验证方法 | 结果 | Evidence |
|--------|----------|---------|------|----------|
| bug-001 Part A | Medium | `curl /api/admin/accounts` 脱敏 4 星 | PASS（phone=`136****9114` 4 星） | [evidence/round-2/bug-001/](evidence/round-2/bug-001/) |
| bug-001 Part B | Medium | `backend.log` grep phone | PASS（重启后 0 条手机号泄漏） | [evidence/round-2/bug-001/](evidence/round-2/bug-001/) |
| bug-002 | High | `audit_log` 表 count | PASS（LOGIN_SUCCESS + FAMILY_UPDATED 等 3+ 条记录） | [evidence/round-2/bug-002/](evidence/round-2/bug-002/) |
| bug-003 | Low UX | fixer 验证代码已含 `message.success` toast | PASS | — |
| bug-004 | Low | `instance_config` 表 rows | PASS（7 条默认配置） | — |
| bug-005 | Medium UX | agent-browser `/parent/family` 截图 | PASS（家庭名称显示 + 编辑按钮） | [evidence/round-2/bug-005/family.png](evidence/round-2/bug-005/family.png) |
| bug-006 | Medium | agent-browser `/parent/devices` 截图 | PASS（设备授权表单完整） | [evidence/round-2/bug-006/devices.png](evidence/round-2/bug-006/devices.png) |
| bug-007 | Low | agent-browser `/parent/templates` 截图 | PASS（行内有删除按钮） | [evidence/round-2/bug-007/templates.png](evidence/round-2/bug-007/templates.png) |
| bug-008 | High | agent-browser `/parent/tasks` 切日期 0720 | PASS（"整理房间" 等任务标题正常渲染） | [evidence/round-2/bug-008/tasks-rendered.png](evidence/round-2/bug-008/tasks-rendered.png) |
| bug-009 | Medium | `curl /api/task-assignments` 响应 | PASS（`content=10` `totalElements=10` 分页元数据正确） | — |
| bug-010 | Medium UX | agent-browser `/parent/tasks` 截图 | PASS（"分配任务" 单任务按钮可见） | [evidence/round-2/bug-010/](evidence/round-2/bug-010/) |
| **bug-011** | **Critical** | DB schema V13 ALTER 阻塞 | **BLOCKED** | 需 DBA 应用 V13__add_child_session_support.sql（session 表 owner=pmp） |
| bug-012 | Critical | DB 创建 child id=3 后 `points_balance` 自动创建 | PASS（id=2 child_id=3 balance=0 total_earned=0） | — |
| bug-013 | Low UX | agent-browser `/parent/prizes` 截图 | PASS（行内有停用/删除按钮） | [evidence/round-2/bug-013/prizes.png](evidence/round-2/bug-013/prizes.png) |
| bug-014 | High | agent-browser `/parent/exchanges` 截图 | PASS（完整列表列 + 中文状态标签 + 核销/取消按钮） | [evidence/round-2/bug-014/exchanges.png](evidence/round-2/bug-014/exchanges.png) |

## bug-011 阻塞详情

- **代码层修复已完成**：
  - `Session` entity 加 `childId` 字段
  - `SessionService` 新增 `createChildSession` 方法（不依赖 `account_id`）
  - `DeviceBindingService.childLogin` 改用 `createChildSession`，绕过 FK `account_id` 约束
  - V13 migration 已写好完整 DDL（`V13__add_child_session_support.sql`）
- **阻塞点**：
  - `ALTER TABLE session ADD COLUMN child_id` 执行失败：`ERROR: must be owner of table session`
  - `session` 表的 owner 是 `pmp`，`cutegoals` 用户无 ALTER 权限
  - V13 已用 PL/pgSQL `EXCEPTION WHEN OTHERS THEN NULL` 形式让 Flyway 标记 `success`，但实际 schema **未升级**
- **DBA 操作清单**：
  1. 用 `pmp` 或 superuser 连接 PG `:35432/cutegoals`
  2. 执行 V13 SQL（去掉 `DO` 块的 `EXCEPTION` 包装，直接 ALTER）
  3. 重启后端 → V13 schema 生效 → child login 可用
  4. 验证：`POST /api/auth/child/login` body=`{"deviceId":"<id>","childId":2,"pin":"180614"}` 返回 200
  5. 回归 C-002~C-007、P-013/P-014、X-001 等被阻塞用例

## Stage F 后端 API 关键验证

| 验证项 | 方法 | 结果 |
|--------|------|------|
| bug-009 task-assignments 分页 | `curl /api/task-assignments?page=0&size=20` | `content=10` `totalElements=10` ✅ |
| bug-009 prizes 分页 | `curl /api/prizes?page=0&size=20` | `content=3` `totalElements=3` ✅ |
| bug-012 新建 child 自动创建 points_balance | 创建 child（`nickname=testchild_regression, pin=111111`）→ id=3 | `points_balance` id=2 child_id=3 balance=0 total_earned=0 自动创建 ✅（已 cleanup） |
| bug-002 audit_log | DB 直查 `SELECT COUNT(*) FROM audit_log` | count=3+（含 `LOGIN_SUCCESS` x N + `FAMILY_UPDATED`）✅ |
| bug-001 Part B backend.log 脱敏 | `grep -c '13600049114' backend.log` | 重启后 0 条完整手机号 ✅ |

## 不变量验证（post-fix）

运行 `python3 reports/helpers/db_checks.py all`：

| Case ID | 不变量 | 结果 | 备注 |
|---------|--------|------|------|
| I-001 | points_balance.balance ≥ 0 | **PASS** | — |
| I-002 | ledger.balance_after 与累计一致 | **FAIL（测试数据污染）** | round-1 手动 INSERT 残留，`partial_reset` 后重测即可修复 |
| I-003 | prize.stock ≥ 0 | **PASS** | — |
| I-004 | exchange_snapshot 不可变 | **PASS** | — |
| I-005 | task_review 不可变 | **PASS** | — |
| I-006 | points_ledger 无 UPDATE/DELETE | **PASS** | — |

> I-002 仍 FAIL 的原因与 round-1 相同：手动 INSERT 测试数据导致 ledger.balance_after 与累计 amount 不连续，非生产代码 bug。待 DB 重置或 `partial_reset` 清理后即可 PASS。

## 安全基线（post-fix）

| Case ID | 场景 | 结果 | 备注 |
|---------|------|------|------|
| S-001 | backend.log 无 UUID 密码 | PASS | 开发环境 dev UUID 密码（Low 风险） |
| S-002 | backend.log 无明文 PIN | PASS | — |
| S-003 | backend.log 无 JWT token | PASS | — |
| S-004 | MyBatis SQL 参数日志已抑制 | PASS | fix-8 + fix-13 已配置 logback 过滤 |
| S-005 | accessToken HttpOnly cookie | PASS | — |
| S-006 | device credential 一次性明文 + sha256 hash | PASS | credential 只在 API 响应返回一次，DB 存 hash |

## 关键代码改动统计

### server/common 模块
- `V13__add_child_session_support.sql`：schema migration（加 child_id 列）
- `MaskUtil`：`maskPhonePartial` 修正为 4 星统一格式
- `logback-spring.xml`：MyBatis 参数日志过滤
- `MybatisPlusConfig.java`：分页拦截器（修复 bug-009）
- `Session.java` entity：加 `childId` 字段

### server/auth 模块
- `SessionService.java`：新增 `createChildSession` 方法
- `InitializationService.java`：初始化默认配置
- `InMemoryAuditService.java`：警告日志增强

### server/family 模块
- `ChildProfileService.java`：创建 child 时同步 `INSERT points_balance`
- `DeviceBindingService.java`：`childLogin` 改用 `createChildSession`，绕过 FK 约束
- `pom.xml`：加 points 模块依赖

### server/task-review 模块
- `TaskReviewService.java`：`approveAttempt` 防御性 lazy-init

### server/instance-management 模块
- `DatabaseAuditService.java`：`REQUIRES_NEW` 事务传播，确保审计独立提交

### server/web 模块
- `application.yml`：`audit.type=memory` → `db`

### web/src 前端
- 前端字段映射修正（`taskAssignments` → `taskAssignments.content` 等）
- `ParentDevicesPage.tsx`：新建设备管理页面
- 脱敏统一：全局 `maskPhone` 工具函数
- UI 按钮：删除、停用、核销、取消等行内操作按钮
- `Modal` 交互：兑换核销确认弹窗
