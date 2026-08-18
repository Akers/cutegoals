# Outcome

家长端 `/parent/reviews`（任务审核）页面正常显示每条待审核任务的业务字段（任务名、孩子昵称、提交时间、提交内容/备注、是否逾期），并通过/驳回操作能携带正确的 `attemptId` 调用后端接口；后端 `GET /api/task-review/pending` 与 `GET /api/task-review/history` 返回的 item 结构同时满足前端 `interface ReviewItem`（顶层字段）和既有嵌套结构消费方（保留 `attempts[]` 等字段）。

# Scope

- 后端：`server/task-review/src/main/java/com/cutegoals/taskreview/service/TaskReviewService.java` 中的 `enrichAssignmentWithAttempts(TaskAssignment)`，在保留现有全部字段的基础上，额外补齐前端 `ReviewItem` 期望的 7 个顶层字段。
- 复用影响：同一 helper 被 `queryPendingReviews`(:577)、`queryReviewHistory`(:622)、`queryChildHistory`(:748) 三处复用，补字段后三者同步生效；既有字段必须保留以避免破坏后端集成测试（`server/web/src/test/java/com/cutegoals/web/it/TaskTypeIntegrationTest.java:321,328`）。
- 前端：无需改动。`web/src/parent/pages/index.tsx:1803 ParentReviewsPage` 与 `interface ReviewItem` (:201-209) 维持现状。

# Non-goals

- 不重构列表维度（不把 assignment 维度展开为 attempt 维度）。若一个 assignment 有多条 attempt，仍按一条卡片展示。
- 不修改 `interface ReviewItem` 字段定义，不新增前端 adapter 层。
- 不优化 N+1 查询性能（既有 attempts+reviews 也是 N+1，本 change 保持同级）。
- 不修复孩子端 `/api/task-review/child/{childId}/history`（前端无消费者，bug 不可见；但补字段会自动同步生效）。
- 不修改 `/task-review/{attemptId}/approve|reject` 接口契约（前端 decide() 已正确传 attemptId，前提是后端补字段让 attemptId 可用）。

# Acceptance examples

**例 1：家长端审核列表显示完整业务字段**

Given 家庭 1 有一个孩子（id=10, nickname="小明"），有一条 task_assignment（id=20, status=SUBMITTED, snapshotTemplateName="整理书桌", childId=10, deadline=2026-08-10 23:59:59），该 assignment 有 1 条 attempt（id=30, attemptNumber=1, content="已经整理好了", submittedAt=2026-08-12 10:00:00, isLate=true）

When 家长请求 `GET /api/task-review/pending`

Then `data.content[0]` 同时包含：
- 既有字段：`id=20, childId=10, snapshotTemplateName="整理书桌", attempts=[{id:30, content:"已经整理好了", submittedAt:"2026-08-12T10:00:00", isLate:true, ...}]`
- 新增顶层字段：`assignmentId=20, attemptId=30, templateTitle="整理书桌", childNickname="小明", submittedAt="2026-08-12T10:00:00", notes="已经整理好了", isOverdue=<见 Decisions 与 Open questions>`

**例 2：前端渲染正常**

When 家长打开 `/parent/reviews`

Then 每张卡片显示：
- 任务名（强显示）：如 "整理书桌"
- 二级文本："小明 · 2026-08-12T10:00:00"（昵称 · 时间）
- 提交内容：如 "已经整理好了"（可选）
- 通过/驳回按钮 onClick 携带 attemptId=30

**例 3：审核历史复用同一 mapper**

When 家长请求 `GET /api/task-review/history`

Then `data.content[i]` 与 pending 同样具有新增顶层字段（attemptId/assignmentId/childNickname/templateTitle/submittedAt/notes/isOverdue）。

**例 4：既有集成测试仍通过**

When 运行 `TaskTypeIntegrationTest`

Then `/api/task-review/pending` 与 `/history` 的既有断言不破坏（新增字段不破坏断言；既有字段全部保留）。

# Constraints and invariants

- 后端字段补齐为 **additive**：所有现有字段（`id/childId/templateId/snapshotTemplateName/.../attempts[]`）必须保留，仅新增 7 个顶层字段。
- `assignmentId` 直接等于 `assignment.id`；`templateTitle` 直接等于 `assignment.snapshotTemplateName`。
- `childNickname` 来自 `task_childMapper.findById(assignment.childId).nickname`；若 child 不存在或 status != ACTIVE，`childNickname` 设为 null（前端会渲染空字符串，不抛错）。
- `attemptId`/`submittedAt`/`notes` 来自所选取的 attempt（见 Decisions）。
- `isOverdue` 计算口径见 Decisions 与 Open questions。
- 不引入新依赖；不修改数据库 schema；不修改 `TaskReviewController`。
- 后端补字段必须同时生效于 `queryPendingReviews`、`queryReviewHistory`、`queryChildHistory`（同一 helper）。

# Decisions

- **方案**：后端补字段（additive），不引入前端 adapter 层；理由是 `childNickname` 必须从后端获取（前端无数据源），且 additive 不破坏其他消费者与测试。
- **childNickname 查询**：在 `enrichAssignmentWithAttempts` 内调用 `taskChildMapper.findById(assignment.childId)`，取 `nickname`。N+1 与既有 attempts+reviews 查询同级，本 change 不优化（Non-goals）。
- **保留所有现有字段**：`id/childId/templateId/difficultyId/deadline/status/latePolicy/cancelled/version/snapshotTemplateName/snapshotTemplateDescription/snapshotTemplateCategory/snapshotDifficultyName/snapshotDifficultyReward/createdAt/attempts[]` 全部保留。
- **添加新顶层字段**：`assignmentId, attemptId, templateTitle, childNickname, submittedAt, notes, isOverdue`。
- **`assignmentId` ← `assignment.id`**；**`templateTitle` ← `assignment.snapshotTemplateName`**。
- **attemptId/submittedAt/notes 取最新一条 attempt**（按 `id` 倒序，等价于按提交时间倒序的最新一次提交）。`attemptId ← attempts[max].id`；`submittedAt ← attempts[max].submittedAt`；`notes ← attempts[max].content`。理由：审核列表关心最新一次提交；assignment 级 status=SUBMITTED 与 attempt 级最新提交语义一致。
- **`notes` 语义为"孩子提交内容"**：映射自 `attempt.content`（孩子提交时填写的文字内容）。
- **`isOverdue` 口径为"deadline<now 即为逾期"**（不考虑 assignment.status，也不考虑是否 REPEAT）：审核列表场景下用于提醒家长"提交已超期，请尽快审核"，与 spec 中"任务列表 isOverdue"（status ∈ {PENDING, REJECTED} && deadline<now && 非 REPEAT）语义不同但场景不同，前者是审核场景、后者是孩子任务展示场景。本 change 不修改 spec 的任务列表 isOverdue 定义。
- **`childNickname` 取值**：`taskChildMapper.findById(assignment.childId).nickname`。若查询返回空或 status!=ACTIVE，`childNickname=null`（前端 React 渲染 undefined/null 等价于不显示，不抛错）。

# Open questions

- `- [blocking] CONFIRM: 共享理解——目标=后端 `enrichAssignmentWithAttempts` additive 补 7 个顶层字段（assignmentId/attemptId/templateTitle/childNickname/submittedAt/notes/isOverdue），保留所有现有字段；范围=`server/task-review/.../TaskReviewService.java` 单文件，前端无改动；关键决定=attemptId/submittedAt/notes 取最新 attempt（id desc 首条）、notes←attempt.content、isOverdue=deadline<now 不考虑 status、childNickname 来自 taskChildMapper.findById；验收=`/pending` 与 `/history` item 同时含新旧字段、TaskTypeIntegrationTest 既有断言不破坏、前端卡片显示完整业务字段且通过/驳回携带正确 attemptId；非目标=不展开列表维度、不优化 N+1、不改 controller/接口契约/schema。用户答 3 项 blocking 后已表示「继续」视为最终确认。`

# Verification expectations

- 后端单元/集成测试：`TaskTypeIntegrationTest`（pending/history 既有断言）必须通过；新增字段断言至少一条用例验证 `assignmentId/attemptId/templateTitle/childNickname/submittedAt/notes/isOverdue` 在 pending 响应中存在且非空。
- 前端：手动验证 `/parent/reviews` 列表卡片显示完整业务字段；通过/驳回操作能携带正确 attemptId（不报 404/400）。无前端自动化测试（既有基线无）。
- 验证证据：后端测试运行日志、前端截图对比（修复前空 vs 修复后正常）。
