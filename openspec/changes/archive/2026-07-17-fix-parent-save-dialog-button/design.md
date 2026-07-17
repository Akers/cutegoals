# Design: fix-parent-save-dialog-button

## 修复方案

对 `web/src/parent/pages/index.tsx` 中 4 个保存对话框应用同一改造模式。

### 1. Modal 属性改造

每个保存 Modal 增加：

```tsx
<Modal
  open={showModal}
  onCancel={() => setShowModal(false)}
  title={...}
  okText="保存"
  onOk={handleSave}
  confirmLoading={saving}
>
```

并删除 body 内的 `<Button onClick={handleSave} loading={saving}>保存</Button>`。

受控 `open` 下，`onOk` 不会自动关闭对话框：仅成功路径调用 `setShowModal(false)`，失败路径保持打开，天然满足"失败不关闭"。

### 2. 对话框内错误展示

每个对话框组件新增局部错误状态，在 Modal body 顶部渲染 Alert：

```tsx
const [saveError, setSaveError] = useState<string | null>(null);
// Modal body 顶部：
{saveError && <Alert message={saveError} type="error" showIcon style={{ marginBottom: 8 }} />}
```

打开对话框（openNew / openEdit / openNewChild）时同步清空 `saveError`。

- `ParentFamilyPage` 添加孩子：`handleSaveChild` 改用对话框局部 `childSaveError`（不再复用页面级 `actionError`，避免错误显示在对话框外）。
- `ParentTemplatesPage`：移除 `Modal.error(...)` 弹窗调用，改为 `setSaveError(...)`。

### 3. handleSave 统一结构

```tsx
const handleSave = async () => {
  setSaving(true);
  setSaveError(null);
  const res = editing
    ? await getClient().put(`/path/${editing.id}`, payload)
    : await getClient().post('/path', payload);
  setSaving(false);
  if (res.error) {
    setSaveError(res.error.message ?? '保存失败');
    return; // 对话框保持打开
  }
  setShowModal(false);
  message.success('保存成功');
  await refetch();
};
```

- `ParentChildrenPage.handleSave` 与 `ParentPrizesPage.handleSave` 补上缺失的 `res.error` 检查（原为静默吞错）。
- `message` 从 antd 静态导入（与 `web/src/admin/pages/index.tsx` 现有用法一致）。

### 4. 回归测试

新增 `web/src/__tests__/parent-save-dialogs.test.tsx`，mock `@shared/api/client`（参照 `admin-config-page.test.tsx` 的 `vi.hoisted` 模式），覆盖：

1. 打开对话框后点击底部"保存"按钮触发 API 提交（修复前失败：底部确定按钮无 handler）。
2. API 返回 `{ error }` 时对话框保持打开且框内显示错误信息（档案/奖品对话框修复前失败：静默关闭）。
3. API 成功时对话框关闭并提示保存成功。

### 兼容性

- 纯前端交互行为变更，不涉及 API 契约、数据库 schema 或 public API。
- 输入框 `id`（`#child-nickname`、`#tpl-title` 等）保持不变，不影响 e2e 选择器；e2e 若依赖 body 内"保存"按钮需改用底部按钮（检查 `e2e/` 确认无此依赖）。
