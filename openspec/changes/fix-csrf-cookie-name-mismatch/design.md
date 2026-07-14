# Design: fix-csrf-cookie-name-mismatch

## 方案
1. `getCsrfToken()` 将 cookie 匹配正则从 `XSRF-TOKEN=` 改为 `csrf_token=`，与后端 `AuthConstants.COOKIE_CSRF_TOKEN` 对齐。
2. `handleSaveChild`：`const res = await getClient().post(...)`；若 `res.error` 存在则 `setActionError(res.error.message)` 并 `return`，不关闭弹窗、不刷新。
3. `handleRemoveChild`：同理检查 `res.error`。

## 边界
- meta tag 读取逻辑保留不变（仅 cookie 名修正）。
- 错误展示复用现有 `actionError` 状态和 UI。
