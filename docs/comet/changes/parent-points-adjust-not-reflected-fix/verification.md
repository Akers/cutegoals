---
generated_from_state_version: 6
---

# 验证

## 当前结果

- 结果: **已阻塞**
- 验证情况: **解决报告中的阻塞项后恢复验证**
- 目标周期: 1
- 迭代: 1
- 验证器尝试次数: 1
- 完成时间: 2026-09-15T10:37:43.366Z
- 摘要: 修复证据充分：alova 3.5.5 源码级证明 cacheFor:null 永不写缓存、每个 GET 必达网络；对照实验独立复验成立（修复前陈旧 0 网络请求，修复后立即更新+1 网络请求）；e2e skip 惯例已实证。A3/A9 待用户重建 cutegoals-core-console 镜像并提供家长凭据后跑 deployed-console-e2e 补验。

## 验收

| 编号 | 结果 | 来源 | 验收项 | 原因 |
| --- | --- | --- | --- | --- |
| A1 | passed | brief.md | A1: 家长在积分管理页选择孩子并确认一次积分调整，成功提示出现后不刷新页面，余额卡片立即显示调整后的新余额，流水列表立即出现刚才的调整记录，数值与后端 `GET /api/points/ledger/{childId}` 一致。 | 独立复跑对照实验（真实 dist-fixed 构建 + 真实 chromium）：调整成功提示后不刷新页面，余额 100→105、流水 1→2，与后端 ledger 状态一致；dist-fixed 与仓库当前 dist 字节相同 |
| A2 | passed | brief.md | A2: console 中同一会话内对同一 GET 接口的重复请求（如积分页切换孩子后再切回）不再返回 5 分钟内的陈旧缓存，每次都发起真实网络请求（可通过请求计数或响应时效验证）。 | alova 3.5.5 源码级证明 cacheFor:null 使缓存永不写入、所有 GET 必达网络；实验网络计数判别成立（buggy 0 次 vs fixed 1 次）；无 per-method cacheFor/force/hitSource 覆盖、无 service worker |
| A3 | blocked | brief.md | A3: 修复后的 console 构建通过（`pnpm build`），且新增的 e2e 回归用例在提供 `E2E_PARENT_PHONE`/`E2E_PARENT_PASSWORD` 与 `BASE_URL`（指向完整栈网关）时通过。 | 构建部分已通过（dist 含 cacheFor:null 且与浏览器实验验证构建字节一致）；e2e 实跑部分待用户提供 E2E_PARENT_PHONE/PASSWORD 凭据并重建部署 cutegoals-core-console 镜像（本机 docker 无权限）后执行 deployed-console-e2e |
| A4 | passed | specs/console-frontend/spec.md | 空闲超过原阈值不锁屏 - WHEN 已登录的 console 用户保持空闲超过 1 小时（原锁屏阈值）后操作页面 - THEN 不弹出锁屏覆盖层，页面直接响应操作 | web/apps/console/src 中 Lockscreen/screenLock/IS-SCREENLOCKED/useBattery/useOnline/useTime/timekeeping/锁屏 全部零匹配，无可弹出锁屏的代码路径；本次 diff 未触及 |
| A5 | passed | specs/console-frontend/spec.md | 顶栏无锁屏入口且源码无残留 - WHEN 检查 console 顶栏功能图标列表并在仓库 web/apps/console 中搜索 Lockscreen、screenLock、IS-SCREENLOCKED、useBattery、useOnline、useTime - THEN 顶栏不显示"锁屏"图标项，且上述搜索在源码与引用中均无结果 | 同 A4 grep 零结果；Header 组件无锁屏菜单项 |
| A6 | passed | specs/console-frontend/spec.md | 构建与既有功能不受影响 - WHEN 对 console 应用执行类型检查与生产构建，并访问登录、家长端、管理端页面 - THEN 构建成功，页面行为与本变更前一致 | pnpm build 通过（产物与实验验证 chunk 逐字节一致）；diff 仅共享 HTTP 客户端单一选项，信封/CSRF/401 逻辑逐行核对未变；登录+家长端页面已在真实构建上浏览器级驱动；管理端共享同一未改管线且编译通过 |
| A7 | passed | specs/console-frontend/spec.md | 积分调整后界面不刷新即更新 - WHEN 家长在 `/parent/points` 选择孩子，确认一次积分调整并看到「积分已调整」成功提示，期间不刷新页面 - THEN 余额卡片立即显示调整后的新余额，流水列表立即出现该调整记录，数值与后端 `GET /api/points/ledger/{childId}` 的 `currentBalance` 与 `content` 一致 | 同 A1：调整后不刷新页面余额/流水立即更新，数值与 ledger API 响应交叉核对一致 |
| A8 | passed | specs/console-frontend/spec.md | 重复 GET 不返回陈旧内存缓存 - WHEN 同一会话内对同一 GET 接口在 5 分钟内发起重复请求（如积分页切换孩子后再切回原孩子） - THEN 每次请求都真实到达后端（浏览器网络记录可见新请求），响应反映后端当时最新状态，不返回 alova 内存缓存副本 | 同 A2：源码级证明缓存永不写入，重复 GET 必发起真实网络请求；实验网络计数判别成立 |
| A9 | blocked | specs/console-frontend/spec.md | 构建与 e2e 回归通过 - WHEN 对 console 执行 `pnpm build`，并在提供 `BASE_URL`（完整栈网关）与家长凭据环境变量时运行新增的积分调整 e2e 用例 - THEN 构建成功，用例通过；未提供凭据时用例按套件惯例 skip 而非失败 | 构建通过且 skip 惯例已实证（无 BASE_URL/凭据时全部 skip、exit 0，Runtime 检查 e2e-skip-convention passed）；提供凭据+BASE_URL 的 e2e 实跑未执行，同 A3 待部署重建与凭据 |

## 检查

| 检查 | 命令 | 工作目录 | 状态 | 退出码 | 耗时 |
| --- | --- | --- | --- | ---: | ---: |
| console 生产构建（含 cacheFor:null 修复） | -c set -o pipefail; cd web/apps/console && pnpm build 2>&1 \| tail -3 | . | passed | 0 | 19661 ms |
| 构建产物包含 cacheFor:null | -c grep -l 'cacheFor:null' web/apps/console/dist/assets/index-*.js | . | passed | 0 | 8 ms |
| 新增 e2e 用例编译/发现 | -c set -o pipefail; cd e2e && npx playwright test tests/points-adjust.spec.ts --list 2>&1 \| tail -3 | . | passed | 0 | 1394 ms |
| 浏览器级对照实验（修复前陈旧 vs 修复后立即更新） | /tmp/cutegoals-deploy-check/run-control-experiment.sh | . | passed | 0 | 16594 ms |
| 无 BASE_URL/凭据时新用例全部 skip 且退出码 0 | -c cd e2e && npx playwright test tests/points-adjust.spec.ts 2>&1 \| tail -5 | . | passed | 0 | 5166 ms |

### Builder 报告的证据

以下为 Builder 报告，不等同于 Runtime 检查凭据或独立验收结果。

- console-production-build: passed — cd web/apps/console && pnpm build 成功，产物 dist/assets 含 cacheFor:null
- mock-ui-control-experiment: passed — 本地 HTTPS mock 后端驱动真实 dist：修复前构建调整后界面陈旧/ledger网络0次；修复后构建界面立即更新/网络1次
- e2e-spec-compile: passed — npx playwright test tests/points-adjust.spec.ts --list 编译通过，skip 守卫（FULL_STACK/凭据/project 名）生效
- deployed-stack-e2e: not-run — 需 E2E_PARENT_PHONE/E2E_PARENT_PASSWORD 凭据并重建部署 cutegoals-core-console 镜像（本机 docker 无权限），留待 Verify
- 已知限制: 部署环境（https://localhost）的 e2e 实跑待用户提供家长凭据并重建 console 镜像后执行
- 已知限制: 诊断中发现的后端独立缺陷（不在本 change 范围）：/api/auth/refresh 在旧 access token 过期时会先 revoke refresh token 再因解析旧 token 失败返回 401，导致 refresh 链被烧掉；建议另立 change 修复

## 阻塞项

- **user**: 修复证据充分：alova 3.5.5 源码级证明 cacheFor:null 永不写缓存、每个 GET 必达网络；对照实验独立复验成立（修复前陈旧 0 网络请求，修复后立即更新+1 网络请求）；e2e skip 惯例已实证。A3/A9 待用户重建 cutegoals-core-console 镜像并提供家长凭据后跑 deployed-console-e2e 补验。 (acceptance: A3, A9) — next: `resolve-verifier-blocker`

## 风险与跳过的工作

- 部署栈 https://localhost 仍运行修复前 console 构建，用户可见 bug 在重建部署前依然存在
- 诊断中发现后端独立缺陷（不在本 change 范围）：/api/auth/refresh 在旧 access token 过期时先 revoke refresh token 再解析旧 token 失败返回 401，refresh 链被烧掉，建议另立 change

## 之前的迭代

| 目标周期 | 迭代 | 尝试 | 结果 | 未解决项 | 摘要 | 完成时间 |
| ---: | ---: | ---: | --- | --- | --- | --- |
| 1 | 1 | 1 | blocked | A3, A9 | 修复证据充分：alova 3.5.5 源码级证明 cacheFor:null 永不写缓存、每个 GET 必达网络；对照实验独立复验成立（修复前陈旧 0 网络请求，修复后立即更新+1 网络请求）；e2e skip 惯例已实证。A3/A9 待用户重建 cutegoals-core-console 镜像并提供家长凭据后跑 deployed-console-e2e 补验。 | 2026-09-15T10:37:43.366Z |



## 结论

修复证据充分：alova 3.5.5 源码级证明 cacheFor:null 永不写缓存、每个 GET 必达网络；对照实验独立复验成立（修复前陈旧 0 网络请求，修复后立即更新+1 网络请求）；e2e skip 惯例已实证。A3/A9 待用户重建 cutegoals-core-console 镜像并提供家长凭据后跑 deployed-console-e2e 补验。
