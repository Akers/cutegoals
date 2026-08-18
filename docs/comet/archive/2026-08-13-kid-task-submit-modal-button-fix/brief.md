# Outcome

修复孩子端「我的任务」页面「提交任务」对话框的按钮冗余与失效问题：去除表单内冗余的「提交」按钮，将提交能力整合到 antd Modal 默认 footer 的主按钮上（由当前空响应的「确认」变为可用的「提交」/「重新提交」），并恢复提交按钮的正常响应，使孩子点击「提交」后任务能正确通过 `POST /task-review/submissions` 提交并关闭对话框、刷新列表。

# Scope

- `web/src/child/pages/index.tsx`：修改 389-411 行的「提交任务」Modal：
  - 删除 `<Space>` 内显式渲染的「提交」/「重新提交」`<Button>`（407-409 行）。
  - 给 `<Modal>` 显式声明 `onOk={handleSubmit}`、`okText={active?.status === 'REJECTED' ? '重新提交' : '提交'}`、`okButtonProps={{ loading: submitting, disabled: !notes.value.trim() }}`、`cancelText="取消"`。
  - 保留 `open`、`onCancel`、`title` 等其他既有 props 不变。
  - 保留 `handleSubmit`（282-300 行）请求体原状 `{assignmentId, notes, idempotencyKey}`（不含 childId，由后端 session 派生）。
- `server/task-review/src/main/java/com/cutegoals/taskreview/controller/TaskReviewController.java`：修改 `submitTask` 方法（L59-62）的 childId 解析逻辑：
  - 替换 `extractLong(request, "childId")` + null 检查 + `VALIDATION_FAILED "childId is required"` 抛错，为 `resolveChildIdFromSession(httpRequest)` 调用。
  - 新增私有 helper `resolveChildIdFromSession(HttpServletRequest)`（紧邻 generateRequestId 之前）：优先从 `httpRequest.getAttribute(AuthConstants.ATTR_CHILD_ID)`（child session JWT claim `currentChildId`，由 WebSecurityConfig.java:312 设置）提取；否则从 parent session 的 accountId 经 `taskChildMapper.findByAccountId(accountId)` 派生。
  - 与同 controller L172-L193 `queryReviewHistory`、L218-L234 `queryChildHistory` 和 `PointsController#resolveChildIdFromSession` 既有同模式一致。

# Non-goals

- 不改动任务卡片上的「提交」/「重新提交」入口按钮（卡片操作不变）。
- 不改动前端 `handleSubmit` 的请求体形状（保持 `{assignmentId, notes, idempotencyKey}`，与原始设计一致；childId 由后端从 session 派生）。
- 不改动提交后的成功/失败处理（`message.success` / `message.error`、关闭对话框、`refetch`）。
- 不改动五分类筛选、列表归类、提交受限展示（由上一个 change `kid-task-filter-active-highlight-fix` 覆盖）。
- 不改动盲盒开启确认弹窗（`index.tsx:660-673`）、家长端、管理端或孩子端其他页面。
- 不改动 TaskReviewService、ResubmissionPolicyEvaluator、积分计算等其他后端逻辑（仅 Controller 入口的 childId 解析）。

# Acceptance examples

- Given 孩子在「我的任务」点击任务卡片上的「提交」按钮，When 「提交任务」对话框打开，Then 对话框内只显示任务名 + 完成情况说明 textarea，且右下角 footer 显示「取消」与「提交」两个按钮（不再出现表单内独立的「提交」按钮）。
- Given 任务为 REJECTED 状态、孩子点击「重新提交」，When 对话框打开，Then 右下角主按钮文案为「重新提交」。
- Given 对话框已打开且 textarea 为空，When 用户查看 footer 主按钮，Then 「提交」按钮处于禁用态（视觉禁用，点击无反应）。
- Given 对话框已打开、textarea 已输入非空内容，When 用户点击右下角「提交」，Then 触发 `POST /task-review/submissions`，按钮进入 loading 态；提交成功后 toast「提交成功，等待家长审核」、对话框关闭、列表刷新；失败时 toast 错误信息，对话框保持打开。
- Given 用户点击右下角「取消」或 × 或按 ESC 或点遮罩，When 任一关闭触发，Then 对话框关闭并重置 notes，不触发提交。
- Given 修复部署完成，Then 真实浏览器（agent-browser 验证）中点击「提交」按钮能正常完成提交流程，不再无响应。

# Constraints and invariants

- 修复 MUST NOT 改动 `handleSubmit` 内部逻辑（除保持请求体原状外）、API 请求体形状（不含 childId）、幂等键生成、成功/失败分支处理。
- 修复 MUST 保留 textarea 非空才允许提交的禁用约束（与既有 `disabled={!notes.value.trim()}` 等价）。
- 修复 MUST 保留 loading 态（`submitting`）正确显示在主按钮上。
- 修复 MUST 保留「取消」/× / ESC / 点遮罩四条关闭路径，且关闭时 reset notes。
- 修复 MUST NOT 改变 antd Modal 的可访问性语义（默认 footer 的 ARIA 角色、focus 行为保留）。
- 修复 MUST NOT 影响其他 antd Modal 实例（盲盒确认弹窗、其他页面弹窗）。
- 后端 `TaskReviewController.submitTask` MUST 从可信 session（JWT claim `currentChildId` 或 parent session accountId→child profile）派生 childId，不再接受请求体 childId 字段。与同 controller L172-L193 queryReviewHistory 既有 child session 派生模式一致。
- 后端修改 MUST NOT 影响 TaskReviewService 内部业务逻辑（service 仍从 assignment.getChildId() 重新提取，与 Controller 解析解耦）。
- 提交 API 行为不变（`POST /task-review/submissions`），后端校验、积分上限、最大提交次数限制等仍由后端 + 列表接口的 `canSubmit` / `submissionBlockReason` 控制。

# Decisions

- D1（实现选择）：复用 antd `<Modal>` 的默认 footer（取消 + 主按钮）而不是自定义 `footer={[...]}`，理由：默认 footer 已满足「取消 + 提交」的双按钮布局，且与截图当前视觉一致（截图右下角已显示「取消」「确定」两按钮），只需通过 `onOk`/`okText`/`okButtonProps` 把主按钮从空响应的「确定」改造为可用的「提交」。
- D2（实现选择）：去除表单内独立的「提交」按钮（407-409 行），而非仅禁用——避免冗余入口、消除"两个提交按钮"的视觉混乱，符合用户「红框中的提交按钮去除」的要求。
- D3（静默假设）：保持既有提交后行为不变（成功 → 关闭 + refetch + 成功 toast；失败 → 错误 toast + 保持打开）。如用户希望改变此行为需在确认时指出。
- D4（静默假设）：保持 textarea 非空校验（按钮在空内容时禁用）。
- D5（用户确认，2026-08-13）：用户明确确认上述共享理解摘要（目标、范围、保留行为、REJECTED 文案、验证方式）无误，授权推进到 Build 实施。
- D6（Build/Verify 阶段发现，2026-08-13）：agent-browser 真实浏览器验证时发现后端 `TaskReviewController.java:59-62` 强制要求请求体 `childId` 字段（缺失返回 `VALIDATION_FAILED "childId is required"`），而前端 `handleSubmit` 原本只发 `{assignmentId, notes, idempotencyKey}`——这是用户报告"点击提交无反应"的另一个根因。**解决方案**：不改前端请求体（保持原始设计 `{assignmentId, notes, idempotencyKey}`），改后端 `submitTask` 从 session JWT claim `currentChildId` 派生 childId（child session 直接取；parent session 经 `taskChildMapper.findByAccountId` 派生）。与同 controller L172-L193 queryReviewHistory、PointsController#resolveChildIdFromSession 既有同模式一致。这是更安全的鉴权设计：前端不暴露 childId，后端从可信 session 提取，防止越权。
- D7（用户确认扩展范围，2026-08-13）：Build/Verify 反思发现 D6 的实现跨前端+后端，超出原 D5 共享理解范围（"不涉及后端"）。用户在 verify 阶段被告知 scope 冲突后明确选择「扩展范围修复后端」，授权更新 brief Scope/Non-goals/Constraints 以包含 `TaskReviewController.java` 修改，并重新确认契约。

# Open questions

（无；用户已确认全部共享理解，并在 verify 阶段追加确认 D6/D7 的范围扩展，无未决问题）

# Verification expectations

- `npm run lint`（即 `tsc --noEmit`）不引入新的 TypeScript 错误。
- 既有孩子端单元测试（如存在）保持通过，无回归。
- agent-browser 真实浏览器验证：打开孩子端「我的任务」→ 点击任务「提交」→ 对话框无表单内独立提交按钮、右下角显示「取消」「提交」→ 输入完成情况说明 → 点击「提交」→ 提交成功、对话框关闭、列表刷新、Toast 显示成功；REJECTED 任务下主按钮文案为「重新提交」；textarea 为空时主按钮禁用。
