---
comet_change: migrate-parent-to-antd-pro
role: technical-design
canonical_spec: openspec
archived-with: 2026-07-15-migrate-parent-to-antd-pro
status: final
---

# Parent 页面 Ant Design Pro 迁移 — 深度技术设计

- Date: 2026-07-15
- Change: migrate-parent-to-antd-pro

## 1. 范围

| 页面 | 行数 | 主要组件 |
|------|------|---------|
| ParentHomePage | ~60 | 状态展示、快捷入口 |
| ParentFamilyPage | ~320 | 家庭信息 Card、邀请码 |
| ParentChildrenPage | ~120 | 孩子列表 Table |
| ParentTemplatesPage | ~200 | 任务模板 CRUD → Table + Modal |
| ParentTasksPage | ~245 | 任务管理 → Table + 批量分配 Modal |
| ParentReviewsPage | ~140 | 审核列表 Table |
| ParentPointsPage | ~110 | 积分查询 + 手动调整 Modal |
| ParentPrizesPage | ~120 | 奖品管理 → Table + Modal |
| ParentBlindBoxesPage | ~75 | 盲盒配置 → Form |
| ParentExchangesPage | ~95 | 兑换履约列表 Table |
| TaskTypeFilter | ~60 | 子组件 — 任务类型筛选 |
| TaskTypeConfigForms | ~386 | 子组件 — 任务模板表单 |

## 2. 组件映射

复用 Admin 已验证映射：

| 自建/Tailwind | antd 替代 |
|---------------|----------|
| `PageShell` / Layout wrapper | 移除（ProLayout 提供外层） |
| Tailwind `flex`/`grid`/`p-*`/`gap-*` | `Row`/`Col`/`Space` |
| `PageHeader` / 原生 `<h1>` | `Typography.Title level={4}` |
| `StatusBadge` | `Tag` + `statusLabel()` 中文映射 |
| `CardSection` | `Card` |
| `<table>` + 自建 Pagination | `Table` columns + dataSource + pagination |
| `<select>` | `Select` |
| `<input>` / `<textarea>` / `type="number"` | `Input` / `Input.TextArea` / `InputNumber` |
| 自建 `Modal` / `ConfirmModal` | `Modal` / `Modal.confirm` |
| `useToast().showToast()` | `App.useApp().message` |
| `<button>` | `Button`（type/loading/danger） |

## 3. 迁移策略

### 分批提交

| 批次 | 页面 | 预估 |
|------|------|------|
| 1 | HomePage + FamilyPage + ChildrenPage | 简单布局页 |
| 2 | TemplatesPage + TasksPage | 复杂 CRUD |
| 3 | ReviewsPage + PointsPage + PrizesPage | 列表+操作 |
| 4 | BlindBoxesPage + ExchangesPage + 子组件 | 收尾 |

每批提交后验证 tsc + tests。

### 重点页面处理

**ParentTasksPage（最复杂）**：
- 任务列表 Table（含选择框 rowSelection 用于批量分配）
- 批量分配 Modal（儿童选择 + 周期选择）
- `Select` 模式切换视图

**ParentTemplatesPage**：
- 模板列表 Table
- 新增/编辑 Modal（Form 表单）
- 子组件 TaskTypeConfigForms 同步替换

## 4. 风险

| 风险 | 缓解 |
|------|------|
| 分页功能缺失（Admin 已知 bug） | 所有 Table 添加 onChange + page state |
| 标签中文化遗漏 | 复用 statusLabel() 函数 |
| ParentTasksPage 批量分配复杂度高 | 保持核心逻辑不变，仅替换组件外壳 |

## 5. 测试

- 94 tests 保持通过
- 焦点：ParentTasksPage 批量分配、ParentTemplatesPage CRUD

