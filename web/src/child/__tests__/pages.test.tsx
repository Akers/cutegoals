import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import userEvent from '@testing-library/user-event';
import ChildApp from '../App';
import { RoleProvider } from '@shared/RoleContext';
import { AuthProvider } from '@shared/auth';
import { ToastProvider } from '@shared/components';

const childAccount = { accountId: 3, roles: ['CHILD'], familyId: 1, childId: 3 };

function renderChildRoute(initialEntries: string[]) {
  render(
    <RoleProvider role="child">
      <MemoryRouter initialEntries={initialEntries}>
        <AuthProvider initialAccount={childAccount}>
          <ToastProvider>
            <ChildApp />
          </ToastProvider>
        </AuthProvider>
      </MemoryRouter>
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

describe('Child pages', () => {
  it('renders home page with balance and tasks', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(
        mockResponse({
          data: {
            items: [
              {
                id: 1,
                templateTitle: '整理房间',
                status: 'PENDING',
                deadline: '2026-07-11T20:00:00',
                points: 10,
                isOverdue: false,
              },
            ],
          },
        }),
      )
      .mockResolvedValueOnce(mockResponse({ data: { balance: 120 } }));

    renderChildRoute(['/child']);
    await waitFor(() => {
      expect(screen.getByText(/120.*积分/)).toBeInTheDocument();
    });
    expect(screen.getByText('整理房间')).toBeInTheDocument();
  });

  it('renders task list and allows submission', async () => {
    vi.mocked(fetch).mockResolvedValue(
      mockResponse({
        data: {
          items: [
            {
              id: 1,
              templateTitle: '整理房间',
              status: 'PENDING',
              deadline: '2026-07-11T20:00:00',
              points: 10,
              isOverdue: false,
            },
          ],
        },
      }),
    );

    renderChildRoute(['/child/tasks']);
    await waitFor(() => {
      expect(screen.getByText('整理房间')).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole('button', { name: /提交/ }));
    expect(screen.getByRole('dialog')).toHaveTextContent('提交任务');

    await userEvent.type(screen.getByPlaceholderText('说说你是怎么完成任务的'), '我收拾好了');
    vi.mocked(fetch)
      .mockReset()
      .mockResolvedValueOnce(mockResponse({ data: { id: 100 } }));
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /提交/ }));

    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
  });

  it('renders rejected task with reason', async () => {
    vi.mocked(fetch).mockResolvedValue(
      mockResponse({
        data: {
          items: [
            {
              id: 1,
              templateTitle: '整理房间',
              status: 'REJECTED',
              deadline: '2026-07-11T20:00:00',
              points: 10,
              isOverdue: false,
              rejectionReason: '收拾不够干净',
            },
          ],
        },
      }),
    );

    renderChildRoute(['/child/tasks']);
    await waitFor(() => {
      expect(screen.getByText('驳回原因：收拾不够干净')).toBeInTheDocument();
    });
    expect(screen.getByRole('button', { name: /重新提交/ })).toBeInTheDocument();
  });

  it('renders prize shop and exchanges', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(mockResponse({ data: { balance: 200 } }))
      .mockResolvedValueOnce(
        mockResponse({
          data: {
            items: [{ id: 1, name: '贴纸', description: '可爱贴纸', pointsCost: 50, availableStock: 5 }],
          },
        }),
      )
      .mockResolvedValueOnce(mockResponse({ data: { id: 10 } }));

    renderChildRoute(['/child/prizes']);
    await waitFor(() => {
      expect(screen.getByText('贴纸')).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole('button', { name: /兑换/ }));
    await userEvent.click(screen.getByRole('button', { name: /确认兑换/ }));

    await waitFor(() => {
      expect(screen.getByText(/兑换成功/)).toBeInTheDocument();
    });
  });

  it('renders exchange history', async () => {
    vi.mocked(fetch).mockResolvedValue(
      mockResponse({
        data: {
          items: [
            {
              id: 1,
              type: 'PRIZE',
              targetName: '贴纸',
              pointsCost: 50,
              status: 'PENDING_FULFILLMENT',
              createdAt: '2026-07-10',
            },
          ],
        },
      }),
    );

    renderChildRoute(['/child/exchanges']);
    await waitFor(() => {
      expect(screen.getByText('贴纸')).toBeInTheDocument();
    });
  });

  it('renders blind boxes and opens result', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(mockResponse({ data: { balance: 100 } }))
      .mockResolvedValueOnce(
        mockResponse({
          data: {
            items: [{ id: 1, name: '幸运盒', cost: 20, availabilityVersion: 'v1' }],
          },
        }),
      )
      .mockResolvedValueOnce(
        mockResponse({
          data: [{ prizeId: 1, prizeName: '神秘贴纸', probability: 1 }],
        }),
      )
      .mockResolvedValueOnce(
        mockResponse({ data: { prizeId: 1, prizeName: '神秘贴纸' } }),
      );

    renderChildRoute(['/child/blind-boxes']);
    await waitFor(() => {
      expect(screen.getByText('幸运盒')).toBeInTheDocument();
    });

    await userEvent.click(screen.getByText('幸运盒'));
    await waitFor(() => {
      expect(screen.getByText('神秘贴纸')).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole('button', { name: /开启盲盒/ }));
    await userEvent.click(screen.getByRole('button', { name: /确认开启/ }));

    await waitFor(() => {
      expect(screen.getByRole('dialog')).toHaveTextContent('神秘贴纸');
    });
  });
});
