# 验证报告：fix-parent-menu-permission

日期：2026-07-17
验证模式：light（4→5 个文件、0 delta spec；任务粒度细但改动极小，已用 `comet state set verify_mode light` 覆盖自动评估）
验证轮次：2（第 1 轮代码审查 2 项 Minor → 自动修复后复验）

## 问题与修复概述

家长端登录后所有菜单 403。根因：初始化管理员账号持 `INSTANCE_ADMIN`+`PARENT` 双角色，`deriveRole` 折叠为单角色 `admin`，`wrappers/AuthGuard.tsx` 单角色相等比较误判。修复为角色集合成员检查（`normalizeRoles` + `includes`），并应代码审查建议加固 `normalizeRoles` 防御。

## 轻量验证 6 项检查

| # | 检查项 | 结果 | 证据 |
|---|--------|------|------|
| 1 | tasks.md 全部完成 | PASS | 6/6 `[x]` |
| 2 | 改动文件与 tasks 一致 | PASS | `git diff --stat d0f6108...HEAD`：app.tsx、role.ts、AuthGuard.tsx + 2 个测试文件（role.test.ts 为审查修复新增），与 tasks/审查闭环一致 |
| 3 | 编译通过 | PASS | `npm run build`（umi build ✓）；comet guard 构建门禁（含 `mvn compile -q`）✓；`tsc --noEmit` exit 0 ✓ |
| 4 | 相关测试通过 | PASS | `npx vitest run`：95/95 通过；回归用例 RED→GREEN 已确认（修复前双角色用例失败、修复后 3/3 通过）；`role.test.ts` 4 用例通过 |
| 5 | 无明显安全问题 | PASS | diff 无密钥/无不安全操作；越权向量经审查逐项验证：空 roles/未知角色/小写注入均 fail-safe 到 403 |
| 6 | 代码审查（review_mode=standard） | PASS | @oracle 轻量审查（正确性/安全/边界）：Ready to merge = Yes，0 Critical / 0 Important；2 项 Minor（normalizeRoles 非字符串防御、映射类型约束）已在第 2 轮自动修复并复验通过 |

## 预存问题（与本改动无关，已归因）

- `src/child/__tests__/auth.test.tsx`、`src/shared/auth/__tests__/auth-pages.test.tsx` 两个套件因 `node_modules` 缺 `react-router-dom` 无法加载（环境/依赖问题）。已用 `git stash` 在未含本改动的代码上复现，确认为预存问题；建议后续单独 change 处理（补 devDependency 或改用 umi mock）。
- 工作区存在大量与本 change 无关的未提交改动（`.opencode/**` 技能更新、`web/config/config.ts` 代理端口等），未触碰、未提交。

## 结论

**PASS**。修复消除根因，单角色账号行为不变，双角色账号可正常访问家长端与管理员端。
