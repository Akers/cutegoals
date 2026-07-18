---
archived-with: 2026-07-18-e2e-system-test-and-fix
status: final
---
# E2E 系统测试与修复 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

```yaml
change: e2e-system-test-and-fix
design-doc: docs/superpowers/specs/2026-07-18-e2e-system-test-and-fix-design.md
base-ref: dfd0d6f6eaecfcd6469ad4d78b1674af3b815b34
```

**Goal:** 在本地 dev 环境通过 agent-browser 对 CuteGoals 2.0 执行完整端到端系统测试（admin/parent/child 三端 × 9 个后端模块），发现、记录、修复全部级别缺陷，通过分层回归验证后归档。

**Architecture:** 采用「3 阶段 × 多轮循环」执行架构 — Round-1 全量测试（6 个子阶段：A→B→C1→C2→C3→C4→C5→C6→D）→ 修复阶段（9 模块按依赖顺序批量提交）→ Round-2 最终回归。每用例落盘 test-progress.md checkpoint，每模块报告落盘后触发上下文压缩安全点。不变量验证采用 agent-browser + psql 直查 DB 双重手段；安全基线采用三层观测（API 响应 / 浏览器存储 / 后端日志）。

**Tech Stack:** PostgreSQL :35432 / Redis :6379 / Spring Boot :8080 / UmiJS :5173 / agent-browser / psql / 账号：`13600049114/117315Akers`（管理员/家长）、`cici/PIN 180614`（孩子）

---

## 全局约束

1. **数据库**：重置后全程不中途重置（Design Doc §3.1 D3）；修复阶段的模块阶段回归前允许 `partial-reset`（Design Doc §9.1）
2. **测试方式**：所有用例必须通过 `agent-browser` 以真实用户身份执行，不使用 mock 或直接调 API（spec spec.md Req 3）
3. **账号隔离**：admin/parent/child 三套独立 browser context，cookie/localStorage 不串扰（Design Doc §9.2）
4. **缺陷分级**：Critical / High / Medium / Low / Cosmetic 五级；**全部级别必须修复**（spec spec.md Req 5）
5. **分层回归**：单 bug 回归 → 阶段回归（模块级） → 最终全量回归（spec spec.md Req 6）
6. **范围扩张**：修复涉及 schema 变更 / 跨 3+ 模块 / 新增 capability / 改变 requirement 语义时，暂停触发决策点（Design Doc §8.3）
7. **Commit 策略**：按模块批量 commit，一个 commit = 一个模块的 bug 修复批次（Design Doc §8.2）
8. **上下文压缩安全点**：共 8 处强制压缩点（Design Doc §3.3），到达时必须保证相关报告已落盘
9. **安全基线**：明文密码/PIN/Token/完整手机号不出现于 API 响应、浏览器存储、后端日志（Design Doc §6）
10. **报告产物**：`reports/` 目录必须包含 `test-plan.md`、`round-1.md`、`bug-NNN-*.md`（每 bug 一份）、`round-2-*.md`、`final.md`（spec spec.md Req 7）

---

## Stage A: 环境准备与启动（6 任务）

**Design Doc 引用**：§3.1 Stage A、§9 异常处理矩阵、§5.1 表清单  
**产物路径**：`reports/backend.log`、`reports/frontend.log`  
**依赖**：无（基础环境）  
**安全点**：本阶段无强制压缩点，但后端日志文件需在整个测试期间持续写入

### Task A-1: 启动 Redis 容器

**验证手段**：`redis-cli -h localhost -p 6379 ping` 返回 PONG

- [x] **Step 1: 确认 docker-compose 文件存在**

```bash
ls -la deploy/docker-compose.yml
# 预期：文件存在，包含 mit-modelide-core-redis 服务
```

- [x] **Step 2: 启动 Redis 容器**

```bash
docker-compose -f deploy/docker-compose.yml up -d mit-modelide-core-redis
# 预期：Container started, :6379 监听
```

- [x] **Step 3: 验证 Redis 可用**

```bash
redis-cli -h localhost -p 6379 ping
# 预期：PONG

ss -tlnp | grep 6379
# 预期：LISTEN 状态
```

- [x] **Step 4: 记录到 test-progress.md**（创建初始文件）

```bash
mkdir -p openspec/changes/e2e-system-test-and-fix/reports/evidence/network
mkdir -p openspec/changes/e2e-system-test-and-fix/reports/helpers
cat > openspec/changes/e2e-system-test-and-fix/reports/test-progress.md << 'EOF'
# Test Progress Tracker

- Change: e2e-system-test-and-fix
- Round: 1
- Started: `date -Iseconds`
- Last updated: `date -Iseconds`

## 用例进度表

| Case ID | Module | Status | Bug IDs | DB Hash | Notes |
|---------|--------|--------|---------|---------|-------|

## Bug 状态

| Bug ID | Severity | Module | Status | Case | Root-cause | Fix-commit |
|--------|----------|--------|--------|------|------------|------------|

## 模块汇总

- [x] A admin
- [x] P parent
- [x] C child
- [x] X cross-module
- [x] I invariants
- [x] S security
EOF
```

### Task A-2: 确认 PostgreSQL :35432 可连接

**验证手段**：psql 查询 `\dt` 确认 schema cutegoals 中表清单可见

- [x] **Step 1: 测试连接**

```bash
psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "SELECT current_database(), current_schema;"
# 预期：显示 cutegoals / cutegoals
```

- [x] **Step 2: 记录连接信息**

```bash
psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "\dt" -o /dev/null
echo "PostgreSQL :35432 connected successfully" >> reports/test-progress.md
```

### Task A-3: 重置数据库到干净初始状态

**Design Doc §9**：若 `reset-dev-db.sh` 非零退出，立即停止，问用户是否手动恢复 DB

- [x] **Step 1: 记录重置前表清单**

```bash
psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "\dt" > /tmp/before-reset-tables.txt
wc -l /tmp/before-reset-tables.txt
```

- [x] **Step 2: 执行重置脚本**

```bash
bash scripts/reset-dev-db.sh
# 预期：退出码 0，无错误输出
echo "Reset exit code: $?"
```

- [x] **Step 3: 记录重置后表清单**

```bash
psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "\dt" > /tmp/after-reset-tables.txt
diff /tmp/before-reset-tables.txt /tmp/after-reset-tables.txt || true
# 预期：可能存在差异（schema 重建前后）；记录到 reports
```

- [x] **Step 4: 确认 health endpoint 可用**

```
等待后端启动后才能验证，在 Task A-4 完成后补执行 curl /api/health
```

### Task A-4: 启动后端（Spring Boot :8080）

**Design Doc §9**：若启动失败或 `/api/health` 超时/非 UP，记录 Critical bug-001，跳过后端依赖用例

- [x] **Step 1: 清理上次构建**

```bash
cd server && mvn clean -pl web -am -DskipTests -q 2>&1 | tail -5
# 预期：BUILD SUCCESS
```

- [x] **Step 2: 后台启动后端，日志重定向到 reports/backend.log**

```bash
cd server && nohup mvn -pl web -am spring-boot:run -DskipTests > ../reports/backend.log 2>&1 &
echo "Backend PID: $!"
# 预期：进程启动，持续写入日志
```

- [x] **Step 3: 等待 :8080 就绪（最长 180s）**

```bash
for i in $(seq 1 36); do
  if curl -s http://localhost:8080/api/health 2>/dev/null | grep -q UP; then
    echo "Backend ready after ${i}s"
    break
  fi
  sleep 5
done
# 预期：输出 "Backend ready after Ns"
# 若超时：读取 backend.log 尾 50 行定位问题，记录 Critical bug-001
```

- [x] **Step 4: 验证后端日志无 ERROR**

```bash
grep -c "ERROR" reports/backend.log || echo "No ERROR in backend.log"
grep "Started Application" reports/backend.log
# 预期：No ERROR（WARN 可接受），启动日志中有 "Started Application"
```

### Task A-5: 启动前端（UmiJS :5173）

- [x] **Step 1: 确认前端技术栈**

```bash
# 从 web/package.json 判断
grep -E '"umi|"vite|"dev"' web/package.json | head -5
# 预期：确定前端是 UmiJS（web/src/.umi 目录存在为标志）
ls web/src/.umi 2>/dev/null && echo "UmiJS confirmed"
```

- [x] **Step 2: 安装依赖（如 node_modules 不存在或 package-lock.json 有变更）**

```bash
cd web && npm install 2>&1 | tail -3
# 预期：added N packages 或 up to date
```

- [x] **Step 3: 后台启动前端，日志重定向**

```bash
cd web && nohup npm run dev > ../reports/frontend.log 2>&1 &
echo "Frontend PID: $!"
```

- [x] **Step 4: 等待 :5173 就绪（最长 120s）**

```bash
for i in $(seq 1 24); do
  if curl -s http://localhost:5173 2>/dev/null | head -1 | grep -qE "html|DOCTYPE|React"; then
    echo "Frontend ready after ${i}s"
    break
  fi
  sleep 5
done
# 预期：输出 "Frontend ready after Ns"
# 若超时：读取 frontend.log 尾 50 行定位问题，记录 Critical bug-002
```

### Task A-6: 验证前端 API 代理正常

- [x] **Step 1: 从未登录状态请求一个需要认证的 API**

```bash
# 前端 /api 代理 → 后端 :8080：发送任意请求验证代理链
curl -s -o /dev/null -w "%{http_code}" http://localhost:5173/api/health
# 预期：200（health endpoint 无需认证）

# 验证代理链完整：前端 5173 → 后端 8080
DIFF=$(diff <(curl -s http://localhost:5173/api/health) <(curl -s http://localhost:8080/api/health))
if [ -z "$DIFF" ]; then echo "API proxy OK: identical responses"; else echo "API proxy MISMATCH"; fi
```

- [x] **Step 2: 记录验证结果到 test-progress.md**

```bash
echo "- [x] A-6: API proxy verified (5173 → 8080)" >> reports/test-progress.md
```

---

## Stage B: 制定详细测试计划（3 任务）

**Design Doc 引用**：§3.1 Stage B、§4 Case ID 命名规则、§5.1 表清单调研  
**产物路径**：`reports/test-plan.md`、`reports/helpers/db-checks.sh`、`reports/helpers/security-check.sh`  
**依赖**：Stage A 环境就绪  
**安全点**：Stage B 完成后无强制压缩点（进入 Stage C 前自动压缩）

### Task B-1: 调研数据库表结构

- [x] **Step 1: 列出所有业务表**

```bash
psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "\dt" | grep -vE 'schema_migrations|flyway|DATABASECHANGELOG'
# 输出保存到 reports/evidence/tables-inventory.txt
```

- [x] **Step 2: 调研关键不变量表（Design Doc §5.1）**

```bash
# points 模块
psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "\d points_account"
psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "\d points_ledger"
psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "\d points_adjustment"

# task-review 模块
psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "\d task_review"
psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "\d task_review_log"  # 若存在

# exchange 模块
psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "\d exchange_order"
psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "\d exchange_snapshot"  # 或类似名
psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "\d prize_stock"

# auth 模块
psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "\d user_credential"
psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "\d session_token"

# family 模块
psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "\d child_profile"
psql -h localhost -p 35432 -U cutegoals -d cutegoals -c "\d device_authorization"
```

- [x] **Step 3: 记录实际表名与 Design Doc 的偏差（如有）**

```bash
# 比较实际表名与 Design Doc §5.1 列出的预期表名
# 写入 reports/evidence/table-mapping.md
```

### Task B-2: 创建 DB 检查与安全 helper 脚本

- [x] **Step 1: 创建 `reports/helpers/db-checks.sh`**

Design Doc §5.4 定义的封装脚本：

```bash
cat > reports/helpers/db-checks.sh << 'DBEOF'
#!/bin/bash
# DB 不变量检查 helper
# 用法: ./db-checks.sh <command> [args]

PG_OPTS="-h localhost -p 35432 -U cutegoals -d cutegoals"

case "${1:-help}" in
  ledger-hash)
    psql $PG_OPTS -tAc "SELECT md5(string_agg(t::text, ',' ORDER BY id)) FROM points_ledger"
    ;;
  balance)
    psql $PG_OPTS -tAc "SELECT balance FROM points_account WHERE child_id='$2'"
    ;;
  audit-log-hash)
    psql $PG_OPTS -tAc "SELECT md5(string_agg(t::text, ',' ORDER BY id)) FROM task_review_log WHERE task_id='$2'"
    ;;
  stock)
    psql $PG_OPTS -tAc "SELECT stock FROM prize_stock WHERE prize_id='$2'"
    ;;
  snapshot-hash)
    psql $PG_OPTS -tAc "SELECT md5(string_agg(t::text, ',' ORDER BY id)) FROM exchange_snapshot WHERE order_id='$2'"
    ;;
  append-only-check)
    psql $PG_OPTS -tAc "SELECT count(*) FROM points_ledger WHERE updated_at > created_at"
    ;;
  table-rows)
    psql $PG_OPTS -tAc "SELECT count(*) FROM $2"
    ;;
  *)
    echo "Usage: $0 {ledger-hash|balance <child_id>|audit-log-hash <task_id>|stock <prize_id>|snapshot-hash <order_id>|append-only-check|table-rows <table>}"
    ;;
esac
DBEOF
chmod +x reports/helpers/db-checks.sh
```

- [x] **Step 2: 创建 `reports/helpers/security-check.sh`**

Design Doc §6.4 定义的三层安全扫描脚本：

```bash
cat > reports/helpers/security-check.sh << 'SECEOF'
#!/bin/bash
# 安全基线检查 helper
# 用法: ./security-check.sh {response|storage|logs|logs-since <timestamp>}

SENSITIVE_PATTERNS="117315Akers|117315|Akers|180614|13600049114|password=|pin=|token=eyJ"

case "${1:-help}" in
  response)
    echo "=== Layer 1: API Response Scan ==="
    grep -E "$SENSITIVE_PATTERNS" reports/evidence/network/*.json 2>/dev/null || echo "No sensitive data in API responses"
    ;;
  storage)
    echo "=== Layer 2: Browser Storage Scan ==="
    # 此命令由 agent-browser 执行后在本地检查证据
    grep -E "$SENSITIVE_PATTERNS" reports/evidence/storage/*.txt 2>/dev/null || echo "No sensitive data in browser storage"
    ;;
  logs)
    echo "=== Layer 3: Backend Log Scan ==="
    grep -E "$SENSITIVE_PATTERNS" reports/backend.log 2>/dev/null || echo "No sensitive data in backend logs"
    ;;
  logs-since)
    echo "=== Layer 3: Backend Log Incremental Scan since $2 ==="
    # 需要先记录上次检查的行号
    grep -E "$SENSITIVE_PATTERNS" reports/backend.log 2>/dev/null || echo "No sensitive data in backend logs since $2"
    ;;
  *)
    echo "Usage: $0 {response|storage|logs|logs-since <timestamp>}"
    ;;
esac
SECEOF
chmod +x reports/helpers/security-check.sh
```

### Task B-3: 编写 `reports/test-plan.md`

**Design Doc 引用**：§4.2 Case ID 命名规则、§5.3 不变量用例、§6.5 安全用例  
**验收标准**：计划必须覆盖 admin/parent/child 三端全部 UI 可达功能 + 6 条不变量用例 + 6 条安全用例，按模块分组

- [x] **Step 1: 创建 test-plan.md 整体结构**

```bash
cat > reports/test-plan.md << 'PLANEOF'
# E2E 系统测试计划

- Change: e2e-system-test-and-fix
- Generated: `date -Iseconds`
- Tester: agent-browser
- Environment: local dev (PG:35432 / Redis:6379 / Backend:8080 / Frontend:5173)
- Accounts: 13600049114/117315Akers (admin+parent) / cici/PIN 180614 (child)

## 用例清单

### A. Admin 端（/admin）
| Case ID | 模块 | 功能 | 操作步骤 | 预期 | 证据类型 |
|---------|------|------|---------|------|---------|
| A-001 | admin.login | 管理员登录 | 1.打开 /admin 2.输入 13600049114/117315Akers 3.点登录 | 跳转概览页 | 截图 |
| A-002 | admin.overview | 概览面板 | 登录后查看 dashboard | 显示家庭/任务/积分统计数据 | 截图 |
| A-003 | admin.account-mgmt | 账号管理 | 查看/搜索/编辑账号列表 | 列表展示正确，分页可用 | 截图+API |
| A-004 | admin.audit-log | 审计日志 | 查看操作日志 | 日志条目完整，时间顺序正确 | 截图 |
| A-005 | admin.health | 健康检查 | 查看系统健康状态 | 各服务状态显示正常 | 截图 |

### P. Parent 端（/parent）
| Case ID | 模块 | 功能 | 操作步骤 | 预期 | 证据类型 |
|---------|------|------|---------|------|---------|
| P-001 | auth.parent-login | 家长登录 | 1.打开 /parent 2.输入 13600049114/117315Akers 3.点登录 | 跳转家庭概览页 | 截图+API |
| P-002 | family.view | 家庭信息查看 | 查看家庭名称/成员列表 | 显示正确家庭信息 | 截图 |
| P-003 | family.edit | 家庭信息编辑 | 修改家庭名称/设置 | 保存成功，刷新后一致 | 截图+API |
| P-004 | family.add-child | 添加孩子 | 填写 cici 档案（姓名/年龄/PIN） | 孩子出现在家庭列表 | 截图+DB 查询 |
| P-005 | family.add-parent | 添加家长 | 添加另一位家长账号 | 新家长出现在家庭设置 | 截图 |
| P-006 | family.device-auth | 设备授权 | 对孩子设备发起授权 | 授权成功，PIN 验证流程 | 截图+API |
| P-007 | task.create-template | 创建任务模板 | 填写任务名称/积分/类型/频率 | 任务模板出现在任务列表 | 截图 |
| P-008 | task.list | 任务列表查看 | 查看已发布和草稿任务 | 列表正确 | 截图 |
| P-009 | task.child-completion | 孩子完成查看 | 查看孩子任务提交/完成情况 | 提交列表正确，状态正确 | 截图 |
| P-010 | task-review.approve | 审核通过 | 对孩子提交任务点「通过」 | 积分入账，审核状态变为通过 | 截图+DB |
| P-011 | task-review.reject | 审核驳回 | 对孩子提交任务点「驳回」 | 任务状态变为驳回，积分不入账 | 截图+DB |
| P-012 | task-review.approve-dup | 审核幂等 | 对已通过任务再次点「通过」 | 操作无效，不产生二次积分 | DB 查询 |
| P-013 | points.ledger-view | 积分流水查看 | 查看积分变动记录 | 流水列表正确，每笔有来源 | 截图+DB |
| P-014 | points.adjustment | 积分调整 | 手动加减孩子积分 | 余额正确，流水可追溯 | 截图+DB |
| P-015 | prize.create | 创建奖品 | 填写奖品名称/所需积分/库存数量 | 奖品出现在奖品列表 | 截图 |
| P-016 | prize.create-blindbox | 创建盲盒 | 创建盲盒奖品，设置内含奖品 | 盲盒出现在奖品列表 | 截图 |
| P-017 | prize.stock-mgmt | 库存管理 | 修改奖品库存 | 库存数量正确更新 | 截图+DB |
| P-018 | exchange.view-requests | 查看兑换申请 | 查看孩子提交的兑换申请列表 | 申请列表正确 | 截图 |
| P-019 | exchange.fulfill | 核销兑换 | 对孩子兑换申请执行核销 | 订单状态变更为已核销，库存扣减 | 截图+DB |
| P-020 | exchange.fulfill-dup | 核销幂等 | 对已核销订单再次核销 | 操作无效，库存不重复扣减 | DB 查询 |

### C. Child 端（/child）
| Case ID | 模块 | 功能 | 操作步骤 | 预期 | 证据类型 |
|---------|------|------|---------|------|---------|
| C-001 | child.login | 孩子设备授权登录 | 1.打开 /child 2.选择 cici 3.输入 PIN 180614 | 进入孩子首页 | 截图+API |
| C-002 | child.today-tasks | 今日任务查看 | 查看今天的待完成/已完成任务 | 显示正确的任务列表 | 截图 |
| C-003 | child.submit-task | 提交任务 | 对今日任务点「提交完成」 | 任务状态变为待审核 | 截图 |
| C-004 | child.prize-list | 奖品列表查看 | 查看可兑换奖品 | 显示奖品的积分价格和库存 | 截图 |
| C-005 | child.exchange | 兑换奖品 | 选择奖品点击兑换 | 积分扣减，兑换订单生成 | 截图+DB |
| C-006 | child.blindbox-draw | 盲盒抽取 | 使用积分抽取盲盒 | 扣减积分，获得随机奖品 | 截图+DB |
| C-007 | child.history | 历史记录 | 查看兑换历史/任务历史 | 历史列表正确 | 截图 |

### X. 跨模块端到端
| Case ID | 模块 | 功能 | 操作步骤 | 预期 | 证据类型 |
|---------|------|------|---------|------|---------|
| X-001 | cross.full-chain | 全链路验证 | 孩子提交→家长审核→积分入账→孩子兑换→家长核销 | 全链路状态一致，DB 可追溯 | 截图+DB |

### I. 不变量验证（Design Doc §5.3）
| Case ID | 不变量 | 验证手段 | DB 查询 |
|---------|--------|---------|---------|
| I-001 | 积分流水 append-only | ledger hash + updated_at > created_at 检查 | points_ledger |
| I-002 | 积分余额非负 | 构造负数场景，验证拒绝 | points_account |
| I-003 | 审核幂等 | 重复审核后增量=0 | task_review_log |
| I-004 | 兑换快照不可变 | snapshot 表 hash + 字段不变 | exchange_snapshot |
| I-005 | 库存非负 | 超额兑换场景，验证拒绝 | prize_stock |
| I-006 | 核销幂等 | 重复核销后状态不变，无重复扣减 | exchange_order |

### S. 安全基线（Design Doc §6.5）
| Case ID | 场景 | 三层观测 |
|---------|------|---------|
| S-001 | parent 登录响应 | Layer1/2/3 → 检查 password/token/手机号 |
| S-002 | admin 登录响应 | Layer1/2/3 → 同上 |
| S-003 | child 设备授权（PIN 输入） | Layer1/2/3 → 检查 PIN 明文 |
| S-004 | 任务审核 API 响应 | Layer1 → 不返回敏感字段 |
| S-005 | 兑换 API 响应 | Layer1 → 手机号脱敏、token 脱敏 |
| S-006 | 后端日志全量扫描 | Layer3 → 全程敏感字符串扫描 |
PLANEOF

echo "Test plan created: reports/test-plan.md"
wc -l reports/test-plan.md
```

**验收标准**：test-plan.md 包含 5（A）+ 20（P）+ 7（C）+ 1（X）+ 6（I）+ 6（S）= 45 个用例，覆盖三个端、不变量、安全基线。

---

## Stage C: Round-1 分模块全量测试（12 子任务）

**Design Doc 引用**：§3.1 Stage C1-C6、§4 用例级 progress checkpoint、§5 不变量、§6 安全  
**产物路径**：每子阶段产出独立 round-1-{module}.md 报告  
**安全点**：C1/C2/C3/C4/C5/C6 完成后各触发一次上下文压缩（Design Doc §3.3 安全点 1-6）  
**模块依赖**：C1→C2→C3→C4→C5→C6 顺序执行（不可并行）  
**恢复协议**：每用例完成后更新 test-progress.md；会话恢复时读 test-progress.md 跳过已完成用例（Design Doc §4.4）

### Task C-0: 准备 agent-browser

- [x] **Step 1: 加载 agent-browser skill，确认 CLI 可用**

```bash
# 验证 agent-browser 命令可用
which agent-browser 2>/dev/null || npm list -g agent-browser 2>/dev/null || pip list 2>/dev/null | grep agent-browser
# 若不可用：触发异常处理（Design Doc §9）
```

- [x] **Step 2: 创建三套独立 browser context 目录**

```bash
mkdir -p reports/evidence/network
mkdir -p reports/evidence/storage
# agent-browser context 隔离：admin/parent/child 使用独立 profile 目录
```

- [x] **Step 3: 从 admin 身份登录，预热 browser context**

```
agent-browser 指令：打开 http://localhost:5173/admin
输入账号 13600049114 / 密码 117315Akers
点击登录按钮
验证跳转到 admin 概览页
截图保存到 reports/evidence/admin-login.png
登出（清除 admin context 的 cookie）
```

### Task C-1: Admin 端测试（Design Doc §3.1 C1）

**产物**：`reports/round-1-admin.md`  
**安全点**：本任务完成后触发上下文压缩安全点 1

- [x] **Step 1: 执行 A-001 admin 登录**

```
agent-browser admin context：
1. 打开 http://localhost:5173/admin
2. 输入 13600049114 / 117315Akers
3. 点击登录
4. 截图：reports/evidence/A-001-login.png
5. 捕获 POST /api/auth/login 响应 → reports/evidence/network/A-001-response.json
6. 判定：登录成功跳转概览页 → pass/fail
7. 更新 test-progress.md：A-001 行
```

- [x] **Step 2: 执行 A-002 概览面板**

```
agent-browser admin context：
1. 确认概览页显示统计面板（家庭数/任务数/积分总数）
2. 截图：reports/evidence/A-002-overview.png
3. 判定：至少有一个 KP 面板可见 → pass/fail
4. 更新 test-progress.md
```

- [x] **Step 3: 执行 A-003 账号管理**

```
agent-browser admin context：
1. 导航到账号管理页面
2. 查看列表、尝试搜索
3. 截图：reports/evidence/A-003-account-mgmt.png
4. 判定：列表渲染正确，搜索功能可用 → pass/fail
5. 更新 test-progress.md
```

- [x] **Step 4: 执行 A-004 审计日志**

```
agent-browser admin context：
1. 导航到审计日志页面
2. 查看日志列表
3. 截图：reports/evidence/A-004-audit-log.png
4. 判定：日志条目按时序展示 → pass/fail
5. 更新 test-progress.md
```

- [x] **Step 5: 执行 A-005 健康检查**

```
agent-browser admin context：
1. 导航到健康检查页面（或直接调 /api/health）
2. 截图：reports/evidence/A-005-health.png
3. 判定：各服务状态显示正常 → pass/fail
4. 更新 test-progress.md
```

- [x] **Step 6: 生成 round-1-admin.md**

```bash
cat > reports/round-1-admin.md << 'EOF'
# Round-1 Admin 端测试报告

- Module: admin
- Cases: A-001 ~ A-005
- Executed: `date -Iseconds`
- Context: agent-browser admin context

## 用例结果

| Case ID | 功能 | 状态 | Bug ID | 备注 |
|---------|------|------|--------|------|
| A-001 | admin.login | ⬜ | - | - |
| A-002 | admin.overview | ⬜ | - | - |
| A-003 | admin.account-mgmt | ⬜ | - | - |
| A-004 | admin.audit-log | ⬜ | - | - |
| A-005 | admin.health | ⬜ | - | - |
EOF
# 从 test-progress.md 同步结果到本文件
```

- [x] **Step 7: 退出 admin context（清除 cookie/localStorage，Design Doc §9.2）**

```
agent-browser 执行登出操作，关闭 admin browser context
```

- [x] **Step 8: ✦ 上下文压缩安全点 1 ✦**

确保 `reports/round-1-admin.md` 与 `reports/test-progress.md` 已落盘，记录当前上下文状态。

### Task C-2: Parent 端测试 — 家庭模块

**产物**：融入 `reports/round-1-parent.md`（全端汇总）  
**注意**：Parent 端用例较多，分 6 个子模块（P-001~P-020），每个子模块完成后更新 test-progress.md

- [x] **Step 1: 执行 P-001 家长登录（parent context）**

```
agent-browser parent context（新 context）：
1. 打开 http://localhost:5173/parent
2. 输入 13600049114 / 117315Akers
3. 点击登录
4. 截图：reports/evidence/P-001-login.png
5. 捕获登录 API 响应 → reports/evidence/network/P-001-response.json
6. 判定：登录成功跳转家庭概览页 → pass/fail
7. 更新 test-progress.md
```

- [x] **Step 2: 执行 P-002 家庭信息查看 + P-003 家庭信息编辑**

```
agent-browser parent context：
1. 查看当前家庭名称和成员
2. 截图：reports/evidence/P-002-family-view.png
3. 尝试编辑家庭名称
4. 保存，刷新验证
5. 截图：reports/evidence/P-003-family-edit.png
6. 判定：P-002 查看正确 / P-003 编辑成功 → pass/fail
7. 更新 test-progress.md
```

- [x] **Step 3: 执行 P-004 添加孩子档案（cici）**

```
agent-browser parent context：
1. 导航到添加孩子
2. 填写：姓名=cici, PIN=180614, 年龄=...
3. 提交
4. 截图：reports/evidence/P-004-add-child.png
5. DB 验证：psql -c "SELECT * FROM child_profile WHERE name='cici'"
6. 判定：孩子出现在家庭列表 + DB 可查 → pass/fail
7. 更新 test-progress.md
```

- [x] **Step 4: 执行 P-005 添加家长账号 + P-006 设备授权**

```
agent-browser parent context：
1. 添加另一位家长账号
2. 截图：reports/evidence/P-005-add-parent.png
3. 对 cici 的设备发起授权
4. 截图：reports/evidence/P-006-device-auth.png
5. 判定：新家长添加成功 / 设备授权流程正常 → pass/fail
6. 更新 test-progress.md
```

### Task C-3: Parent 端测试 — 任务模块

- [x] **Step 1: 执行 P-007 创建任务模板**

```
agent-browser parent context：
1. 导航到任务管理
2. 创建新任务：名称="整理房间"，积分=10，类型=每日
3. 提交
4. 截图：reports/evidence/P-007-create-task.png
5. DB 验证：psql -c "SELECT * FROM task_templates WHERE name='整理房间'"
6. 更新 test-progress.md
```

- [x] **Step 2: 执行 P-008 任务列表查看**

```
agent-browser parent context：
1. 查看已发布任务列表
2. 截图：reports/evidence/P-008-task-list.png
3. 判定：任务列表包含刚创建的任务 → pass/fail
4. 更新 test-progress.md
```

- [x] **Step 3: 执行 P-009 孩子完成情况查看**

```
agent-browser parent context：
1. 查看孩子任务完成情况
2. 截图：reports/evidence/P-009-child-completion.png
3. 判定：列表显示正确（此时尚无提交）→ pass/fail
4. 更新 test-progress.md
```

### Task C-4: Parent 端测试 — 审核模块

- [x] **Step 1: 执行 P-010 审核通过**

```
准备工作：需先切到 child context 提交一个任务，再切回 parent context

agent-browser parent context：
1. 查看待审核列表
2. 对 cici 提交的任务点「通过」
3. 截图：reports/evidence/P-010-approve.png
4. DB 验证：检查积分是否入账
   psql -c "SELECT balance FROM points_account WHERE child_name='cici'"
5. 判定：审核状态变为通过，积分入账 → pass/fail
6. 更新 test-progress.md
```

- [x] **Step 2: 执行 P-011 审核驳回**

```
agent-browser parent context：
1. 对另一个待审核任务点「驳回」
2. 截图：reports/evidence/P-011-reject.png
3. DB 验证：积分未入账
   psql -c "SELECT * FROM task_review WHERE status='rejected'"
4. 更新 test-progress.md
```

- [x] **Step 3: 执行 P-012 审核幂等验证**

```
agent-browser parent context：
1. 在已通过的任务上再次点「通过」
2. 截图：reports/evidence/P-012-approve-dup.png
3. DB 验证：
   HASH_BEFORE=$(./reports/helpers/db-checks.sh ledger-hash)
   # 执行重复审核操作
   HASH_AFTER=$(./reports/helpers/db-checks.sh ledger-hash)
   if [ "$HASH_BEFORE" = "$HASH_AFTER" ]; then echo "IDEMPOTENT: OK"; else echo "IDEMPOTENT: FAIL"; fi
4. 判定：重复审核不产生二次积分 → pass/fail
5. 更新 test-progress.md
```

### Task C-5: Parent 端测试 — 积分模块

- [x] **Step 1: 执行 P-013 积分流水查看**

```
agent-browser parent context：
1. 导航到 cici 的积分流水页面
2. 截图：reports/evidence/P-013-ledger.png
3. DB 验证：流水列表与 DB 一致
   ./reports/helpers/db-checks.sh table-rows points_ledger
4. 判定：流水页面正确展示所有变动记录 → pass/fail
5. 更新 test-progress.md
```

- [x] **Step 2: 执行 P-014 积分调整**

```
agent-browser parent context：
1. 对孩子积分执行手动调整（+5 和 -3）
2. 截图：reports/evidence/P-014-adjustment.png
3. DB 验证：
   psql -c "SELECT * FROM points_adjustment WHERE child_name='cici'"
   BALANCE=$(./reports/helpers/db-checks.sh balance <child_id>)
4. 尝试调整为负数（余额不足扣减），验证 UI 或 API 拒绝
5. 判定：正调整成功 / 负数拒绝 → pass/fail
6. 更新 test-progress.md
```

### Task C-6: Parent 端测试 — 奖品模块

- [x] **Step 1: 执行 P-015 创建奖品**

```
agent-browser parent context：
1. 导航到奖品管理
2. 创建奖品：名称="玩具车"，所需积分=20，库存=5
3. 截图：reports/evidence/P-015-prize-create.png
4. DB 验证：psql -c "SELECT * FROM prize_stock WHERE name='玩具车'"
5. 更新 test-progress.md
```

- [x] **Step 2: 执行 P-016 创建盲盒 + P-017 库存管理**

```
agent-browser parent context：
1. 创建盲盒奖品，设置内含奖品和概率
2. 截图：reports/evidence/P-016-blindbox.png
3. 修改某奖品库存 +3
4. 截图：reports/evidence/P-017-stock.png
5. DB 验证库存数量
   ./reports/helpers/db-checks.sh stock <prize_id>
6. 判定：创建、修改均成功 → pass/fail
7. 更新 test-progress.md
```

### Task C-7: Parent 端测试 — 兑换模块

- [x] **Step 1: 执行 P-018 查看兑换申请 + P-019 核销**

```
准备工作：需有孩子提交的兑换申请

agent-browser parent context：
1. 查看兑换申请列表
2. 截图：reports/evidence/P-018-exchange-list.png
3. 对某条申请执行核销
4. 截图：reports/evidence/P-019-fulfill.png
5. DB 验证库存扣减
   ./reports/helpers/db-checks.sh stock <prize_id>
6. 判定：核销成功，库存扣减正确 → pass/fail
7. 更新 test-progress.md
```

- [x] **Step 2: 执行 P-020 核销幂等验证**

```
agent-browser parent context：
1. 对已核销订单再次点核销
2. 截图：reports/evidence/P-020-fulfill-dup.png
3. DB 验证：库存不重复扣减
   STOCK_BEFORE=$(./reports/helpers/db-checks.sh stock <prize_id>)
   # 执行重复核销
   STOCK_AFTER=$(./reports/helpers/db-checks.sh stock <prize_id>)
   if [ "$STOCK_BEFORE" = "$STOCK_AFTER" ]; then echo "IDEMPOTENT: OK"; else echo "IDEMPOTENT: FAIL"; fi
4. 判定：重复核销无效 → pass/fail
5. 更新 test-progress.md
```

- [x] **Step 3: 登出 parent context（清除 cookie）**

```
agent-browser 执行 parent 登出，关闭 parent browser context
```

- [x] **Step 4: ✦ 上下文压缩安全点 2 ✦**

确保 `reports/round-1-parent.md` 已生成或 test-progress.md 中 parent 用例全部有状态。

### Task C-8: Child 端测试

**产物**：`reports/round-1-child.md`

- [x] **Step 1: 执行 C-001 孩子设备授权登录（child context）**

```
agent-browser child context（新 context）：
1. 打开 http://localhost:5173/child
2. 选择孩子档案 cici
3. 输入 PIN 180614
4. 截图：reports/evidence/C-001-login.png
5. 捕获登录 API 响应
6. 判定：成功进入孩子首页 → pass/fail
7. 更新 test-progress.md
```

- [x] **Step 2: 执行 C-002 今日任务查看 + C-003 提交任务**

```
agent-browser child context：
1. 查看今日待完成任务列表
2. 截图：reports/evidence/C-002-today.png
3. 选择一个任务点击「提交完成」
4. 截图：reports/evidence/C-003-submit.png
5. 判定：任务状态变为待审核 → pass/fail
6. 更新 test-progress.md
```

- [x] **Step 3: 执行 C-004 奖品列表查看**

```
agent-browser child context：
1. 导航到奖品列表
2. 截图：reports/evidence/C-004-prize-list.png
3. 判定：显示奖品的积分价格和可用库存 → pass/fail
4. 更新 test-progress.md
```

- [x] **Step 4: 执行 C-005 兑换奖品**

```
agent-browser child context：
1. 选择积分足够的奖品点兑换
2. 截图：reports/evidence/C-005-exchange.png
3. DB 验证：
   psql -c "SELECT * FROM exchange_order WHERE child_name='cici'"
   BALANCE_AFTER=$(./reports/helpers/db-checks.sh balance <child_id>)
4. 判定：积分扣减，兑换订单生成 → pass/fail
5. 更新 test-progress.md
```

- [x] **Step 5: 执行 C-006 盲盒抽取**

```
agent-browser child context：
1. 导航到盲盒抽取
2. 点击抽盒
3. 截图：reports/evidence/C-006-blindbox.png
4. DB 验证：
   psql -c "SELECT * FROM exchange_order WHERE type='blindbox' AND child_name='cici'"
5. 更新 test-progress.md
```

- [x] **Step 6: 执行 C-007 历史记录**

```
agent-browser child context：
1. 查看兑换历史/任务历史
2. 截图：reports/evidence/C-007-history.png
3. 判定：历史记录完整正确 → pass/fail
4. 更新 test-progress.md
```

- [x] **Step 7: 登出 child context，生成 round-1-child.md**

```
agent-browser 执行 child 登出，关闭 child browser context
```

```bash
cat > reports/round-1-child.md << 'EOF'
# Round-1 Child 端测试报告

- Module: child
- Cases: C-001 ~ C-007
- Executed: `date -Iseconds`

## 用例结果

| Case ID | 功能 | 状态 | Bug ID | 备注 |
|---------|------|------|--------|------|
| C-001 | child.login | ⬜ | - | - |
| C-002 | child.today-tasks | ⬜ | - | - |
| C-003 | child.submit-task | ⬜ | - | - |
| C-004 | child.prize-list | ⬜ | - | - |
| C-005 | child.exchange | ⬜ | - | - |
| C-006 | child.blindbox-draw | ⬜ | - | - |
| C-007 | child.history | ⬜ | - | - |
EOF
```

- [x] **Step 8: ✦ 上下文压缩安全点 3 ✦**

### Task C-9: 跨模块端到端测试

**产物**：`reports/round-1-cross.md`  
**Design Doc 引用**：§3.1 C4

- [x] **Step 1: 执行 X-001 全链路验证**

```
完整链路（可能涉及切换 3 个 browser context）：
1. [child context] 孩子提交一个新任务 → 截图
2. [parent context] 家长审核通过该任务 → 截图
3. [parent context] 验证积分入账 → DB 查询
4. [child context] 孩子用积分兑换奖品 → 截图
5. [parent context] 家长核销兑换 → 截图
6. DB 最终验证：
   - 积分流水完整（points_ledger → 4 条流水：审核+兑换）
   - 兑换订单状态正确（exchange_order.status = fulfilled）
   - 库存已扣减（prize_stock）
7. 判定：全链路状态一致 → pass/fail
8. 更新 test-progress.md
```

- [x] **Step 2: 生成 round-1-cross.md，记录全链路结果**

```bash
cat > reports/round-1-cross.md << 'EOF'
# Round-1 跨模块端到端测试报告

- Cases: X-001
- Executed: `date -Iseconds`

## 用例结果

| Case ID | 功能 | 状态 | Bug ID | 备注 |
|---------|------|------|--------|------|
| X-001 | cross.full-chain | ⬜ | - | - |
EOF
```

- [x] **Step 3: ✦ 上下文压缩安全点 4 ✦**

### Task C-10: 不变量验证

**产物**：`reports/round-1-invariants.md`  
**Design Doc 引用**：§5.3 不变量用例 I-001~I-006、§5.2 统一验证结构  
**验收标准**：6 条不变量用例全部执行，每例含操作前/后 DB 快照哈希

- [x] **Step 1: 执行 I-001 积分流水 append-only 验证**

```bash
# 操作前快照
HASH_BEFORE=$(./reports/helpers/db-checks.sh ledger-hash)
echo "I-001 before hash: $HASH_BEFORE"

# agent-browser 操作：执行正常任务提交→审核通过，产生积分流水
# ...

# 操作后快照
HASH_AFTER=$(./reports/helpers/db-checks.sh ledger-hash)
echo "I-001 after hash: $HASH_AFTER"

# 精确验证 SQL：检查是否存在 updated_at > created_at 的行（违反 append-only）
VIOLATIONS=$(./reports/helpers/db-checks.sh append-only-check)
if [ "$VIOLATIONS" -eq 0 ]; then echo "I-001: APPEND-ONLY OK"; else echo "I-001: APPEND-ONLY FAIL ($VIOLATIONS violations)"; fi

# 判定：新流水仅在末尾追加，无行内更新 → pass/fail
# 更新 test-progress.md
```

- [x] **Step 2: 执行 I-002 积分余额非负验证**

```
场景：尝试构造余额为负的操作

agent-browser 操作：
1. 查看当前孩子积分余额
2. 使用 parent 端尝试对孩子执行超额扣减（积分调整 -99999）
3. 或者：孩子尝试兑换积分不够的奖品

DB 验证：
BALANCE=$(./reports/helpers/db-checks.sh balance <child_id>)
if [ "$BALANCE" -ge 0 ]; then echo "I-002: BALANCE NON-NEGATIVE OK"; else echo "I-002: BALANCE NON-NEGATIVE FAIL"; fi

预期：系统拒绝操作，提示积分不足
判定：UI 显示错误提示 + 余额仍为非负 → pass/fail
更新 test-progress.md
```

- [x] **Step 3: 执行 I-003 审核幂等验证**

```bash
# 操作前快照
HASH_BEFORE=$(./reports/helpers/db-checks.sh audit-log-hash <task_id>)

# agent-browser 操作：对已审核通过的任务再次点通过
# ...

# 操作后快照：应与操作前一致
HASH_AFTER=$(./reports/helpers/db-checks.sh audit-log-hash <task_id>)
if [ "$HASH_BEFORE" = "$HASH_AFTER" ]; then echo "I-003: AUDIT IDEMPOTENT OK"; else echo "I-003: AUDIT IDEMPOTENT FAIL"; fi

# 验证积分未重复入账
# 更新 test-progress.md
```

- [x] **Step 4: 执行 I-004 兑换快照不可变验证**

```bash
# 操作前快照
HASH_BEFORE=$(./reports/helpers/db-checks.sh snapshot-hash <order_id>)

# agent-browser 操作：无（快照应在创建后不可变）
# 或者触发兑换操作后验证快照未被修改

# 验证快照字段未变更
# 更新 test-progress.md
```

- [x] **Step 5: 执行 I-005 库存非负验证**

```
场景：尝试超额兑换

agent-browser 操作：
1. 查看某奖品库存（假设 stock=1）
2. 孩子兑换该奖品 → 成功
3. 另一个孩子（或重复兑换）→ 应被拒绝

DB 验证：
STOCK=$(./reports/helpers/db-checks.sh stock <prize_id>)
if [ "$STOCK" -ge 0 ]; then echo "I-005: STOCK NON-NEGATIVE OK"; else echo "I-005: STOCK NON-NEGATIVE FAIL"; fi

判定：超额兑换被拒绝 + 库存非负 → pass/fail
更新 test-progress.md
```

- [x] **Step 6: 执行 I-006 核销幂等验证**

```bash
# agent-browser 操作：对已核销的订单再次点击核销

# DB 验证：订单状态不变，库存不重复扣减
psql -c "SELECT status, stock_deducted FROM exchange_order WHERE id='<order_id>'"
# 预期：status=fulfilled 不变，被扣库存量在第二次操作后不变

# 更新 test-progress.md
```

- [x] **Step 7: 生成 round-1-invariants.md**

```bash
cat > reports/round-1-invariants.md << 'EOF'
# Round-1 不变量验证报告

- Cases: I-001 ~ I-006
- Executed: `date -Iseconds`

## 用例结果

| Case ID | 不变量 | 状态 | Bug ID | 操作前 Hash | 操作后 Hash | 备注 |
|---------|--------|------|--------|------------|------------|------|
| I-001 | 积分流水 append-only | ⬜ | - | - | - | - |
| I-002 | 积分余额非负 | ⬜ | - | - | - | - |
| I-003 | 审核幂等 | ⬜ | - | - | - | - |
| I-004 | 兑换快照不可变 | ⬜ | - | - | - | - |
| I-005 | 库存非负 | ⬜ | - | - | - | - |
| I-006 | 核销幂等 | ⬜ | - | - | - | - |
EOF
```

- [x] **Step 8: ✦ 上下文压缩安全点 5 ✦**

### Task C-11: 不变量违反紧急处理（条件执行）

若 C-10 中发现核心不变量被破坏（Design Doc §9.3）：
- 立即记录 Critical bug
- 执行 `pg_dump > reports/evidence/critical-<bug-id>-dump.sql`
- 截图 + 后端日志 + 网络响应全部留证
- 跳过依赖该不变量的后续用例

- [x] **Step 1: 检查 C-10 结果，如有不变量违反则执行紧急处理流程**
- [x] **Step 2: 将 dump 和证据归档到 `reports/evidence/`**

### Task C-12: 安全基线验证

**产物**：`reports/round-1-security.md`  
**Design Doc 引用**：§6.2 三层观测矩阵、§6.5 安全用例 S-001~S-006  
**验收标准**：6 条安全用例全部覆盖三层观测

- [x] **Step 1: 执行 S-001 parent 登录响应安全检查**

```
Layer 1 - API 响应：
- 从 reports/evidence/network/P-001-response.json 中提取
- grep 检查：不应包含 "117315Akers"、"117315"、"Akers"、"password="
- 检查 token 格式（应为 JWT 脱敏格式，不含明文密码）

Layer 2 - 浏览器存储：
- 通过 agent-browser 执行 localStorage/sessionStorage/document.cookie 读取
- 保存到 reports/evidence/storage/S-001-storage.txt
- grep 敏感字符串

Layer 3 - 后端日志：
./reports/helpers/security-check.sh logs-since <test-start>
```

- [x] **Step 2: 执行 S-002 admin 登录响应安全检查**

```
Layer 1：从 A-001 的 API 响应中检查
Layer 2：admin context 的浏览器存储扫描
Layer 3：后端日志扫描
```

- [x] **Step 3: 执行 S-003 child 设备授权（PIN 输入）安全检查**

```
Layer 1：child 登录 API 响应中检查 PIN 明文
Layer 2：child context 的浏览器存储扫描
Layer 3：后端日志扫描 PIN="180614" 或 "pin=180614"
```

- [x] **Step 4: 执行 S-004 任务审核 API 响应 + S-005 兑换 API 响应**

```
通过 agent-browser 捕获审核 API 和兑换 API 的网络响应
检查是否返回敏感字段
```

- [x] **Step 5: 执行 S-006 后端日志全量扫描**

```bash
# 全量扫描整个测试期间的 backend.log
./reports/helpers/security-check.sh logs

# 任何命中 → Critical bug（Design Doc §6.2）
```

- [x] **Step 6: 生成 round-1-security.md**

```bash
cat > reports/round-1-security.md << 'EOF'
# Round-1 安全基线验证报告

- Cases: S-001 ~ S-006
- Executed: `date -Iseconds`

| Case ID | 场景 | Layer1 | Layer2 | Layer3 | 总体 | Bug ID |
|---------|------|--------|--------|--------|------|--------|
| S-001 | parent 登录 | ⬜ | ⬜ | ⬜ | ⬜ | - |
| S-002 | admin 登录 | ⬜ | ⬜ | ⬜ | ⬜ | - |
| S-003 | child PIN | ⬜ | ⬜ | ⬜ | ⬜ | - |
| S-004 | 审核 API | ⬜ | N/A | ⬜ | ⬜ | - |
| S-005 | 兑换 API | ⬜ | N/A | ⬜ | ⬜ | - |
| S-006 | 日志全量扫描 | N/A | N/A | ⬜ | ⬜ | - |
EOF
```

- [x] **Step 7: ✦ 上下文压缩安全点 6 ✦**

---

## Stage D: Round-1 汇总与 Bug 初始化（2 任务）

**Design Doc 引用**：§3.1 Stage D、§7 Bug 文件结构与生命周期  
**产物路径**：`reports/round-1.md`、`reports/bug-NNN-*.md`（每 bug 一份）  
**安全点**：本阶段完成后触发上下文压缩安全点 7（进入修复阶段前最后一次压缩）

### Task D-1: 汇总 `reports/round-1.md`

- [x] **Step 1: 从各子报告和 test-progress.md 提取全部 bug 清单**

```bash
# 汇总格式
cat > reports/round-1.md << 'EOF'
# Round-1 全量测试报告

- Change: e2e-system-test-and-fix
- Generated: `date -Iseconds`
- Tester: agent-browser
- Environment: local dev (PG:35432 / Redis:6379 / Backend:8080 / Frontend:5173)
- Test account: 13600049114/117315Akers (admin+parent) / cici/PIN 180614 (child)

## 执行摘要

| 模块 | 总用例 | 通过 | 失败 | 缺陷数 | 完成率 |
|------|--------|------|------|--------|--------|
| A admin | 5 | - | - | - | -% |
| P parent | 20 | - | - | - | -% |
| C child | 7 | - | - | - | -% |
| X cross | 1 | - | - | - | -% |
| I invariants | 6 | - | - | - | -% |
| S security | 6 | - | - | - | -% |
| **总计** | **45** | **-** | **-** | **-** | **-**% |

## 缺陷清单（按严重度排序）

### Critical
| Bug ID | 模块 | 标题 | 复现步骤 | 证据 |
|--------|------|------|---------|------|

### High
| Bug ID | 模块 | 标题 | 复现步骤 | 证据 |
|--------|------|------|---------|------|

### Medium
| Bug ID | 模块 | 标题 | 复现步骤 | 证据 |
|--------|------|------|---------|------|

### Low
| Bug ID | 模块 | 标题 | 复现步骤 | 证据 |
|--------|------|------|---------|------|

### Cosmetic
| Bug ID | 模块 | 标题 | 复现步骤 | 证据 |
|--------|------|------|---------|------|

## 不变量验证结果摘要

| Case ID | 不变量 | 结果 | 关联 Bug |
|---------|--------|------|---------|
| I-001 | 积分流水 append-only | ⬜ | - |
| I-002 | 积分余额非负 | ⬜ | - |
| I-003 | 审核幂等 | ⬜ | - |
| I-004 | 兑换快照不可变 | ⬜ | - |
| I-005 | 库存非负 | ⬜ | - |
| I-006 | 核销幂等 | ⬜ | - |

## 安全基线验证结果摘要

| Case ID | 场景 | Layer1 | Layer2 | Layer3 | 总体 |
|---------|------|--------|--------|--------|------|
| S-001 | parent 登录 | ⬜ | ⬜ | ⬜ | ⬜ |
| S-002 | admin 登录 | ⬜ | ⬜ | ⬜ | ⬜ |
| S-003 | child PIN | ⬜ | ⬜ | ⬜ | ⬜ |
| S-004 | 审核 API | ⬜ | N/A | ⬜ | ⬜ |
| S-005 | 兑换 API | ⬜ | N/A | ⬜ | ⬜ |
| S-006 | 日志全量扫描 | N/A | N/A | ⬜ | ⬜ |
EOF
```

- [x] **Step 2: 从 test-progress.md 的 Bug 状态表读取数据，填入 round-1.md**

- [x] **Step 3: 确认每行数据已填入，无空字段**

### Task D-2: 为所有 Bug 创建单文件

**Design Doc 引用**：§7.2 Bug 单文件模板、§7.3 证据目录结构、§7.4 编号规则

- [x] **Step 1: 为 round-1.md 中每个 bug 创建 `reports/bug-NNN-<slug>.md`**

```bash
# 每个 bug 使用 Design Doc §7.2 的模板
# 例如 bug-001：
cat > reports/bug-001-<slug>.md << 'BUGEOF'
# Bug-001: <简短标题>

- Severity: Critical | High | Medium | Low | Cosmetic
- Module: auth | family | task | task-review | points | prize | exchange | admin | web-frontend
- Status: open
- Discovered in: round-1 / case <CASE-ID>
- Fixed in commit: （空则未修）
- Regression: round-2 / pass or fail

## 复现步骤

1. ...
2. ...
3. ...

## 预期

<正确行为>

## 实际

<观察到的错误行为>

## 证据

- 截图：`reports/evidence/bug-NNN-<step>.png`
- API 响应：`reports/evidence/bug-NNN-response.json`
- DB 状态：`reports/evidence/bug-NNN-db.json`
- 后端日志片段：`reports/evidence/bug-NNN-backend.log`

## 根因（修复阶段填写）

## 修复方案（修复阶段填写）

## 单 bug 回归（修复后填写）

## 阶段回归（模块批量回归后填写）

## 最终回归（final 全量回归后填写）
BUGEOF

# Critical/High 缺陷额外附根因初判（spec spec.md Req 4 Scenario）
```

- [x] **Step 2: 更新 test-progress.md 中的 Bug 状态表，每个 bug 一行**

- [x] **Step 3: 确认所有证据文件路径在 bug-NNN-*.md 中正确引用**

- [x] **Step 4: ✦ 上下文压缩安全点 7 ✦（进入修复阶段前）**

确保 `reports/round-1.md`、所有 `bug-NNN-*.md`、`reports/test-progress.md` 已落盘。这是进入修复阶段前最后的全量压缩点。

---

## Stage E: 缺陷修复（9 模块批次）

**Design Doc 引用**：§3.2 模块修复顺序、§7 状态机、§8 修复阶段工作流  
**依赖顺序**：auth → family → task → task-review → points → prize → exchange → admin → web-frontend（不可并行，不可倒序）  
**安全点**：每个模块修复 commit 后触发一次上下文压缩（Design Doc §3.3 安全点 8，共 9 次）  
**范围扩张**：任何修复涉及 schema 变更 / 跨 3+ 模块 / 新增 capability 时暂停（Design Doc §8.3）

### Task E-1: 修复 auth 模块缺陷

**模块依赖**：无（基础模块）

- [x] **Step 1: 从 round-1.md 过滤 auth 模块的 bug，按 severity 排序**
- [x] **Step 2: 对每个 auth bug 执行修复 SOP（Design Doc §8.1）**

```bash
# 每 bug SOP：
# a) 确保 reports/bug-NNN-<slug>.md Status=open（已在 D-2 创建）
# b) 调研代码定位根因：codegraph_explore / grep
# c) 修改代码
# d) Status=in-progress
# e) 单 bug 回归：通过 agent-browser 重放该 bug 的复现用例
#    通过 → Status=fixed，填回归结果
#    失败 → Status=in-progress，继续改（最多 3 轮后升级 oracle 评审）
# f) bug-NNN 单文件完成
```

- [x] **Step 3: 模块阶段回归**

```bash
# 该模块全部 bug Status=fixed 后：
# a) 重放该模块全部用例（auth 相关 case）
# b) 所有 bug Status=verified
# c) 写 reports/round-2-auth.md
```

- [x] **Step 4: 批量 commit**

```bash
git add server/...  # auth 模块改动的文件
git commit -m "fix(e2e/round-1/auth): bug-NNN, bug-NNN, ...

模块批量修复：

- bug-NNN: <一句话描述> (Critical/High/Medium/Low/Cosmetic)
- ...

回归：
- 单 bug 回归全部通过
- 模块阶段回归：reports/round-2-auth.md"
```

- [x] **Step 5: ✦ 上下文压缩安全点（auth commit 后）✦**

### Task E-2: 修复 family 模块缺陷

**模块依赖**：依赖 auth（用户身份已正确）

- [x] **Step 1: 从 round-1.md 过滤 family 模块的 bug**
- [x] **Step 2: 逐 bug：调研根因→改代码→单 bug 回归（SOP §8.1）**
- [x] **Step 3: 模块阶段回归 → 写 round-2-family.md**
- [x] **Step 4: 批量 commit**
- [x] **Step 5: ✦ 上下文压缩安全点 ✦**

### Task E-3: 修复 task 模块缺陷

**模块依赖**：依赖 family（家庭和孩子档案就绪）

- [x] **Step 1: 过滤 task 模块 bug**
- [x] **Step 2: 逐 bug 修复（SOP §8.1）**
- [x] **Step 3: 模块阶段回归 → 写 round-2-task.md**
- [x] **Step 4: 批量 commit**
- [x] **Step 5: ✦ 上下文压缩安全点 ✦**

### Task E-4: 修复 task-review 模块缺陷

**模块依赖**：依赖 task + points  
**重点**：审核幂等、事务原子（Design Doc §8 重点标注）

- [x] **Step 1: 过滤 task-review 模块 bug**
- [x] **Step 2: 逐 bug 修复（SOP §8.1），重点验证审核幂等（P-012/I-003）**
- [x] **Step 3: 模块阶段回归 → 写 round-2-task-review.md**
- [x] **Step 4: 批量 commit**
- [x] **Step 5: ✦ 上下文压缩安全点 ✦**

### Task E-5: 修复 points 模块缺陷

**模块依赖**：依赖 task-review（积分产生）  
**重点**：余额非负、流水不可变（Design Doc §5 核心不变量）

- [x] **Step 1: 过滤 points 模块 bug**
- [x] **Step 2: 逐 bug 修复（SOP §8.1），重点验证 I-001/I-002**
- [x] **Step 3: 模块阶段回归 → 写 round-2-points.md**
- [x] **Step 4: 批量 commit**
- [x] **Step 5: ✦ 上下文压缩安全点 ✦**

### Task E-6: 修复 prize 模块缺陷

**模块依赖**：依赖 points（积分购买奖品）

- [x] **Step 1: 过滤 prize 模块 bug**
- [x] **Step 2: 逐 bug 修复（SOP §8.1）**
- [x] **Step 3: 模块阶段回归 → 写 round-2-prize.md**
- [x] **Step 4: 批量 commit**
- [x] **Step 5: ✦ 上下文压缩安全点 ✦**

### Task E-7: 修复 exchange 模块缺陷

**模块依赖**：依赖 prize + points  
**重点**：核销幂等、库存扣减原子（Design Doc §5 核心不变量）

- [x] **Step 1: 过滤 exchange 模块 bug**
- [x] **Step 2: 逐 bug 修复（SOP §8.1），重点验证 I-004/I-005/I-006**
- [x] **Step 3: 模块阶段回归 → 写 round-2-exchange.md**
- [x] **Step 4: 批量 commit**
- [x] **Step 5: ✦ 上下文压缩安全点 ✦**

### Task E-8: 修复 admin 端缺陷

**模块依赖**：独立端（最后修复，避免对其他模块的依赖干扰）

- [x] **Step 1: 过滤 admin/web 模块的 bug**
- [x] **Step 2: 逐 bug 修复（SOP §8.1）**
- [x] **Step 3: 模块阶段回归 → 写 round-2-admin.md**
- [x] **Step 4: 批量 commit**
- [x] **Step 5: ✦ 上下文压缩安全点 ✦**

### Task E-9: 修复前端 web-frontend 缺陷

**模块依赖**：无后端依赖，Cosmetic 类集中修复  
**注意**：包括三端 UmiJS 页面的 Cosmetic 级视觉问题

- [x] **Step 1: 过滤 web-frontend 模块的 bug（含所有 Cosmetic 级）**
- [x] **Step 2: 逐 bug 修复（SOP §8.1），重点确认 UI 渲染正确**
- [x] **Step 3: 模块阶段回归 → 写 round-2-web-frontend.md**
- [x] **Step 4: 批量 commit**
- [x] **Step 5: ✦ 上下文压缩安全点 ✦（最后一次修复阶段压缩点）**

---

## Stage F: Round-2 最终回归测试（4 任务）

**Design Doc 引用**：§3.1 Round-2、§8.1 步骤 4  
**产物路径**：`reports/final.md`  
**依赖**：所有模块修复已 commit 且 Status=verified  
**验收标准**：全量重放必须零缺陷；若发现新 bug 则回到 Stage E 新增修复

### Task F-1: 重置数据库并重放完整 test-plan.md

- [x] **Step 1: 全量重置数据库（Design Doc §9.1 允许最终回归前 reset）**

```bash
bash scripts/reset-dev-db.sh
# 预期：schema 重建，初始数据可用
```

- [x] **Step 2: 确认环境健康**

```
curl -s http://localhost:8080/api/health → UP
curl -s http://localhost:5173 → HTML（UmiJS）
```

- [x] **Step 3: 重放 test-plan.md 全部 45 个用例**

```
按 test-plan.md 的 Case ID 顺序逐用例执行 agent-browser 操作
执行方式同 Stage C（C-1~C-12），但预期全部 pass
每完成一个用例更新 test-progress.md（Round=2）

特别注意不变量用例（I-001~I-006）和安全用例（S-001~S-006），
这些必须在回归中再次验证
```

- [x] **Step 4: 确认所有用例 pass**

```bash
# 统计
PASS_COUNT=$(grep -c "pass" reports/test-progress.md | head -1)
echo "Regression pass count: $PASS_COUNT / 45"
# 预期：45/45 pass
```

### Task F-2: 处理回归中发现的新缺陷

- [x] **Step 1: 如有失败用例 → 记录新 bug（bug-NNN-side 后缀，Design Doc §7.4）**

```
发现新 bug → 创建 bug-NNN-side-<slug>.md
→ 回到 Stage E 对应模块执行修复 SOP
→ 补充一轮回归 → 写 round-3.md（如有）
```

- [x] **Step 2: 循环直至零缺陷**

### Task F-3: 生成 `reports/final.md`

- [x] **Step 1: 汇总最终回归结果**

```bash
cat > reports/final.md << 'EOF'
# E2E 系统测试最终回归报告

- Change: e2e-system-test-and-fix
- Generated: `date -Iseconds`
- Round: 2 (final)
- Environment: local dev (PG:35432 / Redis:6379 / Backend:8080 / Frontend:5173)

## 回归结果

| 指标 | 值 |
|------|-----|
| 总用例数 | 45 |
| 通过 | ⬜ |
| 失败 | ⬜ |
| 新发现缺陷 | ⬜ |
| 回归轮次 | ⬜ |

## 模块明细

| 模块 | 用例数 | 通过 | 失败 |
|------|--------|------|------|
| A admin | 5 | ⬜ | ⬜ |
| P parent | 20 | ⬜ | ⬜ |
| C child | 7 | ⬜ | ⬜ |
| X cross | 1 | ⬜ | ⬜ |
| I invariants | 6 | ⬜ | ⬜ |
| S security | 6 | ⬜ | ⬜ |

## 缺陷状态总表

| Bug ID | 严重度 | 模块 | 状态 | 发现于 | 修复 commit |
|--------|--------|------|------|--------|------------|
| - | - | - | ⬜ | - | - |

> 所有 bug Status=closed ✅ 零缺陷达成

## 不变量验证（回归）

| Case ID | 不变量 | 结果 |
|---------|--------|------|
| I-001 | 积分流水 append-only | ⬜ |
| I-002 | 积分余额非负 | ⬜ |
| I-003 | 审核幂等 | ⬜ |
| I-004 | 兑换快照不可变 | ⬜ |
| I-005 | 库存非负 | ⬜ |
| I-006 | 核销幂等 | ⬜ |

## 安全基线验证（回归）

| Case ID | 场景 | 三层结果 |
|---------|------|---------|
| S-001~S-006 | 全部安全用例 | ⬜ |
EOF
```

- [x] **Step 2: 将所有 bug 状态更新为 closed**

```bash
for f in reports/bug-*.md; do
  sed -i 's/Status: .*/Status: closed/' "$f"
  echo "- [x] Updated $f to closed"
done
```

### Task F-4: 确认所有报告产物齐全

- [x] **Step 1: 校验 reports/ 目录**

```bash
echo "=== 报告产物完整性检查 ==="
required_files=(
  reports/test-plan.md
  reports/round-1.md
  reports/final.md
)
for f in "${required_files[@]}"; do
  [ -f "$f" ] && echo "✅ $f" || echo "❌ MISSING: $f"
done

# 检查 bug 文件
bug_count=$(ls reports/bug-*.md 2>/dev/null | wc -l)
echo "Found $bug_count bug files"

# 检查证据目录
evidence_count=$(find reports/evidence -type f 2>/dev/null | wc -l)
echo "Found $evidence_count evidence files"
```

- [x] **Step 2: 确认 round-1.md 和 final.md 中所有字段填写完整，无空字段**

---

## Stage G: 归档准备（4 任务）

**Design Doc 引用**：OpenSpec tasks.md §6  
**产物路径**：`openspec/changes/e2e-system-test-and-fix/`  
**依赖**：Round-2 零缺陷通过

### Task G-1: 在 design.md 末尾追加测试结果摘要

- [x] **Step 1: 从 round-1.md 和 final.md 提取统计摘要**

```bash
# 格式示例
echo "
---

## 测试结果摘要（自动追加）

- **Change**: e2e-system-test-and-fix
- **执行日期**: `date -Iseconds`
- **总用例**: 45
- **Round-1 发现的缺陷**: N（Critical X / High Y / Medium Z / Low W / Cosmetic V）
- **修复数量**: N（全部修复）
- **回归轮次**: 2
- **最终回归**: 零缺陷 ✅

### 模块修复分布

| 模块 | 缺陷数 | 修复 commit |
|------|--------|------------|
| auth | - | <sha> |
| family | - | <sha> |
| task | - | <sha> |
| task-review | - | <sha> |
| points | - | <sha> |
| prize | - | <sha> |
| exchange | - | <sha> |
| admin | - | <sha> |
| web-frontend | - | <sha> |
" >> docs/superpowers/specs/2026-07-18-e2e-system-test-and-fix-design.md
```

### Task G-2: 更新 tasks.md 确认所有任务已完成

- [x] **Step 1: 在 tasks.md 中将所有 checkbox 标记为完成**

```bash
# 替换所有 [ ] 为 [x]（已在执行过程中逐步标记）
# 确认无遗漏
grep -c "\[ \]" openspec/changes/e2e-system-test-and-fix/tasks.md || echo "All tasks completed"
```

### Task G-3: 运行 comet guard 进入 verify 阶段

- [x] **Step 1: 运行 comet 状态检查**

```bash
# 确认设计文档已注册
cat .comet/state 2>/dev/null || echo "No .comet/state file"

# 运行 verify 阶段
comet guard e2e-system-test-and-fix verify --apply
# 预期：进入 verify 阶段
```

### Task G-4: 按 comet-verify 流程完成验证报告

- [x] **Step 1: 检查所有产物齐全（同 F-4）**
- [x] **Step 2: 验证全部 bug 状态为 closed**

```bash
grep -c "Status: closed" reports/bug-*.md
OPEN_BUGS=$(grep -l "Status: open\|Status: investigating\|Status: in-progress" reports/bug-*.md 2>/dev/null)
if [ -n "$OPEN_BUGS" ]; then echo "⚠️ Open bugs exist: $OPEN_BUGS"; else echo "✅ All bugs closed"; fi
```

- [x] **Step 3: 准备归档摘要，准备执行 /comet-verify**

---

## 附录

### A. 上下文压缩安全点速查

| # | 触发时刻 | 前置条件 | 设计引用 |
|---|---------|---------|---------|
| 1 | C-1 完成后 | round-1-admin.md 落盘 | §3.3 |
| 2 | C-7 完成后 | round-1-parent.md 落盘 | §3.3 |
| 3 | C-8 Step 7 后 | round-1-child.md 落盘 | §3.3 |
| 4 | C-9 完成后 | round-1-cross.md 落盘 | §3.3 |
| 5 | C-10 完成后 | round-1-invariants.md 落盘 | §3.3 |
| 6 | C-12 完成后 | round-1-security.md 落盘 | §3.3 |
| 7 | D 完成后 | round-1.md 落盘 + 全部 bug-*.md 就绪 | §3.3 |
| 8 | E-1~E-9 每模块 commit 后（共 9 次） | round-2-<module>.md 落盘 | §3.3 |

### B. 模块依赖顺序速查

```
auth ─→ family ─→ task ─→ task-review ─→ points ─→ prize ─→ exchange ─→ admin ─→ web-frontend
 ↑无依赖 │依赖 auth│依赖 family│依赖 task+points│依赖 task-review│依赖 points│依赖 prize+points│独立端│Cosmetic集中
```

### C. Case ID 编号范围

| 前缀 | 模块 | 用例数 | 编号范围 |
|------|------|--------|---------|
| A | admin | 5 | A-001~A-005 |
| P | parent | 20 | P-001~P-020 |
| C | child | 7 | C-001~C-007 |
| X | cross-module | 1 | X-001 |
| I | invariants | 6 | I-001~I-006 |
| S | security | 6 | S-001~S-006 |

### D. 异常处理快速参考

| 异常 | 动作 |
|------|------|
| 后端启动失败 | Critical bug-001，跳过依赖用例 |
| 前端启动失败 | Critical bug-002，跳过前端依赖用例 |
| DB 重置失败 | 立即停止，问用户手动恢复 |
| agent-browser 不可用 | 立即停止，问用户切 Playwright |
| 核心不变量破坏 | Critical bug，保留 DB dump，跳过依赖用例 |
| 修复引入新 bug | 最多 3 轮修复 → 升级 oracle 评审 |
| 范围扩张触发 | 暂停 → 问用户决策 → 记录到 scope-decisions.md |
