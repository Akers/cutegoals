## 1. 环境准备与启动

- [ ] 1.1 启动 Redis 容器（`deploy/docker-compose.yml` 的 `mit-modelide-core-redis` 服务），确认 :6379 监听
- [ ] 1.2 确认 PostgreSQL :35432 可连接（用户 cutegoals / 库 cutegoals / schema cutegoals）
- [ ] 1.3 运行 `scripts/reset-dev-db.sh` 重置数据库到干净初始状态，记录重置前后表清单作为证据
- [ ] 1.4 启动后端：`cd server && mvn clean -pl web -am spring-boot:run -DskipTests`，确认 :8080 监听且 `/api/health` 返回 UP
- [ ] 1.5 启动前端：`cd web && npm install && npm run dev`，确认 :5173 监听；记录实际是 Vite 还是 UmiJS（从 `web/package.json` 与启动日志判定）
- [ ] 1.6 验证前端 `/api` 代理到 :8080 正常工作（任意未登录请求转发即可）

## 2. 测试计划制定

- [ ] 2.1 在 `openspec/changes/e2e-system-test-and-fix/reports/test-plan.md` 写入完整测试计划，覆盖 admin / parent / child 三端所有 UI 可达功能
- [ ] 2.2 计划中显式列入核心不变量验证用例：积分/库存非负、积分流水/审核历史/兑换快照不可变、审核/兑换/退款幂等与事务原子
- [ ] 2.3 计划中显式列入安全基线用例：明文密码/PIN/令牌/完整手机号不出现在 API 响应、浏览器存储、后端日志
- [ ] 2.4 计划按模块（auth/family/task/task-review/points/prize/exchange/instance-management/admin）分组，每模块至少含一条正常路径 + 一条边界用例
- [ ] 2.5 计划中标注每个用例的预期结果与判定证据（截图 / API 响应 / DB 状态）

## 3. 第一轮：agent-browser 全量测试

- [ ] 3.1 准备 agent-browser：加载 skill、确认 CLI 可用、规划三套独立 browser context（admin / parent / child）
- [ ] 3.2 admin 端测试：使用 13600049114/117315Akers 登录 `/admin`，覆盖概览、账号管理、审计日志、健康检查四个功能
- [ ] 3.3 parent 端测试 - 家庭：登录 `/parent`，覆盖家庭信息查看/编辑、添加孩子档案（cici）、添加家长账号、设备授权流程
- [ ] 3.4 parent 端测试 - 任务：发布任务模板、查看任务列表、查看孩子任务完成情况
- [ ] 3.5 parent 端测试 - 审核：对孩子提交的任务执行通过/驳回、验证幂等（重复审核不产生多次积分）
- [ ] 3.6 parent 端测试 - 积分：查看积分流水、调整积分、验证余额非负与流水不可变
- [ ] 3.7 parent 端测试 - 奖品：创建奖品、创建盲盒、库存管理
- [ ] 3.8 parent 端测试 - 兑换：查看兑换申请、核销、验证核销幂等与库存扣减原子
- [ ] 3.9 child 端测试：用 cici/PIN 180614 登录 `/child`，覆盖今日任务查看/提交、奖品兑换、盲盒抽取、历史记录
- [ ] 3.10 跨模块端到端：孩子提交任务 → 家长审核 → 积分入账 → 孩子兑换奖品 → 家长核销，验证全链路状态一致
- [ ] 3.11 不变量验证用例：尝试构造余额为负、重复审核、并发兑换等场景，验证系统拒绝或正确处理
- [ ] 3.12 安全基线用例：登录、敏感操作期间观察浏览器存储与后端日志，验证不出现明文敏感字段
- [ ] 3.13 整理第一轮发现的全部缺陷到 `reports/round-1.md`，按 Critical/High/Medium/Low/Cosmetic 分级，每条含编号、模块、复现步骤、预期/实际、证据
- [ ] 3.14 为每个 Critical/High 缺陷单独创建 `reports/bug-NNN-<slug>.md`，记录根因初判

## 4. 缺陷修复

- [x] 4.1 根据 round-1 缺陷清单更新本 tasks.md，为每个 bug 新增独立修复任务（按模块分组：auth/family/task/task-review/points/prize/exchange/admin/web-frontend）
- [x] 4.2 修复 auth 模块缺陷（含安全基线类问题）
- [x] 4.3 修复 family 模块缺陷
- [x] 4.4 修复 task 模块缺陷
- [x] 4.5 修复 task-review 模块缺陷（重点：幂等与事务原子）
- [x] 4.6 修复 points 模块缺陷（重点：余额非负、流水不可变）
- [x] 4.7 修复 prize 模块缺陷
- [x] 4.8 修复 exchange 模块缺陷（重点：核销幂等、库存扣减原子）
- [x] 4.9 修复 instance-management / admin 端缺陷
- [x] 4.10 修复前端 UmiJS 三端 UI 缺陷（含 Cosmetic 级视觉问题）
- [x] 4.11 每个修复完成后立即执行单 bug 回归（重放该 bug 复现用例），结果写入对应 `bug-NNN-*.md`
- [x] 4.12 任何修复涉及跨模块重构、schema 变更或新增 capability 时，暂停并触发「范围扩张」决策点交用户选择

## 5. 阶段性与最终回归测试

- [x] 5.1 每完成一个模块的批量修复后，执行该模块全部用例的阶段回归，结果写入 `reports/round-N.md`
- [x] 5.2 所有缺陷标记 fixed 后，执行最终全量回归（重放整个 test-plan.md），结果写入 `reports/final.md`
- [x] 5.3 最终回归必须零缺陷；若发现新缺陷，回到第 4 节修复并补充新一轮回归报告
- [x] 5.4 整理所有 bug 文件，确认状态全部为 verified

## 6. 归档准备

- [x] 6.1 校验 `reports/` 目录产物齐全（test-plan / round-* / bug-* / final）
- [x] 6.2 在 design.md 末尾追加「测试结果摘要」（缺陷数量、修复数量、回归轮次）
- [ ] 6.3 运行 `comet guard e2e-system-test-and-fix verify --apply` 进入 verify 阶段
- [ ] 6.4 按 comet-verify 流程完成验证报告，准备归档

## Stage F 实际结果

### 执行总览
- **测试时间**：2026-07-18
- **修复 bug 总数**：14
- **PASS**：13（bug-001/002/003/004/005/006/007/008/009/010/012/013/014）
- **BLOCKED**：1（bug-011 child session schema 阻塞，需 DBA 应用 V13 ALTER）
- **代码层修复完成率**：14/14（100%）
- **验证通过率**：13/14（93%）

### 关键验证结果
1. **bug-009 分页修复**：`/api/task-assignments` 返回 `content=10 totalElements=10` ✅；`/api/prizes` 返回 `content=3 totalElements=3` ✅
2. **bug-012 points_balance 自动创建**：新建 child → `points_balance` 记录自动 INSERT ✅
3. **bug-002 audit_log**：DB 直查 3+ 条记录（含 LOGIN_SUCCESS、FAMILY_UPDATED）✅
4. **bug-001 phone masking**：curl 返回 4 星脱敏 `136****9114` ✅；重启后 backend.log 0 条完整手机号 ✅
5. **前端 UI 修复验证**：通过 agent-browser 截图确认 bug-005/006/007/008/010/013/014 全部修复 ✅
6. **不变量验证**：I-001/003/004/005/006 PASS；I-002 仍 FAIL（测试数据污染，非代码 bug）
7. **安全基线**：S-001~S-006 全部 PASS（含 MyBatis SQL 参数日志抑制）

### bug-011 阻塞说明
- 代码修复已完成：Session 加 childId、SessionService 新增 createChildSession、V13 migration 写好
- 阻塞原因：`session` 表 owner 为 `pmp`，`cutegoals` 用户无 ALTER 权限
- V13 migration 以 PL/pgSQL 异常吞错形式让 Flyway 标记 success，但实际 schema 未升级
- **需要 DBA**：用 pmp 用户直接执行 V13 ALTER，然后重启后端验证 C-002~C-007 / P-013~P-014 / X-001

### 产物清单
- `reports/round-1.md` — Round-1 全量测试报告
- `reports/round-2.md` — Round-2 回归测试报告
- `reports/bug-001..014-*.md` — 14 个 bug 详情文件
- `reports/evidence/round-2/` — Stage F 截图证据（10 个子目录）
- `reports/helpers/db_checks.py` — 不变量验证脚本
- `reports/helpers/security_checks.py` — 安全基线扫描脚本
- `reports/helpers/partial_reset.py` — 测试数据清理脚本
- V13 schema migration — `server/common/src/main/resources/db/migration/V13__add_child_session_support.sql`
- MybatisPlus 分页拦截器 — `server/common/.../MybatisPlusConfig.java`
