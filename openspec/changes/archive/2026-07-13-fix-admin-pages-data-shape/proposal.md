# Proposal: fix-admin-pages-data-shape

## Why（问题与根因）

点击「账号管理」「审计日志」「健康面板」三个管理后台菜单进入页面时，页面无内容显示，浏览器 console 抛出运行时错误：

```
Uncaught TypeError: data.map is not a function
    at AdminAccountsPage (index.tsx:171:19)
```

**根因**：前端三个 admin 页面的 `useApi<T>('/admin/...')` 类型参数与后端实际响应体结构不匹配。

- `useApi.ts` 第 38 行 `setData(response.data)` 直接把后端 `ApiResponse.data` 字段赋给前端 `data`，**未做任何解包或形状转换**。
- 后端实际返回（经 `AccountManagementService.getAccounts` / `AuditLogService.queryAuditLogs` / `InstanceHealthService.getAdminHealth` 确认）：
  - `/api/admin/accounts` → `{ content, page, pageSize, totalElements, totalPages }`（分页包装 Map）
  - `/api/admin/audit-logs` → `{ content, page, pageSize, totalElements, totalPages }`（分页包装 Map）
  - `/api/admin/health` → `{ status, initialized, version, buildTime, buildCommit, database: { status, type }, backup: {...}, recoveryDrill: {...}, rpoWarning?, rpoWarningMessage? }`
- 前端期望：
  - `AdminAccountsPage`：`useApi<Account[]>` 直接对 `data.map(...)` → 报错 `data.map is not a function`（`data` 是对象，不是数组）
  - `AdminAuditPage`：`useApi<AuditLog[]>` 同样 `data.map(...)` → 同样的 TypeError
  - `AdminHealthPage`：`useApi<HealthData>` 中 `HealthData = { status, checks: [{name, healthy, message}] }`，访问 `data.checks.map(...)` 时 `data.checks` 为 `undefined` → 同样 TypeError

**为什么上个 hotfix（fix-admin-pages-500）之后才暴露**：fix-admin-pages-500 修复了 `-parameters` 编译标志，使后端能正常返回 200 而非 500。之前页面在「加载失败」阶段就 stop 了（HTTP 500 触发 `ErrorState`），未到达 `data.map` 渲染阶段；现在 200 返回正常数据后，前端类型与数据形状不匹配的预存缺陷才显现。

## What Changes（修复内容）

仅修改前端单文件 `web/src/admin/pages/index.tsx`（3 个组件同处一文件）：

1. `AdminAccountsPage`：将 `useApi<Account[]>` 改为 `useApi<PageResult<Account>>`，渲染时使用 `data.content.map(...)`。
2. `AdminAuditPage`：同上，将 `useApi<AuditLog[]>` 改为 `useApi<PageResult<AuditLog>>`，渲染时使用 `data.content.map(...)`。
3. `AdminHealthPage`：扩展 `HealthData` 类型以匹配后端真实字段（`status`、`initialized`、`version`、`buildTime`、`buildCommit`、`database`、`backup`、`recoveryDrill`、可选 `rpoWarning`/`rpoWarningMessage`），从这些字段派生一个 `checks` 数组供现有 UI 渲染（保持「整体 status + 检查项列表」的视觉结构不变）。
4. 在文件顶部新增 `PageResult<T>` 通用类型，描述后端统一分页包装。

**不做的事**：
- 不改 `useApi` 公共 hook 行为（其他页面依赖其原始解包语义）
- 不改后端 API 契约、不改 controller、不改 service、不改 DB schema
- 不改业务逻辑

## Capabilities（能力规格影响）

无新增、无修改、无删除。本次仅修复前端 UI 渲染层与后端响应体形状的内部一致性，不影响 `admin-access-control` capability 的验收场景（认证、授权、审计记录仍由 fix-admin-401 / fix-admin-pages-500 保证）。不创建 delta spec。

## Impact（影响范围）

- **改动文件数**：1（`web/src/admin/pages/index.tsx`）
- **改动性质**：纯前端 TypeScript 类型对齐 + 解包逻辑 + health 页派生数据构造
- **回归风险**：极低。仅 admin 后台 3 个页面受影响，其他页面（child / parent / overview 等）的 `useApi` 用法不变
- **下游影响**：无。前端单文件改动，不涉及任何外部契约
- **可验证性**：前端 `tsc --noEmit` 类型检查通过 + 浏览器端访问 3 个页面不再抛 `data.map is not a function`、可正常渲染数据
