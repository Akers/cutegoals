---
comet_change: migrate-admin-to-antd-pro
role: technical-design
canonical_spec: openspec
---

# Admin 端页面 Ant Design Pro 迁移 — 深度技术设计

- Date: 2026-07-15
- Change: migrate-admin-to-antd-pro
- 依赖: migrate-to-antd-pro-infra (已完成并归档)

## 1. 上下文

基础设施层（UmiJS 4 + antd + ProLayout + ConfigProvider）已在 Change 1 完成迁移。AdminLayout 已配置 5 个侧边菜单项，路由和权限守卫已就位。当前 Admin 5 个页面函数集中在 `admin/pages/index.tsx`（346 行），已完成初步的 antd 组件导入替换，但仍残留：
- 冗余 `<Layout>` wrapper（ProLayout 已提供外层）
- 自建 `<PageHeader>`（应换为 antd Typography.Title）
- 自建 `<StatusBadge>`（应换为 antd Tag）
- 大量 Tailwind utility class（`flex`/`grid`/`gap-*`/`py-*` 等，Tailwind 已从构建中移除）
- 原生 `<table>` 标签（应换为 antd Table）
- 原生 `<input>` 标签（应换为 antd Input）

## 2. 架构

```
AdminLayout.tsx (ProLayout: side nav, breadcrumbs, avatar)
  └── routes.ts → wrappers: [AuthGuard]
        ├── /admin         → AdminOverviewPage
        ├── /admin/config  → AdminConfigPage
        ├── /admin/accounts→ AdminAccountsPage
        ├── /admin/audit   → AdminAuditPage
        └── /admin/health  → AdminHealthPage
```

5 页共享模式：
```
offline → Result status="warning"
loading → Spin
error   → Result status="error"
empty   → Empty
data    → 页面内容（antd 组件）
```

## 3. 迁移映射

### 3.1 框架级（每页适用）

| 当前 | 目标 |
|------|------|
| 所有页面包裹 `<Layout>...</Layout>` | 删除，AdminLayout ProLayout 已提供 |
| `<PageHeader title="..." />` | `<Typography.Title level={3}>` |
| `<StatusBadge status="..." />` | `<Tag color={colorMap[status]}>` |
| `<Spin className="flex justify-center py-12" />` | `<Spin />` (删除 className) |
| `<div className="flex items-center gap-2">` | `<Space>` |
| `<div className="grid grid-cols-1 md:grid-cols-2 gap-4">` | `<Row gutter={[16, 16]}><Col span={24} md={12}>` |
| `<p className="text-cg-text-muted">` | 保留（CSS variable class，可用） |
| `<div className="cg-card p-4">` | 保留或换为 `<Card>` |

### 3.2 页面级

**AdminOverviewPage（实例概览）**
- `<Card title="...">` 保留
- `<StatusBadge>` → `<Tag color=>`
- `<div className="flex items-center gap-2">` → `<Space>`
- `<div className="grid grid-cols-1 gap-4 md:grid-cols-2">` → `<Row><Col>`

**AdminConfigPage（系统配置）**
- 原生 `<input>` → `<Input>` （type="password" 映射到 `Input.Password`）
- `<label>` → `<Form.Item label="...">`
- `<div className="flex flex-col gap-4">` → `<Space direction="vertical" size="middle">`
- `<div className="cg-card p-4">` → `<Card>`
- `<Button onClick={handleSave}>` 保留

**AdminAccountsPage（账号管理）**
- 原生 `<table>` + `<thead>`/`<tbody>` → `<Table columns={columns} dataSource={data.content}>`
- `<StatusBadge>` → `<Tag>`（在 columns render 中）
- `<td className="px-4 py-3">` → Table 内置样式
- `onClick={() => toggle(...)}` → `<Button>` 保留

**AdminAuditPage（审计日志）**
- 原生 `<table>` → `<Table>`
- 5 列（时间/操作者/动作/结果/对象）转为 columns 定义

**AdminHealthPage（健康面板）**
- `<StatusBadge>` → `<Tag>`
- `<div className="grid grid-cols-1 gap-3">` → `<Space direction="vertical" size="middle">`
- `<div className="cg-card p-4">` → `<Card>`

## 4. StatusBadge → Tag 颜色映射

```ts
const statusColor: Record<string, string> = {
  completed: 'success',
  pending: 'default',
  cancelled: 'error',
  rejected: 'error',
  approved: 'success',
};
```

## 5. 文件变更

| 操作 | 文件 | 说明 |
|------|------|------|
| 修改 | `web/src/admin/pages/index.tsx` | 5 页面函数：Layout wrapper 移除 + 组件替换 |
| 修改 | `web/src/shared/components/index.tsx` | StatusBadge 映射优化为 antd Tag |

## 6. 风险

| 风险 | 缓解 |
|------|------|
| Table columns 定义遗漏字段 | 逐列比对原 table header，保持 5 列全量映射 |
| 删除 Layout 后页面无外层容器 | AdminLayout ProLayout 的 Outlet 已提供容器，确认每页顶层返回单个节点 |
| 自定义 CSS class (cg-card) 样式冲突 | 保留这些 class（基于 CSS variables），与 antd 无冲突 |

## 7. 测试

- 单元：更新 `admin/__tests__/` 中的渲染测试
- 类型：`tsc --noEmit`
- 构建：`npm run build`
- 回归：页面逻辑（API 调用/状态流转）不修改

## 8. 迁移步骤

1. 移除所有页面的 `<Layout>` wrapper
2. `<PageHeader>` → `<Typography.Title>`
3. `<StatusBadge>` → `<Tag>`
4. AdminOverviewPage: Tailwind → antd 布局组件
5. AdminConfigPage: 原生 input → antd Input + Form.Item
6. AdminAccountsPage: 原生 table → antd Table
7. AdminAuditPage: 原生 table → antd Table
8. AdminHealthPage: Tailwind → antd 布局组件
9. 验证：tsc + tests + build
