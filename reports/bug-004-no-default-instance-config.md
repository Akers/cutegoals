# Bug bug-004: instance_config 表初始化后为空，需手动录入

| Field | Value |
|-------|-------|
| ID | bug-004 |
| Severity | Low |
| Module | server/init + db/migration |
| Status | open |
| Discovered | 2026-07-18 |
| Blocking Cases | A-001（后置 DB 验证） |
| Evidence | reports/evidence/A-001/after-submit.png, reports/round-1-admin.md |

## 复现步骤

1. 执行 A-001 系统初始化（DB 重置后首次初始化）
2. 初始化完成后查询 `instance_config` 表

## 期望行为

- 系统初始化完成后，`instance_config` 表中应至少存在系统默认配置项（如 sms.provider, backup.schedule 等默认值）
- 新部署的系统无需手动配置即可正常运行

## 实际行为

- A-001 初始化完成后 `instance_config` 表 `count=0`
- 需管理员手动通过 `/admin/config` 页面录入全部 7 项配置（sms.provider, sms.enabled, recovery.email, backup.schedule, backup.retention_days, rate_limit.login_max, rate_limit.pin_max）
- A-003 通过 UI 手动配置后，表数据正常填充（7 条记录写入），说明 CRUD 功能正常

## 根因分析

### 后端代码
- 文件（推测）：
  - `server/instance-management/src/main/java/.../InitializationService.java` 初始化完成后未写入默认配置
  - `server/web/src/main/resources/db/migration/V*__*.sql` — migration 脚本中未包含默认配置 INSERT

### 确认路径
1. 查看 `InitializationService.java` 的 `initialize()` 方法，确认是否在创建管理员账号/实例状态更新后调用了 `instanceConfigService.initDefaults()`
2. 查看 `db/migration/` 目录下的 SQL 文件，确认是否缺少 `INSERT INTO instance_config ...` 语句

### 影响分析
- 新部署的系统在手动配置前：
  - 短信服务（SMS）无 provider 配置 → 发送短信可能失败
  - 备份计划无 schedule → 自动备份不执行
  - 速率限制无配置 → 登录暴力破解无防护
- 但系统仍可正常登录和操作（非崩溃级影响）

## 修复方向

### 推荐方案 A：InitializationService 中写入默认值
在 `InitializationService.initialize()` 方法末尾添加：
```java
if (instanceConfigMapper.count() == 0) {
    instanceConfigService.updateConfig(Map.of(
        "sms.provider", "aliyun",
        "sms.enabled", "true",
        "recovery.email", "admin@cutegoals.local",
        "backup.schedule", "0 2 * * *",
        "backup.retention_days", "30",
        "rate_limit.login_max", "5",
        "rate_limit.pin_max", "3"
    ));
}
```

### 推荐方案 B：Migration 脚本插入默认行
在 `V*__init_default_config.sql`（或追加到已有 migration）中添加：
```sql
INSERT INTO instance_config (config_key, config_value, masked) VALUES
  ('sms.provider', 'aliyun', false),
  ('sms.enabled', 'true', false),
  ('recovery.email', 'admin@cutegoals.local', false),
  ('backup.schedule', '0 2 * * *', false),
  ('backup.retention_days', '30', false),
  ('rate_limit.login_max', '5', false),
  ('rate_limit.pin_max', '3', false)
ON CONFLICT (config_key) DO NOTHING;
```

### 推荐组合
方案 A（新部署生效）+ 方案 B（新安装/重置后生效），双重保险。

## 影响范围

- **阻塞用例**：A-001 后置验证（instance_config count ≥ 1）
- **关联模块**：
  - `server/instance-management/src/main/java/.../InitializationService.java`
  - `server/web/src/main/resources/db/migration/V*__*.sql`

## 回归测试要点

1. **单 bug 回归**：
   - 方案 A 验证：重置 DB → A-001 初始化 → 查询 instance_config count ≥ 7
   - 方案 B 验证：重置 DB → 确认 migration 后 instance_config 有默认行
2. **关联回归**：确认默认配置不影响 A-003 的手动配置功能（覆盖后应使用新值）
