import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
// @ts-ignore - MemoryRouter available as transitive dep of umi
import { MemoryRouter } from 'react-router-dom';
import { ChildBindPage } from '../pages/ChildBindPage';
import { ChildLoginPage } from '../pages/ChildLoginPage';
import { RoleProvider } from '@shared/RoleContext';
import { AuthProvider } from '@shared/auth';
import { __setMockLocation } from 'umi';

function mockResponse(data: unknown, ok = true, status = 200) {
  return {
    ok,
    status,
    headers: new Headers({ 'content-type': 'application/json' }),
    json: () => Promise.resolve(data),
  } as unknown as Response;
}

function renderChildPage(element: React.ReactElement, path: string) {
  __setMockLocation(path);
  return render(
    <RoleProvider role="child">
      <MemoryRouter>
        <AuthProvider>
          {element}
        </AuthProvider>
      </MemoryRouter>
    </RoleProvider>,
  );
}

beforeEach(() => {
  vi.spyOn(globalThis, 'fetch').mockReset();
  // Set up a default mock location with the childId query param for login tests
  __setMockLocation('/child/login?childId=3');
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('ChildBindPage', () => {
  it('shows device binding instructions when no children are available', async () => {
    vi.mocked(fetch).mockResolvedValue(mockResponse({ data: [] }));

    renderChildPage(<ChildBindPage />, '/child/bind');

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /设备绑定/ })).toBeInTheDocument();
    });
    expect(screen.getByLabelText('设备标识')).toBeInTheDocument();
  });

  it('renders child profiles when authorized', async () => {
    vi.mocked(fetch).mockResolvedValue(
      mockResponse({ data: [{ id: 3, nickname: '小明' }] }),
    );

    renderChildPage(<ChildBindPage />, '/child/bind');

    await waitFor(() => {
      expect(screen.getByText('小明')).toBeInTheDocument();
    });
  });

  it('shows binding instructions when device is not yet authorized (DEVICE_NOT_AUTHORIZED)', async () => {
    vi.mocked(fetch).mockResolvedValue(
      mockResponse({ code: 'DEVICE_NOT_AUTHORIZED', message: 'DEVICE_NOT_AUTHORIZED' }, false, 401),
    );

    renderChildPage(<ChildBindPage />, '/child/bind');

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /设备绑定/ })).toBeInTheDocument();
    });
    expect(screen.getByLabelText('设备标识')).toBeInTheDocument();
  });
});

describe('ChildLoginPage', () => {
  it('logs in with PIN', async () => {
    vi.mocked(fetch).mockResolvedValue(
      mockResponse({ data: { childId: 3, nickname: '小明', familyId: 1 } }),
    );

    renderChildPage(<ChildLoginPage />, '/child/login?childId=3');

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /输入 PIN/ })).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole('button', { name: '1' }));
    await userEvent.click(screen.getByRole('button', { name: '2' }));
    await userEvent.click(screen.getByRole('button', { name: '3' }));
    await userEvent.click(screen.getByRole('button', { name: '4' }));
    await userEvent.click(screen.getByRole('button', { name: '5' }));
    await userEvent.click(screen.getByRole('button', { name: '6' }));

    await waitFor(() => {
      expect(vi.mocked(fetch)).toHaveBeenCalledWith(
        expect.stringContaining('/auth/child/login'),
        expect.anything(),
      );
    });
  });

  it('locks after too many attempts', async () => {
    vi.mocked(fetch).mockResolvedValue(
      mockResponse({ code: 'CHILD_AUTHENTICATION_FAILED', message: 'PIN 错误' }, false, 400),
    );

    renderChildPage(<ChildLoginPage />, '/child/login?childId=3');

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /输入 PIN/ })).toBeInTheDocument();
    });

    for (let i = 0; i < 5; i++) {
      await userEvent.click(screen.getByRole('button', { name: '1' }));
      await userEvent.click(screen.getByRole('button', { name: '2' }));
      await userEvent.click(screen.getByRole('button', { name: '3' }));
      await userEvent.click(screen.getByRole('button', { name: '4' }));
      await userEvent.click(screen.getByRole('button', { name: '5' }));
      await userEvent.click(screen.getByRole('button', { name: '6' }));
      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /输入 PIN/ })).toBeInTheDocument();
      });
    }

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/锁定/);
    });
  });
});
