# Tasks: fix-csrf-cookie-name-mismatch

- [x] 1.1 `getCsrfToken()` cookie 正则改为 `csrf_token=`。
- [x] 1.2 `handleSaveChild` 检查 `res.error` 并展示。
- [x] 1.3 `handleRemoveChild` 检查 `res.error` 并展示。
- [x] 1.4 验证 `npx tsc -b` 0 errors。
- [x] 1.5 验证 `npm test` 与 baseline 一致（14 failed / 65 passed），0 新增失败。
- [x] 1.6 git commit：`fix(web): align csrf cookie name and surface child save errors`。
- [x] 1.7 运行 build guard 推进到 verify。
