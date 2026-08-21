import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import HomePage from '../pages/HomePage';
import TasksPage from '../pages/TasksPage';
import type { ChildAssignment } from '../pages';
import { RoleProvider } from '@shared/RoleContext';
import { AuthProvider } from '@shared/auth';
import { App } from 'antd';

const childAccount = { accountId: 3, roles: ['CHILD'], familyId: 1, childId: 3 };

function renderHome() {
  render(
    <RoleProvider role="child">
      <AuthProvider initialAccount={childAccount}>
        <App>
          <HomePage />
        </App>
      </AuthProvider>
    </RoleProvider>,
  );
}

function renderTasks() {
  render(
    <RoleProvider role="child">
      <AuthProvider initialAccount={childAccount}>
        <App>
          <TasksPage />
        </App>
      </AuthProvider>
    </RoleProvider>,
  );
}

function mockResponse(data: unknown, ok = true, status = 200) {
  return {
    ok,
    status,
    headers: new Headers({ 'content-type': 'application/json' }),
    json: () => Promise.resolve(data),
  } as unknown as Response;
}

beforeEach(() => {
  vi.spyOn(globalThis, 'fetch').mockReset();
  vi.spyOn(window, 'matchMedia').mockImplementation((query: string) => ({
    matches: query === '(prefers-reduced-motion: reduce)',
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }));
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('Kid pages', () => {
  it('renders home page with balance and tasks', async () => {
    const today = new Date().toISOString().split('T')[0];
    vi.mocked(fetch).mockImplementation((url: string | URL | Request) => {
      const urlStr = typeof url === 'string' ? url : url.toString();
      if (urlStr.includes('/auth/me')) {
        return Promise.resolve(mockResponse({ data: { accountId: 3, childId: 3, roles: ['CHILD'] } }));
      }
      if (urlStr.includes('/task-assignments')) {
        return Promise.resolve(
          mockResponse({
            data: {
              content: [
                {
                  id: 1,
                  childId: 1,
                  templateId: 1,
                  difficultyId: 1,
                  snapshotTemplateName: '整理房间',
                  snapshotDifficultyReward: 10,
                  status: 'PENDING',
                  deadline: `${today}T20:00:00`,
                  overdue: false,
                  cancelled: false,
                  snapshotTemplateTaskType: 'STANDING',
                  canSubmit: true,
                  submissionBlockReason: null,
                },
              ],
            },
          }),
        );
      }
      if (urlStr.includes('/points/balance')) {
        return Promise.resolve(mockResponse({ data: { balance: 120 } }));
      }
      return Promise.reject(new Error('Unexpected: ' + urlStr));
    });

    renderHome();
    await waitFor(() => {
      expect(screen.getByText(/120.*积分/)).toBeInTheDocument();
    });
    expect(screen.getByText('整理房间')).toBeInTheDocument();
  });

  it('今日任务包含 REPEAT 重复任务（即使 deadline 不是今天）', async () => {
    const today = new Date().toISOString().split('T')[0];
    const past = new Date(Date.now() - 86400000 * 5).toISOString().split('T')[0];
    vi.mocked(fetch).mockImplementation((url: string | URL | Request) => {
      const urlStr = typeof url === 'string' ? url : url.toString();
      if (urlStr.includes('/auth/me')) {
        return Promise.resolve(mockResponse({ data: { accountId: 3, childId: 3, roles: ['CHILD'] } }));
      }
      if (urlStr.includes('/task-assignments')) {
        return Promise.resolve(
          mockResponse({
            data: {
              content: [
                { id: 1, childId: 3, templateId: 1, difficultyId: 1, snapshotTemplateName: '今日普通任务', snapshotDifficultyReward: 10, status: 'PENDING', deadline: `${today}T20:00:00`, overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING', canSubmit: true, submissionBlockReason: null },
                { id: 2, childId: 3, templateId: 2, difficultyId: 1, snapshotTemplateName: '每日阅读REPEAT', snapshotDifficultyReward: 5, status: 'PENDING', deadline: `${past}T20:00:00`, overdue: false, cancelled: false, snapshotTemplateTaskType: 'REPEAT', canSubmit: true, submissionBlockReason: null },
              ],
            },
          }),
        );
      }
      if (urlStr.includes('/points/balance')) {
        return Promise.resolve(mockResponse({ data: { balance: 100 } }));
      }
      return Promise.reject(new Error('Unexpected: ' + urlStr));
    });
    renderHome();
    await waitFor(() => expect(screen.getByText('今日普通任务')).toBeInTheDocument());
    expect(screen.getByText('每日阅读REPEAT')).toBeInTheDocument();
  });

  it('renders content tasks on TasksPage', async () => {
    const past = new Date(Date.now() - 86400000 * 10).toISOString().split('T')[0];
    vi.mocked(fetch).mockImplementation((url: string | URL | Request) => {
      const urlStr = typeof url === 'string' ? url : url.toString();
      if (urlStr.includes('/auth/me')) {
        return Promise.resolve(mockResponse({ data: { accountId: 3, childId: 3, roles: ['CHILD'] } }));
      }
      if (urlStr.includes('/task-assignments')) {
        return Promise.resolve(
          mockResponse({
            data: {
              content: [
                {
                  id: 10, childId: 3, templateId: 2, difficultyId: 1,
                  snapshotTemplateName: '任务A', snapshotDifficultyReward: 5,
                  status: 'PENDING', deadline: `${past}T20:00:00`, overdue: false,
                  cancelled: false, snapshotTemplateTaskType: 'STANDING',
                  canSubmit: true, submissionBlockReason: null,
                },
              ],
              page: 0, pageSize: 100, totalElements: 1, totalPages: 1,
            },
          }),
        );
      }
      return Promise.reject(new Error('Unexpected: ' + urlStr));
    });

    renderTasks();
    await waitFor(() => {
      expect(screen.getByText('任务A')).toBeInTheDocument();
    });
  });

  it('filters tasks by visibility rules on TasksPage', async () => {
    const future = new Date(Date.now() + 86400000 * 30).toISOString().split('T')[0];
    const past = new Date(Date.now() - 86400000 * 30).toISOString().split('T')[0];
    const today = new Date().toISOString().split('T')[0];
    vi.mocked(fetch).mockImplementation((url: string | URL | Request) => {
      const urlStr = typeof url === 'string' ? url : url.toString();
      if (urlStr.includes('/auth/me')) {
        return Promise.resolve(mockResponse({ data: { accountId: 3, childId: 3, roles: ['CHILD'] } }));
      }
      if (urlStr.includes('/task-assignments')) {
        return Promise.resolve(
          mockResponse({
            data: {
              content: [
                {
                  id: 1, childId: 3, templateId: 1, difficultyId: 1,
                  snapshotTemplateName: '任务A', snapshotDifficultyReward: 10,
                  status: 'PENDING', deadline: `${future}T20:00:00`,
                  overdue: false, cancelled: false, snapshotTemplateTaskType: 'REPEAT',
                  canSubmit: true, submissionBlockReason: null,
                },
                {
                  id: 2, childId: 3, templateId: 2, difficultyId: 1,
                  snapshotTemplateName: '任务B', snapshotDifficultyReward: 10,
                  status: 'PENDING', deadline: `${future}T20:00:00`,
                  overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING',
                  canSubmit: true, submissionBlockReason: null,
                },
                {
                  id: 3, childId: 3, templateId: 3, difficultyId: 1,
                  snapshotTemplateName: '任务C', snapshotDifficultyReward: 10,
                  status: 'PENDING', deadline: `${past}T20:00:00`,
                  overdue: false, cancelled: true, snapshotTemplateTaskType: 'STANDING',
                  canSubmit: true, submissionBlockReason: null,
                },
                {
                  id: 4, childId: 3, templateId: 4, difficultyId: 1,
                  snapshotTemplateName: '任务D', snapshotDifficultyReward: 10,
                  status: 'PENDING', deadline: `${past}T20:00:00`,
                  overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING',
                  canSubmit: true, submissionBlockReason: null,
                },
                {
                  id: 5, childId: 3, templateId: 5, difficultyId: 1,
                  snapshotTemplateName: '任务E', snapshotDifficultyReward: 10,
                  status: 'PENDING', deadline: `${today}T23:59:59`,
                  overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING',
                  canSubmit: true, submissionBlockReason: null,
                },
              ],
            },
          }),
        );
      }
      return Promise.reject(new Error('Unexpected: ' + urlStr));
    });

    renderTasks();

    // 五分类行为：进行中默认显示所有非取消非完成非提交非逾期的任务
    await waitFor(() => {
      expect(screen.getByText('任务A')).toBeInTheDocument();
    });
    // 任务A(REPEAT,future)和任务B(STANDING,future)都在进行中，显示"未开始"
    expect(screen.getByText('任务B')).toBeInTheDocument();
    expect(screen.getAllByText('未开始')).toHaveLength(2);
    expect(screen.getByText('任务D')).toBeInTheDocument();
    expect(screen.getByText('任务E')).toBeInTheDocument();
    // 任务C在已取消分类（Chip 筛选）
    await userEvent.click(screen.getByRole('button', { name: '已取消' }));
    await waitFor(() => expect(screen.getByText('任务C')).toBeInTheDocument());
  });

  it('shows empty state when no tasks on TasksPage', async () => {
    vi.mocked(fetch).mockImplementation((url: string | URL | Request) => {
      const urlStr = typeof url === 'string' ? url : url.toString();
      if (urlStr.includes('/auth/me')) {
        return Promise.resolve(mockResponse({ data: { accountId: 3, childId: 3, roles: ['CHILD'] } }));
      }
      if (urlStr.includes('/task-assignments')) {
        return Promise.resolve(mockResponse({ data: { content: [] } }));
      }
      return Promise.reject(new Error('Unexpected: ' + urlStr));
    });

    renderTasks();

    await waitFor(() => {
      expect(screen.getByText('暂无任务')).toBeInTheDocument();
    });
  });
});

describe('TasksPage 五分类筛选', () => {
  const makeTasksResponse = (tasks: Partial<ChildAssignment>[]) =>
    mockResponse({ data: { content: tasks } });

  function renderTasksPage(tasks: Partial<ChildAssignment>[]) {
    vi.mocked(fetch).mockImplementation((url: string | URL | Request) => {
      const urlStr = typeof url === 'string' ? url : url.toString();
      if (urlStr.includes('/auth/me')) {
        return Promise.resolve(mockResponse({ data: { accountId: 3, childId: 3, roles: ['CHILD'] } }));
      }
      if (urlStr.includes('/task-assignments')) {
        return Promise.resolve(makeTasksResponse(tasks as ChildAssignment[]));
      }
      return Promise.reject(new Error('Unexpected: ' + urlStr));
    });
    renderTasks();
  }

  const baseTask = {
    childId: 3,
    templateId: 1,
    difficultyId: 1,
    snapshotDifficultyReward: 10,
    canSubmit: true,
    submissionBlockReason: null,
    snapshotTemplateAllowResubmit: null,
    snapshotTemplateMaxSubmissions: null,
    snapshotTemplatePointsCap: null,
  };

  it('默认选中「进行中」', async () => {
    renderTasksPage([
      { id: 1, ...baseTask, snapshotTemplateName: '任务A', status: 'PENDING', deadline: `${new Date().toISOString().split('T')[0]}T20:00:00`, overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING' },
    ]);
    await waitFor(() => expect(screen.getByText('任务A')).toBeInTheDocument());
    expect(screen.getByRole('button', { name: '提交' })).toBeInTheDocument();
    // 默认选中的「进行中」Chip 为选中态
    expect(screen.getByRole('button', { name: '进行中' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('「进行中」只显示进行中任务', async () => {
    const today = new Date().toISOString().split('T')[0];
    renderTasksPage([
      { id: 1, ...baseTask, snapshotTemplateName: '进行中任务', status: 'PENDING', deadline: `${today}T20:00:00`, overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING' },
      { id: 2, ...baseTask, snapshotTemplateName: '已取消任务', status: 'PENDING', deadline: `${today}T20:00:00`, overdue: false, cancelled: true, snapshotTemplateTaskType: 'STANDING' },
      { id: 3, ...baseTask, snapshotTemplateName: '已完成任务', status: 'APPROVED', deadline: `${today}T20:00:00`, overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING' },
      { id: 4, ...baseTask, snapshotTemplateName: '已提交任务', status: 'SUBMITTED', deadline: `${today}T20:00:00`, overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING' },
    ]);
    await waitFor(() => expect(screen.getByText('进行中任务')).toBeInTheDocument());
    expect(screen.queryByText('已取消任务')).toBeNull();
    expect(screen.queryByText('已完成任务')).toBeNull();
    expect(screen.queryByText('已提交任务')).toBeNull();
  });

  it('「已提交」只显示 SUBMITTED', async () => {
    const today = new Date().toISOString().split('T')[0];
    renderTasksPage([
      { id: 1, ...baseTask, snapshotTemplateName: '已提交任务', status: 'SUBMITTED', deadline: `${today}T20:00:00`, overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING' },
      { id: 2, ...baseTask, snapshotTemplateName: '进行中任务', status: 'PENDING', deadline: `${today}T20:00:00`, overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING' },
    ]);
    await waitFor(() => expect(screen.getByText('进行中任务')).toBeInTheDocument());
    await userEvent.click(screen.getByRole('button', { name: '已提交' }));
    await waitFor(() => expect(screen.getByText('已提交任务')).toBeInTheDocument());
    expect(screen.queryByText('进行中任务')).toBeNull();
  });

  it('未来（PENDING）任务卡片显示「待开始」', async () => {
    const future = new Date(Date.now() + 86400000 * 30).toISOString().split('T')[0];
    const today = new Date().toISOString().split("T")[0];
    renderTasksPage([
      { id: 1, ...baseTask, snapshotTemplateName: '今日PENDING', status: 'PENDING', deadline: `${today}T20:00:00`, overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING' },
      { id: 2, ...baseTask, snapshotTemplateName: '未来PENDING', status: 'PENDING', deadline: `${future}T20:00:00`, overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING' },
    ]);
    await waitFor(() => expect(screen.getByText('今日PENDING')).toBeInTheDocument());
    await waitFor(() => expect(screen.getByText('待开始')).toBeInTheDocument());
    expect(screen.getByText('未来PENDING').parentElement).toHaveTextContent('待开始');
  });

  it('SUBMITTED 任务卡片显示「待审核」（已提交未审核）', async () => {
    const today = new Date().toISOString().split('T')[0];
    renderTasksPage([
      { id: 1, ...baseTask, snapshotTemplateName: '进行中任务', status: 'PENDING', deadline: `${today}T20:00:00`, overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING' },
      { id: 2, ...baseTask, snapshotTemplateName: '待审核任务', status: 'SUBMITTED', deadline: `${today}T20:00:00`, overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING' },
    ]);
    await waitFor(() => expect(screen.getByText('进行中任务')).toBeInTheDocument());
    await userEvent.click(screen.getByRole('button', { name: '已提交' }));
    await waitFor(() => expect(screen.getByText('待审核任务')).toBeInTheDocument());
    expect(screen.getByText('待审核')).toBeInTheDocument();
  });


  it('「已完成」只显示 APPROVED/COMPLETED', async () => {
    const today = new Date().toISOString().split('T')[0];
    renderTasksPage([
      { id: 1, ...baseTask, snapshotTemplateName: '已通过任务', status: 'APPROVED', deadline: `${today}T20:00:00`, overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING' },
      { id: 2, ...baseTask, snapshotTemplateName: '已完成任务', status: 'COMPLETED', deadline: `${today}T20:00:00`, overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING' },
      { id: 3, ...baseTask, snapshotTemplateName: '进行中任务', status: 'PENDING', deadline: `${today}T20:00:00`, overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING' },
    ]);
    await waitFor(() => expect(screen.getByText('进行中任务')).toBeInTheDocument());
    await userEvent.click(screen.getByRole('button', { name: '已完成' }));
    await waitFor(() => expect(screen.getByText('已通过任务')).toBeInTheDocument());
    expect(screen.getByText('已完成任务')).toBeInTheDocument();
    expect(screen.queryByText('进行中任务')).toBeNull();
  });

  it('「已取消」只显示 cancelled', async () => {
    const today = new Date().toISOString().split('T')[0];
    renderTasksPage([
      { id: 1, ...baseTask, snapshotTemplateName: '已取消任务', status: 'PENDING', deadline: `${today}T20:00:00`, overdue: false, cancelled: true, snapshotTemplateTaskType: 'STANDING' },
      { id: 2, ...baseTask, snapshotTemplateName: '进行中任务', status: 'PENDING', deadline: `${today}T20:00:00`, overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING' },
    ]);
    await waitFor(() => expect(screen.getByText('进行中任务')).toBeInTheDocument());
    await userEvent.click(screen.getByRole('button', { name: '已取消' }));
    await waitFor(() => expect(screen.getByText('已取消任务')).toBeInTheDocument());
    expect(screen.queryByText('进行中任务')).toBeNull();
  });

  it('非 REPEAT 截止已过且 PENDING → 出现在「已逾期」', async () => {
    const past = new Date(Date.now() - 86400000).toISOString().split('T')[0];
    renderTasksPage([
      { id: 1, ...baseTask, snapshotTemplateName: '逾期任务', status: 'PENDING', deadline: `${past}T20:00:00`, overdue: true, cancelled: false, snapshotTemplateTaskType: 'STANDING' },
      { id: 2, ...baseTask, snapshotTemplateName: '进行中任务', status: 'PENDING', deadline: `${past}T20:00:00`, overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING' },
    ]);
    await waitFor(() => expect(screen.getByText('进行中任务')).toBeInTheDocument());
    await userEvent.click(screen.getByRole('button', { name: '已逾期' }));
    await waitFor(() => expect(screen.getByText('逾期任务')).toBeInTheDocument());
    expect(screen.queryByText('进行中任务')).toBeNull();
  });

  it('截止已过的 SUBMITTED 任务 → 只在「已提交」不在「已逾期」', async () => {
    const past = new Date(Date.now() - 86400000).toISOString().split('T')[0];
    renderTasksPage([
      { id: 1, ...baseTask, snapshotTemplateName: '逾期提交任务', status: 'SUBMITTED', deadline: `${past}T20:00:00`, overdue: true, cancelled: false, snapshotTemplateTaskType: 'STANDING' },
    ]);
    await waitFor(() => expect(screen.getByText('暂无任务')).toBeInTheDocument());
    await userEvent.click(screen.getByRole('button', { name: '已提交' }));
    await waitFor(() => expect(screen.getByText('逾期提交任务')).toBeInTheDocument());
    await userEvent.click(screen.getByRole('button', { name: '已逾期' }));
    await waitFor(() => expect(screen.getByText('暂无任务')).toBeInTheDocument());
  });

  it('REPEAT 截止已过未提交 → 在「进行中」不在「已逾期」', async () => {
    const past = new Date(Date.now() - 86400000).toISOString().split('T')[0];
    renderTasksPage([
      { id: 1, ...baseTask, snapshotTemplateName: 'REPEAT逾期', status: 'PENDING', deadline: `${past}T20:00:00`, overdue: true, cancelled: false, snapshotTemplateTaskType: 'REPEAT' },
    ]);
    await waitFor(() => expect(screen.getByText('REPEAT逾期')).toBeInTheDocument());
    await userEvent.click(screen.getByRole('button', { name: '已逾期' }));
    await waitFor(() => expect(screen.getByText('暂无任务')).toBeInTheDocument());
  });

  it('未来任务在「进行中」末尾、显示「未开始」、提交按钮禁用', async () => {
    const future = new Date(Date.now() + 86400000 * 30).toISOString().split('T')[0];
    const today = new Date().toISOString().split('T')[0];
    renderTasksPage([
      { id: 1, ...baseTask, snapshotTemplateName: '今天任务', status: 'PENDING', deadline: `${today}T20:00:00`, overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING' },
      { id: 2, ...baseTask, snapshotTemplateName: '未来任务', status: 'PENDING', deadline: `${future}T20:00:00`, overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING' },
    ]);
    await waitFor(() => expect(screen.getByText('今天任务')).toBeInTheDocument());
    expect(screen.getByText('未来任务')).toBeInTheDocument();
    expect(screen.getByText('未开始')).toBeInTheDocument();
    const submitBtns = screen.getAllByRole('button', { name: '提交' });
    expect(submitBtns[1]).toBeDisabled();
  });

  it('REPEAT 重复任务自分配起即为可提交：未来日期按钮不因 future 禁用', async () => { const future = new Date(Date.now() + 86400000 * 30).toISOString().split('T')[0]; const today = new Date().toISOString().split('T')[0]; renderTasksPage([ { id: 1, ...baseTask, snapshotTemplateName: '今日REPEAT', status: 'PENDING', deadline: today + 'T20:00:00', overdue: false, cancelled: false, snapshotTemplateTaskType: 'REPEAT' }, { id: 2, ...baseTask, snapshotTemplateName: '未来REPEAT', status: 'PENDING', deadline: future + 'T20:00:00', overdue: false, cancelled: false, snapshotTemplateTaskType: 'REPEAT' } ]); await waitFor(() => expect(screen.getByText('今日REPEAT')).toBeInTheDocument()); expect(screen.getByText('未来REPEAT')).toBeInTheDocument(); const submitBtns = screen.getAllByRole('button', { name: '提交' }); expect(submitBtns).toHaveLength(2); expect(submitBtns[0]).not.toBeDisabled(); expect(submitBtns[1]).not.toBeDisabled(); });


  it('MAX_REACHED / POINTS_CAP_REACHED → 显示「该任务已达最大提交次数」且无提交按钮', async () => {
    const today = new Date().toISOString().split('T')[0];
    renderTasksPage([
      { id: 1, ...baseTask, snapshotTemplateName: 'MAX任务', status: 'PENDING', deadline: `${today}T20:00:00`, overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING', canSubmit: false, submissionBlockReason: 'MAX_REACHED' },
      { id: 2, ...baseTask, snapshotTemplateName: 'POINTS任务', status: 'REJECTED', deadline: `${today}T20:00:00`, overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING', canSubmit: false, submissionBlockReason: 'POINTS_CAP_REACHED' },
    ]);
    await waitFor(() => expect(screen.getByText('MAX任务')).toBeInTheDocument());
    expect(screen.getAllByText('该任务已达最大提交次数')).toHaveLength(2);
    expect(screen.queryByRole('button', { name: '提交' })).toBeNull();
    expect(screen.queryByRole('button', { name: '重新提交' })).toBeNull();
    expect(screen.getByText('POINTS任务')).toBeInTheDocument();
  });

  it('canSubmit=false 且 reason=null → 按钮禁用但无拦截文案', async () => {
    const today = new Date().toISOString().split('T')[0];
    renderTasksPage([
      { id: 1, ...baseTask, snapshotTemplateName: '禁用任务', status: 'PENDING', deadline: `${today}T20:00:00`, overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING', canSubmit: false, submissionBlockReason: null },
    ]);
    await waitFor(() => expect(screen.getByText('禁用任务')).toBeInTheDocument());
    expect(screen.getByRole('button', { name: '提交' })).toBeDisabled();
    expect(screen.queryByText('该任务已达最大提交次数')).toBeNull();
  });

  it('驳回任务归类：未逾期归进行中，逾期归已逾期', async () => {
    const today = new Date().toISOString().split('T')[0];
    const past = new Date(Date.now() - 86400000).toISOString().split('T')[0];
    renderTasksPage([
      { id: 1, ...baseTask, snapshotTemplateName: '驳回未逾期', status: 'REJECTED', deadline: `${today}T20:00:00`, overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING', rejectionReason: '再具体一点' },
      { id: 2, ...baseTask, snapshotTemplateName: '驳回且逾期', status: 'REJECTED', deadline: `${past}T20:00:00`, overdue: true, cancelled: false, snapshotTemplateTaskType: 'STANDING', rejectionReason: '证据不足' },
    ]);
    // 未逾期的驳回任务默认在「进行中」
    await waitFor(() => expect(screen.getByText('驳回未逾期')).toBeInTheDocument());
    expect(screen.queryByText('驳回且逾期')).toBeNull();
    // 逾期的驳回任务在「已逾期」
    await userEvent.click(screen.getByRole('button', { name: '已逾期' }));
    await waitFor(() => expect(screen.getByText('驳回且逾期')).toBeInTheDocument());
    expect(screen.queryByText('驳回未逾期')).toBeNull();
  });

  it('空分类显示空态', async () => {
    renderTasksPage([]);
    await waitFor(() => expect(screen.getByText('暂无任务')).toBeInTheDocument());
  });
});

describe('TasksPage 提交任务弹层', () => {
  const makeTasksResponse = (tasks: Partial<ChildAssignment>[]) =>
    mockResponse({ data: { content: tasks } });

  const baseTask = {
    childId: 3,
    templateId: 1,
    difficultyId: 1,
    snapshotDifficultyReward: 10,
    canSubmit: true,
    submissionBlockReason: null,
    snapshotTemplateAllowResubmit: null,
    snapshotTemplateMaxSubmissions: null,
    snapshotTemplatePointsCap: null,
  };

  function renderTasksPageWithSubmit(tasks: Partial<ChildAssignment>[]) {
    vi.mocked(fetch).mockImplementation((url: string | URL | Request, init?: RequestInit) => {
      const urlStr = typeof url === 'string' ? url : url.toString();
      if (urlStr.includes('/auth/me')) {
        return Promise.resolve(mockResponse({ data: { accountId: 3, childId: 3, roles: ['CHILD'] } }));
      }
      if (urlStr.includes('/task-assignments')) {
        return Promise.resolve(makeTasksResponse(tasks as ChildAssignment[]));
      }
      if (urlStr.includes('/task-review/submissions') && init?.method === 'POST') {
        return Promise.resolve(mockResponse({ data: { submissionId: 999 } }));
      }
      return Promise.reject(new Error('Unexpected: ' + urlStr));
    });
    renderTasks();
  }

  it('打开弹层后：footer 显示取消+提交；空 textarea 时提交禁用', async () => {
    const today = new Date().toISOString().split('T')[0];
    renderTasksPageWithSubmit([
      { id: 1, ...baseTask, snapshotTemplateName: '测试任务', status: 'PENDING', deadline: `${today}T20:00:00`, overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING' },
    ]);
    await waitFor(() => expect(screen.getByText('测试任务')).toBeInTheDocument());

    // 点击卡片入口的「提交」按钮打开弹层
    await userEvent.click(screen.getByRole('button', { name: '提交' }));

    // 弹层打开后：所有名为「提交」的按钮 = 卡片入口(1) + footer 主按钮(1) = 2 个
    const submitBtns = screen.getAllByRole('button', { name: '提交' });
    expect(submitBtns).toHaveLength(2);

    // footer 有「取消」按钮
    const dialog = screen.getByRole('dialog');
    expect(within(dialog).getByRole('button', { name: '取消' })).toBeInTheDocument();

    // footer 的提交按钮（textarea 为空 → disabled）
    const footerSubmit = within(dialog).getByRole('button', { name: '提交' });
    expect(footerSubmit).toBeDisabled();
  });

  it('输入 textarea 后点击 footer「提交」触发 POST /task-review/submissions', async () => {
    const today = new Date().toISOString().split('T')[0];
    renderTasksPageWithSubmit([
      { id: 1, ...baseTask, snapshotTemplateName: '可提交任务', status: 'PENDING', deadline: `${today}T20:00:00`, overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING' },
    ]);
    await waitFor(() => expect(screen.getByText('可提交任务')).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: '提交' }));

    // 输入完成情况说明
    const textarea = document.querySelector('#submit-notes') as HTMLTextAreaElement;
    expect(textarea).not.toBeNull();
    await userEvent.type(textarea, '我已经完成了');

    // footer 提交按钮现在 enabled
    const dialog = screen.getByRole('dialog');
    const footerSubmit = within(dialog).getByRole('button', { name: '提交' }) as HTMLButtonElement;
    expect(footerSubmit.disabled).toBe(false);

    // 点击 footer 提交按钮
    await userEvent.click(footerSubmit);

    // 验证 POST 请求已发出，且请求体正确
    await waitFor(() => {
      const calls = vi.mocked(fetch).mock.calls.filter(([url, init]) => {
        const u = typeof url === 'string' ? url : (url as URL).toString();
        return u.includes('/task-review/submissions') && init?.method === 'POST';
      });
      expect(calls.length).toBeGreaterThan(0);
      const lastCall = calls[calls.length - 1];
      const body = JSON.parse((lastCall[1]?.body as string) ?? '{}');
      expect(body.assignmentId).toBe(1);
      expect(body.notes).toBe('我已经完成了');
      expect(body.idempotencyKey).toEqual(expect.any(String));
    });
  });

  it('REJECTED 任务：footer 主按钮文案为「重新提交」', async () => {
    const today = new Date().toISOString().split('T')[0];
    renderTasksPageWithSubmit([
      { id: 2, ...baseTask, snapshotTemplateName: '驳回任务', status: 'REJECTED', deadline: `${today}T20:00:00`, overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING', rejectionReason: '再具体一点' },
    ]);
    await waitFor(() => expect(screen.getByText('驳回任务')).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: '重新提交' }));

    const dialog = screen.getByRole('dialog');
    const footerSubmit = within(dialog).getByRole('button', { name: '重新提交' });
    expect(footerSubmit).toBeInTheDocument();
  });
});
