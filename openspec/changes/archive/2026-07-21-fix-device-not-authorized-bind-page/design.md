# 修复方案

## 方案：前端特殊处理 DEVICE_NOT_AUTHORIZED 错误码

在 `ChildBindPage.tsx` 的 `fetchChildren()` 和轮询回调中，对 `DEVICE_NOT_AUTHORIZED` 错误码做特殊处理：将其视为 **设备尚未授权的预期状态**，清除 error 状态并设空 children 列表，使页面进入 children.length === 0 的绑定指引分支。

### 改动文件

- `web/src/child/pages/ChildBindPage.tsx`（仅 1 个文件）

### 改动详情

1. **`fetchChildren` 函数**（第 41-43 行）：在 `response.error` 判断分支中，先检查 `error_code === 'DEVICE_NOT_AUTHORIZED'`，若匹配则 `setError(null)` + `setChildren([])`

2. **轮询回调**（第 54-62 行）：轮询已忽略错误（仅在 `!response.error` 时更新 children），无需额外修改，但为明确语义可加注释

### 为何不修改后端

- `DEVICE_NOT_AUTHORIZED` 在后端语义准确（设备确实未授权），保留该错误码可让其他需要授权校验的端点（如 `childLogin`）继续正确工作
- 修改后端 `getChildrenForDevice` 返回空列表会改变 API 契约，且可能被误解为"设备已授权但无孩子"
- 前端做特殊处理更聚焦：**child/bind 页面本意就是处理未授权设备的场景**
