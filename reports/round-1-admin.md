# Round-1 测试报告：Admin 端（A-001 ~ A-005）

- 测试日期：2026-07-18
- 测试范围：admin 端 5 个用例
- DB Hash (执行前)：account=0 / initialization_token=1 unconsumed / instance=UNINITIALIZED
- DB Hash (执行后)：account=1 / initialization_token=1 consumed / instance=INITIALIZED
- 浏览器 session：`agent-browser --session admin`

## 用例结果总览

| Case ID | Title | Status | Bug IDs | Evidence |
|---------|-------|--------|---------|----------|
| A-001 | 系统初始化（init token） | PASS | bug-004 (Low) | evidence/A-001/ |
| A-002 | 管理员登录 | PASS | — | evidence/A-002/ |
| A-003 | 实例配置（CRUD） | PASS | bug-003 (Low) | evidence/A-003/ |
| A-004 | 账号管理 | PASS | bug-001 (Medium, S-004) | evidence/A-004/ |
| A-005 | 审计日志 + 健康面板 | PASS（功能），FAIL（审计数据） | bug-002 (High) | evidence/A-005/ |

## 详细执行

### A-001 系统初始化
- **步骤**：
  1. 打开 `/admin/init`，DB 中 `initialization_token` id=2 未消费
  2. 输入 token `cutegoals-dev-init-2026`（hash sha256 已 verify 匹配 DB）
  3. 输入管理员手机号 `13600049114` / 密码 `117315Akers` / 确认密码
  4. 点击「完成初始化」
- **结果**：跳转 `/admin/login`，instance=INITIALIZED
- **DB 验证**：
  - account id=2, phone=13600049114, status=ACTIVE ✓
  - initialization_token id=2 consumed=True ✓
  - family id=2 name=我的家庭 ✓
  - family_member: account_id=2 family_id=2 role=PARENT ✓
  - role_binding: account_id=2 INSTANCE_ADMIN + PARENT ✓
  - **instance_config: count=0**（bug-004）

### A-002 管理员登录
- **步骤**：输入 phone+password，点击「登录」
- **结果**：跳转 `/admin`（实例概览），顶栏显示 `136****9114`（4 星脱敏，正确）
- **API**：POST /api/auth/login 200，响应含 accountId/phone/roles，**accessToken 在 HttpOnly Cookie 中**（S-005 良好实践）
- **页面**：显示「初始化状态 已初始化」「备份状态 暂无备份记录」「恢复演练 尚未运行」

### A-003 实例配置
- **步骤**：访问 `/admin/config`，填入 sms.provider=aliyun / sms.enabled=true / recovery.email=admin@cutegoals.local / backup.schedule="0 2 * * *" / backup.retention_days=30 / rate_limit.login_max=5 / rate_limit.pin_max=3，点击「保存配置」
- **结果**：URL 不变，无 toast 反馈（bug-003），但 reload 后值持久化
- **DB 验证**：instance_config 写入 7 条记录 ✓
- **API**：GET /api/admin/config 200（用 cookie 认证）

### A-004 账号管理
- **步骤**：访问 `/admin/accounts`
- **结果**：表格显示 1 个账号（管理员自己）
- **观察 bug-001**：手机号列显示 `136*****114`（5 星，3+5+3）— **与顶栏 `136****9114`（4 星，3+4+4）脱敏规则不一致**
- 跳过实际 disable/enable 操作（停用当前管理员会破坏后续测试）

### A-005 审计日志 + 健康面板
- **审计日志页**：正常加载，显示「暂无审计日志」
- **DB 验证**：audit_log count=0 — **bug-002 (High)**：初始化、登录、配置更新等关键事件均未记录
- **健康面板**：整体状态 UP / 数据库 UP (postgresql) / 备份 NEVER（预期）/ 恢复演练 NEVER（预期）/ version 2.0.0-SNAPSHOT / nextScheduledBackup=2026-07-19T02:00:00（匹配设置的 cron）

## 发现的 Bug（候选）

### bug-001：手机号脱敏规则不一致
- **Severity**: Medium（S-004 安全基线相关）
- **Module**: web/admin
- **现象**：管理员顶栏显示 `136****9114`（显示后 4 位），但账号管理表格显示 `136*****114`（显示后 3 位）
- **影响**：脱敏规则不统一，可能误导用户对隐私保护的预期；安全审计无法明确判定「正确脱敏形式」
- **位置**：
  - 顶栏组件（正确）：保留前 3 + 后 4 = 7 位明文
  - 账号管理表格（错误）：保留前 3 + 后 3 = 6 位明文
- **建议修复**：统一脱敏规则（推荐保留前 3 + 后 4）

### bug-002：审计日志未记录任何事件
- **Severity**: High（合规与安全关键功能失效）
- **Module**: server audit / AuditLogService
- **现象**：完成初始化、登录、配置更新后，`audit_log` 表仍为 0 行
- **影响**：
  - 合规审计无法追溯关键操作
  - 安全事件事后调查无据可查
  - A-005 审计日志页面始终空白
- **疑似原因**：AuditLogService 未在某些事件路径中被调用，或事件发布失败但未抛错
- **建议修复**：
  - 检查 `instance.initialize` / `auth.login` / `admin.config.update` 等 endpoint 是否调用 auditLogService
  - 添加集成测试覆盖 audit 写入

### bug-003：配置保存无 UI 反馈
- **Severity**: Low（UX）
- **Module**: web/admin/config
- **现象**：点击「保存配置」后无 toast/notification，用户无法确认操作结果
- **建议修复**：成功保存后显示 antd message.success('配置已保存')

### bug-004：instance_config 初始化后无默认值
- **Severity**: Low（功能可用，但非预期）
- **Module**: server instance-management / V*__*.sql migration
- **现象**：A-001 完成后 instance_config 表为空，需手动通过 `/admin/config` 录入
- **影响**：默认 SMS / backup / rate_limit 配置缺失，新部署的系统在配置前可能行为异常
- **建议修复**：在 V*__auth_tables.sql 或专门 migration 中 INSERT 默认行；或在 InitializationService 中写入默认值

## 待补充测试

- A-004 实际 disable/enable 流程：需要在 Parent 端创建第二个家长账号后回测
- A-005 audit_log 内容验证：待 bug-002 修复后重测

## 下一步

继续 Stage C-2~C-7 Parent 端测试（20 用例）。
