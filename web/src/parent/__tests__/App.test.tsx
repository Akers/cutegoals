import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import ParentApp from '../App';

describe('ParentApp', () => {
  it('renders the parent heading', () => {
    render(<ParentApp />);
    expect(screen.getByRole('heading', { name: /家长端/ })).toBeInTheDocument();
  });

  it('renders the role indicator', () => {
    render(<ParentApp />);
    expect(screen.getByText('Parent')).toBeInTheDocument();
  });
});
