import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';

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
  TaskCalendar: ({ baseMonth, selectedRange }: any) => {
    const React = require('react');
    return React.createElement(
      'div',
      {
        'data-testid': 'mock-task-calendar',
        'data-base-month': baseMonth,
        'data-selected': selectedRange ? `${selectedRange.startDate}_${selectedRange.endDate}` : '',
      },
      'TaskCalendar',
    );
  },
  __esModule: true,
}));

// ── Imports after mocks ───────────────────────────────────────────
import { useApi } from '@shared/hooks/useApi';
const mockUseApi = vi.mocked(useApi);

import {
  buildQueryA,
  buildQueryRepeat,
  calendarReducer,
  type CalendarPageState,
  ParentTasksPage,
  formatRepeatProgress,
} from '../index';

// ═══════════════════════════════════════════════════════════════════
// 3.4a: Reducer 纯函数测试
// ═══════════════════════════════════════════════════════════════════
describe('calendarReducer - pure function', () => {
  const baseState: CalendarPageState = {
    baseMonth: '2026-07',
    selectedDate: '2026-07-15',
    taskTypeFilters: ['LIMITED', 'REPEAT', 'STANDING'],
  };

  describe('SELECT_DATE', () => {
    it('设置 selectedDate 为指定日期', () => {
      const next = calendarReducer(baseState, { type: 'SELECT_DATE', date: '2026-08-01' });
      expect(next.selectedDate).toBe('2026-08-01');
    });

    it('selectedDate 变为 null 后可恢复', () => {
      const s1 = calendarReducer(baseState, { type: 'CLEAR_DATE' });
      expect(s1.selectedDate).toBeNull();
      const s2 = calendarReducer(s1, { type: 'SELECT_DATE', date: '2026-09-01' });
      expect(s2.selectedDate).toBe('2026-09-01');
    });
  });

  describe('CLEAR_DATE', () => {
    it('selectedDate 有值时变为 null', () => {
      const next = calendarReducer(baseState, { type: 'CLEAR_DATE' });
      expect(next.selectedDate).toBeNull();
    });

    it('selectedDate 已为 null 时保持 null', () => {
      const nullState: CalendarPageState = { ...baseState, selectedDate: null };
      const next = calendarReducer(nullState, { type: 'CLEAR_DATE' });
      expect(next.selectedDate).toBeNull();
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
        {
          ...baseState,
          selectedDate: '2026-07-20',
        },
        { type: 'SET_FILTERS', payload: ['REPEAT'] },
      );
      expect(next.taskTypeFilters).toEqual(['REPEAT']);
      expect(next.selectedDate).toBe('2026-07-20');
    });
  });

  describe('NAV_MONTH', () => {
    it('NAV_MONTH 前进一个月', () => {
      const next = calendarReducer(baseState, { type: 'NAV_MONTH', payload: { baseMonth: '2026-07', direction: 1 } });
      expect(next.baseMonth).toBe('2026-08');
    });

    it('NAV_MONTH 后退一个月', () => {
      const next = calendarReducer(baseState, { type: 'NAV_MONTH', payload: { baseMonth: '2026-07', direction: -1 } });
      expect(next.baseMonth).toBe('2026-06');
    });

    it('跨年 NAV_MONTH 前进', () => {
      const state: CalendarPageState = { ...baseState, baseMonth: '2026-12' };
      const next = calendarReducer(state, { type: 'NAV_MONTH', payload: { baseMonth: '2026-12', direction: 1 } });
      expect(next.baseMonth).toBe('2027-01');
    });

    it('跨年 NAV_MONTH 后退', () => {
      const state: CalendarPageState = { ...baseState, baseMonth: '2026-01' };
      const next = calendarReducer(state, { type: 'NAV_MONTH', payload: { baseMonth: '2026-01', direction: -1 } });
      expect(next.baseMonth).toBe('2025-12');
    });
  });

  describe('复合交互场景', () => {
    it('SELECT_DATE 后 CLEAR_DATE 清除选择', () => {
      const s1 = calendarReducer(baseState, { type: 'SELECT_DATE', date: '2026-08-01' });
      expect(s1.selectedDate).toBe('2026-08-01');
      const s2 = calendarReducer(s1, { type: 'CLEAR_DATE' });
      expect(s2.selectedDate).toBeNull();
    });

    it('CLEAR_DATE 后 SELECT_DATE 重置 selectedDate', () => {
      const s1 = calendarReducer(baseState, { type: 'CLEAR_DATE' });
      expect(s1.selectedDate).toBeNull();
      const s2 = calendarReducer(s1, { type: 'SELECT_DATE', date: '2026-09-01' });
      expect(s2.selectedDate).toBe('2026-09-01');
    });

    it('SET_FILTERS 后 NAV_MONTH 保留筛选', () => {
      const s1 = calendarReducer(baseState, { type: 'SET_FILTERS', payload: ['STANDING'] });
      const s2 = calendarReducer(s1, { type: 'NAV_MONTH', payload: { baseMonth: '2026-07', direction: 1 } });
      expect(s2.taskTypeFilters).toEqual(['STANDING']);
      expect(s2.baseMonth).toBe('2026-08');
    });
  });
});

// ═══════════════════════════════════════════════════════════════════
// 3.4a-bis: buildQueryA / buildQueryRepeat 分页参数测试
// 回归：pageSize 必须落在后端 TaskAssignmentService 校验区间 [1,100] 内
// ═══════════════════════════════════════════════════════════════════
describe('buildQueryA / buildQueryRepeat - pageSize 回归', () => {
  const stateWithDate: CalendarPageState = {
    baseMonth: '2026-07',
    selectedDate: '2026-07-15',
    taskTypeFilters: ['LIMITED', 'REPEAT', 'STANDING'],
  };
  const stateViewAll: CalendarPageState = { ...stateWithDate, selectedDate: null };

  const paramsOf = (q: string) => new URL(q, 'http://localhost').searchParams;

  it('A1: buildQueryA 默认带日期时 pageSize=100 且携带 startDate/endDate', () => {
    const p = paramsOf(buildQueryA(stateWithDate));
    expect(p.get('pageSize')).toBe('100');
    expect(p.get('startDate')).toBe('2026-07-15');
    expect(p.get('endDate')).toBe('2026-07-15');
  });

  it('A1: buildQueryRepeat pageSize=100 且固定 taskType=REPEAT', () => {
    const p = paramsOf(buildQueryRepeat(stateWithDate));
    expect(p.get('pageSize')).toBe('100');
    expect(p.get('taskType')).toBe('REPEAT');
  });

  it('A2: 切换日期后 buildQueryA 仍 pageSize=100 且携带对应日期', () => {
    const p = paramsOf(buildQueryA({ ...stateWithDate, selectedDate: '2026-08-01' }));
    expect(p.get('pageSize')).toBe('100');
    expect(p.get('startDate')).toBe('2026-08-01');
    expect(p.get('endDate')).toBe('2026-08-01');
  });

  it('A3: 查看全部（selectedDate=null）时 buildQueryA pageSize=100 且不带日期', () => {
    const p = paramsOf(buildQueryA(stateViewAll));
    expect(p.get('pageSize')).toBe('100');
    expect(p.get('startDate')).toBeNull();
    expect(p.get('endDate')).toBeNull();
  });
});

// ═══════════════════════════════════════════════════════════════════
// 3.4b: 组件渲染测试
// ═══════════════════════════════════════════════════════════════════
describe('ParentTasksPage - component rendering', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseApi.mockReturnValue({
      data: { content: [], page: 1, pageSize: 100, totalElements: 0, totalPages: 0 },
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
      error: { message: 'network' } as any,
      refetch: vi.fn(),
    });
    render(<ParentTasksPage />);
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
        pageSize: 100,
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

  // ═══════════════ A∪B∪C 合并去重═══════════════
  describe('A∪B∪C 合并去重', () => {
    it('合并 A 类（deadline命中）+ B类（WEEKLY）+ C类（DAILY），MONTHLY 被过滤', () => {
      // 调用顺序: queryA → queryRepeat → templates → children
      mockUseApi
        .mockReturnValueOnce({
          data: {
            content: [
              {
                id: 1,
                childId: 10,
                templateId: 100,
                difficultyId: 5,
                status: 'PENDING',
                deadline: '2026-07-15',
                snapshotTemplateName: 'taskA1',
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
              {
                id: 2,
                childId: 10,
                templateId: 101,
                difficultyId: 5,
                status: 'PENDING',
                deadline: '2026-07-15',
                snapshotTemplateName: 'taskA2',
                snapshotDifficultyName: '中级',
                snapshotDifficultyReward: 50,
                snapshotTemplateTaskType: 'STANDING',
                overdue: false,
                snapshotTemplateAllowResubmit: false,
                snapshotTemplateMaxSubmissions: 1,
                snapshotTemplatePointsCap: 100,
                canSubmit: true,
                submissionBlockReason: null,
              },
            ],
            page: 1,
            pageSize: 100,
            totalElements: 2,
            totalPages: 1,
          },
          loading: false,
          error: undefined,
          refetch: vi.fn(),
        })
        .mockReturnValueOnce({
          data: {
            content: [
              {
                id: 3,
                childId: 10,
                templateId: 102,
                difficultyId: 5,
                status: 'PENDING',
                deadline: '2026-07-20',
                snapshotTemplateName: 'taskBWeekly',
                snapshotDifficultyName: '中级',
                snapshotDifficultyReward: 50,
                snapshotTemplateTaskType: 'REPEAT',
                snapshotTemplateTypeConfig: JSON.stringify({ frequency: 'WEEKLY' }),
                overdue: false,
                snapshotTemplateAllowResubmit: false,
                snapshotTemplateMaxSubmissions: 1,
                snapshotTemplatePointsCap: 100,
                canSubmit: true,
                submissionBlockReason: null,
              },
              {
                id: 4,
                childId: 10,
                templateId: 103,
                difficultyId: 5,
                status: 'PENDING',
                deadline: '2026-07-21',
                snapshotTemplateName: 'taskCDaily',
                snapshotDifficultyName: '中级',
                snapshotDifficultyReward: 50,
                snapshotTemplateTaskType: 'REPEAT',
                snapshotTemplateTypeConfig: JSON.stringify({ frequency: 'DAILY' }),
                overdue: false,
                snapshotTemplateAllowResubmit: false,
                snapshotTemplateMaxSubmissions: 1,
                snapshotTemplatePointsCap: 100,
                canSubmit: true,
                submissionBlockReason: null,
              },
              {
                id: 5,
                childId: 10,
                templateId: 104,
                difficultyId: 5,
                status: 'PENDING',
                deadline: '2026-07-22',
                snapshotTemplateName: 'taskMMonthly',
                snapshotDifficultyName: '中级',
                snapshotDifficultyReward: 50,
                snapshotTemplateTaskType: 'REPEAT',
                snapshotTemplateTypeConfig: JSON.stringify({ frequency: 'MONTHLY' }),
                overdue: false,
                snapshotTemplateAllowResubmit: false,
                snapshotTemplateMaxSubmissions: 1,
                snapshotTemplatePointsCap: 100,
                canSubmit: true,
                submissionBlockReason: null,
              },
            ],
            page: 1,
            pageSize: 100,
            totalElements: 3,
            totalPages: 1,
          },
          loading: false,
          error: undefined,
          refetch: vi.fn(),
        })
        .mockReturnValueOnce({
          data: { content: [], page: 1, pageSize: 100, totalElements: 0, totalPages: 0 },
          loading: false,
          error: undefined,
          refetch: vi.fn(),
        })
        .mockReturnValueOnce({
          data: { content: [], page: 1, pageSize: 100, totalElements: 0, totalPages: 0 },
          loading: false,
          error: undefined,
          refetch: vi.fn(),
        });

      render(<ParentTasksPage />);
      expect(screen.getByText('taskA1')).toBeInTheDocument();
      expect(screen.getByText('taskA2')).toBeInTheDocument();
      expect(screen.getByText('taskBWeekly')).toBeInTheDocument();
      expect(screen.getByText('taskCDaily')).toBeInTheDocument();
      expect(screen.queryByText('taskMMonthly')).not.toBeInTheDocument();
    });

    it('A 中已有的任务不会因 B/C 类重复出现（id 去重）', () => {
      // 如果 taskA1 的 id 也出现在 queryRepeat 结果中，只应显示一次
      mockUseApi
        .mockReturnValueOnce({
          data: {
            content: [
              {
                id: 1,
                childId: 10,
                templateId: 100,
                difficultyId: 5,
                status: 'PENDING',
                deadline: '2026-07-15',
                snapshotTemplateName: 'taskA1',
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
            pageSize: 100,
            totalElements: 1,
            totalPages: 1,
          },
          loading: false,
          error: undefined,
          refetch: vi.fn(),
        })
        .mockReturnValueOnce({
          data: {
            content: [
              {
                id: 1, // same id as taskA1
                childId: 10,
                templateId: 100,
                difficultyId: 5,
                status: 'PENDING',
                deadline: '2026-07-20',
                snapshotTemplateName: 'taskA1_dup',
                snapshotDifficultyName: '中级',
                snapshotDifficultyReward: 50,
                snapshotTemplateTaskType: 'REPEAT',
                snapshotTemplateTypeConfig: JSON.stringify({ frequency: 'WEEKLY' }),
                overdue: false,
                snapshotTemplateAllowResubmit: false,
                snapshotTemplateMaxSubmissions: 1,
                snapshotTemplatePointsCap: 100,
                canSubmit: true,
                submissionBlockReason: null,
              },
            ],
            page: 1,
            pageSize: 100,
            totalElements: 1,
            totalPages: 1,
          },
          loading: false,
          error: undefined,
          refetch: vi.fn(),
        })
        .mockReturnValueOnce({
          data: { content: [], page: 1, pageSize: 100, totalElements: 0, totalPages: 0 },
          loading: false,
          error: undefined,
          refetch: vi.fn(),
        })
        .mockReturnValueOnce({
          data: { content: [], page: 1, pageSize: 100, totalElements: 0, totalPages: 0 },
          loading: false,
          error: undefined,
          refetch: vi.fn(),
        });

      render(<ParentTasksPage />);
      // 只应出现一次
      const cards = screen.getAllByText('taskA1');
      expect(cards.length).toBe(1);
    });
  });

  // ═══════════════ 默认选中今日（回归）═══════════════
  describe('默认选中今日 (回归, fix-calendar-default-current-date)', () => {
    beforeEach(() => {
      vi.useFakeTimers();
      vi.setSystemTime(new Date('2026-07-24T12:00:00'));
    });
    afterEach(() => {
      vi.useRealTimers();
    });

    it('日历 mock 在初次渲染时 data-selected 等于 today 单点 (2026-07-24_2026-07-24)', () => {
      render(<ParentTasksPage />);
      const mockCalendar = screen.getByTestId('mock-task-calendar');
      expect(mockCalendar.getAttribute('data-selected')).toBe('2026-07-24_2026-07-24');
    });

    it('日历 mock 在初次渲染时 data-base-month 等于本月 (2026-07)', () => {
      render(<ParentTasksPage />);
      const mockCalendar = screen.getByTestId('mock-task-calendar');
      expect(mockCalendar.getAttribute('data-base-month')).toBe('2026-07');
    });
  });

  // ═══════════════ formatRepeatProgress 纯函数 ════════════════
  describe('formatRepeatProgress', () => {
    it('正常渲染提交次数和积分', () => {
      const result = formatRepeatProgress({
        approvedSubmissionCount: 2,
        earnedPoints: 30,
        snapshotTemplateMaxSubmissions: 5,
        snapshotTemplatePointsCap: 100,
      });
      expect(result).toBe('提交 2/5 · 积分 30/100');
    });

    it('null 字段兜底为 0', () => {
      const result = formatRepeatProgress({
        approvedSubmissionCount: null,
        earnedPoints: null,
        snapshotTemplateMaxSubmissions: null,
        snapshotTemplatePointsCap: null,
      });
      expect(result).toBe('提交 0/不限 · 积分 0/不限');
    });

    it('上限为 0 时渲染不限', () => {
      const result = formatRepeatProgress({
        approvedSubmissionCount: 2,
        earnedPoints: 30,
        snapshotTemplateMaxSubmissions: 0,
        snapshotTemplatePointsCap: 0,
      });
      expect(result).toBe('提交 2/不限 · 积分 30/不限');
    });
  });

  // ═══════════════ REPEAT 卡片进度显示 ════════════════
  describe('REPEAT 卡片进度显示', () => {
    it('REPEAT 卡片显示进度：提交 2/5 · 积分 30/100', () => {
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
              snapshotTemplateTaskType: 'REPEAT',
              snapshotTemplateTypeConfig: JSON.stringify({ frequency: 'DAILY' }),
              overdue: false,
              snapshotTemplateAllowResubmit: true,
              snapshotTemplateMaxSubmissions: 5,
              snapshotTemplatePointsCap: 100,
              approvedSubmissionCount: 2,
              earnedPoints: 30,
              canSubmit: true,
              submissionBlockReason: null,
            },
          ],
          page: 1,
          pageSize: 100,
          totalElements: 1,
          totalPages: 1,
        },
        loading: false,
        error: undefined,
        refetch: vi.fn(),
      });
      render(<ParentTasksPage />);
      expect(screen.getByText('提交 2/5 · 积分 30/100')).toBeInTheDocument();
    });

    it('上限为 0/null 时渲染不限', () => {
      mockUseApi.mockReturnValue({
        data: {
          content: [
            {
              id: 2,
              childId: 10,
              templateId: 101,
              difficultyId: 5,
              status: 'PENDING',
              deadline: '2026-07-15',
              snapshotTemplateName: '英语打卡',
              snapshotDifficultyName: '中级',
              snapshotDifficultyReward: 30,
              snapshotTemplateTaskType: 'REPEAT',
              snapshotTemplateTypeConfig: JSON.stringify({ frequency: 'WEEKLY' }),
              overdue: false,
              snapshotTemplateAllowResubmit: true,
              snapshotTemplateMaxSubmissions: 0,
              snapshotTemplatePointsCap: null,
              approvedSubmissionCount: 2,
              earnedPoints: 30,
              canSubmit: true,
              submissionBlockReason: null,
            },
          ],
          page: 1,
          pageSize: 100,
          totalElements: 1,
          totalPages: 1,
        },
        loading: false,
        error: undefined,
        refetch: vi.fn(),
      });
      render(<ParentTasksPage />);
      expect(screen.getByText('提交 2/不限 · 积分 30/不限')).toBeInTheDocument();
    });

    it('REPEAT 卡片（overdue=false）不出现「已逾期」', () => {
      mockUseApi.mockReturnValue({
        data: {
          content: [
            {
              id: 3,
              childId: 10,
              templateId: 102,
              difficultyId: 5,
              status: 'PENDING',
              deadline: '2026-07-15',
              snapshotTemplateName: 'REPEAT任务',
              snapshotDifficultyName: '中级',
              snapshotDifficultyReward: 20,
              snapshotTemplateTaskType: 'REPEAT',
              snapshotTemplateTypeConfig: JSON.stringify({ frequency: 'DAILY' }),
              overdue: false,
              snapshotTemplateAllowResubmit: true,
              snapshotTemplateMaxSubmissions: 10,
              snapshotTemplatePointsCap: 200,
              approvedSubmissionCount: 1,
              earnedPoints: 10,
              canSubmit: true,
              submissionBlockReason: null,
            },
          ],
          page: 1,
          pageSize: 100,
          totalElements: 1,
          totalPages: 1,
        },
        loading: false,
        error: undefined,
        refetch: vi.fn(),
      });
      render(<ParentTasksPage />);
      expect(screen.queryByText('已逾期')).not.toBeInTheDocument();
    });

    it('LIMITED 卡片 overdue=true 仍显示「已逾期」（回归保护）', () => {
      mockUseApi.mockReturnValue({
        data: {
          content: [
            {
              id: 4,
              childId: 10,
              templateId: 103,
              difficultyId: 5,
              status: 'PENDING',
              deadline: '2026-07-15',
              snapshotTemplateName: 'LIMITED任务',
              snapshotDifficultyName: '中级',
              snapshotDifficultyReward: 30,
              snapshotTemplateTaskType: 'LIMITED',
              overdue: true,
              snapshotTemplateAllowResubmit: false,
              snapshotTemplateMaxSubmissions: 1,
              snapshotTemplatePointsCap: 100,
              canSubmit: true,
              submissionBlockReason: null,
            },
          ],
          page: 1,
          pageSize: 100,
          totalElements: 1,
          totalPages: 1,
        },
        loading: false,
        error: undefined,
        refetch: vi.fn(),
      });
      render(<ParentTasksPage />);
      expect(screen.getByText('已逾期')).toBeInTheDocument();
    });
  });
});
