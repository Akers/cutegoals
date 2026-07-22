import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TaskCalendar } from '../TaskCalendar';
import type { CalendarAction } from '../TaskCalendar';

// Mock antd Calendar to avoid jsdom limitations (ResizeObserver, etc.)
vi.mock('antd', async (importOriginal) => {
  const actual = await importOriginal<typeof import('antd')>();
  return {
    ...actual,
    Calendar: vi.fn().mockImplementation(
      ({ value }: { value?: { format?: (fmt: string) => string } }) => {
        const month = value?.format?.('YYYY-MM') ?? 'unknown';
        return <div data-testid={`mock-calendar-${month}`} />;
      },
    ),
  };
});

describe('TaskCalendar - 双月日历组件', () => {
  const baseMonth = '2026-07';
  const defaultProps = {
    baseMonth,
    selectedRange: null,
    onSelect: vi.fn(),
    onNavigate: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

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
});
