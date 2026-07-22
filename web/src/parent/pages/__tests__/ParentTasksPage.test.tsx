import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import dayjs from 'dayjs';

// ── Global mocks ──────────────────────────────────────────────────
vi.mock('@shared/hooks/useApi', () => ({
  useApi: vi.fn(),
  useFormField: vi.fn(() => ({ value: '', setValue: vi.fn(), reset: vi.fn() })),
  useIdempotencyKey: vi.fn(() => ({ key: 'test-key', reset: vi.fn() })),
}));

vi.mock('@shared/theme', () => ({
  useOnline: vi.fn(() => true),
}));

vi.mock('@shared/auth', () => ({
  useAuth: vi.fn(() => ({ account: null })),
}));

vi.mock('@shared/api', () => ({
  getClient: vi.fn(),
}));

// Mock child components that have complex internal dependencies
vi.mock('@parent/components/TaskTypeConfigForms', () => ({
  TaskTypeConfigForms: () => null,
  __esModule: true,
}));

vi.mock('@parent/components/PrizeTypeConfigForms', () => ({
  PrizeTypeConfigForms: () => null,
  __esModule: true,
}));

// Mock TaskCalendar — it has its own thorough tests; avoid
// antd Calendar dayjs plugin incompatibility in jsdom.
vi.mock('@parent/components/TaskCalendar', () => ({
  TaskCalendar: ({ baseMonth, selectedRange, onSelect, onNavigate }: any) => {
    const React = require('react');
    return React.createElement('div', {
      'data-testid': 'mock-task-calendar',
      'data-base-month': baseMonth,
      'data-selected': selectedRange ? `${selectedRange.startDate}_${selectedRange.endDate}` : '',
    }, 'TaskCalendar');
  },
  __esModule: true,
}));

// ── Imports after mocks ───────────────────────────────────────────
import { useApi } from '@shared/hooks/useApi';
const mockUseApi = vi.mocked(useApi);

import {
  calendarReducer,
  type CalendarPageState,
  type CalendarAction2,
  ParentTasksPage,
} from '../index';

// ═══════════════════════════════════════════════════════════════════
// 3.4a: Reducer 纯函数测试
// ═══════════════════════════════════════════════════════════════════
describe('calendarReducer - pure function', () => {
  const baseState: CalendarPageState = {
    baseMonth: '2026-07',
    selectedRange: null,
    taskTypeFilters: ['LIMITED', 'REPEAT', 'STANDING'],
    viewAllMode: false,
  };

  describe('SELECT_DATE', () => {
    it('设置 selectedRange 为单日', () => {
      const next = calendarReducer(baseState, { type: 'SELECT_DATE', date: '2026-07-15' });
      expect(next.selectedRange).toEqual({
        type: 'day',
        startDate: '2026-07-15',
        endDate: '2026-07-15',
      });
      expect(next.viewAllMode).toBe(false);
    });
  });

  describe('SELECT_WEEK', () => {
    it('设置 selectedRange 为周（7天）', () => {
      const next = calendarReducer(baseState, { type: 'SELECT_WEEK', startDate: '2026-07-06' });
      expect(next.selectedRange).toEqual({
        type: 'week',
        startDate: '2026-07-06',
        endDate: '2026-07-12',
      });
      expect(next.viewAllMode).toBe(false);
    });

    it('处理跨月周', () => {
      const next = calendarReducer(baseState, { type: 'SELECT_WEEK', startDate: '2026-06-29' });
      expect(next.selectedRange).toEqual({
        type: 'week',
        startDate: '2026-06-29',
        endDate: '2026-07-05',
      });
    });

    it('周日开始到周六', () => {
      const next = calendarReducer(baseState, { type: 'SELECT_WEEK', startDate: '2026-07-05' });
      expect(next.selectedRange).toEqual({
        type: 'week',
        startDate: '2026-07-05',
        endDate: '2026-07-11',
      });
    });
  });

  describe('SELECT_MONTH', () => {
    it('设置 selectedRange 为整月', () => {
      const next = calendarReducer(baseState, { type: 'SELECT_MONTH', year: 2026, month: 7 });
      expect(next.selectedRange).toEqual({
        type: 'month',
        startDate: '2026-07-01',
        endDate: '2026-07-31',
      });
      expect(next.viewAllMode).toBe(false);
    });

    it('处理2月闰年', () => {
      const next = calendarReducer(baseState, { type: 'SELECT_MONTH', year: 2024, month: 2 });
      expect(next.selectedRange).toEqual({
        type: 'month',
        startDate: '2024-02-01',
        endDate: '2024-02-29',
      });
    });

    it('处理2月平年', () => {
      const next = calendarReducer(baseState, { type: 'SELECT_MONTH', year: 2025, month: 2 });
      expect(next.selectedRange).toEqual({
        type: 'month',
        startDate: '2025-02-01',
        endDate: '2025-02-28',
      });
    });
  });

  describe('SET_FILTERS', () => {
    it('设置筛选类型为仅 LIMITED', () => {
      const next = calendarReducer(baseState, { type: 'SET_FILTERS', payload: ['LIMITED'] });
      expect(next.taskTypeFilters).toEqual(['LIMITED']);
    });

    it('设置空数组（全部取消）', () => {
      const next = calendarReducer(baseState, { type: 'SET_FILTERS', payload: [] });
      expect(next.taskTypeFilters).toEqual([]);
    });

    it('不修改其他状态', () => {
      const next = calendarReducer(
        { ...baseState, viewAllMode: true, selectedRange: { type: 'day', startDate: '2026-07-01', endDate: '2026-07-01' } },
        { type: 'SET_FILTERS', payload: ['REPEAT'] },
      );
      expect(next.taskTypeFilters).toEqual(['REPEAT']);
      expect(next.viewAllMode).toBe(true);
      expect(next.selectedRange).toBeTruthy();
    });
  });

  describe('VIEW_ALL', () => {
    it('设置 viewAllMode=true 并清除 selectedRange', () => {
      const stateWithSelection: CalendarPageState = {
        ...baseState,
        selectedRange: { type: 'day', startDate: '2026-07-01', endDate: '2026-07-01' },
      };
      const next = calendarReducer(stateWithSelection, { type: 'VIEW_ALL' });
      expect(next.viewAllMode).toBe(true);
      expect(next.selectedRange).toBeNull();
    });

    it('重复 VIEW_ALL 保持 viewAllMode=true', () => {
      const next = calendarReducer(
        { ...baseState, viewAllMode: true },
        { type: 'VIEW_ALL' },
      );
      expect(next.viewAllMode).toBe(true);
    });
  });

  describe('NAV_MONTH', () => {
    it('NAV_MONTH 前进一个月', () => {
      const next = calendarReducer(baseState, { type: 'NAV_MONTH', payload: 1 });
      expect(next.baseMonth).toBe('2026-08');
    });

    it('NAV_MONTH 后退一个月', () => {
      const next = calendarReducer(baseState, { type: 'NAV_MONTH', payload: -1 });
      expect(next.baseMonth).toBe('2026-06');
    });

    it('跨年 NAV_MONTH 前进', () => {
      const state: CalendarPageState = { ...baseState, baseMonth: '2026-12' };
      const next = calendarReducer(state, { type: 'NAV_MONTH', payload: 1 });
      expect(next.baseMonth).toBe('2027-01');
    });

    it('跨年 NAV_MONTH 后退', () => {
      const state: CalendarPageState = { ...baseState, baseMonth: '2026-01' };
      const next = calendarReducer(state, { type: 'NAV_MONTH', payload: -1 });
      expect(next.baseMonth).toBe('2025-12');
    });
  });

  describe('复合交互场景', () => {
    it('SELECT_DATE 后 VIEW_ALL 清除选择', () => {
      const s1 = calendarReducer(baseState, { type: 'SELECT_DATE', date: '2026-07-15' });
      expect(s1.selectedRange).not.toBeNull();
      const s2 = calendarReducer(s1, { type: 'VIEW_ALL' });
      expect(s2.selectedRange).toBeNull();
      expect(s2.viewAllMode).toBe(true);
    });

    it('VIEW_ALL 后 SELECT_DATE 重置 viewAllMode', () => {
      const s1 = calendarReducer(baseState, { type: 'VIEW_ALL' });
      expect(s1.viewAllMode).toBe(true);
      const s2 = calendarReducer(s1, { type: 'SELECT_DATE', date: '2026-08-01' });
      expect(s2.viewAllMode).toBe(false);
      expect(s2.selectedRange).not.toBeNull();
    });

    it('SET_FILTERS 后 NAV_MONTH 保留筛选', () => {
      const s1 = calendarReducer(baseState, { type: 'SET_FILTERS', payload: ['STANDING'] });
      const s2 = calendarReducer(s1, { type: 'NAV_MONTH', payload: 1 });
      expect(s2.taskTypeFilters).toEqual(['STANDING']);
      expect(s2.baseMonth).toBe('2026-08');
    });
  });
});

// ═══════════════════════════════════════════════════════════════════
// 3.4b: 组件渲染测试
// ═══════════════════════════════════════════════════════════════════
describe('ParentTasksPage - component rendering', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseApi.mockReturnValue({
      data: { content: [], page: 1, pageSize: 20, totalElements: 0, totalPages: 0 },
      loading: false,
      error: undefined,
      refetch: vi.fn(),
    });
  });

  it('renders page title', () => {
    render(<ParentTasksPage />);
    expect(screen.getByText('任务分配')).toBeInTheDocument();
  });

  it('renders 分配任务 and 批量分配 buttons', () => {
    render(<ParentTasksPage />);
    expect(screen.getByText('分配任务')).toBeInTheDocument();
    expect(screen.getByText('批量分配')).toBeInTheDocument();
  });

  it('renders 日历 card', () => {
    render(<ParentTasksPage />);
    expect(screen.getByText('日历')).toBeInTheDocument();
  });

  it('renders 任务列表 card', () => {
    render(<ParentTasksPage />);
    expect(screen.getByText('任务列表')).toBeInTheDocument();
  });

  it('renders 查看全部 button', () => {
    render(<ParentTasksPage />);
    expect(screen.getByText('查看全部')).toBeInTheDocument();
  });

  it('renders 任务类型筛选 card', () => {
    render(<ParentTasksPage />);
    expect(screen.getByText('任务类型筛选')).toBeInTheDocument();
  });

  it('renders 限时任务 checkbox', () => {
    render(<ParentTasksPage />);
    expect(screen.getByText('限时任务')).toBeInTheDocument();
  });

  it('renders 重复任务 checkbox', () => {
    render(<ParentTasksPage />);
    expect(screen.getByText('重复任务')).toBeInTheDocument();
  });

  it('renders 常驻任务 checkbox', () => {
    render(<ParentTasksPage />);
    expect(screen.getByText('常驻任务')).toBeInTheDocument();
  });

  it('shows loading spinner when loading', () => {
    mockUseApi.mockReturnValue({
      data: undefined,
      loading: true,
      error: undefined,
      refetch: vi.fn(),
    });
    render(<ParentTasksPage />);
    expect(document.querySelector('.ant-spin')).toBeInTheDocument();
  });

  it('shows error result when error', () => {
    mockUseApi.mockReturnValue({
      data: undefined,
      loading: false,
      error: { error_code: 'NETWORK_ERROR', message: '加载失败' },
      refetch: vi.fn(),
    });
    render(<ParentTasksPage />);
    // Both title and subtitle may contain "加载失败"
    const errorTexts = screen.getAllByText('加载失败');
    expect(errorTexts.length).toBeGreaterThanOrEqual(1);
  });

  it('shows 当天暂无任务 when no assignments', () => {
    render(<ParentTasksPage />);
    expect(screen.getByText('当天暂无任务')).toBeInTheDocument();
  });

  it('renders assignment items', () => {
    mockUseApi.mockReturnValue({
      data: {
        content: [
          {
            id: 1,
            childId: 10,
            templateId: 100,
            difficultyId: 5,
            status: 'PENDING',
            deadline: '2026-07-15',
            snapshotTemplateName: '数学练习',
            snapshotDifficultyName: '中级',
            snapshotDifficultyReward: 50,
            snapshotTemplateTaskType: 'LIMITED',
            overdue: false,
            snapshotTemplateAllowResubmit: false,
            snapshotTemplateMaxSubmissions: 1,
            snapshotTemplatePointsCap: 100,
            canSubmit: true,
            submissionBlockReason: null,
          },
        ],
        page: 1,
        pageSize: 20,
        totalElements: 1,
        totalPages: 1,
      },
      loading: false,
      error: undefined,
      refetch: vi.fn(),
    });
    render(<ParentTasksPage />);
    expect(screen.getByText('数学练习')).toBeInTheDocument();
    expect(screen.getByText('待处理')).toBeInTheDocument();
  });
});
