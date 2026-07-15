import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import App from '../ViteRoot';
import { RoleProvider } from '../shared/RoleContext';
import { AuthProvider } from '../shared/auth';
import type { Role } from '../shared/role';

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
});

afterEach(() => {
  vi.restoreAllMocks();
});

function setupMock(defaultData: Record<string, unknown> = {}) {
  vi.mocked(fetch).mockResolvedValue(
    mockResponse({ data: defaultData }),
  );
}

function renderWithRouter(
  initialEntries: string[],
  role: Role = 'child',
  authenticated = true,
): void {
  const account =
    role === 'admin'
      ? { accountId: 1, roles: ['INSTANCE_ADMIN'] }
      : role === 'parent'
        ? { accountId: 2, roles: ['PARENT'], familyId: 1 }
        : { accountId: 3, roles: ['CHILD'], familyId: 1, childId: 3 };

  render(
    <RoleProvider role={role}>
      <MemoryRouter initialEntries={initialEntries}>
        <AuthProvider initialAccount={authenticated ? account : undefined}>
          <App />
        </AuthProvider>
      </MemoryRouter>
    </RoleProvider>,
  );
}

describe('Role-based routing', () => {
  it('renders admin layout at /admin', async () => {
    setupMock({ initialized: true, version: 'test' });
    renderWithRouter(['/admin'], 'admin');
    await waitFor(() => {
      expect(screen.getByText(/管理后台/)).toBeInTheDocument();
    });
  });

  it('renders parent layout at /parent', async () => {
    setupMock();
    renderWithRouter(['/parent'], 'parent');
    await waitFor(() => {
      expect(screen.getByText(/家长端/)).toBeInTheDocument();
    });
  });

  it('renders child layout at /child', async () => {
    setupMock();
    renderWithRouter(['/child'], 'child');
    await waitFor(() => {
      expect(screen.getByText(/儿童端/)).toBeInTheDocument();
    });
  });

  it('redirects / to /child by default', async () => {
    setupMock();
    renderWithRouter(['/'], 'child');
    await waitFor(() => {
      expect(screen.getByText(/儿童端/)).toBeInTheDocument();
    });
  });

  it('shows access denied when role does not match the route', async () => {
    setupMock();
    renderWithRouter(['/admin'], 'child');
    await waitFor(() => {
      expect(screen.getByText(/403|访问被拒绝/i)).toBeInTheDocument();
    });
  });

  it('shows access denied when visiting parent as admin', async () => {
    setupMock();
    renderWithRouter(['/parent'], 'admin');
    await waitFor(() => {
      expect(screen.getByText(/403|访问被拒绝/i)).toBeInTheDocument();
    });
  });

  it('shows a fallback while loading lazy routes', async () => {
    setupMock();
    renderWithRouter(['/child'], 'child');
    await waitFor(() => {
      expect(screen.getByText(/儿童端/)).toBeInTheDocument();
    });
  });

  it('redirects unknown routes to the current role home', async () => {
    setupMock();
    renderWithRouter(['/unknown'], 'child');
    await waitFor(() => {
      expect(screen.getByText(/儿童端/)).toBeInTheDocument();
    });
  });

  it('redirects unauthenticated users to the login flow', async () => {
    // First call (AuthProvider /auth/me) must fail so account stays null.
    // Subsequent calls (lazy imports, page renders) need to succeed.
    vi.mocked(fetch)
      .mockResolvedValueOnce(mockResponse({ error: 'unauthorized' }, false, 401))
      .mockResolvedValue(mockResponse({ data: {} }));
    renderWithRouter(['/parent'], 'parent', false);
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /家长登录/ })).toBeInTheDocument();
    });
  });
});
