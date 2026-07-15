# web-app Delta Specification

> 本 delta spec 记录 web-app 能力在 Ant Design Pro 基础设施迁移中的变更。所有原有 behavioral requirements 保持不变；本阶段仅变更构建工具和路由实现，不影响运行时业务行为。

## MODIFIED Requirements

### Requirement: 单一 React SPA 与三角色入口
前端 SHALL 作为单一 React SPA 提供三个固定路由前缀：`/admin` 用于实例管理、`/parent` 用于家长操作、`/child` 用于孩子操作。各入口 MUST 共享认证状态但按路由拆分非首屏资源；反向代理 MUST 将受支持的深层链接回退至 SPA 入口，浏览器刷新不得产生代理层 404。

**实现变更**：路由实现从 React Router DOM v7 迁移至 UmiJS 约定路由（`config/routes.ts`），构建工具从 Vite 5.4 迁移至 UmiJS 4。SPA 入口行为、三角色路由前缀、深层链接兼容性保持不变。

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
