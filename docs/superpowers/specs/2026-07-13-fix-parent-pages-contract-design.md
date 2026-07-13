---
comet_change: fix-parent-pages-contract
role: technical-design
canonical_spec: openspec
archived-with: 2026-07-13-fix-parent-pages-contract
status: final
---

# Design Doc: fix-parent-pages-contract

**Date**: 2026-07-13  
**Change**: fix-parent-pages-contract  
**Workflow**: Comet Full  
**Phase**: Design (Superpowers Design Doc，对应 OpenSpec 阶段 phase=design)

## 1. Context

### 1.1 用户报告的现象

`http://localhost:5173/parent` 家庭、任务、积分页面报错「加载失败 RESOURCE_NOT_FOUND」；审核页面全屏显示无内容且未正常显示菜单栏。

### 1.2 探索阶段发现（5 个独立缺陷叠加）

通过 dev server 日志、curl 测试、codegraph 探索，确认 parent 端有 **5 个独立缺陷** 叠加：

1. **ChildProfileController 无 GET 端点**：`server/family/.../ChildProfileController.java` L23 `@RequestMapping("/api/family/children")`，只有 POST (L34) / PUT `/{id}` (L56) / DELETE `/{id}` (L75)，**完全没有 @GetMapping**。curl `/api/family/children` 返回 HTTP 405。影响前端 3 处 GET 调用。

2. **任务页 calendar 参数错配**：后端 `TaskAssignmentController.getCalendar` L145-150 要求 `year+month` int 必需参数；前端 L493 发 `?date=2026-07-13` 字符串。后端抛 MissingServletRequestParameterException → 400。

3. **审核页形状错配**：后端 `TaskReviewService.queryPendingReviews` L465-505 返回分页 Map `{content,page,pageSize,totalElements,totalPages}`；前端 L595-596 `useApi<ReviewItem[]>` 期望数组。L626 `pending.map(...)` 触发 `pending.map is not a function` → React 崩溃 → ErrorBoundary fallback 纯 `<ErrorState>` 渲染（无 Layout 包裹），用户看到「全屏无菜单栏」。

4. **ErrorBoundary fallback 无 Layout**：`web/src/shared/components/ErrorBoundary.tsx` L34-46 默认 fallback 是纯 `<ErrorState>`，React 崩后丢失外层 Layout（header 主导航 + footer）。

5. **task-templates 形状错配**：前端 L494 `useApi<TaskTemplate[]>` 期望数组，后端 `TaskTemplateController.queryTemplates` 返回分页 Map。与缺陷 #3 同模式。

### 1.3 Brainstorming 阶段深化的关键发现

通过 grep 后端 service 层 `put("content|items|page|...")` 验证，发现 **所有 parent 后端分页端点**（exchange/points/prize/blindbox/task-template/task-assignment/task-review/audit-log/account-management）都返回 admin 同款 PageResult `{content,page,pageSize,totalElements,totalPages}`，但前端：

- `usePaginatedData<T>` L143-158 期望 `{items: T[], total: number}`（6 处使用：invitations/children/task-templates/prizes/blind-boxes/exchanges）
- 多处 `useApi<T[]>` 期望裸数组（children/task-templates/review）
- Calendar 接口根本性契约误解：后端返回 `{year,month,days:{聚合统计}}`（日历视图），前端期望 `{assignments, dates}`（任务列表）

**这意味着 parent 端契约错配比 open 阶段评估的范围更广**：

- 6 处 usePaginatedData 静默显示空列表（data.items 为 undefined，items ?? [] = []，不崩但 UI 错误）
- 多处 useApi<T[]> 在数据存在时崩（task-review 已确认崩；children/task-templates 因 405 或返回 Map 触发 ErrorState）
- Calendar 既崩（参数错配）又用错接口（语义混淆）

### 1.4 上游 hotfix 链参考

5 个连续 admin hotfix 已归档：fix-admin-401 → fix-admin-pages-500 → fix-admin-pages-data-shape → fix-admin-pages-missing-layout。本次 fix-parent-pages-contract 是 fix-admin-pages-data-shape 模式（PageResult<T> 解包）在 parent 端的系统性应用。

## 2. Goals

### 2.1 必须达成

- 4 个 parent 页面（家庭、任务、审核、积分）在 dev 环境正常渲染，无 ErrorState 加载失败、无 React 崩溃
- ChildProfileController 提供 GET 端点返回 PageResult 形状
- usePaginatedData 与 useApi 与所有 parent 后端分页端点契约一致
- ErrorBoundary fallback 在 React 崩溃时保留 Layout（header 菜单 + footer）
- 后端 mvn test 全绿（基线 = HEAD 2bc0e5e 的 66 tests）
- 前端 tsc -b 0 errors + npm test 0 新增失败

### 2.2 非目标（显式排除）

- 不修复 family members nickname 缺失（FamilyService.getFamily 返回的 members 无 nickname/phone 字段，前端 L189 显示空白但不崩；属于 UI 数据完整性问题，独立于本次修复）
- 不修复 GlobalExceptionHandler AUDIT_IMMUTABLE 误用（L62-71 将 HttpRequestMethodNotSupportedException 错误码映射为 AUDIT_IMMUTABLE 而非 METHOD_NOT_ALLOWED；独立于本次修复）
- 不重构 useApi 公共 hook（与 fix-admin-pages-data-shape 同策略）
- 不抽公共 PageResult<T> 到 shared/types（admin 端也是局部定义）
- 不增加 parent 端 controller 全面覆盖测试（仅 ChildProfileController 新增）
- 不删除后端 calendar 接口代码（保留给未来日历视图组件，本次仅前端换用其他端点）

## 3. Design Decisions

### Decision 1: 后端 ChildProfileController 补 GET 走 PageResult 模式

**选择**：新增 `@GetMapping` 方法，复用 `childProfileMapper.findActiveByFamilyId(new Page<>(page, pageSize), familyId)`，返回 LinkedHashMap 走 PageResult 形状。

**理由**：

- 与所有 parent 端点契约一致（8 个端点都返回 PageResult 形状）
- 与 admin 端 fix-admin-pages-data-shape 同模式（已验证）
- 复用现有 mapper 方法，无新业务逻辑
- children 虽然通常是有限集合（家庭 1-3 个），但走分页接口与系统一致，避免接口分裂

**备选（已拒绝）**：

- 返回 `List<ChildProfile>` 裸数组：与 parent 端点契约不一致；前端 useApi 需要不同处理
- 不补 GET，前端用 `/api/family` 的 children 字段：会引入多次请求，且 `/api/family` 的 children 字段形状与 ChildProfile 不完全一致

### Decision 2: 前端引入 PageResult<T> 类型

**选择**：在 `web/src/parent/pages/index.tsx` 顶部引入：

```typescript
interface PageResult<T> {
  content: T[];
  page: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
}
```

**理由**：

- 与 admin 端 fix-admin-pages-data-shape 同模式（已验证）
- 不抽到 shared/types 避免跨模块重构（YAGNI）
- 后续如需统一可一次性提取

### Decision 3: 改造 usePaginatedData<T> 期望 PageResult

**选择**：将 L143-158 usePaginatedData 改造：

```typescript
function usePaginatedData<T>(path: string) {
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const { data, loading, error, refetch } = useApi<PageResult<T>>(`${path}?page=${page}&pageSize=${pageSize}`);
  return {
    items: data?.content ?? [],
    total: data?.totalElements ?? 0,
    page,
    pageSize,
    setPage,
    setPageSize,
    loading,
    error,
    refetch,
  };
}
```

**影响 6 处使用，全部自动适配**：

- L201 `usePaginatedData<Invitation>('/family/invitations')`
- L277 `usePaginatedData<ChildProfile>('/family/children')`
- L377 `usePaginatedData<TaskTemplate>('/task-templates')`
- L783 `usePaginatedData<Prize>('/prizes')`
- L885 `usePaginatedData<BlindBox>('/blind-boxes')`
- L932 `usePaginatedData<Exchange>('/exchanges')`

**理由**：

- 单点修改覆盖 6 处使用，降低改动面
- 接口对外签名不变（items/total/...），调用方代码无需修改
- 与 admin 端 useApi 解包模式同源

### Decision 4: Calendar 接口前端换用 task-assignments 分页查询

**选择**：删除 L493 `useApi<{assignments, dates}>('/task-assignments/calendar?date=${date}')`，改用：

```typescript
const { data, loading, error, refetch } = useApi<PageResult<TaskAssignment>>(
  `/task-assignments?page=1&pageSize=50&childId=${childId ?? ''}&startDate=${date}&endDate=${date}`
);
```

**理由**：

- Calendar 接口语义是「日历聚合视图」（每天任务统计），不是「按日期查任务列表」
- TaskAssignmentController 已有 `selectPage` 方法（TaskAssignmentService L376-433）支持 startDate/endDate 过滤
- 保留 calendar 接口给未来真正的日历视图组件用，避免职责混合
- 前端组件名是 ParentTasksPage（任务分配页），不是日历视图，本应使用列表查询

**备选（已拒绝）**：

- 后端 calendar 接口同时返回 assignments 列表：职责混合，违反单一职责
- 前端直接适配 days 聚合数据：组件语义错（要列表不是日历）
- 前端删除 calendar 调用并保留原数据结构：会让 calendar 接口在前端死代码化

**验证点（build 阶段需 verify）**：

- TaskAssignmentController.selectPage 接受的 query 参数名（startDate/endDate vs deadlineStart/deadlineEnd）
- 如参数名不同，build 阶段按实际 controller 签名调整

### Decision 5: 任务页 useApi<ChildProfile[]> → useApi<PageResult<ChildProfile>>

**选择**：L495/L701 改类型 + 解包：

```typescript
const { data: childrenPage } = useApi<PageResult<ChildProfile>>('/family/children');
// ...
{(childrenPage?.content ?? []).map((c) => <option key={c.id} value={c.id}>{c.nickname}</option>)}
```

**理由**：

- 与 Decision 1（后端 GET 返回 PageResult）契约对齐
- children 在 dev 环境数量少，但前端代码仍走标准分页接口（与 usePaginatedData 一致）

### Decision 6: 任务页 useApi<TaskTemplate[]> → useApi<PageResult<TaskTemplate>>

**选择**：L494 改类型 + 解包：

```typescript
const { data: templatesPage } = useApi<PageResult<TaskTemplate>>('/task-templates');
// ...
{(templatesPage?.content ?? []).map((t) => <option key={t.id} value={t.id}>{t.title}</option>)}
```

**理由**：

- 与后端 TaskTemplateController.queryTemplates 返回的 PageResult 形状对齐
- 与 Decision 3 的 usePaginatedData<TaskTemplate> 内部一致

### Decision 7: 审核页 useApi<ReviewItem[]> ×2 → useApi<PageResult<ReviewItem>> ×2

**选择**：L595-596 改类型 + 解包：

```typescript
const { data: pendingData, ... } = useApi<PageResult<ReviewItem>>('/task-review/pending');
const { data: historyData, ... } = useApi<PageResult<ReviewItem>>('/task-review/history');
// ...
const pending = pendingData?.content ?? [];
const history = historyData?.content ?? [];
// pending.map / history.map 渲染逻辑不变
```

**理由**：

- 与后端 TaskReviewService.queryPendingReviews / queryReviewHistory 返回 PageResult 对齐
- 修复 React 崩溃（`pending.map is not a function`）

### Decision 8: ErrorBoundary fallback 包 Layout

**选择**：`web/src/shared/components/ErrorBoundary.tsx` L34-46 默认 fallback 改造：

```tsx
// 旧：
<ErrorState
  title={this.props.title}
  message={...}
  onReset={...}
/>

// 新：
<Layout>
  <ErrorState
    title={this.props.title}
    message={...}
    onReset={...}
  />
</Layout>
```

**理由**：

- React 崩溃时整个组件树被替换，丢失外层 Layout（header 主导航菜单 + footer）
- 用户报告「审核页全屏无菜单栏」根因之一
- Layout 组件是纯展示组件，无 auth gate 副作用，包裹 fallback 安全
- 与 admin pages fix-admin-pages-missing-layout 同模式

**注意**：Layout 内 NavLinks 会根据当前用户角色显示菜单，但 ErrorBoundary fallback 渲染时可能没有完整 auth context；需要 verify Layout 在 auth context 外的渲染行为。如 Layout 强依赖 useAuth，则需要其他方案（如在 ErrorBoundary 内提供 fallback NavLinks）。

**Build 阶段验证**：先尝试直接包 Layout，如 tsc 报错（useAuth 缺 Provider），降级为在 ErrorBoundary 内 inline 渲染简化版 header（保留菜单链接但不依赖 auth context）。

### Decision 9: 不修 GlobalExceptionHandler AUDIT_IMMUTABLE 误用

**理由**：

- 独立于本次修复（5 缺陷不依赖此错误码映射）
- 修复需要单独 hotfix（影响范围广，需要全局错误码审计）
- 本次缺陷 #1 修复后（补 GET），405 场景不再触发，AUDIT_IMMUTABLE 误用变成 silent 缺陷

### Decision 10: 不修 family members nickname 缺失

**理由**：

- 前端 L189 `<div>{member.nickname}</div>` 显示 undefined 不会崩（React 渲染 undefined 为空）
- 后端 FamilyService.getFamily 返回 members 字段没有 nickname 是独立的数据完整性问题
- 修复需要后端 FamilyService.getFamily 调整查询逻辑或前端 interface 调整，超出本次范围

## 4. Architecture / Component Changes

### 4.1 后端

**新增 1 个 GET endpoint**：

- `GET /api/family/children?page=1&pageSize=20` → 返回 `ApiResponse<PageResult<ChildProfile>>`
- 实现：`ChildProfileController.listChildren` 方法
- 复用：`ChildProfileMapper.findActiveByFamilyId(Page, Long)` + `familyMapper.findFamilyByMemberAccountId(Long)` + SecurityContext accountId

**文件**：

- `server/family/src/main/java/com/cutegoals/family/controller/ChildProfileController.java`（+ ~30 行）
- `server/family/src/test/java/com/cutegoals/family/controller/ChildProfileControllerTest.java`（+ ~80 行 新文件）

### 4.2 前端

**主改文件 `web/src/parent/pages/index.tsx`**（hashline 66F9221E78，~1005 行）：

- 顶部新增 `interface PageResult<T>`（+6 行）
- L143-158 usePaginatedData 改造（替换 2 行 useApi 泛型 + items/total 解包）
- L491-559 ParentTasksPage 改造（替换 L493 useApi 调用 + L523 assignments 解构）
- L494 `useApi<TaskTemplate[]>` → `useApi<PageResult<TaskTemplate>>`（+ 1 行解包）
- L495 `useApi<ChildProfile[]>` → `useApi<PageResult<ChildProfile>>`（+ 1 行解包）
- L595-596 `useApi<ReviewItem[]>` ×2 → `useApi<PageResult<ReviewItem>>` ×2（+ 2 行解包）
- L701 `useApi<ChildProfile[]>` → `useApi<PageResult<ChildProfile>>`（+ 1 行解包）

**改动文件 `web/src/shared/components/ErrorBoundary.tsx`**（hashline 184691912B）：

- L34-46 fallback 用 `<Layout>` 包裹（+ 2 行）

**总改动**：3 文件（包括新建测试文件），约 100-150 行净增

### 4.3 数据流

**Family children GET 流**：

```
ParentTasksPage (L495 useApi<PageResult<ChildProfile>>)
  → getClient().get('/api/family/children?page=1&pageSize=20')
  → AuthContext 注入 Cookie token
  → ChildProfileController.listChildren
  → SecurityContext 拿 accountId
  → familyMapper.findFamilyByMemberAccountId(accountId) → familyId
  → childProfileMapper.findActiveByFamilyId(new Page<>(1, 20), familyId)
  → LinkedHashMap 构造 PageResult
  → ApiResponse.success(result)
  → useApi response.data = PageResult
  → childrenPage.content.map(...) 渲染 Select option
```

**Task assignments 分页查询流（替换原 calendar）**：

```
ParentTasksPage (L493 useApi<PageResult<TaskAssignment>>)
  → getClient().get('/api/task-assignments?page=1&pageSize=50&startDate=2026-07-13&endDate=2026-07-13')
  → TaskAssignmentController.listAssignments
  → taskAssignmentService.selectPage(params, familyId, viewerChildId)
  → LinkedHashMap PageResult
  → useApi response.data = PageResult
  → data.content.map(...) 渲染任务卡片
```

**审核页 task-review 流**：

```
ParentReviewPage (L595-596 useApi<PageResult<ReviewItem>> ×2)
  → getClient().get('/api/task-review/pending?page=1&pageSize=20')
  → TaskReviewController.queryPendingReviews
  → taskReviewService.queryPendingReviews
  → LinkedHashMap PageResult
  → useApi response.data = PageResult
  → pendingData.content ?? [] → pending.map(...) 渲染
```

**ErrorBoundary React 崩溃流**：

```
任意组件渲染抛异常
  → ErrorBoundary.componentDidCatch 捕获
  → state.hasError = true
  → render() 返回 fallback
  → <Layout><ErrorState ... /></Layout>
  → Layout 渲染 header（含 NavLinks）+ main + footer
  → 用户看到完整菜单栏 + 错误提示
```

## 5. Risks & Mitigations

### 5.1 Calendar 接口替换的参数不确定性

**风险**：Decision 4 假设 `/api/task-assignments?page=1&pageSize=50&startDate=...&endDate=...` 端点支持 startDate/endDate 参数。如 controller 实际参数名不同（如 deadlineStart/deadlineEnd），build 阶段需要调整。

**缓解**：Build 阶段第一步 verify TaskAssignmentController.listAssignments 的 @RequestParam 签名，按实际签名调整前端 URL；如不支持日期过滤，降级为只用 page+pageSize（拉全部然后用前端 filter）。

### 5.2 Layout 在 ErrorBoundary fallback 中的 auth context 依赖

**风险**：Layout 内的 NavLinks 组件可能调用 useAuth()（L88-120 显示需要 currentUser.role 判断菜单）。ErrorBoundary fallback 渲染时如果整个组件树崩溃，可能 auth context 已不可用，Layout 内 useAuth() 会抛错。

**缓解**：Build 阶段先 verify Layout 是否强依赖 useAuth。如强依赖，则降级方案——在 ErrorBoundary 内 inline 一个简化版 fallback header（只含 logo + 错误提示，不含菜单），但保留主页面骨架感。

### 5.3 usePaginatedData 改造影响 6 处使用

**风险**：改造一处 hook 影响 6 处调用方。如有调用方依赖 hook 内部细节（不太可能，因为接口签名不变），可能引入回归。

**缓解**：tsc -b 验证类型；dev 浏览器手动测试 6 个页面渲染；hook 接口（返回 items/total/page/pageSize/setPage/setPageSize/loading/error/refetch）保持完全不变。

### 5.4 useApi<ChildProfile[]> → useApi<PageResult<ChildProfile>> 解包遗漏

**风险**：前端 L495/L701 改类型后，调用处可能忘记加 `.content` 解包。

**缓解**：tsc -b 强类型检查会捕获（`ChildProfile[]` 与 `PageResult<ChildProfile>` 不兼容，调用 `.map` 时 tsc 报错）；逐个 verify 调用处。

### 5.5 新增 controller test 增加改动面

**风险**：ChildProfileControllerTest 是新建文件，需要正确配置 MockMvc + SecurityContext + 数据 setup。

**缓解**：参考现有 controller test（如 AuthControllerTest）的 MockMvc 配置模式；如配置过于复杂，最小化测试到只验证 GET 端点存在 + 401 未授权场景（不验证 200 数据形状，由 service 层测试覆盖）。

### 5.6 PageResult 一致性

**风险**：所有 parent 端点都返回 PageResult，但前端各 useApi 调用解包是否一致？

**缓解**：Build 阶段对所有 useApi<...[]> 调用做 grep 审计，确保全部改为 PageResult<T> + `.content` 解包模式。

### 5.7 改动面 verify_mode 评估

**风险**：open 阶段 design.md 估计 4-5 文件改动，但 calendar 替换可能引入额外修改（如 task-assignments 端点参数 verify）。

**缓解**：实际改动如 < 8 文件 = light verify；如超过则 scale 自动评估为 full verify（无影响，verify 阶段按实际处理）。

## 6. Testing Strategy

### 6.1 后端单元测试

**基线**：HEAD `2bc0e5e` 的 `mvn -pl :web -am test` = 66 tests, 0 failures（taskreview 23 + prize 27 + exchange 19 + instancemanagement 35 + web integration 67 + AuthController 3 + ...）。

**新增**：`ChildProfileControllerTest`（位于 `server/family/src/test/java/.../controller/`），至少 2 个 case：

1. `listChildren_unauthorized_returns401`：无 token 调用 → 401
2. `listChildren_authorized_returnsPageResult`：mock SecurityContext + familyMapper → 200 + `{content,page,pageSize,totalElements,totalPages}` 形状

如 MockMvc + SecurityContext 配置复杂，最小化到只验证 401（与 fix-admin-401 hotfix 中验证策略一致）。

**回归验证**：`mvn -pl :web -am test` 保持全绿（66 tests + 新增 1-2 个）。

### 6.2 前端类型检查

**命令**：`cd web && npx tsc -b`

**通过标准**：exit 0，0 errors

**重点 verify**：

- PageResult<T> 类型定义无重复（admin 端已定义同名，但跨文件不影响）
- usePaginatedData 改造后类型签名匹配 6 处调用方
- 所有 useApi<T[]> → useApi<PageResult<T>> 改造完成
- ErrorBoundary fallback 包 Layout 后无类型错误

### 6.3 前端单元测试

**命令**：`cd web && npm test`

**通过标准**：与 baseline 一致（14 failed/65 passed 预存失败，0 新增失败）；admin App.test.tsx 保持 2/2 通过。

**预期**：parent 端可能无现成 test，本次不新增前端 test（覆盖由后端 controller test + 类型检查 + 端到端保证）。

### 6.4 端到端验证

**dev server 状态**：当前运行在 8981 端口（HEAD `2bc0e5e` 编译的字节码）。

**步骤**：

1. 重启 dev server 加载新编译的 ChildProfileController GET 方法
2. `curl -i http://localhost:8981/api/family/children` 应返回 401（未授权，证明端点存在）
3. 浏览器登录 parent 账号 → 访问 `/parent`：
   - 家庭页：正常显示家庭成员 + children 列表（PageResult 解包正确）
   - 任务页：正常显示当天任务列表（task-assignments 分页查询成功）
   - 审核页：正常显示 pending + history 列表（不崩，无白屏）
   - 积分页：正常显示 children 下拉 + 积分流水
4. 故意触发 React 崩溃（如 mock 一个组件抛错）：ErrorBoundary fallback 应显示完整 Layout（header 菜单 + footer + 错误提示）

### 6.5 根因消除 grep

**后端**：`grep -rn '@GetMapping' server/family/src/main/java/com/cutegoals/family/controller/ChildProfileController.java` 应有 1 匹配（新加的 listChildren）

**前端**：

- `grep 'useApi<.*\[\]>' web/src/parent/pages/index.tsx` 应 0 匹配（全部改为 PageResult<T>）
- `grep 'useApi<.*\[\]>' web/src/parent/` 应只有 useApi<TaskAssignment[]> 之类（如有遗漏则补）
- `grep '<Layout>' web/src/shared/components/ErrorBoundary.tsx` 应有 1 匹配（fallback 包裹）

## 7. Implementation Notes

### 7.1 Build 顺序

1. 后端：`ChildProfileController.java` 加 GET 方法
2. 后端：`mvn -pl :web -am compile` 验证编译
3. 后端：`ChildProfileControllerTest.java` 新建
4. 后端：`mvn -pl :web -am test` 验证全绿
5. 前端：`web/src/parent/pages/index.tsx` 顶部加 PageResult<T>
6. 前端：usePaginatedData 改造
7. 前端：ParentTasksPage L493 calendar 改造（先 verify TaskAssignmentController 参数）
8. 前端：L494/L495/L595/L596/L701 useApi<...[]> → useApi<PageResult<...>> + 解包
9. 前端：`ErrorBoundary.tsx` fallback 包 Layout
10. 前端：`npx tsc -b` 验证 0 errors
11. 前端：`npm test` 验证与 baseline 一致
12. 根因消除 grep
13. 端到端 dev server 验证

### 7.2 关键 build 阶段 verify 点

- Decision 4 calendar 参数名（TaskAssignmentController.listAssignments 实际签名）
- Decision 8 Layout 是否强依赖 useAuth（ErrorBoundary fallback 风险）
- 所有 useApi<...[]> 是否全部改造完成（PageResult 一致性）

### 7.3 Commit 策略

按 comet hotfix/build 阶段惯例：

- 单 commit 或 2-3 logical commits（后端 / 前端 / ErrorBoundary）
- commit message 格式：`fix(parent): <短描述>`
- 提交到 main 分支（与前 5 个 hotfix 同策略，由 verify 阶段用户分支决策确认）

## 8. Open Questions / Defer Items

### 8.1 Defer 到独立 change

- GlobalExceptionHandler 错误码映射审计（独立 hotfix）
- family nickname/phone 数据完整性（独立 fix）
- parent 端 controller 全面覆盖测试（独立 enhancement）
- useApi 公共 hook PageResult 解包能力（独立 refactor）

### 8.2 Build 阶段 verify（非 defer）

- TaskAssignmentController.listAssignments 参数名（startDate/endDate vs deadlineStart/deadlineEnd）
- Layout 在 ErrorBoundary fallback 中的 auth context 行为
- ChildProfileMapper 是否有 `findActiveByFamilyId(Page, Long)` 重载（如无，需要在 mapper 加方法或使用 PageHelper）

## 9. References

- Open Spec: `openspec/changes/fix-parent-pages-contract/specs/parent-pages-contract/spec.md`
- Open Proposal: `openspec/changes/fix-parent-pages-contract/proposal.md`
- Open Design: `openspec/changes/fix-parent-pages-contract/design.md`
- Tasks: `openspec/changes/fix-parent-pages-contract/tasks.md`
- Handoff: `openspec/changes/fix-parent-pages-contract/.comet/handoff/design-context.md`
- Brainstorm Summary: `openspec/changes/fix-parent-pages-contract/.comet/handoff/brainstorm-summary.md`
- Admin 模式参考：`openspec/changes/archive/2026-07-13-fix-admin-pages-data-shape/`
- Admin Layout fix 参考：`openspec/changes/archive/2026-07-13-fix-admin-pages-missing-layout/`

