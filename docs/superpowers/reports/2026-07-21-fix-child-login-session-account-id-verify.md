# 验证报告: fix-child-login-session-account-id

**日期**: 2026-07-21
**验证模式**: 轻量（light）
**review_mode**: off

## 概述

修复孩子端 PIN 登录 `Internal Server Error`：`session.createSession(-childId)` 违反外键约束。

## 验证结果

| # | 检查项 | 结果 | 证据 |
|---|--------|------|------|
| 1 | tasks.md 全部任务已完成 `[x]` | ✅ PASS | 4/4 任务已勾选 |
| 2 | 改动文件与 tasks.md 描述一致 | ✅ PASS | 3 个源文件（1 迁移 + 2 Java）与 tasks 完全对应 |
| 3 | 编译通过 | ✅ PASS | 前端 build + Maven compile 均成功 |
| 4 | 相关测试通过 | ✅ PASS | family: 44/44, auth: 49/49 |
| 5 | 无明显安全问题 | ✅ PASS | 无硬编码密钥、无新增 unsafe 操作、正确使用 createChildSession(accountId=null) |
| 6 | 代码审查 | ⏭️ SKIPPED | review_mode=off |

## 修复摘要

1. 迁移 `V13__add_child_session_support.sql` → `V15__add_child_session_support.sql`（添加 `child_id` 列、使 `account_id` 可空）
2. `DeviceBindingService.java:213` → `createChildSession(childId, deviceId)` 替代 `createSession(-childId, deviceId)`
3. `DeviceBindingServiceTest.java` → mock/verify 更新为 `createChildSession`

## 根因消除确认

- `createSession(-childId, ...)` 已在代码库中完全移除（grep 全量搜索返回 0 结果）
- `createChildSession(childId, ...)` 在正确位置调用

## 结论

全部检查通过，无 CRITICAL 或 IMPORTANT 问题。
