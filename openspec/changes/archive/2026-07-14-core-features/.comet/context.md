# Comet Design Handoff

- Change: core-features
- Phase: design
- Mode: compact
- Context hash: 11da1e8b63899f17037696329a5dda98a8aa9be40b70d3f93df8151e06993f63

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/core-features/proposal.md

- Source: openspec/changes/core-features/proposal.md
- Lines: 1-47
- SHA256: b2e3a602bb012e0f708f8fd5aef732c796581dd8b29aeea60ad78ab451b5216f

```md
## Why

CuteGoals 2.0 当前只有未经实现验证的需求草稿，却被错误标记为 89/89 任务完成；同时，草稿仍以公网多家庭系统和短信优先认证为前提，与已确认的“中国大陆、单家庭私有化部署、可长期真实使用的上线级 MVP”目标不一致。现在需要在任何应用代码实施前重建一套一致、可追踪、可验收的需求基线。

## What Changes

- **BREAKING**：将 MVP 部署边界从公网多家庭服务调整为“每个私有化实例恰好服务一个家庭”，跨家庭与机构级能力延后到独立 change。
- **BREAKING**：将家长必备认证从短信验证码调整为手机号加密码；短信登录仅在部署方配置服务商后作为可选能力启用。
- **BREAKING**：将原跨家庭 admin 能力调整为 instance-management，只负责实例初始化、配置、账号、审计、版本与健康管理。
- 新增孩子设备绑定模型：家长授权设备后，孩子选择档案并输入家庭内 PIN，绑定可撤销且具有限流锁定。
- 明确任务审核状态机、不可变提交/审核历史、任务与奖品快照语义，以及重复/并发请求的幂等约束。
- 将积分改为不可变流水事实源，统一获得、消费、退款与调整，并保证余额、库存和兑换记录的事务一致性。
- 补齐兑换履约与取消退款闭环，统一盲盒库存过滤、相对权重归一化和概率透明规则。
- 新增私有化部署运维能力，覆盖 Windows/Linux Docker Compose、本地联网构建、多架构镜像、HTTPS、健康检查、备份恢复和升级迁移。
- 补齐儿童数据最小化、凭据保护、审计、无默认遥测、性能、可访问性和恢复目标等上线验收要求。
- 重置全部虚假完成的任务，并按 capability、产物、测试和验证证据重新组织实施清单。
- 本 PRD 虽包含多个 capability，但用户已确认保留单一 core-features change：当前仓库为空，认证、任务、积分和兑换共同组成不可分割的首个可用闭环；实施任务仍按依赖阶段组织，未来多家庭能力另建 change。

## Capabilities

### New Capabilities

- auth: 实例初始化、家长密码/可选短信认证、会话安全、孩子设备绑定与基于角色的授权。
- family: 单实例单家庭初始化、家庭资料、家长邀请、孩子档案与成员生命周期。
- task-template: 任务模板、分类、可选周期规则、难度等级与积分配置。
- task-assignment: 单次/批量/周期任务分配、快照、日历、截止时间与迟交策略。
- task-review: 孩子提交、家长审核、驳回重提、审核历史与幂等积分发放。
- points: 不可变积分流水、余额投影、获得/消费/退款/调整与家庭查询。
- prize: 奖品资料、积分价格、库存、启停、软删除与历史快照。
- blind-box: 盲盒奖池、相对权重、库存过滤、有效概率与透明展示。
- exchange: 明牌/盲盒兑换、原子扣减、履约、取消退款、幂等与历史查询。
- instance-management: 单实例配置、账号启停、审计日志、版本和健康状态管理。
- web-app: 管理员、家长、孩子三端统一 SPA、主题、响应式、权限守卫与无障碍交互。
- deployment-operations: 多平台 Compose、本地构建、配置与密钥、健康检查、备份恢复、升级和非功能验收。

### Modified Capabilities

（主规范目录当前为空，本 change 中的能力均作为新能力建立。）

## Impact

- **代码结构**：新增 server/ Maven 多模块后端、web/ React SPA、deploy/ 部署与运维资产；不复用已删除的 1.0 实现。
- **API**：认证、任务、积分与兑换接口增加幂等键、稳定错误码、设备绑定、履约和退款语义；旧草稿接口不构成兼容承诺。
- **数据**：领域数据保留 family_id 以支持未来扩展，但 MVP 强制单实例单家庭；新增不可变审核记录、积分流水、兑换状态和审计约束。
- **依赖**：Spring Boot 3、MyBatis-Plus、MySQL 8、Redis、React 18、TypeScript、Vite、Docker Compose 及多架构构建工具链。
- **部署**：部署方在 Windows 或 Linux 主机上联网本地构建并启动 Linux amd64/arm64 容器；不依赖 CuteGoals 厂商 SaaS 或默认外部遥测。
- **迁移**：当前仓库无 2.0 应用数据和实现，无需业务数据迁移；原文档仅作为历史记录保留，由本 change 和新版 Design Doc 取代。

```

## openspec/changes/core-features/design.md

- Source: openspec/changes/core-features/design.md
- Lines: 1-142
- SHA256: 5142cc834d6d1f896b042bb2cbca15a76591f9e7e7019c41f40050352c6d7e0b

[TRUNCATED]

```md
## Context

CuteGoals 2.0 要从空仓重新建立面向真实家庭长期使用的 MVP。仓库当前没有后端、前端、数据库迁移或测试实现，现有 2026-04-07 设计稿和 core-features change 却把全部任务标记为完成，并混用了公网多家庭、短信优先、跨家庭管理员与单家庭业务假设。该状态不能作为实施或验收基线。

本设计服务于四类利益相关方：负责部署和维护实例的管理员、管理家庭激励规则的家长、完成任务和兑换奖励的孩子，以及后续负责扩展学校/社区多家庭模式的开发者。

约束如下：

- 首发区域为中国大陆，产品只提供私有化部署分发包，不提供厂商 SaaS。
- MVP 每个实例恰好服务一个家庭，但数据模型不得阻断未来多家庭扩展。
- 部署方默认具备联网构建条件；不提供离线镜像归档。
- 支持 Windows 和 Linux 主机，以及 Linux amd64、arm64 容器。
- 后端沿用 Spring Boot 与 MyBatis-Plus 技术线，前端使用 React 和 TypeScript。
- 上线级 MVP 必须覆盖安全、隐私、幂等、并发、备份、恢复和可访问性，而不只覆盖演示主流程。

本 PRD 符合大型变更拆分信号，但用户已明确选择继续单一 core-features change。原因是当前仓库没有可独立发布的基础能力，认证、家庭、任务、积分、奖励和部署共同构成首个可用版本；将其拆开会让中间 change 无法独立验收。tasks 仍按依赖阶段设置门禁，未来机构多家庭能力必须作为独立 change。

## Goals / Non-Goals

**Goals:**

- 建立可实现、可测试、可追踪的单家庭私有化 MVP 规范。
- 完成任务创建、分配、提交、审核、积分发放、奖品兑换和履约的业务闭环。
- 在重试、并发和部分失败下保持积分、库存、审核和兑换数据一致。
- 提供实例初始化、账号、配置、审计、备份恢复和升级所需的运维能力。
- 提供管理员、家长和孩子三端一致但差异化的响应式体验。
- 保留 family_id 和清晰模块边界，为未来多家庭 change 提供演进点。

**Non-Goals:**

- 学校、社区、机构或单实例多家庭模式。
- 厂商托管 SaaS、控制面、许可证云校验或默认遥测。
- 社交、排行榜、积分转赠、支付、物流、第三方登录和原生移动应用。
- 强依赖短信服务；短信认证仅为部署方可选配置。
- 数据库迁移原地降级；失败恢复依赖升级前备份。

## Decisions

### D1：单实例单家庭，同时保留家庭作用域

MVP 初始化时创建且只允许存在一个有效家庭。所有领域记录继续携带 family_id，并通过统一的请求上下文和数据访问约束注入当前家庭；UI 和 API 不提供家庭选择或跨家庭查询。

替代方案是删除 family_id，等未来再迁移。该方案当前更简单，但会让多家庭扩展成为高风险全表迁移，因此不采用。保留 family_id 的额外成本很低，且不等同于提前实现多租户。

### D2：统一 SPA 与模块化单体

前端采用一个 React SPA，以 /admin、/parent、/child 路由区分三端，共享认证、API 客户端、领域类型和基础组件，并按路由拆包。后端采用 Spring Boot 3 Maven 多模块单体，按 common、auth、system、business 和 application/启动边界组织。

三个独立前端会增加构建和部署成本；微服务会引入对单家庭实例无价值的网络与运维复杂度，因此均不采用。模块化单体保留清晰边界，同时只需部署一个应用服务。

### D3：统一项目目录

代码和运维资产固定为 server/、web/、deploy/、docs/ 四个顶层目录。server/ 内部承载 Maven 多模块，不再同时使用 cutegoals-server/ 与 server/ 两套表述。deploy/ 保存 Compose、反向代理、环境模板、健康检查及 Bash/PowerShell 管理脚本。

### D4：本地联网构建的多平台 Compose

分发包提供源码、Compose 文档、build.sh 和等价 PowerShell 脚本。部署方在 Windows 或 Linux 主机上联网构建 Linux amd64 或 arm64 镜像。默认 Compose 拓扑包含反向代理、应用、MySQL 和 Redis，使用命名卷和健康检查控制启动依赖。

不分发离线镜像归档，避免版本和架构组合导致包体积与发布流程膨胀。未来若出现隔离网络需求，应通过独立 change 引入离线制品与签名策略。

### D5：实例初始化与角色分离

首次启动生成短期、一次性初始化令牌。完成初始化的首位用户同时获得 INSTANCE_ADMIN 和 PARENT 角色，但两类权限保持分离：实例管理员负责配置、账号、审计、备份恢复、版本和健康；家长负责家庭业务。系统必须阻止移除最后一位有效家长或最后一位实例管理员。

原跨家庭 admin 仪表盘、家庭搜索和家庭数量统计从 MVP 删除。首位管理员凭据遗失时，由部署方在本机执行维护命令触发一次性恢复流程，不联系厂商云服务。

### D6：家长认证与浏览器会话

家长必备认证方式为手机号加密码；短信验证码仅在部署方配置兼容服务商后启用。密码和 PIN 使用自适应哈希，日志、错误和审计中屏蔽凭据、令牌及完整手机号。

浏览器会话使用短期访问令牌和轮换刷新令牌，并通过 Secure、HttpOnly、合适 SameSite 属性的 Cookie 传递；状态修改请求启用 CSRF 防护。登出、密码变更、账号停用和可疑会话处置必须撤销刷新令牌。生产环境必须使用 HTTPS，HTTP 只用于本机开发。

### D7：家长授权的孩子设备绑定

孩子不使用可全局枚举的账号。家长先在已认证会话中创建短期绑定授权，设备绑定当前实例家庭后展示孩子档案，孩子选择头像并输入家庭内唯一 PIN。绑定令牌只授予孩子端范围，可由家长撤销。

连续五次失败会锁定“设备绑定加孩子档案”组合十五分钟，并叠加 IP/设备速率限制。该模型比“仅凭四位 PIN 全局登录”可定位且更安全，也比为孩子分配手机号或复杂用户名更适合目标用户。

### D8：任务快照、迟交和审核历史


```

Full source: openspec/changes/core-features/design.md

## openspec/changes/core-features/tasks.md

- Source: openspec/changes/core-features/tasks.md
- Lines: 1-124
- SHA256: de018642fdb9e0ca18fd231f42b0c5b3c58a95851cd24921dff8db213a950a07

[TRUNCATED]

```md
## 1. 工程基线与模块边界

- [ ] 1.1 [design] 建立 server/ Maven 聚合工程及 common、auth、system、business、application 模块；验证：模块依赖图与 Maven 编译通过。
- [ ] 1.2 [design] 建立 web/ React 18 + TypeScript + Vite 工程并配置 lint、单元测试和路径别名；验证：前端检查、测试和生产构建通过。
- [ ] 1.3 [web-app] 配置 /admin、/parent、/child 路由、懒加载和角色级路由守卫；验证：三角色路由集成测试通过。
- [ ] 1.4 [web-app] 建立共享 API 客户端、稳定错误码映射、Cookie 会话和 CSRF 请求支持；验证：客户端契约测试覆盖成功、401、403/404 和冲突错误。
- [ ] 1.5 [design] 建立统一配置分层、Asia/Shanghai 默认时区、结构化日志和敏感字段脱敏；验证：配置加载与日志脱敏测试通过。
- [ ] 1.6 [deployment-operations] 建立 deploy/ 目录、Compose 环境模板及 Bash/PowerShell 管理脚本入口；验证：脚本帮助与参数检查测试通过。
- [ ] 1.7 [design] 建立持续集成基线，串联后端、前端、OpenSpec 和容器检查；验证：干净环境完整流水线通过。

## 2. 数据库模型与迁移

- [ ] 2.1 [deployment-operations] 接入版本化数据库迁移并创建空库基线；验证：MySQL 8 空库可重复初始化且 schema 版本一致。
- [ ] 2.2 [auth] 创建用户、角色、用户角色、初始化令牌、会话、刷新令牌和登录限流表；验证：唯一约束和失效字段迁移测试通过。
- [ ] 2.3 [family] 创建单家庭、家庭成员、孩子档案、家长邀请和孩子设备绑定表；验证：单有效家庭、最后家长保护所需约束可验证。
- [ ] 2.4 [task-template][task-assignment] 创建模板、难度、周期规则、任务分配和任务快照表；验证：快照字段完整且周期发生键唯一。
- [ ] 2.5 [task-review] 创建提交尝试、审核记录和状态转换所需表；验证：历史不可变和同一提交唯一批准约束测试通过。
- [ ] 2.6 [points] 创建积分账户与不可变流水表；验证：业务引用唯一、退款原消费引用和余额版本约束测试通过。
- [ ] 2.7 [prize][blind-box][exchange] 创建奖品、奖池、奖品项、兑换、兑换快照和履约状态表；验证：权重、库存和退款唯一约束测试通过。
- [ ] 2.8 [instance-management] 创建实例配置与审计日志表并补齐查询索引；验证：迁移前后索引、外键和审计字段检查通过。

## 3. 初始化、认证与家庭成员

- [ ] 3.1 [auth] 实现首次启动一次性初始化令牌的生成、过期和单次消费；验证：重放和过期场景测试通过。
- [ ] 3.2 [auth][family] 在单事务内创建唯一家庭及首位 INSTANCE_ADMIN + PARENT 用户；验证：并发初始化仅一个请求成功，失败无残留数据。
- [ ] 3.3 [auth] 实现手机号加密码登录、密码自适应哈希和不枚举账号的失败响应；验证：成功、错误凭据、停用和限流测试通过。
- [ ] 3.4 [auth] 定义可插拔短信认证接口并保持默认关闭；验证：未配置时明确不可用，测试适配器配置后流程可用。
- [ ] 3.5 [auth] 实现短期访问令牌、刷新令牌轮换、HttpOnly Cookie 和 CSRF 防护；验证：刷新重放、过期和 CSRF 集成测试通过。
- [ ] 3.6 [auth] 实现登出、密码变更、账号停用后的会话撤销；验证：已有刷新令牌立即失效。
- [ ] 3.7 [auth][deployment-operations] 实现部署方本地管理员恢复命令和一次性恢复流程；验证：无需厂商网络且旧凭据被撤销。
- [ ] 3.8 [family] 实现家庭资料查询和编辑；验证：唯一家庭边界、输入校验和审计测试通过。
- [ ] 3.9 [family] 实现家长邀请的创建、接受、拒绝、撤销、过期与幂等；验证：重复邀请和无效邀请码场景测试通过。
- [ ] 3.10 [family] 实现孩子档案创建、编辑、停用、导出和删除匿名化；验证：个人字段清除且历史引用保持一致。
- [ ] 3.11 [family][instance-management] 实现成员移除、退出与账号停用保护；验证：不能移除最后一位有效家长或实例管理员。
- [ ] 3.12 [auth][family] 实现孩子设备绑定授权、消费、撤销、档案选择、PIN 校验和五次失败锁定；验证：绑定重放、跨设备、锁定和解锁测试通过。

## 4. 任务模板与任务分配

- [ ] 4.1 [task-template] 实现模板创建、更新、分页查询、分类筛选和启停；验证：权限与字段校验测试通过。
- [ ] 4.2 [task-template] 实现可选周期规则及其格式、时区和边界校验；验证：无周期、每日、工作日和自定义星期场景通过。
- [ ] 4.3 [task-template] 实现难度等级、顺序和正整数奖励积分管理；验证：重复等级、非正积分和引用中删除场景通过。
- [ ] 4.4 [task-template] 实现模板软删除及历史引用保护；验证：现有分配和历史查询不受后续编辑影响。
- [ ] 4.5 [task-assignment] 实现单次任务分配并固化模板、难度、积分和截止时间快照；验证：模板修改不追溯已有分配。
- [ ] 4.6 [task-assignment] 实现批量分配、客户端幂等键和参数冲突检测；验证：重试不重复，复用键但参数不同返回冲突。
- [ ] 4.7 [task-assignment] 实现周期任务滚动生成和稳定发生键；验证：调度重跑、夏令时边界和并发执行不产生重复。
- [ ] 4.8 [task-assignment] 实现家庭默认迟交策略与单任务覆盖；验证：允许/禁止迟交及稳定错误码测试通过。
- [ ] 4.9 [task-assignment] 实现按日、月和孩子筛选的日历查询及逾期派生标记；验证：Asia/Shanghai 日期边界测试通过。
- [ ] 4.10 [task-assignment] 实现未批准任务的取消与审计；验证：已批准任务不可取消且历史记录保留。

## 5. 提交审核与积分账本

- [ ] 5.1 [task-review] 实现孩子提交任务并创建不可变提交尝试；验证：非本人、无效状态、迟交与重复请求测试通过。
- [ ] 5.2 [task-review] 实现家长驳回、必填原因和 REJECTED 可观察状态；验证：驳回后不发积分并保留审核历史。
- [ ] 5.3 [task-review] 实现驳回后的重新提交和新 attempt；验证：旧审核数据不被覆盖且状态回到 SUBMITTED。
- [ ] 5.4 [task-review][points] 实现批准与 EARN 流水同事务提交；验证：任一步失败整体回滚。
- [ ] 5.5 [task-review][points] 实现同一提交的幂等和并发批准保护；验证：多请求最多产生一笔 EARN。
- [ ] 5.6 [points] 实现积分账户投影与 EARN、SPEND、REFUND、ADJUST 不可变流水服务；验证：流水顺序和余额重算一致。
- [ ] 5.7 [points] 实现家长带必填原因的正负调整和审计；验证：负调整不得造成负余额。
- [ ] 5.8 [points] 实现孩子个人流水、家长家庭汇总、分页和类型/日期筛选；验证：角色数据范围测试通过。
- [ ] 5.9 [points] 实现流水业务引用唯一、退款引用原消费和单次退款约束；验证：重试、并发与重复退款测试通过。

## 6. 奖品、盲盒与兑换履约

- [ ] 6.1 [prize] 实现奖品创建、更新、分页查询、图片、正整数积分价格、非负库存和启停；验证：字段与权限测试通过。
- [ ] 6.2 [prize] 实现库存调整、软删除和历史引用保护；验证：被兑换引用的奖品不能物理删除。
- [ ] 6.3 [blind-box] 实现奖池创建、更新、成本和启停；验证：空奖池或无可抽项时不能对孩子开放。
- [ ] 6.4 [blind-box] 实现奖品项管理和正整数相对权重校验；验证：零值、负值、重复项和任意正权重总和测试通过。
- [ ] 6.5 [blind-box] 实现缺货/停用项过滤、有效概率归一化和孩子端透明查询；验证：概率合计和库存变化场景通过。
- [ ] 6.6 [blind-box] 实现可注入随机源的加权抽取；验证：确定性区间测试覆盖边界，统计测试声明样本与容差。
- [ ] 6.7 [exchange][points][prize] 实现明牌兑换的原子扣分、扣库存、快照和 SPEND 流水；验证：余额或库存不足时整体回滚。
- [ ] 6.8 [exchange][points][blind-box] 实现盲盒兑换、抽取、库存锁定、结果快照和 SPEND 流水；验证：并发不超卖且结果不可重抽。
- [ ] 6.9 [exchange] 实现兑换幂等键和同键参数冲突；验证：网络重试返回原兑换且不重复扣减。
- [ ] 6.10 [exchange] 实现家长兑现及 PENDING_FULFILLMENT → FULFILLED 转换；验证：重复兑现和无权限请求被拒绝。
- [ ] 6.11 [exchange][points] 实现家长取消、单次 REFUND 和库存恢复；验证：已兑现不可取消、重复取消无重复退款、孩子不能取消盲盒。

## 7. 实例管理、安全与审计

- [ ] 7.1 [instance-management] 实现实例初始化状态、版本、构建信息、依赖健康、备份运行状态与恢复演练结果查询；验证：未认证只暴露最小健康信息。
- [ ] 7.2 [instance-management] 实现受控系统配置的读取、校验、更新和审计；验证：未知键、越界值和失败回滚测试通过。
- [ ] 7.3 [instance-management][auth] 实现账号列表、角色展示、启停和会话撤销；验证：最后管理员保护和即时失效测试通过。

```

Full source: openspec/changes/core-features/tasks.md

## openspec/changes/core-features/specs/auth/spec.md

- Source: openspec/changes/core-features/specs/auth/spec.md
- Lines: 1-172
- SHA256: 935bd841ad07f2c96f753a0a7c7273bdf4bfe288f451eeb505961ceb8eac8f4b

[TRUNCATED]

```md
## ADDED Requirements

### Requirement: 首次启动身份初始化
实例处于未初始化状态时，系统 SHALL 仅接受部署方提供的有效一次性初始化令牌来创建首位用户。创建操作 MUST 原子地为该用户授予 `INSTANCE_ADMIN` 与 `PARENT` 两个相互独立的角色，并 MUST 在成功后立即使初始化令牌永久失效。初始化令牌、密码及其派生值 MUST NOT 出现在日志、审计详情或错误响应中。

#### Scenario: 使用有效令牌完成首次初始化
- **WHEN** 未初始化实例收到有效的一次性初始化令牌、合法手机号和符合策略的密码
- **THEN** 系统创建且仅创建首位用户，并为其授予 `INSTANCE_ADMIN` 与 `PARENT`
- **AND** 系统将实例标记为已初始化并立即使该令牌失效

#### Scenario: 重复使用初始化令牌
- **WHEN** 客户端在实例已初始化后再次提交初始化请求，或提交已使用的初始化令牌
- **THEN** 系统拒绝请求并返回稳定错误码 `INITIALIZATION_NOT_ALLOWED`
- **AND** 系统 MUST NOT 创建额外用户、重复授予角色或返回令牌内容

#### Scenario: 并发初始化
- **WHEN** 多个请求并发提交同一个有效初始化令牌
- **THEN** 系统 SHALL 只允许一个请求成功，其余请求返回 `INITIALIZATION_NOT_ALLOWED`
- **AND** 系统最终只存在一个由初始化流程创建的首位用户

### Requirement: 家长手机号与密码登录
系统 SHALL 始终为有效家长账号提供手机号加密码登录，且该方式 MUST NOT 依赖短信服务商、外部厂商云服务或网络可达性。手机号在认证比较前 MUST 按实例配置的中国大陆手机号规则规范化。

#### Scenario: 使用正确密码登录
- **WHEN** 有效家长提交已登记手机号和正确密码
- **THEN** 系统认证该用户并签发其当前角色与家庭范围内的会话

#### Scenario: 密码认证失败不枚举账号
- **WHEN** 客户端提交不存在的手机号、错误密码或已停用账号的凭据
- **THEN** 系统返回相同 HTTP 状态、相同稳定错误码 `AUTHENTICATION_FAILED` 和等价响应结构
- **AND** 响应 MUST NOT 揭示手机号是否登记、账号是否停用或密码哪一部分不正确

#### Scenario: 短信服务不可用时使用密码
- **WHEN** 实例未配置短信服务商或短信服务暂时不可用
- **THEN** 有效家长仍可使用手机号和密码完成登录

### Requirement: 可选短信验证码登录
系统 MAY 在部署方完成短信服务商配置并显式启用后提供短信验证码登录；未完成配置时该能力 MUST 保持关闭，且 MUST NOT 成为初始化、登录、密码变更或管理员恢复的前置依赖。验证码及服务商密钥 MUST 被视为秘密。

#### Scenario: 未配置服务商时请求短信登录
- **WHEN** 客户端在短信服务商未完整配置或功能未启用时请求发送或校验验证码
- **THEN** 系统拒绝请求并返回稳定错误码 `SMS_LOGIN_NOT_CONFIGURED`
- **AND** 系统提示可使用手机号和密码登录，但 MUST NOT 返回任何配置秘密

#### Scenario: 已配置服务商后使用验证码登录
- **WHEN** 部署方已完整配置并启用短信登录，且有效家长提交未过期、未使用并与手机号匹配的验证码
- **THEN** 系统使验证码失效并签发与密码登录等价范围的会话

#### Scenario: 验证码请求受到限制
- **WHEN** 同一手机号或来源在限制窗口内超过配置的验证码发送或校验阈值
- **THEN** 系统返回 `RATE_LIMITED`，且 MUST NOT 继续调用短信服务商或泄露该手机号是否已登记

### Requirement: 密码与 PIN 凭据保护
系统 MUST 使用带独立随机盐、可配置成本且可随安全基线升级的自适应单向哈希存储成人密码和孩子 PIN。系统 MUST NOT 持久化、返回或记录明文密码、明文 PIN、完整哈希、初始化令牌、验证码、刷新令牌或设备绑定凭据。

#### Scenario: 保存新凭据
- **WHEN** 系统创建或重置成人密码或孩子 PIN
- **THEN** 系统仅持久化自适应哈希验证所需的非明文数据
- **AND** API 响应、应用日志和审计记录不包含该秘密或完整派生值

#### Scenario: 哈希参数需要升级
- **WHEN** 用户使用有效凭据登录且现有哈希参数低于当前安全基线
- **THEN** 系统 SHALL 在不暴露明文凭据的情况下使用当前参数重新哈希并替换旧值

#### Scenario: 请求因异常失败
- **WHEN** 包含密码、PIN、验证码或令牌的请求触发校验错误或服务异常
- **THEN** 系统记录经脱敏的错误上下文，并 MUST NOT 在日志、链路追踪或错误详情中记录秘密字段

### Requirement: 短期访问令牌与刷新令牌轮换
系统 SHALL 使用有限有效期的短期访问令牌和较长期刷新令牌建立会话。每次成功刷新 MUST 轮换刷新令牌并使旧刷新令牌不可再次使用；令牌 MUST 绑定账号、实例、授权范围和可撤销的会话标识。

#### Scenario: 使用有效刷新令牌
- **WHEN** 客户端提交有效、未撤销且未使用的刷新令牌
- **THEN** 系统签发新的短期访问令牌和新的刷新令牌
- **AND** 系统在同一原子操作中使旧刷新令牌失效

#### Scenario: 重用已轮换刷新令牌
- **WHEN** 客户端再次提交已被轮换替代的刷新令牌
- **THEN** 系统拒绝请求并返回 `REFRESH_TOKEN_REUSED`
- **AND** 系统撤销该令牌所属会话链并记录安全审计事件

```

Full source: openspec/changes/core-features/specs/auth/spec.md

## openspec/changes/core-features/specs/blind-box/spec.md

- Source: openspec/changes/core-features/specs/blind-box/spec.md
- Lines: 1-103
- SHA256: 741c06c4f6ee1ed29eddcde42916635594196368c119fdbe7afaaeb08f3d9095

[TRUNCATED]

```md
## ADDED Requirements

### Requirement: 盲盒奖池资料与生命周期
系统 SHALL 允许同家庭家长创建和更新盲盒奖池的名称、描述、正整数兑换成本 `cost_points`、启用状态及奖品项，且奖池配置 MUST 始终至少包含一个奖品项。奖池删除 MUST 采用软删除并停止展示和兑换；后续成本或配置更新 MUST 不得改写既有兑换中的奖池、成本、概率和抽取结果快照。

#### Scenario: 创建可用奖池
- **WHEN** 家长创建名称“惊喜宝箱”、`cost_points=30`、状态为启用且包含至少一个合法奖品项的奖池
- **THEN** 系统 MUST 在该家长所属家庭创建奖池并保存全部配置

#### Scenario: 拒绝非法兑换成本
- **WHEN** 家长创建或更新奖池时提交 `cost_points=0`、负数、小数或非数值内容
- **THEN** 系统 MUST 返回 HTTP 400 和错误码 `BLIND_BOX_INVALID_COST`，并保持原配置不变

#### Scenario: 拒绝空奖池
- **WHEN** 家长尝试创建、更新或启用不含任何奖品项的奖池
- **THEN** 系统 MUST 返回 HTTP 400 和错误码 `BLIND_BOX_EMPTY_POOL`，且不得创建或保存任何部分配置

#### Scenario: 删除已有兑换历史的奖池
- **WHEN** 家长删除一个已有盲盒兑换记录的奖池
- **THEN** 系统 MUST 软删除并停用奖池，且 MUST 保留历史记录中的奖池名称、成本、候选概率和抽取结果快照

### Requirement: 奖品项与相对权重约束
每个盲盒奖品项的 `weight` MUST 为正整数，且 SHALL 表示相对于当前有效候选项总权重的相对权重，不要求权重合计为 100。同一奖池 MUST 不得重复配置同一奖品，奖品 MUST 属于同一家庭且未被软删除；任何非法项均 MUST 使整次配置写入失败。

#### Scenario: 保存不合计为 100 的权重
- **WHEN** 家长为奖品 A 配置 `weight=2`、奖品 B 配置 `weight=3`
- **THEN** 系统 MUST 原样保存两个正整数相对权重，不得要求补足或换算为合计 100

#### Scenario: 拒绝零权重和全零配置
- **WHEN** 家长提交任一 `weight=0` 的奖品项或提交全部权重为 0 的配置
- **THEN** 系统 MUST 返回 HTTP 400 和错误码 `BLIND_BOX_INVALID_WEIGHT`，且不得保存任何部分变更

#### Scenario: 拒绝负数或非整数权重
- **WHEN** 家长提交负数、小数或非数值权重
- **THEN** 系统 MUST 返回 HTTP 400 和错误码 `BLIND_BOX_INVALID_WEIGHT`，且不得保存任何部分变更

#### Scenario: 拒绝重复奖品项
- **WHEN** 家长在同一奖池中第二次添加同一奖品
- **THEN** 系统 MUST 返回 HTTP 409 和错误码 `BLIND_BOX_DUPLICATE_PRIZE`，并保持奖池原配置不变

#### Scenario: 拒绝其他家庭奖品
- **WHEN** 家长向奖池添加不属于其家庭的奖品标识
- **THEN** 系统 MUST 返回 HTTP 404 和错误码 `PRIZE_NOT_FOUND`，且不得泄露其他家庭奖品信息

### Requirement: 当前有效候选集
系统 MUST 在展示概率和执行抽取前过滤奖品项，仅保留所属奖池已启用且未删除、奖品已启用且未删除、奖品库存大于 0、权重为正整数的当前有效项。奖池只有在至少存在一个当前有效项时 SHALL 对孩子展示并允许兑换；无有效项时 MUST 拒绝兑换且不得扣除积分。

#### Scenario: 过滤缺货与停用奖品
- **WHEN** 奖池含奖品 A（缺货）、奖品 B（停用）和奖品 C（启用且有库存）
- **THEN** 系统 MUST 仅将奖品 C 纳入孩子可见候选集和抽取集合

#### Scenario: 全部奖品不可用
- **WHEN** 启用奖池的所有奖品均缺货、停用或软删除
- **THEN** 系统 MUST 隐藏该奖池；直接调用兑换接口时 MUST 返回 HTTP 409 `BLIND_BOX_UNAVAILABLE`，且积分、库存、兑换记录和流水 MUST 保持不变

#### Scenario: 仅剩一个有效项
- **WHEN** 过滤后仅剩一个有效奖品项
- **THEN** 系统 MUST 展示该项为 100% 概率，并 MUST 在成功兑换时选择该项
### Requirement: 候选奖品与有效概率披露

孩子在确认兑换前 SHALL 能看到所有当前有效候选奖品及其归一化概率。每项概率 MUST 按“该项权重 ÷ 当前有效项权重总和”计算，响应 MUST 携带可识别该候选集合与成本的 `availability_version`；`availability_version` SHALL 是当前有效候选集合（奖品标识、权重、奖池成本）的确定性内容哈希，固定长度、字母数字，用于校验孩子确认时的候选集与兑换事务执行时的候选集是否一致。兑换请求 MUST 绑定孩子已确认的版本，版本过期时不得按未展示的新概率直接兑换。

#### Scenario: 展示归一化概率
- **WHEN** 当前有效候选只有权重为 2 的奖品 A 和权重为 3 的奖品 B
- **THEN** 系统 MUST 向孩子展示 A 为 40%、B 为 60%，并返回对应的 `availability_version`

#### Scenario: 缺货后重新归一化
- **WHEN** 原权重为 2 的奖品 A 缺货，而权重为 3 的奖品 B 仍有效
- **THEN** 系统 MUST 从候选中移除 A，并将 B 的当前有效概率重新归一化为 100%

#### Scenario: 使用过期概率版本兑换
- **WHEN** 孩子确认候选概率后，成本、权重、启用状态或有效候选集合发生变化，并使用旧 `availability_version` 提交兑换
- **THEN** 系统 MUST 返回 HTTP 409 和错误码 `BLIND_BOX_POOL_CHANGED`，同时返回最新候选与概率供重新确认，且不得扣积分或库存

### Requirement: 加权随机抽取
盲盒抽取 SHALL 仅在兑换事务内对已确认且再次校验为当前有效的候选集执行。每个有效项被选择的概率 MUST 等于其权重占有效项总权重的比例，结果 MUST 为集合中的恰好一个奖品；系统不得先选中无效项后以不透明方式替换结果。

#### Scenario: 相对权重决定分布
- **WHEN** 有效候选为奖品 A `weight=30` 和奖品 B `weight=70`
- **THEN** 系统 MUST 使 A 的理论选择概率为 30%、B 的理论选择概率为 70%，且任何单次结果 MUST 为 A 或 B

```

Full source: openspec/changes/core-features/specs/blind-box/spec.md

## openspec/changes/core-features/specs/deployment-operations/spec.md

- Source: openspec/changes/core-features/specs/deployment-operations/spec.md
- Lines: 1-176
- SHA256: 64ccdab532d0305851c9560bbc137a9f3e93a5e804ed87b190438ed2f2eb160b

[TRUNCATED]

```md
## ADDED Requirements

### Requirement: 单家庭私有化分发包
MVP SHALL 以“每个部署实例服务一个家庭”为隔离边界，并支持在中国大陆的 Windows 与 Linux 主机上通过 Docker Compose 私有化部署。分发包 MUST 提供 Compose 文件、部署文档、环境变量模板、Bash 构建脚本 `build.sh` 和 PowerShell 构建脚本 `build.ps1`（或保持同等入口的等价脚本）；默认流程 SHALL 联网获取依赖并本地构建，不要求附带离线容器镜像包。

#### Scenario: Linux 主机默认部署
- **WHEN** 运维人员在满足先决条件的 Linux 主机上复制环境模板、填写必填值并运行文档中的 Bash 构建和 Compose 启动命令
- **THEN** 系统 MUST 联网完成本地构建并启动单家庭实例，且文档中的健康验证 MUST 通过

#### Scenario: Windows 主机默认部署
- **WHEN** 运维人员在满足先决条件的 Windows 主机上使用 PowerShell 构建入口和 Docker Compose 启动命令
- **THEN** 系统 MUST 产生与 Linux 流程等价的应用版本、服务拓扑和持久化行为

#### Scenario: 默认构建时网络不可用
- **WHEN** 联网构建无法访问文档列明的镜像源或依赖源
- **THEN** 构建脚本 MUST 以非零状态退出并报告 `BUILD_DEPENDENCY_UNAVAILABLE` 及失败来源，不得宣称支持未提供的离线安装

#### Scenario: 环境模板与真实机密分离
- **WHEN** 用户检查分发包和构建产物
- **THEN** 分发包 MUST 包含仅有说明或占位值的环境模板，且 MUST 不包含任一部署实例的真实密码、令牌、私钥或家庭数据

### Requirement: 双架构 Linux 容器构建
应用容器目标平台 MUST 包含 `linux/amd64` 与 `linux/arm64`。Bash 和 PowerShell 构建入口 SHALL 接受或自动选择受支持的目标平台，并 MUST 对不受支持的平台给出明确失败；同一应用版本在两种架构上 MUST 暴露等价功能、版本和健康状态。

#### Scenario: 构建 amd64 镜像
- **WHEN** 运维人员在构建入口指定 `linux/amd64`
- **THEN** 构建流程 MUST 成功生成可由 amd64 Linux 容器运行时启动的该版本镜像

#### Scenario: 构建 arm64 镜像
- **WHEN** 运维人员在构建入口指定 `linux/arm64`
- **THEN** 构建流程 MUST 成功生成可由 arm64 Linux 容器运行时启动的该版本镜像

#### Scenario: 指定不支持的平台
- **WHEN** 运维人员指定不在支持清单中的容器平台
- **THEN** 构建脚本 MUST 在构建前以非零状态退出并报告 `UNSUPPORTED_PLATFORM` 及受支持平台列表

### Requirement: 默认 Compose 服务拓扑与持久化
默认 Compose 配置 MUST 包含反向代理、应用、MySQL 和 Redis 四类服务，并为数据库、Redis 持久数据和用户上传文件配置命名卷。每个关键服务 MUST 提供可执行健康检查；应用和反向代理的启动就绪依赖 MUST 基于下游健康状态而非仅基于容器启动顺序。默认对主机公开的业务入口 MUST 仅经过反向代理。

#### Scenario: 全新启动
- **WHEN** 运维人员使用合法环境配置在空卷上启动默认 Compose
- **THEN** MySQL 和 Redis MUST 先达到健康状态，应用 MUST 完成初始化和版本化迁移后达到健康状态，反向代理随后 MUST 提供业务入口

#### Scenario: 依赖服务未健康
- **WHEN** MySQL 或 Redis 容器已启动但健康检查失败
- **THEN** 应用 MUST 不得报告就绪，启动诊断 MUST 标识 `DEPENDENCY_UNHEALTHY`、失败服务和可执行的检查建议

#### Scenario: 重建容器保留数据
- **WHEN** 运维人员停止并重建应用、数据库、Redis 和反向代理容器但保留命名卷
- **THEN** 数据库业务记录和用户上传文件 MUST 保持可用，且不得因容器重建创建第二个家庭实例

#### Scenario: 仅代理暴露业务入口
- **WHEN** 使用默认生产 Compose 检查主机端口
- **THEN** MySQL、Redis 和应用内部端口 MUST 不向非本机网络直接公开，外部业务流量 MUST 通过反向代理

### Requirement: 参考容量与性能目标
默认 Compose 在参考最低环境 2 vCPU、4 GB 内存、10 万条业务记录和至少 20 个并发会话下 SHALL 保持核心业务可用。除文件上传、下载及外部短信调用外，核心 API 在稳定的代表性混合负载窗口内的 P95 响应时间 MUST 小于或等于 300 毫秒；性能报告 MUST 分别标识被排除的外部或大文件操作。

#### Scenario: 最低环境容量验证
- **WHEN** 在 2 vCPU、4 GB 内存的主机上加载 10 万条业务记录并维持 20 个已认证并发会话执行典型读取和写入流程
- **THEN** 系统 MUST 保持健康，且积分、库存、审核与兑换事务 MUST 继续满足一致性要求

#### Scenario: 核心 API P95 验收
- **WHEN** 参考数据与并发会话在预热后持续执行不少于 10 分钟的代表性核心 API 混合负载
- **THEN** 被测核心 API 的总体 P95 响应时间 MUST 小于或等于 300 毫秒，上传、下载和外部短信耗时 MUST 从该指标单独报告

#### Scenario: 数据量达到十万条后的分页
- **WHEN** 兑换、任务或流水历史累计达到 10 万条业务记录并查询靠后的分页
- **THEN** API MUST 返回有界分页结果，不得因一次请求加载全部历史而使应用失去健康状态

### Requirement: 生产传输与机密安全
生产模式 MUST 使用 HTTPS；明文 HTTP SHALL 仅允许绑定本机地址的开发模式，生产 HTTP 请求 MUST 重定向至 HTTPS 或被拒绝。系统 MUST 不提供跨安装通用的默认密码，生产启动 MUST 拒绝空值、示例值或已知默认机密。机密和环境值 MUST 不写入容器镜像层、客户端资源、健康响应或日志，运行时默认 MUST 禁用外部遥测。

#### Scenario: 生产环境未配置 HTTPS
- **WHEN** 生产模式缺少有效 HTTPS 配置或尝试仅以明文 HTTP 对外监听
- **THEN** 系统 MUST 拒绝进入就绪状态并报告 `TLS_REQUIRED`，不得回退为对外明文服务

#### Scenario: 本机开发使用 HTTP
- **WHEN** 开发模式将 HTTP 仅绑定到回环地址
- **THEN** 系统 SHALL 允许本机开发访问，并 MUST 在诊断中明确标识该模式不可用于生产

```

Full source: openspec/changes/core-features/specs/deployment-operations/spec.md

## openspec/changes/core-features/specs/exchange/spec.md

- Source: openspec/changes/core-features/specs/exchange/spec.md
- Lines: 1-143
- SHA256: fc441c7d07cb39166d10b3867dfc82ac595d61496046d5636c274b0d677521ae

[TRUNCATED]

```md
## ADDED Requirements

### Requirement: 直接兑换与盲盒兑换资格
系统 SHALL 仅允许已认证孩子使用自己的可用积分兑换本家庭的可用奖品或盲盒。直接兑换 MUST 使用奖品当前积分价格并扣减对应奖品 1 件库存；盲盒兑换 MUST 使用孩子已确认的当前成本与 `availability_version`，从当前有效候选集中抽取并扣减选中奖品 1 件库存。

#### Scenario: 成功直接兑换
- **WHEN** 孩子有 100 可用积分，并兑换价格为 50、已启用且库存大于 0 的本家庭奖品
- **THEN** 系统 MUST 原子扣除 50 积分和 1 件库存，创建状态为 `PENDING_FULFILLMENT` 的直接兑换并返回其唯一标识

#### Scenario: 成功兑换盲盒
- **WHEN** 孩子有 100 可用积分，并用当前 `availability_version` 兑换成本为 30 且至少有一个有效候选项的本家庭盲盒
- **THEN** 系统 MUST 原子扣除 30 积分、抽取一个有效奖品、扣减该奖品 1 件库存，并创建状态为 `PENDING_FULFILLMENT` 的盲盒兑换
#### Scenario: 积分不足

- **WHEN** 孩子可用积分少于兑换事务中的实际成本
- **THEN** 系统 MUST 返回 HTTP 409 和错误码 `POINTS_INSUFFICIENT_BALANCE`，且积分、库存、兑换记录和流水 MUST 全部保持不变

#### Scenario: 非孩子发起兑换
- **WHEN** 家长或实例管理员尝试发起直接兑换或盲盒兑换
- **THEN** 系统 MUST 返回 HTTP 403 和错误码 `FORBIDDEN`，且不得产生任何兑换副作用

#### Scenario: 兑换其他家庭资源
- **WHEN** 孩子提交不属于其家庭的奖品或盲盒标识
- **THEN** 系统 MUST 返回 HTTP 404 和对应的 `PRIZE_NOT_FOUND` 或 `BLIND_BOX_NOT_FOUND`，且不得泄露其他家庭数据

### Requirement: 兑换原子创建与不可变快照
每次成功兑换 SHALL 在同一事务内完成积分余额扣减、选中奖品库存扣减、兑换记录创建和积分扣减流水创建。兑换记录 MUST 保存兑换类型、孩子、家庭、实际成本、奖品名称与图片快照；盲盒兑换还 MUST 保存奖池名称、已确认候选概率版本、各候选有效概率和最终抽取结果快照。所有快照和原始扣减流水 MUST 在奖品、奖池或成本后续更新、停用、删除后保持不可变。

#### Scenario: 直接兑换保存快照和流水
- **WHEN** 一次直接兑换成功提交
- **THEN** 系统 MUST 在同一事务中保存实际成本、奖品资料快照、库存扣减、兑换记录和一条关联该兑换的积分扣减流水

#### Scenario: 盲盒兑换保存抽取快照
- **WHEN** 一次盲盒兑换成功提交
- **THEN** 系统 MUST 保存兑换时的奖池成本、候选奖品及概率、选中奖品资料、选中时概率和抽取结果，且这些快照 MUST 与扣分和扣库存同时提交

#### Scenario: 后续修改不改写历史
- **WHEN** 家长在兑换后修改奖品名称、图片、`points_cost`、盲盒成本或权重
- **THEN** 系统 MUST 在历史查询中继续返回兑换创建时的原始快照和实际成本

### Requirement: 客户端幂等键
每个兑换创建请求 MUST 携带由客户端生成的非空幂等键。系统 SHALL 在孩子身份与兑换操作范围内绑定成功提交的键、规范化请求内容和兑换结果；相同键与相同请求的重试 MUST 返回同一兑换结果且不得重复扣分、扣库存、抽取或写流水，相同键用于不同请求 MUST 返回 `EXCHANGE_IDEMPOTENCY_CONFLICT`。当请求同时携带已绑定幂等键与过期 `availability_version` 时，系统 MUST 优先返回 `EXCHANGE_IDEMPOTENCY_CONFLICT`；仅当幂等键未绑定时，对过期版本 MUST 返回 `BLIND_BOX_POOL_CHANGED`。
#### Scenario: 缺少幂等键

- **WHEN** 客户端提交兑换但未提供有效幂等键
- **THEN** 系统 MUST 返回 HTTP 400 和错误码 `EXCHANGE_IDEMPOTENCY_KEY_REQUIRED`，且不得开始兑换事务

#### Scenario: 成功响应丢失后重试
- **WHEN** 首次兑换已经提交但客户端未收到响应，并以相同幂等键和相同请求内容重试
- **THEN** 系统 MUST 返回首次创建的同一兑换及抽取结果，积分和库存 MUST 不再变化
#### Scenario: 相同键承载不同请求

- **WHEN** 客户端使用已绑定的幂等键兑换不同奖品、不同盲盒或以不同确认版本提交
- **THEN** 系统 MUST 返回 HTTP 409 和错误码 `EXCHANGE_IDEMPOTENCY_CONFLICT`，且不得执行第二个请求

#### Scenario: 并发提交相同幂等键
- **WHEN** 两个内容相同且使用同一幂等键的兑换请求并发到达
- **THEN** 系统 MUST 仅创建一条兑换、一条原始扣减流水和一次库存扣减，并向两次调用返回同一逻辑结果

### Requirement: 事务失败与并发余额保护
积分检查与扣减、盲盒有效候选重检和抽取、库存扣减、兑换记录与快照、积分流水 MUST 作为单一原子事务提交；任一步骤失败 SHALL 使全部步骤回滚。并发兑换 MUST 不得使积分余额或库存为负，且内部事务失败 MUST 返回可诊断错误而不得伪装成成功。

#### Scenario: 库存扣减失败时回滚
- **WHEN** 积分扣减已执行但库存条件更新失败
- **THEN** 系统 MUST 回滚积分变更、抽取结果、兑换记录和流水，并返回 HTTP 409 `PRIZE_OUT_OF_STOCK` 或 `BLIND_BOX_POOL_CHANGED`

#### Scenario: 记录或流水写入失败时回滚
- **WHEN** 库存和积分步骤可执行但兑换记录、快照或积分流水写入失败
- **THEN** 系统 MUST 回滚全部变更并返回 HTTP 500 和错误码 `EXCHANGE_TRANSACTION_FAILED`

#### Scenario: 并发消耗有限积分
- **WHEN** 孩子的积分只够一次兑换，而两个使用不同幂等键的合法兑换并发到达
- **THEN** 系统 MUST 至多提交一次兑换，另一请求 MUST 返回 HTTP 409 `POINTS_INSUFFICIENT_BALANCE`，最终积分余额 MUST 不小于 0

### Requirement: 兑换状态与兑现权限
新兑换状态 MUST 为 `PENDING_FULFILLMENT`，且唯一允许的终态转换 SHALL 为 `PENDING_FULFILLMENT → FULFILLED` 或 `PENDING_FULFILLMENT → CANCELLED`。只有兑换所属家庭的家长 MUST 能兑现或在兑现前取消；孩子和实例管理员 MUST 不得执行终态转换。
#### Scenario: 家长兑现待处理兑换

- **WHEN** 同家庭家长兑现状态为 `PENDING_FULFILLMENT` 的兑换
- **THEN** 系统 MUST 将状态原子变更为 `FULFILLED` 并记录操作者与时间，且不得再次改变积分或库存

```

Full source: openspec/changes/core-features/specs/exchange/spec.md

## openspec/changes/core-features/specs/family/spec.md

- Source: openspec/changes/core-features/specs/family/spec.md
- Lines: 1-220
- SHA256: 66ac7ca5e6d8d4800e61550f485d43b1c0c5e9b3d411dca459a42a23eff2e674

[TRUNCATED]

```md
## ADDED Requirements

### Requirement: 私有化实例的唯一家庭
MVP 中，每个已初始化的私有化部署实例 SHALL 恰好包含一个家庭。每个成人账号和孩子档案 MUST 只归属于当前实例的该家庭，系统 MUST NOT 允许创建第二个家庭、加入另一个家庭或迁移到其他实例的家庭。

#### Scenario: 初始化建立唯一家庭
- **WHEN** 首位用户完成实例初始化并获得 `PARENT` 角色
- **THEN** 系统创建或确认当前实例唯一家庭，并将该用户加入为有效家长
- **AND** 系统确保实例中不存在第二个家庭

#### Scenario: 尝试创建或加入第二个家庭
- **WHEN** 当前实例的账号请求创建第二个家庭，或使用不属于当前实例的邀请加入其他家庭
- **THEN** 系统拒绝请求并返回稳定错误码 `SINGLE_FAMILY_ONLY`
- **AND** 系统不创建额外家庭或家庭成员关系

#### Scenario: 使用其他家庭标识访问数据
- **WHEN** 用户在请求中提交非当前实例唯一家庭的标识
- **THEN** 系统返回不泄露对象存在性的 `RESOURCE_NOT_FOUND`

### Requirement: 家庭信息查看与编辑
有效家长 SHALL 能查看和编辑当前家庭的允许字段。系统 MUST 对可编辑字段执行白名单、长度和格式校验，并 MUST 将返回内容限制为完成家庭功能所需的最小信息。

#### Scenario: 家长查看家庭信息
- **WHEN** 有效家长请求当前家庭信息
- **THEN** 系统返回家庭显示信息、当前有效成员的最小资料和家庭创建时间
- **AND** 系统不返回密码、PIN、令牌、邀请秘密、设备绑定秘密或完整凭据派生值

#### Scenario: 家长编辑家庭信息
- **WHEN** 有效家长提交通过校验的家庭名称、头像或其他允许字段
- **THEN** 系统更新当前家庭并返回更新后的允许字段
- **AND** 系统记录家庭信息变更审计事件

#### Scenario: 提交未允许或无效字段
- **WHEN** 家长提交不在白名单内、长度超限或格式无效的家庭字段
- **THEN** 系统拒绝整个更新并返回 `VALIDATION_FAILED`
- **AND** 家庭原有数据保持不变

### Requirement: 限时家长邀请
有效家长 SHALL 能通过限时邀请链接或邀请码邀请新的家长。邀请 MUST 绑定当前家庭、邀请人、预期成人手机号、明确的过期时间和单次使用状态；邀请秘密 MUST 具有足够随机性，并 MUST NOT 以明文写入日志或审计详情。

#### Scenario: 创建家长邀请
- **WHEN** 有效家长提交合法的预期成人手机号和允许的有效期
- **THEN** 系统创建一个仅用于当前家庭的待处理邀请，并返回邀请链接或邀请码
- **AND** 系统记录邀请人、目标手机号的脱敏标识和过期时间，但不记录邀请秘密

#### Scenario: 重复创建同一邀请
- **WHEN** 同一邀请人使用同一幂等键重复请求邀请同一手机号，或该手机号已有内容相同且仍有效的待处理邀请
- **THEN** 系统返回原邀请的相同业务结果，不创建重复邀请或发送重复通知

#### Scenario: 无权限账号创建邀请
- **WHEN** 孩子、已停用账号或不具有 `PARENT` 角色的账号请求创建邀请
- **THEN** 系统拒绝请求并返回 `FORBIDDEN`

### Requirement: 邀请接受、拒绝、撤销与过期
系统 SHALL 支持邀请接受、拒绝、撤销和自动过期。只有与邀请目标手机号匹配且已完成手机号加密码注册或认证的成人账号 MAY 接受邀请；成功接受 MUST 原子地创建唯一家长成员关系并消费邀请。

#### Scenario: 已有账号接受有效邀请
- **WHEN** 与目标手机号匹配的已认证成人账号提交有效且未使用的邀请链接或邀请码
- **THEN** 系统将该账号加入当前家庭并授予 `PARENT` 角色
- **AND** 系统将邀请标记为已接受且不可再次使用

#### Scenario: 新账号接受有效邀请
- **WHEN** 与目标手机号匹配的成人先完成手机号加密码注册，再提交有效且未使用的邀请
- **THEN** 系统在同一实例内创建该账号的唯一家庭成员关系并授予 `PARENT`
- **AND** 密码登录能力不依赖短信服务是否配置

#### Scenario: 受邀人拒绝邀请
- **WHEN** 与目标手机号匹配的成人拒绝一个待处理邀请
- **THEN** 系统将邀请标记为已拒绝，不创建账号角色或家庭成员关系

#### Scenario: 邀请人撤销邀请
- **WHEN** 邀请人或其他有效家长撤销一个待处理邀请
- **THEN** 系统立即使邀请链接或邀请码不可用，并将邀请标记为已撤销

#### Scenario: 使用已过期或已撤销邀请
- **WHEN** 客户端提交已过期或已撤销的邀请链接或邀请码
- **THEN** 系统拒绝请求并返回不区分邀请具体状态的 `INVITATION_NOT_AVAILABLE`
- **AND** 系统不创建家庭成员关系

#### Scenario: 其他手机号尝试接受邀请

```

Full source: openspec/changes/core-features/specs/family/spec.md

## openspec/changes/core-features/specs/instance-management/spec.md

- Source: openspec/changes/core-features/specs/instance-management/spec.md
- Lines: 1-200
- SHA256: 1e7f0520b47ff6dc5e312dd43c5c1eb1913d019b04983ce7abf4e89e5a3d2686

[TRUNCATED]

```md
## ADDED Requirements

### Requirement: 本地实例管理边界
实例管理 SHALL 仅管理当前私有化部署实例的初始化状态、允许的系统配置、账号启停、审计查询以及版本与健康信息。该能力 MUST NOT 提供家庭目录、家庭数量与注册趋势等运营统计、租户控制面或厂商云依赖；家庭业务数据只能由具备 `PARENT` 角色的用户通过家庭能力访问。
#### Scenario: 查看实例管理概览

- **WHEN** 有效 `INSTANCE_ADMIN` 请求实例管理概览
- **THEN** 系统仅返回初始化状态、脱敏配置摘要、账号状态摘要、审计入口、版本、健康信息、最近备份时间与状态、下次计划备份时间以及最近恢复演练结果
- **AND** 响应不包含家庭成员详情、孩子资料、任务、积分、奖励或兑换数据，也不包含备份路径或凭据

#### Scenario: 请求家庭运营管理能力
- **WHEN** `INSTANCE_ADMIN` 请求家庭目录、家庭运营统计或租户级管理操作
- **THEN** 系统拒绝请求并返回 `CAPABILITY_NOT_SUPPORTED`

#### Scenario: 实例离线运行
- **WHEN** 当前私有化实例无法连接任何厂商云服务
- **THEN** 初始化状态、密码登录、实例配置、账号启停、审计查询、版本和本地健康检查 SHALL 仍可工作

### Requirement: 实例初始化状态
系统 SHALL 维护 `UNINITIALIZED` 与 `INITIALIZED` 两种实例初始化状态。未认证状态查询 MUST 仅返回完成首次设置所需的最小状态，且 MUST NOT 返回初始化令牌、账号资料、配置秘密或内部诊断信息；初始化一旦成功，常规 API MUST NOT 将实例恢复为未初始化状态。

#### Scenario: 查询未初始化实例
- **WHEN** 客户端查询尚未完成首次设置的实例状态
- **THEN** 系统返回 `UNINITIALIZED` 和启动初始化界面所需的最小非秘密信息
- **AND** 响应不包含一次性初始化令牌或其派生值

#### Scenario: 查询已初始化实例
- **WHEN** 客户端查询已经完成首次设置的实例状态
- **THEN** 系统返回 `INITIALIZED`，且不返回首位用户手机号、角色详情或任何秘密

#### Scenario: 初始化成功后尝试重置状态
- **WHEN** 客户端通过常规 API 请求将已初始化实例改回 `UNINITIALIZED`
- **THEN** 系统拒绝请求并返回 `INITIALIZATION_NOT_ALLOWED`

### Requirement: 实例管理员角色隔离
只有状态有效且具备 `INSTANCE_ADMIN` 角色的账号 SHALL 能执行实例管理操作。首位用户 MAY 同时具有 `INSTANCE_ADMIN` 与 `PARENT`，但系统 MUST 对实例管理与家庭业务分别执行角色检查，不得因角色共存而合并权限。

#### Scenario: 首位用户访问实例配置
- **WHEN** 同时具有 `INSTANCE_ADMIN` 与 `PARENT` 的首位用户请求实例配置
- **THEN** 系统基于 `INSTANCE_ADMIN` 角色授权，并仅返回实例配置允许字段

#### Scenario: 普通家长访问实例管理
- **WHEN** 仅具有 `PARENT` 角色的有效用户请求配置、账号启停、审计查询或详细健康信息
- **THEN** 系统拒绝请求并返回 `FORBIDDEN`

#### Scenario: 仅有实例管理员角色时访问家庭数据
- **WHEN** 仅具有 `INSTANCE_ADMIN` 角色的用户请求家庭成员或家庭业务数据
- **THEN** 系统拒绝请求并返回 `FORBIDDEN`

### Requirement: 系统配置管理
有效 `INSTANCE_ADMIN` SHALL 能查看和更新明确列入白名单的本地系统配置。系统 MUST 校验配置类型、范围与组合约束，并 MUST 将服务商密钥、签名密钥、数据库凭据及其他秘密字段设为只写或脱敏显示；配置变更 MUST 原子应用并产生审计事件。

#### Scenario: 查看系统配置
- **WHEN** 有效 `INSTANCE_ADMIN` 请求系统配置
- **THEN** 系统返回允许配置项的当前非秘密值及秘密是否已配置的状态
- **AND** 秘密字段仅显示固定掩码或“已配置”，不返回明文、密文或可逆片段

#### Scenario: 更新有效配置
- **WHEN** 有效 `INSTANCE_ADMIN` 提交通过校验的白名单配置变更
- **THEN** 系统原子保存并按配置约定生效
- **AND** 系统记录操作者、时间、结果和变更字段名，但不记录秘密的新旧值

#### Scenario: 更新无效或未知配置
- **WHEN** 请求包含未知配置项、无效类型、越界值或不完整的关联配置
- **THEN** 系统拒绝整个更新并返回 `CONFIGURATION_INVALID`
- **AND** 所有现有配置保持不变

#### Scenario: 短信配置不完整
- **WHEN** 短信服务商所需配置缺失、无效或未显式启用
- **THEN** 系统将短信登录能力保持为关闭状态
- **AND** 手机号加密码登录及本地管理员恢复不受影响

### Requirement: 当前实例账号启停
有效 `INSTANCE_ADMIN` SHALL 能按页查看当前实例账号的最小化状态，并能启用或停用账号。列表和操作响应 MUST 对手机号等敏感字段脱敏，且 MUST NOT 返回凭据、会话、家庭业务详情或孩子隐私资料。停用操作 MUST 原子撤销目标账号全部会话；重新启用 MUST NOT 恢复旧会话。

#### Scenario: 查看账号状态列表
- **WHEN** 有效 `INSTANCE_ADMIN` 请求当前实例账号状态列表
- **THEN** 系统返回分页的非秘密账号标识、脱敏手机号、角色、启停状态和必要时间信息
- **AND** 响应不包含孩子资料、家庭业务数据、密码哈希、PIN 哈希或令牌


```

Full source: openspec/changes/core-features/specs/instance-management/spec.md

## openspec/changes/core-features/specs/points/spec.md

- Source: openspec/changes/core-features/specs/points/spec.md
- Lines: 1-166
- SHA256: 5b3215f6cb04036fe2cc60fd5776769d31cc76ed589977b0c13d0bb03b11baf2

[TRUNCATED]

```md
## ADDED Requirements

### Requirement: 积分账户的家庭边界与角色权限
MVP 每个实例 MUST 仅服务一个家庭。系统 SHALL 在孩子加入家庭的同一事务中为其创建且仅创建一个积分账户，初始可用余额和累计获得积分 MUST 均为零。孩子 SHALL 只读查看自己的账户和流水，MUST NOT 直接创建、修改或删除任何积分记录；家长 SHALL 查看当前家庭全部孩子的余额、流水和汇总，并仅通过获授权的业务操作发起手工调整。系统 MUST 从已认证身份确定家庭和孩子，不得信任客户端传入的家庭标识。越权访问 MUST 返回 `POINTS_FORBIDDEN`，不可见账户 MUST 返回 `POINTS_ACCOUNT_NOT_FOUND` 且不得泄露其他家庭数据。

#### Scenario: 新孩子创建积分账户
- **WHEN** 家长成功把一个新孩子加入当前家庭
- **THEN** 系统在同一事务中创建唯一积分账户，其可用余额和累计获得积分均为 0

#### Scenario: 账户创建失败回滚孩子创建
- **WHEN** 孩子创建流程无法创建其唯一积分账户
- **THEN** 系统整体回滚孩子和账户创建，不留下无账户的孩子或孤立账户

#### Scenario: 孩子访问其他孩子账户
- **WHEN** 孩子请求查看同家庭另一孩子或不可见孩子的余额或流水
- **THEN** 系统返回错误码 `POINTS_FORBIDDEN`，且不返回任何账户或流水字段

### Requirement: 不可变流水是积分事实源
系统 MUST 以不可变积分流水作为账户事实源，并仅支持 `EARN`、`SPEND`、`REFUND` 和 `ADJUST` 四种类型。`EARN`、`SPEND` 和 `REFUND` 的金额 MUST 为 1 至 1000000000 的正整数；`ADJUST` 金额 MUST 为绝对值不超过 1000000000 的非零整数，其中正数增加余额、负数减少余额。`EARN` 和 `REFUND` MUST 增加余额，`SPEND` MUST 减少余额。每条流水 MUST 包含稳定标识、账户、类型、金额、变动后余额、发生时刻、操作者或系统来源、唯一业务引用和来源快照。金额或类型不合法 MUST 返回 `POINTS_INVALID_TRANSACTION`。

#### Scenario: 记录任务奖励
- **WHEN** 一个已批准任务以 3 分快照奖励产生积分
- **THEN** 系统创建金额为 3 的 `EARN` 流水，记录变动后余额和任务来源快照

#### Scenario: 记录兑换支出
- **WHEN** 获授权兑换流程成功使用 20 积分
- **THEN** 系统创建金额为 20 的 `SPEND` 流水并将账户余额减少 20

#### Scenario: 拒绝未知类型或非法金额
- **WHEN** 请求使用未知流水类型、零金额、小数金额或超过上限的金额
- **THEN** 系统返回错误码 `POINTS_INVALID_TRANSACTION`，且流水和余额均不变化

### Requirement: 流水与来源快照不可修改或删除
积分流水一旦成功提交 MUST NOT 被更新、覆盖或物理删除。来源快照 MUST 足以在相关业务对象之后改名、停用或逻辑删除时解释该笔变动：任务奖励 MUST 保存任务名称、难度和奖励积分快照，兑换支出 MUST 保存兑换项名称和成本快照，退款 MUST 保留原支出引用，手工调整 MUST 保存原因。错误记账的更正 MUST 通过新的合法 `ADJUST` 或 `REFUND` 流水表达，不得篡改原流水。

#### Scenario: 模板删除后查看奖励流水
- **WHEN** 家长查看一笔其任务模板已逻辑删除的 `EARN` 流水
- **THEN** 系统仍返回记账时保存的任务名称、难度、奖励积分和业务引用快照

#### Scenario: 尝试删除积分流水
- **WHEN** 任一角色请求删除或改写既有积分流水
- **THEN** 系统拒绝请求并返回错误码 `POINTS_LEDGER_IMMUTABLE`，原流水与余额保持不变

### Requirement: 唯一业务引用保证记账幂等
每条流水 MUST 具有在当前家庭内唯一的业务引用，业务引用 MUST 包含来源类型和来源标识。系统 MUST 保存该引用对应的账户、流水类型、金额和语义摘要。相同业务引用与相同语义的重试 MUST 返回首次流水且不得再次改变余额；相同业务引用被用于不同账户、类型、金额或语义 MUST 返回 `POINTS_REFERENCE_CONFLICT`。事务失败并整体回滚时 MUST 不得留下已占用引用或部分流水。

#### Scenario: 奖励记账因响应丢失而重试
- **WHEN** 审核流程以相同提交尝试引用和相同奖励金额重试已成功的 `EARN` 记账
- **THEN** 系统返回首次创建的同一流水，余额只增加一次

#### Scenario: 同一引用用于不同金额
- **WHEN** 已有业务引用对应 3 分 `EARN`，随后请求以同一引用记 5 分
- **THEN** 系统返回错误码 `POINTS_REFERENCE_CONFLICT`，且不新增流水或改变余额

#### Scenario: 失败事务后复用引用
- **WHEN** 首次记账在事务提交前失败并回滚，随后以相同业务引用重试合法请求
- **THEN** 系统允许重试成功并只产生一条最终流水

### Requirement: 余额投影不得为负且写入必须一致
账户可用余额 MUST 是按流水顺序计算的投影，并在任何已提交事务后保持为非负整数。所有积分写操作 MUST 使用行级串行化、乐观版本检查或等效一致性手段，在同一事务中校验前置条件、追加流水并更新余额投影；任何一步失败 MUST 整体回滚。系统 MUST NOT 仅修改余额而不写流水，也 MUST NOT 写入流水而不更新余额。余额不足 MUST 返回稳定错误码 `POINTS_INSUFFICIENT_BALANCE`，版本或一致性冲突 MUST 返回 `POINTS_ACCOUNT_CONFLICT` 或安全重试后给出确定结果。

#### Scenario: 并发兑换争用同一余额
- **WHEN** 账户余额为 5 且两个独立兑换并发各请求支出 4 分
- **THEN** 系统最多允许一个 `SPEND` 成功，另一个返回 `POINTS_INSUFFICIENT_BALANCE`，最终余额为 1 且仅有一条成功支出流水

#### Scenario: 流水写入后余额更新失败
- **WHEN** 事务已尝试追加流水但余额投影更新失败
- **THEN** 系统整体回滚，既不保留流水也不改变余额

#### Scenario: 检测过期账户版本
- **WHEN** 两个写操作基于同一账户旧版本且其中一个已先成功
- **THEN** 系统拒绝或安全重试另一个操作，不得丢失更新、产生负余额或产生无对应余额变化的流水

### Requirement: 任务批准仅按快照获得一次积分
只有任务审核成功批准 SHALL 创建任务奖励 `EARN` 流水。奖励金额 MUST 来自任务分配快照，唯一业务引用 MUST 指向被批准的提交尝试；模板或难度当前值 MUST NOT 改变该金额。同一事务 MUST 同时提交任务 `APPROVED` 状态、审核历史、`EARN` 流水和余额投影，同一提交尝试最多成功获得一次积分。

#### Scenario: 模板奖励修改后批准旧分配
- **WHEN** 分配快照奖励为 3 分而模板当前奖励已改为 5 分，家长批准该分配的当前提交
- **THEN** 系统创建唯一的 3 分 `EARN` 流水并增加余额 3 分


```

Full source: openspec/changes/core-features/specs/points/spec.md

## openspec/changes/core-features/specs/prize/spec.md

- Source: openspec/changes/core-features/specs/prize/spec.md
- Lines: 1-88
- SHA256: 3a3d0c365c62d3e91f89de2b9666636c60bb733dd181dd4b83b3c95ac6f9a3e2

[TRUNCATED]

```md
## ADDED Requirements

### Requirement: 奖品资料与数值边界
系统 SHALL 允许同一实例家庭内的家长创建奖品，并保存名称、描述、图片、正整数积分价格 `points_cost`、非负整数库存 `stock` 和启用状态。系统 MUST 拒绝空名称、非整数或小于 1 的 `points_cost`，以及非整数或小于 0 的 `stock`，且失败时不得创建部分数据。

#### Scenario: 创建完整奖品
- **WHEN** 家长提交名称“乐高积木”、描述、图片、`points_cost=50`、`stock=3` 且状态为启用的奖品
- **THEN** 系统 MUST 在该家长所属家庭内创建奖品，并返回全部已保存字段

#### Scenario: 创建无图片奖品
- **WHEN** 家长提交合法奖品但未提供图片
- **THEN** 系统 SHALL 创建奖品并返回可识别的默认占位图，不得影响后续更新图片

#### Scenario: 拒绝非法积分价格
- **WHEN** 家长创建或更新奖品时提交 `points_cost=0`、负数、小数或非数值内容
- **THEN** 系统 MUST 返回 HTTP 400 和错误码 `PRIZE_INVALID_POINTS_COST`，并保持原数据不变

#### Scenario: 拒绝非法库存
- **WHEN** 家长创建或更新奖品时提交负数、小数或非数值库存
- **THEN** 系统 MUST 返回 HTTP 400 和错误码 `PRIZE_INVALID_STOCK`，并保持原数据不变

#### Scenario: 非家长创建奖品
- **WHEN** 孩子或未认证用户尝试创建奖品
- **THEN** 系统 MUST 分别返回 HTTP 403 `FORBIDDEN` 或 HTTP 401 `UNAUTHENTICATED`，且不得创建奖品

### Requirement: 奖品更新与启停
同家庭家长 SHALL 能够更新奖品的名称、描述、图片、`points_cost`、`stock` 和启用状态。更新后的积分价格 MUST 仅用于后续兑换，已创建兑换记录中的价格和奖品快照 MUST 保持不变；停用奖品 MUST 立即停止面向孩子展示和新兑换。

#### Scenario: 更新积分价格
- **WHEN** 家长将奖品的 `points_cost` 从 50 更新为 40
- **THEN** 系统 MUST 保存新价格供后续兑换使用，并保持既有兑换记录中的成本快照为原值

#### Scenario: 停用奖品
- **WHEN** 家长停用一个仍有库存的奖品
- **THEN** 系统 MUST 保存停用状态，并从孩子可见列表和所有新兑换候选集中移除该奖品

#### Scenario: 重新启用有库存奖品
- **WHEN** 家长重新启用一个未删除且 `stock>0` 的奖品
- **THEN** 系统 SHALL 使其重新满足直接兑换的可见性条件，并允许盲盒按实时有效性重新纳入候选集

#### Scenario: 越权更新其他家庭奖品
- **WHEN** 家长提交不属于其家庭的奖品标识进行更新
- **THEN** 系统 MUST 返回 HTTP 404 和错误码 `PRIZE_NOT_FOUND`，且不得泄露或修改其他家庭数据

### Requirement: 奖品删除与历史完整性
奖品删除 SHALL 采用软删除并同时停用。被历史兑换引用的奖品、其兑换快照和积分流水 MUST 保留且可查询；软删除不得级联删除盲盒或兑换历史，后续兑换与候选计算 MUST 排除该奖品。

#### Scenario: 删除未被兑换的奖品
- **WHEN** 同家庭家长删除一个尚无兑换历史的奖品
- **THEN** 系统 MUST 将奖品标记为已删除和停用，并从孩子可见入口移除

#### Scenario: 删除被历史兑换引用的奖品
- **WHEN** 同家庭家长删除一个已有直接兑换或盲盒兑换记录的奖品
- **THEN** 系统 MUST 软删除并停用奖品，同时完整保留每条历史记录中的奖品名称、图片、成本和抽取结果快照

#### Scenario: 删除盲盒仍引用的奖品
- **WHEN** 家长软删除一个仍作为盲盒奖品项的奖品
- **THEN** 系统 MUST 保留盲盒配置关系供审计，但 MUST 将该奖品从盲盒当前有效候选集排除

### Requirement: 按角色列出奖品
系统 SHALL 将奖品查询限制在当前实例家庭。家长 MUST 能按启用、停用、缺货和已删除状态分页查询管理清单；孩子 MUST 仅能看到未删除、已启用且 `stock>0` 的奖品，响应不得包含其他家庭数据。

#### Scenario: 家长查询管理清单
- **WHEN** 家长按“停用”状态查询奖品并指定合法分页参数
- **THEN** 系统 SHALL 返回该家庭匹配条件的稳定分页结果，包括库存为 0 的奖品

#### Scenario: 孩子查询可兑换奖品
- **WHEN** 孩子请求奖品列表
- **THEN** 系统 MUST 仅返回其家庭内未删除、已启用且库存大于 0 的奖品

#### Scenario: 孩子直接访问不可用奖品
- **WHEN** 孩子请求已停用、已删除或库存为 0 的奖品详情
- **THEN** 系统 MUST 返回 HTTP 404 和错误码 `PRIZE_NOT_FOUND`，且不得暴露管理字段

### Requirement: 库存不变量与并发保护
奖品库存 SHALL 始终为非负整数。所有兑换扣减和取消恢复 MUST 使用受并发保护的原子库存变更；任何并发结果均不得造成超卖、负库存或重复恢复。

#### Scenario: 并发争抢最后一件库存
- **WHEN** 两个使用不同幂等键的合法兑换同时争抢 `stock=1` 的同一奖品
- **THEN** 系统 MUST 至多允许一个兑换扣减成功，另一个 MUST 返回 HTTP 409 `PRIZE_OUT_OF_STOCK`，最终库存 MUST 为 0

```

Full source: openspec/changes/core-features/specs/prize/spec.md

## openspec/changes/core-features/specs/task-assignment/spec.md

- Source: openspec/changes/core-features/specs/task-assignment/spec.md
- Lines: 1-144
- SHA256: 4c09515b41c276f909e8e97761c7daa349bfd1178cf3ba89bc5c19d30b46d210

[TRUNCATED]

```md
## ADDED Requirements

### Requirement: 分配的家庭边界与角色权限
MVP 每个实例 MUST 仅服务一个家庭。只有该家庭的家长角色 SHALL 创建、批量创建、周期生成、修改、取消和查询家庭内全部任务分配；孩子角色 SHALL 仅能查询分配给自己的任务。系统 MUST 从已认证身份确定家庭和孩子，不得信任客户端传入的家庭标识或用孩子标识扩大访问范围。越权操作 MUST 返回稳定错误码 `TASK_ASSIGNMENT_FORBIDDEN`；不存在或不属于当前家庭的分配 MUST 返回 `TASK_ASSIGNMENT_NOT_FOUND`，且不得泄露其他家庭数据。

#### Scenario: 家长为本家庭孩子分配任务
- **WHEN** 已认证家长为当前家庭内的有效孩子创建任务分配
- **THEN** 系统执行创建并记录创建人、创建时间和来源

#### Scenario: 孩子查询其他孩子的分配
- **WHEN** 孩子请求查询或修改不属于自己的任务分配
- **THEN** 系统拒绝请求并返回错误码 `TASK_ASSIGNMENT_FORBIDDEN`，且不返回该分配的任何字段

### Requirement: 人工单次分配与数据快照
家长 SHALL 能够使用当前启用且未删除的模板及其启用难度，为当前家庭内的有效孩子创建单次分配。请求 MUST 包含模板标识、难度标识、孩子标识、截止时间和幂等键；截止时间 MUST 是可解析的时间且不得早于请求被系统接收的时刻。创建时系统 MUST 原子固化模板标识与版本、模板名称、分类、说明、图标、难度标识与名称、奖励积分、目标孩子、截止时间、有效迟交策略和创建来源。分配初始审核状态 MUST 为 `PENDING`。模板、难度或家庭默认值后续变化 MUST NOT 改写该快照。

#### Scenario: 创建单次分配
- **WHEN** 家长以合法模板、启用难度、家庭内孩子、未来截止时间和新幂等键创建单次分配
- **THEN** 系统创建一条 `PENDING` 分配，返回分配标识、版本及完整快照

#### Scenario: 模板修改不影响既有分配
- **WHEN** 分配创建后家长修改模板名称或难度奖励积分
- **THEN** 既有分配继续显示创建时的模板名称与奖励积分，只有之后的新分配使用新值

#### Scenario: 分配输入不合法
- **WHEN** 请求引用已停用难度、已删除模板、其他家庭孩子、无法解析的截止时间或过去截止时间
- **THEN** 系统不创建分配，并分别返回稳定错误码 `TASK_ASSIGNMENT_DIFFICULTY_INACTIVE`、`TASK_ASSIGNMENT_TEMPLATE_INACTIVE`、`TASK_ASSIGNMENT_CHILD_NOT_FOUND` 或 `TASK_ASSIGNMENT_INVALID_DEADLINE`

### Requirement: 人工创建请求幂等
所有人工单次和批量创建请求 MUST 携带长度为 1 至 128 个字符的幂等键。系统 MUST 在家庭和操作类型范围内唯一约束幂等键，并保存请求语义摘要及成功响应。同一幂等键和相同语义请求的重试 MUST 返回首次成功结果且不得新增分配；同一幂等键被用于不同语义请求 MUST 返回稳定错误码 `TASK_ASSIGNMENT_IDEMPOTENCY_CONFLICT`。首次事务失败 MUST 不得占用可成功重试的幂等结果。

#### Scenario: 单次分配因超时重试
- **WHEN** 客户端以相同幂等键和相同请求内容重试一个已成功的单次分配请求
- **THEN** 系统返回首次创建的同一分配标识，且数据库中仅存在一条对应分配

#### Scenario: 幂等键复用于不同请求
- **WHEN** 客户端以已成功使用的幂等键改为另一孩子、模板、难度或截止时间再次请求
- **THEN** 系统返回错误码 `TASK_ASSIGNMENT_IDEMPOTENCY_CONFLICT`，且不创建任何新分配

#### Scenario: 失败事务后重试
- **WHEN** 首次请求在任何分配落库前整体回滚，随后客户端以相同幂等键重试合法请求
- **THEN** 系统允许重试成功并仅创建一次结果

### Requirement: 原子批量分配
家长 SHALL 能够为一个或多个家庭内孩子按本地日期范围批量创建分配。起止日期 MUST 使用实例时区解释且包含两端，开始日期不得晚于结束日期，单次范围最长 MUST 为 366 个本地日；每个生成项 MUST 具有明确截止本地时间并转换为可持久化时刻。批量请求 MUST 使用一个幂等键，且在任何项目验证或写入失败时整体回滚，不得返回未声明的部分成功。

#### Scenario: 按包含两端的日期范围批量分配
- **WHEN** 家长在 `Asia/Shanghai` 实例中为一个孩子提交 2026-04-08 至 2026-04-14 的每日批量分配
- **THEN** 系统原子创建 7 条分配，每条分别固化对应本地日期的截止时间和创建时模板快照

#### Scenario: 批量项目中存在无效孩子
- **WHEN** 批量请求包含一个有效孩子和一个不属于当前家庭的孩子
- **THEN** 系统返回错误码 `TASK_ASSIGNMENT_CHILD_NOT_FOUND`，且两个孩子均不创建任何分配

#### Scenario: 日期范围超过上限
- **WHEN** 家长提交超过 366 个本地日或开始日期晚于结束日期的批量范围
- **THEN** 系统返回错误码 `TASK_ASSIGNMENT_INVALID_DATE_RANGE`，且不创建任何分配

### Requirement: 周期生成必须确定且幂等
系统 SHALL 根据模板的可选周期规则为指定孩子、难度和本地日期范围生成分配。周期生成 MUST 使用实例时区，默认 `Asia/Shanghai`，并仅为规则命中的本地日期创建分配。每个周期实例 MUST 具有由家庭、孩子、模板和本地发生日期确定的唯一业务键；重复运行、任务调度重试或多个生成器并发执行 MUST 对同一业务键最多创建一条分配。周期生成时 MUST 固化当时有效的模板与难度快照；已停用或删除的模板和难度 MUST 不再生成新分配，并在结果中报告跳过原因。

#### Scenario: 按周期规则生成一周任务
- **WHEN** 家长为每日规则模板、一个孩子和 2026-04-08 至 2026-04-14 的范围触发周期生成
- **THEN** 系统为七个本地发生日期各创建一条分配，并返回创建数与跳过数

#### Scenario: 重跑相同周期范围
- **WHEN** 相同模板、孩子和日期范围的周期生成被重复执行
- **THEN** 系统返回既有发生项为已存在，且不创建任何重复分配

#### Scenario: 两个生成器并发创建同一发生项
- **WHEN** 两个执行者并发生成同一家庭、孩子、模板和本地发生日期
- **THEN** 唯一业务键只允许一个创建成功，另一个复用已存在结果，最终仅有一条分配

#### Scenario: 模板没有周期规则
- **WHEN** 家长对未配置周期规则的模板请求周期生成
- **THEN** 系统拒绝请求并返回错误码 `TASK_ASSIGNMENT_RECURRENCE_NOT_CONFIGURED`

### Requirement: 截止时间、逾期标记与迟交策略
所有时刻 MUST 以无歧义时刻保存，日历日期和未携带偏移量的本地时间 MUST 按实例时区解释，默认时区为 `Asia/Shanghai`。对未取消且当前状态不是 `APPROVED` 的分配，`overdue` MUST 在当前时刻严格晚于截止时间时派生为真；恰好等于截止时间时 MUST 为假。逾期 MUST 仅为派生标记，不得自动删除分配、改写 `PENDING`、`SUBMITTED` 或 `REJECTED` 状态，也不得自动发放或扣除积分。每条分配 MUST 在创建时从家庭默认值固化有效迟交策略 `ALLOW` 或 `REJECT`，家长 SHALL 能够对单条未批准分配显式覆盖该策略；家庭默认值后续变化 MUST 仅影响新分配。


```

Full source: openspec/changes/core-features/specs/task-assignment/spec.md

## openspec/changes/core-features/specs/task-review/spec.md

- Source: openspec/changes/core-features/specs/task-review/spec.md
- Lines: 1-163
- SHA256: 314a9e91ba8f6606abed6f5e8b6e5cb13e6a66e1526d6349f5e5744fc91b7899

[TRUNCATED]

```md
## ADDED Requirements

### Requirement: 审核生命周期、家庭边界与角色权限
每条未取消任务分配的当前审核状态 MUST 遵循 `PENDING → SUBMITTED → APPROVED / REJECTED`。孩子 SHALL 仅能提交分配给自己的任务；当前家庭的家长 SHALL 仅能审核该家庭孩子的提交。系统 MUST 从已认证身份确定家庭和孩子，不得信任客户端提交的家庭标识。越权操作 MUST 返回稳定错误码 `TASK_REVIEW_FORBIDDEN`；不存在或不属于当前家庭的资源 MUST 返回 `TASK_REVIEW_NOT_FOUND` 且不得泄露其他家庭数据。

#### Scenario: 孩子提交自己的任务
- **WHEN** 已认证孩子提交当前状态为 `PENDING` 且分配给自己的未取消任务
- **THEN** 系统接受提交并把当前状态转换为 `SUBMITTED`

#### Scenario: 孩子提交其他孩子的任务
- **WHEN** 孩子尝试提交分配给同家庭另一孩子或不可见孩子的任务
- **THEN** 系统返回错误码 `TASK_REVIEW_FORBIDDEN`，且不创建提交尝试或修改状态

#### Scenario: 家长审核本家庭提交
- **WHEN** 当前家庭家长审核该家庭内当前状态为 `SUBMITTED` 的任务
- **THEN** 系统按审核决定执行 `APPROVED` 或 `REJECTED` 转换并记录审核人

### Requirement: 每次提交形成不可覆盖的尝试
孩子 SHALL 能够为自己的 `PENDING` 任务创建首次提交尝试，并在任务被驳回且完成修改后创建后续提交尝试。每次成功提交 MUST 新建不可覆盖的尝试，包含单调递增的尝试序号、分配标识和版本、提交内容、提交时间、截止时间快照、是否迟交以及当时有效迟交策略。提交说明 MUST 最长为 2000 个字符，佐证引用 MUST 最多 10 个且每个最长 500 个字符；不合法输入 MUST 返回 `TASK_SUBMISSION_VALIDATION_FAILED`。提交成功 MUST NOT 发放积分。

#### Scenario: 首次提交
- **WHEN** 孩子以合法内容提交自己的 `PENDING` 任务
- **THEN** 系统创建序号为 1 的不可变提交尝试，将当前状态设为 `SUBMITTED`，且积分余额不变

#### Scenario: 提交内容不合法
- **WHEN** 孩子提交超过 2000 字符的说明或超过 10 个佐证引用
- **THEN** 系统返回错误码 `TASK_SUBMISSION_VALIDATION_FAILED`，且不创建尝试、不改变状态

#### Scenario: 重复提交当前已提交任务
- **WHEN** 孩子对当前状态为 `SUBMITTED` 的任务发起新的非幂等重试提交
- **THEN** 系统返回错误码 `TASK_REVIEW_INVALID_STATE`，且保留原提交尝试不变

### Requirement: 提交请求必须幂等
每次提交请求 MUST 携带长度为 1 至 128 个字符的客户端请求标识。系统 MUST 在孩子和提交操作范围内唯一约束该标识，并保存请求语义摘要。相同标识及相同内容的重试 MUST 返回首次创建的同一尝试；相同标识用于不同分配或不同内容 MUST 返回 `TASK_SUBMISSION_IDEMPOTENCY_CONFLICT`。失败并整体回滚的提交 MUST NOT 占用可成功重试的幂等结果。

#### Scenario: 提交响应丢失后重试
- **WHEN** 孩子以相同请求标识和相同内容重试已成功的提交
- **THEN** 系统返回首次创建的同一尝试和 `SUBMITTED` 状态，且不会新增第二次尝试

#### Scenario: 提交标识复用于不同内容
- **WHEN** 孩子以已使用的请求标识提交另一任务或修改后的内容
- **THEN** 系统返回错误码 `TASK_SUBMISSION_IDEMPOTENCY_CONFLICT`，且所有任务与尝试均保持不变

### Requirement: 截止边界与迟交拒绝
系统 MUST 使用分配中固化或经家长显式覆盖的有效迟交策略校验每次提交。提交时间严格晚于截止时间时 MUST 标记为迟交；提交时间恰好等于截止时间时 MUST 视为按时。有效策略为 `REJECT` 的迟交请求 MUST 返回稳定错误码 `TASK_SUBMISSION_LATE_NOT_ALLOWED`，不得创建尝试或改变审核状态；有效策略为 `ALLOW` 时 SHALL 接受并在尝试中记录迟交。时刻比较 MUST 基于无歧义时刻，默认实例时区 `Asia/Shanghai` 仅用于解释无偏移量本地输入和日期筛选。

#### Scenario: 截止时刻提交
- **WHEN** 系统记录的提交时刻恰好等于分配截止时间
- **THEN** 系统将该尝试标记为按时，并按正常提交规则处理

#### Scenario: 默认策略拒绝迟交
- **WHEN** 分配有效迟交策略为 `REJECT` 且提交时刻晚于截止时间
- **THEN** 系统返回错误码 `TASK_SUBMISSION_LATE_NOT_ALLOWED`，当前状态保持 `PENDING` 或 `REJECTED`，且不新增尝试

#### Scenario: 单条覆盖允许迟交
- **WHEN** 分配有效迟交策略已由家长覆盖为 `ALLOW` 且孩子在截止时间后提交
- **THEN** 系统创建标记为迟交的新尝试并将当前状态设为 `SUBMITTED`

### Requirement: 审核决定形成不可覆盖的历史
家长 SHALL 能够对当前 `SUBMITTED` 的明确尝试作出批准或驳回决定。每个成功决定 MUST 新建不可覆盖的审核记录，包含分配标识、尝试标识与序号、决定、审核人、审核时间和原因。驳回原因 MUST 去除首尾空白后长度为 1 至 1000 个字符；批准原因 MUST 可选且最长为 1000 个字符。缺失或不合法原因 MUST 返回 `TASK_REVIEW_REASON_REQUIRED` 或 `TASK_REVIEW_VALIDATION_FAILED`，且状态与历史不得变化。

#### Scenario: 批准当前提交
- **WHEN** 家长批准当前 `SUBMITTED` 尝试并提交合法的可选原因
- **THEN** 系统创建不可变批准记录并将当前状态转换为 `APPROVED`

#### Scenario: 驳回时提供原因
- **WHEN** 家长以非空且不超过 1000 字符的原因驳回当前 `SUBMITTED` 尝试
- **THEN** 系统创建不可变驳回记录并将当前状态转换为 `REJECTED`

#### Scenario: 驳回时缺少原因
- **WHEN** 家长驳回提交但原因缺失、为空或仅含空白
- **THEN** 系统返回错误码 `TASK_REVIEW_REASON_REQUIRED`，提交仍为 `SUBMITTED` 且不创建审核记录

### Requirement: 驳回保持可观察并允许修改后再提交
`REJECTED` MUST 作为当前状态保持可观察，系统 MUST NOT 在驳回后自动将任务重置为 `PENDING`。孩子 SHALL 能够修改提交内容并从 `REJECTED` 创建下一序号的提交尝试，使当前状态再次成为 `SUBMITTED`；此前被驳回的尝试、原因、审核人和时间 MUST 永久保留。新尝试 MUST 独立审核，批准新尝试不得覆盖旧驳回历史。

#### Scenario: 驳回后仍显示驳回状态
- **WHEN** 家长驳回一次提交且孩子尚未再次提交
- **THEN** 任务当前状态保持 `REJECTED`，家长和该孩子均可查看驳回原因及对应尝试
#### Scenario: 修改后再次提交

```

Full source: openspec/changes/core-features/specs/task-review/spec.md

## openspec/changes/core-features/specs/task-template/spec.md

- Source: openspec/changes/core-features/specs/task-template/spec.md
- Lines: 1-113
- SHA256: ed5f3e0cd0fe523c2ec32c9b4885f77922857ec09cfb3bc82726f998e5fce4ea

[TRUNCATED]

```md
## ADDED Requirements

### Requirement: 模板的家庭边界与操作权限
MVP 每个实例 MUST 仅服务一个家庭。只有该家庭的家长角色 SHALL 创建、修改、停用、恢复、删除和查询任务模板；孩子角色 MUST NOT 直接管理模板。系统 MUST 从已认证身份确定家庭，不得信任客户端提交的家庭标识。越权操作 MUST 返回稳定错误码 `TASK_TEMPLATE_FORBIDDEN`，访问不存在或不属于当前家庭的模板 MUST 返回 `TASK_TEMPLATE_NOT_FOUND`，且响应不得泄露其他家庭的数据。

#### Scenario: 家长管理本家庭模板
- **WHEN** 已认证家长创建或修改当前实例家庭内的任务模板
- **THEN** 系统执行操作并记录操作者、操作时间和变更类型

#### Scenario: 孩子尝试修改模板
- **WHEN** 孩子请求创建、修改、停用或删除任务模板
- **THEN** 系统拒绝请求并返回错误码 `TASK_TEMPLATE_FORBIDDEN`，且不产生任何模板变更

### Requirement: 创建任务模板与字段验证
家长 SHALL 能够创建任务模板。模板 MUST 包含去除首尾空白后长度为 1 至 100 个字符的名称、长度为 1 至 50 个字符的分类，以及至少一个启用的难度；说明和图标 MUST 为可选字段，说明最长 2000 个字符，图标标识最长 500 个字符。系统 MUST 拒绝空白名称、空白分类、超长字段、未知字段类型或不合法难度，并以稳定错误码 `TASK_TEMPLATE_VALIDATION_FAILED` 返回逐字段错误；验证失败 MUST NOT 创建部分模板或部分难度。

#### Scenario: 使用完整字段创建模板
- **WHEN** 家长提交合法的名称、分类、说明、图标、可选周期规则和两个合法难度
- **THEN** 系统在一个原子操作中创建模板及其难度，返回模板标识和当前版本

#### Scenario: 使用最小字段创建模板
- **WHEN** 家长提交合法名称、分类和一个合法难度，且未提交说明、图标或周期规则
- **THEN** 系统创建模板，并将说明、图标和周期规则保存为未设置

#### Scenario: 创建输入不合法
- **WHEN** 家长提交仅含空白的名称、超过长度上限的说明或奖励积分不合法的难度
- **THEN** 系统返回错误码 `TASK_TEMPLATE_VALIDATION_FAILED` 和对应字段错误，且模板与难度均不落库

### Requirement: 配置多个难度与正整数奖励
每个模板 MUST 至少保留一个启用难度，并 SHALL 支持最多 20 个难度。每个难度 MUST 具有在模板内稳定且唯一的标识、去除首尾空白后长度为 1 至 50 个字符的名称、唯一的展示顺序，以及 1 至 1000000 的正整数奖励积分。家长 SHALL 能够新增、修改、排序和停用难度；已被分配或审核历史引用的难度 MUST NOT 被物理删除，停用后 MUST NOT 用于新分配。

#### Scenario: 配置多个难度
- **WHEN** 家长为一个模板配置“简单 1 分”“普通 3 分”和“困难 5 分”，且名称、顺序与奖励积分均合法
- **THEN** 系统保存三个可独立选择的启用难度及其稳定标识

#### Scenario: 奖励积分不是正整数
- **WHEN** 家长提交零、负数、小数或超过 1000000 的奖励积分
- **THEN** 系统返回错误码 `TASK_TEMPLATE_VALIDATION_FAILED`，并且不保存该次难度变更

#### Scenario: 停用被历史引用的难度
- **WHEN** 家长停用一个已被任务分配历史引用的难度
- **THEN** 系统保留该难度及历史引用，禁止其用于新分配，并且既有分配快照不发生变化

#### Scenario: 尝试停用最后一个启用难度
- **WHEN** 家长请求停用模板中最后一个启用难度
- **THEN** 系统拒绝请求并返回错误码 `TASK_TEMPLATE_REQUIRES_ACTIVE_DIFFICULTY`

### Requirement: 可选周期规则与本地日历语义
模板的周期规则 MUST 可选；未配置周期规则的模板 SHALL 仍可用于人工单次或批量分配。MVP SHALL 支持 `DAILY`、`WEEKDAYS`、`WEEKENDS` 和 `CUSTOM_WEEKDAYS`；`CUSTOM_WEEKDAYS` MUST 包含至少一个且不重复的 ISO 星期值 1 至 7（1 为周一，7 为周日）。周期匹配 MUST 使用实例时区，实例默认时区 MUST 为 `Asia/Shanghai`；规则本身 MUST NOT 立即创建分配。未知规则类型、空的自定义星期集合或越界星期值 MUST 返回 `TASK_TEMPLATE_INVALID_RECURRENCE`。

#### Scenario: 不配置周期规则
- **WHEN** 家长创建未设置周期规则的模板
- **THEN** 系统保存该模板且不进行自动周期生成，但允许家长人工分配

#### Scenario: 自定义工作日规则
- **WHEN** 家长在 `Asia/Shanghai` 实例中配置 `CUSTOM_WEEKDAYS` 且星期集合为 1、3、5
- **THEN** 系统将该规则解释为每个本地周一、周三和周五，并保存规范化后的不重复星期集合

#### Scenario: 周期规则不合法
- **WHEN** 家长提交空的 `CUSTOM_WEEKDAYS` 星期集合或集合中包含 0 或 8
- **THEN** 系统拒绝请求并返回错误码 `TASK_TEMPLATE_INVALID_RECURRENCE`

### Requirement: 更新模板仅影响未来分配
家长 SHALL 能够更新未删除模板的名称、分类、说明、图标、周期规则和难度。每次更新 MUST 增加模板版本并保留审计记录。模板或难度的后续修改 MUST 仅影响修改后创建的新分配，不得改写既有分配中已固化的模板名称、难度、奖励积分、截止时间或其他快照字段。

#### Scenario: 更新名称和难度奖励
- **WHEN** 家长修改模板名称并将某难度奖励从 3 分改为 5 分
- **THEN** 系统增加模板版本，之后的新分配使用新名称和 5 分奖励，既有分配仍保留原名称和 3 分奖励

#### Scenario: 并发更新版本冲突
- **WHEN** 两个家长基于同一旧版本并发修改同一模板，且其中一个修改已先成功
- **THEN** 系统拒绝另一个过期版本的修改并返回错误码 `TASK_TEMPLATE_VERSION_CONFLICT`，不得静默覆盖已成功的修改

### Requirement: 停用与恢复模板
家长 SHALL 能够停用或恢复未删除模板。停用模板 MUST 保留全部数据和历史引用，MUST NOT 继续用于人工分配或周期生成；恢复后 SHALL 可用于新分配。对已停用模板创建新分配的请求 MUST 返回稳定错误码 `TASK_TEMPLATE_INACTIVE`。

#### Scenario: 停用模板
- **WHEN** 家长停用一个已有历史分配的模板
- **THEN** 系统保留模板与全部历史记录，并从可用于新分配的模板集合中排除该模板


```

Full source: openspec/changes/core-features/specs/task-template/spec.md

## openspec/changes/core-features/specs/web-app/spec.md

- Source: openspec/changes/core-features/specs/web-app/spec.md
- Lines: 1-138
- SHA256: 0a76ecddc115ff2609e0733b76abf5fbfaef6242bdf09b61075fca5a5918af66

[TRUNCATED]

```md
## ADDED Requirements

### Requirement: 单一 React SPA 与三角色入口
前端 SHALL 作为单一 React SPA 提供三个固定路由前缀：`/admin` 用于实例管理、`/parent` 用于家长操作、`/child` 用于孩子操作。各入口 MUST 共享认证状态但按路由拆分非首屏资源；反向代理 MUST 将受支持的深层链接回退至 SPA 入口，浏览器刷新不得产生代理层 404。

#### Scenario: 访问实例管理入口
- **WHEN** 已认证实例管理员打开 `/admin` 下的受支持页面
- **THEN** SPA MUST 呈现实例管理导航和管理员工作区，且不得加载家长或孩子的受限业务数据

#### Scenario: 访问家长入口
- **WHEN** 已认证家长打开 `/parent` 下的受支持页面
- **THEN** SPA MUST 呈现家长导航及本家庭的任务审核、奖品和兑换管理能力

#### Scenario: 访问孩子入口
- **WHEN** 已认证孩子打开 `/child` 下的受支持页面
- **THEN** SPA MUST 呈现孩子导航及本人任务、积分、奖品和盲盒能力

#### Scenario: 刷新深层链接
- **WHEN** 用户直接访问或刷新 `/parent/exchanges`、`/child/prizes` 等有效深层链接
- **THEN** 反向代理和 SPA MUST 恢复对应页面，不得返回静态文件 404 或跳回错误角色首页

#### Scenario: 未知前端路由
- **WHEN** 用户访问三个入口下不存在的页面
- **THEN** SPA MUST 呈现可返回当前角色首页的 404 状态页，且不得显示空白页面

### Requirement: 路由权限守卫与安全会话
所有受保护路由 SHALL 在呈现业务数据前验证认证状态和角色。未认证访问 MUST 转到登录流程；已认证但角色不匹配 MUST 呈现 HTTP 403 语义的拒绝状态并阻止数据请求。认证令牌 MUST 不得写入 `localStorage`、`sessionStorage`、IndexedDB、URL 或前端日志；生产会话 MUST 使用浏览器脚本不可读、仅 HTTPS 传输且具备跨站请求防护的安全机制。

#### Scenario: 未认证访问受保护路由
- **WHEN** 未认证用户打开 `/admin`、`/parent` 或 `/child` 下的受保护页面
- **THEN** 系统 MUST 导向登录流程，并在登录成功后仅恢复该用户获授权的目标页面

#### Scenario: 角色不匹配
- **WHEN** 孩子尝试访问 `/parent` 或 `/admin` 路由
- **THEN** 路由守卫 MUST 阻止页面和数据加载，呈现 403 状态并提供返回 `/child` 的安全入口

#### Scenario: 会话过期
- **WHEN** 用户停留在受保护页面期间会话失效，后续接口返回 HTTP 401 `UNAUTHENTICATED`
- **THEN** SPA MUST 清除内存中的敏感会话状态并导向登录流程，不得无限重试或继续展示可操作的旧数据

#### Scenario: 检查浏览器持久存储
- **WHEN** 用户登录、刷新页面并完成任意业务操作
- **THEN** 浏览器可由脚本读取的持久存储、地址栏和前端日志 MUST 不包含访问令牌或刷新令牌

### Requirement: 角色主题与移动优先布局
SPA SHALL 为三个入口提供可明显区分但一致可用的主题：管理员为清晰克制的实例管理主题，家长为温暖稳重的家庭管理主题，孩子为活泼友好的成长主题。所有入口 MUST 采用移动优先的响应式布局，在窄屏、横屏、平板和桌面宽度下不得横向溢出或遮挡主要操作。

#### Scenario: 角色主题自动应用
- **WHEN** 经授权的用户进入任一角色入口
- **THEN** SPA MUST 自动应用该入口的颜色、排版、图标和导航主题，且主题不得仅依赖颜色表达角色或状态

#### Scenario: 手机窄屏布局
- **WHEN** 视口宽度为 320 CSS 像素并显示任一主要业务页面
- **THEN** 页面 MUST 保持单列可读，主要操作和导航 MUST 可见可触达，且不得出现页面级横向滚动

#### Scenario: 平板与桌面布局
- **WHEN** 视口空间足以容纳多列内容
- **THEN** SPA SHALL 在不改变任务顺序和权限语义的前提下利用额外空间，并 MUST 保持正文行宽、焦点顺序和操作位置可理解

#### Scenario: 文字放大
- **WHEN** 浏览器文字缩放到 200%
- **THEN** 主要内容和操作 MUST 仍可阅读与执行，不得因截断、重叠或隐藏而丢失功能

### Requirement: 大触控目标与键盘可操作性
所有主要操作 SHALL 同时支持触摸和键盘。交互控件 MUST 具有可见名称、清晰焦点和符合语义的状态，主要按钮和图标按钮的可点击区域 MUST 至少为 44×44 CSS 像素；任何滑动、拖动或手势操作 MUST 提供等价的显式按钮。

#### Scenario: 仅键盘完成主要流程
- **WHEN** 用户仅使用 Tab、Shift+Tab、Enter、Space 和方向键操作登录、任务审核、奖品管理或兑换确认
- **THEN** SPA MUST 以可见且符合视觉顺序的焦点完成流程，不得要求触摸或指针手势

#### Scenario: 触摸主要按钮
- **WHEN** 用户在手机上操作提交、批准、拒绝、兑换、取消或兑现按钮
- **THEN** 每个主要目标 MUST 至少为 44×44 CSS 像素，并 MUST 与相邻危险操作保持可辨识间隔

#### Scenario: 滑动审核的按钮替代
- **WHEN** 家长查看一条待审核任务
- **THEN** 页面 SHALL 可提供左右滑动快捷操作，但 MUST 同时展示可由触摸和键盘使用的“通过”和“拒绝”按钮

#### Scenario: 手势不可用
- **WHEN** 浏览器、辅助技术或用户设置不支持滑动

```

Full source: openspec/changes/core-features/specs/web-app/spec.md
