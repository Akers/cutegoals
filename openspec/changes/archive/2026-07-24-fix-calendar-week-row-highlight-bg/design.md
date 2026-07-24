# Design: 周/日高亮改用半透明浅蓝背景矩形

## 方案

### 1. 高亮配色

使用 antd 5 主色 `var(--ant-color-primary)` (`#1677ff`) 的低透明度作为半透明浅蓝背景：

```css
background-color: rgba(22, 119, 255, 0.12);
```

透明度 0.12 在浅色主题下提供足够对比度，与已有 `var(--ant-color-info-bg)` (#e6f4ff) 区分明显（更饱和、更偏主蓝）。

### 2. WeekNumberColumn 高亮样式

当前：
```tsx
boxShadow: isSelected ? 'inset 0 0 0 2px var(--ant-color-primary)' : undefined,
```

改为：
```tsx
backgroundColor: isSelected ? 'rgba(22, 119, 255, 0.18)' : (hasTasks ? 'var(--ant-color-info-bg)' : undefined),
border: isSelected ? '1px solid rgba(22, 119, 255, 0.5)' : undefined,
```

说明：
- 选中态用 `0.18` 透明度（比 0.12 更强）以视觉压过原有 task 提示色。
- 边框用 1px 半透明蓝作为「矩形外框」，呼应产品对「矩形框选」的描述。
- 不再使用 `boxShadow`，避免视觉上仍是「边框内描」语义。

### 3. dateCellRender 高亮样式

当前：
```tsx
boxShadow: isSelected ? 'inset 0 0 0 2px var(--ant-color-primary)' : undefined,
```

改为：
```tsx
boxShadow: isSelected ? 'inset 0 0 0 2px rgba(22, 119, 255, 0.5)' : undefined,
backgroundColor: isSelected ? bgColor ?? 'rgba(22, 119, 255, 0.12)' : bgColor,
```

说明：
- 日期 cell 已经有 `bgColor`（基于任务类型），叠加选中态需要保留任务类型提示色 → 选中时使用 `rgba(22, 119, 255, 0.12)` 作为「无任务背景的选中色」，覆盖在任务类型色之上时会偏向更明显的蓝。
- 边框 `inset 0 0 0 2px rgba(22, 119, 255, 0.5)` 保留以标识「选中」的状态。

### 4. `isSelected` 语义修正

`dateCellRender` 内当前：
```tsx
const isSelected =
  selectedRange && dateStr >= selectedRange.startDate && dateStr <= selectedRange.endDate;
```

改为：
```tsx
const isSelected =
  selectedRange &&
  selectedRange.type === 'day' &&
  dateStr === selectedRange.startDate &&
  dateStr === selectedRange.endDate;
```

仅当 `selectedRange.type === 'day'` 时，日期 cell 才被标记为选中。`week` 类型下所有日期 cell 的 `data-selected="false"`。

## 影响范围

- 仅 `web/src/parent/components/TaskCalendar.tsx` 一文件。
- 测试 `web/src/parent/components/__tests__/TaskCalendar.test.tsx`：
  - 现有断言 `data-selected` 在 week 选中时为 `false`（需要新增周选中下日期全不高亮用例）。
  - 现有断言 `boxShadow` 引用需替换为 `backgroundColor`。
- 不需要 delta spec（修改的是 UI 视觉样式，不影响任何 spec 验收场景）。

## 风险

- 颜色值 0.12 / 0.18 / 0.5 选取基于 antd 默认主题浅色背景，深色主题下可视性需要后续验证（本次 hotfix 范围仅覆盖浅色）。
- 当前 antd 内置 `cell-selected` 仍会画绿底（被 `task-calendar-non-current-month` scoped CSS 抑制），新增的 `data-selected` 视觉与 antd 内置是两套，行为不变。
