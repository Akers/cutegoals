# Acceptance evidence

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-271d9621b2b87954e09ee9a2521acb3b8b877d5a0ca25caf772cffd5d40c5e9d",
    "status": "passed",
    "evidence_refs": [
      "runtime/evidence/receipts/9bc2d05163320a4b731a37a71e36f35b53d08a1f902815938caf9ec8142bbe42.json",
      "runtime/evidence/receipts/c74daca0dd417d4ef082db7b8b61d99a5d0e2c5305f66c9d98c98f1029ac9aa6.json"
    ]
  },
  {
    "acceptance_id": "acceptance-4bc0895743bc5bc3cc3ce312e2072b6494fba68737a8294926352d4119c2781d",
    "status": "passed",
    "evidence_refs": [
      "runtime/evidence/receipts/9bc2d05163320a4b731a37a71e36f35b53d08a1f902815938caf9ec8142bbe42.json"
    ]
  },
  {
    "acceptance_id": "acceptance-88f659e74807b47f7acfe659a271044003947f6f34348fe97a079ba1ca6b7e1c",
    "status": "passed",
    "evidence_refs": [
      "runtime/evidence/receipts/9bc2d05163320a4b731a37a71e36f35b53d08a1f902815938caf9ec8142bbe42.json"
    ]
  },
  {
    "acceptance_id": "acceptance-cfd4040fe1209aa8ee7b2e15e0f5c50d69f3b118f9109007bffc2d663144b059",
    "status": "passed",
    "evidence_refs": [
      "runtime/evidence/receipts/9bc2d05163320a4b731a37a71e36f35b53d08a1f902815938caf9ec8142bbe42.json"
    ]
  },
  {
    "acceptance_id": "acceptance-d9be0d0889061e19a544f623119fb57c05627810c47cccdb237d2d750750ed75",
    "status": "passed",
    "evidence_refs": [
      "runtime/evidence/receipts/bb35b28ebdbba57555ee8c5ee31cd46cca60c0ad0530d80e25a97c6644be53d2.json"
    ]
  },
  {
    "acceptance_id": "acceptance-f0890bfd7ca13b14964af82210de3d03a14af56e4cebd61daf410418e4c5d416",
    "status": "passed",
    "evidence_refs": [
      "runtime/evidence/receipts/d2faf57b001538042debafd9c0f803f24dab6b6ec4fd359cabbd77dd4e9b4b9b.json"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->

# Commands and results

## 1. task-review 模块新增单元测试

命令：

```bash
mvn -pl task-review clean test \
  -Dtest=TaskReviewServiceTest#shouldExposeFlattenedReviewItemFieldsInPendingReviews+shouldReturnIsOverdueFalseWhenDeadlineNotPassed \
  -Dsurefire.failIfNoSpecifiedTests=false
```

结果：`Tests run: 2, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`

覆盖：
- `shouldExposeFlattenedReviewItemFieldsInPendingReviews`：mock `taskAssignmentMapper.selectPage` 返回 1 个 `SUBMITTED` assignment（`snapshotTemplateName="整理书桌"`、`deadline=now-1d` 已逾期），`taskAttemptMapper.findByAssignmentId` 返回 1 个 attempt（`id=30`、`content="已经整理好了"`、`submittedAt` 非空），`taskChildMapper.findById` 返回 ChildProfile（`nickname="小明"`）。断言 `content[0]` 同时包含既有字段（`id=20`、`snapshotTemplateName="整理书桌"`、`status="SUBMITTED"`、`attempts` 非空）与 7 个新增顶层字段（`assignmentId=20`、`attemptId=30`、`templateTitle="整理书桌"`、`childNickname="小明"`、`notes="已经整理好了"`、`submittedAt` 非空、`isOverdue=true`）。
- `shouldReturnIsOverdueFalseWhenDeadlineNotPassed`：deadline 默认 `now+1d` 未逾期，`isOverdue=false`；child 不存在时 `childNickname=null`（前端容忍 null/undefined）。

绑定的 acceptance：`acceptance-271d...`（例 3+例 4 部分）、`acceptance-4bc0...`（提交内容="已经整理好了"）、`acceptance-88f6...`（既有字段保留）、`acceptance-cfd4...`（7 个新增顶层字段）。

## 2. web 模块集成测试不破坏

命令：

```bash
mvn -pl web clean test -Dtest=TaskTypeIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false
```

结果：`Tests run: 26, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`

`TaskTypeIntegrationTest.java:321` 与 `:328` 分别调用 `GET /api/task-review/pending` 与 `GET /api/task-review/history` 验证未授权 401 场景；本 change 为 additive 修改，未触碰鉴权链路，断言继续通过。

绑定的 acceptance：`acceptance-271d...`（例 4：既有集成测试仍通过）。

## 3. 前端渲染代码静态审查（manual）

`web/src/parent/pages/index.tsx:1862-1866`：

```tsx
<Space direction="vertical" size={2}>
  <Typography.Text strong>{item.templateTitle}</Typography.Text>
  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
    {item.childNickname} · {item.submittedAt}
  </Typography.Text>
```

- L1863 `{item.templateTitle}` 用 `Typography.Text strong` 强显示任务名；本 change 已为 item 顶层补 `templateTitle`，渲染为加粗"整理书桌"。
- L1865 `{item.childNickname} · {item.submittedAt}` 模板字符串在两个 undefined 时输出 ` · `（截图现象），本 change 已为 item 顶层补 `childNickname` 与 `submittedAt`，渲染为"小明 · 2026-08-12T10:00:00"。

绑定的 acceptance：`acceptance-f089...`（任务名强显示）、`acceptance-d9be...`（二级文本"昵称 · 时间"）。

## 4. task-review 模块完整单元测试（参考）

命令：

```bash
mvn -pl task-review clean test
```

结果：`Tests run: 50, Failures: 0, Errors: 2, Skipped: 0`。

2 个 ERROR 为预存失败（与本 change 无关，已用 `git stash` 验证在未应用本 change 时同样失败）：
- `TaskReviewServiceTest.shouldCompleteStandingTaskWhenMaxReached`：`expected COMPLETED but was APPROVED`。
- `TaskReviewServiceTest.shouldRejectStandingSubmissionWhenMaxReached`：期望 `BusinessException` 未抛。

# Skipped checks

- 前端浏览器端到端验证（手动打开 `/parent/reviews` 查看渲染）：环境限制本次未执行；前端逻辑层用代码静态审查代替，待集成联调时再做浏览器验证。
- `queryChildHistory`（孩子端历史接口）端到端测试：前端无消费 `/api/task-review/child/{childId}/history`，且后端复用同一 helper，单元测试已通过 `queryPendingReviews` 路径覆盖 `enrichAssignmentWithAttempts` 行为。
- `mvn -pl task-review test` 中 2 个预存失败测试未修复：经 `git stash` 对比确认为本 change 前已存在的业务逻辑 bug（STANDING 任务完成时 `status` 转换问题），与本 change 的字段补全无关，留待后续单独 change 处理。

# Spec consistency

本 change 未新增 spec_delta（capability=parent-task-review 的 spec 仍保持现状）。补字段行为是修补现有契约与前端期望的偏差，不改变 capability 边界。后端 `enrichAssignmentWithAttempts` 返回结构继续保留所有既有字段，对其他消费者（`queryReviewHistory`、`queryChildHistory`）为向后兼容的 additive 变更。

# Known limitations and risks

1. **`isOverdue` 计算口径**：本 change 采用审核列表场景的口径 `deadline != null && deadline.isBefore(LocalDateTime.now())`，不考虑 `status`、`cancelled` 与 REPEAT 周期。理由：`/pending` 接口的 assignment 已由 `queryPendingReviews` 按 status=`SUBMITTED` 筛选；`/history` 接口的 assignment 已被 reviewed，使用相同口径统一更直观。若产品后续要求与 `child-page-migration` capability 中"已逾期"5 分类规则（排除 REPEAT）严格一致，需在此 helper 上层包装或调用一个共享判定器，单独 change 处理。
2. **`attemptId`/`submittedAt`/`notes` 取最新 attempt**：按 attemptNumber ASC，取 `attempts.get(size-1)`。对 pending 列表（典型每个 assignment 仅 1 个 SUBMITTED attempt）正确；对 history 列表（一个 assignment 可能多 attempt 各自被 review）目前只暴露最新一条的 attempt 元信息到顶层。若产品要求展示多 attempt 历史，需要改用展开每条 attempt 为独立 item（结构变更），单独 change 处理。
3. **`childNickname` N+1 查询**：每个 assignment 额外 `taskChildMapper.findById(childId)`。与既有 `taskAttemptMapper.findByAssignmentId` + `taskReviewMapper.findByAttemptId` 同级 N+1；若出现性能问题需做批量 IN 查询改写，单独 change 处理。
4. **预存 2 个 task-review 单元测试失败**：与本 change 无关，留待单独 change 修复。
5. **前端无自动化测试**：本 change 的前端可见效果通过代码静态审查证据，未做浏览器实际验证。

# Conclusion

所有 6 个 acceptance 通过（4 个 automated + 2 个 manual）。后端在 `enrichAssignmentWithAttempts` 中以 additive 方式补全前端 ReviewItem 期望的 7 个顶层字段，保留所有既有字段；同一 helper 被 `queryPendingReviews`/`queryReviewHistory`/`queryChildHistory` 三处复用，故三处接口同步修复。新增 2 个单元测试覆盖字段存在性与 `isOverdue` 双场景；既有 26 个集成测试（`TaskTypeIntegrationTest`）与 48 个单元测试不破坏。

结论：**PASS**。
