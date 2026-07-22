import React from 'react';
import dayjs from 'dayjs';
import weekOfYear from 'dayjs/plugin/weekOfYear';
import { Calendar, Badge, Spin, Alert, Button } from 'antd';
import { useApi } from '@shared/hooks/useApi';

dayjs.extend(weekOfYear);

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
  baseMonth: string;                // 'YYYY-MM'
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
    <div data-testid={`week-column-${year}-${month}`} style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
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
              padding: '4px 8px',
              cursor: 'pointer',
              fontSize: 12,
              textAlign: 'center',
              lineHeight: '28px',
              minHeight: 36,
              backgroundColor: hasTasks ? 'var(--ant-color-info-bg)' : undefined,
              borderBottom: '1px solid #f0f0f0',
              boxShadow: isSelected
                ? 'inset 0 0 0 2px var(--ant-color-primary)'
                : undefined,
              userSelect: 'none',
            }}
          >
            第{row.weekNum}周
          </div>
        );
      })}
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

export function CalendarPanel({
  year,
  month,
  selectedRange,
  onSelect,
}: CalendarPanelProps) {
  const apiPath = `/task-assignments/calendar?year=${year}&month=${month}`;
  const { data: calendarData, loading, error, refetch } = useApi<CalendarData>(apiPath);
  const monthDate = dayjs(`${year}-${String(month).padStart(2, '0')}-01`);

  // ── 加载态 ──
  if (loading) {
    return (
      <div data-testid={`calendar-panel-${year}-${month}`} style={{ textAlign: 'center', padding: '40px 0' }}>
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
      selectedRange &&
      dateStr >= selectedRange.startDate &&
      dateStr <= selectedRange.endDate;

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
          boxShadow: isSelected
            ? 'inset 0 0 0 2px var(--ant-color-primary)'
            : undefined,
        }}
      >
        <div>{date.date()}</div>
        {total > 0 && <Badge count={total} size="small" offset={[-2, 2]} />}
      </div>
    );
  };

  return (
    <div data-testid={`calendar-panel-${year}-${month}`} style={{ display: 'flex' }}>
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
        <CalendarHeader
          year={year}
          month={month}
          onSelect={onSelect}
        />
        <Calendar
          value={monthDate}
          fullscreen={false}
          headerRender={() => null}
          dateCellRender={renderDateCell}
          onSelect={(date) =>
            onSelect({ type: 'SELECT_DATE', date: date.format('YYYY-MM-DD') })
          }
        />
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
