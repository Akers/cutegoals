# Comet Design Handoff

- Change: fix-parent-pages-contract
- Phase: design
- Mode: compact
- Context hash: 65547d5e17b4f4b256821142c24eb7c92c2a8727eae4ef493d848be435834f86

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/fix-parent-pages-contract/proposal.md

- Source: openspec/changes/fix-parent-pages-contract/proposal.md
- Lines: 1-58
- SHA256: 6757dbd5e3b40fccd12472244b30115a3d601b97ab735cd5e3ece7ce477a8838

```md
## Why

父账号访问 `/parent` 时家庭、任务、积分页面报错「加载失败 RESOURCE_NOT_FOUND」，审核页全屏白屏且菜单栏消失。经 dev server 日志、curl 测试与 codegraph 探索，确认 parent 端存在 **5 个独立缺陷叠加**：

1. **`ChildProfileController` 完全没有 `@GetMapping`**：所有 GET `/api/family/children` 返回 HTTP 405（family、tasks、points 三个页面的 child 下拉、children 列表都依赖此接口）。
2. **任务页 `task-assignments/calendar` 参数错配**：前端发 `?date=YYYY-MM-DD`，后端要求 `year + month` 两个 int，Spring 抛 `MissingServletRequestParameterException` → 400 BAD_REQUEST。
3. **审核页 `task-review/pending` 与 `task-review/history` 数据形状错配**：后端返回分页 Map `{content, page, pageSize, totalElements, totalPages}`，前端 `useApi<ReviewItem[]>` 期望数组，渲染时 `pending.map is not a function` 触发 React 崩溃。
4. **`ErrorBoundary` fallback 纯 `<ErrorState>` 未包 `<Layout>`**：React 渲染期间抛错时整棵组件树被替换为纯 ErrorState，丢失主导航菜单与 footer（这正是用户报告审核页"全屏显示无内容且未正常显示菜单栏"的直接原因）。
5. **任务页 `task-templates` 形状错配**：后端 `TaskTemplateController.queryTemplates` 返回 `Map<String, Object>`（分页 Map），前端 `useApi<TaskTemplate[]>` 期望数组，与缺陷 #3 同模式。

缺陷 #3 与 #5 与已归档的 `fix-admin-pages-data-shape` 同模式（后端分页 Map vs 前端数组期望），属于 parent 端系统性数据形状契约不一致问题。前 5 个 admin hotfix（认证 / `-parameters` 编译 / admin 数据形状 / admin early-return 包 Layout）已修复 admin 端整条渲染链路，本次必须以 full comet 流程系统修复 parent 端的同源问题。

## What Changes

- 后端 `ChildProfileController` 新增 `@GetMapping` 列出当前家庭的孩子档案（复用现有 `ChildProfileMapper.findActiveByFamilyId` / `findActiveOnlyByFamilyId`）。
- 前端 `web/src/parent/pages/index.tsx` 多处类型对齐与解包：
  - 审核页：`useApi<ReviewItem[]>` → `useApi<PageResult<ReviewItem>>`，渲染时解包 `data.content`，pending 与 history 两个端点都修。
  - 任务页：`useApi<TaskTemplate[]>` → `useApi<PageResult<TaskTemplate>>`，渲染时解包 `data.content`。
  - 任务页 calendar：`?date=${date}` → `?year=${year}&month=${month}`（前端从 `new Date()` 派生）。
- 前端 `web/src/shared/components/ErrorBoundary.tsx` 默认 fallback 用 `<Layout>` 包裹 `<ErrorState>`，保留主导航菜单与 footer。
- 可选：修正 `GlobalExceptionHandler` 将 `HttpRequestMethodNotSupportedException` 从误用的 `AUDIT_IMMUTABLE` 改为合适的 `METHOD_NOT_ALLOWED` 错误码（前端不依赖此错误码，但闭环修正语义错配）。
- 不动数据库 schema、不动 API 公共契约（仅补全缺失端点 + 前端契约对齐）。

## Capabilities

### New Capabilities

- `parent-pages-contract`: Parent 端点契约一致性要求——所有 parent 前端调用的端点形状必须与前端类型声明一致；包含 child 列表端点可用性、task calendar 参数契约、错误边界保留全局布局等可测试验收场景。

### Modified Capabilities

无修改。本次修复全部在已有 capability 范围内或新增 parent-pages-contract capability，不修改既有 admin-access-control capability 的任何需求语义。

## Impact

**受影响代码**：

- `server/family/src/main/java/com/cutegoals/family/controller/ChildProfileController.java`（新增 `@GetMapping`，约 20 行）
- 可选：`server/family/src/main/java/com/cutegoals/family/service/ChildProfileService.java`（若需新增 list helper）或直接复用 mapper
- 可选：`server/web/src/main/java/com/cutegoals/web/handler/GlobalExceptionHandler.java` L62-71（错误码映射修正）
- `web/src/parent/pages/index.tsx`（类型对齐 + 参数对齐 + 解包分页，约 10-20 处）
- `web/src/shared/components/ErrorBoundary.tsx`（fallback 包 Layout）

**API 影响**：

- 新增 `GET /api/family/children`（之前 405，前端期望此端点存在并返回 `ChildProfile[]`）。
- 不改变任何已存在端点的请求/响应契约，仅前端对齐后端实际形状。

**依赖与系统**：

- 无新增依赖、无 schema 变更、无 migration。
- 后端修复依赖现有 `ChildProfileMapper.findActiveByFamilyId` 与 `AuthContext` 中的 familyId 解析逻辑（与现有 POST/PUT/DELETE 端点同路径）。

**测试影响**：

- `web/src/admin/__tests__/App.test.tsx` baseline 应保持通过（不修改 admin 端）。
- parent 端目前缺少端到端测试覆盖，本次修复不引入新测试（保持 hotfix 风格，但属于 full 流程的轻量验证范围）。
- 后端 ChildProfileController 测试若存在需补充 GET 用例（dev 现状待 build 阶段确认）。

```

## openspec/changes/fix-parent-pages-contract/design.md

- Source: openspec/changes/fix-parent-pages-contract/design.md
- Lines: 1-148
- SHA256: e47afc17fdee405e3ddd4ad69063ab46e197bc49e1803fb6061f43dd7001a349

[TRUNCATED]

```md
## Context

CuteGoals 前端 parent 端在 admin 端连续 5 个 hotfix 完成后暴露出系统性数据形状契约不一致问题。前序 hotfix 修复链为：fix-admin-401（SecurityContext 认证）→ fix-admin-pages-500（`-parameters` 编译标志）→ fix-admin-pages-data-shape（admin 端分页/对象响应形状）→ fix-admin-pages-missing-layout（admin early return 包 Layout）。parent 端存在与 admin 端 fix-admin-pages-data-shape 同源的形状错配缺陷，叠加若干 parent 特有缺陷（缺 GET 端点、参数错配、ErrorBoundary 无 Layout）。

**dev server 当前事实**（8981 端口，2026-07-13）：

- `curl -i /api/family/children` → HTTP 405 + `{"code":"AUDIT_IMMUTABLE","message":"HTTP method not supported"}`
- `curl -i /api/family` / `/api/task-review/pending` / `/api/task-assignments/calendar` / `/api/points/ledger/1` → 全部 HTTP 401（端点存在但需认证）
- dev server 日志多次重复 `HttpRequestMethodNotSupportedException: Request method 'GET' is not supported` 与 `MissingServletRequestParameterException`

**后端既有可复用能力**：

- `ChildProfileMapper.findActiveByFamilyId(familyId)` 返回 `List<ChildProfile>`，排除 DELETED 状态
- `ChildProfileMapper.findActiveOnlyByFamilyId(familyId)` 返回 `List<ChildProfile>`，仅 ACTIVE 状态
- `TaskReviewService.queryPendingReviews` / `queryReviewHistory` 返回分页 Map `{content, page, pageSize, totalElements, totalPages}`
- `TaskTemplateService.queryTemplates` 返回分页 Map（同上结构）
- `TaskAssignmentController.getCalendar` 要求 `year + month` 两个 int 参数

**前端既有能力**：

- `web/src/shared/hooks/useApi.ts` 的 `useApi<T>` 透传 `response.data` 给前端类型 `T`，类型不匹配会在渲染期触发 React 异常
- `web/src/parent/pages/index.tsx` L126-141 `PageShell` helper 已统一包裹 `<Layout>`，parent 页面的正常 early return 都已保留菜单栏
- `web/src/shared/components/Layout.tsx` L37-86 提供共享 Layout（header + NavLinks + main + footer）
- `web/src/shared/components/ErrorBoundary.tsx` L34-46 默认 fallback 是纯 `<ErrorState>`，**未包 Layout**，这是审核页白屏"无菜单栏"的直接根因

## Goals / Non-Goals

**Goals:**

- 父账号能正常访问 `/parent` 的家庭、任务、审核、积分四个核心页面，无白屏、无 "加载失败"。
- parent 端前端类型与后端实际响应形状对齐（分页接口统一为 `PageResult<T>` 解包）。
- 任务页日历接口参数对齐后端契约（`year + month` 而非 `date`）。
- 任何 React 渲染崩溃场景下用户仍可见菜单栏与 footer（ErrorBoundary fallback 包 Layout）。
- 复用既有 mapper / hook / Layout，不引入新依赖。

**Non-Goals:**

- 不重构 parent 端 UI 结构（除 ErrorBoundary fallback 必须）。
- 不修复 admin / child 端点（admin 已修，child 不在本批次范围）。
- 不引入新 capability、不改变 API 公共契约、不动数据库 schema。
- 不为 parent 端引入新的端到端测试框架（保持现有测试基线，不扩大测试覆盖范围）。
- 不修复 family members 字段中 nickname/phone 的 UI 显示问题（后端 family members 返回 accountId/role/status，前端 interface 有 nickname/phone 字段会显示空白，但**不崩**，属于预存 UI 缺陷不在本次范围）。
- 不修复 GlobalExceptionHandler `AUDIT_IMMUTABLE` 错误码误用（前端不依赖此错误码，可选修正留待后续）。

## Decisions

### 决策 1：后端 `ChildProfileController` 新增 `@GetMapping`，复用既有 mapper

**选择**：在 `ChildProfileController` 新增 `@GetMapping`（无 path 参数），返回当前家庭的孩子列表。

**实现要点**：
- 从 `httpRequest.getAttribute(AuthConstants.ATTR_ACCOUNT_ID)` 获取 accountId
- 从 `httpRequest.getAttribute(AuthConstants.ATTR_ROLES)` 获取 roles 并校验 PARENT 角色
- 通过与现有 POST 端点相同的方式解析 familyId（复用 controller 已有的 helper 或注入 FamilyService）
- 调用 `childProfileMapper.findActiveByFamilyId(familyId)`（与 FamilyService.getFamily 内部逻辑一致）
- 包装 `ApiResponse.success(data, requestId)` 返回

**备选**：
- A. 直接复用 `findActiveByFamilyId`：返回包含 ACTIVE + DISABLED 状态（仅排除 DELETED）。优点是 family 页面能编辑 DISABLED 孩子；缺点是下拉选孩子时可能列出已禁用的孩子。
- B. 使用 `findActiveOnlyByFamilyId`：只返回 ACTIVE 状态。优点是下拉默认只列可用孩子；缺点是 family 页面无法看到 DISABLED 孩子。
- C. 新增 `listChildren(boolean includeDisabled)` service 方法支持 query 参数。

**采用 A**：与 `FamilyService.getFamily` 行为一致（getFamily 也排除 DELETED 但保留 ACTIVE/DISABLED），保持端点语义统一。下拉选择场景的前端过滤可以在前端做（`disabled` 字段）。

### 决策 2：前端审核页与任务页类型从数组改为 `PageResult<T>` 并解包 `data.content`

**选择**：在 `web/src/parent/pages/index.tsx` 顶部新增 `interface PageResult<T> { content: T[]; page: number; pageSize: number; totalElements: number; totalPages: number; }`（与 fix-admin-pages-data-shape 完全相同的模式），审核页与任务页 4 处 useApi 类型改为 `PageResult<T>`，渲染处解包 `data.content`。

**涉及位置**：
- 审核页 `useApi<ReviewItem[]>('/task-review/pending')` → `useApi<PageResult<ReviewItem>>('/task-review/pending')`，渲染处 `data.content.map(...)`
- 审核页 `useApi<ReviewItem[]>('/task-review/history')` → 同上
- 任务页 `useApi<TaskTemplate[]>('/task-templates')` → `useApi<PageResult<TaskTemplate>>('/task-templates')`，渲染处 `data.content.map(...)`

**备选**：
- A. 前端改类型对齐后端分页 Map（**采用**）
- B. 后端改返回数组：破坏分页契约，影响后续可能的分页 UI 扩展
- C. 后端引入 query 参数 `?flat=true` 切换：增加 API 复杂度

**采用 A**：与 fix-admin-pages-data-shape 同模式，前端适配成本最低，不破坏后端分页能力。


```

Full source: openspec/changes/fix-parent-pages-contract/design.md

## openspec/changes/fix-parent-pages-contract/tasks.md

- Source: openspec/changes/fix-parent-pages-contract/tasks.md
- Lines: 1-37
- SHA256: e63aae7bef73147ecc4ab78ae99b79d604bde50d63226595d4a50b098166d5d8

```md
## 1. 后端：补 ChildProfileController GET 端点

- [ ] 1.1 在 `server/family/src/main/java/com/cutegoals/family/controller/ChildProfileController.java` 新增 `@GetMapping` 方法 `listChildren(HttpServletRequest)`，复用 `ChildProfileMapper.findActiveByFamilyId(familyId)`，包装 `ApiResponse.success(data, requestId)` 返回；参考现有 POST 端点的认证与 familyId 解析逻辑（capability: parent-pages-contract）
- [ ] 1.2 验证后端编译通过：`mvn -pl :family -am compile` exit 0（capability: parent-pages-contract）
- [ ] 1.3 验证后端测试：`mvn -pl :family -am test` 全绿（与 fix-admin-pages-500 baseline 对照）（capability: parent-pages-contract）

## 2. 前端：parent/pages/index.tsx 类型与参数对齐

- [ ] 2.1 在 `web/src/parent/pages/index.tsx` 顶部新增 `interface PageResult<T> { content: T[]; page: number; pageSize: number; totalElements: number; totalPages: number; }`（与 admin/pages/index.tsx 同模式）（capability: parent-pages-contract）
- [ ] 2.2 审核页 `useApi<ReviewItem[]>('/task-review/pending')` → `useApi<PageResult<ReviewItem>>('/task-review/pending')`，渲染处解包 `data.content.map(...)`（capability: parent-pages-contract）
- [ ] 2.3 审核页 `useApi<ReviewItem[]>('/task-review/history')` → 同上类型与解包（capability: parent-pages-contract）
- [ ] 2.4 任务页 `useApi<TaskTemplate[]>('/task-templates')` → `useApi<PageResult<TaskTemplate>>('/task-templates')`，渲染处解包 `data.content.map(...)`（capability: parent-pages-contract）
- [ ] 2.5 任务页 calendar 参数从 `?date=${date}` 改为 `?year=${year}&month=${month}`（从 `new Date()` 派生 year/month；如有 date state 需对应调整）（capability: parent-pages-contract）

## 3. 前端：ErrorBoundary fallback 包 Layout

- [ ] 3.1 在 `web/src/shared/components/ErrorBoundary.tsx` L34-46 默认 fallback 改为 `<Layout><ErrorState ... /></Layout>`（保留原有 props）（capability: parent-pages-contract）
- [ ] 3.2 验证 ErrorBoundary 不在 Layout 内部嵌套（确认 ErrorBoundary 在 Router 顶层，包 Layout 后不会出现双 header）（capability: parent-pages-contract）

## 4. 类型检查与单元测试

- [ ] 4.1 `cd web && npx tsc -b` exit 0，0 errors（capability: parent-pages-contract）
- [ ] 4.2 `cd web && npm test`，与 fix-admin-pages-missing-layout baseline 对照（14 failed / 65 passed），admin 端 `src/admin/__tests__/App.test.tsx` 2/2 保持通过，0 新增失败（capability: parent-pages-contract）

## 5. 端到端与回归验证

- [ ] 5.1 dev server 重启加载新字节码（如有运行中的 JVM）（capability: parent-pages-contract）
- [ ] 5.2 curl 验证 `GET /api/family/children`（带父账号 token）返回 200 + 数组形状；不带 token 返回 401（capability: parent-pages-contract）
- [ ] 5.3 dev server 实际访问 `/parent/family`、`/parent/tasks`、`/parent/review`、`/parent/points` 四个页面，无 "加载失败" 与白屏（capability: parent-pages-contract）
- [ ] 5.4 模拟 React 崩溃场景（如临时让 useApi 抛错），验证 ErrorBoundary fallback 仍显示菜单栏（capability: parent-pages-contract）
- [ ] 5.5 根因消除 grep：旧形状 `useApi<ReviewItem[]>` / `useApi<TaskTemplate[]>` / `?date=${date}` / `<ErrorState ... />`（ErrorBoundary 内）均 0 matches；新形状 `useApi<PageResult<...>>` / `data.content.map` / `?year=` / `<Layout><ErrorState ... /></Layout>` 全部就位（capability: parent-pages-contract）

## 6. 提交与推进

- [ ] 6.1 git commit `fix(parent): align pages data shape and add children GET endpoint + wrap ErrorBoundary in Layout`（capability: parent-pages-contract）
- [ ] 6.2 用户分支处理决策（capability: parent-pages-contract）
- [ ] 6.3 guard build --apply 推进到 verify 阶段（capability: parent-pages-contract）

```

## openspec/changes/fix-parent-pages-contract/specs/parent-pages-contract/spec.md

- Source: openspec/changes/fix-parent-pages-contract/specs/parent-pages-contract/spec.md
- Lines: 1-67
- SHA256: 0165d0da45eea287fa40d20a12d1e9e6c3459aec967aa5e483720981db9df95b

```md
## ADDED Requirements

### Requirement: Parent 端点契约一致性

系统 SHALL 保证 parent 角色（`PARENT`）前端页面调用的所有 `/api/**` 列表端点返回与前端类型声明一致的数据形状。具体地：parent 端列表端点 MUST 统一返回 `PageResult<T>` 形状（`{content: T[], page, pageSize, totalElements, totalPages}`），前端 `useApi<T>` 与 `usePaginatedData<T>` 的类型参数 MUST 与后端实际返回形状一一对应（即前端使用 `useApi<PageResult<T>>` 或 `usePaginatedData<T>` 解包 `content` 字段）。

#### Scenario: 前端期望 PageResult 时后端返回 PageResult

- **WHEN** parent 前端 `useApi<PageResult<ChildProfile>>('/family/children')` 或 `useApi<PageResult<TaskTemplate>>('/task-templates')` 或 `useApi<PageResult<ReviewItem>>('/task-review/pending')` 调用任一 parent 端列表端点
- **THEN** 系统 MUST 返回 HTTP 200，且响应体 `data` 字段为 `{content: T[], page, pageSize, totalElements, totalPages}` 形状

#### Scenario: usePaginatedData 自动解包 PageResult

- **WHEN** parent 前端 `usePaginatedData<T>(path)` hook 调用任一 parent 端列表端点
- **THEN** hook 内部 MUST 将 `useApi<PageResult<T>>` 返回的 `data.content` 解包为 `items` 字段，`data.totalElements` 解包为 `total` 字段，调用方代码无需感知 PageResult 形状

#### Scenario: 渲染期间前端不应触发 React 崩溃

- **WHEN** parent 前端任一页面渲染期间后端返回数据形状与前端类型声明一致
- **THEN** React 渲染 MUST NOT 抛出 `TypeError: data.map is not a function` 或类似形状错配异常

### Requirement: Child 列表端点可用性

系统 SHALL 提供 `GET /api/family/children` 端点供 parent 角色账号查询当前家庭的活跃孩子档案。该端点 MUST 返回 `PageResult<ChildProfile>`（不含 `DELETED` 状态的孩子），且 MUST 复用与现有 POST/PUT/DELETE 端点相同的认证与家庭上下文解析逻辑。返回形状 MUST 与其他 parent 端列表端点（如 `/api/task-templates`、`/api/task-review/pending`）保持一致的 PageResult 契约。

#### Scenario: PARENT 角色账号列出孩子档案

- **WHEN** 一个已认证且持有 `PARENT` 角色的账号发起 `GET /api/family/children?page=1&pageSize=20`
- **THEN** 系统 MUST 返回 HTTP 200，响应体 `data` 为 `PageResult<ChildProfile>`（`{content: ChildProfile[], page, pageSize, totalElements, totalPages}`），`content` 字段仅包含当前家庭中 `status != 'DELETED'` 的孩子档案

#### Scenario: 未认证请求被拒绝

- **WHEN** 一个未携带有效 access token 的请求发起 `GET /api/family/children`
- **THEN** 系统 MUST 返回 HTTP 401，响应体错误码为 `UNAUTHORIZED`

#### Scenario: 非 PARENT 角色被拒绝

- **WHEN** 一个仅持有 `CHILD` 或 `INSTANCE_ADMIN`（非 `PARENT`）角色的账号发起 `GET /api/family/children`
- **THEN** 系统 MUST 返回 HTTP 403，响应体错误码为 `FORBIDDEN`

### Requirement: Task 按日期列表查询契约

parent 前端任务分配页 MUST 使用 `GET /api/task-assignments` 分页查询端点按 `startDate` + `endDate` 过滤当天任务列表，不得调用 `/api/task-assignments/calendar` 端点（该端点为日历聚合视图专用，返回 `{year, month, days: {...聚合统计}}` 形状，非任务列表）。前端调用 `GET /api/task-assignments` MUST 支持 `page`、`pageSize`、`startDate`、`endDate`、`childId`（可选）查询参数。

#### Scenario: 前端按日期范围查询当天任务列表

- **WHEN** parent 前端任务分配页 `useApi<PageResult<TaskAssignment>>('/task-assignments?page=1&pageSize=50&startDate=2026-07-13&endDate=2026-07-13')` 调用 `GET /api/task-assignments`
- **THEN** 系统 MUST 返回 HTTP 200，响应体 `data` 为 `PageResult<TaskAssignment>`（`{content: TaskAssignment[], page, pageSize, totalElements, totalPages}`），`content` 字段仅包含 `deadline` 在 `[startDate 00:00, endDate 23:59:59]` 区间内的任务分配

#### Scenario: 后端 task-assignments 端点支持日期过滤

- **WHEN** 任意请求调用 `GET /api/task-assignments?page=1&pageSize=50&startDate=2026-07-13&endDate=2026-07-13` 且已认证为 PARENT 角色
- **THEN** 系统 MUST 返回 HTTP 200，`PageResult.content` 中的 TaskAssignment 其 `deadline` MUST 落在 `startDate` 至 `endDate` 区间内（端到端验证前端按日期过滤正确）

### Requirement: 错误边界保留全局布局

前端 `ErrorBoundary` 组件的默认 fallback MUST 用 `<Layout>` 包裹错误状态，确保 React 渲染崩溃场景下用户仍可见主导航菜单与 footer。

#### Scenario: React 渲染崩溃时仍显示菜单

- **WHEN** parent 前端任一子组件在渲染期间抛出异常被 ErrorBoundary 捕获
- **THEN** ErrorBoundary 的 fallback MUST 渲染 `<Layout><ErrorState /></Layout>`，保留 header（含主导航菜单）与 footer

#### Scenario: 非渲染崩溃场景不影响正常页面

- **WHEN** 任意 parent 页面正常渲染（无异常）
- **THEN** ErrorBoundary MUST NOT 替换正常渲染输出，页面显示 PageShell 包裹的正常内容

```
