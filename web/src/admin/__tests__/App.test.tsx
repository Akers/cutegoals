import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import AdminApp from '../App';

describe('AdminApp', () => {
  it('renders the admin heading', () => {
    render(<AdminApp />);
    expect(screen.getByRole('heading', { name: /管理后台/ })).toBeInTheDocument();
  });

  it('renders the role indicator', () => {
    render(<AdminApp />);
    expect(screen.getByText('Admin')).toBeInTheDocument();
  });
});
