# Proposal: fix-admin-config-display

## Why

管理端 `/admin/config` 系统配置页面无法正确显示配置：页面标签显示为数字索引 `0`~`9`，输入框内容全部显示为 `[object Object]`（见用户报告截图），配置查看与编辑功能实质不可用。

根因是 **前端对 API 响应结构的错误假设**：

1. 后端 `GET /api/admin/config`（`server/instance-management/.../InstanceConfigController.java:27-36`）按契约返回**配置项数组** `List<Map<String, Object>>`，每项形如 `{key, type, description, masked, value, configured}`（共 10 个白名单配置项，与截图 10 行吻合）。秘密字段（`sms.api_key`、`sms.api_secret`）的 `value` 为固定掩码 `***MASKED***`，并通过 `masked: true` 标识。
2. 前端 `AdminConfigPage`（`web/src/admin/pages/index.tsx:108-150`）却声明 `useApi<Record<string, string>>('/admin/config')`，把响应当成扁平 key→value map。
3. `Object.entries(data)` 作用于数组时产生 `[["0", {...}], ["1", {...}], ...]`：标签列渲染数字索引；`values[key]` 取到整个配置项对象，作为 `<Input value>` 时被 toString 为 `[object Object]`。
4. 连带问题：`key.toLowerCase().includes('secret')` 作用于 `"0"`,`"1"`… 永远为 false，秘密字段从未以 password 输入框展示；保存时把数组整体 PUT 回后端，后端按 `Map<String, Object>` 反序列化数组必然 400，保存同样失败。

`instance-management` spec 的「系统配置管理」Requirement（`openspec/specs/instance-management/spec.md:53-63`）要求「系统返回允许配置项的当前非秘密值及秘密是否已配置的状态」「秘密字段仅显示固定掩码」——后端实现正确，前端渲染偏离契约。

## What Changes

- 前端 `web/src/admin/pages/index.tsx` 的 `AdminConfigPage` 改为按真实契约消费数据：
  - 新增 `ConfigEntry` 接口（`key/type/description/masked/value/configured`），`useApi<ConfigEntry[]>('/admin/config')`。
  - 直接遍历数组渲染：标签显示 `entry.key`，副文本显示 `entry.description`；`entry.masked === true` 时使用 password 输入框（替代原先对索引名做字符串匹配的失效逻辑）。
  - 编辑状态 `values` 以 `entry.key` 为键，初值取 `entry.value ?? ''`。
  - 保存时仅提交**发生变更**的 key→value 扁平 map（与后端 `PUT` 契约一致）；未改动的掩码秘密字段（值仍为 `***MASKED***`）自然被排除，避免把掩码字符串回写为真实秘密；无变更时不发请求。
  - 保存结果补充成功/失败反馈（当前代码完全忽略 PUT 返回值）。
- 新增该页面的回归测试，用真实数组契约 mock API，断言渲染与保存行为。

## Capabilities

### New Capabilities

无。本次仅修复显示 bug，不引入新能力。

### Modified Capabilities

无。`instance-management` capability 的「系统配置管理」Requirement 验收场景（`spec.md:56-63`）约束的是后端 API 行为，本次修复不涉及后端改动；`admin-page-migration` 的「系统配置页面迁移」Requirement 要求页面展示配置项列表并支持编辑保存——本修复使页面行为恢复符合该验收语义，而非修改验收场景，无需 delta spec。

## Impact

- 受影响代码：
  - `web/src/admin/pages/index.tsx`（仅 `AdminConfigPage` 组件，约 108-150 行）
  - `web/src/__tests__/admin-config-page.test.tsx`（新增回归测试）
- 无 API 契约变更、无数据库 schema 变更、无依赖变更、无 breaking change。
- 风险：极低。改动局限于单个前端页面组件；后端契约保持不变。
