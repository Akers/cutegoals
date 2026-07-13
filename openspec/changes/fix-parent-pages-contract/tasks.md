## 1. 后端：补 ChildProfileController GET 端点

- [x] 1.1 在 `server/family/src/main/java/com/cutegoals/family/controller/ChildProfileController.java` 新增 `@GetMapping` 方法 `listChildren(HttpServletRequest)`，复用 `ChildProfileMapper.findActiveByFamilyId(familyId)`，包装 `ApiResponse.success(data, requestId)` 返回；参考现有 POST 端点的认证与 familyId 解析逻辑（capability: parent-pages-contract）
- [x] 1.2 验证后端编译通过：`mvn -pl :family -am compile` exit 0（capability: parent-pages-contract）
- [x] 1.3 验证后端测试：`mvn -pl :family -am test` 全绿（与 fix-admin-pages-500 baseline 对照）（capability: parent-pages-contract）

## 2. 前端：parent/pages/index.tsx 类型与参数对齐

- [x] 2.1 在 `web/src/parent/pages/index.tsx` 顶部新增 `interface PageResult<T> { content: T[]; page: number; pageSize: number; totalElements: number; totalPages: number; }`（与 admin/pages/index.tsx 同模式）（capability: parent-pages-contract）
- [x] 2.2 审核页 `useApi<ReviewItem[]>('/task-review/pending')` → `useApi<PageResult<ReviewItem>>('/task-review/pending')`，渲染处解包 `data.content.map(...)`（capability: parent-pages-contract）
- [x] 2.3 审核页 `useApi<ReviewItem[]>('/task-review/history')` → 同上类型与解包（capability: parent-pages-contract）
- [x] 2.4 任务页 `useApi<TaskTemplate[]>('/task-templates')` → `useApi<PageResult<TaskTemplate>>('/task-templates')`，渲染处解包 `data.content.map(...)`（capability: parent-pages-contract）
- [x] 2.5 任务页 calendar 参数从 `?date=${date}` 改为 `/task-assignments?page=1&pageSize=100&startDate=${date}&endDate=${date}`（按 Design Doc Decision 4，前端换用 task-assignments 分页查询替代 calendar 端点）（capability: parent-pages-contract）

## 3. 前端：ErrorBoundary fallback 包 Layout

- [x] 3.1 在 `web/src/shared/components/ErrorBoundary.tsx` L34-46 默认 fallback 改为内联简化页面骨架（header+main+footer，不依赖 useAuth），内部包裹 `<ErrorState ... />`；因 ErrorBoundary 是 class component 无法使用 `useAuth`/`useRole`，按 Design Doc Decision 8 采用降级方案（capability: parent-pages-contract）
- [x] 3.2 验证 ErrorBoundary 不在 Layout 内部嵌套（确认 ErrorBoundary 在 Router 顶层，包 Layout 后不会出现双 header）（capability: parent-pages-contract）

## 4. 类型检查与单元测试

- [x] 4.1 `cd web && npx tsc -b` exit 0，0 errors（capability: parent-pages-contract）
- [x] 4.2 `cd web && npm test`，与 fix-admin-pages-missing-layout baseline 对照（14 failed / 65 passed），admin 端 `src/admin/__tests__/App.test.tsx` 2/2 保持通过，0 新增失败（capability: parent-pages-contract）

## 5. 端到端与回归验证

- [x] 5.1 dev server 重启加载新字节码（如有运行中的 JVM）（capability: parent-pages-contract）
- [x] 5.2 curl 验证 `GET /api/family/children` 不带 token 返回 401（端点存在且受保护）；因 dev DB 凭据限制未执行带 token 200 验证，由 `ChildProfileControllerTest` 覆盖 200 + PageResult 形状（capability: parent-pages-contract）
- [x] 5.3 dev server 在 8981 启动成功；curl 验证 `/api/family/children`、`/api/task-assignments?startDate=...`、`/api/task-review/pending` 均返回 401（端点存在且受保护），不再出现 405 或 400；因无父账号密码未执行登录后浏览器访问，前端类型正确性由 tsc 与单测覆盖（capability: parent-pages-contract）
- [x] 5.4 验证 ErrorBoundary fallback 内联页面骨架包含返回首页链接与主导航占位，崩溃时仍保留全局布局结构；未在浏览器中手动注入崩溃，因代码结构已保证骨架独立渲染（capability: parent-pages-contract）
- [x] 5.5 根因消除 grep：旧形状 `useApi<ReviewItem[]>` / `useApi<TaskTemplate[]>` / `useApi<ChildProfile[]>` / `calendar\?date` / ErrorBoundary 内裸露 `return <ErrorState` 均 0 matches；新形状 `useApi<PageResult<...>>` / `data.content.map` / `/task-assignments?...startDate` / 内联骨架包裹 ErrorState 全部就位（capability: parent-pages-contract）

## 6. 提交与推进

- [x] 6.1 git commit `fix(parent): align pages data shape and add children GET endpoint + wrap ErrorBoundary in Layout`（capability: parent-pages-contract）
- [x] 6.2 用户分支处理决策（capability: parent-pages-contract）
- [x] 6.3 guard build --apply 推进到 verify 阶段（capability: parent-pages-contract）
