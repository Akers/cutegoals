# 目标

补齐 `e2e/tests/resubmission-controls.spec.ts` 中 4 个自声明 skip 的骨架用例，使其成为真实可执行的 Playwright 用例：家长端两条（模板重复提交配置、模板表单字段联动）默认可跑（契约桩后端或真实后端均可）；孩子端两条（重复提交达上限、积分上限）按现有惯例以 `BASE_URL` 完整栈门控，无完整栈时自动 skip。全部用例更新后套件通过。

# 范围

- `e2e/tests/resubmission-controls.spec.ts`：移除 4 处 `test.skip(true, ...)`，实现真实流程（家长登录 fixture 沿用 `parent-task-calendar.spec.ts` 的 `page.request` 登录方式）。
- `e2e/playwright.config.ts`（如需）：仅当孩子端用例需要额外 webServer/port 配置时最小调整。
- 桩后端契约（`.e2e-stub/stub-server.mjs`，未跟踪的本地验证工具）：按需补充模板创建、任务分配、提交流程所需端点；不进入仓库提交范围（或经用户确认后纳入 e2e 目录）。

# 非目标

- 不修改 `web/apps/console`、`web/apps/kid`、`web/packages/shared` 任何源码（server/ 的例外见 D4）。
- 不实现孩子端在无完整栈时的本地运行（孩子端用例保持 BASE_URL 门控）。
- 不改变既有 auth-guard、parent-task-calendar 用例的覆盖意图。

# 验收示例

- Given 已配置家长凭据（`E2E_PARENT_PHONE`/`E2E_PARENT_PASSWORD`），When 运行家长端两条用例（真实 Chromium + console dev + 后端），Then 家长创建模板并可配置 `allow_resubmit` + `max_submissions`（或经 API 等价验证配置生效），且模板表单显示「允许重复提交」开关，勾选后出现「最大提交次数」「积分上限」字段。
- Given 完整栈（`BASE_URL` 指向网关），When 运行孩子端两条用例，Then 孩子重复提交达 `max_submissions` 后任务列表 `canSubmit=false`、再次提交被拒（422 `TASK_SUBMISSION_MAX_REACHED`）；积分达上限后同理（`POINTS_CAP_REACHED`）。
- Given 未设置 `BASE_URL` 的本地环境，When 运行整个 e2e 套件，Then 全部用例 0 失败（孩子端 2 条与既有 `/child` 用例自动 skip），套件通过。
- Given 本地契约桩环境，When 运行家长端两条用例，Then 真实 Chromium 下通过。

# 约束与不变量

- 测试必须对后端实现无关：同一用例在契约桩与真实后端下均可运行（不得断言桩特有行为）。
- 不降低既有覆盖：auth-guard 16 条与 parent-task-calendar 5 条保持通过。
- 孩子端用例遵循现有 `FULL_STACK` 门控惯例。
- 不在仓库文件中写入任何真实凭据。

# 决策

- D1（用户确认，2026-09-04）：四条全做、分层可跑——家长端两条默认可跑，孩子端两条 BASE_URL 完整栈门控。
- D2（实现选择）：家长登录 fixture 复用 `parent-task-calendar.spec.ts` 的既有模式（`page.request` POST `/api/auth/login` 携带凭据）。
- D3（静默假设）：模板创建优先走真实 UI 表单；若 console 表单交互在 e2e 中不稳定，允许「UI 打开表单验证字段 + API 创建模板」的混合验证，但表单字段联动的断言必须走 UI。
- D4（用户确认，2026-09-04）：复核发现 GlobalExceptionHandler 未把 TASK_SUBMISSION_MAX_REACHED / TASK_SUBMISSION_POINTS_CAP_REACHED 映射为 422（实际 500），与孩子端用例期望矛盾；用户确认在本 change 内顺手修复（一行 case 映射），TaskReviewResubmissionControlIT 10/10 通过。

# 待解决问题

（无）

# 验证预期

- 本地（契约桩 + Chromium 双项目）：家长端 2 条真实通过；孩子端 2 条按门控 skip；全套件 0 失败。
- `web` workspace build 不受影响（本 change 不改前端源码，构建仅作回归确认）。
