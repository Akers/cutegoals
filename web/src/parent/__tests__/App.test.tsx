import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import ParentApp from '../App';
import { RoleProvider } from '@shared/RoleContext';
import { AuthProvider } from '@shared/auth';

function renderParent() {
  render(
    <RoleProvider role="parent">
      <MemoryRouter initialEntries={['/parent']}>
        <AuthProvider initialAccount={{ accountId: 2, roles: ['PARENT'], familyId: 1 }}>
          <ParentApp />
        </AuthProvider>
      </MemoryRouter>
    </RoleProvider>
  );
}

describe('ParentApp', () => {
  it('renders the parent heading', () => {
    renderParent();
    expect(screen.getByRole('heading', { name: /家庭/ })).toBeInTheDocument();
  });

  it('renders the role indicator', () => {
    renderParent();
    expect(screen.getByText(/家长端/)).toBeInTheDocument();
  });
});
