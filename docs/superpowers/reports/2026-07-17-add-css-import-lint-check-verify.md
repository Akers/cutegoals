# Verify Report: add-css-import-lint-check

**Change**: `add-css-import-lint-check`
**Workflow**: tweak
**Date**: 2026-07-17
**Verify mode**: light（手动覆盖 scale=full；实际 2 文件 ~73 行）
**Review mode**: standard
**Language**: zh-CN

## 改动规模

| 指标 | 值 |
|---|---|
| 实现文件 | 2（`web/scripts/check-css-loads.mjs` 新建 + `web/package.json`）|
| 增量行 | +73 / -1 |
| delta spec | 0 capabilities |
| 新增 capability | 无 |

## 6 项轻量验证检查

| # | 检查项 | 结果 | 证据 |
|---|---|---|---|
| 1 | tasks.md 全部 `[x]` | ✅ PASS | 8/8 tasks checked |
| 2 | 改动文件与 tasks 一致 | ✅ PASS | git diff --stat：2 files +73/-1（scripts/check-css-loads.mjs + package.json）|
| 3 | 编译通过 | ✅ PASS | `npm run build` ✓ built in 5.40s |
| 4 | 测试通过 | ✅ PASS | `npm run test` 86/86 PASS；2 collection 失败 react-router-dom 解析（预存在环境问题，与本次无关，已在干净 main 复现确认）|
| 5 | 无安全风险 | ✅ PASS | 零外部依赖（仅 `node:fs/promises` + `node:path` + `node:url`）；无 eval/exec/child_process；无 fs 写入；无网络；`escapeRegex` 防 ReDoS |
| 6 | code review | ✅ PASS | oracle（`ses_0923f6251ffeitPvCo2Wbd2oXW`）Ready to merge: Yes；0 Critical；3 Important latent 接受偏差（见下）|

## Fresh 验证证据（Iron Law，本消息内）

### lint（tsc + lint:css）
```
> @cutegoals/web@0.1.0 lint
> tsc --noEmit && npm run lint:css

> @cutegoals/web@0.1.0 lint:css
> node scripts/check-css-loads.mjs

✓ All 2 CSS file(s) imported: src/styles/index.css, src/styles/themes.css
```

### build
```
✓ built in 5.40s
```

### test
```
Test Files  2 failed | 9 passed (11)
     Tests  86 passed (86)
```
2 failed 是 `react-router-dom` 解析（预存在环境问题）。

### 红绿验证（build 阶段完成，此处引用）
- **PASS**：脚本报告 `✓ All 2 CSS file(s) imported`
- **FAIL**：删 `app.tsx` 的 `import '@/styles/index.css'` 行 → 脚本报告 `✗ 1 CSS file(s) ... src/styles/index.css` + exit 1 + 修复建议
- **恢复**：恢复 import 行 → PASS

验证脚本红绿双向有效，检测逻辑正确。

## Oracle Code Review 结论

**Ready to merge: Yes (with caveat)** — `ses_0923f6251ffeitPvCo2Wbd2oXW`

- **0 Critical**
- **0 Important-blocking**（3 Important 全部 latent，接受偏差见下）
- **Strengths**：零依赖、`escapeRegex` 完整（15 个元字符全转义）、self-exclusion 正确（line 69）、quick pre-filter（line 70 `content.includes`）、`&&` 短路、红绿验证
- 7 项审查重点：3 ✅ + 4 ⚠️（latent）

## 接受偏差：3 个 latent Important

用户决策（2026-07-17）：**接受偏差继续合并**。

### 1. `@import url(...)` 语法漏检
- **问题**：正则 `@import\s+['"]` 要求 `@import` 后紧跟引号，但 CSS 标准也支持 `@import url('./x.css')` 语法
- **当前状态**：latent（项目用 `@import './themes.css'` quote 语法，无 url()）
- **触发条件**：未来有人改用 `@import url(...)` 语法
- **影响**：漏检（false negative），CSS 未被检测为 unreferenced
- **修复成本**：低（正则加 `url()` 分支）

### 2. `listFiles` 无 symlink 循环保护
- **问题**：递归遍历不检查 symbolic link，`src/` 下若有 symlink 循环会栈溢出
- **当前状态**：latent（版本控制的 `src/` 不太可能有 symlink 循环）
- **触发条件**：misconfigured build tooling（umi 插件、workspace symlinks）
- **影响**：栈溢出 / `ENAMETOOLONG` 崩溃
- **修复成本**：中等（加 visited inode set 或 skip symlink）

### 3. basename 子串 false positive
- **问题**：若两 CSS 文件名互为子串（如 `a.css` + `data.css`），import `data.css` 会让 `a.css` 被误判为 referenced
- **当前状态**：latent（`index.css` + `themes.css` 无 ≥4 字符重叠）
- **触发条件**：未来加命名冲突的 CSS 文件
- **影响**：误判（false positive），dead CSS 不被检出
- **修复成本**：低（正则加路径边界）

### 接受原因
- 3 项全部 **latent**，当前代码不触发
- oracle 明确 "Ready to merge: Yes, don't block for current baseline"
- 项目当前 CSS 基线干净（2 文件在 `src/styles/` 根，无子目录，无 basename 冲突）
- 留 follow-up：未来代码库扩展到这些模式时再修复（如 `fix-css-lint-coverage-enhancement`）

## 浏览器视觉验证限制

本次改动是 CI 工具脚本，无 UI 视觉变化，不需要浏览器验证。agent-browser CLI 未安装（前几次一致）。

## 分支处理

- 环境：normal repo（`GIT_DIR==GIT_COMMON`），main 分支
- 用户决策：Commit 到 main 并推送
- 执行：`git add` + commit + push origin main

## 影响范围（net positive）

- `npm run lint` 现在含 CSS import 检查，未来若有人误删 `app.tsx` 的 `import '@/styles/index.css'` 行，lint 会立即 fail
- 预防"CSS 写了但没 import"同类问题复现（前几次 `fix-parent-login-fullscreen` / `redesign-tri-login-pages` / `fix-global-styles-not-loaded` 的根因）
- 零 regression（纯增量工具，不改已有功能）
