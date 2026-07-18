# Bug bug-007: 模板列表行内只有「编辑」「停用」按钮，无「删除」按钮

| Field | Value |
|-------|-------|
| ID | bug-007 |
| Severity | Low UX |
| Module | parent/templates（前端） |
| Status | open |
| Discovered | 2026-07-18 |
| Blocking Cases | P-009（删除路径） |
| Evidence | reports/evidence/P-009/template-disabled.png |

## 复现步骤

1. 家长登录 → 导航到模板管理页面 `/parent/templates`
2. 查看任务模板列表的行内操作按钮
3. 找到已创建的模板「整理房间」

## 期望行为

- 模板列表每行应包含三个操作按钮：「编辑」「停用/启用」「**删除**」
- 删除操作为软删除（deleted=true），删除后模板不在主列表中显示

## 实际行为

- 行内只有「编辑」和「停用」两个按钮
- 缺少「删除」按钮，用户无法通过 UI 删除模板
- 后端 `DELETE /api/task-templates/{id}` API 正常工作（软删除 `deleted=true`）

## 根因分析

- 文件（推测）：`web/src/parent/pages/templates/index.tsx`（模板列表组件的 column 定义）
- 当前 columns 定义缺少一个「删除」操作列
- 类似于「编辑」按钮的实现方式：
  ```tsx
  {
    title: '操作',
    key: 'action',
    render: (_, record) => (
      <>
        <Button onClick={() => handleEdit(record)}>编辑</Button>
        <Button onClick={() => handleToggle(record)}>
          {record.enabled ? '停用' : '启用'}
        </Button>
        {/* ❌ 缺少删除按钮 */}
      </>
    ),
  }
  ```

## 修复方向

### 推荐方案
1. 在操作列添加「删除」按钮：
   ```tsx
   <Button danger onClick={() => handleDelete(record)}>删除</Button>
   ```
2. 添加二次确认 Modal（避免误操作）：
   ```tsx
   const handleDelete = (record: Template) => {
     Modal.confirm({
       title: '确认删除',
       content: `确定要删除模板「${record.name}」吗？此操作不可撤销。`,
       onOk: async () => {
         await deleteTemplate(record.id);
         message.success('模板已删除');
         refreshList();
       },
     });
   };
   ```
3. 调 `DELETE /api/task-templates/{id}` 后刷新列表

### 视觉风格参考
与 bug-013（奖品行缺删除按钮）相同的模式。建议统一实现删除确认 Modal 组件。

## 影响范围

- **阻塞用例**：P-009（模板启停与删除 — 删除路径无法通过 UI 完成）
- **关联模块**：
  - `web/src/parent/pages/templates/index.tsx` — 操作列定义
  - `web/src/parent/pages/prizes/index.tsx` — bug-013 同模式修复

## 回归测试要点

1. **单 bug 回归**：模板列表行出现「删除」按钮
2. **删除回归**：点击删除 → 确认 Modal → 确认后模板从列表消失
3. **DB 回归**：删除后 DB `task_template.deleted = true`
