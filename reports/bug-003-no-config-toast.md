# Bug bug-003: 配置保存成功后无 message.success toast 反馈

| Field | Value |
|-------|-------|
| ID | bug-003 |
| Severity | Low UX |
| Module | admin/config（前端） |
| Status | open |
| Discovered | 2026-07-18 |
| Blocking Cases | — |
| Evidence | reports/evidence/A-003/config-saved.png |

## 复现步骤

1. 管理员登录后导航到 `/admin/config`（A-003）
2. 修改一项或多项配置项的值
3. 点击「保存配置」按钮
4. 观察保存后页面行为

## 期望行为

- 配置保存成功后，页面右上角或顶部出现 `message.success('配置已保存')` 的绿色 toast 提示
- 若保存失败，出现红色 `message.error(...)` 提示错误原因
- 用户能明确知晓操作结果

## 实际行为

- 点击保存后页面 URL 不变，**无任何 toast/notification 反馈**
- 刷新后配置值已持久化（数据正确保存了），但用户无法在操作时确认结果
- 界面看起来好像什么都没发生，用户可能会重复点击「保存」

## 根因分析

- 文件（推测）：`web/src/admin/pages/config/index.tsx`
- 表单提交成功回调中缺少 `message.success` 调用
- 当前代码结构可能类似：
  ```tsx
  const handleSubmit = async (values: ConfigFormValues) => {
    await updateConfig(values);
    // ❌ 缺少 message.success('配置已保存')
  };
  ```

## 修复方向

### 推荐方案
1. 在表单成功提交回调中添加 toast：
   ```tsx
   import { message } from 'antd';
   
   const handleSubmit = async (values: ConfigFormValues) => {
     await updateConfig(values);
     message.success('配置已保存');
   };
   ```
2. 添加错误处理：
   ```tsx
   try {
     await updateConfig(values);
     message.success('配置已保存');
   } catch (error) {
     message.error('配置保存失败: ' + (error as Error).message);
   }
   ```
3. 在 PUT /api/admin/config 成功响应（200）后触发 toast

### 替代方案
- 使用 antd `notification` API（更显眼）：
  ```tsx
  notification.success({ message: '配置已保存', duration: 3 });
  ```

## 影响范围

- **阻塞用例**：无
- **关联模块**：`web/src/admin/pages/config/index.tsx`

## 回归测试要点

1. **单 bug 回归**：修改一项配置后点击保存，确认出现绿色 toast「配置已保存」
2. **错误场景**：模拟网络断开后保存，确认出现红色错误提示
