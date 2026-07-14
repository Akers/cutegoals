## 1. 工程基线与模块边界

- [x] 1.1 [design] 建立 server/ Maven 聚合工程及 common、auth、system、business、application 模块；验证：模块依赖图与 Maven 编译通过。
- [x] 1.2 [design] 建立 web/ React 18 + TypeScript + Vite 工程并配置 lint、单元测试和路径别名；验证：前端检查、测试和生产构建通过。
- [x] 1.3 [web-app] 配置 /admin、/parent、/child 路由、懒加载和角色级路由守卫；验证：三角色路由集成测试通过。
- [x] 1.4 [web-app] 建立共享 API 客户端、稳定错误码映射、Cookie 会话和 CSRF 请求支持；验证：客户端契约测试覆盖成功、401、403/404 和冲突错误。
- [x] 1.5 [design] 建立统一配置分层、Asia/Shanghai 默认时区、结构化日志和敏感字段脱敏；验证：配置加载与日志脱敏测试通过。
- [x] 1.6 [deployment-operations] 建立 deploy/ 目录、Compose 环境模板及 Bash/PowerShell 管理脚本入口；验证：脚本帮助与参数检查测试通过。
- [x] 1.7 [design] 建立持续集成基线，串联后端、前端、OpenSpec 和容器检查；验证：干净环境完整流水线通过。

## 2. 数据库模型与迁移

- [x] 2.1 [deployment-operations] 接入版本化数据库迁移并创建空库基线；验证：MySQL 8 空库可重复初始化且 schema 版本一致。
- [x] 2.2 [auth] 创建用户、角色、用户角色、初始化令牌、会话、刷新令牌和登录限流表；验证：唯一约束和失效字段迁移测试通过。
- [x] 2.3 [family] 创建单家庭、家庭成员、孩子档案、家长邀请和孩子设备绑定表；验证：单有效家庭、最后家长保护所需约束可验证。
- [x] 2.4 [task-template][task-assignment] 创建模板、难度、周期规则、任务分配和任务快照表；验证：快照字段完整且周期发生键唯一。
- [x] 2.5 [task-review] 创建提交尝试、审核记录和状态转换所需表；验证：历史不可变和同一提交唯一批准约束测试通过。
- [x] 2.6 [points] 创建积分账户与不可变流水表；验证：业务引用唯一、退款原消费引用和余额版本约束测试通过。
- [x] 2.7 [prize][blind-box][exchange] 创建奖品、奖池、奖品项、兑换、兑换快照和履约状态表；验证：权重、库存和退款唯一约束测试通过。
- [x] 2.8 [instance-management] 创建实例配置与审计日志表并补齐查询索引；验证：迁移前后索引、外键和审计字段检查通过。

## 3. 初始化、认证与家庭成员

- [x] 3.1 [auth] 实现首次启动一次性初始化令牌的生成、过期和单次消费；验证：重放和过期场景测试通过。
- [x] 3.2 [auth][family] 在单事务内创建唯一家庭及首位 INSTANCE_ADMIN + PARENT 用户；验证：并发初始化仅一个请求成功，失败无残留数据。
- [x] 3.3 [auth] 实现手机号加密码登录、密码自适应哈希和不枚举账号的失败响应；验证：成功、错误凭据、停用和限流测试通过。
- [x] 3.4 [auth] 定义可插拔短信认证接口并保持默认关闭；验证：未配置时明确不可用，测试适配器配置后流程可用。
- [x] 3.5 [auth] 实现短期访问令牌、刷新令牌轮换、HttpOnly Cookie 和 CSRF 防护；验证：刷新重放、过期和 CSRF 集成测试通过。
- [x] 3.6 [auth] 实现登出、密码变更、账号停用后的会话撤销；验证：已有刷新令牌立即失效。
- [x] 3.7 [auth][deployment-operations] 实现部署方本地管理员恢复命令和一次性恢复流程；验证：无需厂商网络且旧凭据被撤销。
- [x] 3.8 [family] 实现家庭资料查询和编辑；验证：唯一家庭边界、输入校验和审计测试通过。
- [x] 3.9 [family] 实现家长邀请的创建、接受、拒绝、撤销、过期与幂等；验证：重复邀请和无效邀请码场景测试通过。
- [x] 3.10 [family] 实现孩子档案创建、编辑、停用、导出和删除匿名化；验证：个人字段清除且历史引用保持一致。
- [x] 3.11 [family][instance-management] 实现成员移除、退出与账号停用保护；验证：不能移除最后一位有效家长或实例管理员。
- [x] 3.12 [auth][family] 实现孩子设备绑定授权、消费、撤销、档案选择、PIN 校验和五次失败锁定；验证：绑定重放、跨设备、锁定和解锁测试通过。

## 4. 任务模板与任务分配

- [x] 4.1 [task-template] 实现模板创建、更新、分页查询、分类筛选和启停，包含 `task_type`（LIMITED/REPEAT/STANDING）与 `type_config` 字段的 CRUD；验证：权限校验、字段校验、`type_config` 与 `task_type` 匹配测试通过。
- [x] 4.2 [task-template] 实现重复任务的触发日规则（DAILY/WEEKLY/MONTHLY/YEARLY 四种 frequency），包括 ISO 周几(1-7)、月模式(FIRST_DAY/LAST_DAY/MID_MONTH 含月末自适应)、年月日(含闰年/月份合法性自适应)；验证：四种 frequency 的合法/非法输入、月末自适应、Asia/Shanghai 时区边界通过。
- [x] 4.3 [task-template] 实现难度等级、顺序和正整数奖励积分管理；验证：重复等级、非正积分和引用中删除场景通过。
- [x] 4.4 [task-template] 实现模板软删除及历史引用保护；验证：现有分配和历史查询不受后续编辑影响。
- [x] 4.5 [task-assignment] 实现单次任务分配并固化模板、难度、积分、截止时间和任务类型快照；验证：LIMITED/STANDING 直接创建实例、模板修改不追溯已有分配。
- [x] 4.6 [task-assignment] 实现批量分配、客户端幂等键和参数冲突检测，覆盖三类 task_type；验证：重试不重复，复用键但参数不同返回冲突，三类模板批量分配幂等。
- [x] 4.7 [task-assignment] 实现 REPEAT 模板的双触发推进（提交触发器在审核通过事件中创建下一期、时间触发器每日 Asia/Shanghai 00:05 运行扫描过期与切换状态）和稳定 `assignment_id + trigger_day` 发生键；验证：调度重跑、夏令时边界、并发执行不产生重复。
- [x] 4.8 [task-assignment] 实现家庭默认迟交策略与单任务覆盖，覆盖 LIMITED/REPEAT 的 EXPIRED 状态不可补交；验证：允许/禁止迟交、LIMITED 过期、REPEAT 非触发日、稳定错误码测试通过。
- [x] 4.9 [task-assignment] 实现按日、月和孩子筛选的日历查询，显示三类任务的状态(PENDING/SUBMITTABLE/ACTIVE/PENDING_OPEN/OPEN/COMPLETED/EXPIRED)及逾期派生标记；验证：Asia/Shanghai 日期边界、三类任务在日历视图正确展示通过。
- [x] 4.10 [task-assignment] 实现未批准任务的取消与审计，支持三类 task_type 的取消语义（LIMITED/SANDING 取消实例、REPEAT 取消当前期并停止后续生成）；验证：已批准任务不可取消且历史记录保留，REPEAT 取消后不生成新期。
- [x] 4.11 [task-template] 实现 LIMITED 任务的时间窗口状态机：`start_date`/`end_date` 解析（Asia/Shanghai 当地 00:00 与 23:59:59.999）、PENDING→SUBMITTABLE 自动切换、EXPIRED 自动标记；验证：未到开始日期、过期、空 start_date 即时开始场景通过。
- [x] 4.12 [task-template] 实现 STANDING 任务的 `submission_count` 字段和按孩子独立计数（每次审核通过自增 1，驳回/拒绝不自增）；验证：单孩子多次提交、多孩子并发提交不串号通过。
- [x] 4.13 [task-template] 实现 STANDING 任务达上限处理：`max_submissions=null` 永远 ACTIVE、`max_submissions=N` 时 count==N 切换 COMPLETED；验证：达上限后提交返回 `TASK_STANDING_LIMIT_REACHED`、列表展示提示通过。
- [x] 4.14 [task-template] 实现 REPEAT 触发日计算函数 `nextTriggerDate()`：DAILY（plusDays(1)）、WEEKLY（下一个指定 weekday）、MONTHLY（FIRST_DAY/LAST_DAY/MID_MONTH 月末自适应）、YEARLY（含闰年 2 月 29 日只在闰年触发）；验证：四种 frequency 的跨周期边界通过。
- [x] 4.15 [task-template] 实现 REPEAT 提交触发钩子：审核通过事件中同步将本期切换 COMPLETED 并创建下一期 PENDING_OPEN 实例；验证：审核通过后下一期立刻可见但不可提交，到下个触发日才可提交。
- [x] 4.16 [task-template] 实现 REPEAT 时间触发器 `RepeatTaskScheduler`：每日 Asia/Shanghai 00:05 运行，扫描 OPEN 实例推进 EXPIRED、PENDING_OPEN 切换 OPEN；验证：单模板失败不影响其他、同日多次运行幂等、审计日志写入通过。
- [x] 4.17 [task-template] 实现 REPEAT 单期状态机 PENDING_OPEN→OPEN→COMPLETED/EXPIRED 与模板分配即生成首期逻辑；验证：分配当日是/否触发日两种场景、非触发日提交返回 `TASK_REPEAT_NOT_TRIGGER_DAY` 通过。
- [x] 4.18 [task-template] 实现 `task_type` 不可改性验证：PUT 请求中 `task_type` 字段与既有值不一致时返回 `TASK_TEMPLATE_TYPE_IMMUTABLE` 且不增加版本；`type_config` 内字段允许修改。验证：尝试改类型被拒绝、修改 type_config 内字段成功通过。

## 5. 提交审核与积分账本

- [x] 5.1 [task-review] 实现孩子提交任务并创建不可变提交尝试；验证：非本人、无效状态、迟交与重复请求测试通过。
- [x] 5.2 [task-review] 实现家长驳回、必填原因和 REJECTED 可观察状态；验证：驳回后不发积分并保留审核历史。
- [x] 5.3 [task-review] 实现驳回后的重新提交和新 attempt；验证：旧审核数据不被覆盖且状态回到 SUBMITTED。
- [x] 5.4 [task-review][points] 实现批准与 EARN 流水同事务提交；验证：任一步失败整体回滚。
- [x] 5.5 [task-review][points] 实现同一提交的幂等和并发批准保护；验证：多请求最多产生一笔 EARN。
- [x] 5.6 [points] 实现积分账户投影与 EARN、SPEND、REFUND、ADJUST 不可变流水服务；验证：流水顺序和余额重算一致。
- [x] 5.7 [points] 实现家长带必填原因的正负调整和审计；验证：负调整不得造成负余额。
- [x] 5.8 [points] 实现孩子个人流水、家长家庭汇总、分页和类型/日期筛选；验证：角色数据范围测试通过。
- [x] 5.9 [points] 实现流水业务引用唯一、退款引用原消费和单次退款约束；验证：重试、并发与重复退款测试通过。

## 6. 奖品、盲盒与兑换履约

- [x] 6.1 [prize] 实现奖品创建、更新、分页查询、图片、正整数积分价格、非负库存和启停；验证：字段与权限测试通过。
- [x] 6.2 [prize] 实现库存调整、软删除和历史引用保护；验证：被兑换引用的奖品不能物理删除。
- [x] 6.3 [blind-box] 实现奖池创建、更新、成本和启停；验证：空奖池或无可抽项时不能对孩子开放。
- [x] 6.4 [blind-box] 实现奖品项管理和正整数相对权重校验；验证：零值、负值、重复项和任意正权重总和测试通过。
- [x] 6.5 [blind-box] 实现缺货/停用项过滤、有效概率归一化和孩子端透明查询；验证：概率合计和库存变化场景通过。
- [x] 6.6 [blind-box] 实现可注入随机源的加权抽取；验证：确定性区间测试覆盖边界，统计测试声明样本与容差。
- [x] 6.7 [exchange][points][prize] 实现明牌兑换的原子扣分、扣库存、快照和 SPEND 流水；验证：余额或库存不足时整体回滚。
- [x] 6.8 [exchange][points][blind-box] 实现盲盒兑换、抽取、库存锁定、结果快照和 SPEND 流水；验证：并发不超卖且结果不可重抽。
- [x] 6.9 [exchange] 实现兑换幂等键和同键参数冲突；验证：网络重试返回原兑换且不重复扣减。
- [x] 6.10 [exchange] 实现家长兑现及 PENDING_FULFILLMENT → FULFILLED 转换；验证：重复兑现和无权限请求被拒绝。
- [x] 6.11 [exchange][points] 实现家长取消、单次 REFUND 和库存恢复；验证：已兑现不可取消、重复取消无重复退款、孩子不能取消盲盒。

## 7. 实例管理、安全与审计

- [x] 7.1 [instance-management] 实现实例初始化状态、版本、构建信息、依赖健康、备份运行状态与恢复演练结果查询；验证：未认证只暴露最小健康信息。
- [x] 7.2 [instance-management] 实现受控系统配置的读取、校验、更新和审计；验证：未知键、越界值和失败回滚测试通过。
- [x] 7.3 [instance-management][auth] 实现账号列表、角色展示、启停和会话撤销；验证：最后管理员保护和即时失效测试通过。
- [x] 7.4 [instance-management] 实现审计日志分页、操作者/事件/日期筛选和脱敏导出；验证：秘密不进入日志。
- [x] 7.5 [auth][family][exchange][points] 为登录、绑定、成员、审核、积分调整、兑换取消和配置操作接入统一审计；验证：成功与失败事件均可追踪。
- [x] 7.6 [auth] 统一认证限流、Cookie 安全属性、CSRF 和资源枚举防护；验证：安全集成测试覆盖恶意重试和跨角色访问。
- [x] 7.7 [family][instance-management] 实现家庭与孩子数据导出以及删除匿名化作业；验证：导出完整、删除不可逆确认且历史主体匿名。
- [x] 7.8 [design] 统一稳定错误码、请求关联 ID 和敏感日志过滤；验证：API 契约与日志快照测试通过。

## 8. 三端 Web 体验

- [x] 8.1 [web-app] 实现 admin、parent、child 三套主题变量、共享组件和路由级切换；验证：视觉回归与主题隔离测试通过。
- [x] 8.2 [web-app][auth] 实现初始化、家长登录、可选短信入口、登出和会话过期页面；验证：键盘、触摸与错误恢复流程通过。
- [x] 8.3 [web-app][auth][family] 实现孩子设备绑定、档案选择、PIN 键盘和锁定提示；验证：撤销绑定后设备立即失效。
- [x] 8.4 [web-app][instance-management] 实现实例概览、配置、账号、审计、版本和健康页面；验证：不出现跨家庭 SaaS 功能。
- [x] 8.5 [web-app][family][task-template] 实现家长家庭成员、邀请、孩子档案和任务模板/难度管理；验证：核心 CRUD E2E 通过。
- [x] 8.6 [web-app][task-assignment][task-review] 实现家长日历、批量分配、迟交标记和审核历史；验证：滑动审核同时提供按钮替代。
- [x] 8.7 [web-app][points][prize][blind-box][exchange] 实现家长积分、奖品、奖池、有效概率和兑换履约页面；验证：取消与退款反馈一致。
- [x] 8.8 [web-app][task-assignment][task-review] 实现孩子首页、任务列表、提交、驳回原因和重提；验证：状态与历史显示 E2E 通过。
- [x] 8.9 [web-app][points][prize][blind-box][exchange] 实现孩子积分、商店、概率确认、开箱和兑换历史；验证：孩子不能触发受限取消操作。
- [x] 8.10 [web-app] 完成响应式、加载/空/错误/离线状态、减少动态效果和低端设备降级；验证：主要页面 LCP 与可访问性交互检查通过。

## 9. 私有化部署、备份与升级

- [x] 9.1 [deployment-operations] 为后端、前端和反向代理编写可复现的多阶段 Dockerfile；验证：linux/amd64 与 linux/arm64 构建成功。
- [x] 9.2 [deployment-operations] 编写包含反向代理、应用、MySQL、Redis、命名卷和健康检查的 Compose；验证：全新主机一条命令启动并达到 healthy。
- [x] 9.3 [deployment-operations] 提供 Bash 与 PowerShell 本地联网构建脚本；验证：Windows 与 Linux 文档化环境完成相同版本构建。
- [x] 9.4 [deployment-operations] 提供环境模板、秘密生成、配置校验和无默认密码门禁；验证：缺失/弱秘密时生产启动失败并给出诊断。
- [x] 9.5 [deployment-operations] 配置生产 HTTPS、反向代理安全头和 localhost 开发例外；验证：生产 HTTP 被重定向或拒绝。
- [x] 9.6 [deployment-operations] 实现应用、数据库、Redis、磁盘和迁移状态健康检查及 doctor 命令；验证：故障依赖可准确定位。
- [x] 9.7 [deployment-operations] 实现数据库与上传文件每日备份、七日加四周保留和校验；验证：计划任务与过期清理测试通过。
- [x] 9.8 [deployment-operations] 实现恢复脚本、预检和恢复后一致性检查；验证：RPO 不超过 24 小时、参考数据 RTO 不超过 2 小时。
- [x] 9.9 [deployment-operations] 实现升级前强制备份、版本化迁移、失败停止和从备份恢复流程；验证：成功升级与故障演练通过。
- [x] 9.10 [deployment-operations] 编写 Windows/Linux 安装、配置、HTTPS、备份、恢复、升级和故障排查文档；验证：未参与开发的审阅者按文档完成部署演练。

## 10. 综合验证与交付门禁

- [x] 10.1 [all] 建立 Requirement/Scenario 到任务和自动化测试的追踪清单；验证：每条 Requirement 至少有一项验证证据。
- [x] 10.2 [auth][family] 完成初始化、家长认证、孩子绑定和成员生命周期集成测试；验证：成功、权限、限流和恢复边界全部通过。
- [x] 10.3 [task-template][task-assignment][task-review][points] 完成任务到积分闭环集成测试；验证：驳回重提、迟交、幂等、并发和回滚通过。
- [x] 10.4 [prize][blind-box][exchange][points] 完成奖励兑换闭环集成测试；验证：概率、超卖、退款、履约和重试通过。
- [x] 10.5 [instance-management][web-app] 完成三角色 API 权限与端到端测试；验证：越权读写、不可见资源和账号停用场景通过。
- [x] 10.6 [deployment-operations] 在 Linux amd64、Linux arm64 及 Windows Docker 环境执行 Compose 烟雾测试；验证：构建、启动、健康和基础闭环通过。
- [x] 10.7 [deployment-operations] 在 2 vCPU、4 GB、十万业务记录和二十并发会话参考环境执行性能测试；验证：API P95 和页面 LCP 达标。
- [x] 10.8 [deployment-operations] 执行备份恢复与升级故障演练并保存报告；验证：RPO/RTO、数据一致性和操作步骤达标。
- [x] 10.9 [web-app][auth] 完成依赖漏洞、秘密扫描、Cookie/CSRF、日志脱敏和无障碍检查；验证：无未处置高风险问题。
- [x] 10.10 [all] 运行后端/前端完整测试、容器检查及 openspec validate core-features --strict；验证：全部通过并将证据写入验证报告后才允许勾选 change 完成。

## 11. 验证失败修复（core-features verify 回退 build）

- [x] 11.1 [task-template] 实现任务模板三类型系统数据库层与错误码：在 `task_template` 表添加 `task_type`/`type_config`，在 `task_assignment` 表添加 `submission_count`，在 `ErrorCode` 补充 `TASK_TEMPLATE_TYPE_IMMUTABLE`、`TASK_LIMITED_NOT_STARTED`、`TASK_LIMITED_EXPIRED`、`TASK_STANDING_LIMIT_REACHED`、`TASK_REPEAT_NOT_TRIGGER_DAY`；验证：迁移在空库和现有库均可重复执行，错误码被全局处理器识别。
- [x] 11.2 [task-template] 重构周期规则模型为 `frequency` + `trigger_day`：移除 `task_recurrence_rule` 简化表，将 DAILY/WEEKLY/MONTHLY/YEARLY 和 `trigger_day` 结构（含 weekday/mode/month/day 与月末自适应）作为 `task_template.type_config` 的 JSON 字段；验证：原有数据迁移不丢失，四种频率合法/非法输入与月末自适应测试通过。
- [x] 11.3 [task-template] 实现 STANDING 与 LIMITED 任务类型业务逻辑：STANDING 按孩子独立 `submission_count` 计数、达上限切换 COMPLETED 并返回 `TASK_STANDING_LIMIT_REACHED`；LIMITED 解析 `start_date`/`end_date` 并维护 PENDING→SUBMITTABLE→ACTIVE→EXPIRED 状态机，未到开始日期返回 `TASK_LIMITED_NOT_STARTED`，已过期返回 `TASK_LIMITED_EXPIRED`；验证：状态转换、错误码和审计覆盖。
- [x] 11.4 [task-template][task-assignment] 实现 REPEAT 任务双触发器：时间触发器 `RepeatTaskScheduler` 每日 Asia/Shanghai 00:05 扫描并推进 OPEN/EXPIRED 与 PENDING_OPEN→OPEN；提交触发器在审核通过事件中同步创建下一期 PENDING_OPEN 实例；实现 `nextTriggerDate()` 支持 DAILY/WEEKLY/MONTHLY/YEARLY 含闰年自适应；验证：调度重跑幂等、并发不产生重复、非触发日提交返回 `TASK_REPEAT_NOT_TRIGGER_DAY`。
- [x] 11.5 [task-template][task-assignment] 补充任务模板三类型系统端到端测试：覆盖 LIMITED/REPEAT/STANDING 的 CRUD、类型不可变、批量分配、状态机、触发器、错误码和 API 契约；验证：新增测试与既有测试全部通过。
- [x] 11.6 [task-template] 在服务层接线 taskType/typeConfig：`createTemplate()` 从请求提取 `taskType`/`typeConfig`，`getTemplateDetail()`/`queryTemplates()` 在响应中包含这两个字段，`updateTemplate()` 支持更新 `typeConfig`；验证：全部测试通过。
