# Proposal: fix-csrf-cookie-name-mismatch

## 问题
家长端添加孩子时 POST `/api/family/children` 返回 403，孩子无法创建，但前端 `handleSaveChild` 不检查 `response.error`，错误被静默吞掉，用户误以为添加成功。

## 根因
`web/src/shared/api/client.ts` 的 `getCsrfToken()` 读取 cookie `XSRF-TOKEN`，但后端 `AuthConstants.COOKIE_CSRF_TOKEN = "csrf_token"`，写入的 cookie 名是 `csrf_token`。两者不匹配，前端永远拿不到 CSRF token，`X-CSRF-TOKEN` header 从不发送，所有带认证的 POST/PUT/DELETE 请求被后端 CSRF 过滤器拒绝（403）。

次要问题：`handleSaveChild` 和 `handleRemoveChild` 调用 `getClient().post/delete()` 后不检查返回的 `error` 字段（API client 不抛异常，只返回 `{ error }`），导致任何失败都被静默忽略。

## 修复目标
1. `getCsrfToken()` 改读 `csrf_token` cookie，与后端一致。
2. `handleSaveChild` 和 `handleRemoveChild` 检查 `response.error` 并展示错误信息。

## 范围
- `web/src/shared/api/client.ts`：修改 cookie 名正则。
- `web/src/parent/pages/index.tsx`：添加错误检查。

## 非目标
- 不修改后端；
- 不新增 API 或 delta spec。
