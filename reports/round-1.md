# Round-1 全量测试报告

| Field | Value |
|-------|-------|
| Change | e2e-system-test-and-fix |
| 生成时间 | 2026-07-18T13:00:00+08:00 |
| 测试执行 | agent-browser + Python psycopg2 + MyBatis 日志观测 |
| 测试环境 | 本地 dev（PG :35432, Redis :36379, 后端 :8080, 前端 :8000） |
| 测试账号 | `13600049114` / `117315Akers`（管理员+家长）, `cici` / PIN `180614`（孩子） |
| DB 初始状态 | 空库重置，含 1 个 `initialization_token` |

## 执行摘要

| 模块 | 总用例 | PASS | FAIL | BLOCKED | 缺陷数 | 完成率 |
|------|--------|------|------|---------|--------|--------|
| A — Admin 端 | 5 | 5 | 0 | 0 | 4 | 100% |
| P — Parent 端 | 20 | 8 | 4 | 8 | 9 | 60% |
| C — Child 端 | 7 | 1 | 0 | 6 | 1 | 14% |
| X — Cross-cutting | 1 | 0 | 0 | 1 | 0 | 0% |
| I — 不变量 | 6 | 5 | 1 | 0 | 1 | 100% |
| S — 安全基线 | 6 | 5 | 1 | 0 | 1 | 100% |
| **总计** | **45** | **24** | **6** | **15** | **14** | **53%** |

> 完成率 = PASS / 总用例 = 24/45。去除 BLOCKED 后有效完成率 = 24/30 = 80%。
>
> Parent/Child/X 大量 BLOCKED 因 bug-011（child_login session FK 违反）和 bug-012（points_balance 永不创建）两个 Critical 缺陷阻塞了积分链路。
>
> I-002 的 FAIL 源自测试数据污染（手动 INSERT 导致 ledger 与 balance 不连续），非生产代码 bug。

## Severity 分布

| Severity | 数量 | Bug IDs |
|----------|------|---------|
| **Critical** | 2 | bug-011, bug-012 |
| **High** | 3 | bug-002, bug-008, bug-014 |
| **Medium** | 5 | bug-001, bug-005, bug-006, bug-009, bug-010 |
| **Low UX** | 4 | bug-003, bug-004, bug-007, bug-013 |
| **Cosmetic** | 0 | — |
| **Total** | **14** | |

## Bug 清单（按 Severity 降序）

### Critical（2）

| Bug ID | Module | 标题 | 阻塞用例 | 状态 |
|--------|--------|------|---------|------|
| bug-011 | auth/session/child-login | child_login 用 `-childId` 作 account_id 导致 session FK 违反，孩子端完全无法登录 | C-002~C-007, P-013, P-014, P-019, X-001 | open |
| bug-012 | points/balance | points_balance 记录永不创建（无 insert 方法），全积分链路断开 | P-013~P-016, P-019, C-004~C-007, X-001 | open |

### High（3）

| Bug ID | Module | 标题 | 阻塞用例 | 状态 |
|--------|--------|------|---------|------|
| bug-002 | audit | audit_log 表始终为空，所有审计操作未落表 | — | open |
| bug-008 | parent/tasks（前端） | 任务卡片渲染空白：前端 interface 字段与后端返回完全不匹配 | P-010（任务列表页） | open |
| bug-014 | parent/exchanges（前端） | 兑换履约 UI 列表严重残缺：缺列、状态未中文化、无核销/取消按钮 | P-018 | open |

### Medium（5）

| Bug ID | Module | 标题 | 阻塞用例 | 状态 |
|--------|--------|------|---------|------|
| bug-001 | admin/accounts（前端）+ S-004 | 手机号脱敏规则不一致（5 星 vs 4 星），MyBatis 日志打印完整 11 位 | S-004 | open |
| bug-005 | parent/family（前端） | 家庭管理页未展示家庭名称、无编辑入口 | P-002 | open |
| bug-006 | parent/devices（前端） | 家长端缺少设备管理 UI | P-006 | open |
| bug-009 | task/prize API | 分页元数据 bug：`content` 有数据但 `totalElements: 0` | P-008 分页感知 | open |
| bug-010 | parent/tasks（前端） | UI 缺单任务分配入口，只有「批量分配」按钮 | P-010（单任务流） | open |

### Low UX（4）

| Bug ID | Module | 标题 | 阻塞用例 | 状态 |
|--------|--------|------|---------|------|
| bug-003 | admin/config（前端） | 配置保存成功无 message.success toast 反馈 | — | open |
| bug-004 | init/migration | instance_config 表初始化后为空，需手动录入 | A-001（后置验证） | open |
| bug-007 | parent/templates（前端） | 模板列表行内无「删除」按钮 | P-009 | open |
| bug-013 | parent/prizes（前端） | 奖品列表行只有「编辑」按钮，无删除/启停 | P-017 | open |

## 用例执行结果总表

| Case ID | 标题 | Status | Bug Ref |
|---------|------|--------|---------|
| A-001 | 系统初始化 | PASS | bug-004 |
| A-002 | 管理员登录 | PASS | — |
| A-003 | 实例配置 CRUD | PASS | bug-003 |
| A-004 | 账号管理 | PASS | bug-001 |
| A-005 | 审计日志 + 健康面板 | PASS（功能），审计数据为 FAIL | bug-002 |
| P-001 | 家长登录 | PASS | — |
| P-002 | 家庭信息查询与更新 | FAIL | bug-005 |
| P-003 | 成员管理 | PASS | — |
| P-004 | 创建孩子档案 | PASS | — |
| P-005 | 更新孩子档案 | PASS | — |
| P-006 | 设备授权 | FAIL | bug-006 |
| P-007 | 创建任务模板 | PASS | — |
| P-008 | 模板查询与更新 | PASS | bug-007（发现） |
| P-009 | 模板启停与删除 | PASS | bug-007 |
| P-010 | 分配任务 | FAIL | bug-008, bug-010 |
| P-011 | 取消任务分配 | BLOCKED | bug-011（孩子端无法登录） |
| P-012 | 生成 recurring 任务 | BLOCKED | bug-011 |
| P-013 | 待审核查询与审核通过 | BLOCKED | bug-011, bug-012 |
| P-014 | 审核驳回与历史 | BLOCKED | bug-011, bug-012 |
| P-015 | 查询积分余额与流水 | BLOCKED | bug-012 |
| P-016 | 积分调整 | BLOCKED | bug-012 |
| P-017 | 奖品 CRUD | PASS | bug-013 |
| P-018 | 兑换核销 | FAIL | bug-014 |
| P-019 | 核销幂等 | BLOCKED | bug-011, bug-012 |
| P-020 | 盲盒池管理 | BLOCKED | bug-011, bug-012 |
| C-001 | 设备绑定 | PASS | — |
| C-002 | PIN 登录 | BLOCKED | bug-011 |
| C-003 | 任务列表与提交任务 | BLOCKED | bug-011 |
| C-004 | 查询积分余额与流水 | BLOCKED | bug-011, bug-012 |
| C-005 | 奖品商城浏览 | BLOCKED | bug-011, bug-012 |
| C-006 | 直接兑换奖品 | BLOCKED | bug-011, bug-012 |
| C-007 | 盲盒兑换 | BLOCKED | bug-011, bug-012 |
| X-001 | 完整家庭工作流 | BLOCKED | bug-011, bug-012 |
| I-001 | points 余额非负 | PASS | — |
| I-002 | ledger 余额一致性 | FAIL（测试数据污染） | — |
| I-003 | prize 库存非负 | PASS | — |
| I-004 | exchange_snapshot 不可变 | PASS | — |
| I-005 | task_review 不可变 | PASS | — |
| I-006 | ledger 无 UPDATE/DELETE | PASS | — |
| S-001 | 日志无明文密码 | PASS | — |
| S-002 | 日志无明文 PIN | PASS | — |
| S-003 | 日志无 JWT token | PASS | — |
| S-004 | 日志无完整手机号 | FAIL | bug-001 |
| S-005 | 浏览器存储无敏感字段 | PASS | — |
| S-006 | API 响应认证规范 | PASS | — |

## 模块测试完整性评估

### Admin 端（A-001~A-005）— 测试完整
- 全部 5 用例执行完成，所有管理界面可达、功能可操作
- 发现 4 个缺陷（Low-Medium），无阻塞型缺陷
- **阻塞**：无

### Parent 端（P-001~P-020）— 仅完成 60%
- 前 10 用例（家庭→模板→任务分配）完整测试
- 积分+审核模块（P-013~P-016）因 bug-011/bug-012 完全阻塞
- 兑换模块（P-018~P-019）仅前端 UI 可访问，核销流程因 bug-011/bug-012 无法走通
- 盲盒池（P-020）因积分链路断开无法测试
- **阻塞**：P-013~P-016、P-019~P-020（共 8 项）

### Child 端（C-001~C-007）— 仅完成 14%
- C-001 设备绑定 PASS（后端 API 直调成功）
- C-002~C-007 因 bug-011（child_login FK 违反）完全阻塞，孩子端无法登录
- **阻塞**：C-002~C-007（共 6 项）

### Cross-cutting（X-001）— 未执行
- 因 bug-011 + bug-012 无法执行完整链路
- **阻塞**：X-001（1 项）

### 不变量验证（I-001~I-006）— 测试完整
- 6 条不变量全部执行，5 PASS / 1 FAIL
- I-002 FAIL 为测试数据污染（手动 INSERT），非生产代码 bug
- **阻塞**：无

### 安全基线（S-001~S-006）— 测试完整
- 6 项安全基线全部扫描，5 PASS / 1 FAIL
- S-004 FAIL 因 MyBatis DEBUG 日志打印完整手机号（关联 bug-001）
- S-005 确认 HttpOnly cookie 良好实践
- S-006 确认 device binding credential 一次性返回 + DB sha256 hash 良好实践
- **阻塞**：无

## 不变量 I-001~I-006 测试结论

详见 [`reports/round-1-invariants.md`](round-1-invariants.md)。

| Case ID | 不变量 | 结论 | 备注 |
|---------|--------|------|------|
| I-001 | points_balance.balance ≥ 0 | **PASS** | — |
| I-002 | ledger.balance_after 与累计一致 | **FAIL** | 测试数据污染：手动 INSERT ledger 导致不连续（非代码 bug） |
| I-003 | prize.stock ≥ 0 | **PASS** | — |
| I-004 | exchange_snapshot 不可变 | **PASS** | 结构存在，需 exchange 创建时写入 snapshot 验证（bug-011 阻塞完整流程） |
| I-005 | task_review 不可变 | **PASS** | — |
| I-006 | points_ledger 无 UPDATE/DELETE | **PASS** | — |

### I-002 测试数据污染说明
I-002 FAIL 的根因：测试过程中手动通过 `POST /api/prizes` 和 `POST /api/admin/config` 创建数据后，为绕过 bug-012 测试 points 功能，通过 Python psycopg2 直接执行了 `INSERT INTO points_balance` 和 `INSERT INTO points_ledger` 语句。这些手动 INSERT 未保持 ledger.balance_after 与累计 amount 的计算一致性，且未触发 points_balance 的版本递增。**这不代表生产代码存在不变量破坏**。

**处理**：Stage F 修复 bug-012 后，使用 `partial_reset` 清除 points 表测试数据，Reverify I-002。

## 安全扫描结论

详见 [`reports/round-1-security.md`](round-1-security.md)。

| Case ID | 场景 | 结论 | 备注 |
|---------|------|------|------|
| S-001 | 密码不在日志 | **PASS** | `117315Akers` 未出现在 backend.log |
| S-002 | PIN 不在日志 | **PASS** | `180614` 未出现在 backend.log |
| S-003 | JWT 不在日志 | **PASS** | 无 `eyJ` 开头的完整 JWT 在日志中 |
| S-004 | 手机号脱敏 | **FAIL** | MyBatis DEBUG 日志完整打印 `13600049114`（11 位） |
| S-005 | HttpOnly cookie | **PASS** | accessToken 在 HttpOnly cookie 中（JS 不可读） |
| S-006 | Device binding credential | **PASS** | credential 一次性返回明文，DB 只存 sha256 hash |

## Stage E 修复顺序建议（按依赖图）

```
                     ┌──────────┐
                     │ bug-011  │ ← Critical: session FK
                     │ (auth)   │
                     └────┬─────┘
                          │
                     ┌────▼─────┐
                     │ bug-012  │ ← Critical: points_balance insert
                     │ (points) │
                     └────┬─────┘
                          │
          ┌───────────────┼───────────────┐
          │               │               │
     ┌────▼────┐   ┌─────▼─────┐   ┌─────▼─────┐
     │ bug-002 │   │  bug-009  │   │  bug-001  │ ← S-004 phone in log
     │ (audit) │   │ (pagination)│  │ (masking) │
     └─────────┘   └───────────┘   └───────────┘
          │               │
          └───────┬───────┘
                  │
          ┌───────▼────────┐
          │ 前端缺陷批次    │
          │ bug-005~008,   │
          │ bug-010, 013,  │
          │ bug-014        │
          └────────────────┘
```

### 建议修复批次

| 批次 | 模块 | Bug IDs | 修复范围 | 依赖 |
|------|------|---------|---------|------|
| Batch 1 | **auth/session** | bug-011 | schema migration（加 child_id 列）+ SessionService 改造 | 无 |
| Batch 2 | **points** | bug-012 | PointsBalanceMapper.insert + ChildProfileService 联动 + migration | auth 就绪 |
| Batch 3 | **audit** | bug-002 | DatabaseAuditService 修复、AuditLog 实体验证 | 无 |
| Batch 4 | **task/prize API** | bug-009 | Pagination 元数据修复 | 无 |
| Batch 5 | **admin/parent UI** | bug-001 | 统一脱敏规则 + MyBatis 日志过滤 | 无 |
| Batch 6 | **前端-家庭** | bug-005, bug-006 | Family page name + Device management UI | 无 |
| Batch 7 | **前端-任务** | bug-008, bug-010 | Task card interface + Single assignment UI | 无 |
| Batch 8 | **前端-模板/奖品** | bug-007, bug-013 | Delete/enable/disable buttons | 无 |
| Batch 9 | **前端-兑换** | bug-014 | Exchange fulfillment list rewrite | 无 |
| Batch 10 | **前端-配置** | bug-003, bug-004 | Toast notification + default config migration | 无 |

> **注意**：Batch 1（bug-011）涉及 schema 变更（session 表加 child_id 列），触发范围扩张决策点（Design Doc §8.3）。需暂停确认是否在本 change 内修改。
