import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import ChildApp from '../App';
import { RoleProvider } from '@shared/RoleContext';
import { AuthProvider } from '@shared/auth';

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
  it('renders the child heading', () => {
    renderChild();
    expect(screen.getAllByRole('heading', { name: /今日任务/ }).length).toBeGreaterThanOrEqual(1);
  });

  it('renders the role indicator', () => {
    renderChild();
    expect(screen.getByText(/儿童端/)).toBeInTheDocument();
  });
});
