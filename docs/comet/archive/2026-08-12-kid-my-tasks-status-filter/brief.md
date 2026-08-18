# Outcome

孩子端「我的任务」页增加状态筛选功能：可按「进行中」「已逾期」「已提交」「已完成」「已取消」五个分类筛选任务，默认进入页面时列出「进行中」的任务。同时，可重复任务在已达最大提交次数或已达积分限额时，提交入口失效，原提交按钮处显示文案「该任务已达最大提交次数」。

用户给出的分类定义：

- 已逾期：截止日期已过的非重复任务；
- 已提交：孩子端已提交、家长未审核的任务；
- 已完成：孩子端已提交且家长端已审核通过的任务；
- 已取消：被取消的任务；
- 进行中：未单独定义（默认分类，待澄清边界）。

# Scope

- `web/src/child/pages/index.tsx` ChildTasksPage（「我的任务」页）：新增五分类筛选 UI（默认「进行中」），按确认后的归类规则展示任务；已达最大提交次数/积分限额的任务在原提交按钮位置显示「该任务已达最大提交次数」。
- `server/task` 模块 `TaskAssignmentService`：落实既有规格契约「任务分配响应携带可提交状态」（openspec/specs/task-review/spec.md），在列表响应中真实计算 `canSubmit` 与 `submissionBlockReason`（当前为占位实现：恒为 `true`/`null`），聚合口径与 `POST /api/task-review/submissions` 一致。
- 前端/后端单元测试覆盖筛选归类与提交拦截展示。

# Non-goals

- 不改变审核状态流转（SUBMITTED/APPROVED/REJECTED/COMPLETED）与取消逻辑。
- 不改变 RepeatTaskScheduler 的 PENDING_OPEN→OPEN→EXPIRED 生命周期。
- 不改变提交接口本身的 max/cap 前置校验行为（已存在且已规格化）。
- 不改变家长端任务列表、日历视图行为。
- 不改变孩子端首页「今日任务」、积分商城、盲盒、兑换历史页面。

# Acceptance examples

- Given 孩子进入「我的任务」页，When 页面加载完成，Then 筛选器选中「进行中」，列表仅展示归类为「进行中」的任务。
- Given 一条非 REPEAT 任务且截止时间已过、状态为 PENDING 或 REJECTED，When 孩子选择「已逾期」，Then 该任务出现在列表中。
- Given 一条状态为 SUBMITTED（家长未审核）的任务（含截止已过的），When 孩子选择「已提交」，Then 该任务出现在列表中，且不出现在「已逾期」中。
- Given 一条状态为 APPROVED 或 COMPLETED 的任务，When 孩子选择「已完成」，Then 该任务出现在列表中。
- Given 一条 cancelled=true 的任务，When 孩子选择「已取消」，Then 该任务出现在列表中，且不出现在其他分类中。
- Given 一条 REPEAT 任务且截止时间已过、未提交未取消未通过，When 孩子查看筛选，Then 该任务归入「进行中」，不出现在「已逾期」中。
- Given 「进行中」分类中存在任务日期晚于今天的任务，When 孩子查看列表，Then 这些任务排在末尾，以灰色标注「未开始」，提交按钮不可用。
- Given 一条 allow_resubmit=true 的任务且（孩子,模板）维度已通过次数已达 max_submissions（canSubmit=false、submissionBlockReason=MAX_REACHED），When 孩子查看该任务卡片，Then 原提交按钮位置显示「该任务已达最大提交次数」，无提交按钮。
- Given 一条 allow_resubmit=true 的任务且（孩子,模板）维度累计获得积分已达 points_cap（submissionBlockReason=POINTS_CAP_REACHED），When 孩子查看该任务卡片，Then 原提交按钮位置同样显示「该任务已达最大提交次数」。
- Given 一条 canSubmit=false 且 submissionBlockReason=null 的任务（如逾期且迟交策略 REJECT），When 孩子查看该任务卡片，Then 提交按钮保持禁用态，不显示拦截文案。
- Given 后端列表 API 返回处于 PENDING/REJECTED 状态的任务，When 计算 canSubmit，Then 按既有规格契约（状态、未取消、迟交策略、max、cap 五项条件）计算，聚合口径与提交接口一致（批量查询，无 N+1）。

# Constraints and invariants

- 五个筛选分类的归类规则以用户确认为准（见 Open questions）；确认前不修改项目实现。
- `canSubmit`/`submissionBlockReason` 的计算 MUST 遵循 openspec/specs/task-review/spec.md「任务分配响应携带可提交状态」：canSubmit 需同时满足状态为 PENDING/REJECTED、未取消、未到截止时间（按有效迟交策略）、未达 max、未达 cap；submissionBlockReason 仅在 max/cap 阻塞时取 MAX_REACHED/POINTS_CAP_REACHED，其余原因取 null。
- max/cap 聚合口径 MUST 与提交接口一致：按（孩子,模板）跨 assignment 聚合，已通过次数取 task_review decision='APPROVED'，累计积分取 points_ledger type='EARN'；批量查询避免 N+1。
- REPEAT 任务一律不视为「已逾期」（既有用户决定，2026-08-07 archived change recurring-task-status-display D3）。
- 孩子端页面为移动优先布局，筛选控件不得破坏既有响应式与键盘可达性要求（web-app spec）。

# Decisions

- D1（调查结论）：任务分配状态集为 PENDING / PENDING_OPEN / OPEN / EXPIRED（REPEAT 生命周期）/ SUBMITTED / APPROVED / REJECTED / COMPLETED；取消为独立布尔标记 cancelled（任意状态可被取消）。任务类型：LIMITED / REPEAT / STANDING。
- D2（调查结论）：后端 overdue 派生规则为「非 REPEAT 且 now>deadline 且未取消且状态非 APPROVED」；REPEAT 恒不逾期（用户已确认决定）。
- D3（调查结论）：列表 API `GET /api/task-assignments` 已支持 status/cancelled/taskType/startDate/endDate/page/pageSize 查询参数；孩子端当前以 pageSize=100 拉取后客户端过滤。
- D4（调查结论）：`canSubmit`/`submissionBlockReason` 在列表响应中已有正式规格契约（task-review spec「任务分配响应携带可提交状态」），但后端当前为占位实现（恒 true/null）；前端 ChildAssignment 类型与禁用态渲染已就位。
- D5（调查结论）：提交拦截策略已实现于 ResubmissionPolicyEvaluator（提交时校验），max=0/cap=0 表示不限制；allow_resubmit 未启用时不校验。
- D6（调查结论）：当前「我的任务」页展示规则为「有效 REPEAT 任务 ∪ 任务日期（deadline 日期部分）≤ 今天」，已取消不展示（2026-08-12 archived change fix-child-my-tasks-listing，用户已确认）；未来日期的一次性任务当前不展示。
- D7（调查结论）：既有 child-page-migration 规格仅要求「支持按状态 Tab 筛选」，未定义分类；本变更予以落实。
- D8（用户要求明确项）：达到 max 或达到 cap 两种情形均在原提交按钮处显示同一文案「该任务已达最大提交次数」。
- D9（用户决定，2026-08-12）：五个筛选分类互斥，每个任务只归入一个分类；归类优先级为 已取消 > 已完成 > 已提交 > 已逾期 > 进行中。即：非重复任务截止已过但已提交（家长未审核）归「已提交」，已审核通过归「已完成」，取消归「已取消」；「已逾期」仅包含截止已过且仍未提交（或已被驳回待重提）的非重复任务。
- D10（用户决定，2026-08-12）：任务日期晚于今天的任务（未来任务）也展示，归入「进行中」，排序优先级最低（列于该分类末尾），提交按钮不可用，并以灰色标注「未开始」状态。
- D11（调查结论，边界）：REPEAT 生命周期状态 PENDING_OPEN/OPEN/EXPIRED 不在本次扩展范围：既有规格契约（task-review spec「任务分配响应携带可提交状态」）将 canSubmit 限定于 PENDING/REJECTED 状态，本变更不向 OPEN/EXPIRED 状态扩展提交按钮；此类任务若存在，按归类规则展示（无提交按钮，与现状一致）。未来日期的 PENDING_OPEN 周期按未来任务规则展示（「未开始」）。
- D12（语义推导）：「已完成」含 APPROVED 与 COMPLETED 两种状态；REJECTED 任务未逾期时归「进行中」、逾期（非 REPEAT 且截止已过）时归「已逾期」；REPEAT 任务无论日期永不归「已逾期」；五分类生效后，旧展示规则（REPEAT ∪ 日期≤今天、已取消不展示）被分类规则取代，已取消任务在「已取消」分类可见。
- D13（实现边界）：未来任务「提交按钮不可用」为前端展示规则；后端提交接口不新增提前提交拦截（现有接口允许早于任务日期提交的行为不变）。
- D14（用户确认，2026-08-12）：用户确认共享理解摘要（「正确」）——目标、五分类归类规则（状态优先、互斥）、未来任务展示（进行中末尾、灰色未开始、按钮禁用）、max/cap 拦截文案、后端 canSubmit 契约落实与非目标均按本 brief 与完整目标规格执行。
- D15（规格解释）：task-review spec 条件 5 文本（累计积分+奖励 <= cap）与提交端评估器（earned >= cap）在中间值情形不一致；按规格中「字段计算 MUST 使用与提交接口完全相同的聚合口径，保证列表展示与提交校验双写一致」的强制要求，列表 canSubmit 的 max/cap 判定复用提交端评估器语义（approvedCount >= max → MAX_REACHED；earnedPoints >= cap → POINTS_CAP_REACHED），不改变提交接口行为。规格中的 cap 边界场景（95+5=100 接受、100 时拒绝）与该语义结果一致。
- D16（规格修正）：完整目标规格的替换对象是 Native canonical 的 child-page-migration 规格（仅含「我的任务列表数据契约与展示规则」要求，由 2026-08-12 fix-child-my-tasks-listing 归档建立），不是 Classic 轨道 openspec/specs 中的旧迁移规格。初稿误引用旧迁移规格（含今日任务/积分商城/盲盒/兑换历史等 Native 轨道不存在的要求，其中「今日任务完成按钮」等场景与现有实现不符且超出本变更非目标），已重写：保留 content 字段数据契约，旧展示规则（REPEAT ∪ 日期≤今天、已取消与未来任务不展示）由五分类规则取代。用户可见行为变化均已在本 brief 决定中确认（D9/D10/D12）。

# Open questions

（无；用户已于 2026-08-12 确认共享理解摘要与规格修正 D16）

# Verification expectations

- 前端单元测试（vitest）：五分类筛选的归类与默认选中「进行中」；达到 max/cap 时提交按钮位置显示「该任务已达最大提交次数」；空列表空态。
- 后端测试：列表 API 的 canSubmit/submissionBlockReason 按规格契约计算（含 max 达到、cap 达到、未启用重复提交控制、PENDING/REJECTED 之外的状态等用例）。
- 既有相关测试（child pages vitest、TaskAssignmentServiceTest、TaskReviewServiceTest 等）保持通过。
