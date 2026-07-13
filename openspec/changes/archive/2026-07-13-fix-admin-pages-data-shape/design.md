# Design: fix-admin-pages-data-shape

## Context（背景）

CuteGoals 管理后台三个页面（账号管理 / 审计日志 / 健康面板）的渲染层在 fix-admin-pages-500 修复 HTTP 500 之后暴露出新的运行时错误：`data.map is not a function`。

**根因链**：

1. 后端 admin 接口按统一约定返回 `ApiResponse<T>` 包装，T 在分页场景为 `Map<String, Object>`，字段：`content / page / pageSize / totalElements / totalPages`；在 health 场景为扁平 Map，字段：`status / initialized / version / buildTime / buildCommit / database / backup / recoveryDrill`（可选 `rpoWarning / rpoWarningMessage`）。
2. 前端 `useApi<T>` 简单透传 `response.data`（即 `T`），不做形状转换。
3. 前端三个 admin 页面在 fix-admin-pages-500 之前对响应形状的假设错误：accounts/audit 期望 `T = Account[]` / `AuditLog[]`，health 期望 `T = { status, checks: [...] }`。
4. fix-admin-pages-500 之前 HTTP 500 让请求在 `ErrorState` 阶段停止，未触达 `data.map`；现在 200 响应到达后，形状错配引发运行时 TypeError。

**底层约束**：

- 后端 `AccountManagementService.getAccounts` 与 `AuditLogService.queryAuditLogs` 都用 `LinkedHashMap` 显式构造分页包装，字段名为 `content / page / pageSize / totalElements / totalPages`。这是稳定的内部契约，不应为了前端临时改动。
- 后端 `InstanceHealthService.getAdminHealth` 返回的字段集合是面向「运维健康检查」语义设计（DB、备份、恢复演练、RPO），不是前端原先假设的简单 `checks` 列表。后端字段更详细，前端需要在渲染层做派生映射。
- 公共 hook `useApi<T>` 被多个非 admin 页面（child / parent / overview / shared 等）使用，**修改其解包语义会引入跨页面回归**，明确排除该方案。

## Goals / Non-Goals

**Goals**：

1. 让 admin 三个页面对齐后端真实响应形状，消除 `data.map is not a function` 运行时错误。
2. 保持单文件改动范围（`web/src/admin/pages/index.tsx`），不动后端、不动公共 hook、不动其他前端模块。
3. 保持 health 页面的视觉结构（整体 status Badge + 检查项卡片列表）不变，仅改数据派生来源。

**Non-Goals**：

1. ❌ 不引入分页 UI（页码切换、`totalPages` 显示）。当前页面只渲染 `content` 数组，分页字段在类型层声明但不渲染。后续若需分页 UI 应另起 change。
2. ❌ 不改 `useApi` 公共 hook。
3. ❌ 不改后端 API 契约或字段名。
4. ❌ 不重组 health 页面 UI（保持视觉一致性，仅适配数据来源）。
5. ❌ 不为 admin endpoint 新增 controller 测试（这是已知约束，不属于本 hotfix 范围；上一 hotfix verification_report 已记录）。

## Decisions

### 决策 1：仅修前端，不动后端

**理由**：

- 后端分页包装 `{content, page, pageSize, totalElements, totalPages}` 是 Spring/MyBatis-Plus 生态的常见模式，且 `AccountManagementServiceTest` / `AuditLogServiceTest` 已断言该字段集合（10+ 个测试用例）。改后端会引入测试大改与 API 契约破坏。
- health 后端返回的字段（DB / backup / recoveryDrill / RPO）有运维语义价值，前端简单 `checks` 列表反而是信息丢失。改后端会丢功能。
- 前端是形状错位的「消费方」，让消费方对齐生产方是更小的扰动。

### 决策 2：新增 `PageResult<T>` 类型而不内联

**理由**：

- accounts 与 audit 共享同一分页结构。内联两次会重复且容易漂移。
- 在 `web/src/admin/pages/index.tsx` 文件顶部声明 `interface PageResult<T> { content: T[]; page: number; pageSize: number; totalElements: number; totalPages: number; }` 是最聚焦的做法。
- **不抽到 `web/src/shared/`**：当前仅 admin 两处使用，过早抽象会扩大改动面。后续若有第三个分页消费方再考虑提取。

### 决策 3：health 页采用「派生 checks」而非重组 UI

**理由**：

- 现有 UI 结构（整体 status Badge + 检查项卡片 grid）视觉一致、用户已熟悉。
- 后端字段比原 `checks` 列表更结构化（嵌套对象），但可以扁平化派生为 checks 数组：`[{ name: '数据库', healthy: data.database.status === 'UP', message: data.database.type }, { name: '备份', healthy: data.backup.status === 'UP', message: ... }, { name: '恢复演练', healthy: data.recoveryDrill.status === 'UP', message: ... }]`。
- 这样保持现有 JSX 渲染逻辑不变，只在 `data` 与 `data.checks.map` 之间插入一层派生。

### 决策 4：不改 useApi 公共 hook

**理由**：

- `useApi<T>` 的契约是「透传 `ApiResponse.data` 给类型 T」，对调用方最透明。
- 让 `useApi` 自动检测 `.content` 字段并解包会破坏其他非分页页面（如 overview 调用 `/api/instance/status` 拿 `{ initialized, version }` 对象，没有 `.content`）。
- 新增 `usePaginatedApi<T>` 是更干净的设计，但当前仅 2 处分页消费，过度设计。

## Risks（风险）

| 风险 | 评估 | 缓解 |
|------|------|------|
| health 页 `backup` / `recoveryDrill` 字段子结构未完全确认 | `getBackupStatus()` / `getRecoveryDrillStatus()` 返回具体字段（如 `lastBackupTime`、`lastDrillTime`、`status`）需在 build 阶段读取确认 | build 阶段先 `codegraph_node getBackupStatus` + `getRecoveryDrillStatus` 查源码，按实际字段派生 checks |
| 前端单测基线不明 | 项目 web 端可能没有 admin pages 的单测覆盖 | 在 build 阶段 `npm run typecheck` 或 `tsc --noEmit` 验证类型对齐；运行时通过浏览器端访问验证（fix-admin-pages-500 已建立 dev server 验证通道） |
| 改动后字段名笔误 | 派生逻辑中字段名拼写错误会导致 `undefined` | TypeScript 类型对齐 + 严格 `noUncheckedIndexedAccess` 检查（若启用）+ 浏览器端实际渲染验证 |
| 用户期望分页 UI | 本次只渲染 `content`，不显示总页数 / 页码切换 | 在 proposal Non-Goals 与 design Non-Goals 明确声明；后续可独立 change 添加 |

## Open Questions

无。所有不确定点（health 子字段、前端测试基线）都安排在 build 阶段第一步确认。
