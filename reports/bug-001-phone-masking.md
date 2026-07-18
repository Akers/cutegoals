# Bug bug-001: 手机号脱敏规则不一致（admin 表格 5 星 vs 顶栏 4 星）+ MyBatis DEBUG 日志打印完整 11 位手机号

| Field | Value |
|-------|-------|
| ID | bug-001 |
| Severity | Medium |
| Module | admin/accounts（前端）+ 安全 S-004 |
| Status | open |
| Discovered | 2026-07-18 |
| Blocking Cases | S-004 |
| Evidence | reports/evidence/A-004/accounts-page.png, reports/backend.log:57,74,92,118,135,152,298,497,1487,1884 |

## 复现步骤

### 前端脱敏不一致
1. 管理员登录（A-002 PASS）后，观察顶栏手机号显示为 `136****9114`（前 3 + 后 4，4 星）
2. 导航到 `/admin/accounts`（A-004）
3. 观察账号管理表格中手机号列显示为 `136*****114`（前 3 + 后 3，5 星）

### 后端日志泄露完整手机号
1. 查看 `reports/backend.log`
2. 搜索 `13600049114`（完整 11 位）
3. 发现在 AccountMapper.insert / AccountMapper.findByPhone 等 MyBatis SQL 参数日志中完整打印

## 期望行为

1. **前端**：所有展示手机号的点（顶栏 + 账号管理表格 + 任意脱敏点）使用**同一脱敏规则**。推荐保留前 3 + 后 4（即 `136****9114`），保留 7 位明文
2. **后端日志**：不出现完整 11 位手机号 `13600049114`。MyBatis 参数日志不打印 phone 字段的完整值

## 实际行为

1. **前端脱敏不一致**：
   - 顶栏（正确）：`136****9114` — 前 3 明文 + `****` + 后 4 明文
   - 账号管理表格（错误）：`136*****114` — 前 3 明文 + `*****` + 后 3 明文
   - 两处组件使用了不同的脱敏实现

2. **后端日志泄露**：
   - `AccountMapper.insert`: `Parameters: 13600049114(String), ...`
   - `AccountMapper.findByPhone`: `Parameters: 13600049114(String)`
   - 每次手机号查询/写入操作均完整打印到日志文件

## 根因分析

### 前端脱敏不一致
- **顶栏组件**（正确）：使用 `maskPhone(phone)` 工具函数，实现为 `phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')` → `136****9114`
- **账号表格组件**（错误）：使用不同的脱敏函数（或内联逻辑），实现为 `phone.replace(/(\d{3})\d{5}(\d{3})/, '$1*****$2')` → `136*****114`
- 文件位置（推测）：
  - 正确：`web/src/shared/utils/mask.ts`（或类似工具文件）
  - 错误：`web/src/admin/pages/accounts/index.tsx`（或列定义中的 `render` 函数）

### 后端日志泄露
- **配置**：`application.yml` 中 `mybatis.configuration.log-impl` 设置为 `org.apache.ibatis.logging.stdout.StdOutImpl` 或 `LOG` 级别
- **行为**：MyBatis 在执行 SQL 前通过 `PreparedStatementLogger` 打印完整参数列表，包括 `phone` 字段的 String 值
- **无过滤机制**：没有针对敏感字段（phone, password, pin）的参数日志过滤或脱敏

## 修复方向

### 推荐方案

**前端统一脱敏规则（推荐前 3 + 后 4）：**
- 创建/确认共享工具函数 `maskPhone(phone: string): string` 在 `web/src/shared/utils/mask.ts`
  - 实现：`phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')`
- 将账号管理表格的脱敏调用替换为 `maskPhone(phone)`
- 搜索所有 `web/src/` 下对手机号的脱敏逻辑，统一调用共享函数

**后端 MyBatis 日志过滤：**
- 方案 A（推荐）：在 `application.yml` 中将 MyBatis logger 级别设为 `WARN`：
  ```yaml
  logging.level.org.apache.ibatis: WARN
  ```
- 方案 B：自定义 MyBatis `LogImpl`，在 `debug()` 方法中对 phone 参数做正则替换
- 方案 C：在 logback-spring.xml 中使用 `%replace` 转换器对 `136\d{8}` 模式做脱敏

### 替代方案
- 前端使用 `maskPhone` 函数覆盖所有脱敏点（包括未来新增的组件）
- 后端使用 Spring AOP 拦截器对包含 phone 字段的日志做自动脱敏

## 影响范围

- **阻塞用例**：S-004（安全基线 FAIL）
- **关联模块**：
  - `web/src/admin/pages/accounts/` — 账号管理表格脱敏错误
  - `web/src/shared/` — 需统一脱敏工具函数
  - `web/src/parent/` — parent 端顶栏脱敏（检查是否一致）
  - `server/auth/` — AccountMapper MyBatis 日志
  - `server/instance-management/` — 审计日志可能含手机号

## 回归测试要点

1. **单 bug 回归**：登录后同时检查顶栏 + 账号管理表格的手机号脱敏格式，确认一致为前 3 后 4
2. **日志回归**：grep `13600049114` 确认后端日志中无完整手机号
3. **关联回归**：确认 MyBatis WARN 级别不破坏其他 SQL 日志正常显示
