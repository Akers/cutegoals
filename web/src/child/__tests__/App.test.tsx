import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import ChildApp from '../App';

describe('ChildApp', () => {
  it('renders the child heading', () => {
    render(<ChildApp />);
    expect(screen.getByRole('heading', { name: /儿童端/ })).toBeInTheDocument();
  });

  it('renders the role indicator', () => {
    render(<ChildApp />);
    expect(screen.getByText('Child')).toBeInTheDocument();
  });
});
