import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import AdminApp from '../App';
import { RoleProvider } from '@shared/RoleContext';
import { AuthProvider } from '@shared/auth';

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

function renderAdmin() {
  render(
    <RoleProvider role="admin">
      <AuthProvider initialAccount={{ accountId: 1, roles: ['INSTANCE_ADMIN'] }}>
        <AdminApp />
      </AuthProvider>
    </RoleProvider>,
  );
}

describe('AdminApp', () => {
  it('renders the admin heading', async () => {
    vi.mocked(fetch).mockResolvedValue(
      mockResponse({ data: { initialized: true, version: 'test' } }),
    );
    renderAdmin();
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /实例概览/ })).toBeInTheDocument();
    });
  });
});
