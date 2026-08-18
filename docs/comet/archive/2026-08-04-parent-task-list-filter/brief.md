# Outcome

家长端「任务分配」页面的任务列表展示逻辑改为：以日历选中日为锚点，合并显示三类任务——截止日落在选中当天的任务、所有每周重复任务、所有每日重复任务。让家长在某一天能看到当天需处理的任务以及所有需要每日/每周关注的重复任务。

# Scope

- 修改 `web/src/parent/pages/index.tsx` 中 `ParentTasksPage`：
  - `CalendarPageState` 简化：`selectedRange` → `selectedDate: string | null`；移除 `viewAllMode`，由 `selectedDate === null` 表达「查看全部」。
  - 移除 `calendarReducer` 中 `SELECT_WEEK`、`SELECT_MONTH`、`VIEW_ALL` 分支；保留 `SELECT_DATE`、`NAV_MONTH`、`SET_FILTERS`，新增 `CLEAR_DATE` 替代原 `VIEW_ALL`。
  - 改写 `buildQuery`：不再以 `taskType` 切换 query；改为输出 2 个查询串（A 类与 REPEAT 全量），由 `useApi` 各拉一次。
  - 新增合并/去重逻辑：A ∪ B ∪ C（B/C 在前端按 `typeConfig.frequency` 过滤 REPEAT 全量）。
  - 新增 `TaskTypeFilter` 后置过滤：根据 `taskTypeFilters` 过滤最终集合。
  - `TaskCalendar` 仅暴露单日选择交互（去掉 week/month 入口）。
- 同步更新 `web/src/parent/pages/__tests__/ParentTasksPage.test.tsx`：
  - 删除/重写原 `SELECT_WEEK`、`SELECT_MONTH`、`VIEW_ALL` reducer 用例。
  - 新增「A ∪ B ∪ C 合并去重」、「DAILY/WEEKLY 全显」、「TaskTypeFilter 后置过滤」、「CLEAR_DATE」用例。
  - 调整默认「选中今日」用例适配新 state 形状。
- 不改后端 `TaskAssignmentController` / `TaskAssignmentMapper`。

# Non-goals

- 不改后端 REPEAT 任务的生成调度逻辑（`RepeatTaskScheduler`、`TaskTemplateFrequencyService`）。
- 不改任务详情页、创建/编辑模板流程。
- 不引入新的任务类型枚举。
- 不改孩子端任何逻辑。

# Acceptance examples

- **A1 默认进入页面**：选中今日 D。列表显示：
  - 所有 `deadline` 为 D 的任务（不论 LIMITED/REPEAT/STANDING 或 MONTHLY/YEARLY）；
  - 所有 `frequency=WEEKLY` 的 REPEAT 任务（无论 `trigger_day.weekday` 是否等于 D 的周几）；
  - 所有 `frequency=DAILY` 的 REPEAT 任务；
  - 同一任务在多类命中时只显示一次（按 id 去重）。
- **A2 切换日期**：选中 D'（≠ D）。
  - 第一类（deadline 命中）随 D' 变化；
  - 第二、三类（WEEKLY/DAILY 全量）保持不变。
- **A3 TaskTypeFilter 后置过滤**：默认全选 → 显示完整集合；取消 REPEAT → 列表中所有 `taskType=REPEAT` 任务（含 DAILY/WEEKLY/MONTHLY/YEARLY 与第一类中的 REPEAT）全部消失；恢复勾选后重新出现。
- **A4 查看全部**：点击「查看全部」 → `selectedDate=null`，第一类扩展为「所有 deadline」；再次点击或选择某日 → 恢复单日视图。
- **A5 月切换不影响 WEEKLY/DAILY 全量**：NAV_MONTH 切换 baseMonth 后，B、C 两类集合不变。
- **A6 空态**：合并后集合为空时仍显示「当天暂无任务」。
- **A7 默认选中今日不回退**：fix-calendar-default-current-date 的回归用例继续通过（适配新 state 形状）。

# Constraints and invariants

- 不破坏现有 calendarReducer 行为契约（除非澄清后明确移除某种选择模式）。
- 不回退 fix-calendar-default-current-date 的「默认选中今日」。
- `useApi` 拉取路径仍为 `/task-assignments`。
- `pageSize=20` 默认值若不足以容纳「所有 DAILY/WEEKLY 任务」时需要明确处理策略。
- 不在摘要、报告或产物中写入敏感凭据。

# Decisions

- 默认进入 Shape，沿用现有 `TaskTypeValue = 'LIMITED' | 'REPEAT' | 'STANDING'` 与 `snapshotTemplateTypeConfig.frequency` 字段判定任务类别。
- 沿用现有「`deadline` 落在 `[startDate, endDate]` 区间」的语义表达「截止日为选中当天」。
- **D1（用户确认）**：规则 1 解释为「任何 taskType，只要 deadline 命中选中当天就显示」。即合并集合：
  - A = `{ task | task.deadline == selectedDay }`（包含 LIMITED / STANDING / MONTHLY / YEARLY 以及 WEEKLY/DAILY 中 deadline 恰好命中当天的）
  - B = `{ task | task.snapshotTemplateTaskType == 'REPEAT' && typeConfig.frequency == 'WEEKLY' }`
  - C = `{ task | task.snapshotTemplateTaskType == 'REPEAT' && typeConfig.frequency == 'DAILY' }`
  - 最终列表 = A ∪ B ∪ C，按 id 去重。
- **D2（由 D1 推导）**：B、C 两类忽略日历选择，无论 `trigger_day` 是否命中都显示。
- **D3（用户确认）**：UI 控件去留：
  - 取消周选择、月选择，日历仅保留单日选择。
  - 保留 `TaskTypeFilter` 三个 Checkbox（限时/重复/常驻），作为对最终合并集合的「后置过滤维度」：
    - 默认全选 → 显示 A ∪ B ∪ C 完整集合。
    - 取消勾选 LIMITED → 移除最终集合中所有 `taskType=LIMITED` 的任务。
    - 取消勾选 REPEAT → 移除最终集合中所有 `taskType=REPEAT` 的任务（覆盖 DAILY/WEEKLY/MONTHLY/YEARLY 全部）。
    - 取消勾选 STANDING → 移除最终集合中所有 `taskType=STANDING` 的任务。
  - 保留「查看全部」按钮，语义为「临时绕过 A 类的日期过滤」：A 扩展为「所有 deadline 的任务」，仍受 TaskTypeFilter 后置过滤影响；再次点击恢复单日选择。
- **D4（实现选择）**：过滤层采用前端合并方案，不改后端：
  - `useApi` 拉取两次：`/task-assignments?startDate=D&endDate=D&pageSize=200`（A 类）+ `/task-assignments?taskType=REPEAT&pageSize=200`（B/C 类来源，再在前端按 `typeConfig.frequency` 过滤）。
  - 「查看全部」时改为 `/task-assignments?pageSize=200` + `/task-assignments?taskType=REPEAT&pageSize=200`。
  - 取消 `viewAllMode` 与 `selectedRange.type` 中 `week`/`month` 分支，`CalendarPageState.selectedRange` 简化为 `selectedDate: string | null`。
  - `TaskTypeFilter` 默认全选保留；`buildQuery` 不再因 length=3 省略 taskType，统一交给前端后置过滤。
  - 若实际任务总量超过 pageSize 上限，后续可改为后端 `taskFrequency` 参数；当前不引入。
- **D5（实现选择）**：保持 `pageSize=200` 作为兼顾常规家庭任务量的安全上限，避免 DAILY/WEEKLY 全量被分页截断；分页 UI 暂不引入。

# Open questions

（无未决问题。共享理解已由用户确认，详见上方 Decisions D1-D5。）

# Verification expectations

- `web/src/parent/pages/__tests__/ParentTasksPage.test.tsx` 中相关用例更新并通过（含 A1–A7）。
- `npm test --prefix web`（或等价 vitest 命令）在 `web` 目录全部通过。
- `npm run -w web lint`（若存在）不引入新告警。
- `npm run -w web build`（若存在）成功，不破坏既有构建。
- 手动验证：切换日历日期、点击「查看全部」、操作 TaskTypeFilter，列表符合 A1–A7 描述。
