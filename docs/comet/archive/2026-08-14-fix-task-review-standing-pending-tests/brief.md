# Outcome

修复 `server/task-review` 模块 `TaskReviewServiceTest` 中两个长期失败的 STANDING 任务单元测试，让该模块单元测试通过率恢复 100%（50/50 通过）。不修改任何生产代码。

# Scope

仅修改测试文件：`server/task-review/src/test/java/com/cutegoals/taskreview/service/TaskReviewServiceTest.java`，具体两处变更：

1. 三处 `createSampleTemplate("STANDING", "{\"max_submissions\":5}")` 调用之后补充字段赋值：
   - `shouldIncrementSubmissionCountOnStandingApproval`（L611 后）
   - `shouldCompleteStandingTaskWhenMaxReached`（L634 后）
   - `shouldRejectStandingSubmissionWhenMaxReached`（L655 后）

   每处追加两行：
   ```java
   template.setMaxSubmissions(5);
   template.setAllowResubmit(true);
   ```

2. `shouldRejectStandingSubmissionWhenMaxReached` 测试断言（L667）：
   ```java
   assertEquals(ErrorCode.TASK_STANDING_LIMIT_REACHED, ex.getErrorCode());
   ```
   改为：
   ```java
   assertEquals(ErrorCode.TASK_SUBMISSION_MAX_REACHED, ex.getErrorCode());
   ```

# Acceptance examples

- task-review 模块单元测试通过率恢复 100%：Given 当前 `mvn -pl task-review test` 存在 2 个 ERROR（`shouldCompleteStandingTaskWhenMaxReached` 期望 COMPLETED 但得到 APPROVED；`shouldRejectStandingSubmissionWhenMaxReached` 期望 BusinessException 未抛），When 修改 `TaskReviewServiceTest.java`（三处 STANDING template 补 `setMaxSubmissions(5)+setAllowResubmit(true)`；一处错误码期望改为 `TASK_SUBMISSION_MAX_REACHED`）后运行 `mvn -pl task-review clean test`，Then `Tests run: 50, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`。

- `shouldCompleteStandingTaskWhenMaxReached` 三个断言成立：Given `assignment.status="SUBMITTED"`、`assignment.submissionCount=4`、`assignment.snapshotTemplateMaxSubmissions=null`、`template.taskType="STANDING"`、`template.maxSubmissions=5`、`template.allowResubmit=true`、`approveAttempt` 链路 mock 完整，When `taskReviewService.approveAttempt(attemptId, request, familyId, accountId)` 被调用，Then `assignment.getSubmissionCount()==5`、`assignment.getStatus()=="COMPLETED"`、`taskAssignmentMapper.updateById(assignment)` 被调用恰好一次。

- `shouldRejectStandingSubmissionWhenMaxReached` 抛 BusinessException 且 errorCode 为 `TASK_SUBMISSION_MAX_REACHED`：Given `assignment.status="PENDING"`、`assignment.submissionCount=5`、`assignment.snapshotTemplateMaxSubmissions=null`、`template.taskType="STANDING"`、`template.maxSubmissions=5`、`template.allowResubmit=true`、`taskAssignmentMapper.findByIdForUpdate` 与 `taskTemplateMapper.findById` mock 完整，When `taskReviewService.submitTask(request, childId, familyId, accountId)` 被调用（含 `assignmentId`、`content`、`idempotencyKey` 字段），Then 抛出 `BusinessException`，且 `ex.getErrorCode()==ErrorCode.TASK_SUBMISSION_MAX_REACHED`（来源：`ResubmissionPolicyEvaluator.ResubmissionDecision.blocked("TASK_SUBMISSION_MAX_REACHED", ...)`，由 `TaskReviewService.submitTask` 的 L202-208 错误码映射抛出）。

- `shouldIncrementSubmissionCountOnStandingApproval` 仍通过（回归保护）：Given `assignment.status="SUBMITTED"`、`assignment.submissionCount=0`、`template.maxSubmissions=5`、`approveAttempt` 链路 mock 完整，When `taskReviewService.approveAttempt` 被调用，Then `assignment.getSubmissionCount()==1`、`assignment.getStatus()=="APPROVED"`、`updateById(assignment)` 被调用一次（newCount=1 < maxSubmissions=5 → APPROVED 而非 COMPLETED）。

- `ResubmissionPolicyEvaluatorTest` 7 个测试继续通过：When 运行 `mvn -pl task-review test -Dtest=ResubmissionPolicyEvaluatorTest`，Then `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`。

- `TaskTypeIntegrationTest` 26 个集成测试继续通过：When 运行 `mvn -pl web clean test -Dtest=TaskTypeIntegrationTest`，Then `Tests run: 26, Failures: 0, Errors: 0, Skipped: 0`（既有 `/api/task-review/pending` 与 `/api/task-review/history` 401 断言不破坏）。

# Non-goals

- 不修改 `TaskReviewService.java` / `ResubmissionPolicyEvaluator.java` / `TaskAssignment.java` / `ErrorCode.java` 等任何生产代码
- 不修改 `createSampleTemplate` factory helper（保持 LIMITED/REPEAT 测试不受影响）
- 不修改 `createSampleAssignment` factory helper（默认 `snapshotTemplateMaxSubmissions=null` + `snapshotTemplateAllowResubmit=null` 是 D9 fallback 设计所需）
- 不删除或重命名 ErrorCode 枚举值（保留 `TASK_SUBMISSION_MAX_REACHED` 与 `TASK_STANDING_LIMIT_REACHED` 双名并存）
- 不修改 STANDING 任务类型定义或产品语义
- 不修改前端、web 模块、API 契约
- 不修复 `ResubmissionPolicyEvaluatorTest`（已通过）
- 不修改其他模块（task / points / family 等）测试

# Constraints and invariants

- 仅修改 `TaskReviewServiceTest.java` 一个文件
- 不向生产代码引入新依赖、不修改公共 API、不修改错误码枚举值
- HTTP 响应行为不变：`TASK_SUBMISSION_MAX_REACHED` 与 `TASK_STANDING_LIMIT_REACHED` 均映射 HTTP 409 CONFLICT（见 `GlobalExceptionHandler.java:139`）
- D9 fallback 语义保持：`assignment.getSnapshotTemplateMaxSubmissions()` 为 null 时由 evaluator 读取 `template.getMaxSubmissions()`
- `createSampleAssignment` 默认值不变（`snapshotTemplateMaxSubmissions` 与 `snapshotTemplateAllowResubmit` 均为 null），D9 fallback 路径天然可用

# Decisions

- 错误码统一到 `TASK_SUBMISSION_MAX_REACHED`：
  - `ResubmissionPolicyEvaluator.ResubmissionDecision.blocked("TASK_SUBMISSION_MAX_REACHED", ...)` 是当前生产路径（TaskReviewService.java:204）
  - `ResubmissionPolicyEvaluatorTest` 全部 7 处 blockCode 断言都用 `TASK_SUBMISSION_MAX_REACHED` 或 `TASK_SUBMISSION_POINTS_CAP_REACHED`
  - 失败测试用 `TASK_STANDING_LIMIT_REACHED` 是早期 STANDING 专用命名，与 evaluator 通用命名不一致
  - 两码均映射 HTTP 409，HTTP 行为不变；前端/客户端无基于此错误码名差异化的处理
  - 保留 `ErrorCode.TASK_STANDING_LIMIT_REACHED` 枚举值，不删除（避免影响其他可能的未来 caller）
  - 若保留 `TASK_STANDING_LIMIT_REACHED` 期望，需在生产代码加 STANDING 专用分支污染 `ResubmissionPolicyEvaluator` 通用性，不取

- 直接在三处 STANDING 测试调用后追加 setter，不抽 helper：
  - 影响面最小（仅 3 处）
  - 保留 `createSampleTemplate("STANDING", "{\"max_submissions\":5}")` 不变，避免对 factory helper 的修改蔓延到未来调用
  - 不抽 helper 是因为当前只有 STANDING 类型需要这两个字段，且 helper 增加阅读间接性

- 不修改 `createSampleAssignment` factory：
  - 当前默认值 `snapshotTemplateMaxSubmissions=null` + `snapshotTemplateAllowResubmit=null` 是 D9 fallback 路径正常工作所需
  - 其他测试已依赖此默认行为，修改会引发回归

# Open questions

- `[blocking] CONFIRM: 共享理解——目标=修复 `TaskReviewServiceTest` 中 2 个 STANDING 失败用例让 task-review 通过率 100%；范围=仅改 `TaskReviewServiceTest.java`（三处 STANDING template 补 setMaxSubmissions(5)+setAllowResubmit(true) + 一处错误码期望改为 TASK_SUBMISSION_MAX_REACHED）；关键决定=错误码统一到 TASK_SUBMISSION_MAX_REACHED（与 evaluator 与 evaluatorTest 既有断言一致，两码均 HTTP 409 行为不变；保留 TASK_STANDING_LIMIT_REACHED 枚举值不删除）；不动 factory helper、不动生产代码、不动 ErrorCode 枚举、不动其他模块；验收=task-review 50/50 通过 + ResubmissionPolicyEvaluatorTest 7/7 + TaskTypeIntegrationTest 26/26；非目标=不修复 evaluator Test（已通过）、不重命名枚举、不改 API 契约、不改前端。`

# Verification expectations

自动化验证（4 个 mvn 命令，全部应在 commit 前通过）：

- `mvn -pl task-review clean test` → `Tests run: 50, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`
- `mvn -pl task-review test -Dtest=TaskReviewServiceTest#shouldCompleteStandingTaskWhenMaxReached+shouldRejectStandingSubmissionWhenMaxReached+shouldIncrementSubmissionCountOnStandingApproval -Dsurefire.failIfNoSpecifiedTests=false` → `Tests run: 3, Failures: 0, Errors: 0` / `BUILD SUCCESS`
- `mvn -pl task-review test -Dtest=ResubmissionPolicyEvaluatorTest -Dsurefire.failIfNoSpecifiedTests=false` → `Tests run: 7, Failures: 0, Errors: 0` / `BUILD SUCCESS`
- `mvn -pl web clean test -Dtest=TaskTypeIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false` → `Tests run: 26, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`

无 required checks / manual checks。