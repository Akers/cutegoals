# Proposal: fix-task-calendar-cell-render

## Why

家长端「任务分配」页面（`/parent/tasks`）的月历卡片存在两个视觉 bug：

1. **日期 cell 出现两个天数数字**：每个 cell 同时显示一个 `01-30` 的两位带前导零数字，与一个 `1-30` 的自然位数数字。
2. **周号列与日历行未对齐**：左侧周号列从面板顶部开始排布，但右侧 antd `<Calendar>` 上方还有月份标题与星期表头行，两边行高也相互独立，导致周号 1 并不对应日历第一周。

用户期望：

- 日期 cell 只显示一个数字，10 以内为一位数（`1`、`2`、…、`9`），10 及以上为两位数（`10`、…、`30`），即 dayjs `date.date()` 的自然位数输出。
- 周号 1~6 与 antd `<Calendar>` 的六个日期行视觉对齐。

## 根因分析

来源：explorer 侦察 `web/src/parent/components/TaskCalendar.tsx`。

### Bug 1：重复数字

组件使用 antd 旧版 `dateCellRender` API：

```tsx
// TaskCalendar.tsx:214-252
const renderDateCell = (date: dayjs.Dayjs) => {
  // ...
  return (
    <div ...>
      <div>{date.date()}</div>          {/* ← 自定义渲染的第二个数字 */}
      {total > 0 && <Badge ... />}
    </div>
  );
};

// TaskCalendar.tsx:267-273
<Calendar dateCellRender={renderDateCell} ... />
```

`dateCellRender` 的 antd 语义是「向 cell **追加**内容」，rc-picker 仍会渲染默认 cell value（`01`、`02`、…），自定义代码又显式渲染 `date.date()`，因此真实 DOM 同时出现两个数字。

单元测试 `web/src/parent/components/__tests__/TaskCalendar.test.tsx` 把 `dateCellRender` 的返回值当作整个 cell 的唯一内容（`vi.mock('antd', ...)`），与真实 antd 行为不一致，因此 CI 一直绿但生产踩坑——与之前 dayjs hotfix 同类型的「测试 mock 掩盖生产 bug」。

### Bug 2：周号列未对齐

```tsx
// TaskCalendar.tsx:254-266
<div style={{ display: 'flex' }}>
  <WeekNumberColumn ... />
  <div style={{ flex: 1 }}>
    <CalendarHeader ... />        {/* ← 月份标题，周号列没有对应占位 */}
    <Calendar ... />              {/* ← 内部还有星期表头行，周号列也没有对应占位 */}
  </div>
</div>
```

周号列自身：

```tsx
// TaskCalendar.tsx:115-168
<div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
  {rows.map(row => /* 固定 lineHeight:28 + minHeight:36 的周号行 */)}
</div>
```

两个独立布局系统的行高完全解耦：周号行固定 36px，Calendar 内部行高由 antd CSS 与 cell 内容决定，且周号列缺少与 `CalendarHeader` + 星期表头等高的占位，因此从首行起就错位。

## What Changes

修改 `web/src/parent/components/TaskCalendar.tsx`：

1. 消除日期 cell 内重复的 `date.date()` 渲染，让 antd `<Calendar>` 自己负责唯一的天数数字（保留自定义背景与 Badge）。
2. 让左侧 `WeekNumberColumn` 的首行与 antd `<Calendar>` 的首个日期行对齐：在周号列顶部补偿 `CalendarHeader` 高度 + 星期表头高度，并使六个周号行与六个 antd 日期行采用同一行高基准。

可选修改 `web/src/parent/components/__tests__/TaskCalendar.test.tsx`：

- 调整 antd mock 使其接近真实 cell 语义（默认数字 + 自定义追加）。
- 新增回归断言：自定义渲染函数返回的 JSX 不再包含独立的天数数字 `<div>`，避免再次被 mock 掩盖。

## Impact

- 受影响用户：所有访问 `/parent/tasks` 的家长端用户。
- 受影响组件：仅 `TaskCalendar`。
- 不影响：后端、其他家长端页面、儿童端、管理员端。

## Non-Goals

- 不重构周号的位置（保持现状在 antd `<Calendar>` 左侧，不迁入星期表头或首格）。
- 不重设计视觉语言、配色、字体、间距系统。
- 不引入 `@designer` 介入。
- 不调整 `computeWeekNumbers` 的算法（已正确生成 6 行）。
- 不修改全局 CSS / 主题 token / `app.tsx`。
