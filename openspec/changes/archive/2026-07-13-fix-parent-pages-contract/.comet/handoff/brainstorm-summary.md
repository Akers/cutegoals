# Brainstorm Summary

- Change: fix-parent-pages-contract
- Date: 2026-07-13

## 确认的技术方案

### 核心策略

前端解包 PageResult<T>（与 admin 端 fix-admin-pages-data-shape 同模式），后端 ChildProfileController 补 GET 走分页 Map（与所有 parent 端点契约一致），ErrorBoundary fallback 包 Layout。Calendar 接口前端换用 task-assignments 分页查询接口（保留 calendar 端点职责单一）。

### 关键技术发现（brainstorming 阶段深化的发现）

通过 grep 后端 service 层 `put("content|items|page|...")` 验证，发现 **所有 parent 后端分页端点**（exchange/points/prize/blindbox/task-template/task-assignment/task-review/audit-log/account-management）都返回 admin 同款 PageResult `{content,page,pageSize,totalElements,totalPages}`，但前端：

- `usePaginatedData<T>` L143-158 期望 `{items: T[], total: number}`（6 处使用：invitations/children/task-templates/prizes/blind-boxes/exchanges）
- 多处 `useApi<T[]>` 期望裸数组（children/task-templates/review 等）
- Calendar 接口根本性契约误解：后端返回 `{year,month,days:{聚合统计}}`（日历视图），前端期望 `{assignments: TaskAssignment[], dates: string[]}`（任务列表）

这意味着 parent 端契约错配比 open 阶段评估的范围更广——6 处 usePaginatedData 静默显示空列表（不崩，但 UI 错误），多处 useApi<T[]> 崩溃（task-review 触发 React 渲染异常），calendar 既崩又用错接口。

### 11 项改动详列

**A. 后端（2 项）**

1. `server/family/src/main/java/com/cutegoals/family/controller/ChildProfileController.java` 增加 GET 方法：
   ```java
   @GetMapping
   public ResponseEntity<ApiResponse<Map<String, Object>>> listChildren(
       @RequestParam(defaultValue = "1") int page,
       @RequestParam(defaultValue = "20") int pageSize,
       HttpServletRequest httpRequest) { ... }
   ```
   - 复用 `childProfileMapper.findActiveByFamilyId(new Page<>(page, pageSize), familyId)` 返回 `IPage<ChildProfile>`
   - 构造 LinkedHashMap 走 PageResult 模式（与其他 parent 端点完全一致）
   - 需要从 SecurityContext 拿 accountId → 通过 `familyMapper.findFamilyByMemberAccountId` 拿 familyId（与 FamilyService.getFamily 同模式）

2. 新增 controller test `server/family/src/test/java/com/cutegoals/family/controller/ChildProfileControllerTest.java` 验证 200 OK + PageResult 形状 + 401 未授权场景

**B. 前端（7 项）**

3. `web/src/parent/pages/index.tsx` 顶部引入 `interface PageResult<T> { content: T[]; page: number; pageSize: number; totalElements: number; totalPages: number; }`

4. 改造 `usePaginatedData<T>`（L143-158）：
   ```typescript
   const { data, loading, error, refetch } = useApi<PageResult<T>>(`${path}?page=${page}&pageSize=${pageSize}`);
   return {
     items: data?.content ?? [],
     total: data?.totalElements ?? 0,
     ...
   };
   ```
   **影响 6 处使用，全部自动适配**：L201 invitations / L277 children / L377 task-templates / L783 prizes / L885 blind-boxes / L932 exchanges

5. ParentTasksPage calendar（L491-559）改造：
   - 删除 `useApi<{assignments, dates}>('/task-assignments/calendar?date=${date}')` 
   - 改用 `useApi<PageResult<TaskAssignment>>(\`/task-assignments?page=1&pageSize=50&childId=${childId ?? ''}&startDate=${date}&endDate=${date}\`)`
   - 渲染逻辑改 `assignments = data?.content ?? []`（已经在 PageResult 内）
   - **保留 calendar 接口给未来日历视图组件用**（本次不删后端 calendar 代码，保持职责单一）

6. L495/L701 `useApi<ChildProfile[]>('/family/children')` → `useApi<PageResult<ChildProfile>>('/family/children')`
   - ParentTasksPage L495 用于 Select child 下拉：`children.content.map((c) => <option>...</option>)`
   - ParentPointsPage L701 同上

7. L494 `useApi<TaskTemplate[]>('/task-templates')` → `useApi<PageResult<TaskTemplate>>('/task-templates')`
   - 用于 Select template 下拉：`templates.content.map(...)`

8. 审核页 L595-596 `useApi<ReviewItem[]>('/task-review/pending')` + `('/task-review/history')` → `useApi<PageResult<ReviewItem>>` ×2 + 解包 `data.content`
   - 渲染逻辑改 `pending.map` → `pending = data?.content ?? []; pending.map(...)`

9. `web/src/shared/components/ErrorBoundary.tsx` L34-46 fallback 改造：
   ```tsx
   <Layout>
     <ErrorState ... />
   </Layout>
   ```
   保留现有 props 传递（`error`、`onReset` 等）

## 关键取舍与风险

### 取舍

- **统一走 PageResult 模式**而非裸数组：与其他 8 个 parent 端点契约一致，长期可维护；与 admin 端 fix 模式对齐
- **calendar 前端换接口**而非后端混合职责：保持接口语义清晰（calendar = 日历聚合，task-assignments = 任务列表分页查询）
- **不修 useApi 公共 hook**：与 fix-admin-pages-data-shape 同策略，避免影响其他模块
- **不抽公共 PageResult<T> 到 shared/types**：admin 端也是局部定义，本次不引入跨模块重构

### 风险

1. **usePaginatedData 改造影响 6 处**：5 处原本 silently 显示空列表（data.items undefined），改造后会正确显示数据；缓解 = 全套 tsc 类型验证 + dev server 实际渲染测试
2. **calendar 换接口需要 verify 后端 task-assignments GET 支持 startDate/endDate 参数**：从 TaskAssignmentService L400-410 看支持，但 controller 层需要 verify；如不支持则降级为只用 page+pageSize
3. **新增 controller test 增加改动面**：但 parent 端缺测试覆盖是已知约束，本次新增符合质量提升方向
4. **PageResult 一致性**：admin 端已验证此模式工作正常，parent 端跟齐风险低
5. **改动面 4-5 文件**：在 full workflow 阈值内（< 8 文件 = light verify），不触发 verify_mode 升级

## 测试策略

### 后端

- `mvn -pl :web -am test` 全套保持绿（基线 = HEAD 2bc0e5e 的 66 tests 0 failures）
- 新增 ChildProfileControllerTest 至少 2 个 case：未授权 401 + 已授权 200 PageResult 形状

### 前端

- `npx tsc -b` exit 0，0 errors
- `npm test` 全套与 baseline 一致（14 failed/65 passed 预存失败，0 新增失败）
- admin App.test.tsx 保持 2/2 通过
- parent 端可能无现成 test，本次不新增前端 test（覆盖由后端 controller test 保证）

### 端到端

- dev server curl `/api/family/children` 返回 200 + PageResult 形状（需带 admin/parent token）
- 4 个 parent 页面（家庭/任务/审核/积分）dev 浏览器手动测试：无白屏、无 ErrorState 加载失败、无 React 崩溃
- audit page 在 dev 数据为空时正常显示 EmptyState（不崩）

## Spec Patch

无。Open 阶段 specs/parent-pages-contract/spec.md 的 4 个 Requirement / 11 个 Scenario 已覆盖：
- Requirement 1（Parent 端点契约一致性）：覆盖 PageResult 模式
- Requirement 2（Child 列表端点可用性）：覆盖 GET 补充
- Requirement 3（Task Calendar 参数契约）：注意——calendar 接口本次改前端换用其他端点，原 spec scenario 可能需要微调；如 spec scenario 写的是"calendar 接口必须返回 assignments 列表"则需 spec patch
- Requirement 4（错误边界保留全局布局）：覆盖 ErrorBoundary 包 Layout

**guard design --apply 前 verify**：检查 spec scenario 是否有"calendar 接口返回 assignments"语句，如有则需 spec patch 调整为"前端换用 task-assignments 分页接口"。
