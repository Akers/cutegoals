# Design: fix-admin-config-display

## 修复方案（唯一）

让 `AdminConfigPage` 按后端真实契约（配置项数组）渲染与提交，替代错误的扁平 map 假设。

### 数据契约（后端既有行为，不变）

`GET /api/admin/config` 响应 `data` 为数组，每项：

```ts
interface ConfigEntry {
  key: string;            // 配置键，如 "sms.api_key"
  type: string;           // "string" | "boolean" | "integer"
  description: string;    // 配置项说明
  masked: boolean;        // 是否秘密字段
  value: string | null;   // 当前值；秘密字段已配置时为 "***MASKED***"，未配置为 null
  configured: boolean;    // 是否已配置
}
```

`PUT /api/admin/config` 请求体为扁平 `Record<string, string>`（key→value），后端校验白名单与类型。

### 前端改动（`web/src/admin/pages/index.tsx` `AdminConfigPage`）

1. **类型与获取**：`useApi<ConfigEntry[]>('/admin/config')`。
2. **编辑状态**：`values: Record<string, string>` 以 `entry.key` 为键；`useEffect` 在 `data` 到达时初始化为 `{ [e.key]: e.value ?? '' }`，同时保留 `original` 快照用于变更比较。
3. **渲染**：遍历 `data` 数组（保持后端白名单顺序）：
   - 标签：`entry.key`；副文本：`entry.description`。
   - 输入框：`entry.masked ? type="password" : type="text"`；`value={values[entry.key] ?? ''}`。
4. **保存**：
   - 构造 payload：仅包含 `values[k] !== original[k]` 的键。秘密字段未改动时值仍为 `***MASKED***`，与原值相等，自然被排除，杜绝把掩码回写为真实秘密。
   - payload 为空 → 提示「没有需要保存的变更」，不发请求。
   - 检查 `put` 返回的 `error`：失败时提示错误信息，成功时提示「保存成功」并 `refetch()`。

### 回归测试（`web/src/__tests__/admin-config-page.test.tsx`）

用 vitest + @testing-library/react，mock `getClient()`：

1. **渲染契约**：mock GET 返回 3 个 ConfigEntry（含 1 个 masked）。断言页面显示配置键文本（如 `sms.api_key`）而非 `0/1/2`；输入框值为真实 value 而非 `[object Object]`；masked 项渲染 `type="password"` 输入框。
2. **保存契约**：修改一个非秘密项后点击保存，断言 PUT body 为 `{changed_key: new_value}` 且不包含未变更的 masked 键。

### 明确不做

- 不改后端任何代码（契约正确）。
- 不做 `admin-page-migration` spec 中的 ProForm 重建（属独立迁移工作，非本 bug）。
- 不引入 delta spec（未改变已有 spec 验收场景）。
