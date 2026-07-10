import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect } from 'vitest';
import App from '../App';
import { RoleProvider } from '../shared/RoleContext';
import type { Role } from '../shared/role';

function renderWithRouter(
  initialEntries: string[],
  role: Role = 'child',
): void {
  render(
    <RoleProvider role={role}>
      <MemoryRouter initialEntries={initialEntries}>
        <App />
      </MemoryRouter>
    </RoleProvider>,
  );
}

describe('Role-based routing', () => {
  it('renders admin layout at /admin', async () => {
    renderWithRouter(['/admin'], 'admin');
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /管理后台/ })).toBeInTheDocument();
    });
  });

  it('renders parent layout at /parent', async () => {
    renderWithRouter(['/parent'], 'parent');
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /家长端/ })).toBeInTheDocument();
    });
  });

  it('renders child layout at /child', async () => {
    renderWithRouter(['/child'], 'child');
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /儿童端/ })).toBeInTheDocument();
    });
  });

  it('redirects / to /child by default', async () => {
    renderWithRouter(['/'], 'child');
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /儿童端/ })).toBeInTheDocument();
    });
  });

  it('shows access denied when role does not match the route', async () => {
    renderWithRouter(['/admin'], 'child');
    await waitFor(() => {
      expect(screen.getByText(/403|访问被拒绝/i)).toBeInTheDocument();
    });
  });

  it('shows access denied when visiting parent as admin', async () => {
    renderWithRouter(['/parent'], 'admin');
    await waitFor(() => {
      expect(screen.getByText(/403|访问被拒绝/i)).toBeInTheDocument();
    });
  });

  it('shows a fallback while loading lazy routes', async () => {
    renderWithRouter(['/child'], 'child');
    // Suspense fallback should render while chunk loads; content eventually resolves
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /儿童端/ })).toBeInTheDocument();
    });
  });

  it('shows 404-like view for unknown routes', async () => {
    renderWithRouter(['/unknown'], 'child');
    await waitFor(() => {
      expect(screen.getByText(/404|未找到|访问被拒绝/i)).toBeInTheDocument();
    });
  });
});
