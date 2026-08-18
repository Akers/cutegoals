# Acceptance evidence

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-0316fab6dc2fe5e455dcd009654a4a0e0ff70266aa3104ef798b6f4c3ad32ec8",
    "status": "passed",
    "evidence_refs": [
      "runtime/evidence/receipts/aad397af6ced018ef9b1d74328c26ecb825bca78e3155cd700032fe186d57a74.json"
    ]
  },
  {
    "acceptance_id": "acceptance-05ecb7b1eddc8857bcf1dbe815ca9a09c5494947a01ea80e2204ec4636d16b31",
    "status": "passed",
    "evidence_refs": [
      "runtime/evidence/receipts/aad397af6ced018ef9b1d74328c26ecb825bca78e3155cd700032fe186d57a74.json"
    ]
  },
  {
    "acceptance_id": "acceptance-54239b916796c20ebe644b941f9e5f1a2c822ee3cf8c40b89de66364cc08b44d",
    "status": "passed",
    "evidence_refs": [
      "runtime/evidence/receipts/24468add9f7afa4c2679433d909fc2746338659be9a95b628feb2316ae35bafb.json",
      "runtime/evidence/receipts/aad397af6ced018ef9b1d74328c26ecb825bca78e3155cd700032fe186d57a74.json",
      "runtime/evidence/receipts/fb93ae13e71f9f30482efe99c533e60bdd8937629fef13a9450a59c031b27d57.json"
    ]
  },
  {
    "acceptance_id": "acceptance-61cd34ce07e38fe4b1cf432fb42ee8c075fd27a0be27701389059ed4741777a0",
    "status": "passed",
    "evidence_refs": [
      "runtime/evidence/receipts/aad397af6ced018ef9b1d74328c26ecb825bca78e3155cd700032fe186d57a74.json"
    ]
  },
  {
    "acceptance_id": "acceptance-7d11fe7b54929ba52ead4171e4f4b380934f3e059bd09fe18b54bfa950c9e86b",
    "status": "passed",
    "evidence_refs": [
      "runtime/evidence/receipts/aad397af6ced018ef9b1d74328c26ecb825bca78e3155cd700032fe186d57a74.json"
    ]
  },
  {
    "acceptance_id": "acceptance-7d7f19129cd7d60844782aaff20c97784dcc736a9492bcf8a134630dd3da5368",
    "status": "passed",
    "evidence_refs": [
      "runtime/evidence/receipts/aad397af6ced018ef9b1d74328c26ecb825bca78e3155cd700032fe186d57a74.json"
    ]
  },
  {
    "acceptance_id": "acceptance-afea6e22a6d1f8ba70b9440e60359d5ad6eb4996688ed3759e09520f2f1ab6b9",
    "status": "passed",
    "evidence_refs": [
      "runtime/evidence/receipts/fb93ae13e71f9f30482efe99c533e60bdd8937629fef13a9450a59c031b27d57.json"
    ]
  },
  {
    "acceptance_id": "acceptance-b2e4c4b3b629cb666fccd343563d916123f5c8083d95994cd5ae468e16c5438a",
    "status": "passed",
    "evidence_refs": [
      "runtime/evidence/receipts/24468add9f7afa4c2679433d909fc2746338659be9a95b628feb2316ae35bafb.json",
      "runtime/evidence/receipts/aad397af6ced018ef9b1d74328c26ecb825bca78e3155cd700032fe186d57a74.json"
    ]
  },
  {
    "acceptance_id": "acceptance-d4ce6d57314389f89c5ebc39a7cebafc181a5a13ab07a00e2689ec3c4397c66b",
    "status": "passed",
    "evidence_refs": [
      "runtime/evidence/receipts/24468add9f7afa4c2679433d909fc2746338659be9a95b628feb2316ae35bafb.json"
    ]
  },
  {
    "acceptance_id": "acceptance-ec3eb68b16a705b0cf13f58040723a8a19948ffe9478dce3d522ad4c6681dbf3",
    "status": "passed",
    "evidence_refs": [
      "runtime/evidence/receipts/aad397af6ced018ef9b1d74328c26ecb825bca78e3155cd700032fe186d57a74.json"
    ]
  },
  {
    "acceptance_id": "acceptance-f8d42ecdb42581b5f6ee950e0152a558583c1b8f8a5b711b0357b5ac55af627d",
    "status": "passed",
    "evidence_refs": [
      "runtime/evidence/receipts/aad397af6ced018ef9b1d74328c26ecb825bca78e3155cd700032fe186d57a74.json"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->

# Commands and results

1. 孩子端单元测试（receipt `runtime/evidence/receipts/aad397af6ced018ef9b1d74328c26ecb825bca78e3155cd700032fe186d57a74.json`，exit 0）：
   `bash -c 'cd /home/akers/projects/cutegoals/web && set -o pipefail && npx vitest run src/child 2>&1 | tail -12'`
   结果：`Test Files 3 passed (3)`，`Tests 10 passed (10)`（含 ChildTasksPage 4 个用例：渲染 content、可见性过滤规则——REPEAT 未来可见/非 REPEAT 未来不可见/已取消不可见/今日日期可见、空态）。
2. cici 真实环境端到端核对（receipt `runtime/evidence/receipts/24468add9f7afa4c2679433d909fc2746338659be9a95b628feb2316ae35bafb.json`，exit 0）：
   `bash -c 'set -o pipefail; python3 /tmp/opencode/cici-live-check.py 2>&1 | tail -20'`
   脚本以 cici（childId=2，PIN 登录）调用 `POST /api/auth/child/login` 与 `GET /api/task-assignments?childId=2&pageSize=100`（与孩子端相同 URL），并施加与 ChildTasksPage 完全相同的过滤规则。
   结果：API 返回 `content` 12 条；过滤后可见 11 条（ids 1,2,4,5,6,7,8,9,10,11,12），仅排除已取消的 id=3；可见 REPEAT 2 条。断言全部通过。
3. 前端全量回归（receipt `runtime/evidence/receipts/fb93ae13e71f9f30482efe99c533e60bdd8937629fef13a9450a59c031b27d57.json`，exit 0）：
   `bash -c 'cd /home/akers/projects/cutegoals/web && set -o pipefail && npx vitest run 2>&1 | tail -8'`
   结果：`Test Files 18 passed (18)`，`Tests 187 passed (187)`。
4. 类型检查（未生成 receipt，仅供参考）：`npx tsc --noEmit` 仅报告 1 个既有错误 `src/parent/components/TaskTypeConfigForms.tsx(241,29): error TS6198: All destructured elements are unused`——该文件不在本变更 diff 内（`git diff` 仅含 web/src/child/ 两文件），为变更前已存在，与本变更无关。

# Skipped checks

- 浏览器手工 UI 走查未执行：以「真实 API + 与页面完全相同的过滤逻辑」的端到端脚本（命令 2）与覆盖渲染/过滤/空态的组件测试（命令 1）替代，二者共同等价验证页面展示结果。
- `tsc --noEmit` 未作为 receipt：存在与本变更无关的既有错误（见 Commands and results 第 4 条），无法以 exit 0 通过；已核实无新增错误。

# Spec consistency

- 完整目标规格（specs/child-page-migration/spec.md）仅含本变更新确立的「我的任务列表数据契约与展示规则」要求，与 brief 的 Outcome/Scope/Acceptance examples 一致；用户已于 2026-08-12 两次确认（共享理解、契约修订再确认，见 brief Decisions D5/D6）。
- 实现与规格逐条对应：读取 `content` 字段（index.tsx ChildHomePage/ChildTasksPage）、列表规则 visibleTasks（未取消 ∧ (REPEAT ∨ 日期≤今天)）、空态「暂无任务」。
- 后端 API 契约未改动；家长端、日历、审核流程不受影响（全量回归 187 通过）。

# Known limitations and risks

- 孩子端列表一次性请求 pageSize=100（与家长端一致）；若单孩子分配超过 100 条，需后续引入分页/滚动加载（当前数据 12 条，远未触及）。
- 「开始日期」按 deadline 日期部分映射（数据模型无独立开始日期字段），为用户已确认的语义（含当天）。

# Conclusion

pass：11 项验收全部由真实 automated receipt 覆盖并通过；孩子端「我的任务」修复后正确列出有效 REPEAT 任务与任务日期不晚于今天的任务，已取消任务不展示，cici 实测 12→11 与验收示例一致；全量回归无破坏。
