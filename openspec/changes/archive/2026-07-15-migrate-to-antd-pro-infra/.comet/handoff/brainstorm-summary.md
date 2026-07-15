# Brainstorm Summary

- Change: migrate-to-antd-pro-infra
- Date: 2026-07-15

## 确认的技术方案

将 CuteGoals 前端从 React 18 + Vite + Tailwind + 自建组件全量迁移至 Ant Design Pro 基础设施层：

**架构**：UmiJS 4 单 SPA → 三角色 ProLayout（侧边栏导航）→ 各角色独立 ConfigProvider（theme.token 映射 admin/parent/child 色系）→ antd/pro-components 直接替换自建组件。保留 AuthContext/RoleContext/ApiClient/hooks 全部基础设施。

**9 项关键决策**：
1. ProLayout side nav（侧边栏），原生响应式折叠
2. antd-style (CSS-in-JS) 替代 Tailwind，与 ConfigProvider 集成
3. 单入口 index.html，移除多 HTML 入口
4. UmiJS 默认 dist/ 输出，同步更新 Dockerfile
5. 直接 import antd 组件，不留 wrapper 层
6. UmiJS 约定路由 + wrappers AuthGuard
7. ConfigProvider theme.token 三套色系
8. 保留自建 ApiClient（CSRF/重试/幂等）
9. AuthContext + UmiJS access.ts 路由级权限

## 关键取舍与风险

- Tailwind → antd-style 大量 class 需逐页替换，但增量可控
- ProTable 语法差异大，需逐列映射
- 移除 React Router DOM 可能影响测试，先跑现有用例再适配
- ErrorBoundary 保留 React 外壳，fallback 用 antd Result

## 测试策略

- 单元：@testing-library/react 适配 antd
- 集成：路由守卫 + API 客户端回归
- 构建：tsc + build
- E2E：不修改

## Spec Patch

无
