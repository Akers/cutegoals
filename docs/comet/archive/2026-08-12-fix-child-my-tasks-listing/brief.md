# Outcome

孩子端登录后进入「我的任务」页（如 cici 账号），任务列表正确展示：列出该孩子所有有效的可重复（REPEAT）任务，以及所有任务日期（开始日期）不晚于当前日期的任务；已取消的任务不展示。修复前端字段读取错误导致列表恒为「暂无任务」的缺陷。

根因（调查结论）：孩子端页面 `web/src/child/pages/index.tsx` 从 `GET /api/task-assignments?childId=X` 响应中读取 `items` 字段，而后端实际返回 `{ content, page, pageSize, totalElements, totalPages }`（实测 cici 登录返回 12 条记录于 `data.content`），`items` 恒为 undefined，页面恒显示「暂无任务」。家长端页面使用 `content`（PageResult）显示正常。

# Scope

- `web/src/child/pages/index.tsx` ChildTasksPage（「我的任务」页）：
  - 修复响应字段读取：`items` → `content`（类型对齐 PageResult 结构）；
  - 应用列表规则：展示「有效的 REPEAT 任务」∪「任务日期（deadline 日期部分）≤ 今天的任务」；「有效」= 未取消（cancelled=false）；
  - 请求显式携带 pageSize=100（与家长端一致），避免默认分页（20 条）截断导致客户端过滤后列表不全。
- 同文件 ChildHomePage（首页「今日任务」卡片）：修复同一 `items` 字段读取错误（同一根因；其「按今天过滤」逻辑保持不变）。
- 前端单元测试（vitest）：覆盖字段修复与列表规则。

# Non-goals

- 不修改后端 `/api/task-assignments` 的响应结构与查询逻辑。
- 不改变家长端任务列表、日历视图、任务分配功能的行为。
- 不改变 RepeatTaskScheduler 的 OPEN/EXPIRED 生命周期与周期任务生成逻辑。
- 不改变孩子端任务提交/重新提交流程与审核状态流转。
- 不改变积分、兑换、盲盒等其他孩子端页面。

# Acceptance examples

- Given cici（childId=2）有 12 条分配记录：10 条 STANDING、2 条 REPEAT、其中 1 条已取消，全部任务日期 ≤ 2026-08-07（今天 2026-08-12），When cici 登录孩子端进入「我的任务」，Then 列出 11 条任务（不含已取消的一条），其中包含 2 条 REPEAT 任务，每条显示名称、截止时间、状态与积分。
- Given 一条非 REPEAT 分配且任务日期晚于今天，When 孩子查看「我的任务」，Then 该任务不列出。
- Given 一条有效的 REPEAT 分配且任务日期晚于今天（如存在），When 孩子查看「我的任务」，Then 该任务仍列出。
- Given 一条任务日期为今天的分配，When 孩子查看「我的任务」，Then 该任务列出。
- Given 后端返回 `content` 为空数组，When 孩子查看「我的任务」，Then 显示「暂无任务」空态。

# Constraints and invariants

- 后端 API 契约（字段、分页、状态过滤参数）保持不变；家长端列表与日历行为不受影响。
- 孩子端任务提交入口（PENDING 提交 / REJECTED 重新提交、canSubmit 拦截提示）行为不变。
- 列表规则只作用于孩子端页面展示层，不改变数据库记录与后端派生字段（overdue、canSubmit 等）。

# Decisions

- D1（调查结论，非用户决定）：根因为前端字段不匹配（`items` vs `content`）；后端数据与接口正常（实测 cici 返回 12 条）。
- D2（实现选择）：列表规则在孩子端前端页面（客户端过滤）实现，不修改后端 TaskAssignmentService 共享查询逻辑，避免影响家长端与日历。
- D3（语义映射）：数据模型中任务分配只有 deadline（任务日期，当日 23:59:59），无独立「开始日期」字段；「开始日期」映射为 deadline 的日期部分。「开始日期在当前日期之前」按「日期 ≤ 今天」实现（当天开始/到期的任务必须可见）。
- D4（语义映射）：「有效」= 分配未取消（cancelled=false）。分配创建时已含模板快照，模板后续停用/删除不影响已创建分配的有效性。
- D5（用户确认，2026-08-12）：用户确认共享理解摘要（「正确」）——目标、范围、列表规则（有效 REPEAT ∪ 任务日期 ≤ 今天，已取消不展示）、语义映射（开始日期=deadline 日期部分、含当天）与非目标均按本 brief 执行。
- D6（用户确认，2026-08-12）：契约修订再确认——完整目标规格收缩为仅含本变更新确立的「我的任务列表数据契约与展示规则」要求；移除误复制的 child-page-migration 既有 Classic 要求（含未实现且非本变更范围的「状态 Tab 筛选」）。用户确认目标、范围与验收标准不变。

# Open questions

（无；用户已于 2026-08-12 确认共享理解摘要）

# Verification expectations

- 前端单元测试（vitest）：ChildTasksPage 正确渲染 `content` 中的任务；列表规则用例（REPEAT 恒显示、非 REPEAT 仅日期 ≤ 今天显示、已取消不显示、空列表空态）；ChildHomePage 今日任务卡片字段修复。
- 真实环境验证：以 cici（PIN 登录）调用 `GET /api/task-assignments?childId=2` 核对返回数据，并验证页面过滤逻辑输出 11 条（排除已取消）。
- 既有相关测试（child pages 相关 vitest、后端任务模块测试）保持通过。
