import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import ChildApp from '../App';
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
            items: [
              {
                id: 1,
                templateTitle: '整理房间',
                status: 'PENDING',
                deadline: `${today}T20:00:00`,
                points: 10,
                isOverdue: false,
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
});
