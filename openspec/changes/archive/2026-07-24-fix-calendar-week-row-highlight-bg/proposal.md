# Fix: 日历周/日高亮改用半透明浅蓝背景矩形

## 问题

`/parent/tasks` 日历面板的高亮样式与单选行为不符合产品期望：

1. **样式错误**：当前周/日高亮使用 `boxShadow: inset 0 0 0 2px var(--ant-color-primary)`（primary 蓝色边框），产品期望是「半透明的浅蓝背景的矩形」（background 而非 border）。
2. **联动错误**：当用户点击某一周时，周号行被高亮，但**该周内的所有日期 cell 也被一并高亮**（`dateCellRender` 内 `dateStr >= selectedRange.startDate && dateStr <= selectedRange.endDate` 把周范围当日期范围处理）。产品期望周选中时只高亮周号行，日期 cell 不跟随高亮。
3. **当前行为还残留**：选中一周后，周内日期的高亮「看起来」像是普通日期选中态，未体现「周选中 → 单选周号行」的语义。

## 根因

- `WeekNumberColumn` 高亮走 `boxShadow`。
- `dateCellRender` 内的 `isSelected` 仅用日期范围判断，未区分 `selectedRange.type === 'day'` 与 `selectedRange.type === 'week'`。

## 修复目标

1. 周号行选中态：`backgroundColor = 半透明浅蓝`，配合同色边框作为外框；移除 `boxShadow` 视觉。
2. 日期 cell 选中态：同样改为 `backgroundColor = 半透明浅蓝`（保持选中态视觉一致）。
3. 选中一周时，**只高亮周号行**，周内日期 cell 不再被标记 `data-selected="true"`（不画高亮）。
4. 选中一天时，仅该日期 cell 获得高亮；其他日期 cell `data-selected="false"`。

## 非目标

- 不改动 `selectedRange` 状态机本身（`taskType: 'day' / 'week' / 'month'` 与 `viewAllMode` 切换逻辑保持不变）。
- 不改动 `weekReducer` 与 `calendarReducer`。
- 不改动 antd `<Calendar>` 的 `value` 通道与 className 分流（`task-calendar-current-month` / `task-calendar-non-current-month`）。
- 不引入新 capability / 新 public API / 新 schema。

## 验收

- 用户点击周号 → 周号行背景为半透明浅蓝矩形；该周内所有日期 cell 不显示高亮。
- 用户点击日期 → 仅该日期 cell 显示半透明浅蓝矩形；其他日期 cell 与所有周号行均无高亮。
- 默认状态（仅 `selectedRange = 今天 day`）→ 今日 cell 显示高亮，其他日期/周号均无高亮（与现有 `fix-calendar-default-current-date` 行为兼容）。
- 已有回归测试继续通过（`selectedRange` 单选语义保持）。
