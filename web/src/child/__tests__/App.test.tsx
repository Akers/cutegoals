import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import ChildApp from '../App';
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
  vi.mocked(fetch).mockResolvedValue(mockResponse({ data: {} }));
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

function renderChild() {
  render(
    <RoleProvider role="child">
      <MemoryRouter initialEntries={['/child']}>
        <AuthProvider initialAccount={{ accountId: 3, roles: ['CHILD'], familyId: 1, childId: 3 }}>
          <ChildApp />
        </AuthProvider>
      </MemoryRouter>
    </RoleProvider>,
  );
}

describe('ChildApp', () => {
  it('renders the child heading', async () => {
    renderChild();
    await waitFor(() => {
      // Both the PageHeader <h1> and the CardSection <h2> say "今日任务"
      expect(screen.getAllByRole('heading', { name: /今日任务/ }).length).toBeGreaterThanOrEqual(1);
    });
  });

  it('renders the role indicator', async () => {
    renderChild();
    await waitFor(() => {
      expect(screen.getByText(/儿童端/)).toBeInTheDocument();
    });
  });
});
