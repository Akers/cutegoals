# Outcome

修复孩子端 `/child/exchanges` 页面访问报错 “加载失败 Internal server error”，消除后端 `MethodArgumentTypeMismatchException`，使页面正常返回兑换历史列表（即使没有记录也展示空态而非 500）。

# Scope

- 修改文件：`web/src/child/pages/index.tsx` 中 `usePaginatedData` helper（行 49-62），使其正确处理传入路径已包含查询字符串的情况。
- 不修改后端代码（控制器契约 `childId` 仍为 `@RequestParam`）。
- 不修改其他 helper（`useApi`、`useChildId`）。
- 不修改家长端等价 helper（`web/src/parent/pages/index.tsx:268-291` 已正确处理）。

# Non-goals

- 不重构分页抽象为通用 hook；本次仅修复具体 bug。
- 不去掉 `childId` 查询参数（虽然对子会话冗余，但移除会扩大改动面，且当前 spec 无回归需求）。
- 不修复其他 page 端潜在 URL 拼接问题；除非有具体证据，否则不超出本 bug 范围。
- 不修改家长端 `/parent/exchanges` 或其他端点（其 helper 行为已正确）。
- 不引入新的依赖或工具。

# Acceptance examples

1. **bug 复现到消失**：登录子账号（childId=2）→ 访问 `/child/exchanges` → 不再返回 500。后端日志不再出现 `MethodArgumentTypeMismatchException: For input string: "2?page=1"`。
2. **空记录正确展示**：兑换记录为 0 时，页面显示 “还没有兑换记录” 空态（已存在），而非 “加载失败”。
3. **非空记录正确展示**：兑换记录 ≥ 1 时，页面渲染卡片列表（targetName / type / costPoints / createdAt / status）。
4. **URL 正确拼接**：实际请求 URL 为 `/api/exchanges?childId=2&page=1&pageSize=20`（使用 `&` 分隔），而非 `/api/exchanges?childId=2?page=1&pageSize=20`。
5. **回归检查**：家长端 `/parent/exchanges` 仍按 `usePaginatedData('/exchanges')` 调用（无 childId），URL 仍为 `/api/exchanges?page=1&pageSize=20` 形式，不受影响。
6. **未变页面回归**：孩子端 `/child/prizes` 仍调用 `usePaginatedData<Prize>('/prizes/available')`（路径无 `?`），URL 仍为 `/api/prizes/available?page=1&pageSize=20`，渲染正常。

# Constraints and invariants

- 项目使用 React + Ant Design；不能引入新 UI 库或破坏现有组件。
- `useApi` 已封装 GET/POST 等方法，本 helper 只负责拼接 URL，不重新实现 HTTP 请求。
- 后端 controller `ExchangeController.queryExchanges` 在 `ROLE_CHILD` 上下文优先使用 `sessionChildId`（来自 `currentChildId` request 属性），仅在请求 `childId` 与会话不一致时拒绝。修复后子账号行为不变。
- 项目 TypeScript 严格模式；helper 修改必须通过 `tsc`。
- 修改单文件单函数，不影响其他模块。

# Decisions

1. **采用最小修复路径**：在 `usePaginatedData` 中检测路径是否已包含 `?`，决定分隔符（`?` vs `&`）。这是 1 行代码变更，保留所有现有调用方契约，不引入新参数。
   - **备选**：让前端不再传 `childId`（依赖后端 sessionChildId）。
   - **理由**：选 A 改动面更小，与既有 API 契约一致；选 B 涉及跨前后端契约变更，超出 bug 修复范围。
2. **保持 helper 单参数签名**：不引入 filters 参数（与家长端不同），避免扩大接口。子端用例仅 2 个，无重复需求。
3. **不变更后端**：后端契约保持 `@RequestParam(required = false) Long childId`，前端照常传值，作为子会话下的冗余校验。

# Open questions

（用户已确认共享理解；阻塞项已解除。调查闭合。）

# Verification expectations

- **V1 类型检查**：在 `web/` 目录运行 `npx tsc --noEmit` 退出码 0，无新增类型错误。
- **V2 单元/快照检查**：`grep -n "usePaginatedData" web/src/child/pages/index.tsx` 仍 2 个调用方（Prize/Exchange）；修改行数 ≤ 5。
- **V3 静态 URL 拼接验证**：人工/脚本验证 `usePaginatedData<Exchange>(childId ? '/exchanges?childId=${childId}' : '')` 在 childId=2 时产出 `/exchanges?childId=2&page=1&pageSize=20`，不含第二个 `?`。
- **V4 dev server 启动**：可选；如启动则访问 `/child/exchanges` 验证 200 响应且后端日志无 `MethodArgumentTypeMismatchException`。
- **V5 回归**：家长端 `/parent/exchanges` 仍正常（手动或脚本）。
- 未运行的检查：V4/V5 受环境限制时记录在 verification.md 的 Skipped checks。