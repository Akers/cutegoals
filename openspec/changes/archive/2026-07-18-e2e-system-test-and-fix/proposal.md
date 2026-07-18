## Why

CuteGoals 2.0 经过多次功能迭代后，系统缺少一次完整的端到端（E2E）质量验证。各模块（admin/parent/child 三端 + 9 个后端领域）虽然在单元测试和部分组件测试层面有覆盖，但跨模块、跨角色的真实业务流从未通过模拟真实用户操作的方式被系统性地遍历过。为了在投入更大规模用户之前发现并修复所有阻塞性和细节缺陷，需要对系统做一次「真实启动 + agent-browser 实际操作 + 修复 + 回归」的完整闭环，覆盖管理员初始化、家长管理家庭/任务/审核/积分/奖品/兑换、孩子完成/提交/兑换/盲盒等全部主流程与边界场景。

## What Changes

- 制定覆盖三端（admin / parent / child）所有 UI 可达功能的端到端测试计划，按模块组织测试用例清单。
- 在本地 dev 环境实际启动 PostgreSQL（已运行于 :35432）、Redis、Spring Boot 后端（:8080）和前端 dev server（:5173），并对数据库执行 `scripts/reset-dev-db.sh` 重置为干净初始状态。
- 使用 `agent-browser` 以真实用户身份（管理员/家长 13600049114/117315Akers；孩子 cici/PIN 180614）逐项执行测试用例，覆盖正常路径、关键边界、不变量校验和安全基线。
- 形成结构化测试报告，按严重度（Critical / High / Medium / Low / Cosmetic）分级记录所有发现的缺陷，附复现步骤、证据截图与影响范围。
- 针对每个缺陷制定修复方案，在当前 change 内一次性修复全部级别缺陷（Critical / High / Medium / Low / Cosmetic 全部纳入范围）。
- 修复完成后进行回归测试，验证所有缺陷已消除且未引入新问题，循环直至测试报告为零缺陷。

## Capabilities

### New Capabilities

- `system-e2e-test-coverage`: 端到端系统测试能力，定义对 admin/parent/child 三端所有功能的真实启动 + agent-browser 操作式覆盖要求、缺陷分级与回归闭环规则。

### Modified Capabilities

<!-- 本 change 是新增测试能力并修复测试中发现的缺陷；任何对现有功能行为的实质性修改（如审核幂等、积分流水不变量等）都将以 bug 修复形式落地，并在 specs 中以 delta 形式记录。测试启动后根据实际发现的缺陷再补充 Modified Capabilities。 -->

## Impact

- **后端**：`server/` 下 9 个领域模块（auth、family、task、task-review、points、prize、exchange、instance-management、web）均可能在测试中发现缺陷并需要修复。
- **前端**：`web/src/admin|parent|child` 三端页面、共享组件、API 封装均需接受端到端验证；当前前端实际基于 UmiJS（`web/src/.umi` 存在），与 README 描述的「Vite」存在偏差，需在测试中确认实际运行时栈。
- **数据**：测试开始前执行 `scripts/reset-dev-db.sh` 重置 PostgreSQL `cutegoals` schema，所有历史数据被清除；测试过程中产生的家庭/任务/积分/奖品/兑换数据保留在 dev 库中用于回归。
- **核心不变量**：测试必须显式验证「积分余额和库存不得为负、积分流水/审核历史/兑换快照不可变、审核/兑换/退款幂等且事务原子」等核心不变量不被破坏。
- **安全基线**：测试必须显式验证「明文密码、PIN、令牌、完整手机号不被记录到日志或响应中」。
- **依赖服务**：测试依赖本机 PostgreSQL:35432 与 Redis:6379 可用，且 8080/5173 端口空闲。
- **范围外**：不跑 Playwright（`e2e/tests/`）；不做性能/压力测试；不做安全渗透测试；不做多实例/多家庭边界（MVP 单实例单家庭）。
