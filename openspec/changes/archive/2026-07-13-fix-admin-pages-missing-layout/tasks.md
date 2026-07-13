# Tasks: fix-admin-pages-missing-layout

## 1. 代码改动

- [x] 1.1 修改 `AdminOverviewPage`（`web/src/admin/pages/index.tsx` L43-46）：将 4 个 early return（`<OfflineState>` / `<LoadingState>` / `<ErrorState>` / `<EmptyState>`）用 `<Layout>` 包裹
- [x] 1.2 修改 `AdminConfigPage`（L111-114）：同上，4 个 early return 用 `<Layout>` 包裹
- [x] 1.3 修改 `AdminAccountsPage`（L162-165）：同上，4 个 early return 用 `<Layout>` 包裹
- [x] 1.4 修改 `AdminAuditPage`（L224-227）：同上，4 个 early return 用 `<Layout>` 包裹（用户报告的核心场景）
- [x] 1.5 修改 `AdminHealthPage`（L292-295）：同上，4 个 early return 用 `<Layout>` 包裹；保留了 L297-320 `const checks` 派生数组在 early return 之后（TypeScript narrowing 需要），主 JSX 不动

## 2. 验证

- [x] 2.1 前端类型检查：`npx tsc -b`，exit=0，0 errors
- [x] 2.2 前端单测：`npm test`，10 文件 14 failed / 65 passed；admin 测试 `src/admin/__tests__/App.test.tsx` 2/2 通过；baseline 对照（git stash 后跑）确认 0 新增失败（HEAD=edcbae3 同样 14 failed / 65 passed）
- [x] 2.3 根因消除证据：grep 确认裸露 early return 0 处，`<Layout>` 包裹的 early return 20 处（5 组件 × 4 处），分布与 design.md 一致
- [x] 2.4 端到端验证：CLI agent 无浏览器工具，采用等价证据覆盖：① `npx tsc -b` 0 errors；② admin test 2/2 通过；③ grep 根因消除（裸露 0、包裹 20）；④ 用户报告场景（审计页空数据）的修复由 L227 静态检查证明 `if (!data || data.content.length === 0) return <Layout><EmptyState title="暂无审计日志" /></Layout>`。运行时浏览器端验证留给用户在 dev server 启动后做最终确认
- [x] 2.5 提交代码，commit message：`fix(admin): wrap early returns in Layout to preserve header/menu/footer`
