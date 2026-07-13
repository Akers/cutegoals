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

### 决策 3：任务页 calendar 参数从 `date` 改为 `year + month`

**选择**：前端 `useApi<{ assignments: ...; dates: ... }>(\`/task-assignments/calendar?date=${date}\`)` 改为 `useApi<...>(\`/task-assignments/calendar?year=${year}&month=${month}\`)`，其中 `year` 与 `month` 从 `new Date()` 派生。

**备选**：
- A. 前端适配后端契约（**采用**）
- B. 后端兼容 `date` 参数：违反单一契约原则，增加后端解析复杂度

**采用 A**：后端契约已稳定，前端适配成本低。

### 决策 4：`ErrorBoundary` 默认 fallback 用 `<Layout>` 包裹

**选择**：`web/src/shared/components/ErrorBoundary.tsx` L34-46 默认 fallback `<ErrorState ... />` 改为 `<Layout><ErrorState ... /></Layout>`。

**备选**：
- A. 在 ErrorBoundary fallback 包 Layout（**采用**）
- B. 把 ErrorBoundary 包在 Layout 内部（不可行，因为 ErrorBoundary 需要捕获 Layout 子树的渲染错误，必须在 Layout 之上或同级）
- C. 在 ErrorBoundary 接收 fallback prop 让调用方决定：增加 parent/app 入口复杂度

**采用 A**：最小改动，全局生效，与 fix-admin-pages-missing-layout 的修复哲学一致（保留菜单栏是核心 UX 不变量）。

### 决策 5：不动 family 页面 members 字段 nickname/phone 错配

**选择**：family 页面 `interface FamilyMember {id, nickname, role, phone?}` 与后端实际返回的 `{id, accountId, role, status}` 错配，导致 nickname 显示空白。本次不修。

**理由**：
- 不会触发 React 崩溃（nickname 是 undefined 时 React 渲染空白文本）
- 不是用户报告的 bug（用户报告的是"加载失败"和白屏，对应缺陷 #1-#5）
- 修复需要后端 join account 表返回 phone/nickname 或前端调用单独接口，属于独立增强而非契约对齐
- 留待后续独立 change 处理 family 页面 UX 增强

### 决策 6：不修 `GlobalExceptionHandler` `AUDIT_IMMUTABLE` 错误码误用

**选择**：`GlobalExceptionHandler` L62-71 将 `HttpRequestMethodNotSupportedException` 错误映射为 `ErrorCode.AUDIT_IMMUTABLE`（语义错配），但前端 `errors.ts` 不依赖此错误码决定渲染路径，仅显示通用错误消息。本次不修。

**理由**：修了之后会改变 405 响应体错误码，可能影响其他模块（虽概率低），不属于 parent 端契约对齐的核心范围。留待独立 change 处理 error code 体系。

## Risks / Trade-offs

### 风险 1：后端新增 `GET /api/family/children` 可能影响现有 POST/PUT/DELETE 端点的 MyBatis-Plus 路径匹配

- **风险**：MyBatis-Plus 或 Spring MVC 在同一 `@RequestMapping("/api/family/children")` 下同时存在无 path 的 GET 与无 path 的 POST 时可能路由冲突。
- **缓解**：Spring MVC 按方法区分路由，`@GetMapping` 与 `@PostMapping` 在同 path 下是标准用法，AccountManagementController 等已有先例。
- **验证**：build 阶段加 controller 集成测试（若已有 test 基础设施）或 dev server curl 验证。

### 风险 2：前端 PageResult 类型可能与某些已有 useApi 调用冲突

- **风险**：parent 端可能有其他 useApi 调用本来期望分页 Map 但被错误标为数组，本次修复需确保所有相关调用点都同步修改。
- **缓解**：build 阶段 grep 所有 `useApi<...Array[]>` 与对应后端返回类型，逐一对照。
- **验证**：tsc 类型检查 + npm test baseline 对照。

### 风险 3：ErrorBoundary fallback 包 Layout 可能在某些场景重复渲染 header

- **风险**：如果 ErrorBoundary 被包在 Layout 内部，包 Layout 后会出现双 header。
- **缓解**：先确认 ErrorBoundary 在组件树中的位置（应在 Router 顶层），build 阶段验证。
- **验证**：dev server 实际触发 React 错误场景（如禁用 API 模拟 500）查看渲染。

### 风险 4：calendar 参数修复后 UI 行为可能变化

- **风险**：之前发 `date` 时后端 400，前端 ErrorState；改 `year+month` 后后端返回数据，前端 UI 实际渲染日历视图可能与设计不符。
- **缓解**：calendar 接口前端期望 `{ assignments: TaskAssignment[]; dates: string[] }`，后端实际返回结构需在 build 阶段从 service 代码确认（可能与期望不符）。
- **验证**：build 阶段先 curl calendar 端点确认实际返回字段名，再决定是否调整前端解构。

### 风险 5：后端 controller 测试覆盖

- **风险**：ChildProfileController 现有测试（如果有）可能没有 GET 用例，新增 GET 后覆盖率下降。
- **缓解**：build 阶段先 grep 现有测试，决定是否补充。
- **验证**：mvn test 全绿（与 fix-admin-pages-500 baseline 对照）。
