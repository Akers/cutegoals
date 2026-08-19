import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import AuthGuard from '../wrappers/AuthGuard';
import HomePage from '../pages/HomePage';
import { RoleProvider } from '@shared/RoleContext';
import { AuthProvider } from '@shared/auth';
import { App } from 'antd';

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
  // /auth/me 返回真实账号数据（与后端契约一致），避免空 data 覆盖 initialAccount
  vi.mocked(fetch).mockResolvedValue(
    mockResponse({ data: { accountId: 3, phone: '', roles: ['CHILD'], familyId: 1, childId: 3 } }),
  );
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

describe('Kid AuthGuard', () => {
  it('renders the guarded outlet for an authenticated child', async () => {
    render(
      <RoleProvider role="child">
        <AuthProvider initialAccount={{ accountId: 3, roles: ['CHILD'], familyId: 1, childId: 3 }}>
          <App>
            <AuthGuard />
          </App>
        </AuthProvider>
      </RoleProvider>,
    );
    await waitFor(() => {
      expect(screen.getByTestId('outlet')).toBeInTheDocument();
    });
  });

  it('redirects unauthenticated visitors to /child/login', async () => {
    // 未登录：/auth/me 返回 401
    vi.mocked(fetch).mockReset();
    vi.mocked(fetch).mockResolvedValue(mockResponse({ code: 'UNAUTHORIZED', message: '未登录' }, false, 401));
    render(
      <RoleProvider role="child">
        <AuthProvider>
          <App>
            <AuthGuard />
          </App>
        </AuthProvider>
      </RoleProvider>,
    );
    await waitFor(() => {
      const nav = screen.getByTestId('navigate');
      expect(nav.getAttribute('data-to')).toBe('/child/login');
    });
  });

  it('renders home heading inside guard for authenticated child', async () => {
    const today = new Date().toISOString().split('T')[0];
    vi.mocked(fetch).mockImplementation((url: string | URL | Request) => {
      const urlStr = typeof url === 'string' ? url : url.toString();
      if (urlStr.includes('/auth/me')) return Promise.resolve(mockResponse({ data: {} }));
      if (urlStr.includes('/task-assignments')) return Promise.resolve(mockResponse({ data: { content: [] } }));
      if (urlStr.includes('/points/balance')) return Promise.resolve(mockResponse({ data: { balance: 0 } }));
      return Promise.reject(new Error('Unexpected: ' + urlStr));
    });
    render(
      <RoleProvider role="child">
        <AuthProvider initialAccount={{ accountId: 3, roles: ['CHILD'], familyId: 1, childId: 3 }}>
          <App>
            <HomePage />
          </App>
        </AuthProvider>
      </RoleProvider>,
    );
    await waitFor(() => {
      expect(screen.getAllByRole('heading', { name: /今日任务/ }).length).toBeGreaterThanOrEqual(1);
    });
    expect(today).toBeTruthy();
  });
});
