# CuteGoals 2.0 系统级 E2E 测试计划

## 元数据

- Change: `e2e-system-test-and-fix`
- 测试日期: 2026-07-18
- 测试执行人: agent-browser + psql(Python 替代) + 后端日志扫描
- 测试环境: 本地 dev (PG :35432, Redis :36379, 后端 :8080, 前端 :8000)
- DB 初始化: 已重置到空库（仅含 1 个 initialization_token）
- DB Schema: `cutegoals`（通过 Python psycopg2 直连 host:35432，options='-c search_path=cutegoals'）
- DB/Redis 验证工具: `/tmp/cg-venv/bin/python3` + `reports/helpers/db_checks.py`
- 安全扫描工具: `reports/helpers/security_checks.py`
- 测试前端: UmiJS，通过 agent-browser 以真实用户身份操作
- 账号/凭据:
  - 系统管理员 + 家长: 手机号 `13600049114` / 密码 `117315Akers`
  - 孩子: 昵称 `cici` / PIN `180614`（由家长创建后绑定）

## 测试范围

覆盖三大客户端（admin / parent / child）的全部 UI 可达功能，涵盖 9 个后端业务模块，以及跨端协作流程、6 条核心数据库不变量、6 项安全基线。

| 维度 | 覆盖范围 |
|------|---------|
| Admin 端 | 系统初始化、管理员登录、配置管理、账号管理（启用/禁用）、审计日志查询、健康检查 |
| Parent 端 | 家庭信息管理、孩子档案 CRUD、设备授权、任务模板 CRUD、任务分配（批量/日历/recurring）、任务审核通过/驳回/幂等、积分余额/流水/调整、奖品 CRUD/库存、盲盒池 CRUD/条目、兑换核销/幂等 |
| Child 端 | 设备绑定、PIN 登录、今日任务查看/提交、积分余额/流水查询、奖品商城浏览、直接兑换、盲盒兑换、任务/兑换历史 |
| 跨端协作 | 完整家庭工作流：创建任务 → 孩子提交 → 家长审核 → 积分入账 → 孩子兑换 → 家长核销 |
| 不变量 | 6 条核心 DB 约束：积分非负、流水余额一致、库存非负、快照不可变、审核不可变、ledger 无 UPDATE/DELETE |
| 安全基线 | 三层观测（API 响应/浏览器存储/后端日志）：无明文密码/PIN/JWT token/完整手机号 |

## 账号

| 角色 | 端 | 账号 | 凭据 | 说明 |
|------|-----|------|------|------|
| 管理员 | admin | 13600049114 | 密码 117315Akers | 初始化时创建，拥有 instance_admin 角色 |
| 家长 | parent | 13600049114 | 密码 117315Akers | 与管理员为同一账号（admin+parent 双角色） |
| 孩子 | child | cici | PIN 180614 | 由家长创建并绑定设备后使用 PIN 登录 |

## 模块依赖顺序

```
auth → family → task → task-review → points → prize → exchange → admin → web-frontend
```

各阶段执行时按此顺序进行，前置模块用例通过后方可执行后代模块。

## 用例总览

| 类别 | 前缀 | 数量 | 覆盖模块 |
|------|------|------|---------|
| Admin 端 | A | 5 | auth, admin, instance-management |
| Parent 端 | P | 20 | auth, family, task, task-review, points, prize, exchange |
| Child 端 | C | 7 | auth, device, task, points, prize, exchange |
| Cross-cutting | X | 1 | 全模块（auth→task→review→points→prize→exchange） |
| Invariants | I | 6 | points, prize, exchange, task-review（DB 约束） |
| Security | S | 6 | 所有模块（三层安全观测） |
| **合计** | | **45** | |

## 用例详情

---

### A. Admin 端（5 用例）

#### A-001 系统初始化

- **前置条件**:
  1. DB 已重置到干净初始状态（`scripts/reset-dev-db.sh` 成功执行）
  2. 实例状态为 `UNINITIALIZED`
  3. `initialization_token` 表中有且仅有一条未使用的 token
  4. 后端 :8080 已启动且 `/api/health` 返回 `UP`
  5. 前端 :5173 已启动且 API 代理正常

- **测试步骤**:
  1. 打开浏览器访问 `http://localhost:5173/admin/init`
  2. 确认页面显示系统初始化表单（token 输入框 + 手机号 + 密码）
  3. 查询当前有效的初始化 token:
     ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT token FROM initialization_token WHERE consumed=false LIMIT 1;"
     ```
  4. 在表单中填入: token（上一步查询的值）、手机号 `13600049114`、密码 `117315Akers`
  5. 点击提交
  6. 等待页面跳转
  7. 截图保存到 `reports/evidence/A-001-init.png`
  8. 捕获 `POST /api/auth/initialize` 响应 → `reports/evidence/network/A-001-response.json`

- **期望结果**:
  1. 页面跳转到 `/admin/login`
  2. 实例状态变为 `INITIALIZED`

- **后置 DB 验证**:
  1. 验证 `initialization_token` 已消费:
     ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT consumed FROM initialization_token WHERE consumed=true;"
     ```
  2. 验证 `account` 表有 1 条记录（管理员账号）:
     ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT id, phone, roles FROM account;"
     ```
  3. 验证 `instance_config` 表有默认配置:
     ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT count(*) FROM instance_config;"
     ```
  4. 期望: account 1 条、instance_config ≥ 1 条、initialization_token.consumed = true

- **关联不变量**: 无（全局初始化）

---

#### A-002 管理员登录

- **前置条件**:
  1. A-001 系统初始化已完成（instance=INITIALIZED）
  2. 管理员账号 `13600049114` 已创建
  3. 当前处于未登录状态（无有效 cookie/session）

- **测试步骤**:
  1. 打开浏览器访问 `http://localhost:5173/admin/login`
  2. 输入手机号 `13600049114`、密码 `117315Akers`
  3. 点击登录按钮
  4. 截图保存到 `reports/evidence/A-002-login.png`
  5. 捕获 `POST /api/auth/login` 响应 → `reports/evidence/network/A-002-response.json`
  6. 确认页面跳转到 admin 概览页（`/admin`）
  7. 确认页面显示 dashboard 面板

- **期望结果**:
  1. 登录成功，跳转到 admin 概览页
  2. API 响应中包含 `accountId`、`phone`、`roles`（含 `INSTANCE_ADMIN`）、`familyId`
  3. 响应中的 `phone` 应脱敏展示（`136****49114`），不应返回完整手机号
  4. 浏览器 cookie 中设置了 `access_token`（HttpOnly）和 `csrf_token`

- **后置 DB 验证**:
  1. 验证 `account` 表中该账号 `disabled = false`
  2. 验证 `session_token` 表新增一条 session 记录

- **关联不变量**: 无

---

#### A-003 配置查询与更新

- **前置条件**:
  1. A-002 管理员已登录
  2. 当前在 admin 概览页

- **测试步骤**:
  1. 导航到配置管理页面 `/admin/config`
  2. 截图保存到 `reports/evidence/A-003-config-view.png`
  3. 确认配置列表展示正确（包含默认系统配置项）
  4. 尝试修改一项配置（如修改 `default_task_points` 为 `15`）
  5. 提交保存
  6. 刷新页面确认配置已更新
  7. 截图保存到 `reports/evidence/A-003-config-update.png`
  8. 捕获 `PUT /api/admin/config` 响应 → `reports/evidence/network/A-003-config-response.json`

- **期望结果**:
  1. 配置列表完整展示所有系统配置项（key-value 格式）
  2. 配置更新后刷新页面显示新值
  3. API 返回 200

- **后置 DB 验证**:
  1. ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT config_key, config_value FROM instance_config WHERE config_key='default_task_points';"
     ```
  2. 期望: 更新后的值已持久化

- **关联不变量**: 无

---

#### A-004 账号管理（查询/禁用/启用）

- **前置条件**:
  1. A-002 管理员已登录
  2. 系统中有至少一个账号（管理员自身）
  3. 如果有多个账号可操作效果更好

- **测试步骤**:
  1. 导航到账号管理页面 `/admin/accounts`
  2. 截图保存到 `reports/evidence/A-004-accounts-list.png`
  3. 确认账号列表展示正确（分页、账号信息完整）
  4. 尝试搜索指定账号（如搜索 `13600049114`）
  5. 选择一个非管理员账号点击「禁用」
  6. 截图保存到 `reports/evidence/A-004-account-disable.png`
  7. 捕获 `POST /api/admin/accounts/{id}/disable` 响应
  8. 列表确认该账号状态变为「已禁用」
  9. 对该账号点击「启用」
  10. 列表确认状态恢复为「正常」
  11. 捕获 `POST /api/admin/accounts/{id}/enable` 响应

- **期望结果**:
  1. 账号列表分页展示、搜索功能正常
  2. 禁用操作后账号状态变为 disabled，启用后恢复
  3. 禁用/启用 API 返回 200

- **后置 DB 验证**:
  1. ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT id, phone, disabled FROM account;"
     ```
  2. 期望: 被禁用账号的 `disabled = true`，重新启用后 `disabled = false`

- **关联不变量**: 无

---

#### A-005 审计日志查询

- **前置条件**:
  1. A-002 管理员已登录
  2. 系统中已有若干操作记录（之前的初始化、登录、配置变更等产生了 audit_log）

- **测试步骤**:
  1. 导航到审计日志页面 `/admin/audit`
  2. 截图保存到 `reports/evidence/A-005-audit-log.png`
  3. 确认日志列表按时序展示（最新的在前）
  4. 尝试按事件类型筛选（如 `LOGIN`、`CONFIG_UPDATE`）
  5. 尝试按日期范围筛选
  6. 截图保存到 `reports/evidence/A-005-audit-filter.png`

- **期望结果**:
  1. 审计日志完整展示，包含 `event_type`、`actor_id`、`result`、`detail`、`created_at`
  2. 筛选功能正常
  3. 日志条目时序正确

- **后置 DB 验证**:
  1. ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT event_type, count(*) FROM audit_log GROUP BY event_type ORDER BY count(*) DESC;"
     ```
  2. 期望: 至少包含 `INITIALIZE`、`LOGIN`、`CONFIG_UPDATE` 等事件类型

- **关联不变量**: 无

---

### B. Parent 端（20 用例 P-001 ~ P-020）

#### P-001 家长登录

- **前置条件**:
  1. A-001 系统初始化已完成（管理员与家长为同一账号）
  2. 当前处于未登录状态（使用隔离的 parent browser context）
  3. 家庭（family）已自动创建

- **测试步骤**:
  1. 在 parent browser context 中打开 `http://localhost:5173/parent/login`
  2. 输入手机号 `13600049114`、密码 `117315Akers`
  3. 点击登录按钮
  4. 截图保存到 `reports/evidence/P-001-login.png`
  5. 捕获 `POST /api/auth/login` 响应 → `reports/evidence/network/P-001-response.json`
  6. 确认页面跳转到 parent 首页（`/parent` 或 `/parent/family`）

- **期望结果**:
  1. 登录成功，跳转到家庭概览页
  2. API 响应包含 `roles`（含 `PARENT`）、`familyId`
  3. HttpOnly cookie 中正确设置了 access/refresh token

- **后置 DB 验证**:
  1. 验证 `account` 表该账号关联的 `family_id` 非空
  2. 验证 `family` 表有一条记录

- **关联不变量**: 无

---

#### P-002 家庭信息查询与更新

- **前置条件**:
  1. P-001 家长已登录
  2. 家庭已创建

- **测试步骤**:
  1. 在 parent context 中查看家庭信息页面（`/parent/family`）
  2. 截图保存到 `reports/evidence/P-002-family-view.png`
  3. 确认页面展示: 家庭名称、家庭成员列表
  4. 捕获 `GET /api/family` 响应
  5. 在家庭信息页面点击编辑
  6. 修改家庭名称（如添加后缀「-测试家庭」）
  7. 提交保存
  8. 截图保存到 `reports/evidence/P-002-family-edit.png`
  9. 捕获 `PUT /api/family` 响应
  10. 刷新页面确认名称已更新

- **期望结果**:
  1. 家庭名称查询正确显示
  2. 成员列表中包含当前账号（家长角色）
  3. 家庭名称更新成功，刷新后显示新名称
  4. API 均返回 200，数据完整

- **后置 DB 验证**:
  1. ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT id, name FROM family;"
     ```
  2. 期望: 名称与修改后的值一致

- **关联不变量**: 无

---

#### P-003 成员管理（添加家长、移除成员）

- **前置条件**:
  1. P-001 家长已登录
  2. 家庭已存在

- **测试步骤**:
  1. 在家庭信息页面查看成员列表
  2. 截图保存到 `reports/evidence/P-003-member-list.png`
  3. 确认成员列表中包含当前家长账号
  4. 如有「添加家长」功能，填写新家长手机号发送邀请
  5. 截图保存到 `reports/evidence/P-003-add-member.png`
  6. 捕获 `POST /api/family/invitations` 响应
  7. 查看成员管理中的移除功能
  8. 捕获 `DELETE /api/family/members/{id}` 响应（注意不要移除当前主账号）

- **期望结果**:
  1. 成员列表展示所有家庭成员及角色
  2. 添加家长功能可用（或邀请流程正常）
  3. API 返回 200

- **后置 DB 验证**:
  1. ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT a.id, a.phone, a.roles FROM account a JOIN family_member fm ON a.id = fm.account_id WHERE fm.family_id = (SELECT id FROM family LIMIT 1);"
     ```

- **关联不变量**: 无

---

#### P-004 创建孩子档案

- **前置条件**:
  1. P-001 家长已登录
  2. 家庭已存在

- **测试步骤**:
  1. 导航到孩子管理页面 `/parent/children`
  2. 点击「添加孩子」
  3. 填写孩子信息: 昵称 `cici`、PIN `180614`、年龄 `8`（或可选字段）
  4. 点击提交
  5. 截图保存到 `reports/evidence/P-004-add-child.png`
  6. 捕获 `POST /api/family/children` 响应 → `reports/evidence/network/P-004-response.json`
  7. 在孩子管理页面查看孩子列表，确认 cici 出现
  8. 点击 cici 查看详情
  9. 截图保存到 `reports/evidence/P-004-child-detail.png`
  10. 捕获 `GET /api/family/children` 响应

- **期望结果**:
  1. 孩子 `cici` 出现在家庭孩子列表中
  2. PIN 输入时使用密码输入框（隐藏明文）
  3. API 返回 201，包含孩子 `id`、`nickname` 等字段
  4. 孩子列表展示完整（分页、搜索可用），PIN 不展示在详情中

- **后置 DB 验证**:
  1. ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT id, nickname, pin_hash FROM child_profile WHERE nickname='cici';"
     ```
  2. 期望: `child_profile` 表有 nickname=cici 的记录，`pin_hash` 为哈希值（非明文）

- **关联不变量**: 无

---

#### P-005 查询/更新孩子档案

- **前置条件**:
  1. P-004 孩子 cici 已创建
  2. 家长已登录

- **测试步骤**:
  1. 在孩子管理页面查看孩子列表
  2. 截图保存到 `reports/evidence/P-005-child-list.png`
  3. 点击 cici 进入编辑模式
  4. 修改昵称为 `cici-updated`，提交保存
  5. 截图保存到 `reports/evidence/P-005-child-update.png`
  6. 捕获 `PUT /api/family/children/{id}` 响应
  7. 改回昵称为 `cici`，提交保存（保持后续用例一致）
  8. 捕获 `GET /api/family/children` 响应

- **期望结果**:
  1. 孩子列表展示完整（分页、搜索功能可用）
  2. 孩子信息更新成功，PIN 不展示在详情中
  3. API 返回 200

- **后置 DB 验证**:
  1. ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT id, nickname FROM child_profile WHERE nickname='cici' AND deleted=false;"
     ```
  2. 期望: 孩子 cici 存在且未删除

- **关联不变量**: 无

---

#### P-006 设备授权（为 cici 绑定设备）

- **前置条件**:
  1. P-004 孩子 cici 已创建
  2. 家长已登录
  3. 准备一个测试设备 ID（如 `test-device-cici-001`）

- **测试步骤**:
  1. 在家长端导航到设备管理页面
  2. 对 cici 发起设备授权: 输入设备 ID `test-device-cici-001`
  3. 点击授权绑定
  4. 截图保存到 `reports/evidence/P-006-device-bind.png`
  5. 捕获 `POST /api/family/devices/bind` 响应

- **期望结果**:
  1. 设备绑定成功，授权记录出现在设备列表中
  2. API 返回 200，包含绑定信息

- **后置 DB 验证**:
  1. ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT id, device_id, child_id FROM device_authorization WHERE device_id='test-device-cici-001';"
     ```
  2. 期望: `device_authorization` 表有该设备记录且 `child_id` 指向 cici

- **关联不变量**: 无

---

#### P-007 创建任务模板

- **前置条件**:
  1. P-001 家长已登录
  2. 家庭已有孩子 cici

- **测试步骤**:
  1. 导航到任务模板管理页面 `/parent/templates`
  2. 点击「创建模板」
  3. 填写模板信息:
     - 名称: `整理房间`
     - 说明: `每天整理自己的房间，保持整洁`
     - 积分: `10`
     - 任务类型: `DAILY`（每日）
     - 分类: `生活习惯`
  4. 提交保存
  5. 截图保存到 `reports/evidence/P-007-template-create.png`
  6. 捕获 `POST /api/task-templates` 响应

- **期望结果**:
  1. 模板创建成功，出现在模板列表中
  2. API 返回 201，包含模板详情（id、name、points、type 等）

- **后置 DB 验证**:
  1. ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT id, name, points, type FROM task_template WHERE name='整理房间';"
     ```
  2. 期望: `task_template` 表有对应记录

- **关联不变量**: 无

---

#### P-008 模板查询与更新

- **前置条件**:
  1. P-007 任务模板已创建
  2. 家长已登录

- **测试步骤**:
  1. 在模板管理页面查看模板列表
  2. 截图保存到 `reports/evidence/P-008-template-list.png`
  3. 按分类/关键字搜索模板
  4. 点击「整理房间」模板进入编辑
  5. 修改积分为 `15`，提交保存
  6. 截图保存到 `reports/evidence/P-008-template-update.png`
  7. 捕获 `PUT /api/task-templates/{id}` 响应

- **期望结果**:
  1. 模板列表展示正确，搜索正常
  2. 模板更新成功，积分变为 15
  3. API 返回 200

- **后置 DB 验证**:
  1. ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT points FROM task_template WHERE name='整理房间';"
     ```
  2. 期望: points = 15

- **关联不变量**: 无

---

#### P-009 模板启停与删除

- **前置条件**:
  1. P-007 模板已创建
  2. 家长已登录

- **测试步骤**:
  1. 在模板列表中找到「整理房间」模板
  2. 点击「停用」开关，确认状态变为「已停用」
  3. 截图保存到 `reports/evidence/P-009-template-disable.png`
  4. 捕获 `PUT /api/task-templates/{id}/enabled` 响应（enabled=false）
  5. 点击「启用」恢复
  6. 捕获 `PUT /api/task-templates/{id}/enabled` 响应（enabled=true）
  7. 点击「删除」模板
  8. 截图保存到 `reports/evidence/P-009-template-delete.png`
  9. 捕获 `DELETE /api/task-templates/{id}` 响应

- **期望结果**:
  1. 启停操作正常，状态切换正确
  2. 软删除后模板不在主列表中
  3. API 均返回 200

- **后置 DB 验证**:
  1. ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT enabled, deleted FROM task_template WHERE name='整理房间';"
     ```
  2. 期望: deleted = true

- **关联不变量**: 无

---

#### P-010 创建与批量分配任务（单任务 + 批量 + 日历）

- **前置条件**:
  1. 有启用的任务模板（需在 P-009 删除之前执行，或重新创建模板）
  2. 孩子 cici 已存在
  3. 家长已登录

- **测试步骤**:
  1. 导航到任务分配页面 `/parent/tasks`
  2. 点击「分配任务」
  3. 选择: 模板 `整理房间`、孩子 `cici`、任务日期 `今天`、积分为模板默认（15）
  4. 提交单任务分配
  5. 截图保存到 `reports/evidence/P-010-assign-task.png`
  6. 捕获 `POST /api/task-assignments` 响应
  7. 使用批量分配功能，选择模板 `整理房间`、孩子 cici、日期范围（未来 3 天）
  8. 提交批量分配
  9. 截图保存到 `reports/evidence/P-010-batch-assign.png`
  10. 捕获 `POST /api/task-assignments/batch` 响应
  11. 导航到日历视图（`/parent/tasks` 日历模式）
  12. 截图保存到 `reports/evidence/P-010-calendar.png`
  13. 捕获 `GET /api/task-assignments/calendar` 响应

- **期望结果**:
  1. 单任务分配成功，status = `ASSIGNED`
  2. 批量分配成功，多个 assignment 被创建
  3. 日历视图展示任务分布
  4. API 均返回 200/201

- **后置 DB 验证**:
  1. ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT count(*) FROM task_assignment WHERE status='ASSIGNED';"
     ```
  2. 期望: assignment 数量正确

- **关联不变量**: 无

---

#### P-011 取消任务分配

- **前置条件**:
  1. P-009/010 有已分配的任务（status=ASSIGNED）
  2. 家长已登录

- **测试步骤**:
  1. 在任务列表中找到一条 status=ASSIGNED 的任务
  2. 点击「取消分配」
  3. 输入取消原因（如「测试取消」）
  4. 确认取消
  5. 截图保存到 `reports/evidence/P-011-cancel-task.png`
  6. 捕获 `POST /api/task-assignments/{id}/cancel` 响应

- **期望结果**:
  1. 任务状态变为 `CANCELLED`
  2. 该任务不再出现在待办列表中
  3. API 返回 200

- **后置 DB 验证**:
  1. ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT id, status, cancel_reason FROM task_assignment WHERE status='CANCELLED';"
     ```
  2. 期望: status = CANCELLED，cancel_reason 非空

- **关联不变量**: 无

---

#### P-012 生成 recurring 任务

- **前置条件**:
  1. 有启用的模板且设置了 recurrence 规则
  2. 家长已登录

- **测试步骤**:
  1. 导航到任务管理页
  2. 使用「生成定期任务」功能
  3. 选择模板和 recurrence 规则（每周一、三、五）
  4. 提交生成
  5. 截图保存到 `reports/evidence/P-012-recurring.png`
  6. 捕获 `POST /api/task-assignments/generate-recurring` 响应

- **期望结果**:
  1. 定期任务生成成功，未来日期出现相应 assignment
  2. API 返回 200，包含生成的 assignment 列表

- **后置 DB 验证**:
  1. ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT count(*) FROM task_assignment WHERE due_date > CURRENT_DATE;"
     ```
  2. 期望: 生成了未来的 assignment

- **关联不变量**: 无

---

#### P-013 待审核查询与审核通过

- **前置条件**:
  1. 孩子 cici 已提交任务（需在 child 端先提交，详见 C-003）
  2. 家长已登录

- **测试步骤**:
  1. 导航到审核页面 `/parent/reviews`
  2. 截图保存到 `reports/evidence/P-013-pending-review.png`
  3. 确认待审核列表中包含 cici 提交的任务
  4. 捕获 `GET /api/task-review/pending` 响应
  5. 在待审核列表中找到 cici 提交的任务，点击「通过」
  6. 截图保存到 `reports/evidence/P-013-approve.png`
  7. 捕获 `POST /api/task-review/{attemptId}/approve` 响应
  8. 确认该任务从待审核列表中消失

- **期望结果**:
  1. 待审核列表展示孩子提交的任务尝试（task attempt）
  2. 审核通过后任务状态变为 `APPROVED`
  3. 孩子积分入账（`points_balance` 增加对应金额）
  4. API 返回 200，包含 `reviewId`、`decision: "APPROVED"`

- **后置 DB 验证**:
  1. 验证积分入账:
     ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT cp.nickname, pb.balance, pb.total_earned FROM points_balance pb JOIN child_profile cp ON pb.child_id = cp.id WHERE cp.nickname='cici';"
     ```
  2. 验证审核记录:
     ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT id, attempt_id, decision, reviewer_id FROM task_review WHERE decision='APPROVED';"
     ```
  3. 验证积分流水:
     ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT id, child_id, type, amount, balance_after FROM points_ledger WHERE type='EARNED';"
     ```

- **关联不变量**: 本次审核后执行 I-001（points-non-negative）验证

---

#### P-014 审核驳回与查询审核历史

- **前置条件**:
  1. 有另一个等待审核的任务（child 已提交，或重新提交）
  2. 家长已登录

- **测试步骤**:
  1. 在待审核列表中找到另一个提交
  2. 点击「驳回」
  3. 填写驳回原因（如「需要更多细节」）
  4. 确认驳回
  5. 截图保存到 `reports/evidence/P-014-reject.png`
  6. 捕获 `POST /api/task-review/{attemptId}/reject` 响应
  7. 切换到「历史记录」视图
  8. 截图保存到 `reports/evidence/P-014-review-history.png`
  9. 按条件筛选（如按孩子筛选、按决策类型筛选）
  10. 捕获 `GET /api/task-review/history` 响应

- **期望结果**:
  1. 驳回后任务状态变为 `REJECTED`，积分不入账
  2. API 返回 200，包含 `decision: "REJECTED"`、`reason`
  3. 审核历史列表展示所有已审核的记录（通过/驳回）
  4. 筛选功能正常，API 返回 200，分页正确

- **后置 DB 验证**:
  1. ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT decision, count(*) FROM task_review GROUP BY decision;"
     ```
  2. 验证积分未变化:
     ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT cp.nickname, pb.balance FROM points_balance pb JOIN child_profile cp ON pb.child_id = cp.id WHERE cp.nickname='cici';"
     ```

- **关联不变量**: 无

---

#### P-015 查询积分余额与流水

- **前置条件**:
  1. 孩子 cici 已有积分变化（审核通过 + 积分调整）
  2. 家长已登录

- **测试步骤**:
  1. 导航到积分管理页面 `/parent/points`
  2. 查看 cici 的积分余额
  3. 截图保存到 `reports/evidence/P-015-balance.png`
  4. 查看积分流水列表
  5. 截图保存到 `reports/evidence/P-015-ledger.png`
  6. 捕获 `GET /api/points/balance/{childId}` 和 `GET /api/points/ledger/{childId}` 响应

- **期望结果**:
  1. 积分余额正确展示
  2. 流水列表展示所有积分变动条目（每笔有类型、金额、变动后余额、时间戳）
  3. API 返回 200

- **后置 DB 验证**:
  1. ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT pb.child_id, pb.balance, pb.total_earned, pl.type, pl.amount, pl.balance_after FROM points_balance pb JOIN points_ledger pl ON pb.child_id = pl.child_id WHERE pb.child_id = (SELECT id FROM child_profile WHERE nickname='cici') ORDER BY pl.id;"
     ```

- **关联不变量**: 本次查询后执行 I-002（ledger-balance-consistency）验证

---

#### P-016 积分调整

- **前置条件**:
  1. 孩子 cici 有积分余额（至少 > 0）
  2. 家长已登录

- **测试步骤**:
  1. 在积分管理页面找到 cici 的积分调整功能
  2. 执行正向调整: 增加 `5` 分，原因「测试加分」
  3. 截图保存到 `reports/evidence/P-016-adjustment-plus.png`
  4. 确认余额增加 5
  5. 执行负向调整: 减少 `3` 分，原因「测试扣分」
  6. 截图保存到 `reports/evidence/P-016-adjustment-minus.png`
  7. 确认余额减少 3（净增 2）
  8. 捕获 `POST /api/points/adjustments` 响应

- **期望结果**:
  1. 正向调整成功，余额增加
  2. 负向调整成功，余额减少（余额仍 ≥ 0）
  3. 流水可追溯（type 为 `ADJUSTMENT`）
  4. API 返回 200

- **后置 DB 验证**:
  1. ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT pl.id, pl.type, pl.amount, pl.balance_after, pl.reason FROM points_ledger pl JOIN child_profile cp ON pl.child_id = cp.id WHERE cp.nickname='cici' AND pl.type='ADJUSTMENT' ORDER BY pl.id;"
     ```
  2. 验证余额非负:
     ```bash
     python3 reports/helpers/db_checks.py points-non-negative
     ```

- **关联不变量**: 本次调整后执行 I-001（points-non-negative）和 I-002（ledger-balance-consistency）验证

---

#### P-017 创建、查询与更新奖品

- **前置条件**:
  1. P-001 家长已登录
  2. 家庭已存在

- **测试步骤**:
  1. 导航到奖品管理页面 `/parent/prizes`
  2. 点击「创建奖品」
  3. 填写奖品信息:
     - 名称: `玩具车`
     - 描述: `一辆炫酷的红色玩具车`
     - 所需积分: `20`
     - 库存数量: `5`
  4. 提交
  5. 截图保存到 `reports/evidence/P-017-prize-create.png`
  6. 捕获 `POST /api/prizes` 响应
  7. 确认奖品出现在列表中，查看列表
  8. 截图保存到 `reports/evidence/P-017-prize-list.png`
  9. 点击「玩具车」进入编辑
  10. 修改所需积分为 `25`，提交保存
  11. 截图保存到 `reports/evidence/P-017-prize-update.png`
  12. 捕获 `PUT /api/prizes/{id}` 响应

- **期望结果**:
  1. 奖品创建成功，出现在奖品列表中
  2. 奖品列表展示正确
  3. 更新成功，积分变为 25
  4. API 返回 201/200

- **后置 DB 验证**:
  1. ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT id, name, points_cost, stock FROM prize WHERE name='玩具车';"
     ```
  2. 期望: points_cost = 25

- **关联不变量**: 无

---

#### P-018 库存调整、删除奖品与兑换核销

- **前置条件**:
  1. P-017 奖品已创建且孩子有足够积分
  2. 家长已登录
  3. 孩子 cici 已提交兑换申请（需先执行 C-006）

- **测试步骤**:
  1. 在奖品详情页找到库存管理功能
  2. 将库存调整为 `10`（+5）
  3. 截图保存到 `reports/evidence/P-018-stock-adjust.png`
  4. 捕获 `PUT /api/prizes/{id}/stock` 响应
  5. 导航到兑换管理页面 `/parent/exchanges`
  6. 截图保存到 `reports/evidence/P-018-exchange-list.png`
  7. 查看兑换申请列表，确认有 cici 的申请
  8. 捕获 `GET /api/exchanges` 响应
  9. 找到待核销的申请，点击「核销」
  10. 截图保存到 `reports/evidence/P-018-fulfill.png`
  11. 捕获 `POST /api/exchanges/{id}/fulfill` 响应
  12. 验证订单状态变为 `FULFILLED`，库存扣减

- **期望结果**:
  1. 库存调整成功（stock = 10）
  2. 兑换申请列表正确展示
  3. 核销成功，订单状态变为 `FULFILLED`
  4. 奖品库存扣减
  5. API 均返回 200

- **后置 DB 验证**:
  1. 验证库存:
     ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT stock FROM prize WHERE name='玩具车';"
     ```
  2. 验证订单状态:
     ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT id, status, fulfilled_at FROM exchange WHERE status='FULFILLED';"
     ```
  3. 验证快照不可变:
     ```bash
     python3 reports/helpers/db_checks.py exchange-snapshot-immutable
     ```

- **关联不变量**: 本次核销后执行 I-003（prize-stock-non-negative）、I-004（exchange-snapshot-immutable）验证

---

#### P-019 核销幂等验证

- **前置条件**:
  1. P-018 已核销的兑换订单（status=FULFILLED）
  2. 家长仍登录

- **测试步骤**:
  1. 找到已核销的订单
  2. 再次点击「核销」（如果 UI 允许，或直接调 API）
  3. 截图保存到 `reports/evidence/P-019-fulfill-dup.png`
  4. 捕获 `POST /api/exchanges/{id}/fulfill` 响应

- **期望结果**:
  1. 重复核销操作无效
  2. 订单状态仍为 `FULFILLED`（不重复扣减库存）
  3. API 返回 200 或幂等错误提示

- **后置 DB 验证**:
  1. ```bash
     STOCK_BEFORE=$(psql -h localhost -p 35432 -U cutegoals -d cutegoals -tAc "SET search_path TO cutegoals; SELECT stock FROM prize WHERE name='玩具车';")
     # 执行重复核销操作...
     STOCK_AFTER=$(psql -h localhost -p 35432 -U cutegoals -d cutegoals -tAc "SET search_path TO cutegoals; SELECT stock FROM prize WHERE name='玩具车';")
     if [ "$STOCK_BEFORE" = "$STOCK_AFTER" ]; then echo "IDEMPOTENT: OK"; else echo "IDEMPOTENT: FAIL"; fi
     ```

- **关联不变量**: 无

---

#### P-020 盲盒池创建、条目管理与查询

- **前置条件**:
  1. P-001 家长已登录
  2. 至少有一个奖品可用于盲盒条目

- **测试步骤**:
  1. 导航到盲盒管理页面 `/parent/blind-boxes`
  2. 点击「创建盲盒池」
  3. 填写: 名称 `幸运盲盒`、描述 `随机获得一个惊喜奖品`、消耗积分 `30`
  4. 提交
  5. 截图保存到 `reports/evidence/P-020-blindbox-create.png`
  6. 捕获 `POST /api/blind-boxes` 响应
  7. 添加条目: 选择奖品（如「玩具车」）、权重 `50`
  8. 截图保存到 `reports/evidence/P-020-blindbox-add-item.png`
  9. 查看盲盒池列表
  10. 截图保存到 `reports/evidence/P-020-blindbox-list.png`
  11. 点击查看盲盒详情
  12. 截图保存到 `reports/evidence/P-020-blindbox-detail.png`
  13. 获取 `GET /api/blind-boxes/{id}/candidates` 响应，查看各奖品概率
  14. 捕获 `GET /api/blind-boxes/{id}` 和 `GET /api/blind-boxes/{id}/candidates` 响应

- **期望结果**:
  1. 盲盒池创建成功
  2. 条目添加成功
  3. 列表和详情展示正确
  4. candidates 返回各奖品的概率分布
  5. API 均返回 200/201

- **后置 DB 验证**:
  1. ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT bp.id, bp.name, bp.cost_points, bi.prize_id, bi.weight FROM blind_box_pool bp LEFT JOIN blind_box_item bi ON bp.id = bi.pool_id WHERE bp.name='幸运盲盒';"
     ```

- **关联不变量**: 无

---

### C. Child 端（7 用例）

#### C-001 设备绑定

- **前置条件**:
  1. P-006 设备已授权（`test-device-cici-001` 已绑定）
  2. 孩子 cici 档案已创建且关联到该设备
  3. 使用隔离的 child browser context

- **测试步骤**:
  1. 在 child browser context 中打开 `http://localhost:5173/child/bind`
  2. 输入设备 ID `test-device-cici-001`
  3. 提交设备绑定
  4. 截图保存到 `reports/evidence/C-001-bind.png`
  5. 确认设备绑定成功后跳转到孩子选择页面（或直接显示 cici）

- **期望结果**:
  1. 设备绑定成功，显示可用的孩子档案列表
  2. 列表中包含 `cici`

- **后置 DB 验证**:
  1. ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT da.device_id, da.child_id, cp.nickname FROM device_authorization da JOIN child_profile cp ON da.child_id = cp.id WHERE da.device_id='test-device-cici-001';"
     ```

- **关联不变量**: 无

---

#### C-002 PIN 登录

- **前置条件**:
  1. C-001 设备已绑定，孩子列表已加载
  2. 孩子 cici 的 PIN 为 `180614`

- **测试步骤**:
  1. 在 child context 中点击 cici
  2. 输入 PIN `180614`
  3. 点击登录
  4. 截图保存到 `reports/evidence/C-002-login.png`
  5. 捕获 `POST /api/auth/child/login` 响应 → `reports/evidence/network/C-002-response.json`
  6. 确认进入孩子首页

- **期望结果**:
  1. PIN 输入验证通过，进入孩子首页
  2. API 响应中不应包含 PIN 明文
  3. 浏览器存储中不应有 PIN 明文

- **后置 DB 验证**:
  1. ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT id, nickname FROM child_profile WHERE nickname='cici';"
     ```

- **关联不变量**: 无

---

#### C-003 任务列表与提交任务

- **前置条件**:
  1. C-002 孩子已登录
  2. 家长已分配了今日任务给 cici（status=ASSIGNED）

- **测试步骤**:
  1. 在孩子首页查看今日待完成任务列表
  2. 截图保存到 `reports/evidence/C-003-tasks.png`
  3. 确认「整理房间」任务出现在待完成列表中
  4. 点击「提交完成」
  5. 填写提交内容（如「已完成整理」）
  6. 提交
  7. 截图保存到 `reports/evidence/C-003-submit.png`
  8. 捕获 `POST /api/task-review/submissions` 响应

- **期望结果**:
  1. 今日任务列表展示正确，包含“整理房间”
  2. 提交后任务状态变为 `PENDING_REVIEW`
  3. API 返回 200/201，包含 attempt 信息

- **后置 DB 验证**:
  1. ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT ta.id, ta.status, taa.id AS attempt_id, taa.status AS attempt_status FROM task_assignment ta LEFT JOIN task_attempt taa ON ta.id = taa.assignment_id WHERE ta.child_id = (SELECT id FROM child_profile WHERE nickname='cici') ORDER BY taa.id DESC LIMIT 1;"
     ```
  2. 期望: task_attempt.status = 'PENDING_REVIEW'

- **关联不变量**: 无

---

#### C-004 查询积分余额与流水

- **前置条件**:
  1. C-002 孩子已登录
  2. 家长已审核通过至少一个任务（积分已入账）

- **测试步骤**:
  1. 在孩子端导航到积分页面
  2. 截图保存到 `reports/evidence/C-004-points.png`
  3. 确认积分余额正确展示
  4. 查看积分流水记录
  5. 截图保存到 `reports/evidence/C-004-ledger.png`
  6. 捕获 `GET /api/points/balance/{childId}` 响应

- **期望结果**:
  1. 积分余额展示正确（与家长端看到的一致）
  2. 流水列表展示所有变动（审核通过入账记录可见）
  3. 孩子端仅能看到自己的积分

- **后置 DB 验证**:
  1. ```bash
     python3 reports/helpers/db_checks.py ledger-balance-consistency
     ```

- **关联不变量**: 本次查询后执行 I-002 验证

---

#### C-005 奖品商城浏览

- **前置条件**:
  1. C-002 孩子已登录
  2. 家长已创建至少一个奖品（如「玩具车」20 积分）

- **测试步骤**:
  1. 导航到奖品商城页面 `/child/prizes`
  2. 截图保存到 `reports/evidence/C-005-prize-list.png`
  3. 确认奖品列表展示（含奖品名称、所需积分、库存状态）
  4. 点击奖品查看详情
  5. 捕获 `GET /api/prizes/available` 响应

- **期望结果**:
  1. 奖品列表正确展示
  2. 仅展示已启用、有库存的奖品
  3. 孩子端不显示管理功能

- **后置 DB 验证**:
  1. ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT name, points_cost, stock FROM prize WHERE enabled=true AND deleted=false AND stock > 0;"
     ```

- **关联不变量**: 无

---

#### C-006 直接兑换奖品

- **前置条件**:
  1. C-002 孩子已登录
  2. 孩子积分 ≥ 兑换所需积分（如 20 积分兑「玩具车」）
  3. 奖品库存 > 0

- **测试步骤**:
  1. 在奖品列表中选择「玩具车」
  2. 点击「立即兑换」
  3. 确认兑换
  4. 截图保存到 `reports/evidence/C-006-exchange.png`
  5. 捕获 `POST /api/exchanges/direct` 响应
  6. 确认兑换成功提示

- **期望结果**:
  1. 兑换成功，生成兑换订单（status=`PENDING`）
  2. 孩子积分扣减相应金额
  3. 库存扣减
  4. API 返回 201，包含订单详情、snapshot、积分变化

- **后置 DB 验证**:
  1. 验证订单:
     ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT e.id, e.type, e.status, e.cost_points, e.idempotency_key FROM exchange e WHERE e.child_id = (SELECT id FROM child_profile WHERE nickname='cici') AND e.type='DIRECT' ORDER BY e.id DESC LIMIT 1;"
     ```
  2. 验证积分扣减:
     ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT pl.id, pl.type, pl.amount, pl.balance_after FROM points_ledger pl WHERE pl.child_id = (SELECT id FROM child_profile WHERE nickname='cici') AND pl.type='EXCHANGE' ORDER BY pl.id DESC LIMIT 1;"
     ```
  3. 验证快照:
     ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT id, exchange_id, prize_name, points_cost FROM exchange_snapshot WHERE exchange_id = (SELECT max(id) FROM exchange WHERE child_id = (SELECT id FROM child_profile WHERE nickname='cici'));"
     ```

- **关联不变量**: 本次兑换后执行 I-001（points-non-negative）、I-002（ledger-balance-consistency）、I-003（prize-stock-non-negative）、I-004（exchange-snapshot-immutable）验证

---

#### C-007 盲盒兑换

- **前置条件**:
  1. C-002 孩子已登录
  2. 孩子积分 ≥ 盲盒消耗积分（如 30 分）
  3. P-020 盲盒池已创建且包含条目

- **测试步骤**:
  1. 导航到盲盒页面 `/child/blind-boxes`
  2. 截图保存到 `reports/evidence/C-007-blindbox-list.png`
  3. 选择「幸运盲盒」
  4. 点击「抽取」
  5. 确认抽取
  6. 截图保存到 `reports/evidence/C-007-blindbox-result.png`
  7. 捕获 `POST /api/exchanges/blind-box` 响应
  8. 查看抽到的奖品结果

- **期望结果**:
  1. 盲盒抽取成功，消耗积分
  2. 返回随机奖品结果
  3. 积分扣减，订单生成（type=`BLIND_BOX`）
  4. 快照记录中保留抽取时的概率信息和抽到奖品名称

- **后置 DB 验证**:
  1. ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT e.id, e.type, e.status, e.cost_points, e.result_prize_id, es.drawn_prize_name, es.drawn_probability FROM exchange e JOIN exchange_snapshot es ON e.id = es.exchange_id WHERE e.child_id = (SELECT id FROM child_profile WHERE nickname='cici') AND e.type='BLIND_BOX' ORDER BY e.id DESC LIMIT 1;"
     ```

- **关联不变量**: 本次兑换后执行 I-001、I-002、I-004 验证

---

### D. Cross-cutting（1 用例）

#### X-001 完整家庭工作流

- **前置条件**:
  1. 管理员已初始化系统（A-001）
  2. 家长已登录（P-001）
  3. 孩子 cici 已创建并绑定设备（P-004, P-006）
  4. 任务模板已创建（P-007）
  5. 奖品已创建且孩子积分足够（P-017, P-018）

- **测试步骤**（涉及三个 browser context 切换）:
  1. **[child context]** 孩子 cici PIN 登录（C-002）
  2. **[child context]** 查看今日任务，提交完成（C-003）
  3. **[child context]** 截图保存到 `reports/evidence/X-001-child-submit.png`
   4. **[parent context]** 家长登录，查看待审核（P-013）
   5. **[parent context]** 审核通过该任务（P-013）
  6. **[parent context]** 截图保存到 `reports/evidence/X-001-parent-approve.png`
  7. **[parent context]** 验证积分已入账
  8. **[child context]** 孩子查看积分余额确认入账
  9. **[child context]** 孩子兑换「玩具车」奖品（C-006）
  10. **[child context]** 截图保存到 `reports/evidence/X-001-child-exchange.png`
  11. **[parent context]** 家长查看兑换申请列表（P-018）
  12. **[parent context]** 核销兑换（P-018）
  13. **[parent context]** 截图保存到 `reports/evidence/X-001-parent-fulfill.png`

- **期望结果**（全链路一致）:
  1. 任务提交流程完整: ASSIGNED → PENDING_REVIEW → APPROVED
  2. 积分入账正确: 审核通过 +15 分，兑换扣减 -20 分，净余额正确
  3. 兑换流程完整: PENDING → FULFILLED
  4. 库存正确扣减

- **后置 DB 验证**（全链路一致性）:
  1. 积分流水完整（应有审核 EARNED + 兑换 EXCHANGE 至少两条流水）:
     ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT pl.type, pl.amount, pl.balance_after FROM points_ledger pl JOIN child_profile cp ON pl.child_id = cp.id WHERE cp.nickname='cici' ORDER BY pl.id;"
     ```
  2. 兑换订单状态:
     ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT e.id, e.status, e.fulfilled_at FROM exchange e JOIN child_profile cp ON e.child_id = cp.id WHERE cp.nickname='cici';"
     ```
  3. 库存已扣减:
     ```bash
     psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SET search_path TO cutegoals; SELECT name, stock FROM prize WHERE name='玩具车';"
     ```
  4. 执行全部不变量检查:
     ```bash
     python3 reports/helpers/db_checks.py all
     ```

- **关联不变量**: X-001 完成后执行全部 6 条不变量检查（I-001 ~ I-006）

---

### E. Invariants（6 用例）

#### I-001 积分余额非负

- **说明**: 所有 `points_balance.balance` 和 `points_balance.total_earned` 必须 ≥ 0

- **触发场景**: 每次积分变动操作后（审核通过 P-015、积分调整 P-019、兑换扣减 C-006、盲盒 C-007）

- **验证命令**:
  ```bash
  python3 reports/helpers/db_checks.py points-non-negative
  ```

- **期望输出**:
  ```json
  {
    "check": "points-non-negative",
    "passed": true,
    "data": {
      "negative_balance": 0,
      "negative_total_earned": 0,
      ...
    }
  }
  ```

- **关联 Bug 检查**: 如果 `negative_balance > 0`，记录 Critical bug（核心不变量破坏）

---

#### I-002 积分流水与余额一致

- **说明**: `points_ledger.balance_after` 必须等于该孩子此前所有 `amount` 累计值

- **触发场景**: 每次积分变动操作后（P-015、P-019、C-006、C-007）

- **验证命令**:
  ```bash
  python3 reports/helpers/db_checks.py ledger-balance-consistency
  ```

- **期望输出**:
  ```json
  {
    "check": "ledger-balance-consistency",
    "passed": true,
    "data": {
      "total_ledger_rows": 1,
      "balance_mismatches": 0
    }
  }
  ```

- **关联 Bug 检查**: 如果 `balance_mismatches > 0`，记录 Critical bug

---

#### I-003 奖品库存非负

- **说明**: 所有未删除奖品的 `stock` 必须 ≥ 0

- **触发场景**: 创建奖品（P-017）、库存调整（P-018）、兑换（C-006）、核销（P-018）

- **验证命令**:
  ```bash
  python3 reports/helpers/db_checks.py prize-stock-non-negative
  ```

- **期望输出**:
  ```json
  {
    "check": "prize-stock-non-negative",
    "passed": true,
    "data": {
      "negative_stock_rows": 0,
      ...
    }
  }
  ```

- **关联 Bug 检查**: 如果 `negative_stock_rows > 0`，记录 Critical bug

---

#### I-004 兑换快照不可变

- **说明**: `exchange_snapshot` 表在创建后不应被 UPDATE/DELETE（快照 hash 不变）

- **触发场景**: 每次兑换操作后（C-006、C-007）执行两次检查对比 hash

- **验证命令**:
  ```bash
  python3 reports/helpers/db_checks.py exchange-snapshot-immutable
  ```
  （记录返回的 `current_snapshot_table_hash`，下次兑换后再次运行并对比 hash）

- **期望输出**:
  ```json
  {
    "check": "exchange-snapshot-immutable",
    "passed": true,
    "data": {
      "current_snapshot_table_hash": "<同一 hash 值>",
      ...
    }
  }
  ```

- **关联 Bug 检查**: 如果两次 hash 不一致（在无新增兑换操作时），记录 Critical bug

---

#### I-005 审核记录不可变

- **说明**: `task_review` 的审核记录创建后不可被 UPDATE/DELETE

- **触发场景**: 每次审核操作后（P-015 通过、P-016 驳回）

- **验证命令**:
  ```bash
  python3 reports/helpers/db_checks.py task-review-immutable
  ```

- **期望输出**:
  ```json
  {
    "check": "task-review-immutable",
    "passed": true,
    "data": {
      "task_review_update_or_delete_events": 0
    }
  }
  ```

- **关联 Bug 检查**: 如果 `task_review_update_or_delete_events > 0`，记录 High bug

---

#### I-006 ledger 无 UPDATE/DELETE

- **说明**: `points_ledger` 表不可变（审计日志中不能有对其的 UPDATE/DELETE 事件）

- **触发场景**: 每次积分操作后（P-015、P-019、C-006、C-007）

- **验证命令**:
  ```bash
  python3 reports/helpers/db_checks.py ledger-no-update-delete
  ```

- **期望输出**:
  ```json
  {
    "check": "ledger-no-update-delete",
    "passed": true,
    "data": {
      "ledger_update_or_delete_events": 0,
      "current_ledger_rows": 1
    }
  }
  ```

- **关联 Bug 检查**: 如果 `ledger_update_or_delete_events > 0`，记录 Critical bug

---

### F. Security（6 用例）

#### S-001 日志中无明文密码

- **说明**: 后端日志中不应出现管理员/家长密码 `117315Akers` 或其变形

- **测试场景**: 以下操作后扫描后端日志:
  - A-001 初始化（发送密码）
  - A-002 管理员登录
  - P-001 家长登录
  - P-015/P-016 审核操作（可能涉及密码验证）

- **扫描命令**:
  ```bash
  python3 reports/helpers/security_checks.py backend-log reports/backend.log
  ```

- **判定标准**: `findings_count = 0`（不包含密码 `117315Akers` 或其任何子串）

- **失败处理**: 任何命中 → 记录 Critical bug，明确指出日志行号

---

#### S-002 日志中无明文 PIN

- **说明**: 后端日志中不应出现孩子 PIN `180614` 或其变形

- **测试场景**: 以下操作后扫描后端日志:
  - P-004 创建孩子（提交 PIN）
  - C-002 孩子 PIN 登录
  - 任何涉及孩子档案更新的操作

- **扫描命令**:
  ```bash
  python3 reports/helpers/security_checks.py backend-log reports/backend.log
  ```

- **判定标准**: `findings_count = 0`（不包含 `pin=180614`、`"pin":"180614"` 等模式）

- **失败处理**: 任何命中 → 记录 Critical bug

---

#### S-003 日志/API 响应中无 JWT 完整 token

- **说明**: API 响应和后端日志中不应包含完整的 JWT token（以 `eyJ` 开头、三段 base64）

- **测试场景**: 以下登录操作后检查:
  - A-001 初始化响应
  - A-002 管理员登录 API 响应
  - P-001 家长登录 API 响应
  - C-002 孩子 PIN 登录 API 响应

- **扫描命令**（API 响应）:
  ```bash
  python3 reports/helpers/security_checks.py api-response reports/evidence/network/
  ```
  （扫描全部 `reports/evidence/network/` 目录下的 JSON 响应文件）

- **扫描命令**（后端日志）:
  ```bash
  python3 reports/helpers/security_checks.py backend-log reports/backend.log
  ```

- **判定标准**: API 响应和后端日志中均不包含 JWT token 完整字符串

- **失败处理**: 任何命中 → 记录 Critical bug

---

#### S-004 日志/API 响应中无完整手机号

- **说明**: API 响应和后端日志中手机号应脱敏（`136****49114`），不允许出现完整 11 位手机号 `13600049114`

- **测试场景**: 所有涉及手机号展示的页面:
  - A-002 / A-003 admin 登录与账号管理
  - P-001 / P-005 家长登录与账号管理
  - 审计日志（可能记录操作者手机号）

- **扫描命令**:
  ```bash
  python3 reports/helpers/security_checks.py api-response reports/evidence/network/
  ```
  ```bash
  python3 reports/helpers/security_checks.py backend-log reports/backend.log
  ```

- **判定标准**: 完整手机号 `13600049114` 不出现在 API 响应和后端日志中（脱敏的 `136****49114` 允许）

- **失败处理**: 任何命中完整手机号 → 记录 High bug

---

#### S-005 浏览器存储中无敏感明文

- **说明**: localStorage / sessionStorage / cookie 中不应存储明文密码、PIN、完整 JWT token

- **测试场景**: 以下操作后通过 agent-browser 读取浏览器存储:
  - A-001 初始化后（admin context）
  - A-002 管理员登录后（admin context）
  - P-001 家长登录后（parent context）
  - C-002 孩子 PIN 登录后（child context）

- **采集方法**:
  1. 通过 agent-browser 执行:
     ```javascript
     JSON.stringify({localStorage: {...localStorage}, sessionStorage: {...sessionStorage}})
     ```
  2. 读取 `document.cookie`
  3. 保存到 `reports/evidence/storage/S-005-admin-storage.txt`、`S-005-parent-storage.txt`、`S-005-child-storage.txt`

- **扫描命令**:
  ```bash
  python3 reports/helpers/security_checks.py browser-storage reports/evidence/storage/S-005-admin-storage.txt
  python3 reports/helpers/security_checks.py browser-storage reports/evidence/storage/S-005-parent-storage.txt
  python3 reports/helpers/security_checks.py browser-storage reports/evidence/storage/S-005-child-storage.txt
  ```

- **判定标准**: 浏览器存储文件中不包含 `117315Akers`、`180614`、`eyJ` 等敏感字符串

- **失败处理**: 任何命中 → 记录 Critical bug（敏感数据在客户端持久化）

---

#### S-006 认证 token 不出现在 URL 参数中

- **说明**: JWT token、密码、PIN 等不应出现在 URL query 参数或路径中

- **测试场景**: 检查所有 agent-browser 访问过程中捕获的请求 URL

- **检查方法**:
  1. 收集 `reports/evidence/network/` 中所有 JSON 的请求 URL（从 sentinel 或代理日志中提取）
  2. 检查 URL 中是否包含 `token=`、`access_token=`、`jwt=`、`password=`、`pin=` 等参数
  3. 检查 URL 路径中是否包含 base64 编码的 token

- **扫描命令**:
  ```bash
  grep -rEl '(token=|access_token=|jwt=|password=|pin=)' reports/evidence/network/ 2>/dev/null || echo "No token in URL params"
  ```

- **判定标准**: 所有 URL 中不包含认证凭据参数

- **失败处理**: 任何命中 → 记录 High bug

---

## 执行顺序

严格按以下阶段顺序执行，每阶段内按用例 ID 顺序执行：

```
A-001 ~ A-005 → P-001 ~ P-020 → C-001 ~ C-007 → X-001 → I-001 ~ I-006 → S-001 ~ S-006
```

## 执行说明

1. **三端 context 隔离**: admin / parent / child 使用独立的 browser context，cookie/localStorage 不串扰
2. **不变量验证时机**: 每个 I 用例在指定的业务操作后立即执行，I-001~I-006 均在 X-001 完成后统一执行一次全量检查
3. **安全验证时机**: S-001~S-006 在全部功能用例执行完成后进行最终扫描（日志扫描在整个测试期间累积）
4. **证据保存**: 每用例截图 + API 响应 JSON + DB 查询结果均保存到 `reports/evidence/` 目录
5. **进度追踪**: 每用例完成后更新 `reports/test-progress.md`

## 用例状态码

| 状态码 | 含义 | 处理方式 |
|--------|------|---------|
| PASS | 完全符合期望 | 继续执行下一用例 |
| FAIL | 行为不符合期望 | 创建 `reports/bug-NNN-*.md` 记录缺陷，继续执行（除非阻塞性） |
| BLOCKED | 前置依赖失败 | 记录阻塞原因和关联 bug ID，跳过后继续 |
| SKIPPED | 因前序 bug 无法执行 | 记录跳过原因和关联 bug ID |
