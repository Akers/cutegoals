import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import dayjs from 'dayjs';

// ── Mock useApi ───────────────────────────────────────────────────
vi.mock('@shared/hooks/useApi', () => ({
  useApi: vi.fn(),
}));

import { useApi } from '@shared/hooks/useApi';
const mockUseApi = vi.mocked(useApi);

import { TaskCalendar, computeWeekNumbers } from '../TaskCalendar';
import type { CalendarAction } from '../TaskCalendar';

// ── Mock antd ─────────────────────────────────────────────────────
// Only mock what we need; avoid importing real antd to prevent
// rc-picker / CSS-in-JS side effects in jsdom.
vi.mock('antd', () => {
  const React = require('react');
  const dayjs = require('dayjs');

  return {
    Button: ({ children, onClick, ...rest }: any) =>
      React.createElement('button', { onClick, ...rest }, children),
    Badge: ({ count, children, size, offset, ...rest }: any) => {
      const elements: any[] = [];
      if (children) elements.push(children);
      elements.push(
        React.createElement(
          'sup',
          { key: 'count', className: 'ant-scroll-number ant-badge-count' },
          count,
        ),
      );
      return React.createElement('span', { className: 'ant-badge', ...rest }, ...elements);
    },
    Spin: () => React.createElement('div', { className: 'ant-spin' }),
    Alert: ({ message, action, type, ...rest }: any) =>
      React.createElement(
        'div',
        { className: `ant-alert ant-alert-${type}` },
        React.createElement('div', { className: 'ant-alert-message' }, message),
        action ? React.createElement('div', { className: 'ant-alert-action' }, action) : null,
      ),
    Calendar: vi.fn().mockImplementation(({ value, dateCellRender, onSelect }: any) => {
      if (!value) {
        return <div data-testid="mock-calendar-empty" />;
      }
      const year = value.year();
      const month = value.month(); // 0-based
      const daysInMonth = value.daysInMonth();
      const monthStr = `${year}-${String(month + 1).padStart(2, '0')}`;
      const cells: React.ReactNode[] = [];
      for (let d = 1; d <= daysInMonth; d++) {
        const date = dayjs(new Date(year, month, d));
        const cellContent = dateCellRender?.(date);
        cells.push(
          <div key={d} data-testid={`date-cell-${d}`} onClick={() => onSelect?.(date)}>
            {/* 模拟真实 antd Calendar 行为：cell 中先渲染默认天数数字，
                  再附加 dateCellRender 返回的自定义内容。 */}
            <span data-testid="antd-default-date">{d}</span>
            {cellContent}
          </div>,
        );
      }
      return <div data-testid={`mock-calendar-${monthStr}`}>{cells}</div>;
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
      total: 3,
      pending: 1,
      submitted: 1,
      approved: 0,
      rejected: 0,
      cancelled: 0,
      overdue: 1,
      taskTypes: { LIMITED: 1, REPEAT: 2, STANDING: 0 },
    },
    '2026-07-05': {
      total: 2,
      pending: 0,
      submitted: 0,
      approved: 0,
      rejected: 0,
      cancelled: 0,
      overdue: 2,
      taskTypes: { LIMITED: 0, REPEAT: 2, STANDING: 0 },
    },
    '2026-07-15': {
      total: 1,
      pending: 0,
      submitted: 0,
      approved: 0,
      rejected: 0,
      cancelled: 0,
      overdue: 0,
      taskTypes: { LIMITED: 0, REPEAT: 0, STANDING: 1 },
    },
    '2026-07-20': {
      total: 5,
      pending: 2,
      submitted: 1,
      approved: 1,
      rejected: 0,
      cancelled: 0,
      overdue: 1,
      taskTypes: { LIMITED: 2, REPEAT: 1, STANDING: 2 },
    },
  },
};

// ── 测试辅助 ──────────────────────────────────────────────────────
/** 获取七月面板作用域（第一个面板，基准月） */
function julyPanel() {
  return screen.getByTestId('calendar-panel-2026-7');
}

/** 获取八月面板作用域（第二个面板） */
function augustPanel() {
  return screen.getByTestId('calendar-panel-2026-8');
}

// ── 主测试套件 ────────────────────────────────────────────────────
describe('TaskCalendar - 双月日历组件', () => {
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

  // ═══════════════ 原有测试（保持向后兼容）═══════════════

  it('渲染导航栏及月份标题', () => {
    render(<TaskCalendar {...defaultProps} />);
    expect(screen.getByText('2026年7月 — 2026年8月')).toBeInTheDocument();
    expect(screen.getByText('<')).toBeInTheDocument();
    expect(screen.getByText('>')).toBeInTheDocument();
  });

  it('渲染两个日历面板（双月）', () => {
    render(<TaskCalendar {...defaultProps} />);
    expect(screen.getByTestId('mock-calendar-2026-07')).toBeInTheDocument();
    expect(screen.getByTestId('mock-calendar-2026-08')).toBeInTheDocument();
  });

  it('月份标题可点击触发 SELECT_MONTH', async () => {
    const onSelect = vi.fn();
    render(<TaskCalendar {...defaultProps} onSelect={onSelect} />);
    await userEvent.click(screen.getByText('2026年7月'));
    expect(onSelect).toHaveBeenCalledWith<[CalendarAction]>({
      type: 'SELECT_MONTH',
      year: 2026,
      month: 7,
    });
  });

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

  it('跨年时显示正确的月份标题', () => {
    render(<TaskCalendar {...defaultProps} baseMonth="2026-12" />);
    expect(screen.getByText('2026年12月 — 2027年1月')).toBeInTheDocument();
  });

  it('第二个面板的 CalendarHeader 显示下月信息', () => {
    render(<TaskCalendar {...defaultProps} />);
    expect(screen.getByText('2026年8月')).toBeInTheDocument();
  });

  // ═══════════════ 回归：cell 不应重复渲染天数数字 ═══════════════

  describe('cell 不应重复渲染天数数字 (回归)', () => {
    it('cell 内存在 antd 默认天数数字', () => {
      render(<TaskCalendar {...defaultProps} />);
      // antd 默认数字应由 Calendar 自身渲染（mock 中以 testid 标记）
      const defaults = within(julyPanel()).getAllByTestId('antd-default-date');
      expect(defaults).toHaveLength(31); // 七月 31 天
      expect(defaults[0]).toHaveTextContent('1');
      expect(defaults[9]).toHaveTextContent('10');
    });

    it('自定义 dateCellRender 不应再输出独立的天数数字', () => {
      render(<TaskCalendar {...defaultProps} />);
      const cell1 = within(julyPanel()).getByTestId('date-cell-1');
      const customContent = cell1.querySelector('[data-bg]');
      expect(customContent).not.toBeNull();
      // 当前代码若仍渲染 <div>{date.date()}</div>，此处会捕获到 1 个匹配
      const directNumericDivs = Array.from(customContent!.children).filter(
        (el: Element) =>
          el.tagName === 'DIV' && el.children.length === 0 && /^\d+$/.test(el.textContent ?? ''),
      );
      expect(directNumericDivs).toHaveLength(0);
    });
  });

  // ═══════════════ 2.2: dateCellRender 颜色标记 ═══════════════

  describe('dateCellRender 颜色标记 (2.2)', () => {
    it('LIMITED 类型（＞0）显示淡红背景（error-bg）', () => {
      render(<TaskCalendar {...defaultProps} />);
      // 2026-07-01: LIMITED=1 → 淡红
      const cell1 = within(julyPanel()).getByTestId('date-cell-1');
      const inner = cell1.querySelector('[data-bg]') as HTMLElement;
      expect(inner.getAttribute('data-bg')).toBe('var(--ant-color-error-bg)');
    });

    it('仅 REPEAT 类型显示淡蓝背景（info-bg）', () => {
      render(<TaskCalendar {...defaultProps} />);
      // 2026-07-05: LIMITED=0, REPEAT=2 → 淡蓝
      const cell5 = within(julyPanel()).getByTestId('date-cell-5');
      const inner = cell5.querySelector('[data-bg]') as HTMLElement;
      expect(inner.getAttribute('data-bg')).toBe('var(--ant-color-info-bg)');
    });

    it('仅 STANDING 类型显示淡绿背景（success-bg）', () => {
      render(<TaskCalendar {...defaultProps} />);
      // 2026-07-15: STANDING=1 → 淡绿
      const cell15 = within(julyPanel()).getByTestId('date-cell-15');
      const inner = cell15.querySelector('[data-bg]') as HTMLElement;
      expect(inner.getAttribute('data-bg')).toBe('var(--ant-color-success-bg)');
    });

    it('无任务数据日期无背景色', () => {
      render(<TaskCalendar {...defaultProps} />);
      // 2026-07-10: 不在 mock 数据中 → 无背景
      const cell10 = within(julyPanel()).getByTestId('date-cell-10');
      const inner = cell10.querySelector('[data-bg]') as HTMLElement;
      expect(inner.getAttribute('data-bg')).toBe('');
    });

    it('LIMITED 优先于 REPEAT 和 STANDING', () => {
      // 2026-07-20: LIMITED=2, REPEAT=1, STANDING=2 → LIMITED 优先
      render(<TaskCalendar {...defaultProps} />);
      const cell20 = within(julyPanel()).getByTestId('date-cell-20');
      const inner = cell20.querySelector('[data-bg]') as HTMLElement;
      expect(inner.getAttribute('data-bg')).toBe('var(--ant-color-error-bg)');
    });

    it('total > 0 时显示任务数角标', () => {
      render(<TaskCalendar {...defaultProps} />);
      // 2026-07-01: total=3
      const cell1 = within(julyPanel()).getByTestId('date-cell-1');
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
              total: 0,
              pending: 0,
              submitted: 0,
              approved: 0,
              rejected: 0,
              cancelled: 0,
              overdue: 0,
              taskTypes: { LIMITED: 0, REPEAT: 0, STANDING: 0 },
            },
          },
        },
        loading: false,
        error: undefined,
        refetch: vi.fn(),
      });
      render(<TaskCalendar {...defaultProps} />);
      const cell1 = within(julyPanel()).getByTestId('date-cell-1');
      expect(cell1.querySelector('[data-testid^="task-badge-"]')).not.toBeInTheDocument();
    });

    it('任务数角标使用绝对定位（不撑高 cell 行高）', () => {
      render(<TaskCalendar {...defaultProps} />);
      const cell1 = within(julyPanel()).getByTestId('date-cell-1');
      const badge = cell1.querySelector('[data-testid="task-badge-2026-07-01"]') as HTMLElement;
      expect(badge).toBeInTheDocument();
      // 绝对定位脱离 normal flow，不参与 line box 高度计算
      expect(badge.style.position).toBe('absolute');
      expect(badge.style.top).toBe('-26px');
      expect(badge.style.left).toBe('20px');
      expect(badge.style.fontSize).toBe('7px');
    });

    it('选中日期显示高亮边框', () => {
      render(
        <TaskCalendar
          {...defaultProps}
          selectedRange={{
            type: 'day',
            startDate: '2026-07-01',
            endDate: '2026-07-01',
          }}
        />,
      );
      const cell1 = within(julyPanel()).getByTestId('date-cell-1');
      const inner = cell1.querySelector('[data-bg]') as HTMLElement;
      expect(inner.getAttribute('data-selected')).toBe('true');
    });

    it('未选中日期无高亮边框', () => {
      render(
        <TaskCalendar
          {...defaultProps}
          selectedRange={{
            type: 'day',
            startDate: '2026-07-05',
            endDate: '2026-07-05',
          }}
        />,
      );
      const cell1 = within(julyPanel()).getByTestId('date-cell-1');
      const inner = cell1.querySelector('[data-bg]') as HTMLElement;
      expect(inner.getAttribute('data-selected')).toBe('false');
    });
  });

  // ═══════════════ 2.3: WeekNumberColumn ═══════════════

  describe('WeekNumberColumn (2.3)', () => {
    it('渲染 6 行周号', () => {
      render(<TaskCalendar {...defaultProps} />);
      const weekRows = within(julyPanel()).getAllByText(/^第\d+周$/);
      expect(weekRows).toHaveLength(6);
    });

    it('computeWeekNumbers 返回正确结构', () => {
      const rows = computeWeekNumbers(2026, 7);
      expect(rows).toHaveLength(6);
      // 2026-07-01 是周三 → startOf('week') = 周日 2026-06-28
      // week() 基于周日起始，2026-06-28 周日 = 第27周
      expect(rows[0].startDate).toBe('2026-06-28');
      expect(rows[0].weekNum).toBe(27);
    });

    it('computeWeekNumbers 每月返回 6 行', () => {
      const rows = computeWeekNumbers(2027, 1);
      expect(rows).toHaveLength(6);
    });

    it('有任务的周显示提示色背景', () => {
      render(<TaskCalendar {...defaultProps} />);
      // 第27周包含 2026-07-01（有任务）→ 有背景色
      const weekRow = within(julyPanel()).getByTestId('week-row-27') as HTMLElement;
      expect(weekRow.getAttribute('data-has-tasks')).toBe('true');
    });

    it('无任务的周无背景色', () => {
      mockUseApi.mockReturnValue({
        data: { year: 2026, month: 7, days: {} },
        loading: false,
        error: undefined,
        refetch: vi.fn(),
      });
      render(<TaskCalendar {...defaultProps} />);
      const weekRow = within(julyPanel()).getByTestId('week-row-27') as HTMLElement;
      expect(weekRow.getAttribute('data-has-tasks')).toBe('false');
    });

    it('点击周号触发 SELECT_WEEK', async () => {
      const onSelect = vi.fn();
      render(<TaskCalendar {...defaultProps} onSelect={onSelect} />);
      await userEvent.click(within(julyPanel()).getByTestId('week-row-27'));
      expect(onSelect).toHaveBeenCalledWith<[CalendarAction]>({
        type: 'SELECT_WEEK',
        startDate: '2026-06-28',
      });
    });

    it('选中周显示高亮边框', () => {
      render(
        <TaskCalendar
          {...defaultProps}
          selectedRange={{
            type: 'week',
            startDate: '2026-06-28',
            endDate: '2026-07-04',
          }}
        />,
      );
      const weekRow = within(julyPanel()).getByTestId('week-row-27') as HTMLElement;
      expect(weekRow.getAttribute('data-selected')).toBe('true');
    });

    // ── 周号列与 antd Calendar 几何对齐（像素级结构断言） ──
    // 以下数值来自 explorer 在真实 Chromium 中对 antd 5.29.3
    // <Calendar fullscreen={false} headerRender={() => null} /> 的实测：
    //   .ant-picker-calendar = 273 px
    //   .ant-picker-panel border-top = 1 px
    //   .ant-picker-body padding = 8 0
    //   .ant-picker-content table height = 256 px（token miniContentHeight）
    //   thead = 18 px（token weekHeight = controlHeightSM * 0.75）
    //   tbody 6 行总高 = 256 - 18 = 238 px，单行 ≈ 39.67 px
    // 要让周号列行 0 与 antd 行 0 对齐：spacer 必须等于
    //   panel border-top (1) + body padding-top (8) + thead (18) = 27 px。
    // 6 个周号行总高必须等于 antd tbody 总高 238 px，
    // 且不能设 minHeight（否则 flex:1 均分会被 minHeight 兜底扭曲）。
    // 底部必须预留 8 px 对应 antd body padding-bottom。

    it('spacer 高度精确匹配 antd Calendar 表头几何（27 px = border 1 + body padding-top 8 + thead 18）', () => {
      render(<TaskCalendar {...defaultProps} />);
      const spacer = screen.getByTestId('week-column-weekday-spacer-2026-7');
      expect(spacer.style.height).toBe('27px');
    });

    it('周号行不设 minHeight（让 flex:1 严格均分 antd tbody 238 px）', () => {
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

  // ═══════════════ 2.4: 三级点击交互 ═══════════════

  describe('三级点击交互 (2.4)', () => {
    it('点击日期触发 SELECT_DATE', async () => {
      const onSelect = vi.fn();
      render(<TaskCalendar {...defaultProps} onSelect={onSelect} />);
      await userEvent.click(within(julyPanel()).getByTestId('date-cell-1'));
      expect(onSelect).toHaveBeenCalledWith<[CalendarAction]>({
        type: 'SELECT_DATE',
        date: '2026-07-01',
      });
    });

    it('点击周号触发 SELECT_WEEK', async () => {
      const onSelect = vi.fn();
      render(<TaskCalendar {...defaultProps} onSelect={onSelect} />);
      await userEvent.click(within(julyPanel()).getByTestId('week-row-27'));
      expect(onSelect).toHaveBeenCalledWith<[CalendarAction]>({
        type: 'SELECT_WEEK',
        startDate: '2026-06-28',
      });
    });

    it('月份标题触发 SELECT_MONTH', async () => {
      const onSelect = vi.fn();
      render(<TaskCalendar {...defaultProps} onSelect={onSelect} />);
      await userEvent.click(screen.getByText('2026年7月'));
      expect(onSelect).toHaveBeenCalledWith<[CalendarAction]>({
        type: 'SELECT_MONTH',
        year: 2026,
        month: 7,
      });
    });
  });

  // ═══════════════ 2.5: useApi 数据获取 ═══════════════

  describe('useApi 数据获取 (2.5)', () => {
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
      const july = julyPanel();
      expect(within(july).getByText('加载失败')).toBeInTheDocument();
      expect(within(july).getByText('重试')).toBeInTheDocument();
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
      await userEvent.click(within(julyPanel()).getByText('重试'));
      expect(refetch).toHaveBeenCalled();
    });

    it('单面板错误不影响另一面板', () => {
      mockUseApi.mockImplementation((path: string) => {
        if (path.includes('month=7')) {
          return {
            data: undefined,
            loading: false,
            error: { error_code: 'NETWORK_ERROR', message: '加载失败' },
            refetch: vi.fn(),
          };
        }
        return {
          data: mockCalendarData,
          loading: false,
          error: undefined,
          refetch: vi.fn(),
        };
      });
      render(<TaskCalendar {...defaultProps} />);
      // 七月面板显示错误
      expect(within(julyPanel()).getByText('加载失败')).toBeInTheDocument();
      // 八月面板正常渲染日历
      expect(screen.getByTestId('mock-calendar-2026-08')).toBeInTheDocument();
    });

    it('正常数据渲染日历', () => {
      render(<TaskCalendar {...defaultProps} />);
      expect(screen.getByTestId('mock-calendar-2026-07')).toBeInTheDocument();
      expect(screen.getByTestId('mock-calendar-2026-08')).toBeInTheDocument();
    });
  });
});
