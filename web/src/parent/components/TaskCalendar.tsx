import React from 'react';
import dayjs from 'dayjs';
import { Calendar, Button } from 'antd';

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
        <div className="calendar-panel">
          <CalendarHeader
            year={year}
            month={month}
            onSelect={onSelect}
          />
          <Calendar
            value={currentMonth}
            fullscreen={false}
            headerRender={() => null}
            dateCellRender={(date) => <div>{date.date()}</div>}
          />
        </div>

        {/* 第二个月份面板 */}
        <div className="calendar-panel">
          <CalendarHeader
            year={nextMonth.year()}
            month={nextMonth.month() + 1}
            onSelect={onSelect}
          />
          <Calendar
            value={nextMonth}
            fullscreen={false}
            headerRender={() => null}
            dateCellRender={(date) => <div>{date.date()}</div>}
          />
        </div>
      </div>
    </div>
  );
}
