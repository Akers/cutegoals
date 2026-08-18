# Outcome

家长端任务分配页面（ParentTasksPage）不再因分页参数越界而报「加载失败 Page size must be between 1 and 100」。页面在默认选中今日、切换日期、查看全部等所有交互下都能正常加载任务列表。

# Scope

仅前端修复，不改动后端。

- `web/src/parent/pages/index.tsx`：
  - `buildQueryA(state)` 行 1183：`params.set('pageSize', '200')` → `'100'`
  - `buildQueryRepeat(_state)` 行 1195：`params.set('pageSize', '200')` → `'100'`
- `web/src/parent/pages/__tests__/ParentTasksPage.test.tsx`：将测试 fixture 中所有 `pageSize: 200` 更新为 `pageSize: 100`，保持与实现一致。

# Non-goals

- 不改后端 `TaskAssignmentService` 的 `MAX_PAGE_SIZE`（=100）契约。
- 不改后端 Controller/Mapper/其他 Service。
- 不引入多页拉取（翻页聚合）逻辑；维持单次请求 + 前端合并/去重/后置过滤的既有方案。
- 不改任务列表的合并规则（A∪B∪C）、TaskTypeFilter 后置过滤、日历单日选择等已确认行为。

# Acceptance examples

- A1（默认加载不报错）：进入页面默认选中今日，`buildQueryA` 生成 `pageSize=100`，`buildQueryRepeat` 生成 `pageSize=100&taskType=REPEAT`，两次请求均通过后端校验，页面正常渲染任务列表，不出现「Page size must be between 1 and 100」。
- A2（切换日期）：切换选中日期后，`buildQueryA` 仍携带 `pageSize=100` 与对应 `startDate/endDate`，请求通过校验。
- A3（查看全部）：激活「查看全部」后，`buildQueryA` 不带日期但 `pageSize` 仍为 100，请求通过校验。
- A4（测试一致）：`ParentTasksPage.test.tsx` 全部用例通过，fixture 中 `pageSize` 与实现一致（100）。

# Constraints and invariants

- 后端契约：`TaskAssignmentService` 对 `pageSize` 校验为 `1 ≤ pageSize ≤ MAX_PAGE_SIZE`，`MAX_PAGE_SIZE = 100`（server/task/.../TaskAssignmentService.java:49,399-401）。前端请求必须落在该区间。
- 保持上一 change（parent-task-list-filter）已确认的合并/过滤/日历行为不变。

# Decisions

- D1：修复方式为前端将 `pageSize` 从 200 调整为 100（后端允许的最大值），不改后端。理由：上一 change 的非目标即「不改后端」，且 100 是后端契约允许的最大单页量，能在不触碰后端的前提下最大化单次拉取覆盖。
- D2：不引入翻页聚合（拉取多页拼全量）。理由：上一 change 已将「超量截断」记录为已知限制；本 change 仅修复回归，不扩大范围。若未来单类任务超 100 条需全量，再另立 change 处理。

# Open questions

（无；共享理解已于 Build 前经用户确认。）

# Verification expectations

- 运行 `ParentTasksPage.test.tsx` 全部用例通过。
- 运行 `TaskCalendar.test.tsx` 确认零回退。
- 类型检查（tsc）不引入新错误（预存 TS6198 与本次无关）。
