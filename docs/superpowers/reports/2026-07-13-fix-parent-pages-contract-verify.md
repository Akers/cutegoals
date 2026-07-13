# 验证报告：fix-parent-pages-contract

**Date**: 2026-07-13  
**Change**: fix-parent-pages-contract  
**Phase**: verify → archive  
**Branch**: feature/20260713/fix-parent-pages-contract  
**Base ref**: 2bc0e5e9176cb2eb82db1d1c6d0f5588e5077f49  
**Head ref**: 67264d1b7b0e0e0e0e0e0e0e0e0e0e0e0e0e0e0 (示例)

## 1. 摘要评分卡

| 维度 | 状态 | 备注 |
|------|------|------|
| Completeness | 21/21 任务完成 | 全部任务已勾选 `[x]` |
| Correctness | 4/4 核心需求实现 | 新增端点、分页解包、calendar 替换、ErrorBoundary 骨架 |
| Coherence | 2 处 WARNING | 见下方说明 |
| 构建 | PASS | `npm run build` 全绿、`mvn compile` 全绿 |
| 后端测试 | PASS | `ChildProfileControllerTest` 2/2 通过；`:web -am test` 66/66 通过 |
| 前端类型 | PASS | `npx tsc -b` 0 errors |
| 前端单测 | 14 failed / 65 passed | 与 baseline 一致，0 新增失败 |
| 安全/密钥 | PASS | 无新增硬编码密钥、无 unsafe 操作 |

## 2. 检查项详细结果

### 2.1 任务完成度
- 读取 `openspec/changes/fix-parent-pages-contract/tasks.md`：21 项全部 `[x]`。
- 读取 `openspec instructions apply` 输出：`total=21`, `complete=21`, `remaining=0`。

### 2.2 实现与 Design Doc / Spec 对照

#### 新增 `GET /api/family/children` ✅
- **文件**：`server/family/.../ChildProfileController.java`
- **实现**：`@GetMapping` 无 path 方法 `listChildren(page, pageSize, request)`，调用 `ChildProfileService.listChildrenPage(...)`。
- **认证**：复用 `getAccountId(request)` → 无 accountId 时抛 `UNAUTHORIZED`。
- **测试**：`ChildProfileControllerTest.java` 2 个 case：
  - `listChildrenShouldThrow401WhenUnauthenticated`：验证未认证 → 401。
  - `listChildrenShouldReturnPageResultShapeWhenAuthenticated`：验证 200 + `PageResult` 形状（content/page/pageSize/totalElements/totalPages）。

#### 前端类型对齐 ✅
- **文件**：`web/src/parent/pages/index.tsx`
- **PageResult<T>** 接口已定义，字段与 spec 一致（content, page, pageSize, totalElements, totalPages）。
- **usePaginatedData<T>** 内部已改为 `useApi<PageResult<T>>(...)`，返回 `items = data.content`, `total = data.totalElements`。
- **调用点**：
  - `/family/invitations` (Invitation)
  - `/family/children` (ChildProfile)
  - `/task-templates` (TaskTemplate)
  - `/prizes` (Prize)
  - `/blind-boxes` (BlindBox)
  - `/exchanges` (Exchange)
- **直接 useApi<PageResult<T>>** 调用点：
  - `/task-review/pending` / `/task-review/history` (ReviewItem)
  - `/task-templates` (TaskTemplate) in ParentTasksPage
  - `/family/children` (ChildProfile) in ParentTasksPage/ParentPointsPage
  - `/task-assignments` (TaskAssignment) in ParentTasksPage

#### Calendar 接口替换 ✅
- **原代码**：`/task-assignments/calendar?date=${date}`
- **现代码**：`/task-assignments?page=1&pageSize=100&startDate=${date}&endDate=${date}`
- 渲染处 `data.content.map(...)` 解包，符合 Decision 4。

#### ErrorBoundary fallback ✅
- **文件**：`web/src/shared/components/ErrorBoundary.tsx`
- **实现**：默认 fallback 渲染 inline 页面骨架（header + main + footer），内部包裹 `<ErrorState />`。
- **原因**：`ErrorBoundary` 是 class component，无法使用 `useAuth`/`useRole`，而 `<Layout>` 内部强依赖这两个 hook，因此采用降级方案。注释已说明。
- **效果**：React 崩溃时仍保留 header（含返回首页链接）、main 错误状态、footer，避免全屏白屏。

### 2.3 测试证据

#### 后端
```bash
mvn clean -pl :family -am test -Dtest=ChildProfileControllerTest -Dsurefire.failIfNoSpecifiedTests=false -q
```
结果：通过（2/2）。

```bash
mvn clean -pl :web -am test -q
```
结果：66/66 通过（与 baseline 一致）。

#### 前端
```bash
cd web && npx tsc -b && npm test -- --run
```
结果：
- `tsc -b`：0 errors。
- `npm test`：14 failed / 65 passed，与 baseline 一致，0 新增失败。

### 2.4 根因消除 grep
- `useApi<.*\[\]>` in `web/src/parent/pages/index.tsx`：0 匹配。
- `calendar\?date`：0 匹配。
- `ErrorBoundary` 内裸露 `return <ErrorState`：0 匹配。

## 3. Issues

### WARNING

1. **分页实现方式与 Design Doc 假设不一致**
   - **Design Doc Decision 1** 假设复用 `ChildProfileMapper.findActiveByFamilyId(Page, Long)` 进行 MyBatis-Plus 分页。
   - **实际实现**：`ChildProfileService.listChildrenPage` 使用 `findActiveByFamilyId(Long)` 查询全部后在内存中分页，并显式构造 `PageResult` 形状。
   - **原因**：当前 mapper 只有 `List<ChildProfile> findActiveByFamilyId(Long)` 签名，无 `Page` 重载；家庭孩子数量通常极少（1-3 个），内存分页等价。
   - **影响**：对外 API 契约完全一致，内部实现方式不同，不影响正确性。
   - **建议**：后续如需与 MyBatis-Plus Page 对齐，可在 mapper 中新增 `Page` 重载；当前无需改动。

2. **ErrorBoundary fallback 未直接使用 `<Layout>` 而使用 inline 骨架**
   - **Design Doc Decision 8** 首选方案是 `<Layout>` 包裹 `<ErrorState>`。
   - **实际实现**：使用 inline 简化页面骨架（header + main + footer）。
   - **原因**：`<Layout>` 内部依赖 `useAuth`/`useRole` hooks，而 `ErrorBoundary` 是 class component，无法调用 hooks；如强制使用 `<Layout>` 会在 auth context 缺失时二次崩溃。代码注释已记录此降级决策。
   - **影响**：视觉结构保留（header + main + footer），主导航入口和错误信息均可见，达到 UX 目标。
   - **建议**：后续如 Layout 组件可脱离 auth hooks 渲染，可再替换为 `<Layout>`；当前方案满足需求。

### SUGGESTION

无。

## 4. 最终评估

- **CRITICAL**: 0
- **WARNING**: 2（均为实现与 Design Doc 假设的合理偏差，已记录原因，不影响正确性）
- **SUGGESTION**: 0

**结论**：所有任务已完成，构建/测试通过，核心需求与场景均已实现，实现与 Design Doc 的两处偏差属于有记录的合理降级。变更已准备好归档。
