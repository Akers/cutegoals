# 验证报告：fix-admin-config-display

- 日期：2026-07-17
- 验证模式：light（3 tasks ≤ 3、0 delta spec ≤ 1、代码改动 2 文件 ≤ 8；scale 脚本将 12 个 openspec 元数据文件计入，已按提交区间复核并手动覆盖为 light）
- review_mode：off（hotfix 预设默认值）

## 背景

管理端 `/admin/config` 页面将后端数组契约（`ConfigEntry[]`）误作扁平 map 处理，导致标签渲染为数字索引、输入框显示 `[object Object]`，且保存时回传数组必然失败。修复为纯前端改动：`web/src/admin/pages/index.tsx` 的 `AdminConfigPage`（+ 新增回归测试 `web/src/__tests__/admin-config-page.test.tsx`）。

## RED 证据（修复前）

- 命令：`npx vitest run src/__tests__/admin-config-page.test.tsx`
- 结果：2/2 失败。`findByText('sms.provider')` 未找到（实际渲染 `"0"/"1"/"2"`）；`findByDisplayValue('aliyun')` 未找到（DOM 快照显示 `value="[object Object]"`），与用户截图一致。

## 轻量验证 6 项

| # | 检查项 | 结果 | 证据 |
|---|--------|------|------|
| 1 | tasks.md 全部完成 | PASS | 3 项全部 `[x]`，0 未完成 |
| 2 | 改动文件与 tasks.md 一致 | PASS | `git diff --stat HEAD~2...HEAD`：代码改动恰为 task 1 的测试文件与 task 2 的 `pages/index.tsx`（其余 12 个为 openspec change 元数据） |
| 3 | 编译通过 | PASS | `npm run build`（web）exit 0，fresh 运行于 verify 阶段；build guard 另执行 `web build + server mvn compile` 全量通过；`tsc --noEmit` 无输出 |
| 4 | 相关测试通过 | PASS | 回归测试 2/2 通过（fresh）；全量 `vitest run` 88/88 通过（fresh 复跑）。2 个失败 suite（`src/child/__tests__/auth.test.tsx`、`src/shared/auth/__tests__/auth-pages.test.tsx`）为**预先存在**问题：引用未安装的 `react-router-dom`，stash 本改动后同样失败，与本修复无关 |
| 5 | 无明显安全问题 | PASS | diff 无硬编码密钥/凭据；保存仅提交变更键，未改动的掩码秘密字段（`***MASKED***`）不回传，杜绝掩码回写为真实秘密；masked 项使用 `type="password"` 输入框 |
| 6 | 代码审查策略 | SKIP | `review_mode: off`（hotfix 预设默认），按规则跳过自动代码审查 |

## 根因消除确认

- `Object.entries(data)`、`key.toLowerCase().includes('secret')`、`useApi<Record<string, string>>('/admin/config')` 在源码中已不存在（grep 无匹配）。
- `/admin/config` 的前端消费点唯一（`AdminConfigPage`），均已按 `ConfigEntry[]` 契约处理。

## 残留不确定性

- 全量测试中 `src/child/__tests__/pages.test.tsx` 曾出现 1 次偶发失败，单独运行及全量复跑均通过，判定为 flaky，与本改动无关（该测试不依赖 admin 页面）。
- 页面视觉效果（描述副文本排版）未在真实浏览器人工复核；已由 jsdom 渲染断言覆盖功能性契约。

## 结论

6 项全部 PASS，无 CRITICAL/IMPORTANT 问题，验证通过。
