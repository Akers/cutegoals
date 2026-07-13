# Verification Report: fix-admin-pages-data-shape

**Change**: fix-admin-pages-data-shape
**Workflow**: hotfix
**Verify Mode**: light（手动覆盖；自动评估误判为 full，因 tasks.md 子任务数含验证项拉高至 9，但实际改动 1 文件、0 delta spec、90 行代码改动）
**Date**: 2026-07-13
**Base ref**: `70c4f4a`（fix-admin-pages-500 归档后 HEAD）
**Verification ref**: `471c59c`（本次提交）

---

## 1. 改动概述

修复 `web/src/admin/pages/index.tsx` 三个 admin 页面的前端类型与后端响应体形状错配。fix-admin-pages-500 修复 HTTP 500 后，原预存的前端形状错配（`useApi<T[]>` 假设后端返回数组，实际返回分页对象 `{content, page, pageSize, totalElements, totalPages}` / health 扁平 Map）显现为运行时 `data.map is not a function`。

改动文件：1 个（`web/src/admin/pages/index.tsx`）；90 行新增、19 行删除。

---

## 2. 6 项轻量验证结果

| # | 检查项 | 结果 | 证据 |
|---|--------|------|------|
| 1 | tasks.md 全部 `[x]` | ✅ PASS | `grep -c '^\- \[ \]'` = 0 |
| 2 | 改动文件与 tasks 一致 | ✅ PASS | `git diff --stat HEAD~1 HEAD` 仅 `web/src/admin/pages/index.tsx`，+90/-19 行 |
| 3 | 编译通过 | ✅ PASS | `npx tsc -b` exit=0，0 errors |
| 4 | 相关测试通过 | ✅ PASS | `npx vitest run src/admin` → App.test.tsx 2/2 PASS；`npm test` 全量：admin/parent-routing 失败的 14 项已用 `git stash` 对照 baseline 确认是 fix-admin-pages-500 时已存在的预存失败（routing/parent/child 端，与 admin 改动无关），本改动 0 新增失败 |
| 5 | 无明显安全问题 | ✅ PASS | diff 中无 `password`/`secret`/`api[-_]?key`/`token`/`hardcod` 关键字；纯前端类型对齐 + 解包逻辑 |
| 6 | 代码审查策略 | ⏭️ SKIP | review_mode=off（hotfix preset 强制，覆盖 .comet/config.yaml 的 standard），按 comet-verify skill 规则跳过自动 code review |

**通过标准**：6 项全部 OK 或显式 SKIP；无 CRITICAL 或 IMPORTANT 问题。

---

## 3. 根因消除证据（grep）

旧形状模式（应为 0）：

```
$ grep -nE "useApi<(Account\[\]|AuditLog\[\]|HealthData)>|data\.map\(|data\.checks" web/src/admin/pages/index.tsx
289:  const { data, loading, error, refetch } = useApi<HealthData>('/admin/health');
```

唯一残留 `useApi<HealthData>` 是预期保留：`/admin/health` 非分页接口，`HealthData` 类型已扩展对齐后端字段（`database`/`backup`/`recoveryDrill` 等），不需要 `PageResult<T>` 包装。

`data.map(` 与 `data.checks` 直接访问模式 0 处残留。

新形状就位：

```
149:  useApi<PageResult<Account>>('/admin/accounts');
181:  data.content.map((account) => (
221:  useApi<PageResult<AuditLog>>('/admin/audit-logs');
244:  data.content.map((log) => (
297:  const checks: HealthCheck[] = [
```

---

## 4. 额外字段对齐说明

修复过程中发现：除原始报告的形状错配外，前端 interface 字段名也与后端实际返回字段不匹配，若不解包会显示空白。已在同一改动中一并修复：

| Interface | 原前端字段 | 后端实际字段 |
|-----------|-----------|-------------|
| Account | `role: string` | `roles: string[]` |
| Account | `enabled: boolean` | `status: 'ACTIVE'\|'DISABLED'`（额外含 `createdAt`/`updatedAt`） |
| AuditLog | `operator: string` | `actorId: number` + `actorType: string` |
| AuditLog | `action: string` | `eventType: string`（额外含 `result`/`summary`/`requestId`） |

字段来源：
- Account: `AccountManagementService.getAccounts` L51-57（LinkedHashMap 构造）
- AuditLog: `AuditLogService.maskSensitiveFields` L146-160

UI 影响：
- accounts 页：角色列从 `{account.role}` 改为 `{account.roles.join(', ')}`；状态列与操作按钮从 `account.enabled` 改为 `account.status === 'ACTIVE'`
- audit 页：新增「结果」列（`log.result`）；操作者列改为 `{log.actorType}#{log.actorId}`；动作列改为 `{log.eventType}`

UI 结构保持不变（仍是 table + thead/tbody），仅数据派生来源调整。

---

## 5. Non-Goals 边界

按 design.md Non-Goals 约束：

- ❌ 未引入分页 UI（页码切换、`totalPages` 显示）。类型层声明了 `page/pageSize/totalElements/totalPages` 但渲染层未使用。后续若需分页 UI 应另起 change。
- ❌ 未改 `useApi` 公共 hook。
- ❌ 未改后端 API 契约或字段名。
- ❌ 未重组 health 页面 UI（保持视觉一致性）。
- ❌ 未为 admin endpoint 新增 controller 测试（已知约束，上 hotfix 已记录）。

---

## 6. 已知风险与边界

| 风险 | 状态 |
|------|------|
| health 子字段（backup/recoveryDrill）来自 `getBackupStatus`/`getRecoveryDrillStatus` 私有方法 | 已读取源码确认字段集合，HealthData 类型与渲染派生一致 |
| CLI agent 无法做浏览器端运行时验证 | 等价证据覆盖（类型检查 + admin App test + 根因 grep + 上 hotfix 后端 API 验证）；最终浏览器端验证留给用户 |
| baseline 14 个测试失败（routing/parent/child） | 与本改动无关，已在 fix-admin-pages-500 时预存；不影响 admin 改动验证结论 |

---

## 7. 验证结论

✅ **PASS** — 改动符合 proposal.md 目标、tasks.md 全部完成、编译通过、相关测试通过、无安全问题。`data.map is not a function` 运行时错误的根因（前端形状错配）已在代码层完全消除。建议进入归档阶段。

---

## 8. 分支处理

当前分支 `main`，本改动直接提交（commit `471c59c`），与 fix-admin-pages-500 / fix-admin-401 同分支。无需单独 PR 或合并操作。`branch_status: handled` 即将在 .comet.yaml 中标记。
