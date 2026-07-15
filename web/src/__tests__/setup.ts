import '@testing-library/jest-dom';
import { vi } from 'vitest';

// jsdom doesn't implement getComputedStyle, which antd Modal uses for scrollbar measurement
const noop = () => {};
Object.defineProperty(window, 'getComputedStyle', {
  value: () => ({
    getPropertyValue: noop,
    length: 0,
  }),
});
(globalThis as any).CSS = { supports: () => false };

// Create a controllable mock for 'umi' module.
// Tests can configure the mock behavior using:
//   import { __setMockLocation } from 'umi';
//   __setMockLocation('/admin');
// This prevents esbuild initialization errors (jsdom breaks invariants
// that esbuild checks) while allowing test-specific configuration.

let mockPathname = '/';
let mockSearch = '';
let mockHash = '';


vi.mock('umi', () => {
  const react = require('react');

  const mockHistory = {
    push: vi.fn(),
    replace: vi.fn(),
    back: vi.fn(),
    go: vi.fn(),
    listen: vi.fn(() => vi.fn()),
    location: {
      get pathname() { return mockPathname; },
      get search() { return mockSearch; },
      get hash() { return mockHash; },
      state: null,
    },
  };

  return {
    __setMockLocation: (fullPath: string) => {
      const [path, ...searchParts] = fullPath.split('?');
      mockPathname = path || '/';
      mockSearch = searchParts.length > 0 ? '?' + searchParts.join('?') : '';
      mockHash = '';
    },
    history: mockHistory,
    Navigate: ({ to }: { to: string }) =>
      react.createElement('div', { 'data-testid': 'navigate', 'data-to': to }),
    Link: ({ to, children, ...props }: any) =>
      react.createElement('a', { href: to, ...props }, children),
    useLocation: () => ({
      pathname: mockPathname,
      search: mockSearch,
      hash: mockHash,
      state: null,
    }),
    useSearchParams: () => {
      const qs = mockSearch.startsWith('?') ? mockSearch.slice(1) : mockSearch;
      return [new URLSearchParams(qs), vi.fn()];
    },
    useNavigate: () => mockHistory,
    Outlet: () => react.createElement('div', { 'data-testid': 'outlet' }),
    useParams: () => ({}),
    useRouteData: () => ({}),
    useAppData: () => ({}),
    useSelectedRoutes: () => [],
    MemoryRouter: ({ children }: { children: React.ReactNode }) => children,
    Routes: ({ children }: { children: React.ReactNode }) => children,
    Route: ({ element }: { element: React.ReactNode }) => element,
  };
});

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});
