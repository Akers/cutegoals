---
change: fix-parent-pages-contract
design-doc: docs/superpowers/specs/2026-07-13-fix-parent-pages-contract-design.md
base-ref: 2bc0e5e9176cb2eb82db1d1c6d0f5588e5077f49
archived-with: 2026-07-13-fix-parent-pages-contract
---

# fix-parent-pages-contract 实施计划

> **产物语言**: zh-CN
> **关联文档**:
> - 任务边界：`openspec/changes/fix-parent-pages-contract/tasks.md`（6 任务组 / 20 子任务）
> - 技术设计：`docs/superpowers/specs/2026-07-13-fix-parent-pages-contract-design.md`（10 项 Design Decision, 7 项 Risk, 4 个 Build Verify Point, 13 步实施顺序）
> - 验收事实源：`openspec/changes/fix-parent-pages-contract/specs/parent-pages-contract/spec.md`（4 个 Requirement + 11 个 Scenario）
> - 上游模式参考：`openspec/changes/archive/2026-07-13-fix-admin-pages-data-shape/`（admin 端 PageResult 解包模式）
> - 上游 Layout 参考：`openspec/changes/archive/2026-07-13-fix-admin-pages-missing-layout/`（admin 端 Layout 包裹 fallback 模式）
> **实施顺序**：13 步（Design Doc §7.1 定义），按后端 → 前端 → ErrorBoundary → 验证 → 端到端推进
> **测试策略**：Design Doc §6（后端单元测试 → 前端类型检查 → 前端单元测试 → 端到端验证 → 根因消除 grep）

archived-with: 2026-07-13-fix-parent-pages-contract
---

## 计划概览

本计划将 20 项子任务按 Design Doc §7.1 定义的 13 步实施顺序归并为 **6 个阶段**，每阶段对应 tasks.md 的一个任务组，阶段间存在严格的前置依赖。每个阶段包含 Design Doc 指定的 Build Verify Point（标记为 ⚡ verify）。

**基线约束**：
- 后端基线 = HEAD `2bc0e5e` 的 `mvn -pl :web -am test` = 66 tests, 0 failures
- 前端基线 = `npm test` = 14 failed / 65 passed（预存失败），admin `App.test.tsx` 2/2 通过
- tsc -b 前必须先通过所有前端类型改造

**改动估计**（Design Doc §4.2）：
- 3 文件（含新建测试文件），约 100-150 行净增
- 后端：`ChildProfileController.java`（+~30 行）+ `ChildProfileControllerTest.java`（+~80 行，新文件）
- 前端：`web/src/parent/pages/index.tsx`（~1005 行，多处方修改）+ `ErrorBoundary.tsx`（+2 行）

archived-with: 2026-07-13-fix-parent-pages-contract
---

## 阶段 1：后端补 ChildProfileController GET 端点（3 子任务）

**目标**：实现 Design Decision 1，补全 `ChildProfileController` 的 GET 端点，返回 PageResult 形状。解决缺陷 #1。

**前置 verify（Design Doc §8.2 第 3 点）**：
- ⚡ verify `ChildProfileMapper` 是否有 `findActiveByFamilyId(Page, Long)` 重载
  - 如有：复用即可
  - 如无：需在 mapper XML 加方法或使用 PageHelper 实现分页能力

**涉及 Design Decision**：DD1（后端补 GET 走 PageResult 模式）

### 任务 1.1：新增 listChildren GET 方法

- **原任务编号**：1.1
- **capability**：parent-pages-contract
- **目标**：在 `server/family/src/main/java/com/cutegoals/family/controller/ChildProfileController.java` 新增 `@GetMapping` 方法
- **实现方式**（Design Doc DD1）：
  - 方法签名：`public ApiResponse<Map<String, Object>> listChildren(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize, HttpServletRequest request)`
  - 复用现有认证逻辑（与 POST 端点同解析模式）
  - SecurityContext 拿 accountId → `familyMapper.findFamilyByMemberAccountId(accountId)` → familyId
  - `childProfileMapper.findActiveByFamilyId(new Page<>(page, pageSize), familyId)`
  - 构造 LinkedHashMap PageResult `{content, page, pageSize, totalElements, totalPages}`
  - 返回 `ApiResponse.success(result, requestId)`
- **输入**：Design Doc §4.1 数据流图
- **输出**：编译通过的 GET 端点
- **验收标准**：端点返回 401（未授权时）而非 405
- **依赖任务**：无（仅验证 ChildProfileMapper 方法存在性）
- **建议提交粒度**：1 个 commit（与任务 1.2 合并）

### 任务 1.2：验证后端编译

- **原任务编号**：1.2
- **capability**：parent-pages-contract
- **目标**：`mvn -pl :family -am compile` exit 0
- **验收标准**：compile 通过，无编译错误
- **依赖任务**：1.1

### 任务 1.3：新建 ChildProfileControllerTest（最小验证）

- **原任务编号**：1.3（合并）
- **capability**：parent-pages-contract
- **目标**：`server/family/src/test/java/.../controller/ChildProfileControllerTest.java` 新建，至少 2 个 case（Design Doc §6.1）
- **测试策略**（Design Doc Risk 5.5）：
  - 1. `listChildren_unauthorized_returns401`：无 token → 401（因 `web/pom.xml` 未引入 `spring-security-test`，无法 mock 认证用户，此为 baseline 预期行为）
  - 2. `listChildren_authorized_returnsPageResult`：mock SecurityContext + familyMapper → 200 + PageResult 形状（如配置复杂可跳过，仅保留 401 测试）
- **运行验证**：`mvn -pl :family -am test` 全绿（66 + 新增测试通过）
- **依赖任务**：1.1
- **建议提交粒度**：与 1.1-1.2 合并为 1 个 commit

archived-with: 2026-07-13-fix-parent-pages-contract
---

## 阶段 2：前端页面类型与参数对齐（5 子任务）

**目标**：实现 Design Decision 2/3/4/5/6/7，前端引入 PageResult<T> 类型，统一 usePaginatedData 和 useApi 的契约对齐。解决缺陷 #2/#4/#5。

**前置 verify（Design Doc §8.2 第 1 点）**：
- ⚡ verify `TaskAssignmentController.listAssignments` 参数名
  - Design Doc Decision 4 假设为 `startDate/endDate`
  - 如实际为 `deadlineStart/deadlineEnd`，build 阶段按实际 controller 签名调整 URL 参数

**涉及 Design Decision**：DD2（PageResult<T> 类型）、DD3（usePaginatedData 改造）、DD4（Calendar 接口替换）、DD5（useApi<ChildProfile[]>）、DD6（useApi<TaskTemplate[]>）、DD7（useApi<ReviewItem[]>）

### 任务 2.1：顶部新增 PageResult<T> 类型

- **原任务编号**：2.1
- **capability**：parent-pages-contract
- **目标**：`web/src/parent/pages/index.tsx` 顶部新增
  ```typescript
  interface PageResult<T> {
    content: T[];
    page: number;
    pageSize: number;
    totalElements: number;
    totalPages: number;
  }
  ```
- **理由**（DD2）：与 admin 端同模式，不抽 shared/types 避免跨模块重构（YAGNI）
- **依赖任务**：1.1（后端端点存在）
- **建议提交粒度**：与 2.2-2.5 合并为 1-2 个 commit

### 任务 2.2：改造 usePaginatedData<T> 期望 PageResult

- **原任务编号**：隐含（Design Doc §4.2）
- **capability**：parent-pages-contract
- **目标**：L143-158 usePaginatedData 改造（DD3）
  ```typescript
  function usePaginatedData<T>(path: string) {
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(20);
    const { data, loading, error, refetch } = useApi<PageResult<T>>(`${path}?page=${page}&pageSize=${pageSize}`);
    return {
      items: data?.content ?? [],
      total: data?.totalElements ?? 0,
      page, pageSize, setPage, setPageSize, loading, error, refetch,
    };
  }
  ```
- **影响范围**（6 处使用，接口签名不变自动适配）：
  - L201 `usePaginatedData<Invitation>('/family/invitations')`
  - L277 `usePaginatedData<ChildProfile>('/family/children')`
  - L377 `usePaginatedData<TaskTemplate>('/task-templates')`
  - L783 `usePaginatedData<Prize>('/prizes')`
  - L885 `usePaginatedData<BlindBox>('/blind-boxes')`
  - L932 `usePaginatedData<Exchange>('/exchanges')`
- **风险缓解**（Design Doc Risk 5.3）：hook 返回接口签名完全不变，tsc + dev 浏览器验证 6 处渲染
- **依赖任务**：2.1

### 任务 2.3：任务页 Calendar 接口替换为分页查询

- **原任务编号**：2.5
- **capability**：parent-pages-contract
- **目标**：删除 L493 `useApi<{assignments, dates}>('/task-assignments/calendar?date=${date}')`，改用（DD4）：
  ```typescript
  const { data, loading, error, refetch } = useApi<PageResult<TaskAssignment>>(
    `/task-assignments?page=1&pageSize=50&childId=${childId ?? ''}&startDate=${date}&endDate=${date}`
  );
  ```
- **参数注意**：根据 verify 结果调整参数名（`startDate/endDate` 或 `deadlineStart/deadlineEnd`）
- **渲染调整**：组件原 `assignments.map(...)` 改为 `(data?.content ?? []).map(...)`
- **根因消除**：缺陷 #2（参数错配 400）和缺陷 #3（calendar 语义混淆）同时修复
- **依赖任务**：2.1

### 任务 2.4：任务页 useApi<T[]> → useApi<PageResult<T>> 解包（×2）

- **原任务编号**：2.4, 2.5（部分）
- **capability**：parent-pages-contract
- **目标**：L494 + L495 + L701 三处改造：
  - L494 `useApi<TaskTemplate[]>('/task-templates')` → `useApi<PageResult<TaskTemplate>>('/task-templates')` + `templatesPage?.content ?? []` 解包（DD6）
  - L495 `useApi<ChildProfile[]>('/family/children')` → `useApi<PageResult<ChildProfile>>('/family/children')` + `childrenPage?.content ?? []` 解包（DD5）
  - L701 `useApi<ChildProfile[]>('/family/children')` → `useApi<PageResult<ChildProfile>>` + 同模式解包（DD5）
- **依赖任务**：2.1

### 任务 2.5：审核页 useApi<ReviewItem[]> ×2 → useApi<PageResult<ReviewItem>> ×2

- **原任务编号**：2.2, 2.3
- **capability**：parent-pages-contract
- **目标**：L595-596 改类型 + 解包（DD7）：
  ```typescript
  const { data: pendingData, ... } = useApi<PageResult<ReviewItem>>('/task-review/pending');
  const { data: historyData, ... } = useApi<PageResult<ReviewItem>>('/task-review/history');
  const pending = pendingData?.content ?? [];
  const history = historyData?.content ?? [];
  // pending.map / history.map 渲染逻辑不变
  ```
- **根因消除**：修复缺陷 #3（`pending.map is not a function` → React 崩溃 → 白屏）
- **依赖任务**：2.1

archived-with: 2026-07-13-fix-parent-pages-contract
---

## 阶段 3：ErrorBoundary fallback 包 Layout（2 子任务）

**目标**：实现 Design Decision 8，解决缺陷 #4（审核页全屏无菜单栏）。

**前置 verify（Design Doc §8.2 第 2 点，Risk 5.2）**：
- ⚡ verify `Layout` 是否强依赖 `useAuth`
  - 如不依赖使用 Auth（纯展示组件）：直接包 Layout，无风险
  - 如强依赖（NavLinks 内调用 useAuth() L88-120）：降级方案——在 ErrorBoundary 内 inline 简化版 header（logo + 错误提示 + 基础菜单链接），保留骨架感

**涉及 Design Decision**：DD8（ErrorBoundary fallback 包 Layout）

### 任务 3.1：改造 ErrorBoundary 默认 fallback

- **原任务编号**：3.1
- **capability**：parent-pages-contract
- **目标**：`web/src/shared/components/ErrorBoundary.tsx` L34-46 改造：
  ```tsx
  // 旧：<ErrorState title={...} message={...} onReset={...} />
  // 新：
  <Layout>
    <ErrorState title={...} message={...} onReset={...} />
  </Layout>
  ```
- **保留 props**：`title`、`message`、`onReset` 等全部保留，仅包裹 Layout
- **依赖任务**：无（独立于阶段 1/2）
- **建议提交粒度**：1 个独立 commit（便于回滚）

### 任务 3.2：验证 ErrorBoundary 与 Layout 未嵌套

- **原任务编号**：3.2
- **capability**：parent-pages-contract
- **目标**：确认 ErrorBoundary 在 Router 顶层（不在 Layout 内部），包 Layout 后不会出现「双 header」渲染
- **验证方法**：代码审查 ErrorBoundary 在组件树中的位置 + tsc 编译验证 + dev 浏览器手动验证
- **依赖任务**：3.1

archived-with: 2026-07-13-fix-parent-pages-contract
---

## 阶段 4：类型检查与单元测试门禁（2 子任务）

**目标**：验证前、后端全部通过基线门禁，无新增失败。

### 任务 4.1：TypeScript 编译验证

- **原任务编号**：4.1
- **capability**：parent-pages-contract
- **目标**：`cd web && npx tsc -b` exit 0，0 errors
- **重点 verify 项**（Design Doc §6.2）：
  - PageResult<T> 类型无重复定义
  - usePaginatedData 改造后类型签名匹配 6 处调用方
  - 所有 useApi<T[]> → useApi<PageResult<T>> 改造完成
  - ErrorBoundary fallback 包 Layout 后无类型错误（如 Layout 缺 Provider 则触发降级方案）
- **依赖任务**：阶段 2 + 阶段 3

### 任务 4.2：前端单元测试回归

- **原任务编号**：4.2
- **capability**：parent-pages-contract
- **目标**：`cd web && npm test`，与 fix-admin-pages-missing-layout baseline 对照：
  - 14 failed / 65 passed（预存失败，列表不变）
  - admin 端 `src/admin/__tests__/App.test.tsx` 2/2 保持通过
  - **0 新增失败**
- **依赖任务**：4.1

archived-with: 2026-07-13-fix-parent-pages-contract
---

## 阶段 5：端到端与回归验证（5 子任务）

**目标**：通过 curl 和浏览器手动测试验证全部 4 个 parent 页面正常渲染。

### 任务 5.1：重启 dev server

- **原任务编号**：5.1
- **capability**：parent-pages-contract
- **目标**：如有运行中的 JVM，需重启加载新编译的 ChildProfileController GET 字节码
- **注意**：当前 dev server 运行在 8981 端口（HEAD 2bc0e5e 字节码），需使用 backend-runner 或手动重启

### 任务 5.2：curl 验证 ChildProfileController GET

- **原任务编号**：5.2
- **capability**：parent-pages-contract
- **目标**：curl 双场景验证（Design Doc §6.4）：
  - `curl -i http://localhost:8981/api/family/children` → 401（未授权，证明端点存在）
  - 带 parent token cookie：`curl -i -b "token=..." http://localhost:8981/api/family/children?page=1&pageSize=20` → 200 + `{content: [...], page, pageSize, totalElements, totalPages}` 形状
- **验收标准**：不再返回 405

### 任务 5.3：浏览器验证 4 个 parent 页面

- **原任务编号**：5.3
- **capability**：parent-pages-contract
- **目标**：浏览器登录 parent 账号，逐一访问（Design Doc §6.4）：
  - `/parent/family`（家庭页）：正常显示家庭成员 + children 列表
  - `/parent/tasks`（任务页）：正常显示当天任务列表（task-assignments 分页查询）
  - `/parent/review`（审核页）：正常显示 pending + history 列表（无白屏/无崩溃）
  - `/parent/points`（积分页）：正常显示 children 下拉 + 积分流水
- **验收标准**：页面无「加载失败」、无白屏、无 ErrorState

### 任务 5.4：模拟 React 崩溃验证 ErrorBoundary

- **原任务编号**：5.4
- **capability**：parent-pages-contract
- **目标**：设计临时 React 崩溃场景（如 mock useApi 抛错），验证 ErrorBoundary fallback 渲染后仍显示菜单栏（Layout header + footer）
- **验收标准**：用户看到完整 Layout（非白屏/非纯错误卡）
- **依赖任务**：3.1（必须先完成 ErrorBoundary 改造）

### 任务 5.5：根因消除 grep

- **原任务编号**：5.5
- **capability**：parent-pages-contract
- **目标**（Design Doc §6.5）：
  - 后端：`@GetMapping` in ChildProfileController → 1 match（listChildren）
  - 前端 index.tsx：
    - `useApi<.*\[\]>` → 0 matches（全部改为 PageResult<T>）
    - `data?.content` 或 `?.content ?? [].map` → 出现在改造后位置
    - `?date=${date}` → 0 matches（改为 startDate/endDate 参数）
  - ErrorBoundary.tsx：
    - `<Layout>` → 1 match（fallback 包裹）
    - 旧 `<ErrorState`（裸）→ 0 matches
- **验收标准**：所有旧形状无残留，新形状全部就位

archived-with: 2026-07-13-fix-parent-pages-contract
---

## 阶段 6：提交与推进（3 子任务）

**目标**：完成 git commit，推进到 verify 阶段。

### 任务 6.1：git commit

- **原任务编号**：6.1
- **capability**：parent-pages-contract
- **目标**：提交代码变更，commit message 提案：
  ```
  fix(parent): align pages data shape and add children GET endpoint + wrap ErrorBoundary in Layout
  ```
- **粒度建议**（Design Doc §7.3）：单 commit 或 2-3 logical commits
  - 方案 A（推荐）：1 commit（小改动，便于回滚）
  - 方案 B：后端 commit + 前端 commit + ErrorBoundary commit

### 任务 6.2：分支策略决策

- **原任务编号**：6.2
- **capability**：parent-pages-contract
- **目标**：用户确认分支策略
- **选项**：
  - 提 main 分支（与前 5 个 hotfix 同策略）
  - 新分支（如 `fix/parent-pages-contract`）→ PR → main

### 任务 6.3：guard build --apply 推进到 verify 阶段

- **原任务编号**：6.3
- **capability**：parent-pages-contract
- **目标**：触发 comet build 流程推进到 verify 阶段
- **前置条件**：阶段 1~5 全部通过

archived-with: 2026-07-13-fix-parent-pages-contract
---

## 附加：Build 阶段 Verify Point 汇总

来源于 Design Doc §8.2（Build 阶段需 verify，非 defer）：

| # | Verify 点 | 来源 | 影响 | 决策时机 |
|---|---|---|---|---|
| 1 | `TaskAssignmentController.listAssignments` 参数名（`startDate/endDate` vs `deadlineStart/deadlineEnd`） | DD4 / Risk 5.1 | 任务 2.3 前端 URL 参数 | 阶段 2 开始前 |
| 2 | `Layout` 是否强依赖 `useAuth` | DD8 / Risk 5.2 | 任务 3.1 ErrorBoundary 包 Layout vs 降级方案 | 阶段 3 开始前 |
| 3 | `ChildProfileMapper` 是否有 `findActiveByFamilyId(Page, Long)` 重载 | DD1 / §8.2 | 任务 1.1 实现方式 | 阶段 1 开始前 |
| 4 | 所有 `useApi<...[]>` 是否全部改造完成（PageResult 一致性） | Risk 5.6 / §6.5 | 任务 5.5 grep 审计 | 阶段 5 |

archived-with: 2026-07-13-fix-parent-pages-contract
---

## 附加：Risks & Mitigations 摘要（来自 Design Doc §5）

| # | 风险 | 缓解措施 | 影响阶段 |
|---|---|---|---|
| 5.1 | Calendar 接口替换的参数不确定性 | Build verify 实际签名；如不支持日期过滤降级为全量拉取 + 前端 filter | 阶段 2 |
| 5.2 | Layout 在 ErrorBoundary fallback 中 auth context 依赖 | Build verify Layout 依赖；如强依赖则 inline 简化版 header | 阶段 3 |
| 5.3 | usePaginatedData 改造影响 6 处使用 | 接口签名不变；tsc + dev 浏览器验证 | 阶段 2 |
| 5.4 | useApi<ChildProfile[]> → PageResult 解包遗漏 | tsc 强类型捕获；逐个 verify 调用处 | 阶段 2 |
| 5.5 | 新增 controller test 增加改动面 | 最小化到 401 验证（参考 fix-admin-401 策略） | 阶段 1 |
| 5.6 | PageResult 一致性 | grep 审计所有 useApi<...[]> 调用 | 阶段 5 |
| 5.7 | 改动面 verify_mode 评估 | < 8 文件 = light verify；超过则 scale 自动评估 | 全阶段 |

archived-with: 2026-07-13-fix-parent-pages-contract
---

## 附加：Testing Strategy 汇总（来自 Design Doc §6）

| 验证层级 | 命令/方式 | 通过标准 | 阶段 |
|---|---|---|---|
| 后端单元测试 | `mvn -pl :family -am test` | 66 + 新增测试全绿 | 阶段 1 |
| 前端类型检查 | `cd web && npx tsc -b` | exit 0, 0 errors | 阶段 4 |
| 前端单元测试 | `cd web && npm test` | baseline 一致，0 新增失败 | 阶段 4 |
| curl 端点验证 | `curl -i /api/family/children` | 401（未授权） | 阶段 5 |
| 浏览器 E2E | 手动访问 4 个 parent 页面 | 无加载失败/白屏/崩溃 | 阶段 5 |
| React 崩溃验证 | 临时 mock 抛错 | ErrorBoundary 含 Layout | 阶段 5 |
| 根因消除 grep | 多 grep 模式审计 | 旧形状 0 matches | 阶段 5 |

archived-with: 2026-07-13-fix-parent-pages-contract
---

## 附录：文件改动清单

| 文件 | 操作 | 估计行数 | 说明 |
|---|---|---|---|
| `server/family/src/main/java/.../ChildProfileController.java` | 修改 | +~30 行 | 新增 `@GetMapping listChildren` 方法 |
| `server/family/src/test/java/.../ChildProfileControllerTest.java` | 新建 | +~80 行 | 最小化 401 + 200 形状测试 |
| `web/src/parent/pages/index.tsx` | 修改 | +~20 行 | PageResult 类型 + usePaginatedData + 5 处 useApi 改造 + Calendar 替换 |
| `web/src/shared/components/ErrorBoundary.tsx` | 修改 | +2 行 | fallback 用 `<Layout>` 包裹 |

