# 目标

修复家长端积分管理页（`/parent/points`）的 bug：家长确认积分调整并收到成功提示后，页面上的积分余额与流水列表不更新（仍显示调整前的数据），必须整页刷新才能看到新状态。根因是 console 前端 alova 实例未关闭 GET 响应的默认 5 分钟内存缓存，写操作成功后的重新拉取命中了陈旧缓存。修复后，console 所有页面在写操作后的重新拉取都必须反映后端最新状态。

# 范围

- `web/apps/console/src/utils/http/alova/index.ts`：在 `createAlova` 中启用 `cacheFor: null`，全局关闭响应缓存（恢复代码中被注释掉的「关闭全局请求缓存」配置）。这是唯一必需的代码改动。
- `e2e/tests/`：新增积分调整后界面即时更新的 Playwright 回归用例（沿用 `E2E_PARENT_PHONE`/`E2E_PARENT_PASSWORD` 环境变量登录模式，凭据缺失时按套件惯例 skip）。
- 已完成的诊断证据（2026-09-15，部署环境 https://localhost）：
  - 后端正确：`POST /api/points/adjustments` 返回 SUCCESS 后，`GET /api/points/balance/1` 返回 22、`GET /api/points/ledger/1` 含新增记录；响应头已带 `no-cache, no-store`。
  - 浏览器级复现（Playwright 注入会话）：调整 +3 成功提示后，同页面余额仍 22/流水仍 2 条；全新加载后 25/3 条，与后端一致 → 陈旧数据来自前端内存缓存（alova v3.5.5 GET 默认 300000ms 内存缓存）。
  - 部署的 JS bundle 与仓库代码一致（调整后调用 refetch），排除部署过期构建。

# 非目标

- 不修改后端 points 接口与数据（已验证行为正确）。
- 不修改孩子端 `web/apps/kid` 与 `web/packages/shared`。
- 不引入新的缓存策略（如按端点配置缓存时长）；如未来需要缓存优化，另立 change。
- 不处理本机 docker 权限问题；重新部署容器镜像由用户执行或授权执行。

# 验收示例

- A1: 家长在积分管理页选择孩子并确认一次积分调整，成功提示出现后不刷新页面，余额卡片立即显示调整后的新余额，流水列表立即出现刚才的调整记录，数值与后端 `GET /api/points/ledger/{childId}` 一致。
- A2: console 中同一会话内对同一 GET 接口的重复请求（如积分页切换孩子后再切回）不再返回 5 分钟内的陈旧缓存，每次都发起真实网络请求（可通过请求计数或响应时效验证）。
- A3: 修复后的 console 构建通过（`pnpm build`），且新增的 e2e 回归用例在提供 `E2E_PARENT_PHONE`/`E2E_PARENT_PASSWORD` 与 `BASE_URL`（指向完整栈网关）时通过。

# 约束与不变量

- 后端零改动；`web/apps/kid`、`web/packages/shared` 零改动。
- 保持 alova 模板封装结构与现有拦截/转换逻辑不变，仅调整缓存配置。
- 管理端与家长端所有现有页面行为不回退；HTTP 层其余行为（信封解析、CSRF、401 跳转）不变。

# 决策

- D1（实现选择，Agent 决定）：修复采用全局 `cacheFor: null` 关闭 alova 响应缓存，而不是仅在积分页 refetch 处加 `force`。理由：默认缓存影响 console 全部页面的「写后读」一致性（任务审核、奖品、兑换核销等同场景），全局关闭恢复模板注释中「关闭全局请求缓存」的原意；CRUD 控制台数据正确性优先于请求节省。
- D2（验证方式，Agent 决定）：回归用例放入现有 Playwright e2e 套件（BASE_URL 指向完整栈时执行），console 本身无单测设施，不新增单测框架。

# 待解决问题

（无。用户已于 2026-09-15 确认目标/范围/验收/非目标，进入实现。）

# 验证预期

- `web/apps/console` 执行 `pnpm build` 成功。
- 部署环境浏览器级 e2e（与诊断相同的复现步骤）：调整积分成功提示后，同页面余额与流水立即更新且与后端 API 一致。
- e2e 套件：`BASE_URL=https://localhost`（忽略自签证书）+ 家长凭据环境变量下，新增用例通过；无凭据环境按套件惯例 skip。
- 注意：验收「部署环境」项需要先重新构建并部署 `cutegoals-core-console` 镜像；本机 docker 需用户权限，由用户执行或授权。
