# Design: fix-calendar-task-badge-alignment

## Context

`web/src/parent/components/TaskCalendar.tsx` 的 `renderDateCell`（line 250-286）通过 antd `<Calendar>` 的 `dateCellRender` 钩子返回任务数角标：

```tsx
<span
  data-testid={`task-badge-${dateKey}`}
  style={{
    position: 'absolute',
    zIndex: 1,
    top: -26,   // ← 修复：0
    left: 20,   // ← 修复：right: 0
    backgroundColor: '#ff4d4f',
    ...
  }}
>
  {total}
</span>
```

**当前 bug**：`top: -26 / left: 20` 让角标实际渲染到上一周日期行。2026-08-03 / 05 / 07 三个有任务日的角标视觉位置见截图取证（observer 分析：DOM `data-testid="task-badge-2026-08-03"`，内联 `top: -26px; left: 20px;`，角标中心 y≈145 而日期中心 y≈178，垂直错位 ~33 px，跨入上一周日期行）。

**根因**：antd 5.x 的 `dateCellRender` 返回内容被渲染到 `.ant-picker-cell-inner`（24 × 24 px 日期内盒）内部，`position: absolute` 以该内盒左上角为定位参考（observer 双数据点实测确认：`top:-26` → y=166-26=140，`left:20` → x=500+20=520）。`top: -26` 相对 24 px 内盒向上越界 ~108%，跨出 cell 顶部进入上一行。历史 commit `1f94e8c` 修过 dateCellRender 相关错位但未覆盖角标自身定位。

**目标位置**：用户在归档确认阶段明确澄清——角标应位于「天的方框的**右上角**」，与 main spec「任务类型颜色标记」requirement 既有措辞「单元格右上角 MUST 显示任务总数徽章」一致。

## Goals / Non-Goals

**Goals**
- 角标实际渲染位置在日期内盒（`.ant-picker-cell-inner`）**右上角（贴角）** 处（`top: 0, right: 0`）
- 最小改动（`TaskCalendar.tsx` 2 行定位值），不动其他视觉属性
- 修正既有 vitest 断言（原断言锁定了 buggy 值），作为定位值的回归护栏
- delta spec 补齐角标位置基线 Scenario（原 spec 未规定定位数值）
- 不引入新依赖

**Non-Goals**
- 不调整 antd `Calendar` 组件 API
- 不重构为 antd `fullCellRender`（会替换整个 cell 渲染，引入新回归面）
- 不修改角标颜色 / 字号 / 形状
- 不修复其他日历视觉 bug

## Decisions

### 决策 1：最小数值修改（top: -26 → 2, left: 20 → right: 0）

- **选择**：`top: -26 → top: 0`，删除 `left: 20` 改为 `right: 0`，保留 `position: absolute`
- **理由**：
  - 定位参考是 `.ant-picker-cell-inner`（24×24 日期内盒，observer 实测确认）；
  - `top: 0` 让角标贴内盒顶部——内盒上方在 `<td>` 内还有 ~7 px 空间（行高 ~38 px、内盒 24 px 居中），角标不会跨出 cell 顶部；
  - `right: 0` 让角标右边缘贴内盒右边缘，即日期方框右上角；对角标宽度变化（多位数任务数时 `minWidth: 10` 撑宽）也保持右锚定，比 `left` 值更稳健；
  - 与 main spec「右上角」措辞一致。
- **备选 A（`top: 2, left: 2` 左上角）**：初版实现采用，用户在归档确认阶段澄清应为右上角，故改为 `right: 0`
- **备选 B（重构为 `fullCellRender`）**：渲染整个 cell（含日期数字），工程量翻倍且引入新回归面，放弃
- **备选 C（antd `<Badge>` 组件）**：不支持 Calendar dateCellRender 的小盒场景，放弃

### 决策 2：回归测试断言内联 style 值

- **选择**：修正 `TaskCalendar.test.tsx` 既有测试「任务数角标使用绝对定位」的断言为 `style.top === '0px'` 且 `style.right === '0px'`
- **理由**：直接断言 inline style 不依赖 jsdom 的 getComputedStyle 实现差异；原断言锁定了 buggy 值（`-26px / 20px`）正是 bug 长期存在的原因，必须修正
- **TDD 证据**：先改断言跑红（`expected '' to be '2px'`，right 为空）→ 改代码 → 跑绿（30/30）

### 决策 3：创建 MODIFIED delta spec 补齐位置基线

- OpenSpec schema 要求 change 至少有一个 delta；且原 spec 未规定定位数值导致 buggy 值长期存在
- delta 保持「任务类型颜色标记」requirement 主文不变（仍为「右上角」），仅新增 Scenario「角标位置基线 (fix-calendar-task-badge-alignment)」明确 `top: 0px; right: 0px` 基线

## Risks / Trade-offs

- **[antd 升级风险]** antd 6.x 若改变 `.ant-picker-cell-inner` 尺寸或定位上下文，角标可能再次错位 → 缓解：`top: 0 / right: 0` 为小偏移量，在合理参考系下仍靠近右上角；vitest 断言提供护栏；视觉回归由 E2E / 人工 preview 兜底
- **[多位数角标宽度]** 任务数 ≥ 10 时角标撑宽（`minWidth: 10` + `padding: 0 2px`），`right: 0` 保持右锚定，角标向左扩展，不会超出内盒右缘 → 可接受
- **[角标与日期数字重叠]** 内盒 24×24，角标 10×10 在右上角，日期数字（16px 字号）居中偏下，重叠面积极小 → 可接受

## Migration Plan

无需迁移。不改 API / DB / theme / 依赖；部署即生效；回滚：revert 提交即可。

## Open Questions

（无；用户已在归档确认阶段澄清目标位置为右上角）
