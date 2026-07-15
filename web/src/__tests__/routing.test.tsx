import { render, screen, waitFor } from '@testing-library/react';
// @ts-ignore - MemoryRouter available as transitive dep of umi
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import App from '../ViteRoot';
import { RoleProvider } from '../shared/RoleContext';
import { AuthProvider } from '../shared/auth';
import type { Role } from '../shared/role';

// Import the mock controller to configure the fake location
import { __setMockLocation } from 'umi';

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

  // Set the mock location before rendering
  __setMockLocation(initialEntries[0] || '/');

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
      expect(screen.getByRole('heading', { name: /实例概览/ })).toBeInTheDocument();
    });
  });

  it('renders parent layout at /parent', async () => {
    setupMock();
    renderWithRouter(['/parent'], 'parent');
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /家庭/ })).toBeInTheDocument();
    });
  });

  it('renders child layout at /child', async () => {
    setupMock();
    renderWithRouter(['/child'], 'child');
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /今日任务/ })).toBeInTheDocument();
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

  it('redirects unauthenticated users to the login flow', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(mockResponse({ error: 'unauthorized' }, false, 401))
      .mockResolvedValue(mockResponse({ data: {} }));
    renderWithRouter(['/parent'], 'parent', false);
    // Navigate mock renders a div with data-testid="navigate" and data-to attribute
    await waitFor(() => {
      const nav = screen.getByTestId('navigate');
      expect(nav).toHaveAttribute('data-to', '/parent/login');
    });
  });
});
