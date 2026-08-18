import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ChildApp from '../App';
import { ChildTasksPage, ChildAssignment } from '../pages';
import { RoleProvider } from '@shared/RoleContext';
import { AuthProvider } from '@shared/auth';
import { App, ConfigProvider } from 'antd';

const childAccount = { accountId: 3, roles: ['CHILD'], familyId: 1, childId: 3 };

function renderChild() {
  render(
    <RoleProvider role="child">
      <AuthProvider initialAccount={childAccount}>
        <ConfigProvider button={{ autoInsertSpace: false }}>
          <App>
            <ChildApp />
          </App>
        </ConfigProvider>
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

/** Helper: reserve the first mockResolvedValueOnce for AuthProvider's /auth/me call */
function authMock() {
  vi.mocked(fetch).mockResolvedValueOnce(mockResponse({ data: {} }));
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('Child pages', () => {
  it('renders home page with balance and tasks', async () => {
    authMock();
    const today = new Date().toISOString().split('T')[0];
    vi.mocked(fetch)
      .mockResolvedValueOnce(
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
      )
      .mockResolvedValueOnce(mockResponse({ data: { balance: 120 } }));

    renderChild();
    await waitFor(() => {
      expect(screen.getByText(/120.*积分/)).toBeInTheDocument();
    });
    expect(screen.getByText('整理房间')).toBeInTheDocument();
  });

  it('renders content tasks on ChildTasksPage', async () => {
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

    render(
      <RoleProvider role="child">
        <AuthProvider initialAccount={childAccount}>
          <ConfigProvider button={{ autoInsertSpace: false }}>
            <App>
              <ChildTasksPage />
            </App>
          </ConfigProvider>
        </AuthProvider>
      </RoleProvider>,
    );
    await waitFor(() => {
      expect(screen.getByText('任务A')).toBeInTheDocument();
    });
  });

  it('filters tasks by visibility rules on ChildTasksPage', async () => {
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

    render(
      <RoleProvider role="child">
        <AuthProvider initialAccount={childAccount}>
          <ConfigProvider button={{ autoInsertSpace: false }}>
            <App>
              <ChildTasksPage />
            </App>
          </ConfigProvider>
        </AuthProvider>
      </RoleProvider>,
    );

    // 新五分类行为：进行中默认显示所有非取消非完成非提交非逾期的任务
    await waitFor(() => {
      expect(screen.getByText('任务A')).toBeInTheDocument();
    });
    // 任务A(REPEAT,future)和任务B(STANDING,future)都在进行中，显示"未开始"
    expect(screen.getByText('任务B')).toBeInTheDocument();
    expect(screen.getAllByText('未开始')).toHaveLength(2);
    expect(screen.getByText('任务D')).toBeInTheDocument();
    expect(screen.getByText('任务E')).toBeInTheDocument();
    // 任务C在已取消分类
    const items = document.querySelectorAll('.ant-segmented-item');
    await userEvent.click(items[4].querySelector('.ant-segmented-item-label') || items[4]);
    await waitFor(() => expect(screen.getByText('任务C')).toBeInTheDocument());
  });

  it('shows empty state when no tasks on ChildTasksPage', async () => {
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

    render(
      <RoleProvider role="child">
        <AuthProvider initialAccount={childAccount}>
          <ConfigProvider button={{ autoInsertSpace: false }}>
            <App>
              <ChildTasksPage />
            </App>
          </ConfigProvider>
        </AuthProvider>
      </RoleProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText('暂无任务')).toBeInTheDocument();
    });
  });
});

describe('ChildTasksPage 五分类筛选', () => {
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
    render(
      <RoleProvider role="child">
        <AuthProvider initialAccount={childAccount}>
          <ConfigProvider button={{ autoInsertSpace: false }}>
            <App>
              <ChildTasksPage />
            </App>
          </ConfigProvider>
        </AuthProvider>
      </RoleProvider>,
    );
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
    // Switch to 已提交 (index 2)
    const items = document.querySelectorAll('.ant-segmented-item');
    await userEvent.click(items[2].querySelector('.ant-segmented-item-label') || items[2]);
    await waitFor(() => expect(screen.getByText('已提交任务')).toBeInTheDocument());
    expect(screen.queryByText('进行中任务')).toBeNull();
  });

  it('「已完成」只显示 APPROVED/COMPLETED', async () => {
    const today = new Date().toISOString().split('T')[0];
    renderTasksPage([
      { id: 1, ...baseTask, snapshotTemplateName: '已通过任务', status: 'APPROVED', deadline: `${today}T20:00:00`, overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING' },
      { id: 2, ...baseTask, snapshotTemplateName: '已完成任务', status: 'COMPLETED', deadline: `${today}T20:00:00`, overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING' },
      { id: 3, ...baseTask, snapshotTemplateName: '进行中任务', status: 'PENDING', deadline: `${today}T20:00:00`, overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING' },
    ]);
    await waitFor(() => expect(screen.getByText('进行中任务')).toBeInTheDocument());
    // Switch to 已完成 (index 3)
    const items = document.querySelectorAll('.ant-segmented-item');
    await userEvent.click(items[3].querySelector('.ant-segmented-item-label') || items[3]);
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
    const items = document.querySelectorAll('.ant-segmented-item');
    await userEvent.click(items[4].querySelector('.ant-segmented-item-label') || items[4]);
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
    const items = document.querySelectorAll('.ant-segmented-item');
    await userEvent.click(items[1].querySelector('.ant-segmented-item-label') || items[1]);
    await waitFor(() => expect(screen.getByText('逾期任务')).toBeInTheDocument());
    expect(screen.queryByText('进行中任务')).toBeNull();
  });

  it('截止已过的 SUBMITTED 任务 → 只在「已提交」不在「已逾期」', async () => {
    const past = new Date(Date.now() - 86400000).toISOString().split('T')[0];
    renderTasksPage([
      { id: 1, ...baseTask, snapshotTemplateName: '逾期提交任务', status: 'SUBMITTED', deadline: `${past}T20:00:00`, overdue: true, cancelled: false, snapshotTemplateTaskType: 'STANDING' },
    ]);
    await waitFor(() => expect(screen.getByText('暂无任务')).toBeInTheDocument());
    const items = document.querySelectorAll('.ant-segmented-item');
    await userEvent.click(items[2].querySelector('.ant-segmented-item-label') || items[2]);
    await waitFor(() => expect(screen.getByText('逾期提交任务')).toBeInTheDocument());
    await userEvent.click(items[1].querySelector('.ant-segmented-item-label') || items[1]);
    await waitFor(() => expect(screen.getByText('暂无任务')).toBeInTheDocument());
  });

  it('REPEAT 截止已过未提交 → 在「进行中」不在「已逾期」', async () => {
    const past = new Date(Date.now() - 86400000).toISOString().split('T')[0];
    renderTasksPage([
      { id: 1, ...baseTask, snapshotTemplateName: 'REPEAT逾期', status: 'PENDING', deadline: `${past}T20:00:00`, overdue: true, cancelled: false, snapshotTemplateTaskType: 'REPEAT' },
    ]);
    await waitFor(() => expect(screen.getByText('REPEAT逾期')).toBeInTheDocument());
    const items = document.querySelectorAll('.ant-segmented-item');
    await userEvent.click(items[1].querySelector('.ant-segmented-item-label') || items[1]);
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
    const items = document.querySelectorAll('.ant-segmented-item');
    await userEvent.click(items[1].querySelector('.ant-segmented-item-label') || items[1]);
    await waitFor(() => expect(screen.getByText('驳回且逾期')).toBeInTheDocument());
    expect(screen.queryByText('驳回未逾期')).toBeNull();
  });

  it('空分类显示空态', async () => {
    renderTasksPage([]);
    await waitFor(() => expect(screen.getByText('暂无任务')).toBeInTheDocument());
  });
});

describe('ChildTasksPage 提交任务 Modal footer', () => {
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
    render(
      <RoleProvider role="child">
        <AuthProvider initialAccount={childAccount}>
          <ConfigProvider button={{ autoInsertSpace: false }}>
            <App>
              <ChildTasksPage />
            </App>
          </ConfigProvider>
        </AuthProvider>
      </RoleProvider>,
    );
  }

  it('打开 Modal 后：表单内无独立提交按钮；footer 显示取消+提交；空 textarea 时提交禁用', async () => {
    const today = new Date().toISOString().split('T')[0];
    renderTasksPageWithSubmit([
      { id: 1, ...baseTask, snapshotTemplateName: '测试任务', status: 'PENDING', deadline: `${today}T20:00:00`, overdue: false, cancelled: false, snapshotTemplateTaskType: 'STANDING' },
    ]);
    await waitFor(() => expect(screen.getByText('测试任务')).toBeInTheDocument());

    // 点击卡片入口的「提交」按钮打开 Modal
    await userEvent.click(screen.getByRole('button', { name: '提交' }));

    // Modal 打开后：所有名为「提交」的按钮 = 卡片入口(1) + footer 主按钮(1) = 2 个；不应有第 3 个独立的表单内提交按钮
    const submitBtns = screen.getAllByRole('button', { name: '提交' });
    expect(submitBtns).toHaveLength(2);

    // footer 有「取消」按钮
    expect(screen.getByRole('button', { name: '取消' })).toBeInTheDocument();

    // footer 的提交按钮（Modal footer 在 antd 上挂载于 .ant-modal-footer）
    const footer = document.querySelector('.ant-modal-footer');
    expect(footer).not.toBeNull();
    const footerSubmit = footer!.querySelector('button.ant-btn-primary');
    expect(footerSubmit).not.toBeNull();
    expect(footerSubmit?.textContent).toContain('提交');
    // textarea 为空 → footer 提交按钮 disabled
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
    const footer = document.querySelector('.ant-modal-footer')!;
    const footerSubmit = footer.querySelector('button.ant-btn-primary') as HTMLButtonElement;
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

    // 切到「进行中」筛选项（驳回未逾期默认在进行中）
    await userEvent.click(screen.getByRole('button', { name: '重新提交' }));

    const footer = document.querySelector('.ant-modal-footer')!;
    const footerSubmit = footer.querySelector('button.ant-btn-primary') as HTMLButtonElement;
    expect(footerSubmit.textContent).toContain('重新提交');
  });
});
