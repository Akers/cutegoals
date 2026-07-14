# 任务简报：修正前端单元测试与实现偏离

## 任务定位

- **类型**：实现偏离修正（非新增功能）
- **来源**：core-features change 在实施阶段的前端测试（Web 三端）与当前组件实现不一致
- **范围**：`web/src/__tests__/routing.test.tsx`、`web/src/child/__tests__/App.test.tsx`、`web/src/child/__tests__/pages.test.tsx`
- **验收标准**：`npm test`（vitest run）在 `web/` 目录下全部通过，且不破坏现有组件视觉结构与交互意图

## 当前失败事实

后端测试 `mvn -f server/pom.xml test` 已通过（exit 0）。前端 `npm test` 失败 14 处，集中在：

1. **routing.test.tsx**（6 个失败）：
   - 渲染 `App` 后，测试期望页面出现 `/管理后台/`、`/家长端/`、`/儿童端/` 等文本。
   - 当前 `App.tsx` 通过 `React.lazy` 加载 `AdminApp/ParentApp/ChildApp`，且仅在角色匹配时渲染。失败原因可能是 lazy 组件在测试环境中未正确加载/渲染，或子组件中不存在这些文本。
   - 期望的 `redirects unauthenticated users to the login flow` 使用 `/家长登录/`，但 `ParentLoginPage` 实际标题可能不同。

2. **child/App.test.tsx**（2 个失败）：
   - 直接渲染 `ChildApp`，期望 heading `/今日任务/` 和文本 `/儿童端/`。
   - `ChildHomePage` 使用 `useApi` 发起 fetch，测试未 mock fetch，可能停留在 loading 状态导致 `PageHeader` 的 heading 未渲染。

3. **child/pages.test.tsx**（6 个失败）：
   - 测试通过 mock fetch 验证 `ChildHomePage`、`ChildTasksPage`、`ChildPrizesPage`、`ChildBlindBoxesPage`、`ChildExchangesPage` 的渲染和交互。
   - 失败原因包括：mock 的响应结构与实际 API 调用不匹配、文本查找方式与实际 DOM 结构不一致（如 `120.*积分` 格式）、按钮文案/模态框文案不匹配等。

## 关键实现文件（禁止大幅重构视觉结构）

- `web/src/App.tsx`
- `web/src/child/App.tsx`
- `web/src/child/pages/index.tsx`（ChildHomePage, ChildTasksPage, ChildPrizesPage, ChildBlindBoxesPage, ChildExchangesPage）
- `web/src/parent/pages/index.tsx`（ParentHomePage, ParentLoginPage）
- `web/src/admin/pages/index.tsx`（AdminOverviewPage）
- `web/src/shared/components/`（PageHeader, Layout, LoadingState 等）
- `web/src/shared/hooks/useApi.ts`
- `web/src/shared/auth/AuthGuard.tsx`

## 约束与原则

1. **优先修正测试**：组件视觉/交互实现已经过多次 hotfix 迭代，是当前权威实现。尽量通过调整测试期望、mock 数据、等待条件来匹配实现，而不是反向修改组件文案。
2. **必要时最小化修改组件**：若测试暴露的偏离是真实 bug（如按钮文案与 spec 不一致、API 路径错误），可做最小修正。
3. **保持三端路由结构**：不要移除 lazy loading、AuthGuard、RoleProvider 等核心设计。
4. **TDD 证据**：因是测试修复任务，先提供当前失败的 RED 证据（运行 `npm test` 的输出），修复后提供 GREEN 证据（`npm test` 全部通过）。
5. **测试必须稳定**：避免依赖定时器或网络超时的脆弱断言；使用 `waitFor` 或 `findBy*` 处理异步加载。
6. **产物语言**：zh-CN（中文界面）

## 建议修复方向

1. **routing.test.tsx**：
   - 如果 lazy 加载在测试环境中不可靠，可考虑在测试里直接渲染 `ChildApp/ParentApp/AdminApp`（跳过最外层 `App` 的 lazy + 角色检查），或者在 `App` 测试中为 lazy 组件提供 mock。
   - 更新期望文本以匹配实际 `PageHeader` 标题（如 `实例概览`、`家庭`、`今日任务`）而非旧的“管理后台/家长端/儿童端”占位文本。
   - 登录页标题以 `ParentLoginPage` 实际渲染的 heading 为准。

2. **child/App.test.tsx**：
   - 为 `ChildHomePage` 的 `useApi` 调用 mock fetch，返回 `{ data: { items: [], balance: 0 } }` 或等价的空数据，确保加载完成后 `PageHeader` 渲染 heading `/今日任务/`。
   - 如果 `ChildHomePage` 中不存在“儿童端”文本，可移除该断言或调整期望。

3. **child/pages.test.tsx**：
   - 检查每个测试用例的 fetch mock 顺序、URL、响应结构是否与 `useApi` 的封装一致（注意 `getClient` 的默认 base URL 和响应格式）。
   - 调整文本断言以匹配实际组件文案（如 `120.*积分` 可能应为 `120 积分`；奖品名称查找是否受子元素分割影响）。
   - 检查 `提交` 按钮、`兑换` 按钮、确认模态框等文案是否匹配实际组件中的中文。

## 输出要求

- 报告文件：`openspec/changes/core-features/.comet/fix-frontend-tests-report.md`
- 报告内容必须包含：
  1. 修正前失败清单（RED 证据）
  2. 每个失败的根本原因（按测试文件分类）
  3. 修改了哪些文件，修改方式（测试调整 / 组件调整）
  4. 修正后测试结果（GREEN 证据：npm test 输出关键行）
  5. 是否还有未解决或需要后续 task 追踪的问题
- 所有代码修改通过 `git` 提交，commit message 格式：`fix(web): align tests with current component implementation (core-features)`

## 禁止

- 不要大规模重写组件 UI 或调整路由结构。
- 不要降低测试覆盖率（如果测试确实无法匹配当前实现，应移除并说明原因，而非保留无意义断言）。
- 不要直接跳过失败或在测试中使用 `test.skip` 而不说明理由。
