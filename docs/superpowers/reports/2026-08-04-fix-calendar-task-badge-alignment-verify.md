# Verify 报告：fix-calendar-task-badge-alignment

- 日期：2026-08-04
- Change：fix-calendar-task-badge-alignment
- Workflow：hotfix
- Verify Mode：full（scale 评估：13 tasks / 1 delta capability / 2 changed files → full，因 tasks > 3）
- Review Mode：off（hotfix 默认）
- Commit：`7275ef7`（fix 代码）+ 未提交的 OpenSpec 产物（proposal/design/tasks/delta spec，归档时一并提交）
- Branch：main（`isolation: current`）

## 改动范围

| 文件 | 类型 | 关键改动 |
|------|------|------|
| `web/src/parent/components/TaskCalendar.tsx` | 修改 | `renderDateCell` 角标内联定位 `top: -26 → 2`、`left: 20 → 2`（+2 行注释） |
| `web/src/parent/components/__tests__/TaskCalendar.test.tsx` | 修改 | 已有测试「任务数角标使用绝对定位」断言由 `-26px/20px` 修正为 `2px/2px`（原断言锁定了 buggy 值） |
| `openspec/changes/fix-calendar-task-badge-alignment/*` | 新增 | proposal / design / tasks / delta spec / .comet.yaml |

代码 diff 合计：2 files / 9 insertions / 5 deletions。

## Bug 与根因

- **现象**（用户截图 + observer 视觉分析）：2026-08-03 / 05 / 07 三个有任务日的角标（`data-testid="task-badge-*"`，10×10 px 红色圆点）视觉中心 y≈145，而所在行日期中心 y≈178，向上错位 ~33 px，跨入上一周日期行；水平方向 `left: 20px` 使角标位于日期数字右侧而非 cell 左上角。
- **根因**：antd 5.x `dateCellRender` 返回内容渲染在 `.ant-picker-cell-inner`（24×24 px）内，`position: absolute` 以最近定位祖先为参考；`top: -26` 相对 24 px 的 inner 越界 ~108%，导致角标跨出 cell 顶部。历史 commit `1f94e8c` 修过 dateCellRender 相关错位但未覆盖角标自身定位。

## 验证证据（fresh run，2026-08-04）

| 检查项 | 命令 | 结果 |
|------|------|------|
| TDD RED | `vitest run TaskCalendar.test.tsx -t "绝对定位"`（先改断言后） | FAIL：`expected '-26px' to be '2px'`（证明测试覆盖正确） |
| TDD GREEN | 同上（修复代码后） | `TaskCalendar.test.tsx` 30/30 pass |
| Vitest 全套 | `pnpm --filter @cutegoals/web run test` | 18 Test Files / 176 tests / 176 passed（0 failed） |
| CSS 引用完整性 | `node web/scripts/check-css-loads.mjs` | `✓ All 2 CSS file(s) imported` |
| TypeScript | `pnpm --filter @cutegoals/web exec tsc --noEmit` | 10 个错误，与 base `838e6f8` 完全相同的历史未用变量错误；本 change 未引入新错误 |
| OpenSpec 校验 | `openspec validate fix-calendar-task-badge-alignment --type change --strict` | `Change is valid` |
| 完整 build | `pnpm build`（comet guard build --apply 内置执行） | umi build ✓ 6.02s + mvn compile 通过 |
| 根因消除 | `grep "top: -26\|left: 20" TaskCalendar.tsx` | 代码中已不存在（仅注释中作为历史说明提及） |

## 验证维度（openspec-verify-change 三维度）

### Completeness
- 任务完成度：13/13 ✓（含复现定位、修复、TDD 回归、全量验证、提交、delta spec 补齐）
- Spec 覆盖：delta spec 1 capability（parent-task-calendar）/ 1 MODIFIED Requirement / 4 Scenario，每个 Scenario 有对应实现或测试。

### Correctness
| Scenario | 实现证据 | 状态 |
|------|------|------|
| 角标位置基线（新增） | `TaskCalendar.tsx` `top: 2, left: 2` + vitest 断言 `style.top === '2px'` / `style.left === '2px'` | ✓ |
| 某日有多种类型任务 / 仅 STANDING / 无任务（保留） | 颜色标记逻辑未改动，既有测试覆盖 | ✓ |

注：delta spec 将 requirement 主文中「单元格**右上角** MUST 显示任务总数徽章」修正为「**左上角**」，与用户原始诉求（「显示在天的方框的左上角」）及实现（`left: 2`）一致；原 spec 措辞与 antd dateCellRender 实际渲染位置不符，属 spec 措辞修正。

### Coherence
- Design 决策遵循：决策 1（最小数值修改）✓、决策 2（inline style 断言回归测试）✓、决策 3（delta spec 处理）✓（因 OpenSpec schema 硬要求补齐了 MODIFIED delta）
- 代码模式一致性：注释风格与既有 `// fix-build ...` / `// tweak-calendar-picker-font-size ...` 一致

## Issues by Priority

### CRITICAL
无。

### WARNING
无。

### SUGGESTION
1. **spec 措辞与视觉基线**：delta 已将「右上角」修正为「左上角」。若后续设计师确认角标应在 cell 右上角，需要新的 change 调整 `left` 值并同步 spec；当前以用户明确诉求（左上角）为准。

## Spec 漂移
无未记录漂移。delta spec 的措辞修正已在 design.md Open Questions 与本报告中说明。

## Final Assessment

**所有 CRITICAL/WARNING 检查通过；1 项 SUGGESTION 为记录性说明，不阻塞归档。**

修复聚焦（2 文件 / 9 行）、TDD red-green 证据完整、根因已消除、全量测试通过，满足归档前置条件。可进入 `/comet-archive`。

---

## 第二轮验证（verify-fail 修正循环后，2026-08-04）

### 修正背景

第一轮验证通过后，用户在归档前最终确认阶段选择「需要调整或重新验证」，澄清角标应位于「天的方框的**右上角**」（与 main spec「单元格右上角 MUST 显示任务总数徽章」一致），而非第一轮实现的左上角。按协议运行 `comet state transition verify-fail`（verify_failures: 0 → 1）回退到 build。

### 修正内容（commit `cd480c8`）

- `TaskCalendar.tsx`：`left: 2 → right: 2`（`top: 2` 不变）；`right` 锚定对角标宽度变化（多位数任务数）更稳健
- `TaskCalendar.test.tsx`：断言 `style.left === '2px'` → `style.right === '2px'`
- delta spec：措辞改回「右上角」（与 main spec 一致，不再修改主文措辞），角标位置基线 Scenario 更新为 `top: 2px; right: 2px`
- proposal.md / design.md 同步更新

### 第二轮证据（fresh run）

| 检查项 | 结果 |
|------|------|
| TDD RED | `expected '' to be '2px'`（right 为空，代码未改前） |
| TDD GREEN | `TaskCalendar.test.tsx` 30/30 pass |
| Vitest 全套 | 18 Test Files / 176 tests pass |
| OpenSpec 校验 | `Change is valid`（strict） |
| 完整 build | comet guard build --apply 内置 umi + mvn 通过 |

### 第二轮结论

修正后实现与用户诉求（右上角）及 main spec 措辞一致。0 CRITICAL / 0 WARNING。满足归档前置条件。

---

## 第三轮验证（贴角微调后，2026-08-04）

### 微调背景

第二轮验证通过后，用户在归档确认阶段再次反馈「角标位置仍不对」，随后明确「往右上角再移动个像素」。按协议运行 `comet state transition verify-fail`（verify_failures=1）回退 build，将角标从「距角 2px」微调为贴角。

### 微调内容（commit `f831f5f`）

- `TaskCalendar.tsx`：`top: 2 → 0, right: 2 → 0`（贴日期内盒 `.ant-picker-cell-inner` 右上角）
- `TaskCalendar.test.tsx`：断言同步 `top:'0px' / right:'0px'`
- delta spec 角标位置基线 Scenario 同步 `top: 0px; right: 0px`；design.md / proposal.md 值引用同步

### 第三轮证据（fresh run）

| 检查项 | 结果 |
|------|------|
| Vitest 全套 | 18 Test Files / 176 tests pass |
| check-css-loads | 通过 |
| OpenSpec 校验 | `Change is valid`（strict） |
| 完整 build | comet guard build --apply 内置 umi + mvn 通过 |

### 第三轮结论

角标已贴日期内盒右上角（top:0/right:0），0 CRITICAL / 0 WARNING。满足归档前置条件。
