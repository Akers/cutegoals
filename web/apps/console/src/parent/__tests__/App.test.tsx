import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import ParentApp from '../App';
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

function renderParent() {
  render(
    <RoleProvider role="parent">
      <AuthProvider initialAccount={{ accountId: 2, roles: ['PARENT'], familyId: 1 }}>
        <ParentApp />
      </AuthProvider>
    </RoleProvider>,
  );
}

describe('ParentApp', () => {
  it('renders the parent heading', async () => {
    renderParent();
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /家庭/ })).toBeInTheDocument();
    });
  });
});
