# Tasks: fix-admin-pages-data-shape

## 1. 代码改动

- [x] 1.1 读取 `InstanceHealthService.getBackupStatus()` 与 `getRecoveryDrillStatus()` 源码，确认 health 子字段（`status`、`lastBackupTime`、`lastDrillTime` 等），用于派生 checks 数组
- [x] 1.2 在 `web/src/admin/pages/index.tsx` 文件顶部新增 `interface PageResult<T> { content: T[]; page: number; pageSize: number; totalElements: number; totalPages: number; }`
- [x] 1.3 修改 `AdminAccountsPage`：`useApi<Account[]>` → `useApi<PageResult<Account>>`，将 `data.map(...)` 改为 `data.content.map(...)`，`data.length === 0` 改为 `data.content.length === 0`；额外对齐 `Account` interface 字段（`role`→`roles[]`、`enabled`→`status`）+ 渲染逻辑
- [x] 1.4 修改 `AdminAuditPage`：`useApi<AuditLog[]>` → `useApi<PageResult<AuditLog>>`，将 `data.map(...)` 改为 `data.content.map(...)`，`data.length === 0` 改为 `data.content.length === 0`；额外对齐 `AuditLog` interface 字段（`operator`→`actorId+actorType`、`action`→`eventType`、新增 `result` 列）
- [x] 1.5 修改 `AdminHealthPage`：扩展 `HealthData` 类型对齐后端字段（`status`、`initialized`、`version`、`buildTime`、`buildCommit`、`database: { status, type }`、`backup`、`recoveryDrill`、可选 `rpoWarning`、`rpoWarningMessage`），从 `database` / `backup` / `recoveryDrill` 派生 checks 数组（保持 UI 结构不变）

## 2. 验证

- [x] 2.1 前端类型检查：`npx tsc -b`（项目 web 目录），0 errors（exit 0，无输出）
- [x] 2.2 前端单测：`npm test`，10 文件中 4 失败（14 tests）/ 6 通过（65 tests）；已用 `git stash` 对照 baseline（HEAD=`70c4f4a`）确认这 14 个失败全部是 fix-admin-pages-500 时已存在的预存失败（routing/parent/child 端，与 admin 改动无关）；admin 测试 `src/admin/__tests__/App.test.tsx` 通过；本改动 0 新增失败
- [x] 2.3 端到端验证：CLI agent 无浏览器工具，采用等价证据覆盖：① `npx tsc -b` 0 errors（类型对齐证明）；② `npm test` 中 `src/admin/__tests__/App.test.tsx` 通过（admin 页面组件渲染未破坏）；③ grep 根因消除证据（`useApi<Account[]>`/`useApi<AuditLog[]>`/`data.map(`/`data.checks` 全部清除，`data.content.map` 与 `const checks` 全部就位）；④ 上一 hotfix fix-admin-pages-500 已确认 `/api/admin/accounts` 与 `/api/admin/audit-logs` 返回 401（认证拦截正常），后端 API 响应正常。运行时浏览器端验证留给用户在 dev server+前端 vite 启动后做最终确认
- [x] 2.4 提交代码：commit `471c59c fix(admin): align page data shape with backend paginated/health response`
