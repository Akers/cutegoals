# Verification Report: fix-admin-pages-missing-layout

- **Change**: `fix-admin-pages-missing-layout`
- **Workflow**: hotfix
- **Verify mode**: light（手动覆盖；scale 自动评估为 full 因 tasks=10 误判，但实际改动 1 文件 / 0 delta / 20 行替换，无跨模块协调）
- **Language**: zh-CN
- **Date**: 2026-07-13
- **Commit**: `5654569 fix(admin): wrap early returns in Layout to preserve header/menu/footer`
- **Branch**: main（直接提交，与前 4 个 hotfix 同策略）

## 1. 根因回顾

`web/src/admin/pages/index.tsx` 中 5 个 admin page 组件（Overview/Config/Accounts/Audit/Health）的所有 early return 分支（offline/loading/error/empty）直接返回单组件（`<OfflineState>` / `<LoadingState>` / `<ErrorState>` / `<EmptyState>`），**未用 `<Layout>` 包裹**，导致页面骨架（header 主导航菜单 + footer）被绕开。

- 共 **5 组件 × 4 处 = 20 处** early return 缺 Layout
- 用户只在审计页复现：dev 环境审计日志为空触发 empty 分支；其他 4 页在 dev 有数据，但 loading/error/offline 同样会复现（系统性缺陷）
- Layout 组件（`web/src/shared/components/Layout.tsx` L37-86）渲染 `<header>`（含 NavLinks admin 菜单）+ `<main>` + `<footer>`

## 2. 修复内容

将 20 处裸露 early return 用 `<Layout>` 包裹：

```tsx
// Before
return <EmptyState title="暂无审计日志" message="当前列表为空" />;
// After
return <Layout><EmptyState title="暂无审计日志" message="当前列表为空" /></Layout>;
```

涉及 5 个组件的 4 类 early return：

| 组件 | 行号（commit 5654569 后） | EmptyState 锚定 title |
|------|--------------------------|----------------------|
| AdminOverviewPage | L43-46 | 暂无数据 |
| AdminConfigPage | L111-114 | 暂无配置 |
| AdminAccountsPage | L162-165 | 暂无账号 |
| AdminAuditPage | L224-227 | 暂无审计日志 |
| AdminHealthPage | L292-295 | 暂无健康数据 |

AdminHealthPage 的 `const checks: HealthCheck[]` 派生逻辑（L297-320）保留在 early return 之后，主 JSX 不动（TypeScript narrowing 需要）。

## 3. 轻量验证结果（6 项）

| # | 检查项 | 结果 | 证据 |
|---|--------|------|------|
| 1 | tasks.md 全部 `[x]` | PASS | `grep -c '\- \[x\]'` = 10，`grep -c '\- \[ \]'` = 0 |
| 2 | 改动文件与 tasks 一致 | PASS | `git diff --stat 5654569~1..5654569` = 1 file changed, 20 insertions(+), 20 deletions(-)（5 组件 × 4 处） |
| 3 | 编译通过（tsc -b） | PASS | `npx tsc -b` exit 0，0 errors |
| 4 | 相关测试通过 | PASS | `npm test src/admin/__tests__/App.test.tsx` = 2/2 passed；全套 14 failed / 65 passed（routing/parent/child 端，与 data-shape hotfix baseline 完全一致，0 新增失败） |
| 5 | 无明显安全问题 | PASS | diff 中 grep `(password|secret|api_key|token)` = 0 matches；纯 UI 结构调整，无数据/认证/权限改动 |
| 6 | 简化代码审查 | SKIP | `review_mode: off`（hotfix preset 强制）；纯机械包裹，无逻辑变更 |

### 根因消除 grep 证据

- 裸露 early return（`return <(OfflineState|LoadingState|ErrorState|EmptyState)`）：**0 matches**
- Layout 包裹的 early return（`return <Layout><(OfflineState|LoadingState|ErrorState|EmptyState)`）：**20 matches**（5 组件 × 4 处）

## 4. Baseline 对照

工作树改动 stash 后跑 baseline 测试：

- baseline：14 failed / 65 passed（routing/parent/child 端预存失败，与本 change 无关）
- 本 change：14 failed / 65 passed
- **0 新增失败**

admin 端 `src/admin/__tests__/App.test.tsx` 2/2 通过（含 admin 路由渲染）。

## 5. 分支处理

commit `5654569` 已直接提交到 main 分支（与前 4 个 hotfix 同策略：fix-admin-401 / fix-admin-pages-500 / fix-admin-pages-data-shape）。HEAD 领先 origin/main 5 commit。

## 6. 未覆盖项（轻量验证跳过）

- spec scenario 覆盖率（本 change 无 delta spec，admin-access-control capability 验收语义未变）
- design doc 一致性深度比对（hotfix 精简 design，无独立 design doc）
- delta spec 与 design doc 漂移检测（无 delta spec）

## 7. 结论

**PASS** — 6 项轻量验证全部通过（review_mode=off 跳过 code review）。根因已消除：20 处裸露 early return 全部用 `<Layout>` 包裹，admin 5 页在 offline/loading/error/empty 四类状态下都将保留 header 主导航菜单 + footer。修复符合 proposal/design/tasks 描述，无新增测试失败，无安全风险。
