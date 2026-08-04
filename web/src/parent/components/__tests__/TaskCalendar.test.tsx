import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

// ── Mock useApi ───────────────────────────────────────────────────
vi.mock('@shared/hooks/useApi', () => ({ useApi: vi.fn() }));
import { useApi } from '@shared/hooks/useApi';
const mockUseApi = vi.mocked(useApi);

import { TaskCalendar, computeWeekNumbers } from '../TaskCalendar';
import type { CalendarAction } from '../TaskCalendar';

// ── Mock antd ─────────────────────────────────────────────────────
// 仅 mock 必要部分,避免引入真实 antd 的 rc-picker / CSS-in-JS 副作用。
vi.mock('antd', () => {
  const React = require('react');
  const dayjs = require('dayjs');

  return {
    Button: ({ children, onClick }: any) =>
      React.createElement('button', { onClick }, children),
    Badge: ({ count, children }: any) => {
      const elements: any[] = [];
      if (children) elements.push(children);
      elements.push(
        React.createElement(
          'sup',
          { key: 'count', className: 'ant-scroll-number ant-badge-count' },
          count,
        ),
      );
      return React.createElement('span', { className: 'ant-badge' }, ...elements);
    },
    Spin: () => React.createElement('div', { className: 'ant-spin' }),
    Alert: ({ message, action, type }: any) =>
      React.createElement(
        'div',
        { className: `ant-alert ant-alert-${type}` },
        React.createElement('div', { className: 'ant-alert-message' }, message),
        action ? React.createElement('div', { className: 'ant-alert-action' }, action) : null,
      ),
    Calendar: vi
      .fn()
      .mockImplementation(({ value, defaultValue, dateCellRender, onSelect }: any) => {
        if (!value && !defaultValue) {
          return React.createElement('div', { 'data-testid': 'mock-calendar-empty' });
        }
        // 用 value ?? defaultValue 推导显示月份
        const anchor = value ?? defaultValue;
        const year = anchor.year();
        const month = anchor.month(); // 0-based
        const daysInMonth = anchor.daysInMonth();
        const monthStr = `${year}-${String(month + 1).padStart(2, '0')}`;
        const cells: React.ReactNode[] = [];
        for (let d = 1; d <= daysInMonth; d++) {
          const date = dayjs(new Date(year, month, d));
          const cellContent = dateCellRender?.(date);
          cells.push(
            React.createElement(
              'div',
              { key: d, 'data-testid': `date-cell-${d}`, onClick: () => onSelect?.(date) },
              React.createElement('span', { 'data-testid': 'antd-default-date' }, d),
              cellContent,
            ),
          );
        }
        const props: any = { 'data-testid': `mock-calendar-${monthStr}` };
        if (value) props['data-value'] = value.format('YYYY-MM-DD');
        if (defaultValue) props['data-default-value'] = defaultValue.format('YYYY-MM-DD');
        return React.createElement('div', props, ...cells);
      }),
  };
});

// ── 测试数据 ──────────────────────────────────────────────────────
const baseMonth = '2026-07';

const mockCalendarData = {
  year: 2026,
  month: 7,
  days: {
    '2026-07-01': {
      total: 3, pending: 1, submitted: 1, approved: 0, rejected: 0, cancelled: 0, overdue: 1,
      taskTypes: { LIMITED: 1, REPEAT: 2, STANDING: 0 },
    },
    '2026-07-05': {
      total: 2, pending: 0, submitted: 0, approved: 0, rejected: 0, cancelled: 0, overdue: 2,
      taskTypes: { LIMITED: 0, REPEAT: 2, STANDING: 0 },
    },
    '2026-07-15': {
      total: 1, pending: 0, submitted: 0, approved: 0, rejected: 0, cancelled: 0, overdue: 0,
      taskTypes: { LIMITED: 0, REPEAT: 0, STANDING: 1 },
    },
    '2026-07-20': {
      total: 5, pending: 2, submitted: 1, approved: 1, rejected: 0, cancelled: 0, overdue: 1,
      taskTypes: { LIMITED: 2, REPEAT: 1, STANDING: 2 },
    },
  },
};

/** 获取 baseMonth 对应的单月面板作用域（TaskCalendar 现在只渲染 1 个 CalendarPanel） */
function singlePanel() {
  return screen.getByTestId('calendar-panel-2026-7');
}

// ── 主测试套件 ────────────────────────────────────────────────────
describe('TaskCalendar - 单月日历组件', () => {
  const defaultProps = {
    baseMonth,
    selectedRange: null,
    onSelect: vi.fn(),
    onNavigate: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    mockUseApi.mockReturnValue({
      data: mockCalendarData,
      loading: false,
      error: undefined,
      refetch: vi.fn(),
    });
  });

  // ═══════════════ 1: 单月渲染 ═══════════════

  describe('单月渲染 (1)', () => {
    it('渲染导航栏与单月月份标题 (nav + panel header 各显示一次当前月)', () => {
      render(<TaskCalendar {...defaultProps} />);
      // '2026年7月' 同时出现在 nav <span> 和 panel 内 CalendarHeader
      expect(screen.getAllByText('2026年7月')).toHaveLength(2);
      expect(screen.getByText('<')).toBeInTheDocument();
      expect(screen.getByText('>')).toBeInTheDocument();
    });

    it('渲染单个月历面板 (mock-calendar-2026-07 存在, 08 / 09 / 10 等下月不存在)', () => {
      render(<TaskCalendar {...defaultProps} />);
      expect(screen.getByTestId('mock-calendar-2026-07')).toBeInTheDocument();
      expect(screen.queryByTestId('mock-calendar-2026-08')).not.toBeInTheDocument();
      expect(screen.queryByTestId('calendar-panel-2026-8')).not.toBeInTheDocument();
      expect(screen.queryByText('2026年8月')).not.toBeInTheDocument();
    });

    it('跨年时显示单月标题 (baseMonth=2026-12 时显示 2026年12月,无 2027年1月)', () => {
      render(<TaskCalendar {...defaultProps} baseMonth="2026-12" />);
      expect(screen.getAllByText('2026年12月')).toHaveLength(2);
      expect(screen.queryByText('2027年1月')).not.toBeInTheDocument();
      expect(screen.getByTestId('mock-calendar-2026-12')).toBeInTheDocument();
      expect(screen.getByTestId('calendar-panel-2026-12')).toBeInTheDocument();
    });
  });

  // ═══════════════ 2: 导航按钮 ═══════════════

  describe('导航按钮 (2)', () => {
    it('点击 < 按钮触发 onNavigate(-1)', async () => {
      const onNavigate = vi.fn();
      render(<TaskCalendar {...defaultProps} onNavigate={onNavigate} />);
      await userEvent.click(screen.getByText('<'));
      expect(onNavigate).toHaveBeenCalledWith(-1);
    });

    it('点击 > 按钮触发 onNavigate(1)', async () => {
      const onNavigate = vi.fn();
      render(<TaskCalendar {...defaultProps} onNavigate={onNavigate} />);
      await userEvent.click(screen.getByText('>'));
      expect(onNavigate).toHaveBeenCalledWith(1);
    });
  });

  // ═══════════════ 3: 三级点击交互 ═══════════════
  // '2026年7月' 同时出现在 nav <span> 和 CalendarHeader;
  // CalendarHeader 可点击,nav span 不是。within(singlePanel()) 限定到 panel 内。
  describe('三级点击交互 (3)', () => {
    it('点击日期触发 SELECT_DATE', async () => {
      const onSelect = vi.fn();
      render(<TaskCalendar {...defaultProps} onSelect={onSelect} />);
      await userEvent.click(within(singlePanel()).getByTestId('date-cell-1'));
      expect(onSelect).toHaveBeenCalledWith<[CalendarAction]>({
        type: 'SELECT_DATE',
        date: '2026-07-01',
      });
    });

    it('点击周号触发 SELECT_WEEK', async () => {
      const onSelect = vi.fn();
      render(<TaskCalendar {...defaultProps} onSelect={onSelect} />);
      await userEvent.click(within(singlePanel()).getByTestId('week-row-27'));
      expect(onSelect).toHaveBeenCalledWith<[CalendarAction]>({
        type: 'SELECT_WEEK',
        startDate: '2026-06-29',
      });
    });

    it('点击月份标题 (panel 内 CalendarHeader) 触发 SELECT_MONTH', async () => {
      const onSelect = vi.fn();
      render(<TaskCalendar {...defaultProps} onSelect={onSelect} />);
      await userEvent.click(within(singlePanel()).getByText('2026年7月'));
      expect(onSelect).toHaveBeenCalledWith<[CalendarAction]>({
        type: 'SELECT_MONTH',
        year: 2026,
        month: 7,
      });
    });
  });

  // ═══════════════ 4: WeekNumberColumn ═══════════════

  describe('WeekNumberColumn (4)', () => {
    it('渲染 6 行周号', () => {
      render(<TaskCalendar {...defaultProps} />);
      const weekRows = within(singlePanel()).getAllByText(/^第\d+周$/);
      expect(weekRows).toHaveLength(6);
    });

    it('computeWeekNumbers 返回正确结构 (2026-07 周首 Monday, 7/1 在第27周)', () => {
      const rows = computeWeekNumbers(2026, 7);
      expect(rows).toHaveLength(6);
      expect(rows[0].startDate).toBe('2026-06-29');
      expect(rows[0].weekNum).toBe(27);
    });

    it('有任务的周显示提示色背景,无任务的周无背景色', () => {
      // 第27周包含 2026-07-01 → 有任务
      const { rerender } = render(<TaskCalendar {...defaultProps} />);
      const weekRow = within(singlePanel()).getByTestId('week-row-27') as HTMLElement;
      expect(weekRow.getAttribute('data-has-tasks')).toBe('true');

      mockUseApi.mockReturnValue({
        data: { year: 2026, month: 7, days: {} },
        loading: false,
        error: undefined,
        refetch: vi.fn(),
      });
      rerender(<TaskCalendar {...defaultProps} />);
      const weekRow2 = within(singlePanel()).getByTestId('week-row-27') as HTMLElement;
      expect(weekRow2.getAttribute('data-has-tasks')).toBe('false');
    });

    it('选中周边框高亮;选中一天时该周号行高亮,其他周号行不高亮', () => {
      // 选中 week=2026-06-29..2026-07-05 (第27周)
      const { rerender } = render(
        <TaskCalendar
          {...defaultProps}
          selectedRange={{ type: 'week', startDate: '2026-06-29', endDate: '2026-07-05' }}
        />,
      );
      const weekRow27 = within(singlePanel()).getByTestId('week-row-27') as HTMLElement;
      expect(weekRow27.getAttribute('data-selected')).toBe('true');

      // 选中 day=2026-07-01 (第27周):该周高亮,其他周不高亮
      rerender(
        <TaskCalendar
          {...defaultProps}
          selectedRange={{ type: 'day', startDate: '2026-07-01', endDate: '2026-07-01' }}
        />,
      );
      const w27 = within(singlePanel()).getByTestId('week-row-27') as HTMLElement;
      expect(w27.getAttribute('data-selected')).toBe('true');
      for (const wn of [28, 29, 30, 31, 32]) {
        const wk = within(singlePanel()).getByTestId(`week-row-${wn}`) as HTMLElement;
        expect(wk.getAttribute('data-selected')).toBe('false');
      }
    });

    // 几何对齐（像素级结构断言）— 与 antd 5.29.3 <Calendar fullscreen={false} headerRender={() => null} /> 实测:
    //   panel border-top (1) + body padding-top (8) + thead (18) = 27 px (spacer)
    //   6 个 flex:1 周号行 = antd tbody 总高 238 px,单行 ≈ 39.67 px
    //   底部 8 px 对齐 antd body padding-bottom (不能用 minHeight 兜底扭曲 flex:1)
    it('spacer 高度精确匹配 antd Calendar 表头几何 (27 px)', () => {
      render(<TaskCalendar {...defaultProps} />);
      const spacer = screen.getByTestId('week-column-weekday-spacer-2026-7');
      expect(spacer.style.height).toBe('27px');
    });

    it('周号行不设 minHeight (让 flex:1 严格均分 antd tbody 238 px)', () => {
      render(<TaskCalendar {...defaultProps} />);
      const weekRow = screen.getByTestId('week-row-27') as HTMLElement;
      expect(weekRow.style.minHeight).toBe('');
      expect(weekRow.style.flex).toBe('1');
    });

    it('周号列底部预留 8 px 对齐 antd body padding-bottom', () => {
      render(<TaskCalendar {...defaultProps} />);
      const col = screen.getByTestId('week-column-2026-7');
      const children = Array.from(col.children);
      const lastChild = children[children.length - 1] as HTMLElement;
      expect(lastChild.style.height).toBe('8px');
    });
  });

  // ═══════════════ 5: dateCellRender 任务徽章 ═══════════════
  // Plan D: 颜色标记改由 CSS 覆盖 antd 内置 selected/today 样式实现,
  // dateCellRender 只负责返回任务徽章。jsdom 不计算 CSS,视觉由浏览器验证。

  describe('dateCellRender 任务徽章 (5)', () => {
    it('cell 内存在 antd 默认天数数字 (31 个 for 7 月)', () => {
      render(<TaskCalendar {...defaultProps} />);
      const defaults = within(singlePanel()).getAllByTestId('antd-default-date');
      expect(defaults).toHaveLength(31);
      expect(defaults[0]).toHaveTextContent('1');
      expect(defaults[9]).toHaveTextContent('10');
    });

    it('total > 0 时显示任务数角标 (角标内容=该日 total)', () => {
      render(<TaskCalendar {...defaultProps} />);
      const cell1 = within(singlePanel()).getByTestId('date-cell-1');
      const badge = cell1.querySelector('[data-testid^="task-badge-"]');
      expect(badge).toBeInTheDocument();
      expect(badge).toHaveTextContent('3');
    });

    it('total = 0 时不显示 Badge', () => {
      mockUseApi.mockReturnValue({
        data: {
          ...mockCalendarData,
          days: {
            ...mockCalendarData.days,
            '2026-07-01': {
              total: 0, pending: 0, submitted: 0, approved: 0, rejected: 0, cancelled: 0, overdue: 0,
              taskTypes: { LIMITED: 0, REPEAT: 0, STANDING: 0 },
            },
          },
        },
        loading: false,
        error: undefined,
        refetch: vi.fn(),
      });
      render(<TaskCalendar {...defaultProps} />);
      const cell1 = within(singlePanel()).getByTestId('date-cell-1');
      expect(cell1.querySelector('[data-testid^="task-badge-"]')).not.toBeInTheDocument();
    });

    it('任务数角标使用绝对定位 (top:2px, right:2px, fontSize:7px,不撑高 cell 行高)', () => {
      render(<TaskCalendar {...defaultProps} />);
      const badge = within(singlePanel())
        .getByTestId('date-cell-1')
        .querySelector('[data-testid="task-badge-2026-07-01"]') as HTMLElement;
      expect(badge).toBeInTheDocument();
      expect(badge.style.position).toBe('absolute');
      // fix-calendar-task-badge-alignment: 角标应落在日期内盒(.ant-picker-cell-inner)
      // 右上角内 2 px，避免 top:-26 / left:20 导致的「角标跑到上一行」错位。
      expect(badge.style.top).toBe('2px');
      expect(badge.style.right).toBe('2px');
      expect(badge.style.fontSize).toBe('7px');
    });

    it('有任务日期在 selectedRange=day 时仍渲染任务徽章 (Plan D: 颜色由 CSS 控制)', () => {
      render(
        <TaskCalendar
          {...defaultProps}
          selectedRange={{ type: 'day', startDate: '2026-07-01', endDate: '2026-07-01' }}
        />,
      );
      const cell1 = within(singlePanel()).getByTestId('date-cell-1');
      const badge = cell1.querySelector('[data-testid="task-badge-2026-07-01"]');
      expect(badge).toBeInTheDocument();
    });
  });

  // ═══════════════ 6: useApi 数据获取 ═══════════════

  describe('useApi 数据获取 (6)', () => {
    it('loading 状态显示 Spin', () => {
      mockUseApi.mockReturnValue({
        data: undefined,
        loading: true,
        error: undefined,
        refetch: vi.fn(),
      });
      render(<TaskCalendar {...defaultProps} />);
      expect(document.querySelector('.ant-spin')).toBeInTheDocument();
    });

    it('error 状态显示 Alert 和重试按钮', () => {
      mockUseApi.mockReturnValue({
        data: undefined,
        loading: false,
        error: { error_code: 'NETWORK_ERROR', message: '加载失败' },
        refetch: vi.fn(),
      });
      render(<TaskCalendar {...defaultProps} />);
      const panel = singlePanel();
      expect(within(panel).getByText('加载失败')).toBeInTheDocument();
      expect(within(panel).getByText('重试')).toBeInTheDocument();
    });

    it('点击重试触发 refetch', async () => {
      const refetch = vi.fn();
      mockUseApi.mockReturnValue({
        data: undefined,
        loading: false,
        error: { error_code: 'NETWORK_ERROR', message: '加载失败' },
        refetch,
      });
      render(<TaskCalendar {...defaultProps} />);
      await userEvent.click(within(singlePanel()).getByText('重试'));
      expect(refetch).toHaveBeenCalled();
    });

    it('正常数据渲染单月日历 (mock-calendar-2026-07 存在, mock-calendar-2026-08 不存在)', () => {
      render(<TaskCalendar {...defaultProps} />);
      expect(screen.getByTestId('mock-calendar-2026-07')).toBeInTheDocument();
      expect(screen.queryByTestId('mock-calendar-2026-08')).not.toBeInTheDocument();
    });
  });

  // ═══════════════ 7 + 8: today 视觉与 baseMonth 跨月 ═══════════════
  // 测试沙箱的真实 today 已是 2026-07-24,与 baseMonth='2026-07' 一致;
  // (8) 跨月场景 (baseMonth='2026-08') 需要 today 稳定于 7/24 — 用 fake-timers 保证可重放。
  describe('today 视觉与 baseMonth 跨月 (7 + 8)', () => {
    beforeEach(() => {
      vi.useFakeTimers();
      vi.setSystemTime(new Date('2026-07-24T12:00:00'));
    });
    afterEach(() => {
      vi.useRealTimers();
    });

    // ── (7) today cell ──
    it('(7) baseMonth=今日所在月时 antd <Calendar> value=monthDate (data-value=2026-07-01)', () => {
      // CalendarPanel: 无 selectedRange → value=monthDate;antd 渲染 7 月,
      // today=7/24 cell 由 antd 自动加 .ant-picker-cell-today 类 (jsdom 验证结构,视觉层由浏览器验证)。
      render(<TaskCalendar {...defaultProps} baseMonth="2026-07" selectedRange={null} />);
      const julyCalendar = screen.getByTestId('mock-calendar-2026-07');
      expect(julyCalendar.getAttribute('data-value')).toBe('2026-07-01');
    });

    it('(7) today cell 存在 (date-cell-24) 且 selected day 任务徽章正常渲染', () => {
      render(
        <TaskCalendar
          {...defaultProps}
          baseMonth="2026-07"
          selectedRange={{ type: 'day', startDate: '2026-07-24', endDate: '2026-07-24' }}
        />,
      );
      // Plan D: dateCellRender 只返回徽章,selected 视觉由 CSS 覆盖 antd 内置样式。jsdom 不计算 CSS,仅验证 cell 存在。
      const todayCell = within(singlePanel()).getByTestId('date-cell-24');
      expect(todayCell).toBeInTheDocument();
      // 2026-07-24 在 mock 数据中无任务,故无徽章
      const badge = todayCell.querySelector('[data-testid^="task-badge-"]');
      expect(badge).toBeNull();
    });

    // ── (8) baseMonth 跨月 ──
    it('(8) baseMonth=2026-08 (非今日所在月) 时 Calendar value=monthDate (data-value=2026-08-01)', () => {
      render(<TaskCalendar {...defaultProps} baseMonth="2026-08" selectedRange={null} />);
      const augustCalendar = screen.getByTestId('mock-calendar-2026-08');
      expect(augustCalendar.getAttribute('data-value')).toBe('2026-08-01');
    });

    it('(8) baseMonth=2026-08 时 mock-calendar-2026-08 存在, mock-calendar-2026-09 不存在', () => {
      // 单月模式:baseMonth='2026-08' 仅渲染 1 个 panel (8 月),不再自动渲染下月 (9 月)。
      render(<TaskCalendar {...defaultProps} baseMonth="2026-08" />);
      expect(screen.getByTestId('mock-calendar-2026-08')).toBeInTheDocument();
      expect(screen.queryByTestId('mock-calendar-2026-09')).not.toBeInTheDocument();
      expect(screen.getAllByText('2026年8月')).toHaveLength(2);
      expect(screen.queryByText('2026年9月')).not.toBeInTheDocument();
    });

    it('selectedRange=week 时当前周号行 (week-row-30) 有蓝色边框', () => {
      // 本周 2026-07-19 Sun → 2026-07-25 Sat,today=7/24 在第30周
      render(
        <TaskCalendar
          {...defaultProps}
          baseMonth="2026-07"
          selectedRange={{ type: 'week', startDate: '2026-07-19', endDate: '2026-07-25' }}
        />,
      );
      const weekRow = within(singlePanel()).getByTestId('week-row-30') as HTMLElement;
      expect(weekRow.getAttribute('data-selected')).toBe('true');
      expect(weekRow.style.border).toContain('rgba(22, 119, 255, 0.6)');
    });

    it('selectedRange=week(非本周) 时周号行 (week-row-29) 高亮,today cell 正常渲染', () => {
      // 选中 week=7/12~7/18 (第29周),today=7/24 不在该周内 (在第30周)。
      render(
        <TaskCalendar
          {...defaultProps}
          baseMonth="2026-07"
          selectedRange={{ type: 'week', startDate: '2026-07-12', endDate: '2026-07-18' }}
        />,
      );
      const weekRow29 = within(singlePanel()).getByTestId('week-row-29') as HTMLElement;
      expect(weekRow29.getAttribute('data-selected')).toBe('true');
      const todayCell = within(singlePanel()).getByTestId('date-cell-24');
      expect(todayCell).toBeInTheDocument();
    });
  });
});
