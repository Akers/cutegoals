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
