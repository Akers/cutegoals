# 验证报告：fix-admin-auth-redirect

- **change**: `fix-admin-auth-redirect`
- **workflow**: hotfix（preset → open → build → verify → archive）
- **verify_mode**: light（手动覆盖）
- **review_mode**: off
- **date**: 2026-07-17
- **commit**: `54485fc`

## 规模评估

`comet-state scale` 自动评估结果为 `full`（tasks=5 > 3、changed_files=13 > 8），但其中：

- 8 个 `.comet/` 文件是 Comet workflow 元数据（状态、快照、事件日志）
- 3 个 `openspec/changes/.../` 文件是 OpenSpec 必需产物（proposal/design/tasks.md）
- 实际实现改动只有 **2 个源文件**（`web/src/app.tsx`、`web/src/admin/pages/AdminInitPage.tsx`），共 8 行 insert / 4 行 delete
- 0 个 delta spec、0 个新 capability

依据 `comet-verify` 的覆盖机制手动设为 `light`，符合 hotfix 范围。

## 6 项轻量验证

| # | 检查项 | 结果 | 证据 |
|---|---|---|---|
| 1 | tasks.md 全部任务已完成 | PASS | 5/5 `[x]`，0 `[ ]` |
| 2 | 改动文件与 tasks.md 一致 | PASS | `git diff HEAD~1 HEAD --stat -- web/src/`：仅 `web/src/app.tsx`、`web/src/admin/pages/AdminInitPage.tsx` 两个文件，与 tasks 1、2 描述一一对应 |
| 3 | 编译通过 | PASS | `cd web && npm run lint`（`tsc --noEmit` + CSS 检查）exit 0 |
| 4 | 相关测试通过 | PASS | `cd web && npm run test`：86/86 通过；2 个预存在的 react-router-dom collection 失败属于环境层依赖问题（`react-router-dom` 仅作为 `umi` 的 transitive 依赖，未被 test resolver 解析），与本次 hotfix 无关，与历史 hotfix `2026-07-16-fix-parent-login-fullscreen` 报告记录一致 |
| 5 | 无明显安全问题 | PASS | 仅字符串归一化与跳转目标变更，无新增网络调用、无新增 secret 引用、无 unsafe 操作 |
| 6 | 代码审查策略 | SKIP | `.comet.yaml` 中 `review_mode: off`（hotfix 默认），按 `comet-verify` Step 2a 第 6 项跳过自动审查；改动 ≤ 10 行且为机械修复，正确性已由 build/test 与根因消除检查（grep 验证 `roles.includes('admin'|'parent')` 与 `history.replace('/admin')` 已从源码消失）覆盖 |

## 根因消除验证

```bash
grep -rE "roles\.includes\(['\"]admin['\"]\)|roles\.includes\(['\"]parent['\"]\)" web/src
# (no output — 根因代码已消除)

grep -rE "history\.replace\(['\"]/admin['\"]\)" web/src/admin
# (no output — AdminInitPage 跳转目标已改为 /admin/login)
```

`AdminLoginPage.tsx:47` 仍存在 `history.push('/admin')`——这是登录成功后的目标跳转，已由 `deriveRole` 修复后正确生效，不在本次范围内（design.md 已显式排除）。

## 结论

全部 6 项检查通过（5 PASS + 1 按策略 SKIP），根因消除验证通过，无 CRITICAL 或 IMPORTANT 问题。验证通过。

## 手动浏览器验证（建议，归档后由用户执行）

1. 实例未初始化时访问 `/admin/init` → 提交合法表单 → 跳转到 `/admin/login`（不再跳 `/admin`）
2. 在 `/admin/login` 用初始化时的手机号/密码登录 → 跳转到 `/admin`，菜单不再显示「无权访问此页面」
3. 进入「账号管理」「审计日志」「系统健康」「实例配置」等子页面均正常加载
4. 刷新 `/admin` 仍可正常进入（角色判定来自持久化 token + `/auth/me`）

## 影响范围

- 前端：2 个文件（`web/src/app.tsx`、`web/src/admin/pages/AdminInitPage.tsx`）
- 后端：无改动
- spec：无改动（无 delta spec）
- 兼容性：parent / child 端使用同一 `deriveRole`，会顺带受益于大小写无关判定；之前区分大小写时所有大写角色都 fallback 到 `'child'`，本次修复不影响 parent/child 现状
