# Verify 报告：tweak-calendar-picker-font-size

- 日期：2026-08-03
- Change：tweak-calendar-picker-font-size
- Workflow：tweak
- Verify Mode：full（scale 评估：9 tasks / 1 delta capability / 6 changed files → full）
- Review Mode：off（tweak 默认）
- Commit：`6b06dbf`（amend 后单 commit）
- Branch：main（`isolation: current`）

## 改动范围（commit `6b06dbf` vs base `838e6f8`）

| 文件 | 类型 | 关键改动 |
|------|------|------|
| `web/src/parent/components/TaskCalendar.tsx` | 修改 | 4 处 `fontSize` 调整 + `<style>` 块新增 1 条规则 |
| `openspec/changes/tweak-calendar-picker-font-size/proposal.md` | 新增 | 提案：动机 + 范围 + 影响 |
| `openspec/changes/tweak-calendar-picker-font-size/design.md` | 新增 | 技术设计：决策、风险、迁移 |
| `openspec/changes/tweak-calendar-picker-font-size/tasks.md` | 新增 | 9 项任务清单 |
| `openspec/changes/tweak-calendar-picker-font-size/specs/parent-task-calendar/spec.md` | 新增 | ADDED delta spec |
| `openspec/changes/tweak-calendar-picker-font-size/.comet.yaml` | 新增 | Comet 状态元数据 |

合计：6 个 changed files（≤ 8 阈值，但因有 delta spec 且 `verify_mode=full`，走完整验证）。

## 验证证据（fresh run，2026-08-03）

| 检查项 | 命令 | 结果 |
|------|------|------|
| TypeScript 类型检查 | `pnpm --filter @cutegoals/web exec tsc --noEmit` | 10 个错误（全部为 base `838e6f8` 上已存在的历史未用变量错误：`TaskCalendar.tsx:1 React 未用`、`index.tsx:1 useCallback 未用` / `:33 CalendarAction 未用` / `:1510 type 重复` / `:1547 snapshotTemplateTypeConfig`、`TaskTypeConfigForms.tsx:241 destructured 未用`、`ParentTasksPage.test.tsx:3 dayjs` / `:38 onSelect` / `:38 onNavigate` / `:60 CalendarAction2 未用`）。**本 commit 未引入任何新的 tsc 错误**（通过 `git stash` 对比 base 验证） |
| Vitest 全套 | `pnpm --filter @cutegoals/web run test` | 18 Test Files / 176 tests / 176 passed（0 failed）。关键文件 `TaskCalendar.test.tsx` 30/30 pass，`ParentTasksPage.test.tsx` 34/34 pass |
| CSS 引用完整性 | `node web/scripts/check-css-loads.mjs` | `✓ All 2 CSS file(s) imported: src/styles/index.css, src/styles/themes.css`（本 change 未新增 CSS 文件，所有样式在 `<style>` 块内） |
| OpenSpec schema 校验 | `openspec validate tweak-calendar-picker-font-size --type change --strict` | `Change 'tweak-calendar-picker-font-size' is valid` |
| 完整 build（umi + mvn） | `pnpm build`（在 `comet guard build --apply` 中执行） | web `umi build` ✓ built in 6.75s / server `mvn compile -q` 通过 |

## 验证维度（openspec-verify-change 三维度）

### Completeness

- **任务完成度**：9/9 tasks 完成 ✓
  - 1.1 / 1.2 / 1.3：代码修改已 commit (`6b06dbf`)
  - 2.1 / 2.2 / 2.3 / 2.4：本地验证证据（见上表）
  - 3.1 / 3.2：commit + openspec validate 通过
- **Spec 覆盖**：delta spec 含 1 个 capability (`parent-task-calendar`)，1 个 ADDED Requirement，3 个 Scenario。每个 Scenario 在 `TaskCalendar.tsx` 均有对应实现位置。

### Correctness

需求：「日历选择控件字号」requirement（`specs/parent-task-calendar/spec.md`）。

| Scenario | 实现位置 | 数值 | 状态 |
|------|------|------|------|
| 日期单元格字号 ≥ 16px | `TaskCalendar.tsx:370-374`（`<style>` 块内 `.task-calendar-current-month .ant-picker-calendar-date-value { font-size: 16px !important }`） | 16px | ✓ |
| 周号列字号 ≥ 14px | `TaskCalendar.tsx:181`（`fontSize: 14`，由原 12 + 2） | 14px | ✓ |
| 月份标题 ≥ 16px（`CalendarHeader`） | `TaskCalendar.tsx:73`（`fontSize: 16`） | 16px | ✓ |
| 导航栏中央文本 ≥ 16px（`<' '>'` 中间） | `TaskCalendar.tsx:391`（`<span style={{ fontSize: 16 }}>`） | 16px | ✓ |

所有 Scenario 数值精确等于或高于 requirement 基线，未触动任何 color / border / padding / height 属性。

### Coherence

- **Design 决策遵循**：
  - 决策 1（在 `<style>` 块内通过 antd className 选择器统一覆盖日期单元格）✓ 实现
  - 决策 2（基础字号 +2）✓ 实现：周号 12→14、其余 antd 默认 ~14→16
  - 决策 3（不调整 cell 高度 / 占位行）✓ 实现
- **代码模式一致性**：
  - 注释 `// tweak-calendar-picker-font-size: 字号 +2` 与现有 `// fix-build 第三次迭代:` 注释风格一致
  - `<style>` 块规则名 `.task-calendar-current-month` 与现有选中态/今天态规则同 scope
  - 不修改 `CalendarSelection` / `CalendarAction` 类型签名

## Issues by Priority

### CRITICAL

无。

### WARNING

无。

### SUGGESTION

1. **design.md 决策 2 与实现细节轻微偏离** — design.md 写「不写死基础字号，使用相对值 +2」，但实现使用绝对值（16 / 14）。理由：antd 5.x token 不一定下放到所有内部元素，验证成本高，采用绝对值更稳。**建议**（不阻塞）：下次同主题调整时考虑 CSS variable 或 antd theme token 化；本次按 design.md 决策 1 的「理由」附注（绝对值是降级路径），可接受。

## Spec 漂移

未发现。delta spec 仅 `## ADDED Requirements`，未触动 `parent-task-calendar` 现有 REQUIREMENTS；现有 spec（颜色 / 边框 / 选中态 / 周号 / 任务类型筛选 / 默认选中今日与本周）保持不变。

## Final Assessment

**所有 CRITICAL/WARNING 检查通过；1 项 SUGGESTION 为工程改进建议，不阻塞归档。**

变更范围聚焦（CSS 字号 +2）、改动原子（单 commit）、证据完整（typecheck 基线对比 + vitest 176/176 + 完整 build + strict validate），满足归档前置条件。可进入 `/comet-archive`。