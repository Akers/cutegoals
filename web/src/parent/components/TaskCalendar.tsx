import React from 'react';
import dayjs from 'dayjs';
import { Calendar, Badge, Spin, Alert, Button } from 'antd';
import { useApi } from '@shared/hooks/useApi';

// 注：dayjs 的 weekOfYear / weekday 等插件已在 `src/shared/dayjs.ts` 全局注册，
// 由 `src/app.tsx` 顶部 side-effect import 触发，无需在此组件内重复 extend。

// ── TypeScript 类型定义 ──────────────────────────────────────────

export interface CalendarSelection {
  type: 'day' | 'week' | 'month';
  startDate: string; // 'YYYY-MM-DD'
  endDate: string;
}

export type CalendarAction =
  | { type: 'SELECT_DATE'; date: string }
  | { type: 'SELECT_WEEK'; startDate: string }
  | { type: 'SELECT_MONTH'; year: number; month: number };

interface DayData {
  total: number;
  pending: number;
  submitted: number;
  approved: number;
  rejected: number;
  cancelled: number;
  overdue: number;
  taskTypes: { LIMITED: number; REPEAT: number; STANDING: number };
}

interface CalendarData {
  year: number;
  month: number;
  days: Record<string, DayData>;
}

export interface TaskCalendarProps {
  baseMonth: string; // 'YYYY-MM'
  selectedRange: CalendarSelection | null;
  onSelect: (action: CalendarAction) => void;
  onNavigate: (direction: -1 | 1) => void;
}

// ── CalendarHeader 子组件 ───────────────────────────────────────

export interface CalendarHeaderProps {
  year: number;
  month: number;
  selectedRange: CalendarSelection | null;
  onSelect: (a: CalendarAction) => void;
}

function CalendarHeader({
  year,
  month,
  onSelect,
}: {
  year: number;
  month: number;
  onSelect: (a: CalendarAction) => void;
}) {
  return (
    <div
      onClick={() => onSelect({ type: 'SELECT_MONTH', year, month })}
      style={{
        textAlign: 'center',
        fontWeight: 600,
        padding: '8px 0',
        cursor: 'pointer',
        userSelect: 'none',
      }}
    >
      {year}年{month}月
    </div>
  );
}

// ── 周号计算与 WeekNumberColumn ─────────────────────────────────

export interface WeekRow {
  weekNum: number;
  startDate: string; // 'YYYY-MM-DD'
}

export function computeWeekNumbers(year: number, month: number): WeekRow[] {
  const firstDay = dayjs(`${year}-${String(month).padStart(2, '0')}-01`).startOf('month');
  const rows: WeekRow[] = [];
  let current = firstDay.startOf('week'); // Sunday
  for (let i = 0; i < 6; i++) {
    rows.push({ weekNum: current.week(), startDate: current.format('YYYY-MM-DD') });
    current = current.add(1, 'week');
  }
  return rows;
}

export interface WeekNumberColumnProps {
  year: number;
  month: number;
  calendarData: CalendarData | undefined;
  selectedRange: CalendarSelection | null;
  onSelect: (action: CalendarAction) => void;
}

export function WeekNumberColumn({
  year,
  month,
  calendarData,
  selectedRange,
  onSelect,
}: WeekNumberColumnProps) {
  const rows = computeWeekNumbers(year, month);

  return (
    <div
      data-testid={`week-column-${year}-${month}`}
      style={{ display: 'flex', flexDirection: 'column', gap: 0 }}
    >
      {/* 占位行：与右侧 antd Calendar 表头几何精确对齐。
          数值来自 explorer 在真实 Chromium 中对 antd 5.29.3
          <Calendar fullscreen={false} headerRender={() => null} /> 的实测：
            .ant-picker-panel border-top = 1 px
            .ant-picker-body padding-top = 8 px
            .ant-picker-content thead（Mo/Tu/...） = 18 px
                                           （token weekHeight = controlHeightSM * 0.75 = 24 * 0.75）
          合计 1 + 8 + 18 = 27 px，使周号列第一行与 antd 第一个日期行顶部对齐。 */}
      <div
        data-testid={`week-column-weekday-spacer-${year}-${month}`}
        style={{
          height: 27,
          borderBottom: '1px solid #f0f0f0',
          flexShrink: 0,
        }}
      />
      {rows.map((row) => {
        // 检查该周是否有任务
        const weekStart = dayjs(row.startDate);
        const weekEnd = weekStart.add(6, 'day');
        const hasTasks = calendarData?.days
          ? Object.entries(calendarData.days).some(([date, data]) => {
              const d = dayjs(date);
              return (
                d.isAfter(weekStart.subtract(1, 'day')) &&
                d.isBefore(weekEnd.add(1, 'day')) &&
                data.total > 0
              );
            })
          : false;

        // 检查是否在 selectedRange 中
        const weekStartDate = row.startDate;
        const weekEndDate = weekStart.add(6, 'day').format('YYYY-MM-DD');
        const isSelected =
          selectedRange &&
          selectedRange.startDate <= weekEndDate &&
          selectedRange.endDate >= weekStartDate;

        return (
          <div
            key={row.startDate}
            data-testid={`week-row-${row.weekNum}`}
            data-has-tasks={hasTasks ? 'true' : 'false'}
            data-selected={isSelected ? 'true' : 'false'}
            onClick={() => onSelect({ type: 'SELECT_WEEK', startDate: row.startDate })}
            style={{
              flex: 1,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: '0 8px',
              cursor: 'pointer',
              fontSize: 12,
              textAlign: 'center',
              backgroundColor: hasTasks ? 'var(--ant-color-info-bg)' : undefined,
              borderBottom: '1px solid #f0f0f0',
              boxShadow: isSelected ? 'inset 0 0 0 2px var(--ant-color-primary)' : undefined,
              userSelect: 'none',
            }}
          >
            第{row.weekNum}周
          </div>
        );
      })}
      {/* 底部占位：对齐 antd .ant-picker-body padding-bottom = 8 px，
          使 6 个 flex:1 周号行均分得到 (273 - 27 - 8) / 6 = 39.67 px，
          与 antd tbody 单行 39.67 px 严格一致（见 explorer 取证）。 */}
      <div
        data-testid={`week-column-bottom-padding-${year}-${month}`}
        style={{ height: 8, flexShrink: 0 }}
      />
    </div>
  );
}

// ── CalendarPanel 子组件（独立数据获取）───────────────────────────

export interface CalendarPanelProps {
  year: number;
  month: number;
  selectedRange: CalendarSelection | null;
  onSelect: (action: CalendarAction) => void;
}

export function CalendarPanel({ year, month, selectedRange, onSelect }: CalendarPanelProps) {
  const apiPath = `/task-assignments/calendar?year=${year}&month=${month}`;
  const { data: calendarData, loading, error, refetch } = useApi<CalendarData>(apiPath);
  const monthDate = dayjs(`${year}-${String(month).padStart(2, '0')}-01`);

  // ── 加载态 ──
  if (loading) {
    return (
      <div
        data-testid={`calendar-panel-${year}-${month}`}
        style={{ textAlign: 'center', padding: '40px 0' }}
      >
        <Spin />
      </div>
    );
  }

  // ── 错误态 ──
  if (error) {
    return (
      <div data-testid={`calendar-panel-${year}-${month}`}>
        <Alert
          type="error"
          message="加载失败"
          action={
            <a onClick={refetch} style={{ cursor: 'pointer' }}>
              重试
            </a>
          }
        />
      </div>
    );
  }

  // ── dateCellRender ──
  const renderDateCell = (date: dayjs.Dayjs) => {
    const dateKey = date.format('YYYY-MM-DD');
    const dayData = calendarData?.days?.[dateKey];
    const total = dayData?.total ?? 0;

    // 优先级：LIMITED > REPEAT > STANDING
    let bgColor: string | undefined;
    if (dayData?.taskTypes.LIMITED > 0) {
      bgColor = 'var(--ant-color-error-bg)'; // 淡红
    } else if (dayData?.taskTypes.REPEAT > 0) {
      bgColor = 'var(--ant-color-info-bg)'; // 淡蓝
    } else if (dayData?.taskTypes.STANDING > 0) {
      bgColor = 'var(--ant-color-success-bg)'; // 淡绿
    }

    // 选中高亮
    const dateStr = date.format('YYYY-MM-DD');
    const isSelected =
      selectedRange && dateStr >= selectedRange.startDate && dateStr <= selectedRange.endDate;

    return (
      <div
        data-bg={bgColor ?? ''}
        data-selected={isSelected ? 'true' : 'false'}
        style={{
          backgroundColor: bgColor,
          borderRadius: 4,
          padding: '2px 4px',
          minHeight: 30,
          position: 'relative',
          boxShadow: isSelected ? 'inset 0 0 0 2px var(--ant-color-primary)' : undefined,
        }}
      >
        {/* 不再渲染独立天数数字：antd Calendar 默认会在 cell 中输出日期数字，
            dateCellRender 的语义是「追加内容」，再渲染一次会造成同一 cell 出现两个数字。 */}
        {total > 0 && <Badge count={total} size="small" offset={[-2, 2]} />}
      </div>
    );
  };

  return (
    <div
      data-testid={`calendar-panel-${year}-${month}`}
      style={{ display: 'flex', flexDirection: 'column' }}
    >
      {/* 月份标题：跨越面板全宽，避免周号列从月份标题位置开始排布导致整体错位 */}
      <CalendarHeader year={year} month={month} onSelect={onSelect} />
      {/* 周号列 + 日历主体并排；align-items: stretch 让两侧同高，
          周号列内部 6 行 flex:1 均分剩余高度，逐行对齐日历 6 个日期行 */}
      <div style={{ display: 'flex', alignItems: 'stretch' }}>
        {/* 左侧周号列 */}
        <WeekNumberColumn
          year={year}
          month={month}
          calendarData={calendarData}
          selectedRange={selectedRange}
          onSelect={onSelect}
        />
        {/* 右侧日历主体 */}
        <div style={{ flex: 1, minWidth: 0 }}>
          <Calendar
            value={monthDate}
            fullscreen={false}
            headerRender={() => null}
            dateCellRender={renderDateCell}
            onSelect={(date) => onSelect({ type: 'SELECT_DATE', date: date.format('YYYY-MM-DD') })}
          />
        </div>
      </div>
    </div>
  );
}

// ── 主组件 ───────────────────────────────────────────────────────

export function TaskCalendar({
  baseMonth,
  selectedRange,
  onSelect,
  onNavigate,
}: TaskCalendarProps) {
  // 解析 baseMonth
  const [year, month] = baseMonth.split('-').map(Number);

  // 计算当前月和下月
  const currentMonth = dayjs(baseMonth + '-01');
  const nextMonth = currentMonth.add(1, 'month');

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* 响应式媒体查询：小屏幕两列变一列 */}
      <style>{`
        @media (max-width: 767px) {
          .task-calendar-grid { grid-template-columns: 1fr !important; }
        }
      `}</style>

      {/* 导航栏 */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          gap: 16,
        }}
      >
        <Button onClick={() => onNavigate(-1)}>{'<'}</Button>
        <span>
          {currentMonth.format('YYYY年M月')} — {nextMonth.format('YYYY年M月')}
        </span>
        <Button onClick={() => onNavigate(1)}>{'>'}</Button>
      </div>

      {/* 双月面板：CSS Grid 自适应布局 */}
      <div
        className="task-calendar-grid"
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(400px, 1fr))',
          gap: 16,
        }}
      >
        {/* 第一个月份面板 */}
        <CalendarPanel
          year={year}
          month={month}
          selectedRange={selectedRange}
          onSelect={onSelect}
        />

        {/* 第二个月份面板 */}
        <CalendarPanel
          year={nextMonth.year()}
          month={nextMonth.month() + 1}
          selectedRange={selectedRange}
          onSelect={onSelect}
        />
      </div>
    </div>
  );
}
