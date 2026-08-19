# Outcome

将现有单一 Umi 前端工程（web/，内含 admin/parent/child 三端，路由 /admin、/parent、/child）拆分为两个独立可部署的前端工程：

- 「家长+管理端」（console）：承载 /parent 与 /admin 全部页面；
- 「孩子端」（kid）：承载 /child 全部页面（含登录、绑定）。

部署时两个工程作为同一 docker-compose 中两个独立的 frontend 容器运行，达到更好的权限隔离（孩子设备不再下载家长/管理端任何代码；孩子端 API 流量受其容器白名单约束）。同时孩子端重构为移动优先的自适应架构（平板、手机为主，PC 为辅），并采用明亮糖果色童趣视觉风格。

# Scope

- 前端工程拆分：web/ 单工程 → monorepo 双 app（实现决定：pnpm workspace，apps/console、apps/kid、packages/shared 或等价布局），共享代码（API client、类型、工具、认证上下文）以内部 shared 包复用，不复制源码。两 app 各自独立安装依赖、构建、测试、出 Docker 镜像。
- 部署拓扑（D1）：同一 compose 运行 console、kid 两个 frontend 容器 + 现有 nginx 容器作为唯一入口网关（80/443）。网关按路径分流：/child/* → kid 容器；其余前端路径 → console 容器；/api/* → 后端（console 流量）。孩子端 SPA base = /child/。
- 孩子端 API 白名单（D2）：kid SPA 的 API baseURL 为 /child/api；网关将 /child/api/* 分流至 kid 容器，kid 容器 nginx 以白名单放行孩子端点后转发后端，其余 403。白名单端点（/api 相对路径）：POST /auth/child/login、POST /auth/logout、GET /auth/me、GET /family/devices/children、GET /task-assignments、GET /points/balance/*、GET /prizes、GET /blind-boxes/*、GET /exchanges、POST /task-review/submissions、POST /exchanges/direct、POST /exchanges/blind-box。
- 孩子端移动优先重构（D3/D4）：自绘童趣组件层（底部 Tab 导航、大圆角卡片、大触控按钮、Chip 筛选、卡片流），保留 React + 现有 Umi/Vite 构建链，仅保留极少量 antd 底层组件；明亮糖果色设计系统（天空蓝主色 + 蜜桃橙/薄荷绿/柠檬黄点缀，16~20px 圆角，系统圆粗字体栈，emoji/贴纸点缀）。断点：<768px 手机、768~1023px 平板（均为一等形态），≥1024px PC 辅助形态（内容居中受限宽度，非桌面拉伸）。
- 家长端/管理端：功能、页面与视觉风格保持现状，PC 优先不变，仅做工程搬迁与 import 路径适配。
- 部署要素适配：docker-compose.yml / docker-compose.dev.yml / build.sh / Dockerfile（每 app 一个）/ nginx.conf / nginx.dev.conf / .env 模板 / e2e 入口适配双前端拓扑；原单一 web 工程与单 frontend 容器退役。

# Non-goals

- 不修改后端 API 契约、数据结构与 JWT 角色鉴权逻辑。
- 不改变家长端/管理端的功能与视觉风格。
- 不改变孩子端的业务功能与数据契约（任务五分类归类、提交受限展示、积分/奖品/盲盒/兑换流程行为不变，仅重构呈现层与交互架构）。
- 不引入第三方字体文件（使用系统字体栈）；不引入 antd-mobile；不做主题场景化插画。
- 不处理在途 change kid-task-filter-active-highlight-fix：其修复代码已在 main（工作树干净），随本重构一并迁移，本 change 最终验证覆盖其验收（筛选器可见 + 主题色背景白字高亮），之后再行归档。

# Acceptance examples

- Given 同一 compose 启动全部服务，When 访问入口网关，Then /child/* 由 kid 容器提供、其余前端路径由 console 容器提供，两容器可独立停止/启动且互不影响对方构建产物。
- Given kid 构建产物，When 检查其静态资源，Then 不包含家长端/管理端页面代码与路由（bundle 物理隔离）。
- Given 孩子端页面发起的 API 请求，When 命中白名单外端点（经 /child/api/），Then kid 容器返回 403；白名单内端点正常代理至后端。
- Given 手机/平板宽度（320~834px）访问孩子端，When 浏览首页/任务/奖品/盲盒/兑换，Then 为移动优先布局（底部 Tab、单列卡片流、无横向滚动、触控目标 ≥44px）。
- Given PC 宽度（≥1024px）访问孩子端，Then 内容以居中受限宽度呈现且全部功能可用。
- Given 重构完成，Then 孩子端既有行为不回归：vitest 用例通过、五分类筛选（Chip 呈现，选中项主题色背景 + 白字）、提交受限文案、登录/绑定流程均与契约一致；npm run lint 不引入新错误。

# Constraints and invariants

- 两个 frontend 容器 MUST 能独立构建、独立启动/停止。
- kid 产物 MUST NOT 包含家长/管理端页面代码（bundle 层面物理隔离）。
- 后端接口契约 MUST NOT 改变；前端仅消费现有 API。
- 孩子端 MUST 保持现有业务行为契约（数据字段 content 分页、归类规则、未来任务置灰、提交受限文案等）。
- 移动优先 MUST 落实为真实断点与触控目标设计，而非仅缩放桌面布局。
- 孩子端筛选器选中态 MUST 继续以主题色背景 + 白色文字高亮（兼容 child-page-migration 规格）。

# Decisions

- D1（2026-08-19 用户确认）：对外入口采用同域路径分流 —— 保留现有 nginx 容器作为唯一入口网关（80/443），按路径分流：/child/* → 孩子端容器，其余路径 → 家长/管理端容器。URL 结构与证书不变；孩子端 SPA 设置 base 路径 /child/。
- D2（2026-08-19 用户确认）：孩子端容器 nginx 采用白名单 API 过滤 —— 仅放行孩子端运行时实际使用的端点前缀，其余一律 403；后端 JWT 角色鉴权仍为最终兜底。新增孩子端功能时须同步维护白名单。
- D3（2026-08-19 用户确认）：孩子端采用自绘童趣组件层策略 —— 保留 React 与现有构建链（Umi/Vite），新建移动优先童趣组件层，仅保留极少量 antd 底层组件；不引入 antd-mobile，不完全去组件库。
- D4（2026-08-19 用户确认）：孩子端视觉调性为明亮糖果色 —— 高饱和明快配色（天空蓝主色 + 蜜桃橙/薄荷绿/柠檬黄点缀），16~20px 大圆角，emoji/贴纸风点缀，圆粗字形、大字号；不做马卡龙粉彩，不做主题场景化插画。

# Open questions

（无；用户已于 2026-08-19 明确确认共享理解摘要）

# Verification expectations

- 两个前端工程各自构建通过（typecheck/lint/test）。
- compose 级冒烟：两 frontend 容器并存运行，/child/* 与其余路径分流正确，孩子端与家长端登录及核心页面可用。
- kid 容器白名单实测：白名单内端点通、名单外端点 403。
- 孩子端移动端（375/768px）与 PC（1280px）断点真实浏览器渲染核查。
- 覆盖旧 change 验收：孩子端任务筛选器可见且选中项主题色背景 + 白字高亮。