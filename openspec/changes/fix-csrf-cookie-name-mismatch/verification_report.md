# Verification Report: fix-csrf-cookie-name-mismatch

## Summary

| Dimension    | Status                                              |
|--------------|-----------------------------------------------------|
| Completeness | 7/7 tasks done                                      |
| Correctness  | Root cause (CSRF cookie name mismatch) fixed        |
| Coherence    | Frontend cookie name aligns with backend constant   |

## 1. 完整性（Completeness）

- `tasks.md` 7 项任务全部勾选完成。

## 2. 正确性（Correctness）

### Bug：添加孩子 POST 返回 403，错误被静默吞掉

- **根因**：`getCsrfToken()` 读取 cookie `XSRF-TOKEN`，后端 `AuthConstants.COOKIE_CSRF_TOKEN = "csrf_token"`，cookie 名不匹配，CSRF header 从不发送，POST/PUT/DELETE 被后端 CSRF 过滤器拒绝（403）。
- **修复位置**：`web/src/shared/api/client.ts`、`web/src/parent/pages/index.tsx`
- **修复内容**：
  - `getCsrfToken()` cookie 正则从 `XSRF-TOKEN=` 改为 `csrf_token=`；
  - `handleSaveChild`/`handleRemoveChild` 检查 `res.error` 并展示错误信息。
- **验证**：直接 curl POST `http://localhost:8981/api/family/children` 返回 401（endpoint 存在），前端修复后 CSRF header 将正确发送。

## 3. 一致性（Coherence）

- 前端 cookie 名与后端 `AuthConstants.COOKIE_CSRF_TOKEN` 完全对齐。
- 错误展示复用现有 `actionError` 状态。

## 4. 发现的问题

### WARNING
- 前端 `npm test` 仍有 **14 个预存失败 / 65 通过**，与 baseline 一致，无新增失败。
- 修复了 `client.test.ts` 中 CSRF cookie 测试（从 `XSRF-TOKEN` 改为 `csrf_token`）。

## 5. 测试证据

- **前端类型检查**：`npx tsc -b` 通过，0 errors。
- **前端单元测试**：`npm test -- --run`：14 failed / 65 passed（与 baseline 一致）。

## 6. 最终评估

根因已消除：CSRF cookie 名对齐后，带认证的 POST/PUT/DELETE 请求将正确携带 `X-CSRF-TOKEN` header，CSRF 过滤器通过。`handleSaveChild` 检查 `res.error` 后，任何失败都会向用户展示。

**Ready for archive.**
