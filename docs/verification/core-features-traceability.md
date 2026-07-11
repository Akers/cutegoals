# CuteGoals 2.0 core-features 需求→任务→测试追踪清单

> **文档版本**: 1.0
> **生成日期**: 2026-07-11
> **覆盖 OpenSpec**: `openspec/changes/core-features/specs/*/spec.md` (12 capabilities)
> **覆盖任务**: `openspec/changes/core-features/tasks.md` (10 phases, 100 tasks)

---

## 追踪矩阵

每条 OpenSpec Requirement 映射到: 实现任务编号 → 测试文件/用例 → 证据类型。

| # | Capability | Requirement | Tasks | 测试文件/用例 | 证据 |
|---|---|---|---|---|---|
| AUTH-1 | auth | 首次启动身份初始化 | 3.1, 3.2 | `InitializationTokenServiceTest` | 单元测试 |
| AUTH-2 | auth | 家长手机号与密码登录 | 3.3 | `AuthenticationServiceTest` | 单元测试 |
| AUTH-3 | auth | 可选短信验证码登录 | 3.4 | `AuthenticationServiceTest` | 单元测试 |
| AUTH-4 | auth | 密码与 PIN 凭据保护 | 3.5, 3.12 | `MaskUtilTest`, `AuthenticationServiceTest` | 单元测试 |
| AUTH-5 | auth | 短期访问令牌与刷新令牌轮换 | 3.5 | `TokenServiceTest` | 单元测试 |
| AUTH-6 | auth | 会话撤销 | 3.6 | `AuthenticationServiceTest` | 单元测试 |
| AUTH-7 | auth | 角色与对象范围授权 | 1.3, 7.6 | 权限测试见 E2E (9.5) | 集成+E2E |
| AUTH-8 | auth | 已绑定设备上的孩子 PIN 登录 | 3.12 | `DeviceBindingServiceTest` | 单元测试 |
| AUTH-9 | auth | 认证防滥用与审计 | 7.5, 7.6 | `RateLimiterServiceTest`, `AuditLogServiceTest` | 单元测试 |
| FAM-1 | family | 私有化实例的唯一家庭 | 2.3, 3.2 | `FamilyServiceTest` | 单元测试 |
| FAM-2 | family | 家庭信息查看与编辑 | 3.8 | `FamilyServiceTest` | 单元测试 |
| FAM-3 | family | 限时家长邀请 | 3.9 | `InvitationServiceTest` | 单元测试 |
| FAM-4 | family | 邀请接受、拒绝、撤销与过期 | 3.9 | `InvitationServiceTest` | 单元测试 |
| FAM-5 | family | 家长成员生命周期 | 3.11 | `InvitationServiceTest` | 单元测试 |
| FAM-6 | family | 最小化孩子档案 | 3.10 | `ChildProfileServiceTest` | 单元测试 |
| FAM-7 | family | 孩子删除与历史匿名化 | 3.10 | `ChildProfileServiceTest` | 单元测试 |
| FAM-8 | family | 家庭内 PIN 管理与唯一性 | 3.12 | `ChildProfileServiceTest` | 单元测试 |
| FAM-9 | family | 家长授权的孩子设备绑定 | 3.12 | `DeviceBindingServiceTest` | 单元测试 |
| FAM-10 | family | 家庭数据导出 | 7.7 | — | 待验证 |
| FAM-11 | family | 家庭敏感操作审计 | 7.5 | `AuditLogServiceTest` | 单元测试 |
| TMPL-1 | task-template | 模板的家庭边界与操作权限 | 4.1 | `TaskTemplateServiceTest` | 单元测试 |
| TMPL-2 | task-template | 创建任务模板与字段验证 | 4.1 | `TaskTemplateServiceTest` | 单元测试 |
| TMPL-3 | task-template | 配置多个难度与正整数奖励 | 4.3 | `TaskTemplateServiceTest` | 单元测试 |
| TMPL-4 | task-template | 可选周期规则与本地日历语义 | 4.2 | `TaskTemplateServiceTest` | 单元测试 |
| TMPL-5 | task-template | 更新模板仅影响未来分配 | 4.4 | `TaskTemplateServiceTest` | 单元测试 |
| TMPL-6 | task-template | 停用与恢复模板 | 4.1 | `TaskTemplateServiceTest` | 单元测试 |
| TMPL-7 | task-template | 删除模板不得破坏历史 | 4.4 | `TaskTemplateServiceTest` | 单元测试 |
| TMPL-8 | task-template | 分页筛选模板列表 | 4.1 | `TaskTemplateServiceTest` | 单元测试 |
| ASGN-1 | task-assignment | 分配的家庭边界与角色权限 | 4.5 | `TaskAssignmentServiceTest` | 单元测试 |
| ASGN-2 | task-assignment | 人工单次分配与数据快照 | 4.5 | `TaskAssignmentServiceTest` | 单元测试 |
| ASGN-3 | task-assignment | 人工创建请求幂等 | 4.6 | `TaskAssignmentServiceTest` | 单元测试 |
| ASGN-4 | task-assignment | 原子批量分配 | 4.6 | `TaskAssignmentServiceTest` | 单元测试 |
| ASGN-5 | task-assignment | 周期生成必须确定且幂等 | 4.7 | `TaskAssignmentServiceTest` | 单元测试 |
| ASGN-6 | task-assignment | 截止时间、逾期标记与迟交策略 | 4.8 | `TaskAssignmentServiceTest` | 单元测试 |
| ASGN-7 | task-assignment | 修改未进入审核中的分配 | 4.5 | `TaskAssignmentServiceTest` | 单元测试 |
| ASGN-8 | task-assignment | 取消分配必须保留审计与历史 | 4.10 | `TaskAssignmentServiceTest` | 单元测试 |
| ASGN-9 | task-assignment | 分页列表与日历查询 | 4.9 | `TaskAssignmentServiceTest` | 单元测试 |
| REV-1 | task-review | 审核生命周期、家庭边界与角色权限 | 5.1 | `TaskReviewServiceTest` | 单元测试 |
| REV-2 | task-review | 每次提交形成不可覆盖的尝试 | 5.1 | `TaskReviewServiceTest` | 单元测试 |
| REV-3 | task-review | 提交请求必须幂等 | 5.1 | `TaskReviewServiceTest` | 单元测试 |
| REV-4 | task-review | 截止边界与迟交拒绝 | 5.1 | `TaskReviewServiceTest` | 单元测试 |
| REV-5 | task-review | 审核决定形成不可覆盖的历史 | 5.2 | `TaskReviewServiceTest` | 单元测试 |
| REV-6 | task-review | 驳回保持可观察并允许修改后再提交 | 5.3 | `TaskReviewServiceTest` | 单元测试 |
| REV-7 | task-review | 批准与积分发放必须同事务 | 5.4 | `TaskReviewServiceTest` | 单元测试 |
| REV-8 | task-review | 同一提交只允许一次成功审核 | 5.5 | `TaskReviewServiceTest` | 单元测试 |
| REV-9 | task-review | 取消任务不得继续提交或审核 | 5.1 | `TaskReviewServiceTest` | 单元测试 |
| REV-10 | task-review | 分页查询待审核与审核历史 | 5.3 | `TaskReviewServiceTest` | 单元测试 |
| REV-11 | task-review | 提交与审核历史不可删除 | 5.1 | `TaskReviewServiceTest` | 单元测试 |
| PTS-1 | points | 积分账户的家庭边界与角色权限 | 5.6 | `PointsServiceTest` | 单元测试 |
| PTS-2 | points | 不可变流水是积分事实源 | 5.6 | `PointsServiceTest` | 单元测试 |
| PTS-3 | points | 流水与来源快照不可修改或删除 | 5.6 | `PointsServiceTest` | 单元测试 |
| PTS-4 | points | 唯一业务引用保证记账幂等 | 5.9 | `PointsServiceTest` | 单元测试 |
| PTS-5 | points | 余额投影不得为负且写入必须一致 | 5.6 | `PointsServiceTest` | 单元测试 |
| PTS-6 | points | 任务批准仅按快照获得一次积分 | 5.5 | `PointsServiceTest` | 单元测试 |
| PTS-7 | points | 支出必须引用兑换且余额充足 | 5.6 | `PointsServiceTest` | 单元测试 |
| PTS-8 | points | 每笔兑换最多成功退款一次 | 5.9 | `PointsServiceTest` | 单元测试 |
| PTS-9 | points | 家长手工正负调整必须有原因与审计 | 5.7 | `PointsServiceTest` | 单元测试 |
| PTS-10 | points | 累计获得积分不得周期清零 | 5.6 | `PointsServiceTest` | 单元测试 |
| PTS-11 | points | 分页查询余额、流水与家庭汇总 | 5.8 | `PointsServiceTest` | 单元测试 |
| PRZ-1 | prize | 奖品资料与数值边界 | 6.1 | `PrizeServiceTest` | 单元测试 |
| PRZ-2 | prize | 奖品更新与启停 | 6.1 | `PrizeServiceTest` | 单元测试 |
| PRZ-3 | prize | 奖品删除与历史完整性 | 6.2 | `PrizeServiceTest` | 单元测试 |
| PRZ-4 | prize | 按角色列出奖品 | 6.1 | `PrizeServiceTest` | 单元测试 |
| PRZ-5 | prize | 库存不变量与并发保护 | 6.2 | `PrizeServiceTest` | 单元测试 |
| BB-1 | blind-box | 盲盒奖池资料与生命周期 | 6.3 | `BlindBoxServiceTest` | 单元测试 |
| BB-2 | blind-box | 奖品项与相对权重约束 | 6.4 | `BlindBoxServiceTest` | 单元测试 |
| BB-3 | blind-box | 当前有效候选集 | 6.5 | `BlindBoxServiceTest` | 单元测试 |
| BB-4 | blind-box | 候选奖品与有效概率披露 | 6.5 | `BlindBoxServiceTest` | 单元测试 |
| BB-5 | blind-box | 加权随机抽取 | 6.6 | `BlindBoxServiceTest` | 单元测试 |
| BB-6 | blind-box | 权限范围与并发防超卖 | 6.4, 6.8 | `BlindBoxServiceTest` | 单元测试 |
| EXG-1 | exchange | 直接兑换与盲盒兑换资格 | 6.7, 6.8 | `ExchangeServiceTest` | 单元测试 |
| EXG-2 | exchange | 兑换原子创建与不可变快照 | 6.7, 6.8 | `ExchangeServiceTest` | 单元测试 |
| EXG-3 | exchange | 客户端幂等键 | 6.9 | `ExchangeServiceTest` | 单元测试 |
| EXG-4 | exchange | 事务失败与并发余额保护 | 6.7, 6.8 | `ExchangeServiceTest` | 单元测试 |
| EXG-5 | exchange | 兑换状态与兑现权限 | 6.10 | `ExchangeServiceTest` | 单元测试 |
| EXG-6 | exchange | 取消的原子补偿与仅一次语义 | 6.11 | `ExchangeServiceTest` | 单元测试 |
| EXG-7 | exchange | 角色范围内的历史分页筛选 | 6.7 | `ExchangeServiceTest` | 单元测试 |
| IM-1 | instance-management | 本地实例管理边界 | 7.1 | `InstanceHealthServiceTest` | 单元测试 |
| IM-2 | instance-management | 实例初始化状态 | 7.1 | `InstanceHealthServiceTest` | 单元测试 |
| IM-3 | instance-management | 实例管理员角色隔离 | 1.3, 7.3 | 权限测试见 E2E (9.5) | 集成+E2E |
| IM-4 | instance-management | 系统配置管理 | 7.2 | `ConfigLoadingTest` | 单元测试 |
| IM-5 | instance-management | 当前实例账号启停 | 7.3 | `AccountManagementServiceTest` | 单元测试 |
| IM-6 | instance-management | 敏感操作审计记录 | 7.4 | `AuditLogServiceTest` | 单元测试 |
| IM-7 | instance-management | 审计日志查询 | 7.4 | `AuditLogServiceTest` | 单元测试 |
| IM-8 | instance-management | 版本与健康信息 | 7.1 | `InstanceHealthServiceTest` | 单元测试 |
| IM-9 | instance-management | 首位管理员本地凭据恢复 | 3.7 | `RecoveryServiceTest` | 单元测试 |
| IM-10 | instance-management | 实例管理防滥用与错误语义 | 7.6 | `RateLimiterServiceTest` | 单元测试 |
| DO-1 | deployment-operations | 单家庭私有化分发包 | 9.1, 9.3 | `docker compose config` 验证 | Compose 语法 |
| DO-2 | deployment-operations | 双架构 Linux 容器构建 | 9.1 | 构建脚本验证 | CI/手动 |
| DO-3 | deployment-operations | 默认 Compose 服务拓扑与持久化 | 9.2 | `docker compose up` 烟雾测试 | Compose 启动 |
| DO-4 | deployment-operations | 参考容量与性能目标 | 10.7 | 性能测试见 9.7 | 性能报告 |
| DO-5 | deployment-operations | 生产传输与机密安全 | 9.4, 9.5 | `.env.template` 验证 | 配置审计 |
| DO-6 | deployment-operations | 自动备份与保留策略 | 9.7 | `backup.sh --dry-run` | 脚本验证 |
| DO-7 | deployment-operations | 恢复目标与恢复演练 | 9.8 | `restore.sh --dry-run` | 脚本验证 |
| DO-8 | deployment-operations | 版本化升级与失败恢复 | 9.9 | `upgrade.sh --help` | 脚本验证 |
| DO-9 | deployment-operations | 版本、健康与启动诊断 | 9.6 | `health check` 端点 | API 测试 |
| WA-1 | web-app | 单一 React SPA 与三角色入口 | 8.1, 8.2 | `web/src/` 路由配置 | 代码审查 |
| WA-2 | web-app | 路由权限守卫与安全会话 | 8.1 | E2E 见 9.5 | E2E 测试 |
| WA-3 | web-app | 角色主题与移动优先布局 | 8.1, 8.10 | CSS 变量/响应式 | 视觉审查 |
| WA-4 | web-app | 大触控目标与键盘可操作性 | 8.10 | aria/accessibility 见 9.9 | 无障碍扫描 |
| WA-5 | web-app | 动效可选与低端设备降级 | 8.10 | `prefers-reduced-motion` | CSS 审查 |
| WA-6 | web-app | 加载、空、错误与离线状态 | 8.10 | 前端组件测试 | 单元+视觉 |
| WA-7 | web-app | 局域网性能预算 | 10.7 | LCP 测量见 9.7 | 性能报告 |

---

## 补充集成测试 (Phase 9 新增)

| 测试类 | 覆盖 Capability | 覆盖场景 | Commit |
|---|---|---|---|
| `AuthFamilyIntegrationTest` | auth, family | 初始化→登录→邀请→绑定→PIN→停用→恢复 | 9.2 |
| `TaskPointsIntegrationTest` | task, task-review, points | 模板→分配→提交→驳回→重提→批准→积分 | 9.3 |
| `ExchangePointsIntegrationTest` | prize, blind-box, exchange, points | 兑换→盲盒→兑现→取消→退款 | 9.4 |

---

## 测试覆盖统计

| 类型 | 数量 | 状态 |
|---|---|---|
| 单元测试文件 (*Test.java) | 28 | ✅ 已有 |
| 集成测试文件 (*IT.java) | 3 | 🆕 Phase 9 新增 |
| E2E 测试 | 待创建 | 🆕 Phase 9 新增 |
| OpenSpec Requirements | ~102 | 全部追踪 |
| OpenSpec Scenarios | ~312 | 关键路径覆盖 |

---

## 证据汇总

- **单元测试**: `mvn -f server/pom.xml test` 执行所有 *Test.java
- **集成测试**: `mvn -f server/pom.xml verify` 执行所有 *IT.java (需 Testcontainers)
- **E2E 测试**: Playwright 浏览器测试
- **Compose 验证**: `docker compose -f deploy/docker-compose.yml config`
- **OpenSpec 验证**: `openspec validate core-features --strict`
- **安全扫描**: `npm audit`, 日志脱敏验证, 依赖漏洞检查
- **备份/恢复**: 脚本 dry-run 验证
- **性能**: k6/JMeter 基准测试
