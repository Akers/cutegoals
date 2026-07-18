# 测试进度跟踪表

> 该文件记录用例级 checkpoint，用于中断恢复。每用例执行后立即更新。
>
> **Change**: e2e-system-test-and-fix
> **Plan**: docs/superpowers/plans/2026-07-18-e2e-system-test-and-fix.md
> **Started**: 2026-07-18

## 用例 ID 命名规则

| Prefix | 模块 | 总数 |
|--------|------|------|
| A-NNN | Admin 端 | 5 |
| P-NNN | Parent 端 | 20 |
| C-NNN | Child 端 | 7 |
| X-NNN | Cross-cutting（端到端完整流程） | 1 |
| I-NNN | 不变量 DB 验证 | 6 |
| S-NNN | 安全基线验证 | 6 |

**总计 45 用例**

## 进度状态

| Status | 含义 |
|--------|------|
| pending | 未开始 |
| running | 执行中 |
| passed | 通过 |
| failed | 失败，已生成 bug-NNN |
| blocked | 被前置依赖阻塞 |
| skipped | 主动跳过（需说明原因） |

## Stage A — 环境准备（pre-flight）

| Task | Status | 备注 |
|------|--------|------|
| A-1 Redis 容器启动 | passed | :36379，redis.ping()=True |
| A-2 PG :35432 可连接 | passed | 30 张表 |
| A-3 数据库重置 | passed | Python 直连 TRUNCATE + sequence + token seed |
| A-4 后端 :8080 启动 | passed | pty_b78fca2d persistent（前次 600s 超时） |
| A-5 前端 :8000 启动 | passed | pty_0f809832 npm run start（端口实际 8000 非 5173） |
| A-6 前端 API 代理验证 | passed | :8000/api/health → :8080 200 |

### 环境差异备忘

- **Redis 端口差异**：`.env.dev` 原写 `REDIS_PORT=6379` 错误；实际容器映射 `host:36379 → container:6379`；已在 `.env.dev` 修正为 36379
- **Docker 权限**：当前用户不在 docker 组，sudo 需要密码；所有 docker exec 操作改用 Python 直连
- **reset-dev-db.sh 容器名错误**：脚本默认 `sino-cms-postgres`（旧名），实际为 `mit-modelide-core-postgres`，但通过 Python 直连规避了该问题

## Stage B — 测试计划

| Task | Status | 备注 |
|------|--------|------|
| B-1 DB 表结构调研 | passed | 30 张表清单 |
| B-2 helpers 脚本 | passed | db_checks.py + security_checks.py + partial_reset.py（Python 实现） |
| B-3 test-plan.md | passed | 1505 行，45 用例（fix-2 subagent） |

## 用例执行（Round-1）

### Admin 端（A-001~A-005）

| Case ID | Title | Status | Bug | Evidence | DB Hash | Started | Ended |
|---------|-------|--------|-----|----------|---------|---------|-------|
| A-001 | 系统初始化（init token） | passed | bug-004 | A-001/ | account 0→1, token consumed→T | 06:55 | 06:56 |
| A-002 | 管理员登录 | passed | — | A-002/ | — | 06:56 | 06:57 |
| A-003 | 实例配置 | passed | bug-003 | A-003/ | instance_config 0→7 | 06:57 | 06:59 |
| A-004 | 账号管理 | passed | bug-001 | A-004/ | — | 06:59 | 07:00 |
| A-005 | 审计日志 + 健康面板 | passed (audit: FAIL) | bug-002 | A-005/ | audit_log=0 | 07:00 | 07:02 |

### Parent 端（P-001~P-020）

| Case ID | Title | Status | Bug | Evidence | DB Hash | Started | Ended |
|---------|-------|--------|-----|----------|---------|---------|-------|
| P-001~P-020 | (待 test-plan.md 细化) | pending | | | | | |

### Child 端（C-001~C-007）

| Case ID | Title | Status | Bug | Evidence | DB Hash | Started | Ended |
|---------|-------|--------|-----|----------|---------|---------|-------|
| C-001~C-007 | (待 test-plan.md 细化) | pending | | | | | |

### Cross-cutting（X-001）

| Case ID | Title | Status | Bug | Evidence | DB Hash | Started | Ended |
|---------|-------|--------|-----|----------|---------|---------|-------|
| X-001 | 端到端完整流程（init→family→task→review→points→prize→exchange） | pending | | | | | |

### 不变量 DB 验证（I-001~I-006）

| Case ID | Title | Status | Bug | DB Query | Result | Verified At |
|---------|-------|--------|-----|----------|--------|-------------|
| I-001 | points_balance 非负 | pending | | | | |
| I-002 | points_ledger 余额一致性 | pending | | | | |
| I-003 | prize 库存非负 | pending | | | | |
| I-004 | exchange_snapshot 不可变 | pending | | | | |
| I-005 | task_review 审核记录不可变 | pending | | | | |
| I-006 | points_ledger 不可变（无 UPDATE/DELETE） | pending | | | | |

### 安全基线（S-001~S-006）

| Case ID | Title | Status | Bug | Layer | Finding | Verified At |
|---------|-------|--------|-----|-------|---------|-------------|
| S-001 | 后端日志无明文密码 | pending | | backend.log | | |
| S-002 | 后端日志无明文 PIN | pending | | backend.log | | |
| S-003 | 后端日志无 JWT token | pending | | backend.log | | |
| S-004 | 后端日志无完整手机号 | pending | | backend.log | | |
| S-005 | 浏览器 localStorage 无敏感字段 | pending | | browser | | |
| S-006 | API 响应不含敏感字段 | pending | | api | | |

## 修复阶段 Bug 进度

| Bug ID | Severity | Module | Status | Fixed In Commit | Round-2 Verified |
|--------|----------|--------|--------|-----------------|------------------|
| (待 Round-1 完成后填充) | | | | | |

## 恢复协议

中断后从此文件恢复：
1. 读取最后状态（Stage / 最后用例 ID）
2. 检查 reports/backend.log + reports/frontend.log 确认服务状态
3. 从下一个 pending 用例继续
4. 必要时使用 reports/helpers/partial-reset.sh 清理数据污染
