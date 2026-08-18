# Acceptance evidence

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-0fd8dc6335eb7c6f0517fd87e0df51cb495de4f660b6990e9c8e65e8c1ed3100",
    "status": "passed",
    "evidence_refs": [
      "runtime/evidence/receipts/de2d64435c239d9cc8e74aca4e63ab7cbaded82343f149a5769cb0f1908bce52.json"
    ]
  },
  {
    "acceptance_id": "acceptance-112e58a95444c33574aac89fef15988cffee448051415cc9d6a4fd39567c71df",
    "status": "passed",
    "evidence_refs": [
      "runtime/evidence/receipts/de2d64435c239d9cc8e74aca4e63ab7cbaded82343f149a5769cb0f1908bce52.json"
    ]
  },
  {
    "acceptance_id": "acceptance-755c625bd321499571f65422ec2b8ce0b54f17b75280cc8b0c056948e23486b0",
    "status": "passed",
    "evidence_refs": [
      "runtime/evidence/receipts/de2d64435c239d9cc8e74aca4e63ab7cbaded82343f149a5769cb0f1908bce52.json"
    ]
  },
  {
    "acceptance_id": "acceptance-8afae9346fc7726dd78601c5058062346e4d8a6d0196643a55079ffc9f468fd7",
    "status": "passed",
    "evidence_refs": [
      "runtime/evidence/receipts/de2d64435c239d9cc8e74aca4e63ab7cbaded82343f149a5769cb0f1908bce52.json"
    ]
  },
  {
    "acceptance_id": "acceptance-a324e5f8168c070e3ed69188f4882f4ff276005a7fed40b44248c2bed882f26f",
    "status": "passed",
    "evidence_refs": [
      "runtime/evidence/receipts/de2d64435c239d9cc8e74aca4e63ab7cbaded82343f149a5769cb0f1908bce52.json"
    ]
  },
  {
    "acceptance_id": "acceptance-c00580bf2f23f83b72dbbf022296d26c1fd797321101ad399635418d1db364da",
    "status": "passed",
    "evidence_refs": [
      "runtime/evidence/receipts/de2d64435c239d9cc8e74aca4e63ab7cbaded82343f149a5769cb0f1908bce52.json"
    ]
  },
  {
    "acceptance_id": "acceptance-d0ba91fe8ab9f101a59c2454c94176837765e65d5c6bacbdf7b7abb5ab46e421",
    "status": "passed",
    "evidence_refs": [
      "runtime/evidence/receipts/de2d64435c239d9cc8e74aca4e63ab7cbaded82343f149a5769cb0f1908bce52.json"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->

# Commands and results

## 主验证命令

```bash
pnpm --filter web exec vitest run \
  src/parent/pages/__tests__/ParentTasksPage.test.tsx \
  src/parent/components/__tests__/TaskCalendar.test.tsx
```

输出（receipt `de2d6443...`）：

```
 ✓ src/parent/components/__tests__/TaskCalendar.test.tsx  (30 tests) 202ms
 ✓ src/parent/pages/__tests__/ParentTasksPage.test.tsx    (31 tests) 383ms

 Test Files  2 passed (2)
      Tests  61 passed (61)
   Duration  1.48s
```

## 验收映射

- **A1 默认今日合并三类**：`ParentTasksPage.test.tsx` 中「A∪B∪C 合并去重」「DAILY/WEEKLY 全显」组件测试覆盖；reducer SELECT_DATE 测试覆盖初值 = todayStr。
- **A2 切换日期仅动 A 类**：`SELECT_DATE` reducer 测试 + `buildQueryA(state)` 仅依赖 `selectedDate`、`buildQueryRepeat(state)` 与 state 无关的源码事实。
- **A3 TaskTypeFilter 后置过滤**：新增「TaskTypeFilter 后置过滤」组件测试，触发 onChange `['LIMITED','STANDING']` 后断言 REPEAT 任务消失。
- **A4 查看全部 selectedDate=null**：`CLEAR_DATE` reducer 测试 + 查看全部按钮在 `selectedDate===null` 时显示「查看全部（已激活）」并切回 SELECT_DATE today 的实现。
- **A5 NAV_MONTH 不影响 B/C**：`buildQueryRepeat` 函数体不引用 `state.baseMonth`；`NAV_MONTH` reducer 测试仅改 baseMonth 不改 selectedDate。
- **A6 空态仍显示「当天暂无任务」**：空态组件测试断言 `当天暂无任务` 文案存在。
- **A7 默认今日回归**：`fix-calendar-default-current-date` 测试用 fake timer `2026-07-24T12:00:00` 断言 mock-task-calendar `data-selected=2026-07-24_2026-07-24`、`data-base-month=2026-07`。

## TaskCalendar 兼容性

TaskCalendar 自身测试 30/30 全部通过，证明新增 `singleDayOnly` 可选 prop 未回退既有周/月选择行为。

# Skipped checks

- `pnpm --filter web lint`：未在 receipt 中执行；本项目 web 工程未配置独立 lint 脚本（package.json 仅 typecheck + test + build）。可后续接入 ESLint 平台时补测。
- `pnpm --filter web build`：未运行；本次 change 仅触及 parent 模块的两个 tsx 文件与一个测试文件，build 验证不在 brief 强制清单内，且单次验证已通过 vitest + 类型环境覆盖。
- 手动浏览器交互（点击日历切换日期、勾选 TaskTypeFilter、点击查看全部）：未在 CI 中执行；逻辑等价由组件测试覆盖（见上文验收映射）。

# Spec consistency

- `brief.md` Scope 全部条目落实：
  - `CalendarPageState` 已简化为 `{ baseMonth, selectedDate: string | null, taskTypeFilters }`。
  - `CalendarAction2` 移除 `SELECT_WEEK` / `SELECT_MONTH` / `VIEW_ALL`，新增 `CLEAR_DATE`。
  - `buildQuery` 拆为 `buildQueryA(state)` + `buildQueryRepeat(state)`，pageSize=200。
  - 合并/去重 useMemo 按 id 去重 + TaskTypeFilter 后置过滤。
  - TaskCalendar 新增 `singleDayOnly` prop，仅暴露单日选择。
- `brief.md` Non-goals 全部遵守：
  - 未改后端 `TaskAssignmentController` / `TaskAssignmentMapper` / `RepeatTaskScheduler` / `TaskTemplateFrequencyService`。
  - 未改任务详情、创建模板流程、孩子端代码。
  - 未引入新任务类型枚举。
- Decisions D1-D5 实现一致：
  - D1：A 类按 deadline 命中、B/C 按 frequency 过滤。
  - D2：B/C 不引用 `trigger_day`。
  - D3：取消周/月选择；TaskTypeFilter 作为后置过滤；查看全部按钮 selectedDate=null。
  - D4：两次 useApi 前端合并方案。
  - D5：pageSize=200。

# Known limitations and risks

- **pageSize=200 上限**：若某个家庭 DAILY/WEEKLY 任务总量超过 200，REPEAT 全量查询会被截断。当前家庭场景估算远低于上限，超量场景需改为后端按 frequency 过滤（已记入 brief D4 末段作为后续演进项）。
- **typeConfig JSON 容错**：`parseFrequency` 失败返回 null，则该 REPEAT 任务不会被纳入 B/C；仍可能因 deadline 命中而出现在 A 类。
- **两次 useApi 并发**：queryA 与 queryRepeat 各自独立请求，loading/error 合并采用 `loadingA || loadingRepeat` 与 `errorA ?? errorRepeat`；refetch 用 `Promise.all`。无竞态保护，但 useApi 内部 debounce 300ms + state 同步 effect 已避免抖动。
- **tsc 预存错误**：`web/src/parent/components/TaskTypeConfigForms.tsx` 存在与本次 change 无关的 TS6198 错误（此前已有），不影响本次 vitest 验收。

# Conclusion

7/7 acceptance 全部通过；61/61 vitest 用例通过；实现与 brief 的 Scope / Non-goals / Decisions D1-D5 完全对齐。Comet Native change `parent-task-list-filter` 已具备归档条件。
