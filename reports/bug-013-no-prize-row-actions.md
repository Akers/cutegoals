# Bug bug-013: 奖品列表行只有「编辑」按钮，无删除/启停按钮

| Field | Value |
|-------|-------|
| ID | bug-013 |
| Severity | Low UX |
| Module | parent/prizes（前端） |
| Status | open |
| Discovered | 2026-07-18 |
| Blocking Cases | P-017（奖品列表管理） |
| Evidence | reports/evidence/P-018/prizes-list.png |

## 复现步骤

1. 家长登录 → 导航到奖品管理页面 `/parent/prizes`（P-017）
2. 查看奖品列表的行内操作按钮

## 期望行为

- 奖品列表每行应包含三种操作按钮：「编辑」「**删除**」「**启用/停用**」
- 删除操作软删除（deleted=true），停用操作切换 enabled 状态

## 实际行为

- 每行只有「编辑」按钮
- 缺少「删除」按钮 — 无法通过 UI 删除奖品
- 缺少「启用/停用」切换 — 无法通过 UI 控制奖品上架/下架
- 后端 `DELETE /api/prizes/{id}` 和 `PUT /api/prizes/{id}/stock`（含 enable toggle）均工作正常

## 根因分析

- 文件（推测）：`web/src/parent/pages/prizes/index.tsx`
- 当前 columns 的操作列定义只包含「编辑」按钮，缺少「删除」和「启停」
- 与 bug-007（模板列表缺删除按钮）为同模式缺陷

## 修复方向

### 推荐方案
1. **添加「删除」按钮**：调 `DELETE /api/prizes/{id}`，带二次确认 Modal
2. **添加「启停」开关**：`<Switch checked={record.enabled} onChange={() => toggleEnabled(record)} />`
   - 调 `PUT /api/prizes/{id}/stock`（如果启停是独立的 API）或专门的 `PUT /api/prizes/{id}/enabled`
3. **统一视觉风格**：与 bug-007 的模板列表操作列保持一致

### 参考代码模式
```tsx
{
  title: '操作',
  key: 'action',
  render: (_, record) => (
    <Space>
      <Button onClick={() => handleEdit(record)}>编辑</Button>
      <Switch
        checked={record.enabled}
        onChange={() => handleToggleEnabled(record)}
        checkedChildren="启用"
        unCheckedChildren="停用"
      />
      <Popconfirm title="确定删除该奖品？" onConfirm={() => handleDelete(record)}>
        <Button danger>删除</Button>
      </Popconfirm>
    </Space>
  ),
}
```

## 影响范围

- **阻塞用例**：P-017（奖品管理 UI — 无法删除/启停）
- **关联模块**：
  - `web/src/parent/pages/prizes/index.tsx`

## 回归测试要点

1. **单 bug 回归**：奖品列表行出现「删除」和「启停」操作
2. **删除回归**：点击删除 → 确认 → 奖品从列表消失（DB 软删除）
3. **启停回归**：关闭开关 → 奖品在商城不可见（孩子端 C-005 不可兑换）
