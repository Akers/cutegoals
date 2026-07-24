# Design: fix-calendar-non-current-no-highlight-v2

## 修复方案（单一方案，hotfix 模式）

### 方案概述

对 `TaskCalendar.tsx` CalendarPanel 做两处增量改动，恢复「`value` 驱动显示月份」语义的同时，通过 scoped CSS 抑制非当前月 wrapper 内的 antd 选中样式：

```tsx
// CalendarPanel 内层
<div
  style={{ flex: 1, minWidth: 0 }}
  className={isCurrentMonth ? 'task-calendar-current-month' : 'task-calendar-non-current-month'}
>
  <Calendar
    value={monthDate}        // 月份显示（当前月 = today，非当前月 = monthDate 均可）
    fullscreen={false}
    headerRender={() => null}
    dateCellRender={renderDateCell}
    onSelect={(date) => onSelect({ type: 'SELECT_DATE', date: date.format('YYYY-MM-DD') })}
  />
</div>
```

并在 TaskCalendar 顶部 `<style>` 块（已为响应式存在）追加：

```css
.task-calendar-non-current-month .ant-picker-cell-selected .ant-picker-calendar-date,
.task-calendar-non-current-month .ant-picker-cell-selected .ant-picker-calendar-date-today {
  background: transparent !important;
}
.task-calendar-non-current-month .ant-picker-cell-selected .ant-picker-calendar-date-value {
  color: inherit !important;
}
```

### 关键决策

#### 决策 1：CSS override vs 阻断 antd 选中状态

候选：

- A. **CSS scoped override**（本次选择）— 加 wrapper class，CSS 局部压制绿底/字色。优点：直接、局部、可视化准确匹配用户期望；缺点：依赖选择器稳定性（antd 已暴露给消费者继承的 CSS 钩子，选择器结构稳定多年）。
- B. **替换为自渲染日期网格** — 不再用 antd `<Calendar>`，自己渲染 7x6 网格。优点：完全控制；缺点：放弃 antd 的 hover/focus/keyboard/selectable 协议，工作量超 hotfix 范围。
- C. **欺骗 antd 关于 value** — 试图让 antd 认为 `mergedValue=monthDate` 但实际没有 cell 匹配（如 `value=1900-08-01`）。缺点：break 月份显示（1900 年）。

**选择**：方案 A。

理由：
- antd 的 `&-in-view&-cell-selected` 规则使用 `ant-picker-cell-selected` 类 + `.ant-picker-calendar-date` 内部选择器；这些类名在 antd 5.x 是稳定的 public surface，且本项目已在 styles 入口挂载 antd 已编译样式（`@import` 或 `import 'antd/dist/reset.css'`）。
- 当前我方 CalendarPanel 在 flex 容器里只渲染一个 antd Calendar；wrapper className 不会与其它 Calendar 串扰。
- `!important` 仅在 wrapper 内部生效，不污染全局。

#### 决策 2：是否保留上次 hotfix 引入的 `defaultValue={monthDate}` 与 `value=undefined`

**选择**：移除上次 hotfix 的「defaultValue+undefined」控制逻辑，恢复为 `value={monthDate}`。

理由：
- `value={monthDate}` 与 `value={today}` 在「显示月份」上等价（antd 都用 `value.year()/month()` 推导显示月份）；`value=undefined` 会让 antd 回退 defaultValue，反而**回归**到上次 hotfix 想避免的行为。
- 当前月的高亮由 `value=today` 自然给出，无需额外机制。
- 简化代码、减少心智负担。

#### 决策 3：测试断言策略

旧 mock 仅暴露 `data-value` 属性，不验证 `.ant-picker-cell-selected` 类是否实际应用。**本 change 升级 mock 暴露以下信息**：

| 信息 | 用途 |
|------|------|
| `data-value={value.format('YYYY-MM-DD')}` | 维持旧断言形态（回归测试仍能跑） |
| `data-class-name`（由 mock 实现生成） | 让测试能验证 wrapper 上的 `task-calendar-non-current-month` 是否传入 mock 实例 |

但 mock 本身是简化的 div，CSS override 在 jsdom 中无法渲染计算样式 — **不通过 jsdom 端 CSS 验证**。

替代策略：
- 单元测试断言 wrapper className 正确（mock 外层加 className 即可反映）
- 由 design/verify 文档明确写：浏览器手验是最终验证

### 不引入

- 不修改 `calendarReducer`。
- 不引入新 props、不改接口协议。
- 不引入 CSS-in-JS、不引入外部样式文件。
- 不引入 antd 之外的依赖。

### 回归测试策略

`web/src/parent/components/__tests__/TaskCalendar.test.tsx`：

1. **Mock 改造**：mock `<Calendar>` 收到的 props 增加 `hostClassName` 字段（在 mock 外层 div 反映 wrapper className）。原 TaskCalendar 已通过外层 div 上的 `data-testid={\`calendar-panel-${year}-${month}\`}` 定位面板；本次让 mock 读取外部传入的 `hostClassName` 写到 mock 的外层 div 上。

   简化方案：mock 不从 props 读 className（避免耦合），而由被测代码传 `data-current-month` 给 antd Calendar 实例的 host div，再由测试通过 querySelector 读取。**最终方案**：让 mock 的 host div 上加 `data-task-calendar-mode={isCurrentMonth ? 'current' : 'non-current'}` 属性，行为由 mock 内部根据 value 计算（值在 today 月份则 current，否则 non-current）。

2. **新增断言**（描述「v2 视觉抑制」）：
   - 当 `selectedRange={day: '2026-07-24'}`、`baseMonth='2026-07'`：七月 panel mock `data-task-calendar-mode === 'current'`，八月 panel mock `data-task-calendar-mode === 'non-current'`。
   - 这是本次 fix 的最小可测行为契约：wrapper className 被正确应用。

3. **保留旧断言**：上次的 `not.toHaveAttribute('data-value')` 断言会被本次还原 `value={monthDate}` 而变化。本次删去 / 调整旧断言到「wrapper 分流」「CSS 抑制存在」两条。

### 风险

- antd 类名后续 major 版本可能改。本项目 antd `^5.29.3` semver 允许 minor 升级；CSS 选择器稳定性高，多数 minor 升级不会改这些类名。
- `!important` 一旦 antd 自己再次提升选择器特异性（如未来加 inset 阴影）需要重新覆盖；属于 hotfix 范畴的可接受 trade-off。
- 当用户在 8 月面板点击 cell 时，antd 内部仍把该 cell 设为「选中」（mergedValue 跟随 click 更新）。CSS 抑制保持不变，视觉仍无绿底；custom boxShadow 仍因 selectedRange 更新落到该 cell。这是预期行为。
