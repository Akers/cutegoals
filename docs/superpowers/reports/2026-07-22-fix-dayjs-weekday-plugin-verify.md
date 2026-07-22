# 验证报告：fix-dayjs-weekday-plugin

- Change: `fix-dayjs-weekday-plugin`
- Workflow: hotfix（open → build → verify → archive）
- 日期：2026-07-22
- 验证模式：**light**（手动覆盖，见下「规模评估」）
- 验证结果：**PASS**
- review_mode：`off`（hotfix 默认，跳过自动 code review）

---

## 规模评估与 verify_mode 决策

`comet state scale` 自动判定为 `full`：

| 指标 | 自动统计 | 阈值 | 触发 full |
|---|---|---|---|
| Tasks | 4 | > 3 | ✅ |
| Delta specs | 0 capabilities | > 1 | — |
| Changed files | 17 | > 8 | ✅ |

**手动覆盖为 `light` 的理由**：

1. **17 changed files 是元数据虚高**。`git diff --stat HEAD~2...HEAD` 拆解：
   - 真实实现改动 **5 个**（`web/src/shared/dayjs.ts`、`web/src/shared/__tests__/dayjs.test.ts`、`web/src/app.tsx`、`web/src/parent/components/TaskCalendar.tsx`、`web/src/__tests__/setup.ts`）
   - OpenSpec 产物 **3 个**（proposal.md、design.md、tasks.md）
   - Comet / .openspec 元数据 **9 个**（.comet.yaml、artifacts.json、checkpoint.json、run-state.json、package.json、sha256、state-events.jsonl、trajectory.jsonl、.openspec.yaml）—— 工具自身状态，非实现内容
2. **完整验证的扩展检查项均不适用**：
   - 无 delta spec（hotfix 不改 spec 验收场景）
   - 无 `docs/superpowers/specs/` 下的 Design Doc（hotfix 默认不开）
   - 无 capability spec scenario 变更（`web-app` capability 无 MODIFIED delta）
3. **4 tasks 是同一修复的不同侧面**（新建模块 + 入口接入 + 清理旧注册 + 跑测试），非跨 capability 的复杂协作。
4. 符合 comet-hotfix SKILL.md「无 delta spec 的小范围 hotfix 通常满足轻量验证条件」的判定精神。

---

## 6 项轻量验证检查

| # | 检查项 | 结果 | 证据 |
|---|---|---|---|
| 1 | tasks.md 全部任务已完成 `[x]` | ✅ PASS | 4/4 任务标记为 `[x]`（见 `openspec/changes/fix-dayjs-weekday-plugin/tasks.md`） |
| 2 | 改动文件与 tasks.md 描述一致 | ✅ PASS | `git diff --stat HEAD~2...HEAD` 显示 5 个实现文件改动，与 tasks 1-3 描述一一对应 |
| 3 | 编译通过 | ✅ PASS | `pnpm --filter web build` 成功，耗时 6.96s，exit 0；bundle size 警告为历史既有问题（`themes.js` 246 kB、`umi.js` 637 kB），与本次修复无关 |
| 4 | 相关测试通过 | ✅ PASS | `pnpm --filter web test`：**18 test files / 173 tests 全部通过**，2.84s |
| 5 | 无明显安全问题 | ✅ PASS | grep 检查 `password/secret/api_key/token/AWS_/BEGIN PRIVATE KEY` 硬编码：No files found；grep 检查 `unsafe/eval()/new Function()`：No files found |
| 6 | 代码审查 | ⏭️ SKIP | `review_mode: off`（hotfix 默认）。按 comet-verify SKILL.md Step 2a 第 6 项，跳过自动 code review |

---

## 测试明细（task 4 证据）

新增回归测试 `web/src/shared/__tests__/dayjs.test.ts`（3 个用例）：

```
✓ src/shared/__tests__/dayjs.test.ts  (3 tests) 4ms
```

相关回归测试：

```
✓ src/parent/components/__tests__/TaskCalendar.test.tsx  (31 tests) 596ms
✓ src/parent/pages/__tests__/ParentTasksPage.test.tsx    (32 tests) 552ms
```

合计 18 test files / 173 tests 全部通过。

---

## 根因消除复核

- `rg 'dayjs.extend|dayjs/plugin' web/src --glob '!__tests__/**'`：唯一匹配 `web/src/shared/dayjs.ts`（5 个 extend 集中注册），无散落注册
- `web/src/parent/components/TaskCalendar.tsx`：局部 `weekOfYear` 注册已彻底清除，仅保留 `import dayjs from 'dayjs'`
- `web/src/app.tsx` 顶部 `import '@/shared/dayjs'` 已就位，且早于 antd / AuthProvider 相关 import
- `web/src/__tests__/setup.ts` 顶部同样接入 `import '../shared/dayjs'`，保证单元测试与生产运行时行为一致

原 bug「`clone.weekday is not a function`」根因（dayjs `weekday` 插件未注册）已彻底消除。

---

## 与 proposal / design 的一致性

| proposal 目标 | 实现证据 |
|---|---|
| 在应用入口统一注册 dayjs 插件 | `web/src/app.tsx` 顶部 `import '@/shared/dayjs'` |
| 至少注册 `weekday`，补齐常用插件 | `web/src/shared/dayjs.ts` 注册 5 个：weekday + weekOfYear + customParseFormat + localizedFormat + advancedFormat |
| 移除 TaskCalendar 局部注册 | `TaskCalendar.tsx` 已删除 `dayjs.extend(weekOfYear)` 与对应 import |
| 提供回归测试证据 | `web/src/shared/__tests__/dayjs.test.ts` 3 用例，RED→GREEN 已验证 |

| design 决策 | 实现一致性 |
|---|---|
| 集中注册位置 → `shared/dayjs.ts` | ✅ 一致 |
| 注册插件清单（5 个） | ✅ 一致 |
| 清理 TaskCalendar 局部注册，保留 `import dayjs` | ✅ 一致 |

---

## 非目标验证

- ❌ 未修改任何后端代码或 API（前端单侧修复）
- ❌ 未引入新的 React 组件或业务能力
- ❌ 未改动 antd 主题、DatePicker 业务交互逻辑
- ❌ 未调整测试 mock 策略

全部符合 proposal.md「范围与非目标」声明。

---

## 未覆盖项（hotfix 不适用）

按 comet-verify SKILL.md Step 2a「跳过项」声明，以下检查不在本次轻量验证范围内：

- spec scenario 覆盖率（无 delta spec）
- design doc 一致性深度比对（无 `docs/superpowers/specs/` Design Doc）
- delta spec 与 design doc 漂移检测（无 delta spec）

---

## 结论

**verify_result: pass**

- 修复根因已消除：dayjs `weekday` 插件已在应用入口全局注册，antd DatePicker 的 `rc-picker` 内部调用 `.weekday(0)` 不再抛错
- 回归测试已落盘，3 个插件断言持续守护
- 173/173 现有测试通过，构建成功，无安全风险
- 改动严格限定在 proposal/design 声明范围内

可推进至归档阶段。
